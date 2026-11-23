#!/usr/bin/env bash
# Prove (or refute) that the YuSU branch shape compiles identically to the
# original KernelSU branch shape on a given clang.
#
# Requires the Android NDK clang; falls back to any clang with an aarch64
# target.
#
# usage: ./codegen-diff.sh [path-to-ndk]

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ndk="${1:-${ANDROID_NDK_HOME:-}}"
if [[ -z "$ndk" ]]; then
    for cand in "$HOME/AppData/Local/Android/Sdk/ndk"/* "$HOME/Android/Sdk/ndk"/* /opt/android-ndk*; do
        [[ -d "$cand" ]] && ndk="$cand"
    done
fi

if [[ -n "$ndk" && -x "$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" ]]; then
    bindir="$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin"
    cc="$bindir/clang.exe"
    od="$bindir/llvm-objdump.exe"
elif [[ -n "$ndk" && -x "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]]; then
    bindir="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
    cc="$bindir/clang"
    od="$bindir/llvm-objdump"
else
    cc="$(command -v clang || true)"
    od="$(command -v llvm-objdump || command -v objdump || true)"
fi

[[ -n "$cc" && -n "$od" ]] || { echo "no clang/objdump found; pass the NDK path as \$1" >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "clang : $cc"
"$cc" --version | head -1

"$cc" --target=aarch64-linux-gnu -O2 -c -o "$tmp/fixture.o" "$here/codegen_fixture.c"
"$od" -d --no-show-raw-insn "$tmp/fixture.o" > "$tmp/disasm.txt"

# Extract one function's body, normalise away addresses / comments / labels.
extract() {
    awk -v want="$1" '
        /^[0-9a-f]+ <[^>]+>:/ {
            name = $0
            sub(/^[0-9a-f]+ </, "", name)
            sub(/>:$/, "", name)
            inside = (name == want)
            next
        }
        inside {
            line = $0
            sub(/\/\/.*$/, "", line)                    # strip comments
            sub(/^[ \t]*[0-9a-f]+:[ \t]*/, "", line)    # strip instruction address
            gsub(/0x[0-9a-f]+ *<[^>]*>/, "TGT", line)   # strip branch targets / relocs
            gsub(/\.[Ll]BB[0-9]+_[0-9]+/, ".LBBx", line) # strip block labels
            sub(/^[ \t]+/, "", line)
            sub(/[ \t]+$/, "", line)
            if (line != "") print line
        }
    ' "$tmp/disasm.txt"
}

status=0
for pair in "ksu_ctx_write:yusu_ctx_write" "ksu_status_open:yusu_status_open"; do
    a="${pair%%:*}"
    b="${pair##*:}"
    extract "$a" > "$tmp/$a.s"
    extract "$b" > "$tmp/$b.s"

    if diff -q "$tmp/$a.s" "$tmp/$b.s" >/dev/null; then
        echo "IDENTICAL  $a  ==  $b  ($(wc -l < "$tmp/$a.s" | tr -d ' ') instrs)"
    else
        echo "DIFFERENT  $a  !=  $b"
        diff -u "$tmp/$a.s" "$tmp/$b.s" || true
        status=1
    fi
done

exit "$status"
