package com.wpreborn.data.news

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** Sections of the News app, like the categories of Bing News on Windows Phone 8.1. */
enum class NewsCategory(val id: String) { Top("top"), Brasil("brasil"), Economia("economia"), Mundo("mundo"), Esportes("esportes"), Tecnologia("tecnologia"), Entretenimento("entretenimento") }

/** A Brazilian news source (public RSS feeds published by the outlets themselves). */
data class NewsSource(val id: String, val name: String, val url: String, val category: NewsCategory)

data class Article(
    val title: String,
    val link: String,
    val summary: String,
    val image: String?,
    val source: String,
    val sourceId: String,
    val time: Long,
    val category: NewsCategory,
)

/** Full text of an article for the reader page. */
data class ArticleBody(val image: String?, val paragraphs: List<String>)

object NewsSources {
    val all = listOf(
        NewsSource("g1", "g1", "https://g1.globo.com/rss/g1/", NewsCategory.Top),
        NewsSource("folha", "Folha de S.Paulo", "https://feeds.folha.uol.com.br/emcimadahora/rss091.xml", NewsCategory.Top),
        NewsSource("cnn", "CNN Brasil", "https://www.cnnbrasil.com.br/feed/", NewsCategory.Top),
        NewsSource("bbc", "BBC News Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml", NewsCategory.Top),
        NewsSource("agencia", "Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml", NewsCategory.Brasil),
        NewsSource("g1-politica", "g1 Política", "https://g1.globo.com/rss/g1/politica/", NewsCategory.Brasil),
        NewsSource("poder360", "Poder360", "https://www.poder360.com.br/feed/", NewsCategory.Brasil),
        NewsSource("folha-poder", "Folha Poder", "https://feeds.folha.uol.com.br/poder/rss091.xml", NewsCategory.Brasil),
        NewsSource("g1-economia", "g1 Economia", "https://g1.globo.com/rss/g1/economia/", NewsCategory.Economia),
        NewsSource("folha-mercado", "Folha Mercado", "https://feeds.folha.uol.com.br/mercado/rss091.xml", NewsCategory.Economia),
        NewsSource("infomoney", "InfoMoney", "https://www.infomoney.com.br/feed/", NewsCategory.Economia),
        NewsSource("g1-mundo", "g1 Mundo", "https://g1.globo.com/rss/g1/mundo/", NewsCategory.Mundo),
        NewsSource("folha-mundo", "Folha Mundo", "https://feeds.folha.uol.com.br/mundo/rss091.xml", NewsCategory.Mundo),
        NewsSource("ge", "ge", "https://ge.globo.com/rss/ge/", NewsCategory.Esportes),
        NewsSource("folha-esporte", "Folha Esporte", "https://feeds.folha.uol.com.br/esporte/rss091.xml", NewsCategory.Esportes),
        NewsSource("g1-tec", "g1 Tecnologia", "https://g1.globo.com/rss/g1/tecnologia/", NewsCategory.Tecnologia),
        NewsSource("tecmundo", "Tecmundo", "https://rss.tecmundo.com.br/feed", NewsCategory.Tecnologia),
        NewsSource("olhar", "Olhar Digital", "https://olhardigital.com.br/feed/", NewsCategory.Tecnologia),
        NewsSource("g1-pop", "g1 Pop & Arte", "https://g1.globo.com/rss/g1/pop-arte/", NewsCategory.Entretenimento),
        NewsSource("folha-ilustrada", "Folha Ilustrada", "https://feeds.folha.uol.com.br/ilustrada/rss091.xml", NewsCategory.Entretenimento),
    )
}

