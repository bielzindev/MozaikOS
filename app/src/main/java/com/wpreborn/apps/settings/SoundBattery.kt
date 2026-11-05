package com.wpreborn.apps.settings

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun Context.openSystem(action: String, data: Uri? = null) {
    runCatching { startActivity(Intent(action).apply { if (data != null) this.data = data }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Ringtones+sounds, after Windows Phone: ring / vibrate / silent, the volumes of calls,
 * notifications, media and alarms, and the ringtone and notification sound chosen from a list
 * (tap to hear it), all in Mozaik.
 */
@Composable
fun SoundsPage() {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AudioManager::class.java) }
    var mode by remember { mutableIntStateOf(am.ringerMode) }
    var choosing by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_settings), stringResource(R.string.set_sounds))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            WpText(stringResource(R.string.sound_mode), style = WpType.Subtle, color = Wp.palette.subtle)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AudioManager.RINGER_MODE_NORMAL to R.string.sound_ring,
                    AudioManager.RINGER_MODE_VIBRATE to R.string.sound_vibrate,
                    AudioManager.RINGER_MODE_SILENT to R.string.sound_silent,
                ).forEach { (m, label) ->
                    val on = mode == m
                    Box(
                        Modifier.weight(1f).height(48.dp).background(if (on) Wp.accent else Color.Transparent).border(2.dp, if (on) Wp.accent else Wp.palette.subtle)
                            .wpClickable {
                                // Silent needs "Do not disturb" access on Android.
                                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                                if (m == AudioManager.RINGER_MODE_SILENT && nm?.isNotificationPolicyAccessGranted == false) {
                                    context.openSystem(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                } else {
                                    runCatching { am.ringerMode = m }
                                    mode = am.ringerMode
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { WpText(stringResource(label), style = WpType.Normal, color = if (on) Color.White else Wp.palette.foreground) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Volume(am, AudioManager.STREAM_RING, R.string.sound_vol_ring, enabled = mode == AudioManager.RINGER_MODE_NORMAL)
            Volume(am, AudioManager.STREAM_NOTIFICATION, R.string.sound_vol_notifications, enabled = mode == AudioManager.RINGER_MODE_NORMAL)
            Volume(am, AudioManager.STREAM_MUSIC, R.string.sound_vol_media)
            Volume(am, AudioManager.STREAM_ALARM, R.string.sound_vol_alarm)
            Spacer(Modifier.height(12.dp))
            SoundChoice(RingtoneManager.TYPE_RINGTONE, R.string.sound_ringtone) { choosing = RingtoneManager.TYPE_RINGTONE }
            SoundChoice(RingtoneManager.TYPE_NOTIFICATION, R.string.sound_notification) { choosing = RingtoneManager.TYPE_NOTIFICATION }
            SoundChoice(RingtoneManager.TYPE_ALARM, R.string.sound_alarm) { choosing = RingtoneManager.TYPE_ALARM }
            Spacer(Modifier.height(16.dp))
            WpButton(stringResource(R.string.sound_more)) { context.openSystem(Settings.ACTION_SOUND_SETTINGS) }
            Spacer(Modifier.height(24.dp))
        }
    }
    choosing?.let { type -> RingtoneList(type) { choosing = null } }
}

@Composable
private fun Volume(am: AudioManager, stream: Int, label: Int, enabled: Boolean = true) {
    val max = remember(stream) { am.getStreamMaxVolume(stream).coerceAtLeast(1) }
    var value by remember(stream) { mutableFloatStateOf(am.getStreamVolume(stream).toFloat() / max) }
    WpText(stringResource(label), style = WpType.Subtle, color = if (enabled) Wp.palette.subtle else Wp.palette.disabled, modifier = Modifier.padding(top = 10.dp))
    WpSlider(value, { v ->
        if (!enabled) return@WpSlider
        value = v
        runCatching { am.setStreamVolume(stream, (v * max).toInt(), 0) }
    }, Modifier.fillMaxWidth())
}

@Composable
private fun SoundChoice(type: Int, label: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val name by produceState("", type) {
        value = withContext(Dispatchers.IO) {
            runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, type)?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } }.getOrNull().orEmpty()
        }
    }
    Column(Modifier.fillMaxWidth().wpClickable(onClick = onClick).padding(vertical = 8.dp)) {
        WpText(stringResource(label), style = WpType.Large)
        WpText(name.ifBlank { stringResource(R.string.sound_none) }, style = WpType.Small, color = Wp.accent)
    }
}

