package com.vorthas.yusu.data.filemanager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Preference key for a pane's home path. Kept top-level so it can be unit-tested. */
internal fun homePathKey(pane: Int): String = if (pane == 0) "home_left" else "home_right"

/**
 * Persists the per-pane "home" directory shown when the file manager opens.
 * The two panes are stored under separate keys so setting a home on the focused
 * pane can never overwrite the other pane's home.
 */
class HomePathRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fm_home_paths", Context.MODE_PRIVATE)

    /** [pane] must be 0 (left) or 1 (right). Returns null when no home was set. */
    fun get(pane: Int): String? = prefs.getString(key(pane), null)?.takeIf { it.isNotBlank() }

    fun set(pane: Int, path: String) {
        if (pane !in 0..1 || path.isBlank()) return
        prefs.edit { putString(key(pane), path) }
    }

    private fun key(pane: Int): String = homePathKey(pane)
}
