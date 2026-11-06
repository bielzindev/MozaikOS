package com.wpreborn.data.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The real icons of the Games section. They are the games' own art, so Mozaik doesn't carry
 * them: the phone fetches each one the first time it is shown (from the App Store's public search
 * service, and failing that from the game's Google Play page), keeps it in the cache folder and
 * reuses it from there, also offline. An installed game shows the icon it has on the phone.
 */
object GameIcons {
    private val memory = LruCache<String, Bitmap>(48)
    /** Games that could not be fetched in this run, so lists don't retry on every scroll. */
    private val failed = HashSet<String>()
    /** A few at a time: the list asks for many icons at once. */
    private val limit = Semaphore(4)

    suspend fun load(context: Context, g: GameApp): Bitmap? {
        memory.get(g.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(File(context.cacheDir, "game_icons").also { it.mkdirs() }, g.id + ".png")
            decode(file)?.let {
                memory.put(g.id, it)
                return@withContext it
            }
            if (g.id in failed) return@withContext null
            limit.withPermit {
                val bytes = runCatching { fromAppStore(g) }.getOrNull() ?: runCatching { fromPlay(g) }.getOrNull()
                if (bytes == null) {
                    synchronized(failed) { failed += g.id }
                    return@withPermit null
                }
                runCatching { file.writeBytes(bytes) }
                decode(file)?.also { memory.put(g.id, it) } ?: run {
                    runCatching { file.delete() }
                    synchronized(failed) { failed += g.id }
                    null
                }
            }
        }
    }

    private fun decode(file: File): Bitmap? =
        if (file.length() > 0) runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() else null

    private fun get(url: String, accept: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mozaik")
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("Accept-Language", "pt-BR,en;q=0.8")
            if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    }

    private fun firstWord(s: String) = s.lowercase().split(' ', ':', '-', '.', '!').firstOrNull { it.length > 1 }.orEmpty()

    /** The App Store's search: the artwork of the app whose name and maker match the game's. */
    private fun fromAppStore(g: GameApp): ByteArray? {
        val term = URLEncoder.encode(g.name, "UTF-8")
        val json = get("https://itunes.apple.com/search?term=$term&entity=software&limit=8&country=br", "application/json") ?: return null
        val results = JSONObject(String(json)).optJSONArray("results") ?: return null
        val name = firstWord(g.name)
        val maker = firstWord(g.developer)
        var best: String? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val track = r.optString("trackName").lowercase()
            val artist = (r.optString("artistName") + " " + r.optString("sellerName")).lowercase()
            val url = r.optString("artworkUrl512").ifEmpty { r.optString("artworkUrl100") }
            if (url.isEmpty() || name.isEmpty() || !track.contains(name)) continue
            // Only an app by the same maker counts (a copycat with the same name doesn't); if none, Google Play is tried.
            if (maker.isNotEmpty() && artist.contains(maker)) {
                best = url
                break
            }
        }
        return best?.let { get(it, "image/*") }
    }

    /** The game's Google Play page names its icon in the page's image tag. */
    private fun fromPlay(g: GameApp): ByteArray? {
        val page = get("https://play.google.com/store/apps/details?id=${g.id}&hl=pt_BR", "text/html") ?: return null
        val html = String(page)
        val m = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""").find(html)
            ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""").find(html)
        val url = m?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        // Google's image service takes the size in the address: ask for a 512 px square.
        val sized = if (url.contains("googleusercontent.com")) url.substringBefore("=") + "=s512" else url
        return get(sized, "image/*")
    }
}
