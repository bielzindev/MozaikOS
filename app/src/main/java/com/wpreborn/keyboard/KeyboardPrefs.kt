package com.wpreborn.keyboard

import android.content.Context
import java.util.Locale

/** Keyboard settings, shared by the IME service and its settings screen. */
class KeyboardPrefs(context: Context) {
    private val p = context.getSharedPreferences("zunex_keyboard", Context.MODE_PRIVATE)

    var languages: List<String>
        get() = p.getString("languages", null)?.split(',')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: defaultLanguages()
        set(v) = p.edit().putString("languages", v.joinToString(",")).apply()

    var current: String
        get() = p.getString("current", null)?.takeIf { it in languages } ?: languages.first()
        set(v) = p.edit().putString("current", v).apply()

    var suggestions: Boolean
        get() = p.getBoolean("suggestions", true)
        set(v) = p.edit().putBoolean("suggestions", v).apply()
    var autoCorrect: Boolean
        get() = p.getBoolean("autocorrect", true)
        set(v) = p.edit().putBoolean("autocorrect", v).apply()
    var autoCaps: Boolean
        get() = p.getBoolean("autocaps", true)
        set(v) = p.edit().putBoolean("autocaps", v).apply()
    var doubleSpacePeriod: Boolean
        get() = p.getBoolean("double_space", true)
        set(v) = p.edit().putBoolean("double_space", v).apply()
    var swipe: Boolean
        get() = p.getBoolean("swipe", true)
        set(v) = p.edit().putBoolean("swipe", v).apply()
    var sound: Boolean
        get() = p.getBoolean("sound", false)
        set(v) = p.edit().putBoolean("sound", v).apply()
    var vibrate: Boolean
        get() = p.getBoolean("vibrate", true)
        set(v) = p.edit().putBoolean("vibrate", v).apply()
    /** Key height: 0 small, 1 medium (default), 2 large. */
    var size: Int
        get() = p.getInt("size", 1)
        set(v) = p.edit().putInt("size", v).apply()
    var numberRow: Boolean
        get() = p.getBoolean("number_row", false)
        set(v) = p.edit().putBoolean("number_row", v).apply()

    private fun defaultLanguages(): List<String> {
        val sys = Locale.getDefault().language
        val known = KeyboardLayouts.languages.map { it.code }
        val first = if (sys in known) sys else "en"
        return listOf(first, "en").distinct()
    }
}
