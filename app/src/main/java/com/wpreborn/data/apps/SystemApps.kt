package com.wpreborn.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.wpreborn.data.prefs.SettingsRepository

/**
 * The phone's own Phone, Contacts and Calendar apps. With "use the phone's apps" on (off by
 * default: ZUNEX apps come first) the ZUNEX tiles keep their live content but open these, so calls, contacts and
 * events are handled by the apps the user already trusts.
 */
object SystemApps {

    enum class Kind { Dialer, Contacts, Calendar }

    /** Extra that opens the ZUNEX app itself even when the phone's apps are preferred. */
    const val EXTRA_OWN = "zunex_own"

    /** Whether [activity] should hand over to the phone's app. */
    fun preferred(activity: android.app.Activity) =
        SettingsRepository.get(activity).cached.systemApps && activity.intent?.getBooleanExtra(EXTRA_OWN, false) != true

    /** Opens the system app for [kind], forwarding [data] (a tel: uri) to the dialer. Returns false if none exists. */
    fun open(context: Context, kind: Kind, data: android.net.Uri? = null): Boolean {
        val intent = when (kind) {
            Kind.Dialer -> Intent(Intent.ACTION_DIAL, data)
            Kind.Contacts -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
            Kind.Calendar -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        }
        val target = resolve(context, intent) ?: return false
        return try {
            context.startActivity(intent.setPackage(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** The user's default for [intent] when it is not ZUNEX, else the first other app that handles it. */
    private fun resolve(context: Context, intent: Intent): String? {
        val pm = context.packageManager
        val own = context.packageName
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != own && it != "android" }
            ?.let { return it }
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != own }
    }
}
