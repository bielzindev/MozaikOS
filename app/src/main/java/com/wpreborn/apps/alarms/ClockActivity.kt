package com.wpreborn.apps.alarms

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.alarms.AlarmStore
import kotlinx.coroutines.delay
import java.util.Date
import java.util.TimeZone

/**
 * Clock in the Windows Phone style: the time here and in other cities, a stopwatch with
 * laps (it keeps running while the app is closed) and a timer that rings like an alarm.
 */
class ClockActivity : WpActivity() {

    @Composable
    override fun Content() {
        WpPivot(
            title = stringResource(R.string.app_clock),
            headers = listOf(stringResource(R.string.clock_world), stringResource(R.string.clock_stopwatch), stringResource(R.string.clock_timer)),
        ) { page ->
            when (page) {
                0 -> WorldClock()
                1 -> Stopwatch()
                else -> Timer()
            }
        }
    }
}

@Composable
private fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

@Composable
private fun WorldClock() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("clock", Context.MODE_PRIVATE) }
    val zones = remember { mutableStateListOf<String>().apply { addAll(prefs.getString("zones", "")!!.split(",").filter { it.isNotBlank() }) } }
    fun save() = prefs.edit().putString("zones", zones.joinToString(",")).apply()
    val now = rememberNow()
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<String?>(null) }
    val timeFormat = remember { DateFormat.getTimeFormat(context) }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
            item {
                WpText(timeFormat.format(Date(now)), style = WpType.LockClock.copy(fontSize = 80.sp), modifier = Modifier.slideInStaggered(0))
                WpText(DateFormat.format("EEEE, d MMMM", now).toString(), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.slideInStaggered(1))
                Spacer(Modifier.height(16.dp))
            }
            itemsIndexed(zones, key = { _, z -> z }) { i, z ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
                val tz = TimeZone.getTimeZone(z)
                val fmt = remember(z) { (DateFormat.getTimeFormat(context).clone() as java.text.DateFormat).apply { timeZone = tz } }
                val diffH = (tz.getOffset(now) - TimeZone.getDefault().getOffset(now)) / 3_600_000f
                Row(
                    Modifier.fillMaxWidth().slideInStaggered(2 + i).wpCombinedClickable(onLongClick = { menu = z }) {}.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        WpText(z.substringAfterLast('/').replace('_', ' ').lowercase(), style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WpText((if (diffH >= 0) "+" else "") + (if (diffH % 1f == 0f) diffH.toInt().toString() else "%.1f".format(diffH)) + " h", style = WpType.Subtle, color = Wp.palette.subtle)
                    }
                    WpText(fmt.format(Date(now)), style = WpType.ExtraLarge)
                }
            
        }
    }
            item {
                if (adding) {
                    WpTextBox(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.clock_city_hint))
                    val q = query.trim()
                    if (q.length >= 2) {
                        TimeZone.getAvailableIDs().filter { it.contains('/') && it.substringAfterLast('/').replace('_', ' ').contains(q, true) }.take(8).forEach { id ->
                            WpText(
                                id.substringAfterLast('/').replace('_', ' ') + "  (" + id.substringBefore('/') + ")",
                                style = WpType.Large,
                                modifier = Modifier.fillMaxWidth().wpClickable {
                                    if (id !in zones) { zones.add(id); save() }
                                    adding = false; query = ""
                                }.padding(vertical = 6.dp),
                            )
                        }
                    }
                } else {
                    WpText(stringResource(R.string.clock_add_city), style = WpType.Large, color = Wp.accent, modifier = Modifier.wpClickable { adding = true }.padding(vertical = 12.dp))
                }
            }
        }
        menu?.let { z ->
            WpContextMenu(listOf(ContextMenuItem(stringResource(R.string.delete)) { zones.remove(z); save() }), anchorY = 400f, onDismiss = { menu = null })
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val h = ms / 3_600_000
    val m = ms / 60_000 % 60
    val s = ms / 1000 % 60
    val cs = ms / 10 % 100
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
}

@Composable
private fun Stopwatch() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("stopwatch", Context.MODE_PRIVATE) }
    // started > 0 while running; accumulated holds the time before the last start.
    var started by remember { mutableLongStateOf(prefs.getLong("started", 0)) }
    var accumulated by remember { mutableLongStateOf(prefs.getLong("acc", 0)) }
    val laps = remember { mutableStateListOf<Long>().apply { addAll(prefs.getString("laps", "")!!.split(",").mapNotNull { it.toLongOrNull() }) } }
    fun persist() = prefs.edit().putLong("started", started).putLong("acc", accumulated).putString("laps", laps.joinToString(",")).apply()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(started) {
        while (started > 0) {
            tick = System.currentTimeMillis()
            delay(30)
        }
    }
    val elapsed = accumulated + if (started > 0) (tick - started).coerceAtLeast(0) else 0
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        WpText(formatElapsed(elapsed), style = WpType.LockClock.copy(fontSize = 64.sp), maxLines = 1)
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WpButton(stringResource(if (started > 0) R.string.clock_stop else R.string.clock_start)) {
                val t = System.currentTimeMillis()
                if (started > 0) { accumulated += t - started; started = 0 } else { started = t; tick = t }
                persist()
            }
            if (started > 0) {
                WpButton(stringResource(R.string.clock_lap)) { laps.add(0, elapsed); persist() }
            } else if (elapsed > 0) {
                WpButton(stringResource(R.string.clock_reset)) { accumulated = 0; laps.clear(); persist() }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(laps) { i, lap ->
                val prev = laps.getOrNull(i + 1) ?: 0L
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    WpText(stringResource(R.string.clock_lap_n, laps.size - i), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.weight(1f))
                    WpText("+" + formatElapsed(lap - prev), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(end = 16.dp))
                    WpText(formatElapsed(lap), style = WpType.Large)
                }
            }
        }
    }
}

@Composable
private fun Timer() {
    val context = LocalContext.current
    val store = remember { AlarmStore.get(context) }
    val alarms by store.alarms.collectAsState()
    val running = alarms.firstOrNull { it.id == AlarmStore.TIMER_ID }
    var minutes by remember { mutableStateOf(5) }
    var seconds by remember { mutableStateOf(0) }
    val now = rememberNow(250)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (running != null) {
            val left = (running.at - now).coerceAtLeast(0)
            WpText(formatElapsed(left).substringBeforeLast('.'), style = WpType.LockClock.copy(fontSize = 80.sp))
            WpText(stringResource(R.string.clock_timer_running), style = WpType.Large, color = Wp.palette.subtle)
            Spacer(Modifier.height(16.dp))
            WpButton(stringResource(R.string.cancel)) { store.cancelTimer() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberWheel(0..99, minutes) { minutes = it }
                WpText(":", style = WpType.PivotHeader)
                NumberWheel(0..59, seconds) { seconds = it }
            }
            Spacer(Modifier.height(16.dp))
            WpButton(stringResource(R.string.clock_start)) {
                val ms = (minutes * 60L + seconds) * 1000L
                if (ms > 0) store.startTimer(ms, context.getString(R.string.clock_timer))
            }
        }
    }
}
