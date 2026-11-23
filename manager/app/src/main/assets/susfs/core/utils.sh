#!/system/bin/sh
# Native SuSFS LKM helper for YuSU Manager.
# Adapted from ReSuSFS (GPLv3) by ahmed-alnassif; LKM assets from susfs4ksu-lkm.

PATH=/data/adb/ksu/bin:/system/bin:/system/xbin:$PATH

NATIVE_DIR=/data/adb/ksu/susfs
export SUSFS_LKM="/data/adb/ksu/susfs/lkm"

check_susfs_support() {
    [ -f /proc/config.gz ] || return 1
    zcat /proc/config.gz 2>/dev/null | grep -q '^CONFIG_KSU_SUSFS=y'
}

get_susfs_guard_lkm() {
    local kmi
    kmi="$(uname -r | sed -E 's/^([0-9]+\.[0-9]+)\.[0-9]+-(android[0-9]+)-.*/\2-\1/')"
    case "$kmi" in
    android12-5.10) echo "susfs_guard_lkm-android12-5.10.ko" ;;
    android13-5.10) echo "susfs_guard_lkm-android13-5.10.ko" ;;
    android13-5.15) echo "susfs_guard_lkm-android13-5.15.ko" ;;
    android14-5.15) echo "susfs_guard_lkm-android14-5.15.ko" ;;
    android14-6.1) echo "susfs_guard_lkm-android14-6.1.ko" ;;
    android15-6.6) echo "susfs_guard_lkm-android15-6.6.ko" ;;
    android16-6.12) echo "susfs_guard_lkm-android16-6.12.ko" ;;
    android17-6.18) echo "susfs_guard_lkm-android17-6.18.ko" ;;
    *)
        echo "[!] Unsupported KMI: ${kmi:-unknown}" >&2
        return 1
        ;;
    esac
}

load_susfs_lkm() {
    local kmi lkm_file insmod_bin loaded

    if check_susfs_support; then
        echo "[+] Kernel has built-in SuSFS support, skipping LKM load"
        return 0
    fi

    kmi="$(get_susfs_guard_lkm)" || return 1
    lkm_file="$NATIVE_DIR/lkm/$kmi"
    [ -f "$lkm_file" ] || lkm_file="$SUSFS_LKM/$kmi"
    insmod_bin="$NATIVE_DIR/bin/susfs_insmod"

    if [ ! -f "$lkm_file" ]; then
        echo "[!] Bundled LKM not found: $kmi" >&2
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
