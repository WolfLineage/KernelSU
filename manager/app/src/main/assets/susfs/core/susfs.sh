#!/bin/sh
# Native SuSFS CLI for YuSU Manager. Ported from ReSuSFS (GPLv3).
PATH=/data/adb/ksu/bin:/system/bin:/system/xbin:$PATH
NATIVE_DIR="/data/adb/ksu/susfs"
MODDIR="$NATIVE_DIR"
PERSISTENT_DIR="/data/adb/ksu/susfs/config"
USER_SCRIPTS_DIR="$PERSISTENT_DIR/scripts"
POSTFS_SCRIPTS_FILE="$PERSISTENT_DIR/scripts_postfs.txt"
BOOTCOMPLETED_SCRIPTS_FILE="$PERSISTENT_DIR/scripts_bootcompleted.txt"
SUSFS_BIN=/data/adb/ksu/bin/ksu_susfs
# Exported for hook scripts that source this CLI; not referenced inside this file.
export SUSFS_MIN_VERSION="v2.2.0"
ENGINE_VERSION="native-10"

# Runtime path under /data/adb; ShellCheck cannot resolve it from the repo.
# shellcheck disable=SC1091
. "$NATIVE_DIR/core/utils.sh"

[ -n "$WEBUI_QUIET" ] && [ "${NO_BANNER:-0}" = "0" ] && echo "YuSU native SuSFS $ENGINE_VERSION"

susfs() { "$SUSFS_BIN" "$@"; }

[ ! -d "$PERSISTENT_DIR" ] && mkdir -p "$PERSISTENT_DIR"

get_conf() {
	file="${3:-$PERSISTENT_DIR/config.txt}"
	val=$(grep "^$1=" "$file" 2>/dev/null | tail -n1 | cut -d'=' -f2-)
	[ -z "$val" ] && val="$2"
	echo "$val"
}

read_list() {
	[ -f "$1" ] || return
	sed 's/#.*//' "$1" | grep -v '^[[:space:]]*$'
}

[ ! -d "$USER_SCRIPTS_DIR" ] && mkdir -p "$USER_SCRIPTS_DIR"
[ ! -f "$POSTFS_SCRIPTS_FILE" ] && : > "$POSTFS_SCRIPTS_FILE"
[ ! -f "$BOOTCOMPLETED_SCRIPTS_FILE" ] && : > "$BOOTCOMPLETED_SCRIPTS_FILE"

CRON_SCRIPTS_FILE="$PERSISTENT_DIR/scripts_cron.txt"
[ ! -f "$CRON_SCRIPTS_FILE" ] && : > "$CRON_SCRIPTS_FILE"
CROND_DIR="$PERSISTENT_DIR/crontabs"

run_stage_scripts() {
	stage_file="$1"
	[ -f "$stage_file" ] || return
	list=$(read_list "$stage_file") || return
	[ -z "$list" ] && return
	echo "$list" | while IFS= read -r name; do
		case "$name" in
			"!"*) continue ;;
		esac
		script="$USER_SCRIPTS_DIR/$name"
		if [ -f "$script" ]; then
			echo "[>] running $script"
			sh "$script"
		else
			echo "[!] script not found, skipping: $name"
		fi
	done
}

sync_cron_scripts() {
	[ ! -d "$CROND_DIR" ] && mkdir -p "$CROND_DIR"

	pkill -f "busybox crond -bc $CROND_DIR" 2>/dev/null
	sleep 1

	busybox crond -bc "$CROND_DIR" -L /dev/null

	tmp="${CROND_DIR}/root.tmp.$$"
	: > "$tmp"

	list=$(read_list "$CRON_SCRIPTS_FILE") || { rm -f "$tmp"; return; }
	echo "$list" | while IFS= read -r line; do
		schedule=$(echo "$line" | cut -d' ' -f1-5)
		name=$(echo "$line" | cut -d' ' -f6-)
		[ -z "$name" ] && continue
		[ -f "$USER_SCRIPTS_DIR/$name" ] || { echo "[!] cron script not found, skipping: $name"; continue; }
		echo "$schedule { echo \"[\$(date '+%Y-%m-%d %H:%M:%S')] START $name\"; sh $MODDIR/ReSuSFS.sh --run-script $USER_SCRIPTS_DIR/$name; echo \"[\$(date '+%Y-%m-%d %H:%M:%S')] END $name exit=\$?\"; } >> $PERSISTENT_DIR/cron.log 2>&1" >> "$tmp"
	done

	busybox crontab -c "$CROND_DIR" "$tmp" 2>/dev/null
	rm -f "$tmp"

	echo "[+] cron schedule synced"
}

