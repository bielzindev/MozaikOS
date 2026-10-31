package com.wpreborn.apps.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.wpreborn.R
import com.wpreborn.apps.common.LocalChrome
import com.wpreborn.apps.common.PlaybackArt
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.Favorites
import com.wpreborn.data.media.Playback
import com.wpreborn.data.media.rememberPlayback
import com.wpreborn.services.music.MusicController
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Now playing, as Windows Phone 8.1 drew it: the app title, the lowercase "now playing"
 * header, a square cover, the song in large light type, the artist in the accent colour and
 * the album dimmed, a thin accent progress bar with the times, and the transport row
 * (repeat, previous, play/pause in its circle, next, shuffle). The app bar's "..." holds
 * favourite, up next and the equalizer. The cover swipes to change song, like Xbox Music.
 */
@Composable
fun NowPlayingScreen(onEqualizer: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { MusicController.get(context) }
    val playback = rememberPlayback()
    val favorites by remember { Favorites.get(context) }.ids.collectAsState()
    var showQueue by remember { mutableStateOf(false) }

    // The Playing Now bar would repeat this screen.
    val chrome = LocalChrome.current
    DisposableEffect(chrome) {
        chrome?.hidePlayingNow = true
        onDispose { chrome?.hidePlayingNow = false }
    }

    val appTitle = when {
        playback == null || playback.own -> stringResource(R.string.music_title)
        playback.isSpotify -> "SPOTIFY"
        else -> stringResource(R.string.music_other_app)
    }

    Column(Modifier.fillMaxSize()) {
        WpText(appTitle.uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
        WpText(
            stringResource(if (showQueue) R.string.music_up_next else R.string.music_now_playing),
            style = WpType.Header,
            maxLines = 1,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        if (playback == null) {
            WpText(stringResource(R.string.music_nothing_playing), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
            return@Column
        }

        if (showQueue && playback.own) {
            Queue(playback, Modifier.weight(1f)) { i -> controller.skipAhead(i + 1); showQueue = false }
        } else {
            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    val side = minOf(maxWidth, maxHeight)
                    Cover(playback, Modifier.size(side)) { forward -> if (forward) playback.next(context) else playback.previous(context) }
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    playback.trackKey,
                    transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn(tween(250))) togetherWith fadeOut(tween(120)) },
                    label = "track",
                ) { _ ->
                    Column {
                        WpText(playback.title, style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WpText(playback.artist, style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = Wp.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (playback.album.isNotBlank()) {
                            WpText(playback.album, style = WpType.Normal, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(playback) { playback.seekTo(context, it) }
            }
            // Transport.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Toggle(
                    if (playback.repeatMode == Player.REPEAT_MODE_ONE) WpIcons.RepeatOne else WpIcons.Repeat,
                    on = playback.repeatMode != Player.REPEAT_MODE_OFF,
                    enabled = playback.own,
                ) { controller.cycleRepeat() }
                Glyph(WpIcons.Previous) { playback.previous(context) }
                RoundButton(
                    if (playback.playing) WpIcons.Pause else WpIcons.Play,
                    size = 64.dp,
                    contentDescription = stringResource(if (playback.playing) R.string.music_pause else R.string.music_play),
                ) { playback.togglePlay(context) }
                Glyph(WpIcons.Next) { playback.next(context) }
                Toggle(WpIcons.Shuffle, on = playback.shuffle, enabled = playback.own) { controller.toggleShuffle() }
            }
        }

        // Mini app bar: "..." opens the menu, as in the 8.1 player.
        val id = playback.mediaId
        WpAppBar(
            buttons = emptyList(),
            menuItems = if (playback.own) {
                listOfNotNull(
                    id?.let { AppBarMenuItem(stringResource(if (it in favorites) R.string.music_unfavorite else R.string.music_favorite)) { Favorites.get(context).toggle(it) } },
                    AppBarMenuItem(stringResource(if (showQueue) R.string.music_now_playing else R.string.music_up_next)) { showQueue = !showQueue },
                    AppBarMenuItem(stringResource(R.string.eq_title)) { onEqualizer() },
                )
            } else {
                listOf(AppBarMenuItem(stringResource(R.string.music_open_app)) { playback.packageName?.let { openPackage(context, it) } })
            },
        )
    }
}

/** Square cover; swipe left / right for next / previous. */
@Composable
private fun Cover(playback: Playback, modifier: Modifier, onSwipe: (forward: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    Box(
        modifier
            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
            .offset { IntOffset(swipe.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = swipe.value
                            if (abs(v) > widthPx * 0.3f) {
                                swipe.animateTo(if (v < 0) -widthPx * 1.2f else widthPx * 1.2f, tween(160))
                                onSwipe(v < 0)
                                swipe.snapTo(if (v < 0) widthPx else -widthPx)
                            }
                            swipe.animateTo(0f, tween(220))
                        }
                    },
                ) { change, dx ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + dx) }
                }
            },
    ) {
        PlaybackArt(playback, Modifier.fillMaxSize(), sizePx = 768)
    }
}

/** The 8.1 progress bar: a thin accent line on a dim track, elapsed and total time under it. */
@Composable
private fun ProgressBar(playback: Playback, onSeek: (Long) -> Unit) {
    val duration = playback.durationMs
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: playback.progress
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(duration) { detectTapGestures { if (duration > 0) onSeek((it.x / size.width * duration).toLong()) } }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        change.consume()
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Wp.palette.raised))
            Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.001f)).height(4.dp).background(Wp.accent))
        }
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging != null) (dragging!! * duration).toLong() else playback.positionMs
            WpText(WpFormat.duration(shown), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.weight(1f))
            WpText(if (duration > 0) WpFormat.duration(duration) else "", style = WpType.Small, color = Wp.palette.subtle)
        }
    }
}

@Composable
private fun Glyph(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).wpClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        WpIcon(icon, size = 30.dp)
    }
}

/** Repeat / shuffle: full foreground when on, dimmed when off. */
@Composable
private fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).wpClickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        WpIcon(icon, tint = if (on && enabled) Wp.accent else Wp.palette.subtle.copy(alpha = if (enabled) 1f else 0.4f), size = 24.dp)
    }
}

@Composable
private fun Queue(playback: Playback, modifier: Modifier, onPick: (Int) -> Unit) {
    LazyColumn(modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp)) {
        if (playback.nextTitles.isEmpty()) {
            item { WpText(stringResource(R.string.music_queue_end), style = WpType.Large, color = Wp.palette.subtle) }
        }
        itemsIndexed(playback.nextTitles) { i, title ->
            WpText(
                title,
                style = WpType.ExtraLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().slideInStaggered(i).wpClickable { onPick(i) }.padding(vertical = 6.dp),
            )
        }
    }
}

/** Thin bar with times, for other screens (lock screen): tap or drag to seek. */
@Composable
fun SeekBar(positionMs: Long, durationMs: Long, accent: Color, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(durationMs) { detectTapGestures { if (durationMs > 0) onSeek((it.x / size.width * durationMs).toLong()) } }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        change.consume()
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f)))
            Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.001f)).height(4.dp).background(accent))
        }
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging != null) (dragging!! * durationMs).toLong() else positionMs
            WpText(WpFormat.duration(shown), style = WpType.Small, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            WpText(if (durationMs > 0) WpFormat.duration(durationMs) else "", style = WpType.Small, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

internal fun openPackage(context: android.content.Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
