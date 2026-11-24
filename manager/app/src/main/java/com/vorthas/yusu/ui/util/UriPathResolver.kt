package com.vorthas.yusu.ui.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.vorthas.yusu.ksuApp
import java.io.File

/**
 * Resolve an external ACTION_VIEW Uri to the REAL filesystem path the file manager
 * can reveal. With "Manage all files" access granted, app-level File.exists() and
 * MediaStore queries work directly — no root-shell probes and no internal cache
 * copy. All lookups are fast and land on the file's true location.
 */
object UriPathResolver {

    /** Returns a real, existing path, or null if it cannot be resolved. */
    fun resolve(uri: Uri, displayName: String? = null): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.takeIf(::exists)
        }
        val name = displayName ?: uri.getFileName(ksuApp)

        // 1) MediaStore query (app identity works with all-files access).
        resolveMediaStore(uri)?.let { return it }

        // 2) Document Uris (SAF) decoded to their real storage path.
        resolveDocument(uri)?.let { return it }

        // 3) Locate by display name under common download locations. Bounded —
        //    no full-disk scan, so this stays fast.
        if (!name.isNullOrBlank()) {
            findByName(name)?.let { return it }
        }
        return null
    }

    private fun exists(path: String): Boolean = path.isNotBlank() && File(path).exists()

    private fun resolveMediaStore(uri: Uri): String? = runCatching {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        ksuApp.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (idx >= 0) cursor.getString(idx)?.takeIf(::exists) else null
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
                        resolveMediaStore(contentUri)
                    }
                    else -> null
                }
            }
            else -> null
        }
        candidate?.takeIf(::exists)
    }.getOrNull()

    /** Search likely download folders by exact name — fast with all-files access. */
    private fun findByName(name: String): String? {
        val roots = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/storage/emulated/0",
        )
        for (root in roots) {
            val direct = File(root, name)
            if (direct.exists()) return direct.absolutePath
        }
        // One-level scan of Download (covers QQ/WeChat subfolders) without recursing the whole disk.
        val download = File("/storage/emulated/0/Download")
        download.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val candidate = File(dir, name)
                if (candidate.exists()) return candidate.absolutePath
            }
        }
        return null
    }
}
