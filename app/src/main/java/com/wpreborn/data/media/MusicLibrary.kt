package com.wpreborn.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    /** Seconds since the epoch, from MediaStore. */
    val dateAdded: Long = 0,
    val year: Int = 0,
    val genre: String? = null,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val albumArtUri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
}

data class Album(val id: Long, val title: String, val artist: String, val songs: List<Song>, val artId: Long = id) {
    val artUri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, artId)
}

data class Artist(val name: String, val albums: List<Album>, val songs: List<Song>)

data class Playlist(val id: String, val name: String, val songIds: List<Long>, val system: Boolean)

data class MusicCollection(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    private val byId by lazy { songs.associateBy { it.id } }
    fun songsOf(p: Playlist) = p.songIds.mapNotNull { byId[it] }
}

/** Reads the local music collection from MediaStore. Requires READ_MEDIA_AUDIO. */
class MusicLibrary(private val context: Context) {

    /** "Unknown" artist hidden: such songs show no artist and don't form an artist. */
    var hideUnknown: Boolean
        get() = context.getSharedPreferences("music", Context.MODE_PRIVATE).getBoolean("hideUnknown", false)
        set(v) = context.getSharedPreferences("music", Context.MODE_PRIVATE).edit().putBoolean("hideUnknown", v).apply()

    suspend fun load(): MusicCollection = withContext(Dispatchers.IO) {
        val tags = TagStore.get(context)
        val hide = hideUnknown
        val songs = querySongs().map { tags.apply(it) }.map { if (hide && it.artist == UNKNOWN) it.copy(artist = "") else it }
            .sortedBy { it.title.lowercase() }
        // Songs whose album was renamed in ZUNEX form an album of that name.
        val albums = songs.groupBy { s -> if (tags.get(s.id)?.album.isNullOrBlank()) s.albumId else -(1L + ((s.album.lowercase() + "|" + s.artist.lowercase()).hashCode().toLong() and 0x7FFFFFFF)) }
            .map { (id, list) ->
                Album(id, list.first().album, list.first().artist, list.sortedBy { it.track }, artId = list.first().albumId)
            }.sortedBy { it.title.lowercase() }
        val artists = songs.filter { it.artist.isNotBlank() }.groupBy { it.artist }.map { (name, list) ->
            Artist(name, albums.filter { a -> a.songs.any { it.artist == name } }, list.sortedBy { it.title.lowercase() })
        }.sortedBy { it.name.lowercase() }
        MusicCollection(songs, albums, artists, systemPlaylists() + userPlaylists())
    }

    private fun querySongs(): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.YEAR,
        ) + if (android.os.Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray()
        val result = ArrayList<Song>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val artist = c.getString(2)
                    result += Song(
                        id = c.getLong(0),
                        title = c.getString(1) ?: "",
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") UNKNOWN else artist,
                        album = c.getString(3) ?: UNKNOWN,
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        track = c.getInt(6) % 1000,
                        dateAdded = c.getLong(7),
                        year = c.getInt(8),
                        genre = if (c.columnCount > 9) c.getString(9)?.takeIf { it.isNotBlank() && it != "<unknown>" } else null,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun systemPlaylists(): List<Playlist> {
        val list = ArrayList<Playlist>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME),
                null, null, MediaStore.Audio.Playlists.NAME,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val ids = ArrayList<Long>()
                    context.contentResolver.query(
                        MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                        arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),
                        null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                    )?.use { m -> while (m.moveToNext()) ids += m.getLong(0) }
                    list += Playlist("sys:$id", c.getString(1) ?: "", ids, system = true)
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    private val playlistFile get() = File(context.filesDir, "playlists.json")

    fun userPlaylists(): List<Playlist> = try {
        val arr = JSONArray(playlistFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("songs")
            Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) }, system = false)
        }
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun saveUserPlaylists(list: List<Playlist>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        list.filterNot { it.system }.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds)))
        }
        playlistFile.writeText(arr.toString())
    }

    suspend fun createPlaylist(name: String): Playlist {
        val p = Playlist("user:${System.currentTimeMillis()}", name, emptyList(), system = false)
        saveUserPlaylists(userPlaylists() + p)
        return p
    }

    suspend fun addToPlaylist(playlistId: String, songIds: List<Long>) {
        saveUserPlaylists(userPlaylists().map { if (it.id == playlistId) it.copy(songIds = it.songIds + songIds) else it })
    }

    suspend fun deletePlaylist(playlistId: String) {
        saveUserPlaylists(userPlaylists().filterNot { it.id == playlistId })
    }

    /** Deletes several playlists: ZUNEX ones, and Android's (MediaStore) where allowed. */
    @Suppress("DEPRECATION")
    suspend fun deletePlaylists(ids: Set<String>) = withContext(Dispatchers.IO) {
        saveUserPlaylists(userPlaylists().filterNot { it.id in ids })
        ids.filter { it.startsWith("sys:") }.forEach { id ->
            runCatching {
                val n = id.removePrefix("sys:").toLong()
                context.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, n), null, null)
            }
        }
    }

    suspend fun renamePlaylist(playlistId: String, name: String) {
        saveUserPlaylists(userPlaylists().map { if (it.id == playlistId) it.copy(name = name) else it })
    }

    /** Takes the songs at [positions] out of a ZUNEX playlist (the files stay). */
    suspend fun removeFromPlaylist(playlistId: String, positions: Set<Int>) {
        saveUserPlaylists(userPlaylists().map { p -> if (p.id == playlistId) p.copy(songIds = p.songIds.filterIndexed { i, _ -> i !in positions }) else p })
    }

    companion object {
        const val UNKNOWN = "Unknown"
    }
}
