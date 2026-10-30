package com.wpreborn.data.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.wpreborn.services.notifications.WpNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is currently playing, from our own player or any other app's media session. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    val packageName: String,
)

/**
 * Follows the active media sessions of other apps (Spotify, YouTube Music, ...). Requires
 * notification access, which is what Android uses to authorise getActiveSessions().
 */
object ExternalMediaWatcher {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            pick(manager?.getActiveSessions(component) ?: emptyList())
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list ?: emptyList())
    }

    fun start(context: Context) {
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val c = ComponentName(context, WpNotificationListener::class.java)
        manager = m
        component = c
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, c, handler)
            pick(m.getActiveSessions(c))
        } catch (_: SecurityException) {
            manager = null
        }
    }

    fun stop() {
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        manager = null
        _state.value = null
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    private fun pick(list: List<MediaController>) {
        // Our own player is reported by MusicController, so skip our package.
        val candidates = list.filter { it.packageName != component?.packageName }
        val best = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: candidates.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) {
            _state.value = null
            return
        }
        _state.value = NowPlaying(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
        )
    }
}
