package com.wpreborn.data.notes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updated: Long,
    val starred: Boolean = false,
) {
    val isEmpty get() = title.isBlank() && body.isBlank()

    /** Checklist lines start with ☐ (open) or ☑ (done). */
    val checklist: Pair<Int, Int>?
        get() {
            val lines = body.lines().filter { it.startsWith(OPEN) || it.startsWith(DONE) }
            return if (lines.isEmpty()) null else lines.count { it.startsWith(DONE) } to lines.size
        }

    companion object {
        const val OPEN = "☐ "
        const val DONE = "☑ "
    }
}

/** Notes kept privately in the app's own storage (a small JSON file). */
class NotesRepository private constructor(context: Context) {
    /** Outlives the screens, so a note is still written when the editor closes. */
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = kotlinx.coroutines.sync.Mutex()
    private val file = File(context.filesDir, "notes.json")
    private val _notes = MutableStateFlow<List<Note>?>(null)
    val notes: StateFlow<List<Note>?> = _notes.asStateFlow()

    suspend fun load() {
        if (_notes.value != null) return
        _notes.value = withContext(Dispatchers.IO) {
            try {
                val arr = JSONArray(file.readText())
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Note(o.getString("id"), o.optString("title"), o.optString("body"), o.optLong("updated"), o.optBoolean("starred"))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun get(id: String) = _notes.value?.firstOrNull { it.id == id }

    suspend fun save(note: Note) {
        val list = _notes.value.orEmpty().filterNot { it.id == note.id }
        write(if (note.isEmpty) list else listOf(note) + list)
    }

    suspend fun delete(id: String) = write(_notes.value.orEmpty().filterNot { it.id == id })

    suspend fun toggleStar(id: String) = write(_notes.value.orEmpty().map { if (it.id == id) it.copy(starred = !it.starred) else it })

    fun saveAsync(note: Note) { scope.launch { save(note) } }
    fun deleteAsync(id: String) { scope.launch { delete(id) } }

    private suspend fun write(list: List<Note>) = lock.withLock {
        val sorted = list.sortedByDescending { it.updated }
        _notes.value = sorted
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            sorted.forEach {
                arr.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("updated", it.updated).put("starred", it.starred))
            }
            val tmp = File(file.parentFile, "notes.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    companion object {
        @Volatile
        private var instance: NotesRepository? = null

        fun get(context: Context): NotesRepository =
            instance ?: synchronized(this) { instance ?: NotesRepository(context.applicationContext).also { instance = it } }
    }
}
