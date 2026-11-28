package com.vorthas.yusu.ui.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.vorthas.yusu.ksuApp
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Resolve an external Uri to a real filesystem path the file manager can reveal.
 *
 * No package-name whitelist: every provider goes through the same generic path
 * derivation, followed by provider queries, a bounded root-side name search, and
 * finally a size-capped stream copy as the universal fallback.
 */
object UriPathResolver {

    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

    fun resolve(uri: Uri, displayName: String? = null): String? = resolveInner(uri, displayName)

    private fun resolveInner(uri: Uri, displayName: String?): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.takeIf(::existsApp)?.let { return it }
        }
        val name = displayName?.takeIf { it.isNotBlank() } ?: uri.getFileName(ksuApp)

        // --- Fast path: app identity (no shell) ---
        resolveMediaStore(uri)?.let { return it }
        resolveDocument(uri, ::existsApp)?.let { return it }
        if (!name.isNullOrBlank()) findByName(name, ::existsApp)?.let { return it }

        // --- Generic FileProvider/DocumentsProvider path derivation ---
        deriveFromUriPath(uri)?.let { return it }

        // Display name inside the derived directory (some providers use opaque tails).
        val dir = deriveFromUriPath(uri)?.let(::File)?.parent
        if (!dir.isNullOrBlank() && !name.isNullOrBlank()) {
            val candidate = File(dir, name).absolutePath
            if (existsRoot(candidate)) return candidate
        }

        // Provider may expose a real path column through content query.
        resolveViaRootShell(uri)?.let { return it }

        // Last resort before copying: exact display name under shared storage.
        if (!name.isNullOrBlank()) findByNameViaRoot(name)?.let { return it }

        // Universal fallback: any readable content Uri can be staged into our cache.
        copyToCache(uri, name)?.let { return it }
        return null
    }

    // ---- existence and quoting helpers --------------------------------------

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun existsApp(path: String): Boolean = path.isNotBlank() && File(path).exists()

    private fun existsRoot(path: String): Boolean {
        if (path.isBlank()) return false
        return runCatching {
            Shell.getShell().newJob().add("[ -e ${shellQuote(path)} ]").exec().isSuccess
        }.getOrDefault(false)
    }

    private fun normalizeStoragePath(path: String): String =
        if (path.startsWith("/data/media/0")) {
            "/storage/emulated/0" + path.removePrefix("/data/media/0")
        } else {
            path
        }

    /**
     * Derive candidates from ANY Uri path shape. Common examples:
     *   /external_files/Download/a.zip            -> /storage/emulated/0/Download/a.zip
     *   /external_files/storage/emulated/0/a.zip  -> /storage/emulated/0/a.zip (WeChat/QQ)
     *   /storage/emulated/0/a.zip                 -> /storage/emulated/0/a.zip
     *   /data/media/0/a.zip                       -> /storage/emulated/0/a.zip
     */
    private fun deriveFromUriPath(uri: Uri): String? {
        val segments = uri.path?.trimStart('/')?.split('/')?.filter { it.isNotBlank() }
            ?: return null
        if (segments.size < 2) return null

        val rest = segments.drop(1).joinToString("/")
        val candidates = linkedSetOf<String>()

        fun add(path: String) {
            if (path.startsWith("/")) candidates.add(normalizeStoragePath(path))
        }

        if (rest.startsWith("storage/emulated/0/", ignoreCase = true) ||
            rest.equals("storage/emulated/0", ignoreCase = true)
        ) {
            add("/$rest")
        }
        // Some providers keep the full path as the Uri path, with or without a root name.
        if (segments[0].equals("storage", ignoreCase = true) ||
            segments[0].equals("data", ignoreCase = true)
        ) {
            add("/" + segments.joinToString("/"))
        }
        for (base in listOf("/storage/emulated/0", "/data/media/0")) {
            add("$base/$rest")
        }
        add("/$rest")

        return candidates.firstNotNullOfOrNull { path -> path.takeIf(::existsRoot) }
    }

    // ---- app-level resolvers -------------------------------------------------

    private fun queryDataColumn(uri: Uri): String? = runCatching {
        ksuApp.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex("_data")
            if (index < 0) return null
            cursor.getString(index)?.takeIf { it.startsWith("/") }
        }
    }.getOrNull()

    private fun resolveMediaStore(uri: Uri): String? = runCatching {
        if (uri.authority != MediaStore.AUTHORITY) return null
        ksuApp.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (index < 0) return null
            cursor.getString(index)?.takeIf(::existsApp)
        }
    }.getOrNull()

    private fun resolveDocument(uri: Uri, exists: (String) -> Boolean): String? = runCatching {
        if (!DocumentsContract.isDocumentUri(ksuApp, uri)) return@runCatching null
        val specific = when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val parts = DocumentsContract.getDocumentId(uri).split(":", limit = 2)
                val storage = if (parts[0].equals("primary", true)) "/storage/emulated/0" else "/storage/${parts[0]}"
                if (parts.size == 2) "$storage/${parts[1]}" else null
            }
            "com.android.providers.downloads.documents" -> {
                val id = DocumentsContract.getDocumentId(uri)
                when {
                    id.startsWith("raw:") -> id.removePrefix("raw:")
                    id.startsWith("msf:") -> {
                        val mediaId = id.removePrefix("msf:").toLongOrNull() ?: return@runCatching null
                        resolveMediaStore(
                            android.content.ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                mediaId,
                            )
                        )
                    }
                    else -> null
                }
            }
            else -> null
        }
        specific?.takeIf(exists) ?: queryDataColumn(uri)?.takeIf(exists)
    }.getOrNull()

    private fun findByName(name: String, exists: (String) -> Boolean): String? {
        val roots = listOf("/storage/emulated/0/Download", "/storage/emulated/0/Downloads", "/storage/emulated/0")
        for (root in roots) {
            val direct = File(root, name)
            if (exists(direct.absolutePath)) return direct.absolutePath
        }
        File("/storage/emulated/0/Download").listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val candidate = File(dir, name)
                if (exists(candidate.absolutePath)) return candidate.absolutePath
            }
        }
        return null
    }

    /** Root search by exact file name under shared storage, including app-private dirs. */
    private fun findByNameViaRoot(name: String): String? = runCatching {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val cmd = "find /data/media/0 -maxdepth 8 -type f -name ${shellQuote(name)} 2>/dev/null | head -n 8"
        val result = Shell.getShell().newJob().add(cmd).to(out, err).exec()
        if (!result.isSuccess) return null
        out.map(String::trim)
            .firstOrNull(::existsRoot)
            ?.let(::normalizeStoragePath)
    }.getOrNull()

    /** Query any content Uri via root for a real path column. */
    private fun resolveViaRootShell(uri: Uri): String? = runCatching {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val result = Shell.getShell().newJob()
            .add("content query --uri ${shellQuote(uri.toString())}")
            .to(out, err).exec()
        if (!result.isSuccess) return null
        out.joinToString("\n").lineSequence()
            .flatMap { line ->
                Regex("""(?:_?data|[Dd]ata|file_path)=([^,}]+)""")
                    .findAll(line).map { it.groupValues[1].trim() }
            }
            .map { it.removeSurrounding("\"") }
            .map(::normalizeStoragePath)
            .firstOrNull(::existsRoot)
    }.getOrNull()

    // ---- universal fallback --------------------------------------------------

    private fun sanitizeFileName(displayName: String?): String {
        val base = File(displayName?.replace('\\', '/') ?: "opened_file").name
        val cleaned = base.replace(Regex("""[/\\:*?"<>|\x00-\x1f]"""), "_")
            .trim('.')
            .take(100)
        return cleaned.ifBlank { "opened_file" }
    }

    /** Stage a provider stream into our cache so even virtual files can be revealed. */
    private fun copyToCache(uri: Uri, displayName: String?): String? = runCatching {
        val dir = File(ksuApp.cacheDir, "opened").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "opened_${System.currentTimeMillis()}_${sanitizeFileName(displayName)}")
        val stream = ksuApp.contentResolver.openInputStream(uri) ?: return@runCatching null
        stream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_CACHE_BYTES) error("External file is too large to cache")
                    output.write(buffer, 0, read)
                }
            }
        }
        target.absolutePath.takeIf { target.length() > 0 }
    }.getOrNull()
}
