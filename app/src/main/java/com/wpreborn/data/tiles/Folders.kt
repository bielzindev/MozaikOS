package com.wpreborn.data.tiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A Start folder: a tile that holds several apps. [apps] are [com.wpreborn.data.apps.AppEntry] keys. */
data class Folder(val id: String, val name: String, val apps: List<String>, val sizes: Map<String, TileSize> = emptyMap()) {
    /** The size the app's tile had on Start; its tile keeps it inside the folder. */
    fun sizeOf(app: String) = sizes[app] ?: TileSize.Medium
}

/** The folders of the Start screen. Their tile has the app key "folder:<id>". */
class FolderStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("start_folders", Context.MODE_PRIVATE)
    private val _folders = MutableStateFlow(read())
    val folders: StateFlow<Map<String, Folder>> = _folders.asStateFlow()

    fun create(name: String, apps: List<Pair<String, TileSize>>): Folder {
        val f = Folder(UUID.randomUUID().toString(), name, apps.map { it.first }.distinct(), apps.toMap())
        save(_folders.value + (f.id to f))
        return f
    }

    fun add(id: String, app: String, size: TileSize = TileSize.Medium) {
        val f = _folders.value[id] ?: return
        if (app !in f.apps) save(_folders.value + (id to f.copy(apps = f.apps + app, sizes = f.sizes + (app to size))))
    }

    /** Takes an app out; returns the folder as it is now (null if it no longer exists). */
    fun remove(id: String, app: String): Folder? {
        val f = _folders.value[id] ?: return null
        val n = f.copy(apps = f.apps - app, sizes = f.sizes - app)
        save(_folders.value + (id to n))
        return n
    }

    fun rename(id: String, name: String) {
        val f = _folders.value[id] ?: return
        if (name.isNotBlank()) save(_folders.value + (id to f.copy(name = name.trim())))
    }

    fun delete(id: String) = save(_folders.value - id)

    private fun save(map: Map<String, Folder>) {
        _folders.value = map
        val arr = JSONArray()
        map.values.forEach { f ->
            val sizes = JSONObject()
            f.sizes.forEach { (k, v) -> sizes.put(k, v.name) }
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)).put("sizes", sizes))
        }
        prefs.edit().putString("folders", arr.toString()).apply()
    }

    private fun read(): Map<String, Folder> = runCatching {
        val arr = JSONArray(prefs.getString("folders", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val a = o.getJSONArray("apps")
            val apps = List(a.length()) { a.getString(it) }
            val sz = o.optJSONObject("sizes")
            val sizes = apps.mapNotNull { k -> sz?.optString(k)?.let { n -> runCatching { TileSize.valueOf(n) }.getOrNull() }?.let { k to it } }.toMap()
            Folder(o.getString("id"), o.optString("name"), apps, sizes)
        }.associateBy { it.id }
    }.getOrDefault(emptyMap())

    companion object {
        @Volatile private var instance: FolderStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: FolderStore(context.applicationContext).also { instance = it } }
        const val PREFIX = "folder:"
    }
}

val TileSpec.isFolder get() = appKey.startsWith(FolderStore.PREFIX)
val TileSpec.folderId get() = appKey.removePrefix(FolderStore.PREFIX)