run_script() {
	script="$1"
	[ -z "$script" ] && { echo "[x] no script specified"; echo "[!] syntax: --run-script <path>"; exit 1; }
	case "$script" in
		*.sh) : ;;
		*) echo "[x] only .sh files allowed: $script"; exit 1 ;;
	esac
	[ -f "$script" ] || { echo "[x] script not found: $script"; exit 1; }
	[ -r "$script" ] || { echo "[x] script not readable: $script"; exit 1; }
	echo "[>] running $script"
	sh "$script"
	echo "[*] exit code: $?"
}

apply_list() {
	default="$1"; cmd="$2"; mode="$3"; file="${4:-$default}"

	list=$(read_list "$file") || return
	[ -z "$list" ] && return
	echo "$list" | busybox awk '!seen[$0]++' | while IFS= read -r p; do
		if [ "$mode" = "1" ] && [ ! -e "$p" ]; then echo "[!] skip missing path: $p"; continue; fi
		if [ "$mode" = "2" ] && [ ! -e "$p" ]; then continue; fi
		echo "[>] $cmd $p"
		susfs "$cmd" "$p"
	done
}

apply_sus_paths() { apply_list "$PERSISTENT_DIR/sus_paths.txt" add_sus_path 1 "${1:-$PERSISTENT_DIR/sus_paths.txt}"; }
apply_sus_paths_loop() { apply_list "$PERSISTENT_DIR/sus_paths_loop.txt" add_sus_path_loop 0 "${1:-$PERSISTENT_DIR/sus_paths_loop.txt}"; }
apply_sus_maps() { apply_list "$PERSISTENT_DIR/sus_maps.txt" add_sus_map 1 "${1:-$PERSISTENT_DIR/sus_maps.txt}"; }

apply_kstat() {
	mode="$1"
	file="${2:-$PERSISTENT_DIR/kstat_paths.txt}"
	list=$(read_list "$file") || return
	[ -z "$list" ] && return
	echo "$list" | busybox awk '!seen[$0]++' | while IFS= read -r line; do
		path=$(echo "$line" | awk '{print $1}')
		[ -z "$path" ] && continue
		args=$(echo "$line" | cut -d' ' -f2-)
		case "$mode" in
			add)
				if [ -n "$args" ]; then
					echo "[>] add_sus_kstat_statically $path $args"
					# $args is a list of CLI flags; word splitting is intentional.
					# shellcheck disable=SC2086
					susfs add_sus_kstat_statically "$path" $args
				else
					echo "[>] add_sus_kstat $path"
					susfs add_sus_kstat "$path"
				fi
				;;
			update)
				echo "[>] update_sus_kstat $path"
				susfs update_sus_kstat "$path"
				;;
		esac
	done
}

apply_kstat_add() { apply_kstat add "$1"; }
apply_kstat_update() { apply_kstat update "$1"; }

apply_open_redirect() {
	file="${1:-$PERSISTENT_DIR/open_redirect.txt}"
	list=$(read_list "$file") || return
	[ -z "$list" ] && return
	echo "$list" | busybox awk '!seen[$0]++' | while IFS= read -r line; do
		target=$(echo "$line" | awk '{print $1}')
		redirect=$(echo "$line" | awk '{print $2}')
		scheme=$(echo "$line" | awk '{print $3}')
		if [ -z "$target" ] || [ -z "$redirect" ] || [ -z "$scheme" ]; then continue; fi
		if [ ! -e "$target" ] || [ ! -e "$redirect" ]; then
			echo "[!] skip: $target -> $redirect (missing endpoint)"
			continue
		fi
		echo "[>] add_open_redirect $target -> $redirect (scheme $scheme)"
		susfs add_open_redirect "$target" "$redirect" "$scheme"
	done
}

