package com.wpreborn.services.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wpreborn.data.media.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUi(
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artUri: Uri? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val queueSize: Int = 0,
    val nextTitles: List<String> = emptyList(),
)

/** App-side handle on [MusicService] through a Media3 MediaController. */
class MusicController private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var connecting = false
    private var ticker: Job? = null

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    /** Connects lazily; the service is only started when something is played. */
    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try {
                future.get()
            } catch (_: Exception) {
                null
            } ?: return@addListener
            controller = c
            c.addListener(listener)
            pending.forEach { it(c) }
            pending.clear()
            publish()
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) = withController { c ->
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)), 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun togglePlay() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                if (controller?.isPlaying == true) publish()
                delay(500)
            }
        }
    }

    private fun publish() {
        val c = controller ?: return
        val md = c.mediaMetadata
        val next = buildList {
            var i = c.nextMediaItemIndex
            var guard = 0
            while (i != androidx.media3.common.C.INDEX_UNSET && guard < 4) {
                add(c.getMediaItemAt(i).mediaMetadata.title?.toString().orEmpty())
                i = c.currentTimeline.getNextWindowIndex(i, c.repeatMode, c.shuffleModeEnabled)
                guard++
            }
        }
        _ui.value = PlayerUi(
            hasMedia = c.mediaItemCount > 0,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            album = md.albumTitle?.toString().orEmpty(),
            artUri = md.extras?.getString(EXTRA_ART)?.let(Uri::parse) ?: md.artworkUri,
            playing = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            index = c.currentMediaItemIndex,
            queueSize = c.mediaItemCount,
            nextTitles = next,
        )
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                // No artworkUri: System UI tries to resolve it itself and crashes on MediaStore
                // album URIs. ExoPlayer reads the embedded cover for the notification instead;
                // our own screens get the album URI through the extras.
                .setExtras(android.os.Bundle().apply { putString(EXTRA_ART, albumArtUri.toString()) })
                .build(),
        )
        .build()

    companion object {
        private const val EXTRA_ART = "wp_art_uri"

        @Volatile
        private var instance: MusicController? = null

        fun get(context: Context): MusicController =
            instance ?: synchronized(this) {
                instance ?: MusicController(context.applicationContext).also { instance = it }
            }
    }
}
