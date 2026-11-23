/*
 * selinux_hide timing probe (YuSU verification kit)
 *
 * Measures per-call latency of every selinuxfs / proc-attr operation a
 * detector could plausibly time, plus two syscall controls (statx vs
 * newfstatat) used by the well-known KSU kprobe side-channel test.
 *
 * Design notes:
 *  - Every sample is the wall time of `batch` identical calls, not a single
 *    call. This amplifies the hook's cost above clock_gettime() overhead.
 *  - Ops are executed round-robin (inner loop over ops, outer over samples)
 *    so CPU frequency drift, thermal throttling and scheduler noise hit all
 *    ops equally. Never measure one op to completion before the next.
 *  - Samples are buffered and flushed once at exit, so the measurement loop
 *    never touches the filesystem.
 *
 * Build (aarch64, static, run from /data/local/tmp):
 *   $(NDK)/toolchains/llvm/prebuilt/<host>/bin/aarch64-linux-android30-clang \
 *       -O2 -static -o probe probe.c
 *
 * Usage:
 *   probe --label yusu-hide-on --uid 10123 --samples 400 --batch 32 \
 *         --out /data/local/tmp/yusu-hide-on.10123.csv
 *
 * CSV columns: label,uid,op,sample_index,total_ns
 */

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/xattr.h>
#include <time.h>
#include <unistd.h>

#ifndef AT_FDCWD
#define AT_FDCWD -100
#endif
#ifndef O_CLOEXEC
#define O_CLOEXEC 0
#endif

#define MAX_OPS 12

static inline int64_t now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + (int64_t)ts.tv_nsec;
}

/* prevents the compiler from discarding a syscall's result */
static volatile unsigned long sink;

/* ------------------------------------------------------------------ */
/* operations                                                          */
/* ------------------------------------------------------------------ */

/* selinuxfs/status: the only selinuxfs node an untrusted app may open */
static int64_t op_status_open(int batch)
{
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
        int fd = open("/sys/fs/selinux/status", O_RDONLY | O_CLOEXEC);
        if (fd >= 0) {
            sink += (unsigned long)fd;
            close(fd);
        } else {
            sink += (unsigned long)errno;
        }
    }
    return now_ns() - t0;
}

static int64_t op_status_read(int batch)
{
    char buf[8];
    int fd = open("/sys/fs/selinux/status", O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return -1;
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
        lseek(fd, 0, SEEK_SET);
        sink += (unsigned long)read(fd, buf, sizeof(buf));
    }
    int64_t dt = now_ns() - t0;
    close(fd);
    return dt;
}

/* /proc/self/attr/current read: selinux_getprocattr, not hooked by KSU */
static int64_t op_attr_read(int batch)
{
    char buf[256];
    int fd = open("/proc/self/attr/current", O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return -1;
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
        lseek(fd, 0, SEEK_SET);
        sink += (unsigned long)read(fd, buf, sizeof(buf));
    }
    int64_t dt = now_ns() - t0;
    close(fd);
    return dt;
}

/*
 * /proc/self/attr/current write: goes through selinux_setprocattr, which
 * YuSU replaces with my_setprocattr(). An unknown type keeps the write
 * failing (EINVAL) *after* the context parse, so the parse cost is what
 * gets measured and the probe never mutates its own domain.
 */
static int64_t op_attr_write(int batch)
{
    static const char bad[] = "u:object_r:ksu_probe_no_such_type:s0";
    int fd = open("/proc/self/attr/current", O_WRONLY | O_CLOEXEC);
    if (fd < 0)
        return -1;
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
        lseek(fd, 0, SEEK_SET);
        sink += (unsigned long)write(fd, bad, sizeof(bad) - 1);
    }
    int64_t dt = now_ns() - t0;
    close(fd);
    return dt;
}

/* selinuxfs/context: 0600 root, an app gets EACCES from the VFS */
static int64_t op_context_open(int batch)
{
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
        int fd = open("/sys/fs/selinux/context", O_WRONLY | O_CLOEXEC);
        if (fd >= 0) {
            sink += (unsigned long)fd;
            close(fd);
        } else {
            sink += (unsigned long)errno;
        }
    }
    return now_ns() - t0;
}

/*
 * fsetxattr("security.selinux"): the one path where an unprivileged app
 * actually enters my_inode_setxattr()'s body. It stops at the
 * cap_capable(CAP_MAC_ADMIN) check and falls through to the original, so this
 * measures the hook's entry cost and nothing more.
 */
static int64_t op_setxattr_selinux(int batch)
{
    static int fd = -1;
    if (fd < 0) {
        char tmpl[] = "/data/local/tmp/selprobe.XXXXXX";
        fd = mkstemp(tmpl);
        if (fd < 0) {
            char tmpl2[] = "./selprobe.XXXXXX";
            fd = mkstemp(tmpl2);
            if (fd < 0)
                return -1;
            unlink(tmpl2);
        } else {
            unlink(tmpl);
        }
    }
    static const char ctx[] = "u:object_r:untrusted_app:s0";
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++)
        sink += (unsigned long)fsetxattr(fd, "security.selinux", ctx, sizeof(ctx) - 1, 0);
    return now_ns() - t0;
}

static int64_t op_enforce_read(int batch)
{
    char buf[8];
    int fd = open("/sys/fs/selinux/enforce", O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return -1;
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
        lseek(fd, 0, SEEK_SET);
        sink += (unsigned long)read(fd, buf, sizeof(buf));
    }
    int64_t dt = now_ns() - t0;
    close(fd);
    return dt;
}

