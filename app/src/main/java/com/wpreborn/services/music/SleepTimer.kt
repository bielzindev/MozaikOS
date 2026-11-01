package com.wpreborn.services.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Sleep timer, kept by the playback service so it works with the app closed: at the time set
 * the music fades out over ten seconds and pauses; or it pauses at the end of the song.
 */
class SleepTimer(context: Context, private val player: ExoPlayer) {
    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && prefs.getBoolean(KEY_END_OF_SONG, false)) {
                    player.pause()
                    clear(prefs)
                }
            }
        })
        handler.post(tick)
    }

    private fun check() {
        val at = prefs.getLong(KEY_AT, 0)
        if (at <= 0) return
        val left = at - System.currentTimeMillis()
        when {
            left <= 0 -> {
                player.pause()
                player.volume = 1f
                clear(prefs)
            }
            left < 10_000 && player.isPlaying -> player.volume = (left / 10_000f).coerceIn(0f, 1f)
        }
    }

    fun release() = handler.removeCallbacks(tick)

    companion object {
        private const val KEY_AT = "sleepAt"
        private const val KEY_END_OF_SONG = "sleepEndOfSong"

        private fun prefs(c: Context) = c.getSharedPreferences("music", Context.MODE_PRIVATE)

        private fun clear(p: android.content.SharedPreferences) = p.edit().putLong(KEY_AT, 0).putBoolean(KEY_END_OF_SONG, false).apply()

        fun set(c: Context, minutes: Int) = prefs(c).edit().putLong(KEY_AT, System.currentTimeMillis() + minutes * 60_000L).putBoolean(KEY_END_OF_SONG, false).apply()

        fun endOfSong(c: Context) = prefs(c).edit().putLong(KEY_AT, 0).putBoolean(KEY_END_OF_SONG, true).apply()

        fun cancel(c: Context) = clear(prefs(c))

        /** Minutes left (rounded up), -1 for "end of song", or null when off. */
        fun state(c: Context): Int? {
            val p = prefs(c)
            if (p.getBoolean(KEY_END_OF_SONG, false)) return -1
            val at = p.getLong(KEY_AT, 0)
            if (at <= System.currentTimeMillis()) return null
            return ((at - System.currentTimeMillis() + 59_999) / 60_000).toInt()
        }
    }
}
