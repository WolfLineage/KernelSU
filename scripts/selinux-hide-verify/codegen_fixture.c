/*
 * Codegen fixture: original KernelSU vs YuSU branch shapes.
 *
 * Both spellings below are semantically identical (FIRST_APPLICATION_UID is
 * 10000) and, at -O2, compile to the same instruction sequence. This fixture
 * exists so `codegen-diff.sh` can prove that on a given compiler, instead of
 * trusting a code review.
 */

typedef unsigned int u32;

/* kernel-provided in userspace build */
#ifndef __always_inline
#define __always_inline inline __attribute__((always_inline))
#endif

extern u32 ksu_current_uid(void);
extern int ksu_selinux_hide_enabled;
extern long orig_op(void *f, char *buf, unsigned long size);
extern long hidden_op(void *f, char *buf, unsigned long size);
extern int orig_open(void *inode, void *filp);
extern int fake_open(void *inode, void *filp);

/* ---- original KernelSU: scattered magic constant ------------------ */

long ksu_ctx_write(void *f, char *buf, unsigned long size)
{
    if (__builtin_expect(ksu_current_uid() < 10000, 1))
        return orig_op(f, buf, size);
    return hidden_op(f, buf, size);
}

int ksu_status_open(void *inode, void *filp)
{
    if (__builtin_expect(ksu_current_uid() >= 10000 && ksu_selinux_hide_enabled, 1))
        return fake_open(inode, filp);
    return orig_open(inode, filp);
}

/* ---- YuSU: one inlined predicate ---------------------------------- */

static __always_inline int ksu_selinux_hide_applies(void)
{
    return __builtin_expect(ksu_current_uid() >= 10000, 1);
}

long yusu_ctx_write(void *f, char *buf, unsigned long size)
{
    if (!ksu_selinux_hide_applies())
        return orig_op(f, buf, size);
    return hidden_op(f, buf, size);
}

int yusu_status_open(void *inode, void *filp)
{
    if (ksu_selinux_hide_applies() && ksu_selinux_hide_enabled)
        return fake_open(inode, filp);
    return orig_open(inode, filp);
}
