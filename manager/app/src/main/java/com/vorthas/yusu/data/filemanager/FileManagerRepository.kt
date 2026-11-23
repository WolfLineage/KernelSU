package com.vorthas.yusu.data.filemanager

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** A single row in a file-manager pane. */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mode: String,
    /** Raw symlink target (`ls -l` right-hand side) when this entry is a link. */
    val linkTarget: String? = null,
) {
    val isLink: Boolean get() = linkTarget != null
}

/** Result of an archive extraction, with the number of files written. */
data class ExtractResult(val files: Int, val skipped: Int)

/**
 * Content kind used to select a safe viewer. Sniffed from header bytes first; the file
 * name is only a fallback hint. Binary/archive files must never be handed to a text
 * field: replacing their NUL bytes produces millions of U+FFFD chars and Compose text
 * layout then blocks the main thread indefinitely.
 */
enum class FileKind {
    TEXT, ARCHIVE, ELF, DEX, IMAGE, PDF, SQLITE, BINARY,
}

/** One bounded read. Holds at most `bytes.size` bytes in memory. */
data class FilePage(
    val offset: Long,
    val totalBytes: Long,
    val bytes: ByteArray,
    val text: String,
    val kind: FileKind,
) {
    val atEnd: Boolean get() = offset + bytes.size >= totalBytes

    /** 16 bytes per row with an ASCII column; built only for binary pages. */
    fun hexDump(): String = buildString(bytes.size * 4) {
        for (rowStart in bytes.indices step 16) {
            append(String.format(Locale.ROOT, "%08X  ", offset + rowStart))
            for (i in 0 until 16) {
                val index = rowStart + i
                if (index < bytes.size) {
                    append(String.format(Locale.ROOT, "%02X", bytes[index].toInt() and 0xFF))
                } else {
                    append("  ")
                }
                append(' ')
                if (i == 7) append(' ')
            }
            append(" |")
            for (i in 0 until 16) {
                val index = rowStart + i
                if (index < bytes.size) {
                    val value = bytes[index].toInt() and 0xFF
                    append(if (value in 0x20..0x7E) value.toChar() else '.')
                } else {
                    append(' ')
                }
            }
            append("|\n")
        }
    }
}

/**
 * Root-aware file operations for the built-in file manager.
 *
 * Listing and stream IO go through libsu's SuFile/SuFileInputStream; mutations that would be
 * awkward through the File API (recursive delete, rename across mounts, chmod) go through the
 * existing privileged shell. Every path used in a shell command is single-quoted.
 */
object FileManagerRepository {
    private const val TAG = "FileManagerRepo"

    fun defaultLeft(): String = "/sdcard"

    fun defaultRight(): String = "/data/adb"

    /** POSIX-style single-quote, safe for interpolation into a shell command. */
    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    /**
     * Shell for all file-manager operations: libsu's DEFAULT root shell.
     *
     * KsuCli's custom shells (`libysd.so debug su [-g]`) went silently dead on the test
     * device (every exec returned code=0 with EMPTY stdout, even for `echo hello`) after
     * repeated self-reinstall churn, while libsu's default shell (`/system/bin/su`) kept
     * working — SuFile fallback listings never broke. All mount namespaces on the device
     * are identical (single mnt ns), so the global-mnt variant has no value here anyway.
     */
    private fun fmShell(): Shell = Shell.getShell()

    /** Bind SuFile to the default root shell so all checks/IO use a privileged session. */
    private fun rootFile(path: String): SuFile = SuFile(path).apply { setShell(fmShell()) }

    private fun modeOf(file: SuFile): String = buildString {
        append(if (file.isDirectory) 'd' else '-')
        append(if (file.canRead()) 'r' else '-')
        append(if (file.canWrite()) 'w' else '-')
        append(if (file.canExecute()) 'x' else '-')
    }

    fun joinPath(directory: String, name: String): String = directory.trimEnd('/') + "/" + name

    fun parent(path: String): String {
        val normalized = path.trimEnd('/').ifEmpty { "/" }
        val slash = normalized.lastIndexOf('/')
        return if (slash <= 0) "/" else normalized.substring(0, slash)
    }

