package com.wpreborn.services.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wpreborn.data.media.ExternalMediaWatcher
import com.wpreborn.data.notifications.NotificationCenter

/**
 * Mirrors the active notifications into [NotificationCenter]. Only runs when the user grants
 * notification access in Android settings. Being an enabled listener also lets us read the
 * active media sessions of other players for the Music live tile.
 */
class WpNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationCenter.listener = this
        NotificationCenter.setConnected(true)
        refresh()
        ExternalMediaWatcher.start(this)
    }

    override fun onListenerDisconnected() {
        NotificationCenter.listener = null
        NotificationCenter.setConnected(false)
        ExternalMediaWatcher.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = try {
            activeNotifications
        } catch (_: SecurityException) {
            null
        }
        NotificationCenter.replaceAll(this, active)
    }
}
