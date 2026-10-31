package com.wpreborn.data.alarms

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wpreborn.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** A Windows Phone alarm: time, name, repeat days (Calendar.SUNDAY..SATURDAY) and on/off. */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    /** One-shot timer alarm (from the Clock's timer) instead of a clock alarm. */
    val timer: Boolean = false,
    /** For timers: the exact time it fires. */
    val at: Long = 0,
) {
    /** The next time this alarm rings after [now]. */
    fun next(now: Long = System.currentTimeMillis()): Long {
        if (timer) return at
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        if (days.isNotEmpty()) {
            repeat(7) { if (c.get(Calendar.DAY_OF_WEEK) !in days) c.add(Calendar.DAY_OF_YEAR, 1) }
        }
        return c.timeInMillis
    }
}

/**
 * Alarms that really ring: each enabled alarm is scheduled with AlarmManager.setAlarmClock
 * (shown by Android as the next alarm and allowed in Doze); [AlarmReceiver] shows the ringing
 * screen over the lock screen; repeating alarms reschedule themselves; [BootReceiver] restores
 * everything after a restart.
 */
class AlarmStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun get(id: Int) = _alarms.value.firstOrNull { it.id == id }

    fun save(alarm: Alarm) {
        val a = if (alarm.id == 0) alarm.copy(id = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1) else alarm
        write(_alarms.value.filterNot { it.id == a.id } + a)
        schedule(a)
    }

    fun delete(id: Int) {
        cancel(id)
        write(_alarms.value.filterNot { it.id == id })
    }

    fun setEnabled(id: Int, on: Boolean) = get(id)?.let { save(it.copy(enabled = on)) }

    /** Starts a countdown that rings like an alarm. Returns its id. */
    fun startTimer(durationMs: Long, label: String): Int {
        val a = Alarm(id = TIMER_ID, hour = 0, minute = 0, label = label, timer = true, at = System.currentTimeMillis() + durationMs)
        write(_alarms.value.filterNot { it.id == TIMER_ID } + a)
        schedule(a)
        return TIMER_ID
    }

    fun cancelTimer() = delete(TIMER_ID)

    fun activeTimer() = get(TIMER_ID)

    /** After it rang: one-shot alarms switch off, repeating ones go to their next day. */
    fun rang(id: Int) {
        val a = get(id) ?: return
        when {
            a.timer -> delete(id)
            a.days.isEmpty() -> write(_alarms.value.map { if (it.id == id) it.copy(enabled = false) else it })
            else -> schedule(a)
        }
    }

    fun snooze(id: Int, minutes: Int = 10) {
        val a = get(id) ?: Alarm(id, 0, 0)
        setExact(id, System.currentTimeMillis() + minutes * 60_000L)
        if (a.timer) write(_alarms.value.map { if (it.id == id) it.copy(at = System.currentTimeMillis() + minutes * 60_000L) else it })
    }

    fun rescheduleAll() = _alarms.value.forEach { schedule(it) }

    private fun schedule(a: Alarm) {
        cancel(a.id)
        if (!a.enabled || (a.timer && a.at < System.currentTimeMillis())) return
        setExact(a.id, a.next())
    }

    private fun setExact(id: Int, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val show = PendingIntent.getActivity(
            context, id, Intent(context, com.wpreborn.apps.alarms.AlarmsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), ringIntent(id)) }
            .onFailure { runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ringIntent(id)) } }
    }

    private fun cancel(id: Int) {
        context.getSystemService(AlarmManager::class.java)?.cancel(ringIntent(id))
    }

    private fun ringIntent(id: Int) = PendingIntent.getBroadcast(
        context, id, Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun load(): List<Alarm> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val d = o.optJSONArray("days") ?: JSONArray()
            Alarm(
                o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optString("label"),
                List(d.length()) { d.getInt(it) }.toSet(), o.optBoolean("on", true), o.optBoolean("timer"), o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.timer }, { it.hour }, { it.minute }))
        _alarms.value = sorted
        val arr = JSONArray()
        sorted.forEach {
            arr.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("label", it.label).put("days", JSONArray(it.days.toList())).put("on", it.enabled).put("timer", it.timer).put("at", it.at))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    companion object {
        const val EXTRA_ID = "alarm_id"
        const val TIMER_ID = 9999
        const val CHANNEL = "alarms"

        @Volatile
        private var instance: AlarmStore? = null

        fun get(context: Context): AlarmStore =
            instance ?: synchronized(this) { instance ?: AlarmStore(context.applicationContext).also { instance = it } }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.app_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null) // the ringing screen plays the alarm sound itself
                        enableVibration(false)
                    },
                )
            }
        }
    }
}

/** Fires when an alarm is due: shows the ringing screen (full-screen over the lock screen). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmStore.EXTRA_ID, 0)
        val store = AlarmStore.get(context)
        val alarm = store.get(id)
        AlarmStore.ensureChannel(context)
        val ring = Intent(context, com.wpreborn.apps.alarms.AlarmRingActivity::class.java)
            .putExtra(AlarmStore.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val full = PendingIntent.getActivity(context, id, ring, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = alarm?.label?.takeIf { it.isNotBlank() } ?: context.getString(if (alarm?.timer == true) R.string.clock_timer_done else R.string.app_alarms)
        val n = NotificationCompat.Builder(context, AlarmStore.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alarm_tap_to_open))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_BASE + id, n) }
        // Also try to open the screen directly (works while the app may start activities).
        runCatching { context.startActivity(ring) }
        store.rang(id)
    }

    companion object {
        const val NOTIFICATION_BASE = 7000
    }
}

/** Restores all alarms after the phone restarts or the time changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmStore.get(context).rescheduleAll()
    }
}
