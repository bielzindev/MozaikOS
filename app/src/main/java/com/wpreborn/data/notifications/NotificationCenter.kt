package com.wpreborn.data.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.wpreborn.services.notifications.WpNotificationListener

/** A notification as shown in the Action Center and on live tiles. */
data class WpNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
    val largeIcon: Icon?,
    val picture: Bitmap?,
)

/**
 * In-memory mirror of the active notifications, fed by [WpNotificationListener] when the
 * user granted notification access. Used for Action Center, live tile counters and the
 * lock screen quick status.
 */
object NotificationCenter {
    private val _items = MutableStateFlow<List<WpNotification>>(emptyList())
    val items: StateFlow<List<WpNotification>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Unread counts per package (ongoing/foreground notifications excluded). */
    val counts: Map<String, Int> get() = _items.value.groupingBy { it.packageName }.eachCount()

    internal var listener: WpNotificationListener? = null

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _items.value = emptyList()
    }

    internal fun replaceAll(context: Context, sbns: Array<StatusBarNotification>?) {
        _items.value = sbns.orEmpty().mapNotNull { it.toWp(context) }.sortedByDescending { it.postTime }
    }

    fun dismiss(key: String) {
        listener?.cancelNotification(key)
        _items.value = _items.value.filterNot { it.key == key }
    }

    fun dismissPackage(packageName: String) {
        _items.value.filter { it.packageName == packageName && it.clearable }.forEach { dismiss(it.key) }
    }

    fun clearAll() {
        listener?.cancelAllNotifications()
        _items.value = _items.value.filterNot { it.clearable }
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun accessSettingsIntent(context: Context) =
        android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).also {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                it.action = Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
                it.putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, WpNotificationListener::class.java).flattenToString(),
                )
            }
        }

    private fun StatusBarNotification.toWp(context: Context): WpNotification? {
        val n = notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (isOngoing && n.category != Notification.CATEGORY_CALL) {
            // Media and foreground service notifications are not "new" items.
            if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
            if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return null
        }
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val label = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
        @Suppress("DEPRECATION")
        val picture = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        return WpNotification(
            key = key,
            packageName = packageName,
            appLabel = label,
            title = title,
            text = text,
            postTime = postTime,
            clearable = isClearable,
            contentIntent = n.contentIntent,
            largeIcon = n.getLargeIcon(),
            picture = picture,
        )
    }
}
