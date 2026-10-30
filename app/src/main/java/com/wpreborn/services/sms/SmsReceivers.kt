package com.wpreborn.services.sms

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wpreborn.R
import com.wpreborn.apps.messages.MessagingActivity
import com.wpreborn.data.sms.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming SMS when Windows Phone Reborn is the default SMS app: stores the
 * message in the provider (the default app is responsible for that) and posts a toast-like
 * notification.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val address = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.DATE_SENT, messages.first().timestampMillis)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                val repo = SmsRepository(context)
                SmsNotifications.show(context, address, repo.displayNameFor(address), body, repo.threadIdFor(address))
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

/** Required for the default SMS role. MMS download is not implemented (documented limitation). */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SmsNotifications.show(context, "", context.getString(R.string.messages_mms_received), context.getString(R.string.messages_mms_unsupported), -1)
    }
}

/** Updates the status of a sent message (only meaningful when we are the default app). */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return
        val ok = resultCode == Activity.RESULT_OK
        if (!ok || intent.getBooleanExtra(EXTRA_LAST, true)) {
            try {
                val v = ContentValues().apply {
                    put(Telephony.Sms.TYPE, if (ok) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
                }
                context.contentResolver.update(uri, v, null, null)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.wpreborn.SMS_SENT"
        const val EXTRA_URI = "uri"
        const val EXTRA_LAST = "last"
    }
}

/** "Respond via message" from the incoming call screen. Required for the default SMS role. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!address.isNullOrBlank() && !text.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                SmsRepository(this@HeadlessSmsSendService).send(address, text)
                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}

object SmsNotifications {
    private const val CHANNEL = "messages"

    fun show(context: Context, address: String, title: String, body: String, threadId: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.app_messaging), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = Intent(context, MessagingActivity::class.java)
            .putExtra(MessagingActivity.EXTRA_THREAD_ID, threadId)
            .putExtra(MessagingActivity.EXTRA_ADDRESS, address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, threadId.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(context).notify(threadId.toInt(), n)
            } catch (_: SecurityException) {
            }
        }
    }

    fun cancel(context: Context, threadId: Long) {
        NotificationManagerCompat.from(context).cancel(threadId.toInt())
    }
}
