package com.vorthas.yusu.data.filemanager

import android.database.sqlite.SQLiteDatabase
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Native, bounded readers for the file viewer. Nothing here holds a whole large file in
 * memory: archives are streamed, SQLite databases are copied to a bounded cache file, and
 * binary headers are parsed from a fixed-size prefix.
 */
object FileViewerRepository {

    /**
     * Virtual path used when browsing inside an archive from the file manager panes:
     * "<archive real path>!\u0000!<directory inside archive>". The NUL separator can never
     * occur in a real path, so the two halves are unambiguous and the whole thing rides on
     * the existing pane history (back/forward/up) as a normal path string.
     */
    private const val ARCHIVE_SEP = "\u0000"
    fun archiveVirtualPath(archive: String, dir: String): String = archive + ARCHIVE_SEP + dir.trim('/')
    fun parseArchiveVirtual(path: String): Pair<String, String>? {
        val i = path.indexOf(ARCHIVE_SEP)
        return if (i >= 0) path.substring(0, i) to path.substring(i + 1) else null
    }

    data class ArchiveItem(val name: String, val size: Long, val directory: Boolean)

    enum class ArchiveKind { ZIP, TAR, GZIP, BZIP2, XZ, SEVENZ, RAR }

    private val archiveSuffixes: List<Pair<String, ArchiveKind>> = listOf(
        ".zip" to ArchiveKind.ZIP, ".apk" to ArchiveKind.ZIP, ".apks" to ArchiveKind.ZIP,
        ".jar" to ArchiveKind.ZIP, ".aar" to ArchiveKind.ZIP, ".war" to ArchiveKind.ZIP,
        ".xapk" to ArchiveKind.ZIP, ".mtz" to ArchiveKind.ZIP,
        ".tar.gz" to ArchiveKind.TAR, ".tgz" to ArchiveKind.TAR, ".tar" to ArchiveKind.TAR,
        ".gz" to ArchiveKind.GZIP, ".gzip" to ArchiveKind.GZIP,
        ".tar.bz2" to ArchiveKind.BZIP2, ".tbz2" to ArchiveKind.BZIP2, ".bz2" to ArchiveKind.BZIP2,
        ".tar.xz" to ArchiveKind.XZ, ".txz" to ArchiveKind.XZ, ".xz" to ArchiveKind.XZ,
        ".7z" to ArchiveKind.SEVENZ, ".rar" to ArchiveKind.RAR,
    )

    fun archiveKind(path: String): ArchiveKind? {
        val name = path.substringAfterLast('/').lowercase()
        return archiveSuffixes.firstOrNull { name.endsWith(it.first) }?.second
    }

    private fun isTarVariant(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        return listOf(".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz").any { name.endsWith(it) }
    }

    private fun compressorStream(path: String, local: File?): InputStream {
        val input: InputStream = BufferedInputStream(local?.inputStream() ?: FileManagerRepository.openStream(path))
        return when (archiveKind(path)) {
            ArchiveKind.GZIP -> GzipCompressorInputStream(input)
            ArchiveKind.BZIP2 -> BZip2CompressorInputStream(input)
            ArchiveKind.XZ -> XZCompressorInputStream(input)
            else -> input
        }
    }

