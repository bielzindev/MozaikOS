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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    /** Bumped every time the lock screen is brought back, so the content resets to its rest state. */
    private var session by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        WpActivity.setupWindow(this)
        hideBars()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit // Back does nothing on the lock screen.
        })
        setContent {
            WpTheme {
                LockScreen(session, onUnlock = ::unlock)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        session++
        hideBars()
    }

    private fun hideBars() {
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        val wpBar = SettingsRepository.get(this).cached.wpStatusBar
        controller.isAppearanceLightStatusBars = false
        controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        // Android's own status bar stays unless the Windows Phone one was chosen.
        controller.hide(
            if (wpBar) androidx.core.view.WindowInsetsCompat.Type.systemBars() else androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
        )
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = hideQuietly()
                override fun onDismissCancelled() {
                    session++
                }
                override fun onDismissError() = hideQuietly()
            })
        } else {
            hideQuietly()
        }
    }

    /**
     * Steps aside without being destroyed: the next time the screen turns off the same,
     * already drawn activity is brought back, so it is on screen the instant the display
     * wakes instead of the Android lock screen showing first while ZUNEX starts.
     */
    private fun hideQuietly() {
        if (!moveTaskToBack(true)) finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val WALLPAPER_SIZE = 1920

        /** Decodes the chosen picture ahead of time so the first frame already shows it. */
        fun preloadWallpaper(context: Context, uri: String?) {
            if (uri == null) return
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                ImageLoader.full(context.applicationContext, Uri.parse(uri), WALLPAPER_SIZE)
            }
        }

        /** Registered by the Application: shows the lock screen whenever the screen turns off. */
        fun register(context: Context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val settings = SettingsRepository.get(c).cached
                    if (!settings.lockEnabled || !c.canDrawOverlays()) return
                    preloadWallpaper(c, settings.lockWallpaper)
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
            preloadWallpaper(context, SettingsRepository.get(context).cached.lockWallpaper)
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }
}

@Composable
fun LockScreen(session: Int, onUnlock: () -> Unit) {
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
    val wallpaper by produceState(
        settings.lockWallpaper?.let { ImageLoader.cachedFull(Uri.parse(it), 1920)?.asImageBitmap() },
        settings.lockWallpaper,
    ) {
        if (value == null) value = settings.lockWallpaper?.let { ImageLoader.full(context, Uri.parse(it), 1920)?.asImageBitmap() }
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
    // Back on screen after being hidden (or the PIN prompt was cancelled): rest position.
    LaunchedEffect(session) { offset.snapTo(0f) }

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

            // No fade-in: the screen must be complete on the very first frame after wake-up.
            Column(Modifier.fillMaxSize()) {
                if (settings.wpStatusBar) {
                    WpStatusBar(Modifier.padding(top = 6.dp), color = Color.White)
                } else {
                    Spacer(Modifier.statusBarsPadding())
                }
                // Music widget: the same playback state as the Playing Now bar and the player.
                val playback = com.wpreborn.data.media.rememberPlayback()
                // A paused or stopped player leaves the lock screen after a short while.
                val shown = com.wpreborn.data.media.rememberPlaybackVisible(playback)
                val last = remember { arrayOfNulls<com.wpreborn.data.media.Playback>(1) }
                if (playback != null) last[0] = playback
                androidx.compose.animation.AnimatedVisibility(
                    visible = shown != null,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                ) {
                    last[0]?.let { LockMedia(it) }
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
                // Latest notifications, each swiped away to the left or right to remove it.
                if (settings.lockShowNotifications) {
                    notifications.filter { it.clearable }.take(3).forEach { n ->
                        androidx.compose.runtime.key(n.key) {
                            com.wpreborn.launcher.actioncenter.NotificationRow(n, startPad = 24.dp) {}
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
/**
 * Music on the lock screen, the Windows Phone way: the song and artist in plain white type
 * and the three round transport buttons, no card or artwork.
 */
@Composable
private fun LockMedia(playback: com.wpreborn.data.media.Playback) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.wpreborn.apps.common.PlaybackArt(playback, Modifier.size(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            WpText(playback.title, style = WpType.Normal.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(playback.artist, style = WpType.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(com.wpreborn.apps.common.playbackSource(playback), style = WpType.Small, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        com.wpreborn.core.ui.RoundButton(com.wpreborn.core.icons.WpIcons.Previous, size = 40.dp, color = Color.White) { playback.previous(context) }
        Spacer(Modifier.width(8.dp))
        com.wpreborn.core.ui.RoundButton(
            if (playback.playing) com.wpreborn.core.icons.WpIcons.Pause else com.wpreborn.core.icons.WpIcons.Play,
            size = 40.dp,
            color = Color.White,
        ) { playback.togglePlay(context) }
        Spacer(Modifier.width(8.dp))
        com.wpreborn.core.ui.RoundButton(com.wpreborn.core.icons.WpIcons.Next, size = 40.dp, color = Color.White) { playback.next(context) }
    }
}
