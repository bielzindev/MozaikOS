package com.wpreborn.data.sms

import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import com.wpreborn.data.calls.observe
import com.wpreborn.data.contacts.ContactsRepository
import com.wpreborn.services.sms.SmsSentReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class SmsThread(
    val threadId: Long,
    val address: String,
    val name: String?,
    val photoUri: Uri?,
    val snippet: String,
    val date: Long,
    val unread: Int,
    val lastIsOutgoing: Boolean,
) {
    val displayName get() = name ?: address
}

enum class SmsStatus { Received, Sent, Sending, Failed }

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val status: SmsStatus,
    val read: Boolean,
)

/**
 * SMS through the Telephony provider. Reading needs READ_SMS; writing to the provider
 * (marking as read, deleting, storing received/sent messages) is only possible while this
 * app holds the default SMS role, exactly like any other Android messaging app.
 */
class SmsRepository(private val context: Context) {

    private val contacts = ContactsRepository(context)
    private val names = ConcurrentHashMap<String, Pair<String, Uri?>>()

    fun isDefaultSmsApp(): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun requestDefaultIntent(): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        return if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            null
        }
    }

    fun changes(): Flow<Unit> = observe(context, Telephony.Sms.CONTENT_URI)

    private fun contactFor(address: String): Pair<String, Uri?>? {
        names[address]?.let { return it }
        val found = contacts.lookupNumber(address) ?: return null
        names[address] = found
        return found
    }

    suspend fun threads(): List<SmsThread> = withContext(Dispatchers.IO) {
        val latest = LinkedHashMap<Long, SmsThread>()
        val unread = HashMap<Long, Int>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE,
                ),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val thread = c.getLong(0)
                    val type = c.getInt(5)
                    if (c.getInt(4) == 0 && type == Telephony.Sms.MESSAGE_TYPE_INBOX) unread[thread] = (unread[thread] ?: 0) + 1
                    if (!latest.containsKey(thread)) {
                        val address = c.getString(1) ?: ""
                        latest[thread] = SmsThread(
                            threadId = thread,
                            address = address,
                            name = null,
                            photoUri = null,
                            snippet = c.getString(2) ?: "",
                            date = c.getLong(3),
                            unread = 0,
                            lastIsOutgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
        }
        latest.values.map { t ->
            val contact = contactFor(t.address)
            t.copy(name = contact?.first, photoUri = contact?.second, unread = unread[t.threadId] ?: 0)
        }
    }

    suspend fun messages(threadId: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        val list = ArrayList<SmsMessage>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
                ),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(5)
                    list += SmsMessage(
                        id = c.getLong(0),
                        threadId = c.getLong(1),
                        address = c.getString(2) ?: "",
                        body = c.getString(3) ?: "",
                        date = c.getLong(4),
                        outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                        status = when (type) {
                            Telephony.Sms.MESSAGE_TYPE_INBOX -> SmsStatus.Received
                            Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsStatus.Failed
                            Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> SmsStatus.Sending
                            else -> SmsStatus.Sent
                        },
                        read = c.getInt(6) == 1,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        list
    }

    suspend fun unreadCount(): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.READ} = 0", null, null,
            )?.use { it.count } ?: 0
        } catch (_: SecurityException) {
            0
        }
    }

    fun threadIdFor(address: String): Long = try {
        Telephony.Threads.getOrCreateThreadId(context, address)
    } catch (_: Exception) {
        -1L
    }

    fun displayNameFor(address: String): String = contactFor(address)?.first ?: address

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        if (!isDefaultSmsApp()) return@withContext
        try {
            val v = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, v,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()),
            )
        } catch (_: Exception) {
        }
    }

    suspend fun deleteThread(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isDefaultSmsApp()) return@withContext false
        try {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteMessage(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isDefaultSmsApp()) return@withContext false
        try {
            context.contentResolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends a text. When we are the default SMS app we must record it in the provider
     * ourselves; otherwise the framework writes it to the Sent box for us.
     */
    suspend fun send(address: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val manager: SmsManager = context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            var rowUri: Uri? = null
            if (isDefaultSmsApp()) {
                val v = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                }
                rowUri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, v)
            }
            val parts = manager.divideMessage(body)
            val sentIntents = ArrayList<PendingIntent>()
            parts.forEachIndexed { i, _ ->
                val intent = Intent(context, SmsSentReceiver::class.java)
                    .setAction(SmsSentReceiver.ACTION_SENT)
                    .putExtra(SmsSentReceiver.EXTRA_URI, rowUri?.toString())
                    .putExtra(SmsSentReceiver.EXTRA_LAST, i == parts.size - 1)
                sentIntents += PendingIntent.getBroadcast(
                    context, (System.nanoTime() % Int.MAX_VALUE).toInt() + i, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
            } else {
                manager.sendTextMessage(address, null, body, sentIntents.firstOrNull(), null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
