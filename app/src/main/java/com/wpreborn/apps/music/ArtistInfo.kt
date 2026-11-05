package com.wpreborn.apps.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.news.rememberWebImage
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.rememberThumbnail
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.Artist
import com.wpreborn.data.media.FavoriteArtists
import com.wpreborn.data.media.MusicCollection
import com.wpreborn.data.media.PlayMe
import com.wpreborn.data.media.PlayStats
import com.wpreborn.data.media.Song
import com.wpreborn.data.media.Votes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** An artist's summary from Wikipedia (text under CC BY-SA, shown with its source). */
data class ArtistBio(val title: String, val text: String, val image: String?, val page: String)

object ArtistBios {
    private const val AGENT = "Mozaik launcher (https://github.com/bielzindev/MozaikOS)"

    /** The network failed (offline, timeout, rate limit): not the same as "no such artist". */
    private class Offline : Exception()

    /** The page text, null when it doesn't exist (404); [Offline] on any other failure. */
    private fun get(url: String): String? {
        var lastError: Exception? = null
        repeat(2) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.setRequestProperty("User-Agent", AGENT)
                c.setRequestProperty("Accept", "application/json")
                c.connectTimeout = 10000
                c.readTimeout = 15000
                when (val code = c.responseCode) {
                    200 -> return c.inputStream.bufferedReader().use { it.readText() }
                    404 -> return null
                    else -> lastError = java.io.IOException("HTTP $code")
                }
            } catch (e: java.io.IOException) {
                lastError = e
            } finally {
                c.disconnect()
            }
        }
        throw Offline()
    }

    /** "A feat. B", "A & B", "A, B": the first artist, which is the one with a page. */
    private fun primary(name: String): String =
        name.split(Regex("\\s+(feat\\.?|ft\\.?|featuring|with|x|&)\\s+|\\s*[,;/]\\s*", RegexOption.IGNORE_CASE)).first().trim().ifBlank { name }

    /**
     * Cached for a month when found (three days when there is no page); a failed download is
     * never cached, so the next visit tries again. Looks the artist up on Wikipedia in the
     * phone's language, else English.
     */
    suspend fun load(context: Context, name: String): ArtistBio? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("artist_bios3", Context.MODE_PRIVATE)
        val key = name.lowercase()
        prefs.getString(key, null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                val none = o.optBoolean("none")
                val ttl = if (none) 3L * 24 * 3600 * 1000 else 30L * 24 * 3600 * 1000
                if (System.currentTimeMillis() - o.getLong("t") < ttl) {
                    return@withContext if (none) null else ArtistBio(o.getString("title"), o.getString("text"), o.optString("image").ifBlank { null }, o.getString("page"))
                }
            }
        }
        val lang = java.util.Locale.getDefault().language.takeIf { it == "pt" || it == "es" || it == "fr" || it == "de" || it == "it" } ?: "en"
        val bio = try {
            (if (lang != "en") find(lang, name) else null) ?: find("en", name)
        } catch (_: Offline) {
            return@withContext null
        }
        val o = JSONObject().put("t", System.currentTimeMillis())
        if (bio == null) o.put("none", true) else o.put("title", bio.title).put("text", bio.text).put("image", bio.image ?: "").put("page", bio.page)
        prefs.edit().putString(key, o.toString()).apply()
        bio
    }

    private val musicWords = listOf(
        "singer", "band", "musician", "rapper", "songwriter", "composer", "producer", "vocalist", "duo", "group", "dj", "artist",
        "cantor", "cantora", "banda", "músico", "compositor", "rapper", "grupo", "dupla", "artista", "cantautor",
        "chanteur", "músic", "sänger", "cantante", "musiker",
    )

    private fun summary(lang: String, title: String): JSONObject? {
        val body = get("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")) ?: return null
        val s = JSONObject(body)
        if (s.optString("type") == "disambiguation" || s.optString("extract").isBlank()) return null
        return s
    }

    private fun isMusic(s: JSONObject): Boolean {
        val d = (s.optString("description") + " " + s.optString("extract").take(240)).lowercase()
        return musicWords.any { it in d }
    }

    private fun bioOf(lang: String, title: String, s: JSONObject): ArtistBio {
        val image = s.optJSONObject("originalimage")?.optString("source") ?: s.optJSONObject("thumbnail")?.optString("source")
        val page = s.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page") ?: "https://$lang.wikipedia.org/wiki/${title.replace(' ', '_')}"
        return ArtistBio(s.optString("title").ifBlank { title }, s.optString("extract"), image, page)
    }

    private fun find(lang: String, name: String): ArtistBio? {
        for (n in listOf(name, primary(name)).distinct()) {
            val norm = n.lowercase().filter { it.isLetterOrDigit() }
            if (norm.isEmpty()) continue
            // The page named like the artist (Wikipedia follows redirects), then with the
            // usual qualifiers; accepted when it is about a musician.
            for (t in listOf(n, "$n (band)", "$n (singer)", "$n (rapper)", "$n (musician)")) {
                val s = summary(lang, t) ?: continue
                if (isMusic(s)) return bioOf(lang, t, s)
            }
            // Search as a musician and take the best page about this artist.
            val q = URLEncoder.encode("$n " + if (lang == "pt") "música cantor banda" else "music singer band", "UTF-8")
            val search = JSONObject(get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=5&srsearch=$q") ?: continue)
            val hits = search.getJSONObject("query").getJSONArray("search")
            for (i in 0 until hits.length()) {
                val title = hits.getJSONObject(i).getString("title")
                val t = title.lowercase().filter { it.isLetterOrDigit() }
                if (!t.contains(norm)) continue
                val low = title.lowercase()
                if (listOf("discograph", "discografia", "lista de", "list of", "song", "canção", "álbum", "album", "tour", "turnê").any { it in low }) continue
                val s = summary(lang, title) ?: continue
                if (isMusic(s) || t.length <= norm.length + 14) return bioOf(lang, title, s)
            }
        }
        return null
    }
}

