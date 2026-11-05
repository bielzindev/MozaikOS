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

    fun onScreenOff(c: Context) {
        val s = SettingsRepository.get(c).cached
        if (!s.glance || expired || !c.canDrawOverlays()) return
        val am = c.getSystemService(AudioManager::class.java)
        if (am != null && (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE)) return
        // After the lock screen, so the glance screen is the one on top.
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                c.startActivity(
                    Intent(c, GlanceActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    ),
                )
            }
        }, 400)
    }

    fun onScreenOn() {
        if (expired) {
            expired = false
            close.value++
        }
    }
}

class GlanceActivity : ComponentActivity(), SensorEventListener {
    private var sensors: SensorManager? = null
    private var nearSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        sensors = getSystemService(SensorManager::class.java)
        sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        setContent {
            WpTheme {
                val closeTick by Glance.close.collectAsState()
                val first = remember { closeTick }
                LaunchedEffect(closeTick) { if (closeTick != first) finish() }
                GlanceScreen(onWake = ::wake, onTimeout = ::expire)
            }
        }
    }

    /** Double tap: the phone wakes (the lock screen at normal brightness). */
    private fun wake() {
        Glance.expired = false
        finish()
    }

    /** Let the screen go off as usual, and stay away until the user turns it on. */
    private fun expire() {
        Glance.expired = true
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

    override fun onDestroy() {
        sensors?.unregisterListener(this)
        super.onDestroy()
    }
}

@Composable
private fun GlanceScreen(onWake: () -> Unit, onTimeout: () -> Unit) {
    val context = LocalContext.current
    val status = remember { SystemStatus.get(context) }
    val now by status.clock.collectAsState()
    val battery by status.battery.collectAsState()
    val notifications by NotificationCenter.items.collectAsState()
    val color = Wp.accent
    // 15 minutes on battery, always while charging.
    LaunchedEffect(battery.charging) {
        if (!battery.charging) {
            delay(15 * 60 * 1000L)
            onTimeout()
        }
    }
    // The content moves a little every minute, so no pixel stays lit in one place.
    var shift by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(now / 60_000) { shift = Random.nextInt(-10, 11) to Random.nextInt(-14, 15) }
    val nextAlarm = remember(now / 60_000) {
        context.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.takeIf { it - now < 24L * 3600 * 1000 }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onWake() }) },
    ) {
        Column(
            Modifier.fillMaxSize().offset(shift.first.dp, shift.second.dp).padding(start = 28.dp, end = 24.dp, top = 56.dp, bottom = 56.dp),
        ) {
            GlanceWeather(color)
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
                    if (battery.charging) WpText("${battery.level}%", style = WpType.Normal, color = color)
                    notifications.groupingBy { it.packageName }.eachCount().entries.take(4).forEach { (pkg, n) ->
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
