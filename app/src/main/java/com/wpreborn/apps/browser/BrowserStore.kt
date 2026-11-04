package com.wpreborn.apps.browser

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class WebEntry(val title: String, val url: String, val time: Long = System.currentTimeMillis(), val visits: Int = 1)

enum class SearchEngine(val label: String, val url: String) {
    Bing("Bing", "https://www.bing.com/search?q="),
    Google("Google", "https://www.google.com/search?q="),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q="),
}

/** Favourites, history (not for InPrivate tabs), open tabs and settings of the browser. */
class BrowserStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE)

    val favorites = mutableStateListOf<WebEntry>().apply { addAll(read("favorites")) }
    val history = mutableStateListOf<WebEntry>().apply { addAll(read("history")) }

    var engine: SearchEngine
        get() = runCatching { SearchEngine.valueOf(prefs.getString("engine", null) ?: "") }.getOrDefault(SearchEngine.Google)
        set(v) = prefs.edit().putString("engine", v.name).apply()

    var adBlock: Boolean
        get() = prefs.getBoolean("adblock", true)
        set(v) = prefs.edit().putBoolean("adblock", v).apply()

    /** Dark pages: every site in dark colours (the page is inverted, pictures kept). */
    var forceDark: Boolean
        get() = prefs.getBoolean("force_dark", false)
        set(v) = prefs.edit().putBoolean("force_dark", v).apply()

    /** Text size of pages, in percent. */
    var textZoom: Int
        get() = prefs.getInt("text_zoom", 100)
        set(v) = prefs.edit().putInt("text_zoom", v).apply()

    /** Text size of the reading view, in sp. */
    var readerSize: Int
        get() = prefs.getInt("reader_size", 19)
        set(v) = prefs.edit().putInt("reader_size", v).apply()

    var openTabs: List<String>
        get() = runCatching { JSONArray(prefs.getString("tabs", "[]")).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
        set(v) = prefs.edit().putString("tabs", JSONArray(v).toString()).apply()

    fun isFavorite(url: String) = favorites.any { it.url == url }

    fun toggleFavorite(title: String, url: String) {
        val i = favorites.indexOfFirst { it.url == url }
        if (i >= 0) favorites.removeAt(i) else favorites.add(0, WebEntry(title.ifBlank { url }, url))
        write("favorites", favorites)
    }

    fun removeFavorite(e: WebEntry) {
        favorites.remove(e)
        write("favorites", favorites)
    }

    fun visit(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val i = history.indexOfFirst { it.url == url }
        val visits = if (i >= 0) history[i].visits + 1 else 1
        if (i >= 0) history.removeAt(i)
        history.add(0, WebEntry(title.ifBlank { url }, url, System.currentTimeMillis(), visits))
        while (history.size > 500) history.removeAt(history.lastIndex)
        write("history", history)
    }

    fun removeHistory(e: WebEntry) {
        history.remove(e)
        write("history", history)
    }

    fun clearHistory() {
        history.clear()
        write("history", history)
    }

    /** "Frequent" sites for the new-tab page: most visited, one per site. */
    fun frequent(n: Int = 8): List<WebEntry> =
        history.groupBy { android.net.Uri.parse(it.url).host ?: it.url }
            .map { (_, l) -> l.maxBy { it.visits }.copy(visits = l.sumOf { it.visits }) }
            .sortedByDescending { it.visits }
            .take(n)

    private fun read(key: String): List<WebEntry> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            WebEntry(o.optString("t"), o.getString("u"), o.optLong("ti"), o.optInt("v", 1))
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, list: List<WebEntry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url).put("ti", it.time).put("v", it.visits)) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    companion object {
        @Volatile private var instance: BrowserStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: BrowserStore(context.applicationContext).also { instance = it } }
    }
}
