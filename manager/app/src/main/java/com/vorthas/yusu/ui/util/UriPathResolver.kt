package com.vorthas.yusu.ui.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.vorthas.yusu.ksuApp
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Resolve an external ACTION_VIEW Uri to the REAL filesystem path the file manager
 * can reveal. Fast app-level lookups come first; a root fallback handles
 * third-party provider Uris (QQ/WeChat) and files in other apps' private dirs.
 * No full-disk scan: the Uri path itself carries the relative filesystem path.
 */
object UriPathResolver {

    fun resolve(uri: Uri, displayName: String? = null): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.takeIf(::existsApp)
        }
        val name = displayName ?: uri.getFileName(ksuApp)

        // --- Fast path: app identity (no shell) ---
        resolveMediaStore(uri)?.let { return it }
        resolveDocument(uri, ::existsApp)?.let { return it }
        if (!name.isNullOrBlank()) findByName(name, ::existsApp)?.let { return it }

        // --- Root fallback for third-party providers / private dirs ---
        // 1) Derive the real path from the Uri path (FileProvider style:
        //    /external_files/Android/data/... → /storage/emulated/0/Android/data/...).
        deriveFromUriPath(uri)?.let { return it }

        // 2) Display name inside the derived directory (name may differ from Uri tail).
        val dir = deriveFromUriPath(uri)?.let(::File)?.parent
        if (!dir.isNullOrBlank() && !name.isNullOrBlank()) {
            val candidate = File(dir, name).absolutePath
            if (existsRoot(candidate)) return candidate
        }

        // 3) Last resort: query the provider via root for a path column.
        resolveViaRootShell(uri)?.let { return it }
        return null
    }

    // ---- existence helpers ---------------------------------------------------
    private fun existsApp(path: String): Boolean = path.isNotBlank() && File(path).exists()

    private fun existsRoot(path: String): Boolean {
        if (path.isBlank()) return false
        val q = path.replace("'", "'\\''")
        // Avoid nested sh -c quoting: pass the test expression as one arg to sh -c
        // with the path already single-quoted and escaped.
        return runCatching {
            Shell.getShell().newJob().add("sh -c \"[ -e '$q' ]\"").exec().isSuccess
        }.getOrDefault(false)
    }

    /**
     * FileProvider Uris expose the relative path after the root name in uri.path.
     * Map common provider roots to /storage/emulated/0 and check via root.
     */
    private fun deriveFromUriPath(uri: Uri): String? {
        val segments = uri.path?.trimStart('/')?.split('/')?.filter { it.isNotBlank() }
            ?: return null
        if (segments.size < 2) return null
        val root = segments[0]
        val rest = segments.drop(1).joinToString("/")
        val roots = when (root.lowercase()) {
            "external_files", "external", "external_path", "primary" ->
                listOf("/storage/emulated/0", "/data/media/0")
            "downloads" -> listOf("/storage/emulated/0/Download", "/storage/emulated/0")
            // Unknown provider root: still try the tail as a shared-storage path —
            // a single root existence check is cheap and covers odd provider names.
            else -> listOf("/storage/emulated/0")
        }
        return roots.firstNotNullOfOrNull { base ->
            val candidate = "$base/$rest"
            val mapped = candidate.takeIf(::existsRoot)
            mapped?.replaceFirst("/data/media/0", "/storage/emulated/0")
        }
    }

    // ---- app-level resolvers -------------------------------------------------
    private fun resolveMediaStore(uri: Uri): String? = runCatching {
        if (uri.authority != MediaStore.AUTHORITY) return null
        ksuApp.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (i >= 0) c.getString(i)?.takeIf(::existsApp) else null
            } else null
        }
    }.getOrNull()

    private fun resolveDocument(uri: Uri, exists: (String) -> Boolean): String? = runCatching {
        if (!DocumentsContract.isDocumentUri(ksuApp, uri)) return@runCatching null
        val candidate = when (uri.authority) {
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
                        val n = id.removePrefix("msf:").toLongOrNull() ?: return@runCatching null
                        resolveMediaStore(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, n))
                    }
                    else -> null
                }
            }
            else -> null
        }
        candidate?.takeIf(exists)
    }.getOrNull()

    private fun findByName(name: String, exists: (String) -> Boolean): String? {
        val roots = listOf("/storage/emulated/0/Download", "/storage/emulated/0/Downloads", "/storage/emulated/0")
        for (root in roots) {
            val direct = File(root, name)
            if (exists(direct.absolutePath)) return direct.absolutePath
        }
        File("/storage/emulated/0/Download").listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val c = File(dir, name)
                if (exists(c.absolutePath)) return c.absolutePath
            }
        }
        return null
    }

    /** Query any content Uri via root for a real path column. */
    private fun resolveViaRootShell(uri: Uri): String? = runCatching {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val result = Shell.getShell().newJob()
            .add("content query --uri '$uri'")
            .to(out, err).exec()
        if (!result.isSuccess) return null
        out.joinToString("\n").lineSequence()
            .flatMap { line -> Regex("(?:_?data|[Dd]ata)=([^,}]+)").findAll(line).map { it.groupValues[1].trim() } }
            .map { it.removeSurrounding("\"") }
            .firstOrNull(::existsRoot)
    }.getOrNull()
}
