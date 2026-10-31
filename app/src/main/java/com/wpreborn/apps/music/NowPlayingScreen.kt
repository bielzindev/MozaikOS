package com.wpreborn.apps.music

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.ExternalMediaWatcher
import com.wpreborn.data.media.NowPlaying
import com.wpreborn.services.music.MusicController
import com.wpreborn.services.music.PlayerUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the now playing screen shows: our own player, or another app such as Spotify. */
private class Source(
    val title: String,
    val artist: String,
    val album: String,
    val artUri: Uri?,
    val artBitmap: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val external: NowPlaying?,
)

/**
 * Now playing, after Xbox Music on Windows Phone 8.1: the album art fills the screen behind
 * everything (slowly drifting), the artist name runs across the top in huge light type, the
 * cover can be swiped to change song, and the queue slides over the art. Works for songs
 * playing in ZUNEX and for Spotify or any other player (with notification access).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(ui: PlayerUi, external: NowPlaying?, controller: MusicController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Our player wins while it has something loaded and is playing, or nothing else plays.
    val useOwn = ui.hasMedia && (ui.playing || external?.playing != true)
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(external?.playing) {
        while (external?.playing == true) {
            tick = android.os.SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val src = when {
        useOwn -> Source(ui.title, ui.artist, ui.album, ui.artUri, null, ui.playing, ui.positionMs, ui.durationMs, null)
        external != null -> Source(external.title, external.artist, external.album, null, external.art, external.playing, external.position(tick.takeIf { it > 0 } ?: android.os.SystemClock.elapsedRealtime()), external.durationMs, external)
        else -> null
    }
    var showQueue by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (src == null) {
            Column(Modifier.padding(start = 20.dp, top = 14.dp)) {
                WpText(stringResource(R.string.music_now_playing).uppercase(), style = WpType.AppTitle, color = Color.White)
                WpText(stringResource(R.string.music_nothing_playing), style = WpType.Large, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 24.dp))
            }
            return@Box
        }

        // Background: the cover, enlarged, drifting slowly, blurred where the system allows.
        val drift = rememberInfiniteTransition(label = "drift")
        val t by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "t")
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.25f + 0.15f * t
                    scaleY = scaleX
                    translationX = (t - 0.5f) * size.width * 0.12f
                }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier),
        ) {
            Art(src, Modifier.fillMaxSize(), 768)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.85f)))))

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                WpText(
                    (if (src.external?.isSpotify == true) "SPOTIFY · " else "") + stringResource(R.string.music_now_playing).uppercase(),
                    style = WpType.AppTitle,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
            // Huge artist name, running across the screen like Xbox Music's.
            WpText(
                src.artist.lowercase().ifBlank { " " },
                style = WpType.PanoramaTitle.copy(fontSize = 72.sp, lineHeight = 80.sp),
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 40.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Cover: swipe left / right for next / previous.
                val swipe = remember { Animatable(0f) }
                var widthPx by remember { mutableFloatStateOf(1f) }
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f)
                            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
                            .offset { IntOffset(swipe.value.roundToInt(), 0) }
                            .graphicsLayer { alpha = 1f - (abs(swipe.value) / (widthPx * 1.4f)).coerceIn(0f, 0.8f) }
                            .shadow(18.dp)
                            .background(Wp.accent)
                            .pointerInput(src.external != null) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            val v = swipe.value
                                            if (abs(v) > widthPx * 0.3f) {
                                                swipe.animateTo(if (v < 0) -widthPx * 1.3f else widthPx * 1.3f, tween(160))
                                                if (v < 0) next(controller, src) else previous(controller, src)
                                                swipe.snapTo(if (v < 0) widthPx * 0.6f else -widthPx * 0.6f)
                                            }
                                            swipe.animateTo(0f, tween(220))
                                        }
                                    },
                                ) { change, dx ->
                                    change.consume()
                                    scope.launch { swipe.snapTo(swipe.value + dx) }
                                }
                            }
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { togglePlay(controller, src) }) },
                        contentAlignment = Alignment.Center,
                    ) {
                        WpIcon(WpIcons.MusicNote, tint = Color.White, size = 72.dp)
                        Art(src, Modifier.fillMaxSize(), 768)
                    }
                    Spacer(Modifier.height(16.dp))
                    WpText(
                        src.title,
                        style = WpType.ExtraLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WpText(
                        listOf(src.album, src.artist).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = WpType.Normal,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // The queue slides over the art.
                if (showQueue && useOwn) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        WpText(stringResource(R.string.music_up_next), style = WpType.PivotHeader, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                        if (ui.nextTitles.isEmpty()) WpText(stringResource(R.string.music_queue_end), style = WpType.Large, color = Color.White.copy(alpha = 0.6f))
                        ui.nextTitles.forEachIndexed { i, title ->
                            WpText(
                                title,
                                style = WpType.Large,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().wpClickable { controller.skipAhead(i + 1); showQueue = false }.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                SeekBar(src.positionMs, src.durationMs) { ms ->
                    if (src.external != null) ExternalMediaWatcher.seekTo(ms) else controller.seekTo(ms)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(WpIcons.Previous, size = 58.dp, color = Color.White, contentDescription = stringResource(R.string.music_previous)) { previous(controller, src) }
                    Spacer(Modifier.width(28.dp))
                    Box(
                        Modifier.size(78.dp).background(Color.White, CircleShape).wpClickable { togglePlay(controller, src) },
                        contentAlignment = Alignment.Center,
                    ) {
                        WpIcon(if (src.playing) WpIcons.Pause else WpIcons.Play, tint = Color.Black, size = 34.dp)
                    }
                    Spacer(Modifier.width(28.dp))
                    RoundButton(WpIcons.Next, size = 58.dp, color = Color.White, contentDescription = stringResource(R.string.music_next)) { next(controller, src) }
                }
            }

            if (src.external == null) {
                WpAppBar(
                    background = Color.Black.copy(alpha = 0.6f),
                    buttons = listOf(
                        AppBarButton(WpIcons.Shuffle, stringResource(if (ui.shuffle) R.string.music_shuffle_on else R.string.music_shuffle)) { controller.toggleShuffle() },
                        AppBarButton(
                            if (ui.repeatMode == Player.REPEAT_MODE_ONE) WpIcons.RepeatOne else WpIcons.Repeat,
                            stringResource(
                                when (ui.repeatMode) {
                                    Player.REPEAT_MODE_ALL -> R.string.music_repeat_all
                                    Player.REPEAT_MODE_ONE -> R.string.music_repeat_one
                                    else -> R.string.music_repeat
                                },
                            ),
                        ) { controller.cycleRepeat() },
                        AppBarButton(WpIcons.Playlist, stringResource(R.string.music_up_next)) { showQueue = !showQueue },
                    ),
                )
            } else {
                WpAppBar(
                    background = Color.Black.copy(alpha = 0.6f),
                    buttons = listOf(
                        AppBarButton(WpIcons.ArrowRightCircle, stringResource(R.string.music_open_app)) { openPackage(context, src.external.packageName) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun Art(src: Source, modifier: Modifier, sizePx: Int) {
    val bmp = src.artBitmap
    if (bmp != null) {
        val image = remember(bmp) { bmp.asImageBitmap() }
        Image(image, null, modifier, contentScale = ContentScale.Crop)
    } else if (src.artUri != null) {
        Thumbnail(src.artUri, modifier, sizePx = sizePx)
    }
}

/** Thin bar with a square thumb: tap or drag to seek; times below. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { if (durationMs > 0) onSeek((it.x / size.width * durationMs).toLong()) }
                }
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
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f)))
            Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.001f)).height(3.dp).background(Wp.accent))
            Box(Modifier.offset(x = (w - 6.dp) * fraction).size(width = 6.dp, height = 18.dp).background(Color.White))
        }
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging != null) (dragging!! * durationMs).toLong() else positionMs
            WpText(WpFormat.duration(shown), style = WpType.Small, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            WpText(if (durationMs > 0) "-" + WpFormat.duration(durationMs - shown) else "", style = WpType.Small, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

private fun togglePlay(controller: MusicController, src: Source) = if (src.external != null) ExternalMediaWatcher.playPause() else controller.togglePlay()
private fun next(controller: MusicController, src: Source) { if (src.external != null) ExternalMediaWatcher.next() else controller.next() }
private fun previous(controller: MusicController, src: Source) { if (src.external != null) ExternalMediaWatcher.previous() else controller.previous() }

internal fun openPackage(context: android.content.Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