/** The sounds on the phone; tap one to hear it, then "choose". */
@Composable
private fun RingtoneList(type: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    androidx.activity.compose.BackHandler(onBack = onDone)
    val tones by produceState<List<Pair<String, Uri>>>(emptyList(), type) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val rm = RingtoneManager(context).apply { setType(type) }
                val c = rm.cursor
                val out = ArrayList<Pair<String, Uri>>()
                while (c.moveToNext()) out += c.getString(RingtoneManager.TITLE_COLUMN_INDEX) to rm.getRingtoneUri(c.position)
                out
            }.getOrDefault(emptyList())
        }
    }
    var chosen by remember { mutableStateOf<Uri?>(runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, type) }.getOrNull()) }
    var playing by remember { mutableStateOf<android.media.Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { playing?.stop() } }
    var needsPermission by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Wp.palette.background).wpClickable(tilt = false) {}) {
        WpPageHeader(stringResource(R.string.app_settings), stringResource(R.string.sound_choose))
        if (needsPermission) {
            WpText(stringResource(R.string.sound_needs_permission), style = WpType.Small, color = Wp.accent, modifier = Modifier.padding(horizontal = 20.dp))
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            items(tones.size) { i ->
                val (title, uri) = tones[i]
                val on = chosen == uri
                Row(
                    Modifier.fillMaxWidth().wpClickable {
                        chosen = uri
                        playing?.stop()
                        playing = runCatching { RingtoneManager.getRingtone(context, uri)?.also { it.play() } }.getOrNull()
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp).border(2.dp, Wp.palette.foreground, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(10.dp).background(Wp.palette.foreground, androidx.compose.foundation.shape.CircleShape))
                    }
                    Spacer(Modifier.padding(start = 12.dp))
                    WpText(title, style = WpType.Large)
                }
            }
        }
        com.wpreborn.core.ui.WpAppBar(
            buttons = listOf(
                com.wpreborn.core.ui.AppBarButton(WpIcons.Check, stringResource(R.string.ok), enabled = chosen != null) {
                    playing?.stop()
                    // Changing the phone's sound needs "modify system settings" once.
                    if (!Settings.System.canWrite(context)) {
                        needsPermission = true
                        context.openSystem(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
                    } else {
                        runCatching { RingtoneManager.setActualDefaultRingtoneUri(context, type, chosen) }
                        onDone()
                    }
                },
                com.wpreborn.core.ui.AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { playing?.stop(); onDone() },
            ),
        )
    }
}

/**
 * Battery, after Windows Phone's Battery saver: the charge in large numbers, whether it is
 * charging and how long until full, health, temperature and voltage, and the saver.
 */
@Composable
fun BatteryPage() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val info = remember(tick) { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val level = info?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    val status = info?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val temp = (info?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val volt = (info?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
    val health = when (info?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> R.string.battery_health_good
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.battery_health_hot
        BatteryManager.BATTERY_HEALTH_DEAD, BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE, BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.battery_health_bad
        else -> R.string.battery_health_unknown
    }
    val bm = remember { context.getSystemService(BatteryManager::class.java) }
    val toFull = if (charging && android.os.Build.VERSION.SDK_INT >= 28) bm?.computeChargeTimeRemaining()?.takeIf { it > 0 } else null
    val saver = remember(tick) { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_settings), stringResource(R.string.set_battery))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                WpText("$level", style = WpType.LockClock.copy(fontSize = 96.sp, lineHeight = 100.sp, fontWeight = FontWeight.Light), color = Wp.accent)
                WpText("%", style = WpType.ExtraLarge, color = Wp.accent, modifier = Modifier.padding(bottom = 16.dp))
            }
            WpText(
                when {
                    status == BatteryManager.BATTERY_STATUS_FULL -> stringResource(R.string.battery_full)
                    charging && toFull != null -> stringResource(R.string.battery_charging_until, (toFull / 3_600_000).toInt(), ((toFull / 60_000) % 60).toInt())
                    charging -> stringResource(R.string.battery_charging)
                    else -> stringResource(R.string.battery_on_battery)
                },
                style = WpType.Large,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(14.dp).background(Wp.palette.raised)) {
                Box(Modifier.fillMaxWidth(level / 100f).height(14.dp).background(Wp.accent))
            }
            Spacer(Modifier.height(20.dp))
            listOf(
                stringResource(R.string.battery_health) to stringResource(health),
                stringResource(R.string.battery_temperature) to "%.1f °C".format(temp),
                stringResource(R.string.battery_voltage) to "%.2f V".format(volt),
                stringResource(R.string.battery_saver) to stringResource(if (saver) R.string.on else R.string.off),
            ).forEach { (k, v) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    WpText(k, style = WpType.Subtle, color = Wp.palette.subtle)
                    WpText(v, style = WpType.Large)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Android lets only its own settings turn the saver on and off.
            WpButton(stringResource(R.string.battery_saver_open)) { context.openSystem(Settings.ACTION_BATTERY_SAVER_SETTINGS) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
