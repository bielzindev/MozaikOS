package com.wpreborn.services.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Gradual transition (crossfade) between songs. In the last seconds of a song the next one
 * starts in a second player and the two cross: one fades out while the other fades in. When
 * the main player reaches the next song it takes over at the second player's position and
 * the second player stops. Skipping, seeking or pausing cancels the crossing.
 */
class Crossfade(private val context: Context, private val main: ExoPlayer) {
    private val handler = Handler(Looper.getMainLooper())
    private var aux: ExoPlayer? = null
    private var auxIndex = C.INDEX_UNSET

    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    private val fadeMs get() = prefs.getInt("crossfadeSec", 0) * 1000L

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, 100)
        }
    }

    init {
        main.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val a = aux
                if (a != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && main.currentMediaItemIndex == auxIndex) {
                    // The main player arrives at the song already playing in the second one.
                    main.seekTo(a.currentPosition + 60)
                    main.volume = 1f
                    releaseAux()
                } else {
                    cancel()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && main.playbackState != Player.STATE_ENDED) cancel()
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) cancel()
            }
        })
        handler.post(tick)
    }

    private fun step() {
        val fade = fadeMs
        if (fade <= 0 || !main.isPlaying) return
        val duration = main.duration
        if (duration == C.TIME_UNSET || duration < fade * 3) return
        val remaining = duration - main.currentPosition
        if (remaining > fade) {
            if (aux == null && main.volume < 1f) main.volume = 1f
            return
        }
        if (aux == null) {
            if (main.repeatMode == Player.REPEAT_MODE_ONE) return
            val next = main.nextMediaItemIndex
            if (next == C.INDEX_UNSET) return
            auxIndex = next
            aux = ExoPlayer.Builder(context)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
                .build()
                .apply {
                    setMediaItem(main.getMediaItemAt(next))
                    volume = 0f
                    prepare()
                    play()
                }
        }
        val p = (1f - remaining.toFloat() / fade).coerceIn(0f, 1f)
        main.volume = 1f - p
        aux?.volume = p
    }

    private fun cancel() {
        if (aux != null) releaseAux()
        main.volume = 1f
    }

    private fun releaseAux() {
        aux?.release()
        aux = null
        auxIndex = C.INDEX_UNSET
    }

    fun release() {
        handler.removeCallbacks(tick)
        releaseAux()
    }

    companion object {
        val choices = listOf(0, 3, 6, 10)

        fun seconds(context: Context) = context.getSharedPreferences("music", Context.MODE_PRIVATE).getInt("crossfadeSec", 0)

        /** Off → 3 s → 6 s → 10 s → off. */
        fun cycle(context: Context): Int {
            val now = seconds(context)
            val next = choices[(choices.indexOf(now).coerceAtLeast(0) + 1) % choices.size]
            context.getSharedPreferences("music", Context.MODE_PRIVATE).edit().putInt("crossfadeSec", next).apply()
            return next
        }
    }
}
