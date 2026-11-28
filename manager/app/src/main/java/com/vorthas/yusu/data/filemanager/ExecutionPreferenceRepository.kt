package com.vorthas.yusu.data.filemanager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Remembers the execution choices made in the script dialog so the user does not have to
 * reselect ROOT and PTY/PIPESO on every run. Settings are confirmed explicitly: cancelling
 * the settings dialog leaves the stored preference untouched.
 */
class ExecutionPreferenceRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fm_execution_prefs", Context.MODE_PRIVATE)

    fun load(): ExecutionPreference = ExecutionPreference(
        root = prefs.getBoolean(KEY_ROOT, true),
        pipe = prefs.getBoolean(KEY_PIPE, false),
    )

    fun save(preference: ExecutionPreference) = prefs.edit {
        putBoolean(KEY_ROOT, preference.root)
        putBoolean(KEY_PIPE, preference.pipe)
    }

    private companion object {
        const val KEY_ROOT = "root"
        const val KEY_PIPE = "pipe"
    }
}

data class ExecutionPreference(val root: Boolean, val pipe: Boolean)
