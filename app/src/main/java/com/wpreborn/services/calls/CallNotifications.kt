package com.wpreborn.services.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import com.wpreborn.R
import com.wpreborn.apps.phone.PhoneActivity
import com.wpreborn.data.contacts.ContactsRepository

/** The call notification: incoming (full screen, answer / decline) or in progress (hang up). */
object CallNotifications {
    private const val CHANNEL_INCOMING = "calls_incoming"
    private const val CHANNEL_ONGOING = "calls_ongoing"
    private const val CHANNEL_MISSED = "calls_missed"
    private const val ID = 4711
    private const val ID_MISSED = 4712

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_INCOMING) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.call_incoming), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.call_ongoing), NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_MISSED, context.getString(R.string.call_missed), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun action(context: Context, what: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, Intent(context, CallActionReceiver::class.java).setAction(what),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun update(context: Context) {
        val call = CallManager.primary
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (call == null || call.ended) {
            nm.cancel(ID)
            return
        }
        channels(context)
        val name = runCatching { ContactsRepository(context).lookupNumber(call.number)?.first }.getOrNull() ?: com.wpreborn.core.PhoneFormat.br(call.number).ifBlank { context.getString(R.string.call_unknown) }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, if (call.ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_phone_foreground)
            .setContentTitle(name)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (call.ringing) {
            b.setContentText(context.getString(R.string.call_incoming))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(open, true)
                .addAction(0, context.getString(R.string.call_decline), action(context, CallActionReceiver.DECLINE, 2))
                .addAction(0, context.getString(R.string.call_answer), action(context, CallActionReceiver.ANSWER, 3))
        } else {
            b.setContentText(context.getString(if (call.dialing) R.string.call_calling else R.string.call_ongoing))
                .setUsesChronometer(call.connectedAt > 0)
                .setWhen(if (call.connectedAt > 0) call.connectedAt else System.currentTimeMillis())
                .addAction(0, context.getString(R.string.call_end), action(context, CallActionReceiver.HANG_UP, 4))
        }
        runCatching { nm.notify(ID, b.build()) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    /** Missed calls: Android asks the default Phone app to show them. */
    fun missed(context: Context, count: Int, number: String?) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (count <= 0) {
            nm.cancel(ID_MISSED)
            return
        }
        channels(context)
        val who = number?.let { n -> runCatching { ContactsRepository(context).lookupNumber(n)?.first }.getOrNull() ?: com.wpreborn.core.PhoneFormat.br(n) }
        val open = PendingIntent.getActivity(
            context, 5, Intent(context, PhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_phone_foreground)
            .setContentTitle(context.resources.getQuantityString(R.plurals.call_missed_count, count, count))
            .setContentText(if (count == 1) who.orEmpty() else context.getString(R.string.app_phone))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .build()
        runCatching { nm.notify(ID_MISSED, n) }
    }
}

/** Buttons of the call notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallManager.primary ?: return
        when (intent.action) {
            ANSWER -> {
                CallManager.answer(call.call)
                runCatching {
                    context.startActivity(Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            }
            DECLINE -> CallManager.reject(call.call)
            HANG_UP -> CallManager.hangUp(call.call)
        }
    }

    companion object {
        const val ANSWER = "com.wpreborn.call.ANSWER"
        const val DECLINE = "com.wpreborn.call.DECLINE"
        const val HANG_UP = "com.wpreborn.call.HANG_UP"
    }
}

/** Android's "show missed calls" request to the default Phone app. */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        CallNotifications.missed(
            context,
            intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0),
            intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER),
        )
    }
}
