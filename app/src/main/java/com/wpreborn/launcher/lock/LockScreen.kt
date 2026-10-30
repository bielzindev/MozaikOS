package com.wpreborn.launcher.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.motion.WpEasing
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpTheme
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.ImageLoader
import com.wpreborn.core.ui.WpStatusBar
import com.wpreborn.core.ui.WpText
import com.wpreborn.data.calendar.CalendarRepository
import com.wpreborn.data.notifications.NotificationCenter
import com.wpreborn.data.prefs.SettingsRepository
import com.wpreborn.data.system.SystemStatus
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.canDrawOverlays
import com.wpreborn.permissions.hasPermissions
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Lumia-style lock screen. It is shown in front of the Android keyguard but never replaces
 * it: swiping up asks the system to dismiss the keyguard, so PIN, pattern, fingerprint and
 * face unlock keep working and nothing private is shown before authentication (only app
 * icons with counts, like the WP "quick status").
 */
class LockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        WpActivity.setupWindow(this)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit // Back does nothing on the lock screen.
        })
        setContent {
            WpTheme {
                LockScreen(onUnlock = ::unlock)
            }
        }
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finishQuietly()
                override fun onDismissCancelled() = recreate()
                override fun onDismissError() = finishQuietly()
            })
        } else {
            finishQuietly()
        }
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        /** Registered by the Application: shows the lock screen whenever the screen turns off. */
        fun register(context: Context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val settings = SettingsRepository.get(c).cached
                    if (!settings.lockEnabled || !c.canDrawOverlays()) return
                    val am = c.getSystemService(AudioManager::class.java)
                    if (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE) return
                    try {
                        c.startActivity(
                            Intent(c, LockActivity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                            ),
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val settings = Wp.settings
    val scope = rememberCoroutineScope()
    val status = remember { SystemStatus.get(context) }
    val now by status.clock.collectAsState()
    val battery by status.battery.collectAsState()
    val notifications by NotificationCenter.items.collectAsState()
    val player by com.wpreborn.services.music.MusicController.get(context).ui.collectAsState()
    val external by com.wpreborn.data.media.ExternalMediaWatcher.state.collectAsState()

    // Background: chosen picture, or the latest photos cycling (like the Photos/Bing lock
    // screen apps of 8.1), or an accent gradient.
    val wallpaper by produceState<ImageBitmap?>(null, settings.lockWallpaper) {
        value = settings.lockWallpaper?.let { ImageLoader.full(context, Uri.parse(it), 1920)?.asImageBitmap() }
    }
    val slideshow by produceState(emptyList<Uri>(), settings.lockPhotos, settings.lockWallpaper) {
        value = if (settings.lockPhotos && settings.lockWallpaper == null && context.hasPermissions(Perms.IMAGES)) {
            com.wpreborn.data.media.PhotoLibrary(context).load(limit = 12).map { it.uri }
        } else {
            emptyList()
        }
    }
    // Detailed status: the next appointment, as on the Lumia lock screen.
    val nextEvent by produceState<Pair<String, String>?>(null, now / 600_000) {
        value = if (context.hasPermissions(Perms.CALENDAR)) {
            CalendarRepository(context).upcoming(days = 1, max = 1).firstOrNull()?.let { e ->
                val time = if (e.allDay) context.getString(com.wpreborn.R.string.calendar_all_day) else DateFormat.getTimeFormat(context).format(Date(e.begin))
                e.title to listOf(time, e.location).filter { it.isNotBlank() }.joinToString("  ")
            }
        } else {
            null
        }
    }
    val nextAlarm = remember(now) {
        context.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.takeIf { it - now < 24L * 3600 * 1000 }
    }
    val offset = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(500, easing = WpEasing.ExpOut)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offset.value }
                .pointerInput(Unit) {
                    detectTapGestures {
                        // A tap bounces the screen to hint at the swipe, like on the Lumia.
                        scope.launch {
                            offset.animateTo(-heightPx * 0.06f, tween(150, easing = WpEasing.ExpOut))
                            offset.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value < -heightPx * 0.25f) {
                                    offset.animateTo(-heightPx, tween(220, easing = WpEasing.ExpIn))
                                    onUnlock()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f))
                                }
                            }
                        },
                    ) { change, dy ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + dy).coerceAtMost(0f)) }
                    }
                },
        ) {
            val image = wallpaper
            when {
                image != null -> Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                slideshow.isNotEmpty() -> com.wpreborn.launcher.start.CycleTile(slideshow, enabled = true, seed = 3) { uri ->
                    com.wpreborn.core.ui.Thumbnail(uri, Modifier.fillMaxSize(), sizePx = 1080)
                }
                else -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Wp.accent.copy(alpha = 0.55f), Color.Black))))
            }
            // Keep the text readable on bright pictures.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))

            Column(Modifier.fillMaxSize().graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 60f }) {
                WpStatusBar(Modifier.padding(top = 6.dp), color = Color.White)
                // Media controls when something is playing.
                val title = if (player.hasMedia) player.title else external?.title
                if (title != null && (player.playing || external?.playing == true)) {
                    LockMedia(title, if (player.hasMedia) player.artist else external?.artist.orEmpty(), player.hasMedia)
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.padding(start = 24.dp, end = 24.dp)) {
                    nextEvent?.let { (t, sub) ->
                        WpText(t, style = WpType.Large.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WpText(sub, style = WpType.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(14.dp))
                    }
                    val time = DateFormat.getTimeFormat(context).format(Date(now))
                        .replace(Regex("\\s?[AaPp]\\.?\\s?[Mm]\\.?"), "")
                    WpText(time, style = WpType.LockClock, color = Color.White)
                    WpText(DateFormat.format("EEEE", now).toString(), style = WpType.ExtraLarge, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WpText(DateFormat.format("d MMMM", now).toString(), style = WpType.ExtraLarge, color = Color.White)
                        if (nextAlarm != null) {
                            Spacer(Modifier.width(16.dp))
                            com.wpreborn.core.ui.WpIcon(com.wpreborn.core.icons.WpIcons.Alarm, tint = Color.White, size = 22.dp)
                            Spacer(Modifier.width(4.dp))
                            WpText(DateFormat.getTimeFormat(context).format(Date(nextAlarm)), style = WpType.Normal, color = Color.White)
                        }
                    }
                }
                // Quick status: app icons with counts, and the battery while charging.
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.lockShowNotifications) {
                        val counts = notifications.groupingBy { it.packageName }.eachCount().entries.take(5)
                        counts.forEach { (pkg, count) ->
                            val icon = remember(pkg) {
                                try {
                                    context.packageManager.getApplicationIcon(pkg).toBitmap(64, 64).asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (icon != null) Image(icon, null, Modifier.size(24.dp))
                            Spacer(Modifier.width(4.dp))
                            WpText(count.toString(), style = WpType.Large, color = Color.White)
                            Spacer(Modifier.width(18.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (battery.charging) {
                        com.wpreborn.core.ui.BatteryGlyph(battery.level, true, Color.White)
                        Spacer(Modifier.width(6.dp))
                        WpText("${battery.level}%", style = WpType.Normal, color = Color.White)
                    }
                }
                Spacer(Modifier.height(40.dp).navigationBarsPadding())
            }
        }
    }
}

/** The 8.1 lock screen media strip: title, artist and transport buttons. */
@Composable
private fun LockMedia(title: String, artist: String, own: Boolean) {
    val context = LocalContext.current
    val controller = remember { com.wpreborn.services.music.MusicController.get(context) }
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            WpText(title, style = WpType.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(artist, style = WpType.Small, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        com.wpreborn.core.ui.RoundButton(com.wpreborn.core.icons.WpIcons.Previous, size = 38.dp, color = Color.White) {
            if (own) controller.previous() else com.wpreborn.data.media.ExternalMediaWatcher.previous()
        }
        Spacer(Modifier.width(8.dp))
        com.wpreborn.core.ui.RoundButton(com.wpreborn.core.icons.WpIcons.Pause, size = 38.dp, color = Color.White) {
            if (own) controller.togglePlay() else com.wpreborn.data.media.ExternalMediaWatcher.playPause()
        }
        Spacer(Modifier.width(8.dp))
        com.wpreborn.core.ui.RoundButton(com.wpreborn.core.icons.WpIcons.Next, size = 38.dp, color = Color.White) {
            if (own) controller.next() else com.wpreborn.data.media.ExternalMediaWatcher.next()
        }
    }
}
