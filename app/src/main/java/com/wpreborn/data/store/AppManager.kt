package com.wpreborn.data.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.wpreborn.data.apps.AppRepository
import com.wpreborn.data.apps.BuiltInApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Where an app of the family stands on this phone. */
data class AppState(val installed: Boolean, val enabled: Boolean, val versionCode: Int) {
    val active get() = installed && enabled
}

/**
 * The single place that decides which ZUNEX apps exist on the phone. Installing, enabling,
 * disabling and uninstalling switch the app's launcher component on or off in Android (so a
 * turned-off app really cannot be started), apply the uninstall data policy, track versions
 * for updates, follow dependencies, and refresh Start and the app list.
 */
class AppManager private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("zunex_store", Context.MODE_PRIVATE)
    private val _states = MutableStateFlow(load())
    val states: StateFlow<Map<String, AppState>> = _states.asStateFlow()

    init {
        // Make Android match what we recorded (e.g. after the app was updated).
        _states.value.forEach { (id, s) -> BuiltInApp.byId(id)?.let { setComponent(it, s.active) } }
    }

    fun state(app: BuiltInApp): AppState = _states.value[app.id] ?: AppState(true, true, ZunexCatalog.of(app)?.versionCode ?: 1)

    fun isActive(app: BuiltInApp) = state(app).active

    fun hasUpdate(app: CatalogApp) = state(app.app).let { it.installed && it.versionCode < app.versionCode }

    /** Installed, active apps that need [app] and would stop working fully without it. */
    fun dependents(app: BuiltInApp): List<CatalogApp> = ZunexCatalog.apps.filter { app in it.dependsOn && isActive(it.app) }

    /** Installs [app] (and what it depends on) at the catalogue version. Returns what was installed. */
    fun install(app: BuiltInApp): List<BuiltInApp> {
        val catalog = ZunexCatalog.of(app) ?: return emptyList()
        val installed = ArrayList<BuiltInApp>()
        catalog.dependsOn.filterNot { isActive(it) }.forEach { installed += install(it) }
        put(app, AppState(installed = true, enabled = true, versionCode = catalog.versionCode))
        installed += app
        return installed
    }

    fun enable(app: BuiltInApp) {
        val s = state(app)
        if (!s.installed) {
            install(app)
            return
        }
        put(app, s.copy(enabled = true))
    }

    fun disable(app: BuiltInApp) {
        val catalog = ZunexCatalog.of(app) ?: return
        if (catalog.required) return
        if (app == BuiltInApp.Music) com.wpreborn.services.music.MusicController.get(context).stop()
        put(app, state(app).copy(enabled = false))
    }

    /** Removes the app and, per its policy, its own data (never the user's photos, songs or messages). */
    fun uninstall(app: BuiltInApp) {
        val catalog = ZunexCatalog.of(app) ?: return
        if (catalog.required) return
        catalog.clearData(context)
        put(app, AppState(installed = false, enabled = false, versionCode = 0))
    }

    fun update(app: BuiltInApp) {
        val catalog = ZunexCatalog.of(app) ?: return
        val s = state(app)
        if (!s.installed) return
        put(app, s.copy(versionCode = catalog.versionCode))
    }

    fun updateAll() = ZunexCatalog.apps.filter { hasUpdate(it) }.forEach { update(it.app) }

    private fun put(app: BuiltInApp, s: AppState) {
        _states.value = _states.value + (app.id to s)
        save()
        setComponent(app, s.active)
        // Start and the app list follow at once.
        AppRepository.get(context).reload()
    }

    private fun setComponent(app: BuiltInApp, on: Boolean) {
        val cn = ComponentName(context, app.activity)
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            if (context.packageManager.getComponentEnabledSetting(cn) != wanted) {
                context.packageManager.setComponentEnabledSetting(cn, wanted, PackageManager.DONT_KILL_APP)
            }
        }
    }

    private fun load(): Map<String, AppState> {
        val o = runCatching { JSONObject(prefs.getString("apps", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return ZunexCatalog.apps.associate { c ->
            val e = o.optJSONObject(c.id)
            // New apps of the family arrive installed at their current version.
            c.id to if (e == null) AppState(true, true, c.versionCode) else AppState(e.optBoolean("installed", true), e.optBoolean("enabled", true), e.optInt("version", c.versionCode))
        }
    }

    private fun save() {
        val o = JSONObject()
        _states.value.forEach { (id, s) -> o.put(id, JSONObject().put("installed", s.installed).put("enabled", s.enabled).put("version", s.versionCode)) }
        prefs.edit().putString("apps", o.toString()).apply()
    }

    companion object {
        @Volatile
        private var instance: AppManager? = null

        fun get(context: Context): AppManager =
            instance ?: synchronized(this) { instance ?: AppManager(context.applicationContext).also { instance = it } }
    }
}
