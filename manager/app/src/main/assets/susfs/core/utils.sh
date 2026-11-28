#!/system/bin/sh
# Native SuSFS LKM helper for YuSU Manager.
# Adapted from ReSuSFS (GPLv3) by ahmed-alnassif; LKM assets from susfs4ksu-lkm.
# KMI selection mirrors the upstream susfs4ksu-lkm loader:
#   kmi_override > kernel release (/proc/sys/kernel/osrelease) > ksud boot-info current-kmi
# /proc is used instead of uname because SuSFS itself can spoof uname -r.

PATH=/data/adb/ksu/bin:/system/bin:/system/xbin:$PATH

NATIVE_DIR=/data/adb/ksu/susfs
export SUSFS_LKM="/data/adb/ksu/susfs/lkm"
CUSTOM_LKM_DIR="$NATIVE_DIR/lkm/custom"
KMI_OVERRIDE_FILE="$NATIVE_DIR/kmi_override"
SUPPORTED_KMIS="android12-5.10 android13-5.10 android13-5.15 android14-5.15 android14-6.1 android15-6.6 android16-6.12 android17-6.18"

check_susfs_support() {
    [ -f /proc/config.gz ] || return 1
    zcat /proc/config.gz 2>/dev/null | grep -q '^CONFIG_KSU_SUSFS=y'
}

kernel_release() {
    rel="$(head -n 1 /proc/sys/kernel/osrelease 2>/dev/null)"
    [ -n "$rel" ] || rel="$(uname -r 2>/dev/null | head -n 1)"
    printf '%s\n' "$rel"
}

normalize_kmi() {
    case "$1" in
    android12-5.10|android13-5.10|android13-5.15|android14-5.15|android14-6.1|android15-6.6|android16-6.12|android17-6.18)
        printf '%s\n' "$1"
        return 0
        ;;
    esac
    return 1
}

# Match "5.10.x-android12-..." first. A tag-less kernel is only accepted when
# its series maps to exactly one packaged KMI (5.10/5.15 are shared, so refused).
kmi_from_release() {
    rel="$1"

    for kmi in $SUPPORTED_KMIS; do
        os="${kmi%-*}"
        ver="${kmi#*-}"
        case "$rel" in
        "$ver".*-"$os"-*|"$ver".*-"$os")
            printf '%s\n' "$kmi"
            return 0
            ;;
        esac
    done

    case "$rel" in
    *android[0-9]*) return 1 ;;
    esac

    match=""
    n=0
    for kmi in $SUPPORTED_KMIS; do
        ver="${kmi#*-}"
        case "$rel" in
        "$ver".*) patch="${rel#"$ver".}" ;;
        *) continue ;;
        esac
        patch="${patch%%-*}"
        case "$patch" in
        ''|*[!0-9]*) continue ;;
        esac
        match="$kmi"
        n=$((n + 1))
    done
    [ "$n" = 1 ] || return 1
    printf '%s\n' "$match"
}

kmi_from_ksud() {
    for candidate in /data/adb/ksu/bin/ksud ksud; do
        command -v "$candidate" >/dev/null 2>&1 || continue
        out="$("$candidate" boot-info current-kmi 2>/dev/null)" || continue
        kmi="$(printf '%s\n' "$out" | tr -d '\r' | sed -n '1p')"
        normalize_kmi "$kmi" && return 0
    done
    return 1
}

select_kmi() {
    if [ -s "$KMI_OVERRIDE_FILE" ]; then
        override="$(head -n 1 "$KMI_OVERRIDE_FILE" | tr -d '\r' | tr -d '[:space:]')"
        if normalize_kmi "$override" >/dev/null 2>&1; then
            printf '%s\n' "$override"
            return 0
        fi
    fi

    rel="$(kernel_release)"
    kmi_from_release "$rel" && return 0
    kmi_from_ksud && return 0
    return 1
}

# Echoes the .ko filename and exports SUSFS_KMI for diagnostics.
get_susfs_guard_lkm() {
    kmi="$(select_kmi)" || {
        echo "[!] Unsupported KMI: $(kernel_release)" >&2
        return 1
    }
    SUSFS_KMI="$kmi"
    printf '%s\n' "susfs_guard_lkm-$kmi.ko"
}

load_susfs_lkm() {
    # Android mksh supports local; ShellCheck assumes strict POSIX sh here.
    # shellcheck disable=SC3043
    local lkm_name lkm_file insmod_bin loaded base rel

    if check_susfs_support; then
        echo "[+] Kernel has built-in SuSFS support, skipping LKM load"
        return 0
    fi

    rel="$(kernel_release)"
    lkm_name="$(get_susfs_guard_lkm)" || return 1
    echo "[+] kernel $rel -> ${SUSFS_KMI:-unknown} ($lkm_name)"

    # Custom user-supplied module wins. The upstream module layout is accepted too.
    lkm_file=""
    for base in "$CUSTOM_LKM_DIR" "$SUSFS_LKM" "$NATIVE_DIR/lkm" "/data/adb/modules/susfs_guard_lkm/lib"; do
        if [ -s "$base/$lkm_name" ]; then
            lkm_file="$base/$lkm_name"
            break
        fi
    done
    insmod_bin="$NATIVE_DIR/bin/susfs_insmod"

    if [ -z "$lkm_file" ]; then
        echo "[!] Bundled LKM not found: $lkm_name" >&2
        return 1
    fi

    busybox rmmod susfs_guard_lkm 2>/dev/null
    loaded=0
    if command -v ksud >/dev/null 2>&1; then
        if ksud insmod "$lkm_file"; then
            echo "[+] SuSFS LKM loaded via ksud"
            loaded=1
        else
            echo "[-] ksud insmod failed"
        fi
    fi
    if [ "$loaded" -eq 0 ] && [ -x "$insmod_bin" ]; then
        if "$insmod_bin" "$lkm_file"; then
            echo "[+] SuSFS LKM loaded via susfs_insmod"
            loaded=1
        else
            echo "[-] susfs_insmod failed"
        fi
    fi
    [ "$loaded" -eq 1 ] || return 1
    return 0
}

# EOF
