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
 * can reveal. Fast app-level lookups come first; a root-shell fallback handles
 * third-party provider Uris (QQ/WeChat) and files in other apps' private dirs
 * (/Android/data/...), where the app identity is denied even with all-files access.
 */
object UriPathResolver {

    /** Returns a real, existing path, or null if it cannot be resolved. */
    fun resolve(uri: Uri, displayName: String? = null): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.takeIf(::existsApp)
        }
        val name = displayName ?: uri.getFileName(ksuApp)

        // --- Fast path: app identity (no shell) -------------------------------
        resolveMediaStore(uri)?.let { return it }
        resolveDocument(uri, ::existsApp)?.let { return it }
        if (!name.isNullOrBlank()) findByName(name, ::existsApp)?.let { return it }

        // --- Root fallback: third-party providers / private dirs --------------
        // Only reached when the fast path failed, so normal cases stay instant.
        resolveViaRootShell(uri)?.let { return it }
        // QQ/WeChat provider Uris often deny the app-level name query; ask via root.
        val rootName = if (name.isNullOrBlank()) displayNameViaRoot(uri) else name
        if (!rootName.isNullOrBlank()) {
            findByNameViaRoot(rootName)?.let { return it }
        }
        return null
    }

    // ---- existence helpers ---------------------------------------------------
    private fun existsApp(path: String): Boolean = path.isNotBlank() && File(path).exists()

    private fun existsRoot(path: String): Boolean {
        if (path.isBlank()) return false
        val q = path.replace("'", "'\\''")
        return runCatching {
            Shell.getShell().newJob().add("sh -c '[ -e '\''$q'\'' ]'").exec().isSuccess
        }.getOrDefault(false)
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

    /** Bounded search in likely download folders. */
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

    // ---- root fallbacks ------------------------------------------------------
    /** Query ANY content Uri via root (no authority restriction) for a real path. */
    private fun resolveViaRootShell(uri: Uri): String? = runCatching {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val result = Shell.getShell().newJob()
            .add("content query --uri '$uri'")
            .to(out, err).exec()
        if (!result.isSuccess) return null
        out.joinToString("\n").lineSequence()
            .flatMap { line -> Regex("(?:_?data|[Dd]ata|_display_name)=([^,}]+)").findAll(line).map { it.groupValues[1].trim() } }
            .map { it.removeSurrounding("\"") }
            .firstOrNull(::existsRoot)
    }.getOrNull()

    /** Get _display_name from any content Uri via root when the app query is denied. */
    private fun displayNameViaRoot(uri: Uri): String? = runCatching {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val result = Shell.getShell().newJob()
            .add("content query --uri '$uri' --projection _display_name")
            .to(out, err).exec()
        if (!result.isSuccess) return null
        out.joinToString("\n").lineSequence()
            .mapNotNull { line -> Regex("_display_name=([^,}]+)").find(line)?.groupValues?.get(1)?.trim() }
            .map { it.removeSurrounding("\"") }
            .firstOrNull { it.isNotBlank() }
    }.getOrNull()

    /** Root search including /Android/data (QQ/WeChat private dirs). */
    private fun findByNameViaRoot(name: String): String? = runCatching {
        val q = name.replace("'", "'\\''")
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        // /sdcard is a FUSE view that hides Android/data even from root; the real
        // backing path /data/media/0 is fully traversable with root.
        val cmd = "find /data/media/0 -maxdepth 7 -type f -name '$q' 2>/dev/null | head -n 8"
        val result = Shell.getShell().newJob().add("sh -c '$cmd'").to(out, err).exec()
        if (!result.isSuccess) return null
        out.map(String::trim)
            .firstOrNull(::existsRoot)
            ?.replaceFirst("/data/media/0", "/storage/emulated/0")
    }.getOrNull()
}
