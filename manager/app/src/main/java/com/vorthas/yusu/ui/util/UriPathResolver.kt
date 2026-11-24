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
 * can reveal. The manager has root but no scoped-storage permission, so app-level
 * File.exists()/MediaStore queries often fail — all existence checks and Uri queries
 * therefore go through the root shell. Only as a last resort do we copy into cache.
 */
object UriPathResolver {

    /** Returns a real, existing path, or null if the Uri cannot be read at all. */
    fun resolve(uri: Uri, displayName: String? = null): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.takeIf(::existsViaRoot)
        }
        val name = displayName ?: uri.getFileName(ksuApp)

        // 1) Root-shell MediaStore/content query — works for QQ/download providers
        //    even when the app identity is denied access to the DATA column.
        resolveViaRootShell(uri)?.let { return it }

        // 2) Document Uris (SAF) decoded to their real storage path.
        resolveDocument(uri)?.let { return it }

        // 3) Locate by display name under shared storage. Downloaders (QQ, browsers)
        //    hand over opaque content Uris but the file is really on /sdcard.
        if (!name.isNullOrBlank()) {
            findByNameViaRoot(name)?.let { return it }
            resolveMediaStoreByName(name)?.let { return it }
        }

        // 4) Last resort: local copy (reveal will land in our cache dir).
        return copyToCache(uri, name ?: "file")
    }

    /** Root-shell existence check: app identity has no storage permission. */
    private fun existsViaRoot(path: String): Boolean {
        if (path.isBlank()) return false
        val quoted = path.replace("'", "'\\''")
        return Shell.getShell().newJob()
            .add("sh -c '[ -e '\''$quoted'\'' ]'")
            .exec().isSuccess
    }

    private fun resolveMediaStore(uri: Uri): String? = runCatching {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        ksuApp.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (idx >= 0) cursor.getString(idx)?.takeIf(::existsViaRoot) else null
            } else null
        }
    }.getOrNull()

    private fun resolveDocument(uri: Uri): String? = runCatching {
        if (!DocumentsContract.isDocumentUri(ksuApp, uri)) return@runCatching null
        val candidate = when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":", limit = 2)
                val storage = if (parts[0].equals("primary", true)) "/storage/emulated/0"
                              else "/storage/${parts[0]}"
                if (parts.size == 2) "$storage/${parts[1]}" else null
            }
            "com.android.providers.downloads.documents" -> {
                val docId = DocumentsContract.getDocumentId(uri)
                when {
                    docId.startsWith("raw:") -> docId.removePrefix("raw:")
                    docId.startsWith("msf:") -> {
                        val id = docId.removePrefix("msf:").toLongOrNull() ?: return@runCatching null
                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        resolveViaRootShell(contentUri)
                    }
                    else -> null
                }
            }
            else -> null
        }
        candidate?.takeIf(::existsViaRoot)
    }.getOrNull()

    /**
     * Query any content Uri via the root shell. Extract a real path from common
     * columns (_data, data, ...); no authority restriction so QQ/downloader Uris work.
     */
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
            .firstOrNull(::existsViaRoot)
    }.getOrNull()

    /** Search shared storage for the exact display name using the root shell. */
    private fun findByNameViaRoot(name: String): String? = runCatching {
        val safe = name.replace("'", "'\\''")
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val cmd = "find /sdcard -type f -name '$safe' 2>/dev/null | head -n 5"
        val result = Shell.getShell().newJob().add("sh -c '$cmd'").to(out, err).exec()
        if (!result.isSuccess) return null
        out.map(String::trim).firstOrNull(::existsViaRoot)
    }.getOrNull()

    /** Query MediaStore Downloads by display name via root shell. */
    private fun resolveMediaStoreByName(name: String): String? = runCatching {
        val safe = name.replace("'", "'\\''")
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val sel = "_display_name='$safe'"
        val result = Shell.getShell().newJob()
            .add("content query --uri content://media/external/downloads --projection _data --where \"$sel\"")
            .to(out, err).exec()
        if (!result.isSuccess) return null
        out.joinToString("\n").lineSequence()
            .mapNotNull { line -> Regex("_data=([^,}]+)").find(line)?.groupValues?.get(1)?.trim() }
            .map { it.removeSurrounding("\"") }
            .firstOrNull(::existsViaRoot)
    }.getOrNull()

    private fun copyToCache(uri: Uri, displayName: String): String? = runCatching {
        val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val dir = File(ksuApp.cacheDir, "opened").apply { mkdirs() }
        val target = File(dir, safe)
        val appRead = runCatching {
            ksuApp.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!appRead) {
            val result = Shell.getShell().newJob()
                .add("sh -c 'content read --uri \"$uri\" > \"${target.absolutePath}\"'")
                .exec()
            if (!result.isSuccess || !existsViaRoot(target.absolutePath)) return null
        }
        target.absolutePath
    }.getOrNull()
}
