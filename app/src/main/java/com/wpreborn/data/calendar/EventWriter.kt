package com.wpreborn.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** A calendar events can be written to (the phone's, Google's...). */
data class WritableCalendar(val id: Long, val name: String, val color: Int)

/** What the event editor edits. */
data class EventDraft(
    val title: String = "",
    val location: String = "",
    val notes: String = "",
    val begin: Long,
    val end: Long,
    val allDay: Boolean = false,
    val calendarId: Long? = null,
    /** Minutes before the start, or -1 for none. */
    val reminder: Int = 15,
)

/** Creates, changes and deletes events in the phone's calendars, so Calendar needs no other app. Requires WRITE_CALENDAR. */
class EventWriter(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun calendars(): List<WritableCalendar> = withContext(Dispatchers.IO) {
        val out = ArrayList<WritableCalendar>()
        runCatching {
            resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.CALENDAR_COLOR),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${CalendarContract.Calendars.VISIBLE} = 1",
                null, "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { c -> while (c.moveToNext()) out += WritableCalendar(c.getLong(0), c.getString(1).orEmpty(), c.getInt(2)) }
        }
        out
    }

    /** The phone's own calendar, made when there is none to write to (no account on the phone). */
    private fun localCalendar(): Long? = runCatching {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Mozaik")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val v = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "Mozaik")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Mozaik")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Mozaik")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF1BA1E2.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "Mozaik")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        resolver.insert(uri, v)?.let { ContentUris.parseId(it) }
    }.getOrNull()

    suspend fun load(eventId: Long): EventDraft? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                arrayOf(
                    CalendarContract.Events.TITLE, CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY, CalendarContract.Events.CALENDAR_ID,
                ),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) null else EventDraft(
                    title = c.getString(0).orEmpty(), location = c.getString(1).orEmpty(), notes = c.getString(2).orEmpty(),
                    begin = c.getLong(3), end = c.getLong(4).takeIf { it > 0 } ?: (c.getLong(3) + 3_600_000L),
                    allDay = c.getInt(5) == 1, calendarId = c.getLong(6), reminder = -2,
                )
            }
        }.getOrNull()
    }

    /** Saves [d] as a new event (eventId null) or over an existing one. Returns the event id, or null. */
    suspend fun save(eventId: Long?, d: EventDraft): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val cal = d.calendarId ?: calendars().firstOrNull()?.id ?: localCalendar() ?: return@runCatching null
            val v = ContentValues().apply {
                put(CalendarContract.Events.TITLE, d.title.trim())
                put(CalendarContract.Events.EVENT_LOCATION, d.location.trim())
                put(CalendarContract.Events.DESCRIPTION, d.notes.trim())
                put(CalendarContract.Events.CALENDAR_ID, cal)
                if (d.allDay) {
                    // All-day events are stored in UTC at midnight, as the calendar expects.
                    val tz = TimeZone.getDefault()
                    val start = d.begin + tz.getOffset(d.begin)
                    val day = 86_400_000L
                    val s = start - Math.floorMod(start, day)
                    val e = maxOf(s + day, (d.end + tz.getOffset(d.end)).let { it - Math.floorMod(it, day) + day })
                    put(CalendarContract.Events.DTSTART, s)
                    put(CalendarContract.Events.DTEND, e)
                    put(CalendarContract.Events.ALL_DAY, 1)
                    put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                } else {
                    put(CalendarContract.Events.DTSTART, d.begin)
                    put(CalendarContract.Events.DTEND, maxOf(d.end, d.begin + 60_000))
                    put(CalendarContract.Events.ALL_DAY, 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
            }
            val id = if (eventId == null) {
                resolver.insert(CalendarContract.Events.CONTENT_URI, v)?.let { ContentUris.parseId(it) }
            } else {
                resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), v, null, null)
                eventId
            } ?: return@runCatching null
            if (d.reminder >= -1) {
                resolver.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(id.toString()))
                if (d.reminder >= 0) {
                    resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, id)
                        put(CalendarContract.Reminders.MINUTES, d.reminder)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    })
                }
            }
            id
        }.getOrNull()
    }

    suspend fun delete(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0 }.getOrDefault(false)
    }
}