/**
 * The artist page of the new interface, after MixRadio's: their picture blurred behind, and
 * a pivot of albums, songs, similar artists and "about" (Wikipedia, with your own numbers).
 */
@Composable
fun MixArtistPage(
    col: MusicCollection,
    artist: Artist,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onPlay: (List<Song>, Int, String?) -> Unit,
) {
    val context = LocalContext.current
    val bio by produceState<ArtistBio?>(null, artist.name) { value = ArtistBios.load(context, artist.name) }
    val photo by rememberWebImage(bio?.image, 720)
    val cover by rememberThumbnail(artist.albums.firstOrNull()?.artUri, 512)
    val favStore = remember { FavoriteArtists.get(context) }
    val favs by favStore.artists.collectAsState()
    val plays = remember { PlayStats.get(context).counts() }
    val genres = remember(artist) { artist.songs.mapNotNull { it.genre }.distinct() }
    val similar = remember(col, artist) {
        val g = genres.map { it.lowercase() }.toSet()
        col.artists.filter { it.name != artist.name && it.name.isNotBlank() && it.songs.any { s -> s.genre?.lowercase() in g } }
            .sortedByDescending { a -> a.songs.count { s -> s.genre?.lowercase() in g } }.take(12)
    }
    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        // Background: the artist's picture (or a cover), blurred and dimmed.
        val bg = photo ?: cover
        bg?.let {
            Image(
                it, null, Modifier.fillMaxSize().then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier),
                contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(0f, -0.6f),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        androidx.compose.runtime.CompositionLocalProvider(com.wpreborn.core.theme.LocalPalette provides com.wpreborn.core.theme.WpPalette.Dark) {
            Column(Modifier.fillMaxSize()) {
                WpPivot(
                    title = artist.name,
                    headers = listOf(stringResource(R.string.music_albums), stringResource(R.string.music_songs), stringResource(R.string.artist_similar), stringResource(R.string.artist_about)),
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                            items(artist.albums, key = { it.id }) { a ->
                                Row(Modifier.fillMaxWidth().wpClickable { onAlbum(a.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val art by rememberThumbnail(a.artUri, 256)
                                    Box(Modifier.size(90.dp).background(Wp.accent)) { art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        WpText(a.title, style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val year = a.songs.maxOfOrNull { it.year }?.takeIf { it > 0 }
                                        WpText(listOfNotNull(year?.toString(), context.resources.getQuantityString(R.plurals.songs_count, a.songs.size, a.songs.size)).joinToString(" · "), style = WpType.Small, color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                        1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                            val list = artist.songs.sortedByDescending { plays[it.id] ?: 0 }
                            items(list.size) { i ->
                                val s = list[i]
                                Row(Modifier.fillMaxWidth().wpClickable { onPlay(list, i, artist.name.uppercase()) }.padding(vertical = 6.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        WpText(s.title, style = WpType.Large, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        WpText(s.album, style = WpType.Small, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                                    }
                                    WpText(WpFormat.duration(s.durationMs), style = WpType.Small, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                        2 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                            if (similar.isEmpty()) item { WpText(stringResource(R.string.artist_no_similar), style = WpType.Normal, color = Color.White.copy(alpha = 0.7f)) }
                            items(similar, key = { it.name }) { a ->
                                Row(Modifier.fillMaxWidth().wpClickable { onArtist(a.name) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ArtistPicture(col, a.name, Modifier.size(64.dp))
                                    Spacer(Modifier.width(12.dp))
                                    WpText(a.name, style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                            // The whole picture, in its own proportions (portraits aren't cut).
                            photo?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio((it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.62f, 2.2f)), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) }
                            Spacer(Modifier.height(12.dp))
                            val b = bio
                            if (b != null) {
                                WpText(b.text, style = WpType.Normal.copy(fontSize = 16.sp, lineHeight = 24.sp), color = Color.White)
                                WpText(
                                    stringResource(R.string.artist_source),
                                    style = WpType.Small,
                                    color = Wp.accent,
                                    modifier = Modifier.padding(top = 8.dp).wpClickable {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(b.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    },
                                )
                            } else {
                                WpText(stringResource(R.string.artist_no_bio), style = WpType.Normal, color = Color.White.copy(alpha = 0.7f))
                            }
                            Spacer(Modifier.height(18.dp))
                            // Your numbers for this artist.
                            val total = artist.songs.sumOf { it.durationMs }
                            val years = artist.songs.map { it.year }.filter { it > 0 }
                            val top = artist.songs.maxByOrNull { plays[it.id] ?: 0 }?.takeIf { (plays[it.id] ?: 0) > 0 }
                            Stat(stringResource(R.string.music_songs), artist.songs.size.toString())
                            Stat(stringResource(R.string.music_albums), artist.albums.size.toString())
                            Stat(stringResource(R.string.artist_time), WpFormat.duration(total))
                            if (genres.isNotEmpty()) Stat(stringResource(R.string.music_tag_genre), genres.joinToString(", "))
                            if (years.isNotEmpty()) Stat(stringResource(R.string.music_tag_year), if (years.min() == years.max()) years.min().toString() else "${years.min()}–${years.max()}")
                            top?.let { Stat(stringResource(R.string.artist_most_played), it.title) }
                        }
                    }
                }
                val fav = artist.name in favs
                WpAppBar(
                    buttons = listOf(
                        AppBarButton(WpIcons.Play, stringResource(R.string.artist_play_mix)) {
                            val list = PlayMe.fromArtists(col, listOf(artist.name), Votes.get(context).votes.value)
                            if (list.isNotEmpty()) onPlay(list, 0, artist.name.uppercase())
                        },
                        AppBarButton(WpIcons.Shuffle, stringResource(R.string.music_shuffle)) { onPlay(artist.songs.shuffled(), 0, artist.name.uppercase()) },
                        AppBarButton(if (fav) WpIcons.Star else WpIcons.StarOutline, stringResource(if (fav) R.string.mix_remove_artist else R.string.mix_add_artist)) {
                            if (fav) favStore.remove(listOf(artist.name)) else favStore.add(listOf(artist.name))
                        },
                    ),
                    background = Color.Black.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        WpText(label.lowercase(), style = WpType.Normal, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.width(140.dp))
        WpText(value, style = WpType.Normal, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
