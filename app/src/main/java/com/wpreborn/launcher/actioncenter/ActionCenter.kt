package com.wpreborn.launcher.actioncenter

import android.app.Activity
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.WpEasing
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.BatteryGlyph
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.notifications.NotificationCenter
import com.wpreborn.data.notifications.WpNotification
import com.wpreborn.data.system.SystemStatus
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pull-down state shared between the status bar drag handle and the Action Center panel. */
@Stable
class ActionCenterState {
    /** 0 = hidden, 1 = fully open. */
    val progress = Animatable(0f)
    val visible get() = progress.value > 0f

    suspend fun open() = progress.animateTo(1f, tween(300, easing = WpEasing.ExpOut))
    suspend fun close() = progress.animateTo(0f, tween(220, easing = WpEasing.ExpIn))
    suspend fun dragBy(fraction: Float) = progress.snapTo((progress.value + fraction).coerceIn(0f, 1f))
    suspend fun settle(velocity: Float) = if (velocity > 300f || (velocity > -300f && progress.value > 0.4f)) open() else close()
}

/** Thin strip at the top of every Windows Phone screen: dragging it down opens the Action Center. */
@Composable
fun ActionCenterHandle(state: ActionCenterState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth().height(26.dp)) {
        val heightPx = with(LocalDensity.current) { 600.dp.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var velocity = 0f
                    var last = 0L
                    detectVerticalDragGestures(
                        onDragStart = { velocity = 0f; last = System.currentTimeMillis() },
                        onDragEnd = { scope.launch { state.settle(velocity) } },
                        onDragCancel = { scope.launch { state.close() } },
                    ) { change, dy ->
                        change.consume()
                        val now = System.currentTimeMillis()
                        val dt = (now - last).coerceAtLeast(1)
                        velocity = dy / dt * 1000f
                        last = now
                        scope.launch { state.dragBy(dy / heightPx) }
                    }
                },
        )
    }
}

/**
 * Windows Phone 8.1 Action Center: date and battery, four (plus four) quick action squares,
 * "all settings", then notifications grouped by app. Swipe a notification to the right to
 * dismiss it; swipe the panel up to close.
 */
