package com.wpreborn.apps.alarms

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpCheckBox
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.alarms.Alarm
import com.wpreborn.data.alarms.AlarmStore
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.rememberPermissionState
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface AlarmPage {
    data object List : AlarmPage
    data class Edit(val id: Int) : AlarmPage
}

/**
 * Alarms, like the Windows Phone 8.1 app: every alarm with its time, name and days, an
 * on/off switch, and a page to set the time with looping wheels, the name and the repeat
 * days. Alarms ring through Android's alarm clock, also with the phone locked.
 */
class AlarmsActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val store = remember { AlarmStore.get(context) }
        val nav = rememberNavigator<AlarmPage>(AlarmPage.List)
        // Android 13+: the ringing screen comes through a notification.
        val notif = rememberPermissionState(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
        LaunchedEffect(Unit) { if (!notif.granted) notif.request() }
        WpNavHost(nav) { page ->
            when (page) {
                AlarmPage.List -> AlarmList(store, onEdit = { nav.navigate(AlarmPage.Edit(it)) })
                is AlarmPage.Edit -> AlarmEditor(store, page.id) { nav.pop() }
            }
        }
    }
}

@Composable
private fun AlarmList(store: AlarmStore, onEdit: (Int) -> Unit) {
    val context = LocalContext.current
    val alarms by store.alarms.collectAsState()
    val list = alarms.filterNot { it.timer }
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(null, stringResource(R.string.app_alarms).lowercase())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
            if (list.isEmpty()) item { WpText(stringResource(R.string.alarm_empty), style = WpType.Large, color = Wp.palette.subtle) }
            itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
                Row(
                    Modifier.fillMaxWidth().slideInStaggered(i).wpClickable { onEdit(a.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        WpText(timeText(context, a.hour, a.minute), style = WpType.Header, color = if (a.enabled) Wp.palette.foreground else Wp.palette.subtle)
                        WpText(
                            listOf(a.label.ifBlank { stringResource(R.string.alarm_default_name) }, daysText(a.days)).filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = WpType.Subtle,
                            color = Wp.palette.subtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(a.enabled) { store.setEnabled(a.id, it) }
                }
            }
        }
        WpAppBar(buttons = listOf(AppBarButton(WpIcons.Add, stringResource(R.string.alarm_new)) { onEdit(0) }))
    }
}

/** The 8.1 toggle switch: a bordered track with a block thumb. */
@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 56.dp, height = 26.dp)
            .border(2.dp, Wp.palette.foreground)
            .wpClickable(tilt = false) { onChange(!on) }
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxSize().padding(end = if (on) 14.dp else 0.dp, start = if (on) 0.dp else 14.dp).background(if (on) Wp.accent else Color.Transparent))
        Box(Modifier.width(12.dp).height(26.dp).background(Wp.palette.foreground))
    }
}

@Composable
private fun AlarmEditor(store: AlarmStore, id: Int, onDone: () -> Unit) {
    val existing = remember { store.get(id) }
    val now = remember { Calendar.getInstance() }
    var hour by remember { mutableStateOf(existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(existing?.minute ?: now.get(Calendar.MINUTE)) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_alarms), stringResource(if (existing == null) R.string.alarm_new else R.string.alarm_edit))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            WpText(stringResource(R.string.alarm_name), style = WpType.Subtle, color = Wp.palette.subtle)
            WpTextBox(label, { label = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.alarm_default_name))
            Spacer(Modifier.height(16.dp))
            WpText(stringResource(R.string.alarm_time), style = WpType.Subtle, color = Wp.palette.subtle)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberWheel(0..23, hour) { hour = it }
                NumberWheel(0..59, minute) { minute = it }
            }
            WpText(stringResource(R.string.alarm_repeats), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
            val names = remember { DateFormatSymbols.getInstance().weekdays }
            val first = Calendar.getInstance().firstDayOfWeek
            (0 until 7).map { (first - 1 + it) % 7 + 1 }.forEach { d ->
                WpCheckBox(names[d], d in days) { on -> days = if (on) days + d else days - d }
            }
        }
        WpAppBar(
            buttons = listOfNotNull(
                AppBarButton(WpIcons.Check, stringResource(R.string.save)) {
                    store.save(Alarm(id = existing?.id ?: 0, hour = hour, minute = minute, label = label.trim(), days = days, enabled = true))
                    onDone()
                },
                existing?.let { AppBarButton(WpIcons.Delete, stringResource(R.string.delete)) { store.delete(it.id); onDone() } },
            ),
        )
    }
}

/**
 * The Windows Phone looping picker: a column of large numbers that scrolls and snaps; the
 * selected one is in a box with the accent border.
 */
@Composable
fun NumberWheel(range: IntRange, value: Int, onChange: (Int) -> Unit) {
    val count = range.last - range.first + 1
    val loops = 400
    val start = loops / 2 * count + (value - range.first)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start - 1)
    val fling = rememberSnapFlingBehavior(state)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (first, moving) -> if (!moving) onChange(range.first + (first + 1) % count) }
    }
    Box(Modifier.width(96.dp).height(3 * 76.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(96.dp, 76.dp).border(2.dp, Wp.accent))
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            items(loops * count) { i ->
                val n = range.first + i % count
                Box(Modifier.fillMaxWidth().height(76.dp), contentAlignment = Alignment.Center) {
                    WpText("%02d".format(n), style = WpType.PivotHeader)
                }
            }
        }
    }
}

private fun timeText(context: android.content.Context, h: Int, m: Int): String {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
    return DateFormat.getTimeFormat(context).format(c.time)
}

@Composable
private fun daysText(days: Set<Int>): String = when {
    days.isEmpty() -> stringResource(R.string.alarm_once)
    days.size == 7 -> stringResource(R.string.alarm_every_day)
    else -> {
        val short = remember { DateFormatSymbols.getInstance().shortWeekdays }
        (1..7).filter { it in days }.joinToString(" ") { short[it] }
    }
}
