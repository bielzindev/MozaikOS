package com.wpreborn.data.media

import android.content.Context

/** How often each song was played, for the "your mix" and "most played" mixes. */
class PlayStats private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("play_stats", Context.MODE_PRIVATE)

    fun record(songId: Long) {
        val key = songId.toString()
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun counts(): Map<Long, Int> = prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? Int)?.let { id to it } } }.toMap()

    companion object {
        @Volatile
        private var instance: PlayStats? = null

        fun get(context: Context): PlayStats =
            instance ?: synchronized(this) { instance ?: PlayStats(context.applicationContext).also { instance = it } }
    }
}