@Composable
fun ActionCenter(state: ActionCenterState) {
    if (!state.visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quick = remember { QuickActions(context) }
    DisposableEffect(Unit) { onDispose { quick.release() } }
    val status = remember { SystemStatus.get(context) }
    val battery by status.battery.collectAsState()
    val net by status.network.collectAsState()
    val radios by status.radios.collectAsState()
    val now by status.clock.collectAsState()
    val torch by quick.torch.collectAsState()
    val notifications by NotificationCenter.items.collectAsState()
    val connected by NotificationCenter.connected.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val window = (context as? Activity)?.window

    BackHandler { scope.launch { state.close() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullPx = with(LocalDensity.current) { maxHeight.toPx() }
        // Dim the page behind.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress.value }
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, null) { scope.launch { state.close() } },
        )
        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, ((state.progress.value - 1f) * fullPx).roundToInt()) }
                .background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Header: time + date on the left, battery on the right.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.Bottom) {
                val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
                val date = remember(now) { DateFormat.format("EEEE, d MMMM", now).toString() }
                WpText(time, style = WpType.Header.copy(fontSize = 44.sp), color = Color.White)
                Spacer(Modifier.width(12.dp))
                WpText(date.lowercase(), style = WpType.Normal, color = Color.White, modifier = Modifier.padding(bottom = 8.dp).weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                    BatteryGlyph(battery.level, battery.charging, Color.White)
                    WpText("${battery.level}%", style = WpType.Small, color = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                WpText(net.carrier.uppercase(), style = WpType.AppTitle, color = Color(0x99FFFFFF), modifier = Modifier.weight(1f))
                WpText(
                    stringResource(R.string.ac_all_settings).uppercase(),
                    style = WpType.AppTitle,
                    color = Color.White,
                    modifier = Modifier.wpClickable { scope.launch { state.close() }; quick.openAllSettings() },
                )
            }
            key(refresh) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickSquare(WpIcons.Wifi, stringResource(R.string.ac_wifi), net.wifiConnected) { quick.openWifi() }
                    QuickSquare(WpIcons.Bluetooth, stringResource(R.string.ac_bluetooth), radios.bluetooth) { quick.openBluetooth() }
                    QuickSquare(WpIcons.Airplane, stringResource(R.string.ac_airplane), radios.airplane) { quick.openAirplane() }
                    QuickSquare(WpIcons.RotationLock, stringResource(R.string.ac_rotation), quick.rotationLocked()) {
                        quick.toggleRotationLock(); refresh++
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val level = quick.brightness()
                    val levelLabel = stringResource(
                        when (level) {
                            QuickActions.BrightnessLevel.Low -> R.string.ac_brightness_low
                            QuickActions.BrightnessLevel.Medium -> R.string.ac_brightness_medium
                            QuickActions.BrightnessLevel.High -> R.string.ac_brightness_high
                            QuickActions.BrightnessLevel.Auto -> R.string.ac_brightness_auto
                        },
                    )
                    QuickSquare(WpIcons.Brightness, levelLabel, level != QuickActions.BrightnessLevel.Low) {
                        quick.cycleBrightness(window); refresh++
                    }
                    QuickSquare(WpIcons.Flashlight, stringResource(R.string.ac_flashlight), torch, enabled = quick.hasTorch) { quick.toggleTorch() }
                    QuickSquare(WpIcons.Vibrate, stringResource(R.string.ac_vibrate), radios.ringerMode == android.media.AudioManager.RINGER_MODE_VIBRATE) {
                        quick.toggleVibrate()
                    }
                    QuickSquare(WpIcons.Globe, stringResource(R.string.ac_internet), net.wifiConnected || net.mobileData) { quick.openInternet() }
                }
                var bright by remember { mutableStateOf(quick.brightnessValue()) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    WpIcon(WpIcons.Brightness, tint = Color.White, size = 20.dp)
                    Spacer(Modifier.width(10.dp))
                    WpSlider(bright, { bright = it; quick.setBrightnessRaw(it, window) }, Modifier.weight(1f))
                }
            }

            // Music: the same playback the Playing Now bar and the lock screen show.
            val playback = com.wpreborn.data.media.rememberPlaybackVisible(com.wpreborn.data.media.rememberPlayback())
            if (playback != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color(0xFF1F1F1F))
                        .wpClickable(tilt = false) { scope.launch { state.close() }; playback.openPlayer(context) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.wpreborn.apps.common.PlaybackArt(playback, Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        WpText(playback.title, style = WpType.Normal, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        WpText(
                            listOf(playback.artist, com.wpreborn.apps.common.playbackSource(playback)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = WpType.Small, color = Color(0xB3FFFFFF), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth(playback.progress.coerceAtLeast(0.001f)).height(2.dp).background(Wp.accent))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    com.wpreborn.core.ui.RoundButton(WpIcons.Previous, size = 38.dp, color = Color.White) { playback.previous(context) }
                    Spacer(Modifier.width(4.dp))
                    com.wpreborn.core.ui.RoundButton(if (playback.playing) WpIcons.Pause else WpIcons.Play, size = 38.dp, color = Color.White) { playback.togglePlay(context) }
                    Spacer(Modifier.width(4.dp))
                    com.wpreborn.core.ui.RoundButton(WpIcons.Next, size = 38.dp, color = Color.White) { playback.next(context) }
                }
            }

            // Notifications
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!connected) {
                    Column(Modifier.padding(20.dp)) {
                        WpText(stringResource(R.string.ac_no_access), style = WpType.Normal, color = Color(0x99FFFFFF))
                        Spacer(Modifier.height(12.dp))
                        WpButton(stringResource(R.string.allow)) {
                            context.startActivity(NotificationCenter.accessSettingsIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                } else if (notifications.isEmpty()) {
                    WpText(
                        stringResource(R.string.ac_no_notifications),
                        style = WpType.Large,
                        color = Color(0x99FFFFFF),
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    val grouped = notifications.groupBy { it.packageName }
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                        grouped.forEach { (pkg, list) ->
                            item(key = "h$pkg") {
                                Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIconSmall(pkg)
                                    Spacer(Modifier.width(8.dp))
                                    WpText(list.first().appLabel.uppercase(), style = WpType.AppTitle, color = Color.White)
                                }
                            }
                            items(list, key = { it.key }) { n ->
                                NotificationRow(n) {
                                    scope.launch { state.close() }
                                    try {
                                        n.contentIntent?.send()
                                    } catch (_: Exception) {
                                    }
                                    if (n.clearable) NotificationCenter.dismiss(n.key)
                                }
                            }
                        }
                    }
                }
            }

            // Footer: clear all + pull-up handle.
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = { scope.launch { state.settle(-1000f) } }) { change, dy ->
                            change.consume()
                            scope.launch { state.dragBy(dy / fullPx) }
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                if (notifications.any { it.clearable }) {
                    WpText(
                        stringResource(R.string.ac_clear_all).uppercase(),
                        style = WpType.AppTitle,
                        color = Color.White,
                        modifier = Modifier.wpClickable { NotificationCenter.clearAll() },
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = { scope.launch { state.settle(-1000f) } }) { change, dy ->
                            change.consume()
                            scope.launch { state.dragBy(dy / fullPx) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(40.dp).height(3.dp).background(Color(0x99FFFFFF)))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickSquare(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(Modifier.weight(1f)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .wpClickable(enabled) { onClick() }
                .background(if (active && enabled) Wp.accent else Color(0xFF2E2E2E)),
            contentAlignment = Alignment.Center,
        ) {
            WpIcon(icon, tint = if (enabled) Color.White else Color(0x66FFFFFF), size = 26.dp)
        }
        WpText(
            label.uppercase(),
            style = WpType.Small.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun AppIconSmall(pkg: String) {
    val context = LocalContext.current
    val bmp = remember(pkg) {
        try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(48, 48).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(18.dp))
}

@Composable
fun NotificationRow(n: WpNotification, startPad: androidx.compose.ui.unit.Dp = 46.dp, showIcon: Boolean = false, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissPx = with(density) { 90.dp.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .slideInStaggered(0, key = n.key, distanceDp = 30f)
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (dismissPx * 2.5f)).coerceIn(0f, 0.8f)
            }
            .pointerInput(n.key) {
                if (!n.clearable) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > dismissPx) {
                                val away = if (offset.value > 0) size.width.toFloat() else -size.width.toFloat()
                                offset.animateTo(away, tween(150))
                                NotificationCenter.dismiss(n.key)
                            } else {
                                offset.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(200)) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dx) }
                }
            }
            .wpClickable(tilt = false, onClick = onOpen)
            .padding(start = startPad, end = 20.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // On the lock screen there is no app header above, so each one carries its icon.
            if (showIcon) {
                SquareAppIcon(n.packageName, 40.dp, Wp.accent)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                WpText(n.title, style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotBlank()) {
                    WpText(n.text, style = WpType.Subtle, color = Color(0xB3FFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }
