package com.wpreborn.data.apps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Apps the user hid from Start and the app list (by [AppEntry.key]); undone in Settings. */
class HiddenApps private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(prefs.getStringSet("keys", emptySet()).orEmpty())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun hide(key: String) = save(_keys.value + key)
    fun show(key: String) = save(_keys.value - key)

    private fun save(s: Set<String>) {
        _keys.value = s
        prefs.edit().putStringSet("keys", s).apply()
    }

    companion object {
        @Volatile private var instance: HiddenApps? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: HiddenApps(context.applicationContext).also { instance = it } }
    }
}
