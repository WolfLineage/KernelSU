package com.vorthas.yusu.data.filemanager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Persists file-manager bookmarks (MT Manager style): a simple ordered list of paths. */
class BookmarkRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fm_bookmarks", Context.MODE_PRIVATE)

    fun all(): List<String> = prefs.getStringSet(KEY, emptySet())
        ?.toList()?.sorted() ?: emptyList()

    fun add(path: String) {
        if (path.isBlank()) return
        prefs.edit { putStringSet(KEY, (all() + path).toSet()) }
    }

    fun remove(path: String) = prefs.edit { putStringSet(KEY, (all() - path).toSet()) }

    fun toggle(path: String) {
        if (path in all()) remove(path) else add(path)
    }

    private companion object {
        const val KEY = "paths"
    }
}