/* controls ---------------------------------------------------------- */

static int64_t op_statx(int batch)
{
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
#ifdef __NR_statx
        struct statx stx;
        sink += (unsigned long)syscall(__NR_statx, AT_FDCWD, "/system/bin/sh", 0, 0x7ff, &stx);
#else
        struct stat st;
        sink += (unsigned long)syscall(__NR_stat, "/system/bin/sh", &st);
#endif
    }
    return now_ns() - t0;
}

static int64_t op_newfstatat(int batch)
{
    struct stat st;
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++)
        sink += (unsigned long)syscall(__NR_newfstatat, AT_FDCWD, "/system/bin/sh", &st, 0);
    return now_ns() - t0;
}

/* __NR_faccessat is one of the syscalls KSU replaces in the syscall table */
static int64_t op_faccessat(int batch)
{
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++)
        sink += (unsigned long)syscall(__NR_faccessat, AT_FDCWD, "/system/bin/sh", F_OK);
    return now_ns() - t0;
}

static int64_t op_getpid(int batch)
{
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++)
        sink += (unsigned long)syscall(__NR_getpid);
    return now_ns() - t0;
}

static int64_t op_proc_status_read(int batch)
{
    char buf[512];
    int fd = open("/proc/self/status", O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return -1;
    int64_t t0 = now_ns();
    for (int i = 0; i < batch; i++) {
        lseek(fd, 0, SEEK_SET);
        sink += (unsigned long)read(fd, buf, sizeof(buf));
    }
    int64_t dt = now_ns() - t0;
    close(fd);
    return dt;
}

struct probe_op {
    const char *name;
    int64_t (*fn)(int batch);
};

static const struct probe_op ops[] = {
    { "status_open", op_status_open },
    { "status_read", op_status_read },
    { "attr_read", op_attr_read },
    { "attr_write", op_attr_write },
    { "context_open", op_context_open },
    { "setxattr_selinux", op_setxattr_selinux },
    { "enforce_read", op_enforce_read },
    { "statx", op_statx },
    { "newfstatat", op_newfstatat },
    { "faccessat", op_faccessat },
    { "getpid", op_getpid },
    { "proc_status_read", op_proc_status_read },
};

#define N_OPS ((int)(sizeof(ops) / sizeof(ops[0])))

static void usage(const char *argv0)
{
    fprintf(stderr,
            "usage: %s --label NAME [--uid N] [--samples N] [--batch N]\n"
            "          [--warmup N] [--out FILE]\n"
            "  --uid      drop to this uid before measuring (requires root)\n"
            "  --samples  samples per op (default 400)\n"
            "  --batch    calls per sample (default 32)\n"
            "  --warmup   discarded samples (default 20)\n",
            argv0);
}

int main(int argc, char **argv)
{
    const char *label = "unlabeled";
    const char *out_path = NULL;
    long want_uid = -1;
    int samples = 400, batch = 32, warmup = 20;

    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--label") && i + 1 < argc)
            label = argv[++i];
        else if (!strcmp(argv[i], "--uid") && i + 1 < argc)
            want_uid = strtol(argv[++i], NULL, 10);
        else if (!strcmp(argv[i], "--samples") && i + 1 < argc)
            samples = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--batch") && i + 1 < argc)
            batch = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--warmup") && i + 1 < argc)
            warmup = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--out") && i + 1 < argc)
            out_path = argv[++i];
        else if (!strcmp(argv[i], "--help") || !strcmp(argv[i], "-h")) {
            usage(argv[0]);
            return 0;
        } else {
            usage(argv[0]);
            return 2;
        }
    }

    if (samples <= 0 || batch <= 0 || warmup < 0) {
        fprintf(stderr, "invalid sample counts\n");
        return 2;
    }
    if (want_uid >= 0) {
        if (getuid() != 0) {
            fprintf(stderr, "--uid requires root\n");
            return 3;
        }
        if (setuid((uid_t)want_uid) != 0) {
            fprintf(stderr, "setuid(%ld) failed: %s\n", want_uid, strerror(errno));
            return 3;
        }
    }

    uid_t uid = getuid();
    int total = samples + warmup;

    int64_t *buf = calloc((size_t)N_OPS * (size_t)total, sizeof(int64_t));
    if (!buf) {
        fprintf(stderr, "out of memory\n");
        return 4;
    }

    /* round-robin so that drift affects every op identically */
    for (int s = 0; s < total; s++) {
        for (int o = 0; o < N_OPS; o++)
            buf[(size_t)o * total + s] = ops[o].fn(batch);
    }

    FILE *fp = stdout;
    if (out_path) {
        fp = fopen(out_path, "w");
        if (!fp) {
            fprintf(stderr, "cannot open %s: %s\n", out_path, strerror(errno));
            return 5;
        }
    }

    fprintf(fp, "label,uid,op,sample,total_ns\n");
    for (int o = 0; o < N_OPS; o++) {
        for (int s = warmup; s < total; s++) {
            int64_t v = buf[(size_t)o * total + s];
            if (v < 0)
                continue;
            fprintf(fp, "%s,%u,%s,%d,%lld\n", label, (unsigned)uid, ops[o].name, s, (long long)v);
        }
    }
    if (out_path)
        fclose(fp);

    fprintf(stderr, "probe: label=%s uid=%u samples=%d batch=%d ops=%d\n", label, (unsigned)uid, samples, batch,
            N_OPS);
    free(buf);
    return 0;
}
