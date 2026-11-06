package com.wpreborn.apps.calendar

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.rememberPivotState
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.calendar.CalendarEvent
import com.wpreborn.data.calendar.CalendarRepository
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.util.Calendar

private sealed interface CalPage {
    data object Main : CalPage
    data class Event(val event: CalendarEvent) : CalPage
    /** New appointment (eventId null) or editing one. */
    data class Edit(val eventId: Long?, val start: Long) : CalPage
}

private const val DAY = 24L * 60 * 60 * 1000

/** Calendar: agenda and month views over the Android calendar provider, plus event details. */
class CalendarActivity : WpActivity() {

    @Composable
    override fun Content() {
        val nav = rememberNavigator<CalPage>(CalPage.Main)
        WpNavHost(nav) { page ->
            when (page) {
                CalPage.Main -> CalendarMain(onNew = { nav.navigate(CalPage.Edit(null, it)) }) { nav.navigate(CalPage.Event(it)) }
                is CalPage.Event -> EventPage(page.event) { nav.navigate(CalPage.Edit(page.event.eventId, page.event.begin)) }
                is CalPage.Edit -> EventEditorPage(page.eventId, page.start) { saved ->
                    // After saving or deleting an appointment its details page is out of date.
                    if (saved && page.eventId != null) nav.popToRoot() else nav.pop()
                }
            }
        }
    }
}

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun CalendarMain(onNew: (Long) -> Unit, onOpen: (CalendarEvent) -> Unit) {
    val context = LocalContext.current
    val perm = rememberPermissionState(Perms.CALENDAR)
    val repo = remember { CalendarRepository(context) }
    val pivot = rememberPivotState(2)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var agenda by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    // Month currently displayed (any timestamp within it) and the selected day.
    var month by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selected by remember { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    var monthEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }

    LaunchedEffect(perm.granted) {
        if (!perm.granted) return@LaunchedEffect
        repo.changes().collect {
            val now = startOfDay(System.currentTimeMillis())
            agenda = repo.instances(now, now + 30 * DAY)
        }
    }
    LaunchedEffect(perm.granted, month) {
        if (!perm.granted) return@LaunchedEffect
        val c = Calendar.getInstance().apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, 1) }
        val from = startOfDay(c.timeInMillis) - 7 * DAY
        monthEvents = repo.instances(from, from + 45 * DAY)
    }

    Column(Modifier.fillMaxSize()) {
        WpPivot(
            title = stringResource(R.string.app_calendar),
            headers = listOf(stringResource(R.string.calendar_agenda), stringResource(R.string.calendar_month)),
            state = pivot,
            modifier = Modifier.weight(1f),
        ) { index ->
            PermissionGate(perm, stringResource(R.string.perm_calendar), Modifier.padding(start = 20.dp)) {
                if (index == 0) {
                    val list = agenda
                    when {
                        list == null -> LoadingDots(Modifier.padding(top = 24.dp))
                        list.isEmpty() -> CenteredMessage(stringResource(R.string.calendar_empty), Modifier.padding(start = 20.dp))
                        else -> Agenda(list, onOpen)
                    }
                } else {
                    MonthView(
                        month = month,
                        selected = selected,
                        events = monthEvents,
                        onSelect = { selected = it },
                        onMonth = { delta ->
                            month = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, delta) }.timeInMillis
                        },
                        onOpen = onOpen,
                    )
                }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Add, stringResource(R.string.calendar_new)) {
                    onNew(selected + 9 * 60 * 60 * 1000)
                },
                AppBarButton(WpIcons.Calendar, stringResource(R.string.today)) {
                    month = System.currentTimeMillis()
                    selected = startOfDay(month)
                    scope.launch { pivot.select(1) }
                },
            ),
        )
    }
}

