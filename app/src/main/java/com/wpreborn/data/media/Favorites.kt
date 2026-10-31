package com.wpreborn.data.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Songs marked with the heart in the player; they also form the "Favorites" mix. */
class Favorites private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("music_favorites", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet("ids", emptySet()).orEmpty().toSet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun toggle(mediaId: String) {
        val next = _ids.value.toMutableSet().apply { if (!add(mediaId)) remove(mediaId) }
        _ids.value = next
        prefs.edit().putStringSet("ids", next).apply()
    }

    fun remove(mediaId: String) {
        if (mediaId !in _ids.value) return
        toggle(mediaId)
    }

    companion object {
        @Volatile
        private var instance: Favorites? = null

        fun get(context: Context): Favorites =
            instance ?: synchronized(this) { instance ?: Favorites(context.applicationContext).also { instance = it } }
    }
}
