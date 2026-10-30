package com.wpreborn.data.calls

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

enum class CallType { Incoming, Outgoing, Missed, Rejected, Other }

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val photoUri: Uri?,
    val type: CallType,
    val date: Long,
    val durationSec: Long,
    val isNew: Boolean,
)

/** Call history from the Android call log. Requires READ_CALL_LOG. */
class CallLogRepository(private val context: Context) {

    suspend fun recent(limit: Int = 200): List<CallEntry> = withContext(Dispatchers.IO) {
        val list = ArrayList<CallEntry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build(),
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.CACHED_PHOTO_URI,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.NEW,
                ),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    list += CallEntry(
                        id = c.getLong(0),
                        number = c.getString(1) ?: "",
                        name = c.getString(2)?.takeIf { it.isNotBlank() },
                        photoUri = c.getString(3)?.let(Uri::parse),
                        type = when (c.getInt(4)) {
                            CallLog.Calls.INCOMING_TYPE -> CallType.Incoming
                            CallLog.Calls.OUTGOING_TYPE -> CallType.Outgoing
                            CallLog.Calls.MISSED_TYPE -> CallType.Missed
                            CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> CallType.Rejected
                            else -> CallType.Other
                        },
                        date = c.getLong(5),
                        durationSec = c.getLong(6),
                        isNew = c.getInt(7) == 1,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        list
    }

    /** Number of missed calls the user has not looked at yet (Phone live tile counter). */
    suspend fun missedCount(): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                null,
            )?.use { it.count } ?: 0
        } catch (_: SecurityException) {
            0
        }
    }

    /** Clears the "new" flag of missed calls once the history was seen. Needs WRITE_CALL_LOG. */
    suspend fun markMissedSeen() = withContext(Dispatchers.IO) {
        try {
            val v = ContentValues().apply { put(CallLog.Calls.NEW, 0) }
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI, v,
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
            )
        } catch (_: Exception) {
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} = ?", arrayOf(id.toString()))
        } catch (_: Exception) {
        }
    }

    fun changes(): Flow<Unit> = observe(context, CallLog.Calls.CONTENT_URI)
}

/** Emits once immediately and again whenever the content at [uri] changes. */
fun observe(context: Context, uri: Uri): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    try {
        context.contentResolver.registerContentObserver(uri, true, observer)
    } catch (_: SecurityException) {
    }
    trySend(Unit)
    awaitClose { context.contentResolver.unregisterContentObserver(observer) }
}
