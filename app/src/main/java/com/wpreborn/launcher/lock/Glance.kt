package com.wpreborn.launcher.lock

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpTheme
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.data.notifications.NotificationCenter
import com.wpreborn.data.prefs.GlanceOptions
import com.wpreborn.data.prefs.SettingsRepository
import com.wpreborn.data.system.SystemStatus
import com.wpreborn.permissions.canDrawOverlays
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date
import kotlin.random.Random

/**
 * The Lumia "glance screen" (always on display): when the screen turns off, Mozaik turns it
 * back on very dim, all black, with the time, the weather and the notification icons in thin
 * accent-coloured type, over the lock screen. A double tap wakes the phone. It stays on while
 * charging; on battery it lasts 15 minutes; in a pocket (proximity) it lets the screen go off.
 */
object Glance {
    /** It timed out (or went into a pocket): don't come back until the screen is turned on again. */
    @Volatile var expired = false
    /** Bumped to close an open glance screen (the user turned the screen on). */
    val close = MutableStateFlow(0)
    /** When we last turned the screen on ourselves (a peek), so that screen-on isn't the user's. */
    @Volatile private var launchedAt = 0L
    @Volatile var showing = false
    private var peekListener: SensorEventListener? = null

    /** The glance colour by name (night colours are the deep ones that don't light up a room). */
    fun color(name: String, accent: Color): Color = when (name) {
        "white" -> Color(0xFFE8E8E8)
        "red" -> Color(0xFFE51400)
        "green" -> Color(0xFF34B233)
        "blue" -> Color(0xFF1BA1E2)
        else -> accent
    }

    /** Whether [now] is inside the night mode hours (which may run past midnight). */
    fun isNight(o: GlanceOptions, now: Long): Boolean {
        if (!o.night) return false
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val m = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return if (o.nightFrom <= o.nightTo) m in o.nightFrom until o.nightTo else m >= o.nightFrom || m < o.nightTo
    }

    private fun blocked(c: Context): Boolean {
        val s = SettingsRepository.get(c).cached
        if (!s.glance || !c.canDrawOverlays()) return true
        if (isNight(s.glanceOptions, System.currentTimeMillis()) && s.glanceOptions.nightColor == "hidden") return true
        val am = c.getSystemService(AudioManager::class.java)
        return am != null && (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE)
    }

    private fun launch(c: Context, peek: Boolean) {
        launchedAt = System.currentTimeMillis()
        runCatching {
            c.startActivity(
                Intent(c, GlanceActivity::class.java).putExtra(GlanceActivity.EXTRA_PEEK, peek).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                ),
            )
        }
    }

    fun onScreenOff(c: Context) {
        // The glance screen is up: the power key (or the time-out) turned it off; it handles that.
        if (showing) return
        val s = SettingsRepository.get(c).cached
        if (s.glance && s.glanceOptions.peek) {
            // Peek: the screen stays off until a hand waves over the sensor (or it leaves a pocket).
            armPeek(c.applicationContext)
            return
        }
        disarmPeek(c)
        if (expired || blocked(c)) return
        // After the lock screen, so the glance screen is the one on top.
        Handler(Looper.getMainLooper()).postDelayed({ launch(c, peek = false) }, 400)
    }

    fun onScreenOn(c: Context) {
        // The screen turned on by a peek isn't the user waking the phone.
        if (System.currentTimeMillis() - launchedAt < 3000) return
        disarmPeek(c)
        if (expired) {
            expired = false
            close.value++
        }
    }

    /** Listens to the proximity sensor while the screen is off: near, then far again, is a peek. */
    private fun armPeek(c: Context) {
        if (peekListener != null) return
        val sm = c.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, true) ?: sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        var wasNear = false
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val near = e.values.firstOrNull()?.let { it < e.sensor.maximumRange.coerceAtMost(5f) } == true
                if (near) {
                    wasNear = true
                } else if (wasNear) {
                    wasNear = false
                    val awake = c.getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
                    if (!showing && !awake && !blocked(c)) launch(c, peek = true)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        peekListener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun disarmPeek(c: Context) {
        val l = peekListener ?: return
        c.applicationContext.getSystemService(SensorManager::class.java)?.unregisterListener(l)
        peekListener = null
    }
}