    private fun archiveStream(path: String, local: File? = null): ArchiveInputStream<out ArchiveEntry> {
        val input: InputStream = BufferedInputStream(local?.inputStream() ?: FileManagerRepository.openStream(path))
        return when (archiveKind(path)) {
            ArchiveKind.GZIP, ArchiveKind.BZIP2, ArchiveKind.XZ ->
                ArchiveStreamFactory().createArchiveInputStream(
                    ArchiveStreamFactory.TAR, compressorStream(path, local),
                )
            ArchiveKind.ZIP -> ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, input)
            ArchiveKind.TAR -> ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, input)
            ArchiveKind.SEVENZ -> ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.SEVEN_Z, input)
            ArchiveKind.RAR -> error("当前构建未内置 RAR 解析，请使用解压功能")
            null -> ArchiveStreamFactory().createArchiveInputStream(input)
        }
    }

    private fun singleEntryName(path: String): String = path.substringAfterLast('/')
        .removeSuffix(".gz").removeSuffix(".gzip").removeSuffix(".bz2").removeSuffix(".xz")
        .ifBlank { "entry.bin" }

    private fun isSingleCompressed(path: String): Boolean =
        archiveKind(path) in setOf(ArchiveKind.GZIP, ArchiveKind.BZIP2, ArchiveKind.XZ) && !isTarVariant(path)

    /**
     * Copy the archive to cache first. ZIP/APK then use random-access ZipFile (MT-style
     * instant listing) instead of a fragile root FIFO stream; other formats use the same
     * local copy through Commons Compress.
     */
    fun listArchive(path: String, cacheDir: File, limit: Int = 2000): List<ArchiveItem> {
        if (isSingleCompressed(path)) return listOf(ArchiveItem(singleEntryName(path), -1L, false))
        val local = copyToCache(path, cacheDir, "arc", 512L * 1024 * 1024)
        val items = try {
            if (archiveKind(path) == ArchiveKind.ZIP) {
                ZipFile(local).use { zip ->
                    zip.entries().asSequence().take(limit)
                        .map { ArchiveItem(it.name, it.size, it.isDirectory) }
                        .toList()
                }
            } else {
                archiveStream(path, local).use { stream ->
                    val result = mutableListOf<ArchiveItem>()
                    var entry: ArchiveEntry? = stream.nextEntry
                    while (entry != null && result.size < limit) {
                        result += ArchiveItem(entry.name, entry.size, entry.isDirectory)
                        entry = stream.nextEntry
                    }
                    result
                }
            }
        } finally {
            runCatching { local.delete() }
        }
        return items
    }

    private fun safeFileName(name: String): String = name.replace('\\', '/')
        .substringAfterLast('/')
        .filter { it.isLetterOrDigit() || it in ".-_" }
        .take(80)
        .ifBlank { "entry.bin" }

    /**
     * Extract one entry into the app cache, bounded to 32 MB. The returned file can be fed
     * to the ordinary text/image viewers, so archive preview reuses the native viewers.
     */
    fun extractEntry(path: String, entryName: String, cacheDir: File, maxBytes: Int = 32 * 1024 * 1024): File {
        val target = File(cacheDir, "fm-archive/${UUID.randomUUID()}/${safeFileName(entryName)}")
        target.parentFile?.mkdirs()
        if (isSingleCompressed(path)) {
            compressorStream(path, null).use { input ->
                target.outputStream().use { output -> copyBounded(input, output, maxBytes) }
            }
            return target
        }
        val local = copyToCache(path, cacheDir, "arc", 512L * 1024 * 1024)
        if (archiveKind(path) == ArchiveKind.ZIP) {
            try {
                ZipFile(local).use { zip ->
                    val entry = zip.getEntry(entryName) ?: error("归档中找不到条目：$entryName")
                    if (entry.isDirectory) error("条目是目录：$entryName")
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> copyBounded(input, output, maxBytes) }
                    }
                }
            } finally {
                runCatching { local.delete() }
            }
            return target
        }
        try {
            archiveStream(path, local).use { stream ->
                var entry: ArchiveEntry? = stream.nextEntry
                while (entry != null) {
                    if (entry.name == entryName && !entry.isDirectory) {
                        target.outputStream().use { output -> copyBounded(stream, output, maxBytes) }
                        return target
                    }
                    entry = stream.nextEntry
                }
            }
        } finally {
            runCatching { local.delete() }
        }
        error("归档中找不到条目：$entryName")
    }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream, maxBytes: Int) {
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (total < maxBytes) {
            val want = minOf(buffer.size, maxBytes - total)
            val read = input.read(buffer, 0, want)
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
        }
    }

    /** Random-access consumers (SQLite, PdfRenderer) need a local copy. */
    fun copyToCache(path: String, cacheDir: File, prefix: String, maxBytes: Long = 256L * 1024 * 1024): File {
        val work = File(cacheDir, "fm-$prefix/${UUID.randomUUID()}.$prefix")
        work.parentFile?.mkdirs()
        FileManagerRepository.openStream(path).use { input ->
            work.outputStream().use { output -> copyBounded(input, output, maxBytes.toInt()) }
        }
        return work
    }

    data class SqliteTable(val name: String, val columns: List<String>, val rows: List<List<String?>>)

    /** Copy to cache first: SQLite needs random access, which the root stream cannot provide. */
    fun sqlitePreview(path: String, cacheDir: File, maxBytes: Long = 128L * 1024 * 1024): List<SqliteTable> {
        val work = copyToCache(path, cacheDir, "sqlite", maxBytes)
        val db = SQLiteDatabase.openDatabase(work.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            val tables = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let { tables += it }
            }
            tables.take(40).map { table ->
                val columns = mutableListOf<String>()
                val rows = mutableListOf<List<String?>>()
                db.rawQuery("SELECT * FROM \"${table.replace("\"", "\"\"")}\" LIMIT 30", null).use { cursor ->
                    repeat(cursor.columnCount) { columns += cursor.getColumnName(it) }
                    while (cursor.moveToNext()) {
                        rows += List(cursor.columnCount) { index ->
                            if (cursor.isNull(index)) null else runCatching { cursor.getString(index) }.getOrNull()
                        }
                    }
                }
                SqliteTable(table, columns, rows)
            }
        } finally {
            db.close()
            runCatching { work.delete() }
        }
    }

    /** Header-level summary for binary formats; no whole-file read. */
    fun describeBinary(path: String, kind: FileKind): List<String> {
        val head = runCatching { FileManagerRepository.readHeader(path, 64) }.getOrDefault(ByteArray(0))
        fun u16(offset: Int): Int = if (offset + 1 < head.size) {
            (head[offset].toInt() and 0xFF) or ((head[offset + 1].toInt() and 0xFF) shl 8)
        } else 0
        fun u32(offset: Int): Long = if (offset + 3 < head.size) {
            (head[offset].toLong() and 0xFF) or ((head[offset + 1].toLong() and 0xFF) shl 8) or
                ((head[offset + 2].toLong() and 0xFF) shl 16) or ((head[offset + 3].toLong() and 0xFF) shl 24)
        } else 0L
        return when (kind) {
            FileKind.ELF -> {
                val is64 = head.getOrNull(4) == 2.toByte()
                val little = head.getOrNull(5) == 1.toByte()
                val machine = u16(if (is64) 18 else 18)
                listOf(
                    "格式：ELF ${if (is64) "64" else "32"} 位",
                    "字节序：${if (little) "小端 (LE)" else "大端 (BE)"}",
                    "目标架构：${elfMachine(machine)}",
                    "ELF 类型：${elfType(u16(16))}",
                    "程序头表偏移：${if (is64) u32(32) else u32(28)}",
                    "节区头表偏移：${if (is64) u32(40) else u32(32)}",
                )
            }
            FileKind.DEX -> listOf(
                "格式：Dalvik EXECUTABLE",
                "版本：${String(head, 4, minOf(3, maxOf(0, head.size - 4)), Charsets.US_ASCII)}",
                "文件大小：${u32(32)} B",
                "类定义数量：${u32(56)}",
                "方法定义数量：${u32(64)}",
            )
            FileKind.ARCHIVE -> listOf(
                "格式：压缩归档",
                "归档类型：${archiveKind(path)?.name ?: "未知"}",
                "请在归档视图中查看条目",
            )
            else -> listOf(
                "格式：${kind.name}",
                "文件大小：${FileManagerRepository.fileSize(path)} B",
                "下方为二进制十六进制视图（只读、分页）",
            )
        }
    }

    private fun elfMachine(value: Int): String = when (value) {
        0x03 -> "x86"; 0x3E -> "x86-64"; 0x28 -> "ARM"; 0xB7 -> "AArch64"
        0xF3 -> "RISC-V"; 0x08 -> "MIPS"; else -> "0x${value.toString(16).uppercase(Locale.ROOT)}"
    }

    private fun elfType(value: Int): String = when (value) {
        1 -> "可重定位 (REL)"; 2 -> "可执行 (EXEC)"; 3 -> "共享库 (DYN)"; 4 -> "核心转储 (CORE)"
        else -> "0x${value.toString(16).uppercase(Locale.ROOT)}"
    }
}
