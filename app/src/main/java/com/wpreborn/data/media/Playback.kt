package com.wpreborn.data.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import com.wpreborn.services.music.MusicController
import com.wpreborn.services.music.PlayerUi
import kotlinx.coroutines.delay

/**
 * What is playing right now, for every surface of the system (the Playing Now bar, the
 * status bar, the lock screen, Action Center, the Music tile and the full player). There is
 * a single source of truth: the PulseRadio player (MusicService through [MusicController]) or,
 * when it is idle, another app's media session (Spotify...) through [ExternalMediaWatcher].
 */
data class Playback(
    val own: Boolean,
    val title: String,
    val artist: String,
    val album: String,
    val artUri: Uri?,
    val artBitmap: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val packageName: String?,
    val mediaId: String?,
    val shuffle: Boolean,
    val repeatMode: Int,
    val nextTitles: List<String>,
    val session: Int = 0,
) {
    /** Closing the Playing Now bar hides it until new music starts (or another app plays). */
    val dismissKey get() = if (own) "own:$session" else "ext:$packageName"

    val isSpotify get() = packageName == NowPlaying.SPOTIFY
    val progress get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    /** Stable key of the current track, for transitions. */
    val trackKey get() = mediaId ?: "$title|$artist"

    fun togglePlay(context: Context) = if (own) MusicController.get(context).togglePlay() else ExternalMediaWatcher.playPause()
    fun next(context: Context) { if (own) MusicController.get(context).next() else ExternalMediaWatcher.next() }
    fun previous(context: Context) { if (own) MusicController.get(context).previous() else ExternalMediaWatcher.previous() }
    fun previousTrack(context: Context) { if (own) MusicController.get(context).previousTrack() else ExternalMediaWatcher.previous() }
    fun seekTo(context: Context, ms: Long) { if (own) MusicController.get(context).seekTo(ms) else ExternalMediaWatcher.seekTo(ms) }

    /** Opens the full player: ours, or the app that is playing. */
    fun openPlayer(context: Context) {
        val intent = if (own || packageName == null) {
            Intent(context, com.wpreborn.apps.music.MusicActivity::class.java).putExtra(com.wpreborn.apps.music.MusicActivity.EXTRA_NOW_PLAYING, true)
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        }
        // SINGLE_TOP + CLEAR_TOP: an already open Music app receives the request (onNewIntent)
        // and goes to the player instead of only coming to the front.
        val flags = if (own || packageName == null) Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP else Intent.FLAG_ACTIVITY_NEW_TASK
        runCatching { context.startActivity(intent.addFlags(flags)) }
    }

    companion object {
        /**
         * PulseRadio wins while it plays, or while it has music loaded and no other app plays;
         * otherwise another app's player (Spotify, YouTube Music, Deezer...) is shown and
         * controlled, marked with that app's name and icon.
         */
        fun of(ui: PlayerUi, external: NowPlaying?, now: Long = SystemClock.elapsedRealtime()): Playback? = when {
            ui.hasMedia && (ui.playing || external?.playing != true) -> Playback(
                own = true,
                title = ui.title,
                artist = ui.artist,
                album = ui.album,
                artUri = ui.artUri,
                artBitmap = null,
                playing = ui.playing,
                positionMs = ui.positionMs,
                durationMs = ui.durationMs,
                packageName = null,
                mediaId = ui.mediaId,
                shuffle = ui.shuffle,
                repeatMode = ui.repeatMode,
                nextTitles = ui.nextTitles,
                session = ui.session,
            )
            external != null && (external.title.isNotBlank() || external.artist.isNotBlank()) -> Playback(
                own = false,
                title = external.title,
                artist = external.artist,
                album = external.album,
                artUri = null,
                artBitmap = external.art,
                playing = external.playing,
                positionMs = external.position(now),
                durationMs = external.durationMs,
                packageName = external.packageName,
                mediaId = null,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_OFF,
                nextTitles = emptyList(),
            )
            else -> null
        }
    }
}

/**
 * The current [Playback], kept up to date (including the position of other apps' players,
 * which only report it on changes).
 */
@Composable
fun rememberPlayback(includeExternal: Boolean = true): Playback? {
    val context = LocalContext.current
    val controller = remember { MusicController.get(context) }
    val ui by controller.ui.collectAsState()
    // Inside PulseRadio only its own player counts; everywhere else other apps' too.
    val ext by ExternalMediaWatcher.state.collectAsState()
    val external: NowPlaying? = if (includeExternal) ext else null
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(external?.playing) {
        while (external?.playing == true) {
            tick = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    return remember(ui, external, tick) { Playback.of(ui, external, tick) }
}

/** When music last played, so a paused player can leave the lock screen and Action Center. */
object PlaybackIdle {
    var lastPlayingAt by androidx.compose.runtime.mutableLongStateOf(0L)
}

/**
 * [playback] while it plays, and for [graceMs] after it was paused (to resume with one tap);
 * then null, so a stopped player no longer stays on the lock screen or in Action Center.
 */
@Composable
fun rememberPlaybackVisible(playback: Playback?, graceMs: Long = 30_000): Playback? {
    val playing = playback?.playing == true
    var wasPlaying by remember { androidx.compose.runtime.mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing) {
        if (playing) {
            PlaybackIdle.lastPlayingAt = SystemClock.elapsedRealtime()
            wasPlaying = true
        } else {
            if (wasPlaying) {
                PlaybackIdle.lastPlayingAt = SystemClock.elapsedRealtime()
                wasPlaying = false
            }
            tick = SystemClock.elapsedRealtime()
            val left = PlaybackIdle.lastPlayingAt + graceMs - tick
            if (left > 0) {
                delay(left)
                tick = SystemClock.elapsedRealtime()
            }
        }
    }
    val visible = playing || (playback != null && tick - PlaybackIdle.lastPlayingAt < graceMs)
    return playback.takeIf { visible }
}

/** The Playing Now bar the user closed (by [Playback.dismissKey]); it returns with new music. */
object PlayingNowDismissal {
    var dismissed by androidx.compose.runtime.mutableStateOf<String?>(null)
}
