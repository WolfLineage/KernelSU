#!/usr/bin/env bash
# On-device A/B driver for the selinux_hide timing probe.
#
# Runs the same probe twice on the *same* kernel: once with the feature
# disabled (hooks unpatched -> behaves like stock) and once with it enabled.
# The difference is this kernel's selinux_hide timing signature. Repeat the
# whole run on the original KernelSU kernel and on stock, then compare the
# signatures with analyze.py.
#
# usage: ./run.sh <label-prefix> [--ksud /data/adb/yusud] [--uid 10123]
#
# Requires: adb, root shell, NDK clang.

set -euo pipefail

prefix="${1:-run}"
shift || true
ksud="/data/adb/yusud"
appuid="10123"
samples=400
batch=32

while [[ $# -gt 0 ]]; do
    case "$1" in
        --ksud) ksud="$2"; shift 2 ;;
        --uid) appuid="$2"; shift 2 ;;
        --samples) samples="$2"; shift 2 ;;
        --batch) batch="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
outdir="$here/out/$prefix"
mkdir -p "$outdir"

# ---- build ----------------------------------------------------------
ndk="${ANDROID_NDK_HOME:-}"
if [[ -z "$ndk" ]]; then
    for cand in "$HOME/AppData/Local/Android/Sdk/ndk"/* "$HOME/Android/Sdk/ndk"/* /opt/android-ndk*; do
        [[ -d "$cand" ]] && ndk="$cand"
    done
fi
if [[ -x "$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" ]]; then
    cc="$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
else
    cc="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
fi
[[ -x "$cc" ]] || { echo "NDK clang not found; set ANDROID_NDK_HOME" >&2; exit 1; }

"$cc" --target=aarch64-linux-android30 -O2 -static -o "$outdir/probe" "$here/probe.c"
echo "built $outdir/probe"

adb push "$outdir/probe" /data/local/tmp/sel_probe >/dev/null
adb shell chmod 755 /data/local/tmp/sel_probe

# ---- run ------------------------------------------------------------
run_one() {
    local state="$1" uid="$2"
    local label="${prefix}-hide${state}"
    adb shell su -c "/data/local/tmp/sel_probe --label ${label} --uid ${uid} \
        --samples ${samples} --batch ${batch} \
        --out /data/local/tmp/${prefix}-${state}-${uid}.csv" >/dev/null
    adb pull "/data/local/tmp/${prefix}-${state}-${uid}.csv" "$outdir/" >/dev/null
    echo "  captured ${label} uid=${uid}"
}

for state in 0 1; do
    # capitalise ON/OFF for readability in the CSV labels
    echo "== selinux_hide=${state} =="
    adb shell su -c "${ksud} feature set selinux_hide ${state}" || {
        echo "failed to toggle selinux_hide; is ${ksud} present?" >&2
        exit 1
    }
    # give the text patch / LSM hook a moment to settle
    sleep 1
    run_one "$state" 0
    run_one "$state" "$appuid"
done

# leave the device in the state we found it (enabled)
adb shell su -c "${ksud} feature set selinux_hide 1" || true

echo
echo "CSVs in $outdir"
echo "next:"
echo "  ./analyze.py --baseline ${prefix}-hide0 --candidate ${prefix}-hide1 $outdir/*.csv"
