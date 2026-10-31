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
    /** ApplicationInfo.category, used by the Metro icon pack as a last resort. */
    val category: Int = -1,
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
            // Only the ZUNEX apps that are installed and turned on in the Store.
            val manager = com.wpreborn.data.store.AppManager.get(context)
            val builtIns = BuiltInApp.entries.filter { manager.isActive(it) }.map { b ->
                AppEntry("builtin:${b.id}", context.getString(b.label), context.packageName, ComponentName(context, b.activity), null, b)
            }
            _apps.value = (android + builtIns).sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }

    private fun LauncherActivityInfo.toEntry(user: UserHandle): AppEntry {
        val cn = componentName
        val suffix = if (user == Process.myUserHandle()) "" else "@${user.hashCode()}"
        return AppEntry("${cn.flattenToString()}$suffix", label.toString(), cn.packageName, cn, user, installedAt = firstInstallTime, category = applicationInfo.category)
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

    /** An app's own artwork prepared for a Metro tile: the logo without its mask, plus its brand colour. */
    data class BrandIcon(val logo: Bitmap, val color: Int?)

    private val brandCache = object : LruCache<String, BrandIcon>(200) {}

    fun cachedBrandIcon(entry: AppEntry, sizePx: Int): BrandIcon? = brandCache.get("${entry.key}@$sizePx")

    /**
     * Turns an Android icon into Metro artwork. Adaptive icons give their foreground layer
     * (the logo, in its own colours, without the round/squircle mask) and the colour of their
     * background layer, which becomes the tile colour like Windows Phone app tiles. Legacy
     * icons are used as they are. Near-white backgrounds yield no colour (the accent is used).
     */
    suspend fun brandIcon(entry: AppEntry, sizePx: Int): BrandIcon? {
        if (entry.builtIn != null) return null
        val cacheKey = "${entry.key}@$sizePx"
        brandCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val info = launcherApps.resolveActivity(Intent().setComponent(entry.component), entry.user ?: Process.myUserHandle())
                val drawable = info?.getIcon(context.resources.displayMetrics.densityDpi) ?: return@withContext null
                val result = if (drawable is android.graphics.drawable.AdaptiveIconDrawable && drawable.foreground != null) {
                    val logo = renderSafeZone(drawable.foreground, sizePx)
                    BrandIcon(logo, drawable.background?.let { averageColor(it) }?.takeIf { !isNearWhite(it) })
                } else {
                    BrandIcon(drawable.toBitmap(sizePx, sizePx), null)
                }
                brandCache.put(cacheKey, result)
                result
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Draws the inner 72/108 of an adaptive layer (where the logo lives) at [sizePx]. */
    private fun renderSafeZone(layer: android.graphics.drawable.Drawable, sizePx: Int): Bitmap {
        val full = (sizePx * 108f / 72f).toInt()
        val inset = (full - sizePx) / 2
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.translate(-inset.toFloat(), -inset.toFloat())
        layer.setBounds(0, 0, full, full)
        layer.draw(canvas)
        return bmp
    }

    private fun averageColor(layer: android.graphics.drawable.Drawable): Int? {
        val bmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        layer.setBounds(0, 0, 12, 12)
        layer.draw(android.graphics.Canvas(bmp))
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        val pixels = IntArray(144)
        bmp.getPixels(pixels, 0, 12, 0, 0, 12, 12)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        if (n == 0) return null
        return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun isNearWhite(c: Int): Boolean {
        val r = android.graphics.Color.red(c); val g = android.graphics.Color.green(c); val b = android.graphics.Color.blue(c)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 225
    }

    /**
     * System apps that duplicate a ZUNEX app (dialer, messaging, music, calendar, contacts,
     * gallery, Android Settings). They are hidden from the app list unless the user shows them.
     */
    /**
     * System apps that a turned-on ZUNEX app replaces (dialer, messages, music, calendar,
     * contacts, gallery, calculator, camera, settings). Turning a ZUNEX app off in the Store
     * brings its system counterpart back to the app list.
     */
    fun duplicatePackages(): Set<String> {
        val pm = context.packageManager
        val manager = com.wpreborn.data.store.AppManager.get(context)
        val result = HashSet<String>()
        fun add(app: BuiltInApp, intent: Intent) {
            if (!manager.isActive(app)) return
            try {
                pm.queryIntentActivities(intent, 0).forEach { ri ->
                    val ai = ri.activityInfo.applicationInfo
                    if (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 && ai.packageName != context.packageName) result += ai.packageName
                }
            } catch (_: Exception) {
            }
        }
        fun main(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
        add(BuiltInApp.Phone, Intent(Intent.ACTION_DIAL))
        add(BuiltInApp.Messaging, main(Intent.CATEGORY_APP_MESSAGING))
        add(BuiltInApp.Music, main(Intent.CATEGORY_APP_MUSIC))
        add(BuiltInApp.Calendar, main(Intent.CATEGORY_APP_CALENDAR))
        add(BuiltInApp.People, main(Intent.CATEGORY_APP_CONTACTS))
        add(BuiltInApp.Photos, main(Intent.CATEGORY_APP_GALLERY))
        add(BuiltInApp.Calculator, main(Intent.CATEGORY_APP_CALCULATOR))
        add(BuiltInApp.Camera, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        add(BuiltInApp.Settings, Intent(android.provider.Settings.ACTION_SETTINGS))
        return result
    }

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
