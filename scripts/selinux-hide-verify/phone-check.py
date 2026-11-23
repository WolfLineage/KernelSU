#!/usr/bin/env python3
"""Phone-side, cable-free check for KSU timing fingerprints.

Paste the whole file into a Termux shell (python must be installed):

    python - <<'PY'
    ... paste contents here ...
    PY

No root needed for the newfstatat/statx discriminant. With root it also
toggles selinux_hide off/on and reports that hook's own cost.
"""

import ctypes
import os
import statistics
import subprocess
import time

AT_FDCWD = -100
# asm-generic/unistd.h (arm64 / riscv64)
NR = {"statx": 291, "newfstatat": 79, "faccessat": 48}

if os.uname().machine not in ("aarch64", "arm64", "riscv64"):
    raise SystemExit("this script uses asm-generic syscall numbers (arm64/riscv64)")

libc = ctypes.CDLL(None, use_errno=True)
syscall = libc.syscall
syscall.restype = ctypes.c_long
buf = ctypes.create_string_buffer(512)
path = b"/system/bin/sh"


def call(name):
    nr = NR[name]
    if name == "statx":
        return syscall(nr, AT_FDCWD, path, 0, 0x7FF, buf)
    if name == "newfstatat":
        return syscall(nr, AT_FDCWD, path, buf, 0)
    return syscall(nr, AT_FDCWD, path, 0)


def bench(name, batch=128):
    t0 = time.monotonic_ns()
    for _ in range(batch):
        call(name)
    return (time.monotonic_ns() - t0) / batch


def sample(n=300):
    for _ in range(150):
        for name in NR:
            call(name)
    out = {k: [] for k in NR}
    for _ in range(n):
        for name in NR:
            out[name].append(bench(name))
    return {k: statistics.median(v) for k, v in out.items()}


root = os.geteuid() == 0
ksud = next((p for p in ("/data/adb/yusud", "/data/adb/ksud") if os.path.exists(p)), None)
states = (0, 1) if (root and ksud) else (None,)
results = {}

for st in states:
    if st is not None:
        subprocess.run([ksud, "feature", "set", "selinux_hide", str(st)], capture_output=True)
        time.sleep(0.4)
    results[st] = sample()

if 1 in results:
    subprocess.run([ksud, "feature", "set", "selinux_hide", "1"], capture_output=True)

print("uid=%d root=%s ksud=%s" % (os.geteuid(), root, ksud))
print()
print("%-10s %10s %12s %8s %12s %8s" % ("state", "statx ns", "newfstatat", "ratio", "faccessat", "ratio"))
for st, m in results.items():
    tag = "hide=%d" % st if st is not None else "as-is"
    nf, fa, sx = m["newfstatat"], m["faccessat"], m["statx"]
    print("%-10s %10.0f %12.0f %8.3f %12.0f %8.3f" % (tag, sx, nf, nf / sx, fa, fa / sx))

m = results.get(1) or results[None]
r1 = m["newfstatat"] / m["statx"]
r2 = m["faccessat"] / m["statx"]
print()
print("KSU syscall timing discriminant (> 1.2 = fingerprint present):")
print("  newfstatat/statx = %.3f  -> %s" % (r1, "KSU-like" if r1 > 1.2 else "clean"))
print("  faccessat/statx  = %.3f  -> %s" % (r2, "KSU-like" if r2 > 1.2 else "clean"))

if 0 in results and 1 in results:
    print()
    print("selinux_hide own cost (root context, on minus off):")
    for k in NR:
        print("  %-10s %+8.0f ns  (%.3fx)" % (k, results[1][k] - results[0][k], results[1][k] / results[0][k]))

print()
print("NOTE: under `su` you are in the su domain and on the allowlist, so the")
print("newfstatat path does an extra strncpy_from_user; run in Termux as a plain")
print("app for the number a real detector would see.")