    /** Raw `readlink` target, or null when [path] is not a symlink. */
    fun readLink(path: String): String? = runCatching {
        exec("readlink -- ${path.shellQuote()}").out.firstOrNull()?.trim()?.ifBlank { null }
    }.getOrDefault(null)

    /** Lexically resolve `.`/`..` without touching the filesystem. */
    private fun normalizePath(path: String): String {
        val absolute = path.startsWith("/")
        val parts = mutableListOf<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty() && parts.last() != "..") {
                    parts.removeAt(parts.lastIndex)
                } else if (!absolute) {
                    parts.add("..")
                }
                else -> parts.add(part)
            }
        }
        return (if (absolute) "/" else "") + parts.joinToString("/")
    }

    /**
     * Follow a symlink chain to its final path. Relative targets are resolved against the
     * link's parent directory. Bounded depth protects against link loops.
     */
    fun resolveLink(path: String, maxDepth: Int = 16): String {
        var current = path
        repeat(maxDepth) {
            val target = readLink(current) ?: return normalizePath(current)
            current = if (target.startsWith("/")) target
            else parent(current).trimEnd('/') + "/" + target
            current = normalizePath(current)
        }
        return current
    }

    /**
     * Run a command on the file-manager shell.
     *
     * IMPORTANT: libsu 6.0.0's `Job.exec()` WITHOUT external output lists loses stdout in
     * this environment (returns code=0 with EMPTY output for every command, even `echo`),
     * while the `Job.to(out, err)` variant works reliably. All shell access in this
     * repository MUST go through this helper.
     */
    private fun exec(vararg cmd: String): Shell.Result {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        return fmShell().newJob().add(*cmd).to(out, err).exec()
    }

    fun list(path: String): List<FileEntry> {
        // Fast path: ONE shell round-trip parses every entry (permissions, size, date,
        // type). The naive per-file SuFile.isDirectory/length/lastModified/canX approach
        // costs ~6 shell round-trips PER ENTRY, which made large folders take seconds.
        // A trailing slash is REQUIRED: `ls -l` does not follow a symlinked argument
        // (/sdcard -> /storage/...), but `/sdcard/` forces traversal into the target.
        val lsTarget = if (path == "/") "/" else path.trimEnd('/') + "/"
        val result = runCatching { exec("ls -Apl -- ${lsTarget.shellQuote()}") }
            .onFailure { Log.d(TAG, "list exec threw: $it") }
            .getOrNull()
        if (result != null) {
            val parsed = result.out.mapNotNull { line -> parseLsLine(line, path) }
            if (result.isSuccess && parsed.isNotEmpty()) return parsed.sortedWith(
                compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() }
            )
        }
        // Fallback: SuFile per-file queries (slow but robust).
        return listFallback(path)
    }

    private val lsLineRegex = Regex(
        // Char class covers every POSIX ls permission letter: x, -, s/S (setgid/setuid),
        // t/T (sticky). Missing lowercase 's' silently dropped every FUSE setgid folder
        // (drwxrws---) on /sdcard.
        "^([\\-dlbcps][rwxstSTxX-]{9})\\s+(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$"
    )

    /** Parse one `ls -Apl` line relative to [directory]; null for headers/total lines. */
    private fun parseLsLine(line: String, directory: String): FileEntry? {
        val match = lsLineRegex.find(line) ?: return null
        val (perms, links, owner, group, size, date, time, tail) = match.destructured
        // Symlinks render as "name -> target"; keep both the link name and its target.
        val separator = " -> "
        val isLink = perms.startsWith('l')
        val rawName = tail.substringBefore(separator).trim()
        if (rawName.isEmpty()) return null
        val name = rawName.trimEnd('/')
        if (name.isEmpty() || name == "." || name == "..") return null
        val linkTarget = if (isLink && tail.contains(separator)) {
            tail.substringAfter(separator).trim().ifBlank { null }
        } else null
        val isDir = perms.startsWith('d') || rawName.endsWith('/')
        return FileEntry(
            path = directory.trimEnd('/') + "/" + name,
            name = name,
            isDirectory = isDir,
            size = if (isDir) 0L else size.toLongOrNull() ?: 0L,
            lastModified = parseLsDate(date, time),
            mode = perms,
            linkTarget = linkTarget,
        )
    }

    private fun parseLsDate(date: String, time: String): Long = runCatching {
        val localDate = java.time.LocalDate.parse(date)
        val localTime = runCatching { java.time.LocalTime.parse(time) }
            .getOrElse { java.time.LocalTime.MIN }
        java.time.ZonedDateTime.of(localDate, localTime, java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun listFallback(path: String): List<FileEntry> {
        val directory = rootFile(path)
        if (!directory.isDirectory) return emptyList()
        val files = runCatching { directory.listFiles() }.getOrNull() ?: return emptyList()
        return files
            .map { file ->
                FileEntry(
                    path = file.path,
                    name = file.name.ifEmpty { file.path },
                    isDirectory = file.isDirectory,
                    size = if (file.isDirectory) 0L else runCatching { file.length() }.getOrDefault(0L),
                    lastModified = runCatching { file.lastModified() }.getOrDefault(0L),
                    mode = runCatching { modeOf(file) }.getOrDefault("----"),
                    linkTarget = readLink(file.path),
                )
            }
            .sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /** A search hit with its type pre-resolved, so the UI needs no follow-up stat. */
    data class SearchResult(val path: String, val isDir: Boolean)

    /**
     * Recursive case-insensitive name search under [path], capped at 200 results.
     * Runs find twice (dirs, then everything else) so each hit carries its type —
     * the result consumer may disappear (dialog dismissed) before a follow-up
     * stat could run, which silently cancelled some result taps.
     */
    fun search(path: String, query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        // Sanitize the glob: keep letters/digits/CJK/spaces/dots/dashes/underscores only.
        val escaped = query.filter { it.isLetterOrDigit() || it in " .-_（）()中文" || it.code > 0x2E7F }.trim()
        if (escaped.isEmpty()) return emptyList()
        // toybox find does NOT follow a symlinked starting point (/sdcard -> /storage/...),
        // so resolve the real path first and search there.
        val realPath = runCatching {
            exec("realpath ${path.shellQuote()}").out.firstOrNull()?.trim().orEmpty()
        }.getOrElse {
            logFailure("realpath $path", it)
            ""
        }.ifBlank { path.trimEnd('/') }
        val cap = "head -n 200"
        val dirCmd = "find ${realPath.shellQuote()} -maxdepth 6 -type d -iname '*$escaped*' 2>/dev/null | $cap"
        val fileCmd = "find ${realPath.shellQuote()} -maxdepth 6 ! -type d -iname '*$escaped*' 2>/dev/null | $cap"
        val dirs = runCatching { exec(dirCmd).out }.getOrElse { logFailure("search '$query'", it); return emptyList() }
        val files = runCatching { exec(fileCmd).out }.getOrDefault(emptyList())
        // Map results back to the user-facing path (e.g. keep /sdcard prefix instead of
        // /storage/emulated/0) so result rows read the same as the file list.
        val display = path.trimEnd('/')
        fun rewrite(list: List<String>) = if (realPath != display) {
            list.map { it.replaceFirst(realPath, display) }
        } else list
        return (rewrite(dirs).map { SearchResult(it, true) } +
            rewrite(files).map { SearchResult(it, false) })
            .filter { it.path.isNotBlank() }
            .take(200)
    }

    /** Install an APK through the root package manager. Returns null on success. */
    fun installApk(path: String): String? {
        val result = runCatching {
            exec("pm install -r --user 0 -- ${path.shellQuote()} 2>&1")
        }.getOrElse { return it.message ?: "Install failed" }
        val output = (result.out + result.err).joinToString("\n").trim()
        return when {
            !result.isSuccess && output.isBlank() -> "Install failed"
            output.contains("Success", ignoreCase = true) -> null
            else -> output.lineSequence().lastOrNull { it.isNotBlank() } ?: "Install failed"
        }
    }

    /**
     * Parse APK metadata. Package archives in privileged locations are not readable by
     * the app process, so the file is first streamed into [cacheDir] and parsed there.
     * Returns null when the archive cannot be read/parsed.
     */
    fun apkInfo(path: String, cacheDir: File, pm: android.content.pm.PackageManager): ApkInfo? =
        runCatching {
            val tmp = File.createTempFile("ysu_apk", ".apk", cacheDir.apply { mkdirs() })
            try {
                SuFileInputStream.open(rootFile(path)).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                val packageInfo = pm.getPackageArchiveInfo(tmp.absolutePath, 0) ?: return null
                val app = packageInfo.applicationInfo ?: return null
                app.sourceDir = tmp.absolutePath
                ApkInfo(
                    label = pm.getApplicationLabel(app).toString(),
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName ?: "",
                    versionCode = packageInfo.longVersionCode,
                )
            } finally {
                tmp.delete()
            }
        }.getOrNull()

    data class ApkInfo(
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
    )

    /** Open a root-aware stream for callers that need to parse a file incrementally. */
    fun openStream(path: String): java.io.InputStream = SuFileInputStream.open(rootFile(path))

    fun exists(path: String): Boolean = runCatching { rootFile(path).exists() }.getOrDefault(false)

    fun isDirectory(path: String): Boolean = runCatching { rootFile(path).isDirectory }.getOrDefault(false)

    /** Change permissions with an octal mode such as 0755, including 4-digit special bits. */
    fun chmod(path: String, mode: String, recursive: Boolean): String? {
        if (!mode.matches(Regex("[0-7]{3,4}"))) return "Invalid mode"
        val cmd = "chmod ${if (recursive) "-R " else ""}$mode -- ${path.shellQuote()}"
        val result = exec(cmd)
        return if (result.isSuccess) null else result.err.joinToString("\n").ifBlank { "chmod failed" }
    }

    /** First bytes of a file, used to tell ELF binaries from shell scripts. */
    fun readHeader(path: String, count: Int = 4): ByteArray =
        SuFileInputStream.open(rootFile(path)).use { input ->
            val buffer = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = input.read(buffer, read, count - read)
                if (n <= 0) break
                read += n
            }
            buffer.copyOf(read)
        }

    private fun ByteArray.prefix(vararg values: Int): Boolean =
        size >= values.size && values.indices.all { this[it].toInt() and 0xFF == values[it] }

    private fun ByteArray.ascii(value: String): Boolean =
        size >= value.length && value.indices.all { this[it].toInt().toChar() == value[it] }

    /**
     * Identify the real content type from an 8 KB header (extension is only a hint).
     * Mirrors the reference tool's `SmartFileOpener`: binary and archive files are
     * routed to a hex/chunk viewer instead of the whole-file text editor.
     */
    fun sniff(path: String): FileKind {
        val head = runCatching { readHeader(path, 8 * 1024) }.getOrDefault(ByteArray(0))
        val byMagic = when {
            head.prefix(0x50, 0x4B, 0x03, 0x04) || head.prefix(0x50, 0x4B, 0x05, 0x06) ||
                head.prefix(0x50, 0x4B, 0x07, 0x08) || head.prefix(0x1F, 0x8B) ||
                head.prefix(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) ||
                head.prefix(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) ||
                head.prefix(0x52, 0x61, 0x72, 0x21) -> FileKind.ARCHIVE
            head.prefix(0x7F, 0x45, 0x4C, 0x46) -> FileKind.ELF
            head.ascii("dex\n") -> FileKind.DEX
            head.prefix(0x89, 0x50, 0x4E, 0x47) || head.prefix(0xFF, 0xD8, 0xFF) ||
                head.ascii("GIF8") || head.ascii("BM") ||
                (head.ascii("RIFF") && head.size >= 12 && head.asciiAt(8, "WEBP")) -> FileKind.IMAGE
            head.ascii("%PDF") -> FileKind.PDF
            head.ascii("SQLite format 3") -> FileKind.SQLITE
            head.prefix(0xFF, 0xFE) || head.prefix(0xFE, 0xFF) ||
                head.prefix(0xEF, 0xBB, 0xBF) -> FileKind.TEXT
            else -> null
        }
        if (byMagic != null) return byMagic

        val name = path.substringAfterLast('/').lowercase()
        val byName = when {
            listOf("zip", "apk", "apks", "jar", "aar", "war", "tar", "gz", "tgz", "xz", "7z", "rar")
                .any { name.endsWith(".$it") } -> FileKind.ARCHIVE
            listOf("so", "o", "elf").any { name.endsWith(".$it") } -> FileKind.ELF
            name.endsWith(".dex") -> FileKind.DEX
            listOf("png", "jpg", "jpeg", "gif", "webp", "bmp").any { name.endsWith(".$it") } -> FileKind.IMAGE
            name.endsWith(".pdf") -> FileKind.PDF
            listOf("db", "sqlite", "sqlite3").any { name.endsWith(".$it") } -> FileKind.SQLITE
            else -> null
        }
        if (byName != null) return byName

        // NUL / C0 controls (excluding tab, LF, CR, FF) are a reliable binary signal.
        var suspicious = 0
        for (byte in head) {
            val value = byte.toInt() and 0xFF
            if (value == 0 || (value < 0x20 && value !in intArrayOf(9, 10, 12, 13))) suspicious++
        }
        return if (head.isNotEmpty() && suspicious * 20 > head.size) FileKind.BINARY else FileKind.TEXT
    }

    private fun ByteArray.asciiAt(index: Int, value: String): Boolean =
        size >= index + value.length && value.indices.all { this[index + it].toInt().toChar() == value[it] }

    private fun sanitizeText(text: String): String {
        val needsWork = text.any {
            (it.code < 0x20 && it != '\n' && it != '\r' && it != '\t') || it.code == 0x7F
        }
        if (!needsWork) return text
        val result = StringBuilder(text.length)
        for (char in text) {
            result.append(
                if ((char.code < 0x20 && char != '\n' && char != '\r' && char != '\t') || char.code == 0x7F) {
                    '\uFFFD'
                } else {
                    char
                }
            )
        }
        return result.toString()
    }

    private fun decodePageText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val (charset, skip) = when {
            bytes.prefix(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.prefix(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.prefix(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return sanitizeText(decoder.decode(ByteBuffer.wrap(bytes, skip, bytes.size - skip)).toString())
    }

    /**
     * Read a single bounded window at [offset] (64 KiB at most in practice). Never reads
     * the whole file, so binary blobs and GB-sized logs cannot stall text layout. The
     * libsu stream is a pipe, so random access is implemented by bounded skip reads.
     */
    fun readPage(path: String, offset: Long, maxBytes: Int, kind: FileKind): FilePage {
        val total = fileSize(path)
        val start = offset.coerceAtLeast(0L).coerceAtMost(maxOf(0L, total - 1))
        val bytes = SuFileInputStream.open(rootFile(path)).buffered().use { input ->
            var skipped = 0L
            val scratch = ByteArray(16 * 1024)
            while (skipped < start) {
                val want = minOf(scratch.size.toLong(), start - skipped).toInt()
                val count = input.read(scratch, 0, want)
                if (count <= 0) break
                skipped += count
            }
            val want = minOf(maxBytes.toLong(), maxOf(0L, total - skipped)).toInt()
            val buffer = ByteArray(want)
            var read = 0
            while (read < want) {
                val count = input.read(buffer, read, want - read)
                if (count <= 0) break
                read += count
            }
            buffer.copyOf(read)
        }
        return FilePage(start, total, bytes, if (kind == FileKind.TEXT) decodePageText(bytes) else "", kind)
    }

    fun readText(path: String): String =
        SuFileInputStream.open(rootFile(path)).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** File size in bytes via a single stat shell call. */
    fun fileSize(path: String): Long = runCatching {
        rootFile(path).length()
    }.getOrDefault(0L)

    /**
     * Stream the first [maxBytes] of a file as UTF-8 text. Used by the text editor for
     * huge files: reading them whole blows the heap (the reported crash) and a multi-MB
     * string cannot be rendered by a text field anyway. The read stops on a char boundary
     * so the tail never ends mid-surrogate.
     */
    fun readTextCapped(path: String, maxBytes: Int): String =
        SuFileInputStream.open(rootFile(path)).buffered().use { input ->
            val buffer = ByteArray(maxBytes)
            var read = 0
            while (read < maxBytes) {
                val n = input.read(buffer, read, maxBytes - read)
                if (n <= 0) break
                read += n
            }
            var text = String(buffer, 0, read, Charsets.UTF_8)
            // Trim an incomplete multi-byte sequence at the cut point (last byte(s) of a
            // truncated UTF-8 char decode as U+FFFD replacement chars).
            while (text.isNotEmpty() && text.last() == '�') {
                // Drop replacement chars at the very end (up to 3 per truncated char).
                text = text.dropLast(1)
                if (text.length < read / 3) break // safety net, never strip the whole file
            }
            text
        }

    fun writeText(path: String, text: String) {
        val file = File(path)
        file.parent?.let { rootFile(it).mkdirs() }
        SuFileOutputStream.open(rootFile(path)).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    fun mkdir(path: String): Boolean = runCatching { rootFile(path).mkdirs() }.getOrDefault(false)

    fun createFile(path: String): Boolean = runCatching {
        exec("touch -- ${path.shellQuote()}").isSuccess
    }.getOrDefault(false)

    fun delete(path: String): Boolean {
        if (path == "/" || path.isBlank()) return false
        return exec("rm -rf -- ${path.shellQuote()}").isSuccess
    }

    fun rename(from: String, to: String): String? {
        if (from == to || from.isBlank() || to.isBlank()) return "Invalid path"
        val result = exec("mv -f -- ${from.shellQuote()} ${to.shellQuote()}")
        return if (result.isSuccess) null else result.err.joinToString("\n").ifBlank { "Rename failed" }
    }

    fun copy(from: String, to: String): String? {
        if (from == to) return "Source and destination are the same"
        val cmd = buildString {
            append("cp -a -- ${from.shellQuote()} ${to.shellQuote()}")
        }
        val result = exec(cmd)
        return if (result.isSuccess) null else result.err.joinToString("\n").ifBlank { "Copy failed" }
    }

    fun move(from: String, to: String): String? {
        if (from == to) return "Source and destination are the same"
        val result = exec("mv -f -- ${from.shellQuote()} ${to.shellQuote()}")
        return if (result.isSuccess) null else result.err.joinToString("\n").ifBlank { "Move failed" }
    }

    fun chmod(path: String, mode: String): String? {
        if (!Regex("[0-7]{3,4}").matches(mode)) return "Invalid mode"
        val result = exec("chmod ${mode.shellQuote()} -- ${path.shellQuote()}")
        return if (result.isSuccess) null else result.err.joinToString("\n").ifBlank { "chmod failed" }
    }

    /**
     * Extract an archive into [target]. Entry names are sanitized: absolute paths, '..'
     * components and backslash separators are rejected/normalized so an untrusted archive
     * cannot write outside the target directory.
     */
    fun extract(archivePath: String, target: String): ExtractResult {
        val directory = File(target)
        rootFile(target).mkdirs()
        var count = 0
        var skipped = 0
        val input = SuFileInputStream.open(rootFile(archivePath))
        val archive: ArchiveInputStream<out ArchiveEntry> =
            ArchiveStreamFactory().createArchiveInputStream(BufferedInputStream(input))
        archive.use { stream ->
            var entry: ArchiveEntry? = stream.nextEntry
            while (entry != null) {
                val safeName = sanitizeEntry(entry.name)
                if (safeName == null) {
                    skipped++
                } else {
                    val output = File(directory, safeName)
                    if (entry.isDirectory) {
                        rootFile(output.path).mkdirs()
                    } else {
                        output.parentFile?.let { rootFile(it.path).mkdirs() }
                        SuFileOutputStream.open(rootFile(output.path)).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = stream.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                            }
                        }
                        count++
                    }
                }
                entry = archive.nextEntry
            }
        }
        return ExtractResult(count, skipped)
    }

    internal fun sanitizeEntry(raw: String): String? {
        val normalized = raw.replace('\\', '/')
        if (normalized.startsWith('/')) return null
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        return if (parts.isEmpty()) null else parts.joinToString("/")
    }

    fun formatSize(size: Long): String = when {
        size <= 0 -> "0 B"
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        size < 1024L * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
        else -> "%.2f GB".format(size / (1024.0 * 1024 * 1024))
    }

    fun logFailure(what: String, error: Throwable) {
        Log.w(TAG, "$what failed", error)
    }
}
