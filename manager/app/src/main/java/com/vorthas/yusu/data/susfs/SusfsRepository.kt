package com.vorthas.yusu.data.susfs

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.vorthas.yusu.BuildConfig
import com.vorthas.yusu.ksuApp
import java.io.File

private const val NATIVE_DIR = "/data/adb/ksu/susfs"
private const val PERSISTENT_DIR = "/data/adb/ksu/susfs/config"
private const val LEGACY_CONFIG_DIR = "/data/adb/ReSuSFS"
private const val LEGACY_LKM_DIR = "/data/adb/ReSuSFS_LKM"
private const val CLI = "/data/adb/ksu/bin/ReSuSFS"
private const val SUSFS_BIN = "/data/adb/ksu/bin/ksu_susfs"
private const val HOOKS_DIR = "/data/adb/ksu/susfs/hooks"
private const val POST_FS_HOOK = "/data/adb/ksu/susfs/hooks/post-fs-data.d/zz_yusu_susfs.sh"
private const val BOOT_HOOK = "/data/adb/ksu/susfs/hooks/boot-completed.d/zz_yusu_susfs.sh"
private const val POST_FS_LINK = "/data/adb/post-fs-data.d"
private const val BOOT_LINK = "/data/adb/boot-completed.d"
private const val SERVICE_LINK = "/data/adb/service.d"
private const val KMI_OVERRIDE_FILE = "$NATIVE_DIR/kmi_override"
private const val CUSTOM_LKM_DIR = "$NATIVE_DIR/lkm/custom"
private const val ENGINE_MARKER = "native-10"

/** Status of the manager-owned SuSFS LKM integration. */
data class SusfsStatus(
    val installed: Boolean,
    val integrated: Boolean,
    val active: Boolean,
    val cmdMismatch: Boolean,
    val builtInKernel: Boolean,
    val lkmLoaded: Boolean,
    val kernelRelease: String,
    val kmi: String,
    val lkmName: String?,
    val lkmPresent: Boolean,
    val version: String?,
    val variant: String?,
    val featureCount: Int,
    val hideMounts: Boolean,
    val logging: Boolean,
    val avcSpoof: Boolean,
    val statusLine: String?,
    val loadLog: String?,
)

data class SusfsScriptState(
    val name: String,
    val postFsData: Boolean,
    val bootCompleted: Boolean,
)

/** Native SuSFS integration: assets are bundled in the APK, no flashable module is used. */
object SusfsRepository {
    private const val TAG = "SusfsRepo"
    private const val PREFS = "settings"
    private const val KEY_INTEGRATION = "susfs_integration_enabled"

    /** Matches the upstream susfs4ksu-lkm build matrix; do not add entries without a bundled .ko. */
    private val supportedKmis = listOf(
        "android12-5.10",
        "android13-5.10",
        "android13-5.15",
        "android14-5.15",
        "android14-6.1",
        "android15-6.6",
        "android16-6.12",
        "android17-6.18",
    )

    private val configFiles = listOf(
        "config.txt",
        "uname.txt",
        "sus_paths.txt",
        "sus_paths_loop.txt",
        "sus_maps.txt",
        "kstat_paths.txt",
        "open_redirect.txt",
        "cmdline_or_bootconfig.txt",
        "scripts_postfs.txt",
        "scripts_bootcompleted.txt",
        "scripts_cron.txt",
    )

    private val builtInScripts = listOf(
        "ReSuSFS_apply-cmdline-bootconfig.sh",
        "ReSuSFS_apply-kstat-add.sh",
        "ReSuSFS_apply-ksu-settings.sh",
        "ReSuSFS_apply-uname.sh",
        "ReSuSFS_apply-mount-hiding.sh",
        "ReSuSFS_apply-props.sh",
        "ReSuSFS_apply-settings.sh",
        "ReSuSFS_apply-sus-maps.sh",
        "ReSuSFS_apply-sus-paths-loop.sh",
        "ReSuSFS_apply-sus-paths.sh",
        "ReSuSFS_cleanup-markers.sh",
    )

    private val toggleKeys = setOf("HIDE_SUS_MNTS_NON_SU", "ENABLE_LOG", "ENABLE_AVC_LOG_SPOOFING")

    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    private fun exec(vararg cmd: String): Shell.Result {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        return Shell.getShell().newJob().add(*cmd).to(out, err).exec()
    }

