package com.wpreborn.services.music

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the play queue, the current song and its position, so music picks up where it
 * left off after the app or the phone restarts (paused, like on Windows Phone).
 */
class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("music_queue", Context.MODE_PRIVATE)

    val hasQueue: Boolean get() = prefs.getString(KEY_ITEMS, null)?.let { it.length > 2 } == true

    fun save(player: Player) {
        if (player.mediaItemCount == 0) {
            prefs.edit().remove(KEY_ITEMS).apply()
            return
        }
        // The queue rarely changes: only rewrite it when it did.
        val ids = (0 until player.mediaItemCount).joinToString(",") { player.getMediaItemAt(it).mediaId }
        val edit = prefs.edit()
        if (ids != prefs.getString(KEY_IDS, null)) {
            val arr = JSONArray()
            for (i in 0 until player.mediaItemCount) {
                val item = player.getMediaItemAt(i)
                val md = item.mediaMetadata
                arr.put(
                    JSONObject()
                        .put("id", item.mediaId)
                        .put("uri", item.localConfiguration?.uri?.toString())
                        .put("title", md.title?.toString())
                        .put("artist", md.artist?.toString())
                        .put("album", md.albumTitle?.toString())
                        .put("art", md.extras?.getString(MusicController.EXTRA_ART)),
                )
            }
            edit.putString(KEY_ITEMS, arr.toString()).putString(KEY_IDS, ids)
        }
        edit.putInt(KEY_INDEX, player.currentMediaItemIndex)
            .putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0))
            .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            .putInt(KEY_REPEAT, player.repeatMode)
            .apply()
    }

    /** Loads the saved queue into an empty player, paused. */
    fun restore(player: Player) {
        if (player.mediaItemCount > 0) return
        val arr = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]")) }.getOrNull() ?: return
        if (arr.length() == 0) return
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val uri = o.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MediaItem.Builder()
                .setMediaId(o.optString("id"))
                .setUri(Uri.parse(uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(o.optString("title"))
                        .setArtist(o.optString("artist"))
                        .setAlbumTitle(o.optString("album"))
                        .setExtras(android.os.Bundle().apply { putString(MusicController.EXTRA_ART, o.optString("art")) })
                        .build(),
                )
                .build()
        }
        if (items.isEmpty()) return
        val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, items.lastIndex)
        player.setMediaItems(items, index, prefs.getLong(KEY_POSITION, 0))
        player.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
        player.repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
        player.prepare()
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val KEY_IDS = "ids"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
    }
}
