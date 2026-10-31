package com.wpreborn.apps.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.Playback

/**
 * The system-wide Playing Now bar: shown at the bottom of every ZUNEX screen while music is
 * playing (or paused with a queue), with the cover, the song, live bars while it plays, a
 * thin progress line, play/pause and next. Tapping it opens the full player.
 */
@Composable
fun PlayingNowBar(playback: Playback, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().background(Wp.palette.chrome)) {
        // Progress hairline.
        Box(Modifier.fillMaxWidth().height(2.dp).background(Wp.palette.divider)) {
            Box(Modifier.fillMaxWidth(playback.progress.coerceAtLeast(0.001f)).height(2.dp).background(Wp.accent))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .wpClickable(tilt = false) { playback.openPlayer(context) }
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackArt(playback, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = playback.trackKey,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(220))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(160))) },
                modifier = Modifier.weight(1f),
                label = "track",
            ) { _ ->
                Column {
                    WpText(playback.title, style = WpType.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    WpText(
                        if (playback.own) playback.artist else listOf(playback.artist, appLabel(playback.packageName)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = WpType.Small,
                        color = Wp.palette.subtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RoundButton(
                if (playback.playing) WpIcons.Pause else WpIcons.Play,
                size = 40.dp,
                contentDescription = stringResource(if (playback.playing) R.string.music_pause else R.string.music_play),
            ) { playback.togglePlay(context) }
            Spacer(Modifier.width(6.dp))
            RoundButton(WpIcons.Next, size = 40.dp, contentDescription = stringResource(R.string.music_next)) { playback.next(context) }
        }
    }
}

@Composable
private fun appLabel(pkg: String?): String {
    val context = LocalContext.current
    return remember(pkg) {
        pkg?.let { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull() }.orEmpty()
    }
}

/** Album art of the current track: the cover from our library or the other app's bitmap. */
@Composable
fun PlaybackArt(playback: Playback, modifier: Modifier, sizePx: Int = 256) {
    Box(modifier.background(Wp.accent), contentAlignment = Alignment.Center) {
        WpIcon(WpIcons.MusicNote, tint = Color.White, size = 22.dp)
        val bmp = playback.artBitmap
        if (bmp != null) {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (playback.artUri != null) {
            Thumbnail(playback.artUri, Modifier.fillMaxSize(), sizePx = sizePx)
        }
    }
}

/** Three little bars bouncing to the music, the "now playing" indicator. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, color: Color = Color.White, height: Dp = 14.dp) {
    val transition = rememberInfiniteTransition(label = "bars")
    val a by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    val c by transition.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "c")
    Canvas(modifier.size(width = height, height = height)) {
        val w = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, f ->
            val h = size.height * f
            drawRoundRect(color, Offset(i * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 3))
        }
    }
}