apply_uname() {
	file="${1:-$PERSISTENT_DIR/uname.txt}"
	case "$file" in
		*.txt) : ;;
		*) echo "[x] only .txt files allowed: $file"; return 1 ;;
	esac
	[ -f "$file" ] || { echo "[x] file not found: $file"; return 1; }
	[ -r "$file" ] || { echo "[x] file not readable: $file"; return 1; }

	release=$(grep "^release=" "$file" | tail -n1 | cut -d'=' -f2-)
	version=$(grep "^version=" "$file" | tail -n1 | cut -d'=' -f2-)

	[ -z "$release" ] && { echo "[x] release missing in $file"; return 1; }
	[ -z "$version" ] && { echo "[x] version missing in $file"; return 1; }

	echo "[>] set_uname '$release' '$version'"
	susfs set_uname "$release" "$version"
}

apply_cmdline_bootconfig() {
	file="${1:-$PERSISTENT_DIR/cmdline_or_bootconfig.txt}"
	case "$file" in
		*.txt) : ;;
		*) echo "[x] only .txt files allowed: $file"; return 1 ;;
	esac
	[ -f "$file" ] || { echo "[x] file not found: $file"; return 1; }
	[ -s "$file" ] || return
	size=$(wc -c < "$file")
	if [ "$size" -gt 4096 ]; then
		echo "[x] cmdline/bootconfig file too large: $size bytes (max 4096)"
		return 1
	fi
	echo "[>] set_cmdline_or_bootconfig $file"
	susfs set_cmdline_or_bootconfig "$file"
}

apply_toggles() {
	stage="$1"
	file="${2:-$PERSISTENT_DIR/config.txt}"
	[ -f "$file" ] || return
	if [ "$stage" = "early" ]; then
		hide_mnts=$(get_conf HIDE_SUS_MNTS_NON_SU "" "$file")
		enable_log=$(get_conf ENABLE_LOG "" "$file")
		avc_spoof=$(get_conf ENABLE_AVC_LOG_SPOOFING "" "$file")

		[ -n "$hide_mnts" ] && { echo "[>] hide_sus_mnts_for_non_su_procs $hide_mnts (early)"; susfs hide_sus_mnts_for_non_su_procs "$hide_mnts"; }
		[ -n "$enable_log" ] && { echo "[>] enable_log $enable_log"; susfs enable_log "$enable_log"; }
		[ -n "$avc_spoof" ] && { echo "[>] enable_avc_log_spoofing $avc_spoof"; susfs enable_avc_log_spoofing "$avc_spoof"; }
	elif [ "$stage" = "late" ]; then
		hide_mnts=$(get_conf HIDE_SUS_MNTS_NON_SU "" "$file")
		[ -n "$hide_mnts" ] && { echo "[>] hide_sus_mnts_for_non_su_procs $hide_mnts (late)"; susfs hide_sus_mnts_for_non_su_procs "$hide_mnts"; }
	fi
}

stage_early() {
	echo "[+] stage: early (post-fs-data)"
	apply_kstat_add
	apply_uname
	apply_open_redirect
	apply_cmdline_bootconfig
	apply_toggles early
}

stage_late() {
	echo "[+] stage: late (boot-completed)"
	apply_sus_paths
	apply_sus_paths_loop
	apply_sus_maps
	apply_kstat_update
	apply_toggles late
	sync_cron_scripts
	status_report
}

run() {
	stage_early
	stage_late
}

action() { run; }

show_status() {
	susfs_version=$(susfs show version 2>/dev/null | head -n1)
	echo "$susfs_version"
	susfs show variant
	susfs show enabled_features
}

