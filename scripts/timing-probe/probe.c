#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <fcntl.h>

#ifndef __NR_statx
#error "__NR_statx is not defined for this target"
#endif
#ifndef __NR_newfstatat
#ifdef __NR_fstatat64
#define __NR_newfstatat __NR_fstatat64
#endif
#endif
#ifndef __NR_getpid
#define __NR_getpid 39
#endif
#ifndef __NR_openat
#define __NR_openat 56
#endif
#ifndef __NR_close
#define __NR_close 3
#endif

#define ITERS 2000

static volatile long long sink = 0;

static inline long long now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

enum { OP_NEWFSTATAT, OP_STATX, OP_FACCESSAT, OP_OPENAT, OP_COUNT };
static const char *op_name[OP_COUNT] = {
    "newfstatat", "statx", "faccessat", "openat"
};

static struct stat st_buf;
static char statx_buf[256] __attribute__((aligned(16)));

static long long measure(int op, const char *path, int n)
{
    long long t0 = now_ns();
    for (int i = 0; i < n; i++) {
        switch (op) {
        case OP_NEWFSTATAT:
            sink += syscall(__NR_newfstatat, AT_FDCWD, path, &st_buf, 0);
            break;
        case OP_STATX:
            sink += syscall(__NR_statx, AT_FDCWD, path, 0, 0x7ff, statx_buf);
            break;
        case OP_FACCESSAT:
            sink += syscall(__NR_faccessat, AT_FDCWD, path, F_OK, 0);
            break;
        case OP_OPENAT: {
            long fd = syscall(__NR_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0);
            if (fd >= 0) syscall(__NR_close, fd);
            sink += fd;
            break;
        }
        }
    }
    return now_ns() - t0;
}

static int cmp_ll(const void *a, const void *b)
{
    long long x = *(const long long *)a, y = *(const long long *)b;
    return (x > y) - (x < y);
}

static int cmp_dbl(const void *a, const void *b)
{
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

static void print_identity(const char *tag)
{
    char ctx[256] = {0};
    FILE *f = fopen("/proc/self/attr/current", "r");
    if (f) {
        if (fgets(ctx, sizeof(ctx), f))
            ctx[strcspn(ctx, "\n")] = 0;
        fclose(f);
    }
    printf("[%s] uid=%d euid=%d selinux=%s\n",
           tag, (int)getuid(), (int)geteuid(), ctx[0] ? ctx : "?");
}

static void usage(const char *prog)
{
    printf("usage: %s [-r] [-u UID] [rounds] [path]\n", prog);
    printf("  -r        keep root (debug only)\n");
    printf("  -u UID    drop to UID when root (default 10123)\n");
    printf("  rounds    number of ratio samples (default 15)\n");
    printf("  path      file to stat (default /system/bin/ls)\n");
}

int main(int argc, char **argv)
{
    int keep_root = 0, target_uid = 10123, rounds = 15;
    const char *path = "/system/bin/ls";

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-r") == 0) {
            keep_root = 1;
        } else if (strcmp(argv[i], "-u") == 0 && i + 1 < argc) {
            target_uid = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-h") == 0) {
            usage(argv[0]);
            return 0;
        } else if (argv[i][0] >= '0' && argv[i][0] <= '9') {
            rounds = atoi(argv[i]);
        } else {
            path = argv[i];
        }
    }

    print_identity("start");
    int uid = (int)getuid();
    int drop_failed = 0;
    if (!keep_root && uid == 0) {
        setgid((gid_t)target_uid);
        if (setresuid((uid_t)target_uid, (uid_t)target_uid, (uid_t)target_uid) != 0)
            drop_failed = 1;
        uid = (int)getuid();
        if (uid != target_uid) drop_failed = 1;
        if (!drop_failed) print_identity("after-drop");
    }
    if (drop_failed || uid < 10000)
        printf("WARNING: uid=%d is not an app uid; result may differ.\n", uid);

    /* Per-syscall absolute timings (median of rounds) */
    long long samples[64];
    printf("\nYuSU timing probe (uid=%d, path=%s, rounds=%d)\n", uid, path, rounds);
    printf("  %-11s %8s\n", "syscall", "ns/call");
    for (int op = 0; op < OP_COUNT; op++) {
        measure(op, path, ITERS);
        for (int k = 0; k < rounds && k < 64; k++)
            samples[k] = measure(op, path, ITERS);
        qsort(samples, rounds, sizeof(long long), cmp_ll);
        printf("  %-11s %8lld\n", op_name[op], samples[rounds / 2] / ITERS);
    }

    /* Ratio distribution: median, min, max, how many exceed 1.2 */
    double ratios[64];
    measure(OP_NEWFSTATAT, path, ITERS);
    measure(OP_STATX, path, ITERS);
    for (int k = 0; k < rounds && k < 64; k++) {
        long long a = measure(OP_NEWFSTATAT, path, ITERS);
        long long b = measure(OP_STATX, path, ITERS);
        ratios[k] = (double)a / (double)b;
    }
    qsort(ratios, rounds, sizeof(double), cmp_dbl);
    int over = 0;
    for (int k = 0; k < rounds; k++) if (ratios[k] > 1.2) over++;

    printf("\nnewfstatat/statx over %d rounds:\n", rounds);
    printf("  min=%.3f  median=%.3f  max=%.3f\n",
           ratios[0], ratios[rounds / 2], ratios[rounds - 1]);
    printf("  exceeded 1.2: %d/%d (%.0f%%)\n", over, rounds,
           100.0 * over / rounds);
    printf("\nInterpretation:\n");
    printf("  - median ~1.0 and few exceed  -> statx alignment working\n");
    printf("  - median >1.2 and most exceed  -> timing signal present\n");
    printf("  - wide min/max, ~half exceed   -> noisy; rerun with more rounds\n");
    printf("\nNote: statx is hooked too, and openat is NOT hooked (reference).\n");
    return 0;
}