    private fun output(vararg cmd: String): String = exec(*cmd).out.joinToString("\n").trim()

    fun isRoot(): Boolean = runCatching { Shell.getShell().isRoot }.getOrDefault(false)

    /**
     * ksu_susfs prints diagnostics to stdout, including failure lines such as
     * "[-] CMD: '0x555e1', SUSFS operation not supported, please enable it in kernel".
     * Those lines must never be mistaken for a version, a variant, or a feature.
     */
    private fun isErrorLine(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("[-]") ||
            t.startsWith("[!]") ||
            t.startsWith("[x]") ||
            t.startsWith("usage:") ||
            t.contains("not supported", ignoreCase = true)
    }

    private fun isDiagnosticsLine(line: String): Boolean =
        line.isBlank() || isErrorLine(line)

    private fun cleanProbe(raw: String): String? =
        raw.lineSequence().map { it.trim() }.firstOrNull { !isDiagnosticsLine(it) }

    fun isIntegrationEnabled(): Boolean =
        ksuApp.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_INTEGRATION, true)

    /** Choose at LKM patch time whether the manager integrates SuSFS. */
    fun setIntegrationEnabled(enabled: Boolean): Result<String> = runCatching {
        ksuApp.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INTEGRATION, enabled).apply()
        if (enabled) {
            install().getOrThrow()
        } else {
            val result = exec("rm -f $POST_FS_LINK $BOOT_LINK $SERVICE_LINK $CLI")
            if (!result.isSuccess) error("Failed to disable SuSFS integration")
            "SuSFS integration disabled"
        }
    }.onFailure { Log.e(TAG, "setIntegrationEnabled failed", it) }

    /**
     * Map a kernel release to the KMI name used by susfs4ksu-lkm. The Android tag
     * is authoritative; a missing tag is only accepted when the kernel series maps
     * to exactly one packaged KMI (5.10 and 5.15 are shared, so they stay ambiguous).
     */
    fun kmiOf(release: String): String? {
        val trimmed = release.trim()
        Regex("""^(\d+\.\d+)\.\d+-(android\d+)(?:-.*)?$""").find(trimmed)?.let { match ->
            val (majorMinor, android) = match.destructured
            return "$android-$majorMinor"
        }
        val version = Regex("""^(\d+\.\d+)\.\d+(?:\.|-|$)""").find(trimmed)
            ?.groupValues?.get(1) ?: return null
        return supportedKmis.singleOrNull { it.substringAfter('-') == version }
    }

    private fun normalizeKmi(value: String?): String? =
        value?.trim()?.takeIf { it in supportedKmis }

    /** The real kernel release. SuSFS can spoof uname -r, but not /proc/sys/kernel/osrelease. */
    private fun kernelRelease(): String =
        output("cat /proc/sys/kernel/osrelease 2>/dev/null").lineSequence()
            .map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: output("uname -r").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: System.getProperty("os.version").orEmpty()

    /** Optional upstream-compatible override: one KMI name in $KMI_OVERRIDE_FILE. */
    private fun kmiOverride(): String? =
        output("cat ${KMI_OVERRIDE_FILE.shellQuote()} 2>/dev/null")
            .lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.let(::normalizeKmi)

    private fun kmiFromKsud(): String? =
        output("/data/adb/ksu/bin/ksud boot-info current-kmi 2>/dev/null")
            .lineSequence().map { it.trim() }.firstNotNullOfOrNull(::normalizeKmi)

    /** The KMI actually used: explicit override > kernel release > ksud boot info. */
    private fun selectedKmi(): String? =
        kmiOverride() ?: kmiOf(kernelRelease())?.let(::normalizeKmi) ?: kmiFromKsud()

    private fun lkmNameFor(kmi: String?): String? =
        normalizeKmi(kmi)?.let { "susfs_guard_lkm-$it.ko" }

    /** User-provided modules win over the bundled copy; upstream module layout is also accepted. */
    private fun lkmCandidates(kmi: String?): List<String> {
        val name = lkmNameFor(kmi) ?: return emptyList()
        return listOf(
            "$CUSTOM_LKM_DIR/$name",
            "$NATIVE_DIR/lkm/$name",
            "/data/adb/modules/susfs_guard_lkm/lib/$name",
        )
    }

    private fun hasLkm(kmi: String?): Boolean =
        lkmCandidates(kmi).any { exec("test -s ${it.shellQuote()}").isSuccess }

    private fun copyAsset(assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        ksuApp.assets.open(assetPath).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** Extract the files needed by the current device to a private staging directory. */
    private fun stageAssets(kmi: String): File {
        val stage = File(ksuApp.cacheDir, "susfs-assets")
        if (stage.exists()) stage.deleteRecursively()

        copyAsset("susfs/core/utils.sh", File(stage, "core/utils.sh"))
        copyAsset("susfs/core/susfs.sh", File(stage, "core/susfs.sh"))
        copyAsset("susfs/bin/ksu_susfs", File(stage, "bin/ksu_susfs"))
        copyAsset("susfs/bin/susfs_insmod", File(stage, "bin/susfs_insmod"))

        configFiles.forEach { name ->
            runCatching { copyAsset("susfs/configs/$name", File(stage, "configs/$name")) }
        }
        builtInScripts.forEach { name ->
            copyAsset("susfs/configs/scripts/$name", File(stage, "configs/scripts/$name"))
        }

        val lkmName = lkmNameFor(kmi) ?: error("Unsupported KMI: $kmi")
        copyAsset("susfs/lkm/$lkmName", File(stage, "lkm/$lkmName"))

        val hooks = File(stage, "hooks")
        File(hooks, "post-fs-data.d").mkdirs()
        File(hooks, "boot-completed.d").mkdirs()
        File(hooks, "post-fs-data.d/zz_yusu_susfs.sh").writeText(
            """
            #!/system/bin/sh
            # Installed by YuSU Manager. Native SuSFS LKM integration.
            [ -x "$NATIVE_DIR/core/susfs.sh" ] || exit 0
            "$CLI" --load-lkm >>$NATIVE_DIR/last-load.log 2>&1
            "$CLI" --stage-early >>$NATIVE_DIR/last-load.log 2>&1
            "$CLI" --run-postfs-scripts >>$NATIVE_DIR/last-load.log 2>&1
            "$CLI" --status-report >>$NATIVE_DIR/last-load.log 2>&1
            exit 0
            """.trimIndent() + "\n"
        )
        File(hooks, "boot-completed.d/zz_yusu_susfs.sh").writeText(
            """
            #!/system/bin/sh
            # Installed by YuSU Manager. Native SuSFS LKM integration.
            [ -x "$NATIVE_DIR/core/susfs.sh" ] || exit 0
            "$CLI" --stage-late >/dev/null 2>&1
            "$CLI" --run-bootcompleted-scripts >/dev/null 2>&1
            exit 0
            """.trimIndent() + "\n"
        )
        return stage
    }

    /** The CLI symlink and its helper binary must both be present and executable. */
    private fun engineToolsReady(): Boolean =
        exec("test -x $SUSFS_BIN").isSuccess &&
            exec("test -x $NATIVE_DIR/core/susfs.sh").isSuccess &&
            exec("test -L $CLI").isSuccess

    private fun installedMarker(kmi: String): String = "${BuildConfig.VERSION_CODE}:$ENGINE_MARKER:$kmi"

    /** Accept both symlink hooks and copied-in hooks; install() supports both layouts. */
    private fun hookReady(link: String, hookFile: String? = null): Boolean {
        val linkExists = exec("test -e $link").isSuccess || exec("test -L $link").isSuccess
        return linkExists && (hookFile == null || exec("test -f $hookFile").isSuccess)
    }

    private fun isCurrent(): Boolean {
        val kmi = selectedKmi() ?: return false
        if (lkmNameFor(kmi) == null) return false
        return output("cat $NATIVE_DIR/.installed 2>/dev/null") == installedMarker(kmi) &&
            exec("test -x $NATIVE_DIR/core/susfs.sh").isSuccess &&
            exec("test -x $SUSFS_BIN").isSuccess &&
            exec("test -L $CLI").isSuccess &&
            hookReady(POST_FS_LINK, POST_FS_HOOK) &&
            hookReady(BOOT_LINK, BOOT_HOOK) &&
            hookReady(SERVICE_LINK) &&
            hasLkm(kmi)
    }

    /** Install or refresh the native engine and boot hooks. Existing user config is preserved. */
    fun install(): Result<String> = runCatching {
        if (!isRoot()) error("Root access is required")
        if (isCurrent()) return@runCatching "Native SuSFS engine is up to date"

        val release = kernelRelease()
        val kmi = selectedKmi() ?: error("Unsupported kernel KMI: $release")
        val lkmName = lkmNameFor(kmi) ?: error("No bundled LKM for $kmi")
        val stage = stageAssets(kmi)
        val src = stage.absolutePath

        val commands = mutableListOf<String>().apply {
            add("mkdir -p $NATIVE_DIR/core $NATIVE_DIR/bin $CUSTOM_LKM_DIR $PERSISTENT_DIR/scripts $HOOKS_DIR/post-fs-data.d $HOOKS_DIR/boot-completed.d $HOOKS_DIR/service.d")
            // Migrate user config from the legacy /data/adb/ReSuSFS and /data/adb/yusu layout.
            add("if [ -d $LEGACY_CONFIG_DIR ]; then cp -Rf $LEGACY_CONFIG_DIR/. $PERSISTENT_DIR/; fi")
            add("cp -f $src/core/utils.sh $NATIVE_DIR/core/utils.sh")
            add("cp -f $src/core/susfs.sh $NATIVE_DIR/core/susfs.sh")
            add("chmod 755 $NATIVE_DIR/core/utils.sh $NATIVE_DIR/core/susfs.sh")
            add("cp -f $src/bin/ksu_susfs $SUSFS_BIN")
            add("chmod 755 $SUSFS_BIN")
            add("cp -f $src/bin/susfs_insmod $NATIVE_DIR/bin/susfs_insmod")
            add("chmod 755 $NATIVE_DIR/bin/susfs_insmod")
            // Never delete a user-supplied .ko: the loader matches by current KMI,
            // and a manually downloaded module is often the only working one.
            add("if [ ! -s $NATIVE_DIR/lkm/$lkmName ]; then cp -f $src/lkm/$lkmName $NATIVE_DIR/lkm/$lkmName; fi")
            add("chmod 644 $NATIVE_DIR/lkm/$lkmName $CUSTOM_LKM_DIR/$lkmName 2>/dev/null || true")
            add("ln -sfn $NATIVE_DIR/core/susfs.sh $CLI")
            add("cp -f $src/hooks/post-fs-data.d/zz_yusu_susfs.sh $POST_FS_HOOK")
            add("cp -f $src/hooks/boot-completed.d/zz_yusu_susfs.sh $BOOT_HOOK")
            add("chmod 755 $POST_FS_HOOK $BOOT_HOOK")
            add("if [ -L $POST_FS_LINK ]; then rm -f $POST_FS_LINK; fi")
            add("if [ -d $POST_FS_LINK ]; then rm -f $POST_FS_LINK/zz_yusu_susfs.sh; if [ -z \"\$(ls -A $POST_FS_LINK)\" ]; then rmdir $POST_FS_LINK; fi; fi")
            add("if [ ! -e $POST_FS_LINK ]; then ln -s $HOOKS_DIR/post-fs-data.d $POST_FS_LINK; elif [ -d $POST_FS_LINK ]; then cp -f $POST_FS_HOOK $POST_FS_LINK/zz_yusu_susfs.sh; chmod 755 $POST_FS_LINK/zz_yusu_susfs.sh; fi")
            add("if [ -L $BOOT_LINK ]; then rm -f $BOOT_LINK; fi")
            add("if [ -d $BOOT_LINK ]; then rm -f $BOOT_LINK/zz_yusu_susfs.sh; if [ -z \"\$(ls -A $BOOT_LINK)\" ]; then rmdir $BOOT_LINK; fi; fi")
            add("if [ ! -e $BOOT_LINK ]; then ln -s $HOOKS_DIR/boot-completed.d $BOOT_LINK; elif [ -d $BOOT_LINK ]; then cp -f $BOOT_HOOK $BOOT_LINK/zz_yusu_susfs.sh; chmod 755 $BOOT_LINK/zz_yusu_susfs.sh; fi")
            add("if [ -L $SERVICE_LINK ]; then rm -f $SERVICE_LINK; fi")
            add("if [ -d $SERVICE_LINK ] && [ -z \"\$(ls -A $SERVICE_LINK)\" ]; then rmdir $SERVICE_LINK; fi")
            add("if [ ! -e $SERVICE_LINK ]; then ln -s $HOOKS_DIR/service.d $SERVICE_LINK; fi")
            // Restore pristine defaults once when upgrading from a release that mutated them.
            add("if grep -q 'native-[1-9]' $NATIVE_DIR/.installed 2>/dev/null; then cp -f $src/configs/*.txt $PERSISTENT_DIR/; cp -f $src/configs/scripts/*.sh $PERSISTENT_DIR/scripts/; fi")
            add("printf '%s' '${installedMarker(kmi)}' > $NATIVE_DIR/.installed")
        }

        configFiles.forEach { name ->
            val default = File(stage, "configs/$name")
            if (default.exists()) {
                commands.add("if [ ! -f '$PERSISTENT_DIR/$name' ]; then cp -f ${src}/configs/$name '$PERSISTENT_DIR/$name'; fi; chmod 644 '$PERSISTENT_DIR/$name'")
            }
        }
        builtInScripts.forEach { name ->
            commands.add("if [ ! -f '$PERSISTENT_DIR/scripts/$name' ]; then cp -f ${src}/configs/scripts/$name '$PERSISTENT_DIR/scripts/$name'; chmod 644 '$PERSISTENT_DIR/scripts/$name'; fi")
        }
        commands.add("rm -rf $LEGACY_CONFIG_DIR $LEGACY_LKM_DIR /data/adb/yusu/susfs")
        commands.add("rmdir /data/adb/yusu 2>/dev/null || true")

        val failures = mutableListOf<String>()
        commands.forEach { cmd ->
            val result = exec(cmd)
            if (!result.isSuccess) failures += "$cmd -> ${result.err.firstOrNull().orEmpty()}"
        }
        if (failures.isNotEmpty()) error("Install failed: ${failures.take(3).joinToString("; ")}")

        "Installed native SuSFS engine for $kmi ($lkmName)"
    }.onFailure { Log.e(TAG, "install failed", it) }

    fun status(): SusfsStatus {
        val release = kernelRelease()
        val kmi = selectedKmi().orEmpty()
        val lkmName = lkmNameFor(kmi)
        val installed = exec("test -f $NATIVE_DIR/.installed").isSuccess
        val integrated = hookReady(POST_FS_LINK, POST_FS_HOOK)
        val builtIn = exec("zcat /proc/config.gz 2>/dev/null | grep -q '^CONFIG_KSU_SUSFS=y'").isSuccess
        val versionRaw = output("$SUSFS_BIN show version 2>/dev/null")
        val variantRaw = output("$SUSFS_BIN show variant 2>/dev/null")
        val featuresRaw = output("$SUSFS_BIN show enabled_features 2>/dev/null")
        val version = cleanProbe(versionRaw)
        val variant = cleanProbe(variantRaw)
        val featureCount = featuresRaw.lineSequence().count { !isDiagnosticsLine(it) }
        // Only flag a mismatch when the CLI actually printed an error/diagnostic line.
        // An empty probe (CLI not installed / no root) is NOT an ABI mismatch.
        val cmdMismatch = sequenceOf(versionRaw, variantRaw, featuresRaw)
            .flatMap { it.lineSequence() }
            .any { isErrorLine(it) }
        // /proc/modules hides the guard module, so the ksu_susfs CLI is the authoritative probe.
        val active = version != null && featureCount > 0
        val lkmLoaded = active && !builtIn
        val values = configValues()
        return SusfsStatus(
            installed = installed,
            integrated = integrated,
            active = active,
            cmdMismatch = cmdMismatch,
            builtInKernel = builtIn,
            lkmLoaded = lkmLoaded,
            kernelRelease = release,
            kmi = kmi,
            lkmName = lkmName,
            lkmPresent = hasLkm(kmi),
            version = version,
            variant = variant,
            featureCount = featureCount,
            hideMounts = values["HIDE_SUS_MNTS_NON_SU"] == true,
            logging = values["ENABLE_LOG"] == true,
            avcSpoof = values["ENABLE_AVC_LOG_SPOOFING"] == true,
            statusLine = output("cat $NATIVE_DIR/status.txt 2>/dev/null").takeIf { it.isNotBlank() },
            loadLog = output("tail -n 20 $NATIVE_DIR/last-load.log 2>/dev/null").takeIf { it.isNotBlank() },
        )
    }

    private fun configValues(): Map<String, Boolean> {
        val text = output("cat '$PERSISTENT_DIR/config.txt' 2>/dev/null")
        return text.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) return@mapNotNull null
            val parts = trimmed.split("=", limit = 2)
            if (parts.size != 2 || parts[0] !in toggleKeys) return@mapNotNull null
            parts[0] to (parts[1].trim() == "1")
        }.toMap()
    }

    fun setToggle(key: String, enabled: Boolean): Result<String> = runCatching {
        require(key in toggleKeys) { "Unknown toggle: $key" }
        val value = if (enabled) 1 else 0
        val file = "'$PERSISTENT_DIR/config.txt'"
        val update = exec(
            "if grep -q '^$key=' $file; then sed -i 's/^$key=.*/$key=$value/' $file; else echo '$key=$value' >> $file; fi"
        )
        if (!update.isSuccess) error("Failed to update $key")
        val apply = exec("$CLI --apply-toggles early")
        if (!apply.isSuccess) error("Failed to apply $key")
        apply.out.joinToString("\n").ifBlank { "Saved" }
    }.onFailure { Log.e(TAG, "setToggle failed", it) }

    fun loadLkm(): Result<String> = runCatching {
        val result = exec("$CLI --load-lkm")
        val text = (result.out + result.err).joinToString("\n").trim()
        if (!result.isSuccess) error(text.ifBlank { "Failed to load SuSFS LKM" })
        text.ifBlank { "LKM loaded" }
    }.onFailure { Log.e(TAG, "loadLkm failed", it) }

    /**
     * Close the first-boot gap after an LKM patch: the boot hook runs in post-fs-data,
     * before the manager has installed the engine on that very first boot, so it exits
     * early and the guard LKM is never loaded until the next reboot. When the engine is
     * already integrated but the kernel-side probe is inactive, load the LKM now.
     * No-op for built-in kernels, when already active, or when the engine is not ready.
     */
    fun ensureLoadedOnBoot(): Result<String?> = runCatching {
        if (!isRoot()) return@runCatching null
        val builtIn = exec("zcat /proc/config.gz 2>/dev/null | grep -q '^CONFIG_KSU_SUSFS=y'").isSuccess
        if (builtIn) return@runCatching null
        // A patched kernel may have a different KMI than when the engine was first
        // installed. If the matching guard LKM is missing, reinstall before loading.
        val kmi = selectedKmi()
        if (kmi == null || lkmNameFor(kmi) == null || !hasLkm(kmi)) {
            Log.i(TAG, "SuSFS LKM missing for current KMI ($kmi), reinstalling engine")
            install().getOrThrow()
        }
        if (!engineToolsReady()) return@runCatching null
        val version = cleanProbe(output("$SUSFS_BIN show version 2>/dev/null"))
        val features = output("$SUSFS_BIN show enabled_features 2>/dev/null")
            .lineSequence().count { !isDiagnosticsLine(it) }
        if (version != null && features > 0) return@runCatching null // already active
        val result = exec("$CLI --load-lkm")
        val text = (result.out + result.err).joinToString("\n").trim()
        if (!result.isSuccess) error(text.ifBlank { "Failed to load SuSFS LKM" })
        text.ifBlank { "LKM loaded on startup" }
    }.onFailure { Log.w(TAG, "ensureLoadedOnBoot skipped/failed", it) }

    fun applyNow(): Result<String> = runCatching {
        val result = exec("$CLI --action")
        val text = (result.out + result.err).joinToString("\n").trim()
        if (!result.isSuccess) error(text.ifBlank { "Failed to apply SuSFS config" })
        text.ifBlank { "Applied" }
    }.onFailure { Log.e(TAG, "applyNow failed", it) }

    fun editableConfigs(): List<String> = configFiles.filterNot { name ->
        name in setOf("scripts_postfs.txt", "scripts_bootcompleted.txt", "scripts_cron.txt")
    }

    fun readConfig(name: String): String = output("cat -- '$PERSISTENT_DIR/$name'")

    /** Only the effective lines of a list config, without documentation comments. */
    fun readActiveLines(name: String): String = readConfig(name).lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .joinToString("\n")

    /** Replace effective lines while keeping the documentation comments in the file. */
    fun saveActiveLines(name: String, text: String): Result<String> = runCatching {
        require(name in configFiles) { "Unknown config: $name" }
        val comments = readConfig(name).lineSequence()
            .filter { it.trim().isEmpty() || it.trim().startsWith("#") }
            .toList()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        val merged = (comments + lines).joinToString("\n", postfix = "\n")
        saveConfig(name, merged).getOrThrow()
    }.onFailure { Log.e(TAG, "saveActiveLines failed", it) }

    private fun applyConfig(name: String): Result<String> = runCatching {
        val commands = when (name) {
            "config.txt" -> listOf("$CLI --apply-toggles early")
            "uname.txt" -> listOf("$CLI --apply-uname")
            "cmdline_or_bootconfig.txt" -> listOf("$CLI --apply-cmdline-bootconfig")
            "sus_paths.txt" -> listOf("$CLI --apply-sus-paths")
            "sus_paths_loop.txt" -> listOf("$CLI --apply-sus-paths-loop")
            "sus_maps.txt" -> listOf("$CLI --apply-sus-maps")
            "kstat_paths.txt" -> listOf("$CLI --apply-kstat-add", "$CLI --apply-kstat-update")
            "open_redirect.txt" -> listOf("$CLI --apply-open-redirect")
            else -> listOf("$CLI --stage-early", "$CLI --stage-late")
        }
        val lines = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        commands.forEach { cmd ->
            val result = exec(cmd)
            lines += (result.out + result.err).joinToString("\n").trim()
            if (!result.isSuccess) warnings += (result.err + result.out).firstOrNull { it.isNotBlank() } ?: cmd
        }
        val summary = lines.filter { it.isNotBlank() }.joinToString("\n").ifBlank { "Applied $name" }
        if (warnings.isEmpty()) summary else "$summary\n(saved; kernel apply warning: ${warnings.first()})"
    }.onFailure { Log.e(TAG, "applyConfig failed", it) }

    fun saveConfig(name: String, text: String): Result<String> = runCatching {
        require(name in configFiles) { "Unknown config: $name" }
        val temp = File(ksuApp.cacheDir, "susfs-config-$name.tmp")
        temp.writeText(text)
        val result = exec("cp -f ${temp.absolutePath.shellQuote()} '$PERSISTENT_DIR/$name'")
        if (!result.isSuccess) error("Failed to write $name")
        applyConfig(name).getOrElse { "Saved $name (apply failed: ${it.message})" }
    }.onFailure { Log.e(TAG, "saveConfig failed", it) }

    fun scriptStates(): List<SusfsScriptState> {
        val post = output("cat '$PERSISTENT_DIR/scripts_postfs.txt' 2>/dev/null").lines().map { it.trim() }.toSet()
        val boot = output("cat '$PERSISTENT_DIR/scripts_bootcompleted.txt' 2>/dev/null").lines().map { it.trim() }.toSet()
        val present = output("ls -1 '$PERSISTENT_DIR/scripts' 2>/dev/null").lines().map { it.trim() }.filter { it.endsWith(".sh") }
        return (builtInScripts + present).distinct().sorted().map { name ->
            SusfsScriptState(name, post.contains(name), boot.contains(name))
        }
    }

    fun setScriptStage(name: String, stage: String, enabled: Boolean): Result<Unit> = runCatching {
        require(stage in setOf("postfs", "bootcompleted")) { "Unknown stage" }
        val file = if (stage == "postfs") "scripts_postfs.txt" else "scripts_bootcompleted.txt"
        val path = "$PERSISTENT_DIR/$file"
        val temp = File(ksuApp.cacheDir, "susfs-$file.tmp")
        val current = output("cat '$path' 2>/dev/null").lines().filter { it.isNotBlank() && it.trim() != name }
        val lines = if (enabled) current + name else current
        temp.writeText(lines.joinToString("\n", postfix = "\n"))
        val result = exec("cp -f ${temp.absolutePath.shellQuote()} '$path'")
        if (!result.isSuccess) error("Failed to update $file")
    }.onFailure { Log.e(TAG, "setScriptStage failed", it) }

    fun resetConfigs(): Result<Unit> = runCatching {
        val release = kernelRelease()
        val kmi = selectedKmi() ?: error("Unsupported KMI: $release")
        val stage = stageAssets(kmi)
        val failures = mutableListOf<String>()
        configFiles.forEach { name ->
            val src = File(stage, "configs/$name")
            if (src.exists()) {
                val result = exec("cp -f ${src.absolutePath.shellQuote()} '$PERSISTENT_DIR/$name'")
                if (!result.isSuccess) failures += name
            }
        }
        if (failures.isNotEmpty()) error("Failed to reset: ${failures.joinToString()}")
    }.onFailure { Log.e(TAG, "resetConfigs failed", it) }

}
