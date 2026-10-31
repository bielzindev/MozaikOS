package com.wpreborn.data.media

import android.content.Context
import org.json.JSONObject

/** Tags the user set for a song in ZUNEX Music (blank or 0 = keep the file's own). */
data class SongTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: Int = 0,
    val track: Int = 0,
)

/**
 * Song tags edited in ZUNEX Music. Android doesn't let apps rewrite the tags inside music
 * files, so the edits are kept by ZUNEX and applied wherever the library is shown and played
 * (lists, albums, artists, the player, the lock screen and the Music tile).
 */
class TagStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("music_tags", Context.MODE_PRIVATE)

    fun get(id: Long): SongTags? = prefs.getString(id.toString(), null)?.let { s ->
        runCatching {
            val o = JSONObject(s)
            SongTags(o.optString("t"), o.optString("a"), o.optString("al"), o.optString("g"), o.optInt("y"), o.optInt("n"))
        }.getOrNull()
    }

    /** Merges [edit] into each song's tags: blank / 0 fields keep what was there. */
    fun update(ids: Collection<Long>, edit: SongTags) {
        val e = prefs.edit()
        for (id in ids) {
            val old = get(id) ?: SongTags()
            val t = SongTags(
                title = edit.title.ifBlank { old.title },
                artist = edit.artist.ifBlank { old.artist },
                album = edit.album.ifBlank { old.album },
                genre = edit.genre.ifBlank { old.genre },
                year = if (edit.year > 0) edit.year else old.year,
                track = if (edit.track > 0) edit.track else old.track,
            )
            e.putString(
                id.toString(),
                JSONObject().put("t", t.title).put("a", t.artist).put("al", t.album).put("g", t.genre).put("y", t.year).put("n", t.track).toString(),
            )
        }
        e.apply()
    }

    fun clear(ids: Collection<Long>) {
        val e = prefs.edit()
        ids.forEach { e.remove(it.toString()) }
        e.apply()
    }

    /** The song with the user's tags applied. */
    fun apply(s: Song): Song {
        val t = get(s.id) ?: return s
        return s.copy(
            title = t.title.ifBlank { s.title },
            artist = t.artist.ifBlank { s.artist },
            album = t.album.ifBlank { s.album },
            genre = t.genre.ifBlank { s.genre ?: "" }.ifBlank { null },
            year = if (t.year > 0) t.year else s.year,
            track = if (t.track > 0) t.track else s.track,
        )
    }

    companion object {
        @Volatile private var instance: TagStore? = null
        fun get(context: Context): TagStore = instance ?: synchronized(this) { instance ?: TagStore(context.applicationContext).also { instance = it } }
    }
}
