package com.wpreborn.data.mail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wpreborn.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/** Unread mail across the accounts, for the Mail tile and Start. */
object MailStatus {
    private val _unread = MutableStateFlow(-1)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    fun get(context: Context): StateFlow<Int> {
        if (_unread.value < 0) _unread.value = context.getSharedPreferences("mail_status", Context.MODE_PRIVATE).getInt("unread", 0)
        return unread
    }

    fun set(context: Context, n: Int) {
        _unread.value = n
        context.getSharedPreferences("mail_status", Context.MODE_PRIVATE).edit().putInt("unread", n).apply()
    }
}

/**
 * Checks the inboxes every 15 minutes (with a connection): the unread count for the tile, and a
 * notification for each new message, like Outlook Mail on Windows Phone.
 */
class MailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        check(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK = "mozaik_mail_sync"
        private const val CHANNEL = "mail"

        /** Turns the periodic check on while there is an account (off without one). */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (MailAccounts.get(context).accounts.value.isEmpty()) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val req = PeriodicWorkRequestBuilder<MailSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        suspend fun check(context: Context) {
            val accounts = MailAccounts.get(context)
            val prefs = context.getSharedPreferences("mail_status", Context.MODE_PRIVATE)
            var unread = 0
            for (a in accounts.accounts.value) {
                val pwd = accounts.password(a.id) ?: continue
                val (n, newest) = runCatching { MailClient.inboxStatus(a, pwd) }.getOrNull() ?: continue
                unread += n
                val last = prefs.getLong("last_" + a.id, -1L)
                val fresh = if (last < 0) emptyList() else newest.filter { it.uid > last && !it.seen }
                newest.maxOfOrNull { it.uid }?.let { prefs.edit().putLong("last_" + a.id, maxOf(it, last)).apply() }
                fresh.take(5).forEach { notify(context, a, it) }
            }
            MailStatus.set(context, unread)
        }

        private fun notify(context: Context, a: MailAccount, h: MailHeader) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.app_mail), NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = PendingIntent.getActivity(
                context, a.id.hashCode(),
                Intent(context, com.wpreborn.apps.mail.MailActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_mail_foreground)
                .setContentTitle(h.from.ifBlank { h.fromAddress })
                .setContentText(h.subject)
                .setSubText(a.email)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .build()
            runCatching { nm.notify((a.id + h.uid).hashCode(), n) }
        }
    }
}
