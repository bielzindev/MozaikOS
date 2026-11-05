package com.wpreborn.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Your rating (1 to 5 stars) and review of an app of the family, kept on the phone. */
data class StoreReview(val stars: Int, val text: String, val at: Long)

class StoreReviews private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("store_reviews", Context.MODE_PRIVATE)
    private val _reviews = MutableStateFlow(load())
    val reviews: StateFlow<Map<String, StoreReview>> = _reviews.asStateFlow()

    fun save(id: String, stars: Int, text: String) {
        _reviews.value = _reviews.value + (id to StoreReview(stars.coerceIn(1, 5), text.trim(), System.currentTimeMillis()))
        persist()
    }

    fun delete(id: String) {
        _reviews.value = _reviews.value - id
        persist()
    }

    private fun load(): Map<String, StoreReview> {
        val o = runCatching { JSONObject(prefs.getString("all", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return o.keys().asSequence().mapNotNull { id ->
            o.optJSONObject(id)?.let { e -> id to StoreReview(e.optInt("stars"), e.optString("text"), e.optLong("at")) }
        }.toMap()
    }

    private fun persist() {
        val o = JSONObject()
        _reviews.value.forEach { (id, r) -> o.put(id, JSONObject().put("stars", r.stars).put("text", r.text).put("at", r.at)) }
        prefs.edit().putString("all", o.toString()).apply()
    }

    companion object {
        @Volatile
        private var instance: StoreReviews? = null

        fun get(context: Context): StoreReviews =
            instance ?: synchronized(this) { instance ?: StoreReviews(context.applicationContext).also { instance = it } }
    }
}