class NewsRepository private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("news", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "news.json")
    private val mutex = Mutex()

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _disabled = MutableStateFlow(prefs.getStringSet("disabled", emptySet()).orEmpty())
    val disabled: StateFlow<Set<String>> = _disabled.asStateFlow()
    private var loadedCache = false

    fun setEnabled(id: String, enabled: Boolean) {
        val s = if (enabled) _disabled.value - id else _disabled.value + id
        _disabled.value = s
        prefs.edit().putStringSet("disabled", s).apply()
        _articles.value = _articles.value.filter { it.sourceId !in s }
    }

    /** Cached news first, then fresh ones when older than [maxAgeMs] (or forced). */
    suspend fun refresh(force: Boolean = false, maxAgeMs: Long = 20 * 60_000L) = mutex.withLock {
        if (!loadedCache) {
            loadedCache = true
            withContext(Dispatchers.IO) { readCache() }?.let { if (_articles.value.isEmpty()) _articles.value = it.filter { a -> a.sourceId !in _disabled.value } }
        }
        val age = System.currentTimeMillis() - prefs.getLong("updated", 0)
        if (!force && age < maxAgeMs && _articles.value.isNotEmpty()) return@withLock
        _loading.value = true
        try {
            val sources = NewsSources.all.filter { it.id !in _disabled.value }
            val fresh = withContext(Dispatchers.IO) {
                coroutineScope { sources.map { s -> async { runCatching { fetch(s) }.getOrDefault(emptyList()) } }.awaitAll() }
            }.flatten()
            if (fresh.isNotEmpty()) {
                val list = dedupe(fresh).sortedByDescending { it.time }
                _articles.value = list
                prefs.edit().putLong("updated", System.currentTimeMillis()).apply()
                withContext(Dispatchers.IO) { writeCache(list) }
            }
        } finally {
            _loading.value = false
        }
    }

    fun inCategory(list: List<Article>, c: NewsCategory): List<Article> =
        if (c == NewsCategory.Top) {
            // Top stories: the latest of every outlet, mixed, pictures first.
            list.filter { it.category == NewsCategory.Top }.ifEmpty { list }
                .groupBy { it.sourceId }.values.flatMap { it.take(8) }
                .sortedByDescending { it.time }
        } else {
            list.filter { it.category == c }
        }

    private fun dedupe(list: List<Article>): List<Article> {
        val seen = HashSet<String>()
        return list.filter { seen.add(it.title.lowercase().filter { c -> c.isLetterOrDigit() }.take(60)) }
    }

    private fun readCache(): List<Article>? = runCatching {
        val arr = JSONArray(cacheFile.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Article(
                o.getString("t"), o.getString("l"), o.optString("s"), o.optString("i").ifBlank { null },
                o.getString("so"), o.getString("si"), o.getLong("ti"),
                runCatching { NewsCategory.valueOf(o.getString("c")) }.getOrDefault(NewsCategory.Top),
            )
        }
    }.getOrNull()

    private fun writeCache(list: List<Article>) = runCatching {
        val arr = JSONArray()
        list.take(400).forEach { a ->
            arr.put(
                JSONObject().put("t", a.title).put("l", a.link).put("s", a.summary).put("i", a.image ?: "")
                    .put("so", a.source).put("si", a.sourceId).put("ti", a.time).put("c", a.category.name),
            )
        }
        cacheFile.writeText(arr.toString())
    }

    private fun fetch(s: NewsSource): List<Article> {
        val conn = URL(s.url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", AGENT)
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        return try {
            conn.inputStream.use { parseFeed(it, s) }
        } finally {
            conn.disconnect()
        }
    }

    /** RSS 2.0, RSS 0.91 and Atom; pictures from enclosure, media:content/thumbnail or the HTML. */
    private fun parseFeed(input: java.io.InputStream, s: NewsSource): List<Article> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        val out = ArrayList<Article>()
        var inItem = false
        var title = ""
        var link = ""
        var desc = ""
        var content = ""
        var image: String? = null
        var date = ""
        var tag = ""
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        inItem = true
                        title = ""; link = ""; desc = ""; content = ""; image = null; date = ""
                    } else if (inItem) {
                        when (tag) {
                            "link" -> p.getAttributeValue(null, "href")?.let { if (link.isBlank()) link = it }
                            "enclosure", "media:content", "media:thumbnail" -> {
                                val url = p.getAttributeValue(null, "url")
                                val type = p.getAttributeValue(null, "type").orEmpty()
                                if (url != null && image == null && (type.isBlank() || type.startsWith("image"))) image = url
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                    val t = p.text ?: ""
                    when (tag) {
                        "title" -> title += t
                        "link" -> link += t.trim()
                        "description", "summary" -> desc += t
                        "content:encoded", "content" -> content += t
                        "pubdate", "dc:date", "published", "updated" -> if (date.isBlank()) date = t.trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = p.name.lowercase()
                    if ((name == "item" || name == "entry") && inItem) {
                        inItem = false
                        val html = desc.ifBlank { content }
                        val img = image ?: firstImage(html) ?: firstImage(content)
                        val clean = title.trim().let { Jsoup.parse(it).text() }
                        if (clean.isNotBlank() && link.isNotBlank()) {
                            out += Article(clean, link.trim(), Jsoup.parse(html).text().take(400), img, s.name, s.id, parseDate(date), s.category)
                        }
                    }
                    tag = ""
                }
            }
        }
        return out.take(30)
    }

    private fun firstImage(html: String): String? =
        Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

    private fun parseDate(s: String): Long {
        if (s.isBlank()) return System.currentTimeMillis()
        val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pt in patterns) {
            runCatching { return SimpleDateFormat(pt, Locale.US).parse(s.trim())!!.time }
        }
        return System.currentTimeMillis()
    }

    /**
     * The article's text for the reader: the page is downloaded and its main text taken
     * (the element with the most paragraph text), like a reading view.
     */
    suspend fun body(a: Article): ArticleBody? = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Jsoup.connect(a.link).userAgent(AGENT).timeout(15_000).followRedirects(true).get()
            val image = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() } ?: a.image
            doc.select("script, style, noscript, iframe, form, nav, header, footer, aside, figure figcaption").remove()
            val candidates = doc.select("article, [itemprop=articleBody], main, div")
            val best = candidates.maxByOrNull { el -> el.select("> p").sumOf { it.text().length } }
                ?: doc.body()
            val paragraphs = best.select("p, h2, h3, blockquote, li")
                .map { it.text().trim() }
                .filter { it.length >= 30 && !it.startsWith("Leia também", true) && !it.startsWith("Veja também", true) }
                .distinct()
            if (paragraphs.size < 2) null else ArticleBody(image, paragraphs)
        }.getOrNull()
    }

    companion object {
        private const val AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36 Mozaik-News"

        @Volatile private var instance: NewsRepository? = null
        fun get(context: Context): NewsRepository =
            instance ?: synchronized(this) { instance ?: NewsRepository(context.applicationContext).also { instance = it } }
    }
}
