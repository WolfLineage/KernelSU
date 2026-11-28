package com.vorthas.yusu.data.filemanager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** A bookmark is identified by its path; note/group/order are stored alongside it. */
data class Bookmark(
    val path: String,
    val note: String = "",
    val group: String = "本地",
)

/**
 * Persists file-manager bookmarks (MT Manager style) as an ordered list. The legacy
 * StringSet format is migrated on first read so existing bookmarks are not lost.
 */
class BookmarkRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fm_bookmarks", Context.MODE_PRIVATE)

    fun all(): List<Bookmark> {
        val stored = prefs.getString(KEY_ITEMS, null)
        if (stored != null) {
            return runCatching {
                val array = JSONArray(stored)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    Bookmark(
                        path = obj.getString("path"),
                        note = obj.optString("note").ifBlank {
                            obj.getString("path").substringAfterLast('/').ifBlank { obj.getString("path") }
                        },
                        group = obj.optString("group", "本地").ifBlank { "本地" },
                    )
                }.filter { it.path.isNotBlank() }
            }.getOrDefault(emptyList())
        }
        // Migrate the old unordered path set.
        val legacy = prefs.getStringSet(KEY, emptySet()).orEmpty().toList().sorted()
        return legacy.map { Bookmark(it, it.substringAfterLast('/').ifBlank { it }) }
    }

    fun save(items: List<Bookmark>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("path", item.path)
                put("note", item.note)
                put("group", item.group.ifBlank { "本地" })
            })
        }
        prefs.edit {
            putString(KEY_ITEMS, array.toString())
            remove(KEY)
        }
    }

    fun add(path: String, note: String = "", group: String = "本地") {
        if (path.isBlank()) return
        val items = all().toMutableList()
        if (items.any { it.path == path }) return
        // MT puts the target name in the note; the full path is secondary one-line info.
        val finalNote = note.ifBlank { path.substringAfterLast('/').ifBlank { path } }
        items += Bookmark(path, finalNote, group.ifBlank { "本地" })
        save(items)
    }

    fun remove(path: String) = save(all().filterNot { it.path == path })

    fun toggle(path: String) {
        if (all().any { it.path == path }) remove(path) else add(path)
    }

    fun update(path: String, note: String, group: String) = save(all().map {
        if (it.path == path) it.copy(
            note = note.ifBlank { path.substringAfterLast('/').ifBlank { path } },
            group = group.ifBlank { "本地" },
        ) else it
    })

    /** Move the item at [from] to the position of [to]; groups are preserved. */
    fun move(from: Int, to: Int) {
        val items = all().toMutableList()
        if (from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        // After removal the list is one shorter. Insertion at `items.size`
        // appends, so clamping to lastIndex would make the final slot unreachable.
        items.add(to.coerceIn(0, items.size), item)
        save(items)
    }

    private companion object {
        const val KEY = "paths"
        const val KEY_ITEMS = "items"
    }
}
