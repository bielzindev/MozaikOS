package com.wpreborn.apps.news

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpToggle
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.news.Article
import com.wpreborn.data.news.ArticleBody
import com.wpreborn.data.news.NewsCategory
import com.wpreborn.data.news.NewsRepository
import com.wpreborn.data.news.NewsSources
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private sealed interface NewsPage {
    data object Hub : NewsPage
    data class Read(val article: Article) : NewsPage
    data object Sources : NewsPage
}

val NewsCategory.label: Int
    get() = when (this) {
        NewsCategory.Top -> R.string.news_top
        NewsCategory.Brasil -> R.string.news_brasil
        NewsCategory.Economia -> R.string.news_economia
        NewsCategory.Mundo -> R.string.news_mundo
        NewsCategory.Esportes -> R.string.news_esportes
        NewsCategory.Tecnologia -> R.string.news_tecnologia
        NewsCategory.Entretenimento -> R.string.news_entretenimento
    }

/**
 * News, in the style of Bing News on Windows Phone 8.1: a pivot of sections (top stories,
 * Brazil, economy, world, sport, technology, entertainment) from Brazilian outlets' own
 * feeds, a reading view for each story and the choice of sources.
 */
class NewsActivity : WpActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val repo = remember { NewsRepository.get(context) }
        val nav = rememberNavigator<NewsPage>(NewsPage.Hub)
        LaunchedEffect(Unit) { repo.refresh() }
        WpNavHost(nav) { page ->
            when (page) {
                NewsPage.Hub -> Hub(repo, onOpen = { nav.navigate(NewsPage.Read(it)) }, onSources = { nav.navigate(NewsPage.Sources) })
                is NewsPage.Read -> Reader(repo, page.article)
                NewsPage.Sources -> SourcesPage(repo)
            }
        }
    }
}

@Composable
private fun Hub(repo: NewsRepository, onOpen: (Article) -> Unit, onSources: () -> Unit) {
    val articles by repo.articles.collectAsState()
    val loading by repo.loading.collectAsState()
    val scope = rememberCoroutineScope()
    val cats = NewsCategory.entries
    Column(Modifier.fillMaxSize()) {
        WpPivot(
            title = stringResource(R.string.app_news),
            headers = cats.map { stringResource(it.label) },
            modifier = Modifier.weight(1f),
        ) { index ->
            val list = remember(articles, index) { repo.inCategory(articles, cats[index]) }
            when {
                list.isEmpty() && loading -> LoadingDots(Modifier.padding(start = 20.dp, top = 24.dp))
                list.isEmpty() -> CenteredMessage(stringResource(R.string.news_empty), Modifier.padding(start = 20.dp))
                else -> Stories(list, onOpen)
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Refresh, stringResource(R.string.news_refresh), enabled = !loading) { scope.launch { repo.refresh(force = true) } },
                AppBarButton(WpIcons.Settings, stringResource(R.string.news_sources)) { onSources() },
            ),
        )
    }
}

