package com.wpreborn.apps.calendar

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.alarms.NumberWheel
import com.wpreborn.apps.alarms.Switch
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpMessageBox
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.calendar.EventDraft
import com.wpreborn.data.calendar.EventWriter
import com.wpreborn.data.calendar.WritableCalendar
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.util.Calendar

private val REMINDERS = listOf(-1, 0, 5, 15, 30, 60, 1440)

/**
 * New / edit appointment in Calendar itself (Windows Phone's "new appointment"): subject,
 * location, when (start and end, or all day) chosen with the phone's spinning pickers,
 * reminder, calendar, notes; save, cancel, delete.
 */
@Composable
fun EventEditorPage(eventId: Long?, defaultStart: Long, onDone: (saved: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writer = remember { EventWriter(context) }
    val perm = rememberPermissionState(arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR))
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var begin by remember { mutableLongStateOf(defaultStart) }
    var end by remember { mutableLongStateOf(defaultStart + 3_600_000L) }
    var allDay by remember { mutableStateOf(false) }
    var reminder by remember { mutableIntStateOf(15) }
    var calendarId by remember { mutableStateOf<Long?>(null) }
    var calendars by remember { mutableStateOf<List<WritableCalendar>>(emptyList()) }
    var picking by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) } // (start?, date?)
    var failed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(perm.granted) {
        if (perm.granted) calendars = writer.calendars()
        if (eventId != null) writer.load(eventId)?.let { d ->
            title = d.title; location = d.location; notes = d.notes
            begin = d.begin; end = d.end; allDay = d.allDay; calendarId = d.calendarId
        }
    }
    fun save() {
        if (!perm.granted) { perm.request(); return }
        scope.launch {
            val id = writer.save(eventId, EventDraft(title, location, notes, begin, end, allDay, calendarId ?: calendars.firstOrNull()?.id, if (eventId != null && reminder == 15) -2 else reminder))
            if (id != null) onDone(true) else failed = true
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
            WpText(stringResource(R.string.app_calendar).uppercase(), style = WpType.AppTitle)
            WpText(stringResource(if (eventId == null) R.string.calendar_new_event else R.string.calendar_edit_event), style = WpType.Header)
            Label(R.string.calendar_subject)
            WpTextBox(title, { title = it }, Modifier.fillMaxWidth())
            Label(R.string.calendar_where)
            WpTextBox(location, { location = it }, Modifier.fillMaxWidth())
            Label(R.string.calendar_when)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WpText(stringResource(R.string.calendar_all_day), style = WpType.Large, modifier = Modifier.weight(1f))
                Switch(allDay) { allDay = it }
            }
            Spacer(Modifier.height(8.dp))
            WhenRow(R.string.calendar_starts, begin, allDay, onDate = { picking = true to true }, onTime = { picking = true to false })
            WhenRow(R.string.calendar_ends, end, allDay, onDate = { picking = false to true }, onTime = { picking = false to false })
            Label(R.string.calendar_reminder)
            Field(reminderText(reminder)) { reminder = REMINDERS[(REMINDERS.indexOf(reminder) + 1).mod(REMINDERS.size)] }
            if (calendars.size > 1) {
                Label(R.string.calendar_calendar)
                val cal = calendars.firstOrNull { it.id == calendarId } ?: calendars.first()
                Field(cal.name) { calendarId = calendars[(calendars.indexOf(cal) + 1).mod(calendars.size)].id }
            }
            Label(R.string.calendar_notes)
            WpTextBox(notes, { notes = it }, Modifier.fillMaxWidth(), singleLine = false)
            Spacer(Modifier.height(24.dp))
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.save), enabled = title.isNotBlank()) { save() },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { onDone(false) },
            ),
            menuItems = if (eventId != null) listOf(AppBarMenuItem(stringResource(R.string.delete)) { confirmDelete = true }) else emptyList(),
        )
    }
    picking?.let { (start, date) ->
        val value = if (start) begin else end
        DateTimePicker(value, date, onDismiss = { picking = null }) { picked ->
            if (start) {
                val len = end - begin
                begin = picked
                end = picked + len.coerceAtLeast(if (allDay) 0 else 60_000)
            } else {
                end = maxOf(picked, begin)
            }
            picking = null
        }
    }
    if (failed) {
        WpMessageBox(stringResource(R.string.calendar_save_failed_title), stringResource(R.string.calendar_save_failed), stringResource(R.string.ok), onConfirm = { failed = false }, onDismiss = { failed = false })
    }
    if (confirmDelete && eventId != null) {
        WpMessageBox(
            title = stringResource(R.string.calendar_delete_title), message = title, confirm = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; scope.launch { if (writer.delete(eventId)) onDone(true) else failed = true } },
            dismiss = stringResource(R.string.cancel), onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun reminderText(m: Int): String = when {
    m < 0 -> stringResource(R.string.calendar_reminder_none)
    m == 0 -> stringResource(R.string.calendar_reminder_at_time)
    m < 60 -> stringResource(R.string.calendar_reminder_minutes, m)
    m < 1440 -> stringResource(R.string.calendar_reminder_hours, m / 60)
    else -> stringResource(R.string.calendar_reminder_days, m / 1440)
}

@Composable
private fun Label(res: Int) {
    WpText(stringResource(res), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

/** A value you tap to change, in a box like the phone's list pickers. */
@Composable
private fun Field(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().background(Wp.palette.raised).wpClickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        WpText(text, style = WpType.Large)
    }
}

@Composable
private fun WhenRow(label: Int, time: Long, allDay: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    val context = LocalContext.current
    WpText(stringResource(label), style = WpType.Small, color = Wp.palette.subtle)
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(WpFormat.dayLabel(context, time), Modifier.weight(1.6f), onClick = onDate)
        if (!allDay) Field(WpFormat.time(context, time), Modifier.weight(1f), onClick = onTime)
    }
}

/** Windows Phone's spinning date picker (day, month, year) or time picker (hours, minutes). */
@Composable
private fun DateTimePicker(value: Long, date: Boolean, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    BackHandler(onBack = onDismiss)
    val cal = remember(value) { Calendar.getInstance().apply { timeInMillis = value } }
    var a by remember { mutableIntStateOf(if (date) cal.get(Calendar.DAY_OF_MONTH) else cal.get(Calendar.HOUR_OF_DAY)) }
    var b by remember { mutableIntStateOf(if (date) cal.get(Calendar.MONTH) + 1 else cal.get(Calendar.MINUTE)) }
    var c by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    Column(
        Modifier.fillMaxSize().background(Wp.palette.background).clickable(remember { MutableInteractionSource() }, null) {}.statusBarsPadding(),
    ) {
        WpText(
            stringResource(if (date) R.string.calendar_choose_date else R.string.calendar_choose_time).uppercase(),
            style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (date) {
                    NumberWheel(1..31, a) { a = it }
                    NumberWheel(1..12, b) { b = it }
                    NumberWheel(2000..2050, c) { c = it }
                } else {
                    NumberWheel(0..23, a) { a = it }
                    NumberWheel(0..59, b) { b = it }
                }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.ok)) {
                    val out = Calendar.getInstance().apply { timeInMillis = value }
                    if (date) {
                        out.set(Calendar.DAY_OF_MONTH, 1)
                        out.set(Calendar.YEAR, c)
                        out.set(Calendar.MONTH, b - 1)
                        out.set(Calendar.DAY_OF_MONTH, a.coerceAtMost(out.getActualMaximum(Calendar.DAY_OF_MONTH)))
                    } else {
                        out.set(Calendar.HOUR_OF_DAY, a)
                        out.set(Calendar.MINUTE, b)
                        out.set(Calendar.SECOND, 0)
                        out.set(Calendar.MILLISECOND, 0)
                    }
                    onPick(out.timeInMillis)
                },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel), onClick = onDismiss),
            ),
        )
    }
}