class GlanceActivity : ComponentActivity(), SensorEventListener {
    companion object {
        const val EXTRA_PEEK = "peek"
        /** Opened from Settings to try it: the phone is unlocked, nothing to wake. */
        const val EXTRA_TRY = "try"
    }

    private var sensors: SensorManager? = null
    private var nearSince = 0L
    private val createdAt = System.currentTimeMillis()
    /** When it dimmed itself to let the screen go off (0 while it is showing). */
    private var expiredAt = 0L

    /**
     * The screen went off while the glance screen was up. Either Android's time-out after it
     * dimmed itself (then it just goes away), or the user pressed the power key to turn on what
     * looked like an off screen: then the phone wakes to the lock screen, at normal brightness.
     */
    private val screenOff = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val timeout = runCatching { android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_OFF_TIMEOUT) }.getOrDefault(30_000)
            val timedOut = expiredAt > 0 && System.currentTimeMillis() - expiredAt >= timeout - 1500
            finish()
            if (!timedOut) {
                Glance.expired = false
                wakeScreen()
            }
        }
    }

    /** Unlocked (fingerprint, face...) while it showed: the glance screen must not stay on top. */
    private val unlocked = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (System.currentTimeMillis() - createdAt > 3000) wake()
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        runCatching {
            getSystemService(android.os.PowerManager::class.java)
                ?.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "mozaik:glance-wake")
                ?.acquire(1500)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Glance.showing = true
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // As dim as the panel goes: black pixels are off on an OLED screen.
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        androidx.core.content.ContextCompat.registerReceiver(this, screenOff, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        if (!intent.getBooleanExtra(EXTRA_TRY, false)) {
            androidx.core.content.ContextCompat.registerReceiver(this, unlocked, android.content.IntentFilter(Intent.ACTION_USER_PRESENT), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
        sensors = getSystemService(SensorManager::class.java)
        sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        setContent {
            WpTheme {
                val closeTick by Glance.close.collectAsState()
                val first = remember { closeTick }
                LaunchedEffect(closeTick) { if (closeTick != first) finish() }
                GlanceScreen(peek = intent.getBooleanExtra(EXTRA_PEEK, false), onWake = ::wake, onTimeout = ::expire)
            }
        }
    }

    /** A tap (or back): the phone wakes (the lock screen at normal brightness). */
    private fun wake() {
        Glance.expired = false
        Glance.disarmPeek(this)
        finish()
    }

    /** Let the screen go off as usual, and stay away until the user turns it on. */
    private fun expire() {
        if (!intent.getBooleanExtra(EXTRA_PEEK, false)) Glance.expired = true
        if (expiredAt == 0L) expiredAt = System.currentTimeMillis()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0f }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val near = event.values.firstOrNull()?.let { it < (event.sensor.maximumRange.coerceAtMost(5f)) } == true
        if (near) {
            if (nearSince == 0L) nearSince = System.currentTimeMillis()
            if (System.currentTimeMillis() - nearSince > 2000) expire()
        } else {
            nearSince = 0L
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = wake()

    override fun onDestroy() {
        Glance.showing = false
        runCatching { unregisterReceiver(screenOff) }
        runCatching { unregisterReceiver(unlocked) }
        sensors?.unregisterListener(this)
        super.onDestroy()
    }
}

@Composable
private fun GlanceScreen(peek: Boolean, onWake: () -> Unit, onTimeout: () -> Unit) {
    val context = LocalContext.current
    val status = remember { SystemStatus.get(context) }
    val now by status.clock.collectAsState()
    val battery by status.battery.collectAsState()
    val notifications by NotificationCenter.items.collectAsState()
    val o = Wp.settings.glanceOptions
    val night = Glance.isNight(o, now)
    if (night && o.nightColor == "hidden") {
        LaunchedEffect(Unit) { onTimeout() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val color = Glance.color(if (night) o.nightColor else o.color, Wp.accent)
    LaunchedEffect(battery.charging, peek) {
        when {
            // A peek lasts a few seconds.
            peek -> {
                delay(8_000)
                onTimeout()
            }
            // 15 minutes on battery (unless the time-out is off), always while charging.
            !battery.charging && o.timeout -> {
                delay(15 * 60 * 1000L)
                onTimeout()
            }
        }
    }
    // The content moves a little every minute, so no pixel stays lit in one place.
    var shift by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(now / 60_000) { shift = Random.nextInt(-10, 11) to Random.nextInt(-14, 15) }
    val nextAlarm = remember(now / 60_000) {
        context.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.takeIf { it - now < 24L * 3600 * 1000 }
    }
    val wallpaper = Wp.settings.lockWallpaper
    var picture by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(o.background, wallpaper) {
        picture = if (o.background && wallpaper != null) {
            com.wpreborn.core.ui.ImageLoader.full(context, android.net.Uri.parse(wallpaper), 1280)
        } else {
            null
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { onWake() }) },
    ) {
        picture?.let {
            // The lock screen picture, kept dark so it costs little and the type stays readable.
            androidx.compose.foundation.Image(
                it.asImageBitmap(), null, Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = if (night) 0.12f else 0.3f,
            )
        }
        Column(
            Modifier.fillMaxSize().offset(shift.first.dp, shift.second.dp).padding(start = 28.dp, end = 24.dp, top = 56.dp, bottom = 56.dp),
        ) {
            when (o.detail) {
                "weather" -> GlanceWeather(color)
                "calendar" -> GlanceCalendar(color, now)
                "music" -> GlanceMusic(color)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Top) {
                val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(now)).replace(Regex("\\s?[AaPp]\\.?\\s?[Mm]\\.?"), "")
                WpText(time, style = WpType.LockClock.copy(fontSize = 96.sp, lineHeight = 100.sp, fontWeight = FontWeight.Light), color = color)
                if (nextAlarm != null) WpIcon(WpIcons.Alarm, Modifier.padding(start = 4.dp, top = 18.dp), tint = color, size = 22.dp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    WpText(android.text.format.DateFormat.format("EEEE", now).toString().replaceFirstChar { it.uppercase() }, style = WpType.ExtraLarge.copy(fontWeight = FontWeight.Light), color = color)
                    WpText(android.text.format.DateFormat.format("d MMMM", now).toString(), style = WpType.ExtraLarge.copy(fontWeight = FontWeight.Light), color = color)
                }
                // Notification icons with their counts, like the Lumia's.
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (battery.charging || o.battery) GlanceBattery(battery.level, battery.charging, color)
                    if (o.icons) {
                        notifications.groupingBy { it.packageName }.eachCount().entries.take(5).forEach { (pkg, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.wpreborn.launcher.actioncenter.SquareAppIcon(pkg, 20.dp, color, plain = true, tint = color)
                                Spacer(Modifier.width(4.dp))
                                WpText(n.toString(), style = WpType.Large, color = color)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The battery outline, filled to its level, with the percentage (as on the Lumia). */
@Composable
private fun GlanceBattery(level: Int, charging: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WpText("$level%", style = WpType.Normal, color = color)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.width(26.dp).height(12.dp)) {
            val stroke = 1.5.dp.toPx()
            val body = size.width - 3.dp.toPx()
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(body - stroke, size.height - stroke), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(body, size.height * 0.3f), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height * 0.4f))
            val inset = stroke * 2
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size((body - inset * 2) * level.coerceIn(0, 100) / 100f, size.height - inset * 2))
        }
    }
}

/** The next appointment today or tomorrow: time, title and place, like the Lumia's calendar status. */
@Composable
private fun GlanceCalendar(color: Color, now: Long) {
    val context = LocalContext.current
    var event by remember { mutableStateOf<com.wpreborn.data.calendar.CalendarEvent?>(null) }
    LaunchedEffect(now / (5 * 60_000)) {
        event = runCatching {
            com.wpreborn.data.calendar.CalendarRepository(context).upcoming(days = 2, max = 5).firstOrNull { it.end > now }
        }.getOrNull()
    }
    val e = event ?: return
    Row(verticalAlignment = Alignment.Top) {
        WpIcon(WpIcons.Calendar, Modifier.padding(top = 4.dp, end = 10.dp), tint = color, size = 28.dp)
        Column {
            val df = android.text.format.DateFormat.getTimeFormat(context)
            val day = if (android.text.format.DateUtils.isToday(e.begin)) "" else android.text.format.DateFormat.format("EEE ", e.begin).toString()
            val time = if (e.allDay) stringResource(com.wpreborn.R.string.glance_all_day) else day + df.format(Date(e.begin))
            WpText(time, style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = color)
            WpText(e.title, style = WpType.Large, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.location.isNotBlank()) WpText(e.location, style = WpType.Normal, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What's playing (PulseRadio or another app), in the glance colour. */
@Composable
private fun GlanceMusic(color: Color) {
    val p = com.wpreborn.data.media.rememberPlaybackVisible(com.wpreborn.data.media.rememberPlayback()) ?: return
    Row(verticalAlignment = Alignment.Top) {
        WpIcon(WpIcons.Music, Modifier.padding(top = 4.dp, end = 10.dp), tint = color, size = 28.dp)
        Column {
            WpText(p.title, style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.artist.isNotBlank()) WpText(p.artist, style = WpType.Large, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Today (place, sky, temperature, high / low) and the next two days, separated by thin lines. */
@Composable
private fun GlanceWeather(color: Color) {
    val context = LocalContext.current
    val repo = remember { com.wpreborn.data.weather.WeatherRepository.get(context) }
    val forecasts by repo.forecasts.collectAsState()
    val places by repo.places.collectAsState()
    val f by repo.fahrenheit.collectAsState()
    val place = places.firstOrNull() ?: return
    val w = forecasts[place.id] ?: return
    fun deg(c: Double) = com.wpreborn.data.weather.WeatherRepository.toUnit(c, f).let { kotlin.math.round(it).toInt() }.toString() + "°"
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            WpText(place.name, style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(stringResource(com.wpreborn.apps.weather.Sky.of(w.code).label), style = WpType.Large, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.wpreborn.apps.weather.WeatherGlyph(w.code, w.isDay, 46.dp, color = color)
                Spacer(Modifier.width(8.dp))
                WpText(deg(w.temp) + if (f) "F" else "C", style = WpType.Header.copy(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Light), color = color)
            }
            w.days.firstOrNull()?.let { d -> HighLow(deg(d.max), deg(d.min), color, Modifier.padding(start = 70.dp)) }
        }
        w.days.drop(1).take(2).forEach { d ->
            Canvas(Modifier.width(18.dp).height(150.dp)) {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.8f, 0f), androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height), 1.5.dp.toPx())
            }
            Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WpText(android.text.format.DateFormat.format("EEE", d.date).toString().uppercase(), style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = color)
                com.wpreborn.apps.weather.WeatherGlyph(d.code, true, 28.dp, color = color)
                HighLow(deg(d.max), deg(d.min), color)
            }
        }
    }
}

/** High over low with a line between them, as on the Lumia. */
@Composable
private fun HighLow(high: String, low: String, color: Color, modifier: Modifier = Modifier, width: Dp = 30.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        WpText(high, style = WpType.Normal, color = color)
        Box(Modifier.width(width).height(1.dp).background(color))
        WpText(low, style = WpType.Normal, color = color)
    }
}
