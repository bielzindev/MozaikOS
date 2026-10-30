package com.wpreborn.data.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.wpreborn.data.calls.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val location: String,
    val description: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarName: String,
)

/** Reads event instances from the Android calendar provider. Requires READ_CALENDAR. */
class CalendarRepository(private val context: Context) {

    fun changes(): Flow<Unit> = observe(context, CalendarContract.Events.CONTENT_URI)

    suspend fun instances(from: Long, to: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val list = ArrayList<CalendarEvent>()
        try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, from)
                ContentUris.appendId(it, to)
            }.build()
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                ),
                "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    list += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1) ?: "",
                        location = c.getString(2) ?: "",
                        description = c.getString(3) ?: "",
                        begin = c.getLong(4),
                        end = c.getLong(5),
                        allDay = c.getInt(6) == 1,
                        color = c.getInt(7),
                        calendarName = c.getString(8) ?: "",
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        list
    }

    /** Next events from now, for the Calendar live tile and the lock screen. */
    suspend fun upcoming(days: Int = 7, max: Int = 5): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        return instances(now - 60 * 60 * 1000L, now + days * 24 * 60 * 60 * 1000L)
            .filter { it.end > now }
            .take(max)
    }
}
