package com.wpreborn.data.apps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Which artwork an app shows: the global choice in Personalize, Metro, or the Android icon. */
enum class IconPack { Default, Metro, Original }

/** The icon's colour: as designed, white, black, or a colour of the user's choice. */
enum class IconTint { Default, White, Black, Custom }

data class IconCustom(val pack: IconPack = IconPack.Default, val tint: IconTint = IconTint.Default, val color: Int = 0xFFFFFFFF.toInt()) {
    val isDefault get() = pack == IconPack.Default && tint == IconTint.Default
}

/**
 * Per-app icon settings, stored by the app's permanent key ([AppEntry.key]: its component and
 * user, or builtin:id), never by tile position, so they survive moves, resizes and restarts.
 */
class IconPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("icon_prefs", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, IconCustom>> = _all.asStateFlow()

    fun of(appKey: String) = _all.value[appKey] ?: IconCustom()

    fun set(appKey: String, custom: IconCustom) {
        _all.value = if (custom.isDefault) _all.value - appKey else _all.value + (appKey to custom)
        val o = JSONObject()
        _all.value.forEach { (k, v) -> o.put(k, JSONObject().put("pack", v.pack.name).put("tint", v.tint.name).put("color", v.color)) }
        prefs.edit().putString("icons", o.toString()).apply()
    }

    private fun load(): Map<String, IconCustom> = runCatching {
        val o = JSONObject(prefs.getString("icons", "{}") ?: "{}")
        o.keys().asSequence().associateWith { k ->
            val v = o.getJSONObject(k)
            IconCustom(
                runCatching { IconPack.valueOf(v.optString("pack")) }.getOrDefault(IconPack.Default),
                runCatching { IconTint.valueOf(v.optString("tint")) }.getOrDefault(IconTint.Default),
                v.optInt("color", 0xFFFFFFFF.toInt()),
            )
        }
    }.getOrDefault(emptyMap())

    companion object {
        @Volatile
        private var instance: IconPrefs? = null

        fun get(context: Context): IconPrefs =
            instance ?: synchronized(this) { instance ?: IconPrefs(context.applicationContext).also { instance = it } }
    }
}
