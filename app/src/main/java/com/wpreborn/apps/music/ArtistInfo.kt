package com.wpreborn.apps.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
    private const val AGENT = "ZUNEX launcher (https://github.com/joaogabrielsilvademourap3-spec/zunex)"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", AGENT)
        c.connectTimeout = 8000
        c.readTimeout = 10000
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    /** Cached for a month; looks the artist up on Wikipedia in the phone's language, else English. */
    suspend fun load(context: Context, name: String): ArtistBio? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("artist_bios", Context.MODE_PRIVATE)
        val key = name.lowercase()
        prefs.getString(key, null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                if (System.currentTimeMillis() - o.getLong("t") < 30L * 24 * 3600 * 1000) {
                    return@withContext if (o.optBoolean("none")) null else ArtistBio(o.getString("title"), o.getString("text"), o.optString("image").ifBlank { null }, o.getString("page"))
                }
            }
        }
        val lang = java.util.Locale.getDefault().language.takeIf { it == "pt" || it == "es" || it == "fr" || it == "de" || it == "it" } ?: "en"
        val bio = (if (lang != "en") find(lang, name) else null) ?: find("en", name)
        val o = JSONObject().put("t", System.currentTimeMillis())
        if (bio == null) o.put("none", true) else o.put("title", bio.title).put("text", bio.text).put("image", bio.image ?: "").put("page", bio.page)
        prefs.edit().putString(key, o.toString()).apply()
        bio
    }

    private fun find(lang: String, name: String): ArtistBio? = runCatching {
        // Search for the artist as a musician, then take the summary of the best page.
        val q = URLEncoder.encode("$name " + if (lang == "pt") "música cantor banda" else "music singer band", "UTF-8")
        val search = JSONObject(get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=$q"))
        val hits = search.getJSONObject("query").getJSONArray("search")
        val norm = name.lowercase().filter { it.isLetterOrDigit() }
        for (i in 0 until hits.length()) {
            val title = hits.getJSONObject(i).getString("title")
            if (!title.lowercase().filter { it.isLetterOrDigit() }.contains(norm.take(6))) continue
            val s = JSONObject(get("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")))
            if (s.optString("type") == "disambiguation") continue
            val text = s.optString("extract")
            if (text.isBlank()) continue
            val image = s.optJSONObject("originalimage")?.optString("source") ?: s.optJSONObject("thumbnail")?.optString("source")
            val page = s.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page") ?: "https://$lang.wikipedia.org/wiki/${title.replace(' ', '_')}"
            return@runCatching ArtistBio(title, text, image, page)
        }
        null
    }.getOrNull()
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
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Background: the artist's picture (or a cover), blurred and dimmed.
        val bg = photo ?: cover
        bg?.let {
            Image(
                it, null, Modifier.fillMaxSize().then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier),
                contentScale = ContentScale.Crop,
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
                            photo?.let { Image(it, null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop) }
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