status_report() {
	# Refresh the live status line consumed by the manager's status page.
	# Keep it silent: boot hooks redirect this into last-load.log.
	# Android mksh supports local; ShellCheck assumes strict POSIX sh here.
	# shellcheck disable=SC3043
	local ver variant features stamp
	ver=$(susfs show version 2>/dev/null | head -n1)
	variant=$(susfs show variant 2>/dev/null | head -n1)
	features=$(susfs show enabled_features 2>/dev/null | grep -c .)
	# Do not record CLI diagnostics as a healthy status.
	case "$ver$variant" in
		*"not supported"*|*'[-]'*) return 0 ;;
	esac
	[ -z "$ver" ] && return 0
	stamp=$(date '+%a %b %e %H:%M:%S %Z %Y' 2>/dev/null)
	printf 'status: active | susfs %s (%s) | features: %s | %s\n' \
		"$ver" "${variant:-unknown}" "$features" "$stamp" > "$NATIVE_DIR/status.txt"
}

show_help () {
	banner
	echo "[%] $( grep '^description=' $MODDIR/module.prop | sed 's/description=//' )"
	echo "usage:"
	printf " --action \t\t\t\tfull apply (early+late stage)\n"
	printf " --stage-early \t\t\t\tpost-fs-data stage only\n"
	printf " --stage-late \t\t\t\tboot-completed stage only\n"
	printf " --status \t\t\t\tshow susfs version / variant / enabled features\n"
	printf " --status-report \t\t\tsilently refresh module.prop's live status line\n"
	printf "\n"
	printf "if [file] is given it is appended (deduped) into the default list, then applied:\n"
	printf " --apply-sus-paths [file] \t\tadd_sus_path from list\n"
	printf " --apply-sus-paths-loop [file] \t\tadd_sus_path_loop from list\n"
	printf " --apply-sus-maps [file] \t\tadd_sus_map from list\n"
	printf " --apply-kstat-add [file] \t\tstage add_sus_kstat from list\n"
	printf " --apply-kstat-update [file] \t\tcommit update_sus_kstat from list\n"
	printf " --apply-open-redirect [file] \t\tadd_open_redirect from list\n"
	printf " --apply-uname [file] \t\t\tset_uname from config\n"
	printf " --apply-cmdline-bootconfig [file] \tset_cmdline_or_bootconfig from file\n"
	printf " --apply-toggles <early|late> [file] \tapply hide_sus_mnts/enable_log/avc_log_spoofing from config\n"
	printf " --run-script <file> \t\t\trun a user script from UserHub\n"
	printf " --run-postfs-scripts \t\t\trun all UserHub scripts flagged for post-fs-data\n"
	printf " --run-bootcompleted-scripts \t\trun all UserHub scripts flagged for boot-completed\n"
	printf " --sync-cron-scripts \t\t\trebuild the cron schedule from scripts_cron.txt\n"
	printf "\n"
	printf " --help \t\t\t\tdisplays this message\n"
}

case "$1" in
	--load-lkm) load_susfs_lkm; exit $? ;;
	--action) action; exit ;;
	--stage-early) stage_early; exit ;;
	--stage-late) stage_late; exit ;;
	--status) show_status; exit ;;
	--status-report) status_report; exit ;;
	--apply-sus-paths) apply_sus_paths "$2"; exit ;;
	--apply-sus-paths-loop) apply_sus_paths_loop "$2"; exit ;;
	--apply-sus-maps) apply_sus_maps "$2"; exit ;;
	--apply-kstat-add) apply_kstat_add "$2"; exit ;;
	--apply-kstat-update) apply_kstat_update "$2"; exit ;;
	--apply-open-redirect) apply_open_redirect "$2"; exit ;;
	--apply-uname) apply_uname "$2"; exit ;;
	--apply-cmdline-bootconfig) apply_cmdline_bootconfig "$2"; exit ;;
	--apply-toggles) apply_toggles "$2" "$3"; exit ;;
	--run-script) shift; run_script "$1"; exit ;;
	--run-postfs-scripts) run_stage_scripts "$POSTFS_SCRIPTS_FILE"; exit ;;
	--run-bootcompleted-scripts) run_stage_scripts "$BOOTCOMPLETED_SCRIPTS_FILE"; exit ;;
	--sync-cron-scripts) sync_cron_scripts; exit ;;
	--help|*) show_help; exit ;;
esac

# EOF