@Composable
private fun Agenda(events: List<CalendarEvent>, onOpen: (CalendarEvent) -> Unit) {
    val context = LocalContext.current
    val byDay = remember(events) { events.groupBy { startOfDay(maxOf(it.begin, startOfDay(System.currentTimeMillis()))) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
        byDay.forEach { (day, list) ->
            item(key = "d$day") {
                WpText(WpFormat.dayLabel(context, day), style = WpType.GroupHeader, color = Wp.accent, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            items(list, key = { "${it.eventId}-${it.begin}" }) { e ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) { EventRow(e, onOpen) 
        }
    }
        }
    }
}

@Composable
private fun EventRow(e: CalendarEvent, onOpen: (CalendarEvent) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .slideInStaggered(0, key = e.eventId)
            .wpClickable { onOpen(e) }
            .padding(vertical = 6.dp),
    ) {
        Box(Modifier.width(6.dp).height(52.dp).background(if (e.color != 0) Color(e.color) else Wp.accent))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            WpText(e.title.ifBlank { stringResource(R.string.calendar_no_title) }, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = if (e.allDay) stringResource(R.string.calendar_all_day) else "${WpFormat.time(context, e.begin)} – ${WpFormat.time(context, e.end)}"
            WpText(listOf(time, e.location).filter { it.isNotBlank() }.joinToString("  "), style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MonthView(
    month: Long,
    selected: Long,
    events: List<CalendarEvent>,
    onSelect: (Long) -> Unit,
    onMonth: (Int) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, 1) }
    val title = DateFormat.format("MMMM yyyy", cal).toString().lowercase()
    val firstDow = cal.firstDayOfWeek
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthStart = startOfDay(cal.timeInMillis)
    val today = startOfDay(System.currentTimeMillis())
    val busy = remember(events) { events.map { startOfDay(it.begin) }.toSet() }
    val weekdayNames = remember(firstDow) {
        (0 until 7).map { i ->
            val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, (firstDow - 1 + i) % 7 + 1) }
            DateFormat.format("EEEEE", c).toString().lowercase()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            WpText(title, style = WpType.ExtraLarge, modifier = Modifier.weight(1f))
            RoundButton(WpIcons.Back, size = 36.dp) { onMonth(-1) }
            Spacer(Modifier.width(8.dp))
            RoundButton(WpIcons.Forward, size = 36.dp) { onMonth(1) }
        }
        Row {
            weekdayNames.forEach {
                WpText(it, style = WpType.Small, color = Wp.palette.subtle, textAlign = TextAlign.Start, modifier = Modifier.weight(1f).padding(start = 4.dp))
            }
        }
        val cells = offset + daysInMonth
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (c in 0 until 7) {
                    val cell = r * 7 + c
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (cell >= offset && cell < offset + daysInMonth) {
                            val day = monthStart + (cell - offset) * DAY + 2 * 60 * 60 * 1000
                            val dayStart = startOfDay(day)
                            val isToday = dayStart == today
                            val isSelected = dayStart == selected
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(if (isToday) Wp.accent else Wp.palette.raised)
                                    .then(if (isSelected) Modifier.border(2.dp, Wp.palette.foreground) else Modifier)
                                    .wpClickable { onSelect(dayStart) },
                            ) {
                                WpText(
                                    (cell - offset + 1).toString(),
                                    style = WpType.Normal,
                                    color = if (isToday) Color.White else Wp.palette.foreground,
                                    modifier = Modifier.padding(start = 5.dp, top = 2.dp),
                                )
                                if (dayStart in busy) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(5.dp)
                                            .size(width = 14.dp, height = 3.dp)
                                            .background(if (isToday) Color.White else Wp.accent),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val context = LocalContext.current
        WpText(WpFormat.dayLabel(context, selected), style = WpType.GroupHeader, color = Wp.accent)
        val dayEvents = events.filter { startOfDay(it.begin) == selected || (it.begin < selected && it.end > selected) }
        if (dayEvents.isEmpty()) {
            WpText(stringResource(R.string.calendar_day_empty), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            dayEvents.forEach { EventRow(it, onOpen) }
        }
        Spacer(Modifier.height(16.dp).fillMaxHeight())
    }
}

@Composable
private fun EventPage(e: CalendarEvent, onEdit: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_calendar), null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            WpText(e.title.ifBlank { stringResource(R.string.calendar_no_title) }, style = WpType.Header.copy(fontSize = WpType.ExtraLarge.fontSize * 1.3f), modifier = Modifier.slideInStaggered(0))
            Spacer(Modifier.height(12.dp))
            val when_ = if (e.allDay) {
                WpFormat.dayLabel(context, e.begin) + ", " + stringResource(R.string.calendar_all_day)
            } else {
                WpFormat.dayLabel(context, e.begin) + "\n" + WpFormat.time(context, e.begin) + " – " + WpFormat.time(context, e.end)
            }
            Detail(stringResource(R.string.calendar_when), when_, 1)
            if (e.location.isNotBlank()) Detail(stringResource(R.string.calendar_where), e.location, 2)
            if (e.calendarName.isNotBlank()) Detail(stringResource(R.string.calendar_calendar), e.calendarName, 3)
            if (e.description.isNotBlank()) Detail(stringResource(R.string.calendar_notes), e.description, 4)
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Edit, stringResource(R.string.edit), onClick = onEdit),
            ),
        )
    }
}

@Composable
private fun Detail(label: String, value: String, index: Int) {
    Column(Modifier.padding(vertical = 8.dp).slideInStaggered(index)) {
        WpText(label, style = WpType.Subtle, color = Wp.palette.subtle)
        WpText(value, style = WpType.Large)
    }
}
