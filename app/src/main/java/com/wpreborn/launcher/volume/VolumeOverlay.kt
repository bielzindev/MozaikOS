package com.wpreborn.launcher.volume

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.WpEasing
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.ExternalMediaWatcher
import com.wpreborn.services.music.MusicController
import kotlinx.coroutines.delay

/**
 * State of the Windows Phone 8.1 volume flyout. Our activities intercept the volume keys,
 * change the stream themselves (without the Android panel) and show this instead.
 */
@Stable
class VolumeState {
    var stream by mutableIntStateOf(AudioManager.STREAM_RING)
    var visible by mutableStateOf(false)
    var version by mutableIntStateOf(0)

    fun onKey(context: Context, up: Boolean) {
        val am = context.getSystemService(AudioManager::class.java)
        stream = if (am.isMusicActive) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
        try {
            am.adjustStreamVolume(stream, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        } catch (_: SecurityException) {
            // Do Not Disturb policy forbids changing the ringer: fall back to media.
            stream = AudioManager.STREAM_MUSIC
            am.adjustStreamVolume(stream, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        }
        visible = true
        version++
    }
}

@Composable
fun VolumeOverlay(state: VolumeState) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AudioManager::class.java) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(state.visible, state.version) {
        if (state.visible) {
            slide.animateTo(1f, tween(250, easing = WpEasing.ExpOut))
            delay(3000)
            slide.animateTo(0f, tween(200, easing = WpEasing.ExpIn))
            state.visible = false
        }
    }
    if (slide.value <= 0f && !state.visible) return
    val max = am.getStreamMaxVolume(state.stream).coerceAtLeast(1)
    var value by remember(state.version, state.stream) { mutableIntStateOf(am.getStreamVolume(state.stream)) }
    val player by MusicController.get(context).ui.collectAsState()
    val external by ExternalMediaWatcher.state.collectAsState()
    val vibrate = am.ringerMode == AudioManager.RINGER_MODE_VIBRATE

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -(1f - slide.value) * size.height }
            .background(Wp.palette.chrome)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WpText(
                stringResource(if (state.stream == AudioManager.STREAM_MUSIC) R.string.volume_media else R.string.volume_ringer),
                style = WpType.Normal,
                modifier = Modifier.weight(1f),
            )
            WpText(
                stringResource(if (vibrate) R.string.volume_vibrate_on else R.string.volume_vibrate_off),
                style = WpType.Normal,
                color = Wp.accent,
                modifier = Modifier.wpClickable {
                    try {
                        am.ringerMode = if (vibrate) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_VIBRATE
                    } catch (_: SecurityException) {
                    }
                    state.version++
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            WpIcon(if (value == 0) WpIcons.Mute else WpIcons.Volume, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            WpSlider(
                value / max.toFloat(),
                {
                    value = (it * max).toInt()
                    am.setStreamVolume(state.stream, value, 0)
                },
                Modifier.weight(1f),
                onValueChangeFinished = { state.version++ },
            )
            Spacer(Modifier.width(12.dp))
            WpText(value.toString(), style = WpType.Large.copy(fontSize = 26.sp))
        }
        // Media transport controls, as on 8.1 when something is playing.
        val title = when {
            player.hasMedia -> player.title
            external != null -> external?.title.orEmpty()
            else -> null
        }
        if (title != null) {
            val playing = if (player.hasMedia) player.playing else external?.playing == true
            Box(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    WpText(title, style = WpType.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val artist = if (player.hasMedia) player.artist else external?.artist.orEmpty()
                    WpText(artist, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val c = MusicController.get(context)
                RoundButton(WpIcons.Previous, size = 38.dp) { if (player.hasMedia) c.previous() else ExternalMediaWatcher.previous(); state.version++ }
                Spacer(Modifier.width(8.dp))
                RoundButton(if (playing) WpIcons.Pause else WpIcons.Play, size = 38.dp) {
                    if (player.hasMedia) c.togglePlay() else ExternalMediaWatcher.playPause(); state.version++
                }
                Spacer(Modifier.width(8.dp))
                RoundButton(WpIcons.Next, size = 38.dp) { if (player.hasMedia) c.next() else ExternalMediaWatcher.next(); state.version++ }
            }
        }
    }
}

@Suppress("unused")
private val Transparent = Color.Transparent
