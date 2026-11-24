package com.vorthas.yusu.ui.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.vorthas.yusu.ksuApp
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Resolve an external ACTION_VIEW Uri to a filesystem path the file manager can
 * reveal. Handles file://, MediaStore, and SAF document Uris; falls back to
 * copying the stream into our cache so the file is still reachable.
 */
object UriPathResolver {

    /** Returns a path that exists, or null if the Uri cannot be read at all. */
    fun resolve(uri: Uri, displayName: String? = null): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.takeIf { File(it).exists() }
        }
        resolveMediaStore(uri)?.let { return it }
        resolveDocument(uri)?.let { return it }
        // Last resort: make a local copy so the file manager can reveal it.
        return copyToCache(uri, displayName ?: uri.getFileName(ksuApp) ?: "file")
    }

    private fun resolveMediaStore(uri: Uri): String? = runCatching {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        ksuApp.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (idx >= 0) cursor.getString(idx)?.takeIf { File(it).exists() } else null
            } else null
        }
    }.getOrNull()
        // Scoped storage can deny the DATA column to the app identity; the root
        // shell can still query MediaStore reliably.
        ?: resolveViaRootShell(uri)

    private fun resolveDocument(uri: Uri): String? {
        if (!DocumentsContract.isDocumentUri(ksuApp, uri)) return null
        return when (uri.authority) {
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
                    docId.startsWith("raw:") -> docId.removePrefix("raw:").takeIf { File(it).exists() }
                    docId.startsWith("msf:") -> {
                        val id = docId.removePrefix("msf:").toLongOrNull() ?: return null
                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        resolveMediaStore(contentUri)
                    }
                    else -> null
                }
            }
            else -> null
        }?.takeIf { File(it).exists() }
    }

    /** Query MediaStore via the root shell when the app-level query is denied. */
    private fun resolveViaRootShell(uri: Uri): String? = runCatching {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val result = Shell.getShell().newJob()
            .add("content query --uri $uri --projection _data")
            .to(out, err).exec()
        if (!result.isSuccess) return null
        out.joinToString("\n").lineSequence()
            .mapNotNull { line -> Regex("_data=([^,}]+)").find(line)?.groupValues?.get(1)?.trim() }
            .firstOrNull { File(it).exists() }
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
            // Root shell can read content Uris the app has no direct grant for.
            val result = Shell.getShell().newJob()
                .add("sh -c 'content read --uri \"$uri\" > \"${target.absolutePath}\"'")
                .exec()
            if (!result.isSuccess || !target.exists()) return null
        }
        target.absolutePath
    }.getOrNull()
}
