package com.wpreborn.apps.music

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.wpreborn.core.icons.Glyphs
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.ImageLoader
import com.wpreborn.core.ui.RoundButton
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
 * The full player. The album cover is the centre of the screen; the background takes its
 * colours, the title and artist sit under it with clear hierarchy, then the seek bar and the
 * transport. Shuffle, repeat, favourite, the queue and the equalizer are one tap away. The
 * cover swipes to change song and everything cross-fades when the track changes. It plays
 * ZUNEX's own queue or controls Spotify / any other player through the same [Playback].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(onEqualizer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { MusicController.get(context) }
    val playback = rememberPlayback()
    val favorites by remember { Favorites.get(context) }.ids.collectAsState()
    var showQueue by remember { mutableStateOf(false) }

    // The bar would repeat what this screen shows.
    val chrome = LocalChrome.current
    DisposableEffect(chrome) {
        chrome?.hidePlayingNow = true
        onDispose { chrome?.hidePlayingNow = false }
    }

    val tint = rememberArtColor(playback)
    val top by animateColorAsState(lerp(Color.Black, tint, 0.75f), tween(700), label = "top")
    val bottom by animateColorAsState(lerp(Color.Black, tint, 0.18f), tween(700), label = "bottom")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        if (playback == null) {
            Column(Modifier.padding(start = 20.dp, top = 14.dp)) {
                WpText(stringResource(R.string.music_now_playing).uppercase(), style = WpType.AppTitle, color = Color.White)
                WpText(stringResource(R.string.music_nothing_playing), style = WpType.Large, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 24.dp))
            }
            return@Box
        }
        // A soft, blurred copy of the cover gives depth where the system can blur.
        if (Build.VERSION.SDK_INT >= 31) {
            PlaybackArt(
                playback,
                Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f; scaleX = 1.4f; scaleY = 1.4f }.blur(40.dp),
                sizePx = 256,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, bottom.copy(alpha = 0.9f)))))
        }

        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                WpText(
                    stringResource(R.string.music_now_playing).uppercase() + if (!playback.own) "  ·  " + (if (playback.isSpotify) "SPOTIFY" else stringResource(R.string.music_other_app).uppercase()) else "",
                    style = WpType.AppTitle,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight * 0.92f)
                Cover(playback, Modifier.size(side)) { forward -> if (forward) playback.next(context) else playback.previous(context) }
            }

            // Title, artist and the heart.
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    playback.trackKey,
                    transitionSpec = { (slideInVertically { it / 3 } + fadeIn(tween(300))) togetherWith fadeOut(tween(150)) },
                    modifier = Modifier.weight(1f),
                    label = "title",
                ) { _ ->
                    Column {
                        WpText(
                            playback.title,
                            style = WpType.ExtraLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500),
                        )
                        WpText(
                            listOf(playback.artist, playback.album).filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = WpType.Normal,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val id = playback.mediaId
                if (playback.own && id != null) {
                    val fav = id in favorites
                    Box(
                        Modifier.size(44.dp).wpClickable { Favorites.get(context).toggle(id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(fav, transitionSpec = { scaleIn() togetherWith fadeOut() }, label = "heart") { on ->
                            WpIcon(Glyphs.Heart, tint = if (on) Color(0xFFFF4F6A) else Color.White.copy(alpha = 0.55f), size = 26.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SeekBar(playback.positionMs, playback.durationMs, tint) { playback.seekTo(context, it) }

            // Transport.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(WpIcons.Shuffle, playback.own && playback.shuffle, enabled = playback.own, description = stringResource(R.string.music_shuffle)) { controller.toggleShuffle() }
                RoundButton(WpIcons.Previous, size = 54.dp, color = Color.White, contentDescription = stringResource(R.string.music_previous)) { playback.previous(context) }
                Box(
                    Modifier.size(76.dp).shadow(10.dp, CircleShape).background(Color.White, CircleShape).wpClickable { playback.togglePlay(context) },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(playback.playing, transitionSpec = { scaleIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "play") { playing ->
                        WpIcon(if (playing) WpIcons.Pause else WpIcons.Play, tint = Color.Black, size = 32.dp)
                    }
                }
                RoundButton(WpIcons.Next, size = 54.dp, color = Color.White, contentDescription = stringResource(R.string.music_next)) { playback.next(context) }
                ToggleIcon(
                    if (playback.repeatMode == Player.REPEAT_MODE_ONE) WpIcons.RepeatOne else WpIcons.Repeat,
                    playback.own && playback.repeatMode != Player.REPEAT_MODE_OFF,
                    enabled = playback.own,
                    description = stringResource(R.string.music_repeat),
                ) { controller.cycleRepeat() }
            }

            // Secondary actions.
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (playback.own) {
                    SmallAction(WpIcons.Playlist, stringResource(R.string.music_up_next)) { showQueue = true }
                    SmallAction(Glyphs.Grid, stringResource(R.string.eq_title)) { onEqualizer() }
                } else {
                    SmallAction(WpIcons.ArrowRightCircle, stringResource(R.string.music_open_app)) {
                        playback.packageName?.let { openPackage(context, it) }
                    }
                }
            }
        }

        // The queue slides up over the player.
        AnimatedVisibility(
            showQueue && playback.own,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WpText(stringResource(R.string.music_up_next), style = WpType.PivotHeader, color = Color.White, modifier = Modifier.weight(1f))
                    RoundButton(WpIcons.Close, size = 40.dp, color = Color.White) { showQueue = false }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (playback.nextTitles.isEmpty()) {
                        WpText(stringResource(R.string.music_queue_end), style = WpType.Large, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp))
                    }
                    playback.nextTitles.forEachIndexed { i, title ->
                        WpText(
                            title,
                            style = WpType.Large,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().wpClickable { controller.skipAhead(i + 1); showQueue = false }.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The cover: rounded, lifted, cross-fading between songs; swipe for next / previous. */
@Composable
private fun Cover(playback: Playback, modifier: Modifier, onSwipe: (forward: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val pressed = remember { Animatable(1f) }
    Box(
        modifier
            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
            .offset { IntOffset(swipe.value.roundToInt(), 0) }
            .graphicsLayer {
                alpha = 1f - (abs(swipe.value) / (widthPx * 1.5f)).coerceIn(0f, 0.7f)
                rotationZ = swipe.value / widthPx * 6f
                // Slightly smaller while paused, like the cover "resting".
                val s = pressed.value * if (playback.playing) 1f else 0.92f
                scaleX = s
                scaleY = s
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = swipe.value
                            if (abs(v) > widthPx * 0.28f) {
                                swipe.animateTo(if (v < 0) -widthPx * 1.2f else widthPx * 1.2f, tween(150))
                                onSwipe(v < 0)
                                swipe.snapTo(if (v < 0) widthPx * 0.5f else -widthPx * 0.5f)
                            }
                            swipe.animateTo(0f, tween(260))
                        }
                    },
                ) { change, dx ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + dx) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(playback.trackKey, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) }, label = "cover") { _ ->
            PlaybackArt(
                playback,
                Modifier.fillMaxSize().shadow(24.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)),
                sizePx = 768,
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(playback.playing) {
        pressed.animateTo(1f, tween(300))
    }
}

@Composable
private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).wpClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint = if (!enabled) Color.White.copy(alpha = 0.25f) else if (on) Color.White else Color.White.copy(alpha = 0.5f), size = 24.dp)
        if (on) Box(Modifier.align(Alignment.BottomCenter).size(4.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 72.dp).wpClickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WpIcon(icon, tint = Color.White.copy(alpha = 0.85f), size = 22.dp)
        WpText(label, style = WpType.Small, color = Color.White.copy(alpha = 0.7f))
    }
}

/** Thin bar with a round thumb: tap or drag to seek; elapsed and total time below. */
@Composable
fun SeekBar(positionMs: Long, durationMs: Long, accent: Color, onSeek: (Long) -> Unit) {
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
            val thumb = if (dragging != null) 16.dp else 12.dp
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.001f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(lerp(accent, Color.White, 0.55f)))
            Box(Modifier.offset(x = (w - thumb) * fraction).size(thumb).background(Color.White, CircleShape))
        }
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging != null) (dragging!! * durationMs).toLong() else positionMs
            WpText(WpFormat.duration(shown), style = WpType.Small, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            WpText(if (durationMs > 0) "-" + WpFormat.duration((durationMs - shown).coerceAtLeast(0)) else "", style = WpType.Small, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

/** A vivid colour taken from the album art, used to tint the player. */
@Composable
fun rememberArtColor(playback: Playback?): Color {
    val context = LocalContext.current
    val accent = Wp.accent
    val color by produceState(accent, playback?.trackKey, playback?.artBitmap) {
        val bmp: Bitmap? = playback?.artBitmap ?: playback?.artUri?.let { ImageLoader.thumbnail(context, it, 96) }
        value = bmp?.let { dominantColor(it) } ?: accent
    }
    return color
}

/** Weighted average of the most saturated, mid-bright pixels of a small copy of [bmp]. */
fun dominantColor(bmp: Bitmap): Color? = runCatching {
    val small = Bitmap.createScaledBitmap(if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp, 24, 24, true)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var total = 0.0
    val hsv = FloatArray(3)
    for (y in 0 until small.height) for (x in 0 until small.width) {
        val p = small.getPixel(x, y)
        android.graphics.Color.colorToHSV(p, hsv)
        val weight = (hsv[1] * hsv[1]) * (1f - abs(hsv[2] - 0.6f)) + 0.02f
        r += android.graphics.Color.red(p) * weight
        g += android.graphics.Color.green(p) * weight
        b += android.graphics.Color.blue(p) * weight
        total += weight
    }
    if (total <= 0) null else Color((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
}.getOrNull()

internal fun openPackage(context: android.content.Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
