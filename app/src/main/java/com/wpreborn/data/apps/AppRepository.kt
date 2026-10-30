package com.wpreborn.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer

/** An installed Android app (or one of our built-in apps) that can be listed and launched. */
data class AppEntry(
    /** "pkg/activity" for Android apps, "builtin:<id>" for ours. */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName?,
    val user: UserHandle?,
    val builtIn: BuiltInApp? = null,
    /** First install time, used for the "new" section of the app list. */
    val installedAt: Long = 0,
) {
    /** Letter group for the alphabetical list: a..z, or "#" for digits and symbols. */
    val group: String
        get() {
            val first = Normalizer.normalize(label.trim().take(1), Normalizer.Form.NFD)
                .firstOrNull { it.isLetterOrDigit() }?.lowercaseChar() ?: '#'
            return if (first in 'a'..'z') first.toString() else "#"
        }
}

/**
 * Keeps the list of launchable apps up to date through LauncherApps callbacks (installs,
 * removals, updates, work profile changes).
 */
class AppRepository private constructor(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val iconCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            iconCache.evictAll()
            reload()
        }
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        scope.launch {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            val profiles = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
            val android = profiles.flatMap { user ->
                launcherApps.getActivityList(null, user)
                    .filter { it.applicationInfo.packageName != context.packageName }
                    .map { it.toEntry(user) }
            }
            val builtIns = BuiltInApp.entries.map { b ->
                AppEntry("builtin:${b.id}", context.getString(b.label), context.packageName, ComponentName(context, b.activity), null, b)
            }
            _apps.value = (android + builtIns).sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }

    private fun LauncherActivityInfo.toEntry(user: UserHandle): AppEntry {
        val cn = componentName
        val suffix = if (user == Process.myUserHandle()) "" else "@${user.hashCode()}"
        return AppEntry("${cn.flattenToString()}$suffix", label.toString(), cn.packageName, cn, user, installedAt = firstInstallTime)
    }

    fun find(key: String): AppEntry? = _apps.value.firstOrNull { it.key == key }

    suspend fun icon(entry: AppEntry, sizePx: Int): Bitmap? {
        if (entry.builtIn != null) return null
        val cacheKey = "${entry.key}@$sizePx"
        iconCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val info = launcherApps.resolveActivity(Intent().setComponent(entry.component), entry.user ?: Process.myUserHandle())
                val drawable = info?.getIcon(context.resources.displayMetrics.densityDpi) ?: return@withContext null
                drawable.toBitmap(sizePx, sizePx).also { iconCache.put(cacheKey, it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun cachedIcon(entry: AppEntry, sizePx: Int): Bitmap? = iconCache.get("${entry.key}@$sizePx")

    fun launch(entry: AppEntry, bounds: Rect? = null, options: android.os.Bundle? = null) {
        val cn = entry.component ?: return
        if (entry.builtIn != null) {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).setComponent(cn).addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                options,
            )
            return
        }
        try {
            launcherApps.startMainActivity(cn, entry.user ?: Process.myUserHandle(), bounds, options)
        } catch (_: Exception) {
            context.packageManager.getLaunchIntentForPackage(entry.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
            }
        }
    }

    fun openAppInfo(entry: AppEntry) {
        val cn = entry.component ?: return
        try {
            launcherApps.startAppDetailsActivity(cn, entry.user ?: Process.myUserHandle(), null, null)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun uninstall(entry: AppEntry) {
        @Suppress("DEPRECATION")
        context.startActivity(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${entry.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun isSystemApp(entry: AppEntry): Boolean = try {
        val ai = context.packageManager.getApplicationInfo(entry.packageName, 0)
        ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
    } catch (_: Exception) {
        true
    }

    companion object {
        @Volatile
        private var instance: AppRepository? = null

        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }
    }
}