/** The first story large with its picture, then a list with pictures on the left. */
@Composable
private fun Stories(list: List<Article>, onOpen: (Article) -> Unit) {
    val lead = list.firstOrNull { it.image != null } ?: list.first()
    val rest = list.filter { it !== lead }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        item(key = "lead" + lead.link) {
            Column(Modifier.fillMaxWidth().wpClickable { onOpen(lead) }.padding(bottom = 18.dp)) {
                if (lead.image != null) {
                    WebImage(lead.image, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Wp.palette.raised), 1080)
                    Spacer(Modifier.height(8.dp))
                }
                WpText(lead.title, style = WpType.Large.copy(fontSize = 24.sp, lineHeight = 29.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                WpText(byline(lead), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 4.dp))
            }
        }
        items(rest, key = { it.link }) { a ->
            Row(Modifier.fillMaxWidth().wpClickable { onOpen(a) }.padding(vertical = 8.dp)) {
                if (a.image != null) {
                    WebImage(a.image, Modifier.size(96.dp).background(Wp.palette.raised), 288)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    WpText(a.title, style = WpType.Normal.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    WpText(byline(a), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun byline(a: Article): String =
    a.source + " · " + DateUtils.getRelativeTimeSpanString(a.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

/** The reading view: headline, source and time, picture and the text of the story. */
@Composable
private fun Reader(repo: NewsRepository, a: Article) {
    val context = LocalContext.current
    val body by produceState<ArticleBody?>(null, a.link) { value = repo.body(a) }
    var tried by remember(a.link) { mutableStateOf(false) }
    LaunchedEffect(body) { if (body != null) tried = true }
    LaunchedEffect(a.link) {
        kotlinx.coroutines.delay(16_000)
        tried = true
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)) {
            WpText(a.source.uppercase(), style = WpType.AppTitle)
            WpText(a.title, style = WpType.Large.copy(fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Light), modifier = Modifier.padding(top = 10.dp))
            WpText(byline(a), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            val image = body?.image ?: a.image
            if (image != null) {
                WebImage(image, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Wp.palette.raised), 1440)
                Spacer(Modifier.height(14.dp))
            }
            val b = body
            when {
                b != null -> b.paragraphs.forEach { p ->
                    WpText(p, style = WpType.Normal.copy(fontSize = 17.sp, lineHeight = 26.sp), modifier = Modifier.padding(bottom = 14.dp))
                }
                !tried -> {
                    if (a.summary.isNotBlank()) WpText(a.summary, style = WpType.Normal.copy(fontSize = 17.sp, lineHeight = 26.sp))
                    LoadingDots(Modifier.padding(top = 16.dp))
                }
                else -> {
                    if (a.summary.isNotBlank()) WpText(a.summary, style = WpType.Normal.copy(fontSize = 17.sp, lineHeight = 26.sp))
                    WpText(stringResource(R.string.news_read_site), style = WpType.Normal, color = Wp.accent, modifier = Modifier.padding(top = 16.dp).wpClickable { openSite(context, a) })
                }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Share, stringResource(R.string.share)) {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.title + "\n" + a.link), null)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                AppBarButton(WpIcons.Globe, stringResource(R.string.news_open_site)) { openSite(context, a) },
            ),
        )
    }
}

private fun openSite(context: Context, a: Article) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun SourcesPage(repo: NewsRepository) {
    val disabled by repo.disabled.collectAsState()
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)) {
        WpText(stringResource(R.string.app_news).uppercase(), style = WpType.AppTitle)
        WpText(stringResource(R.string.news_sources), style = WpType.Header, modifier = Modifier.padding(bottom = 12.dp))
        NewsCategory.entries.forEach { c ->
            val list = NewsSources.all.filter { it.category == c }
            if (list.isEmpty()) return@forEach
            WpText(stringResource(c.label), style = WpType.GroupHeader, color = Wp.accent, modifier = Modifier.padding(top = 12.dp))
            list.forEach { s ->
                WpToggle(s.name, s.id !in disabled, onText = on, offText = off) { repo.setEnabled(s.id, it) }
            }
        }
        WpText(stringResource(R.string.news_sources_hint), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 16.dp))
    }
}

/** Pictures from the web, cached in memory and on disk. */
object WebImages {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(url: String, size: Int): Bitmap? = memory.get("$url@$size")

    suspend fun load(context: Context, url: String, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        memory.get("$url@$size")?.let { return@withContext it }
        runCatching {
            val dir = File(context.cacheDir, "web-images").apply { mkdirs() }
            val file = File(dir, url.hashCode().toString() + "_" + url.length)
            if (!file.exists()) {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ZUNEX-News")
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                try {
                    conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size / 2) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.also { memory.put("$url@$size", it) }
        }.getOrNull()
    }
}

@Composable
fun rememberWebImage(url: String?, size: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    val initial = remember(url, size) { url?.let { WebImages.cached(it, size)?.asImageBitmap() } }
    return produceState(initial, url, size) {
        // When the address changes the state still holds the previous picture: always load
        // the new one (or clear it), so nothing shows another item's picture.
        val cached = url?.let { WebImages.cached(it, size) }
        value = when {
            url == null -> null
            cached != null -> cached.asImageBitmap()
            else -> WebImages.load(context, url, size)?.asImageBitmap()
        }
    }
}

@Composable
fun WebImage(url: String, modifier: Modifier, size: Int) {
    val img by rememberWebImage(url, size)
    Box(modifier) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
