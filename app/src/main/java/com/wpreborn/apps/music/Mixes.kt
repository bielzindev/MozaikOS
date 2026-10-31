package com.wpreborn.apps.music

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.MusicCollection
import com.wpreborn.data.media.Song
import java.util.Calendar
import kotlin.math.abs

/** A MixRadio-style mix made from the user's own collection. */
data class Mix(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val color: Color,
    val featured: Boolean = false,
) {
    /** Up to four different album covers for the collage. */
    val covers: List<Uri> get() = songs.distinctBy { it.albumId }.take(4).map { it.albumArtUri }
}

object MixBuilder {
    private val palette = listOf(
        AccentColor.Magenta, AccentColor.Cobalt, AccentColor.Orange, AccentColor.Emerald, AccentColor.Violet,
        AccentColor.Crimson, AccentColor.Teal, AccentColor.Amber, AccentColor.Indigo, AccentColor.Pink,
    ).map { it.color }

    private fun colorFor(key: String) = palette[abs(key.hashCode()) % palette.size]

    fun build(context: Context, col: MusicCollection, plays: Map<Long, Int>, favorites: Set<String> = emptySet()): List<Mix> {
        val songs = col.songs
        if (songs.isEmpty()) return emptyList()
        val r = context.resources
        val mixes = ArrayList<Mix>()

        // "Play Me": favourites by play count, topped up with the rest, shuffled.
        val favourites = songs.filter { (plays[it.id] ?: 0) > 0 }.sortedByDescending { plays[it.id] ?: 0 }.take(40)
        val playMe = (favourites + songs.shuffled()).distinctBy { it.id }.take(60).shuffled()
        mixes += Mix("playme", r.getString(R.string.mix_play_me), r.getString(R.string.mix_play_me_sub), playMe, colorFor("playme"), featured = true)

        val favs = songs.filter { it.id.toString() in favorites }
        if (favs.isNotEmpty()) mixes += Mix("favorites", r.getString(R.string.mix_favorites), r.getQuantityString(R.plurals.songs_count, favs.size, favs.size), favs.shuffled(), colorFor("fav"))

        val recentCutoff = System.currentTimeMillis() / 1000 - 45L * 24 * 3600
        val recent = songs.filter { it.dateAdded >= recentCutoff }.ifEmpty { songs.sortedByDescending { it.dateAdded }.take(30) }
        if (recent.size >= 2) mixes += Mix("recent", r.getString(R.string.mix_recent), r.getQuantityString(R.plurals.songs_count, recent.size, recent.size), recent.sortedByDescending { it.dateAdded }, colorFor("recent"))

        val most = songs.filter { (plays[it.id] ?: 0) >= 2 }.sortedByDescending { plays[it.id] ?: 0 }.take(50)
        if (most.size >= 3) mixes += Mix("most", r.getString(R.string.mix_most_played), r.getQuantityString(R.plurals.songs_count, most.size, most.size), most, colorFor("most"))

        songs.filter { it.genre != null }.groupBy { it.genre!!.trim().lowercase() }
            .filter { it.value.size >= 3 }
            .entries.sortedByDescending { it.value.size }.take(6)
            .forEach { (g, list) -> mixes += Mix("genre:$g", g, r.getString(R.string.mix_genre), list.shuffled(), colorFor(g)) }

        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        songs.filter { it.year in 1940..thisYear }.groupBy { it.year / 10 * 10 }
            .filter { it.value.size >= 3 }
            .entries.sortedByDescending { it.key }.take(4)
            .forEach { (decade, list) ->
                mixes += Mix("decade:$decade", r.getString(R.string.mix_decade, decade), r.getString(R.string.mix_decade_sub), list.shuffled(), colorFor("d$decade"))
            }

        col.artists.filter { it.songs.size >= 2 && it.name != com.wpreborn.data.media.MusicLibrary.UNKNOWN }
            .sortedByDescending { a -> a.songs.sumOf { plays[it.id] ?: 0 } * 10 + a.songs.size }.take(8)
            .forEach { a ->
                // An artist mix leans on the artist and fills with albums of similar size from the collection.
                val others = songs.filter { it.artist != a.name && (a.songs.firstOrNull()?.genre?.let { g -> it.genre == g } ?: false) }.shuffled().take(a.songs.size)
                mixes += Mix("artist:${a.name}", r.getString(R.string.mix_artist, a.name), r.getString(R.string.mix_artist_sub), (a.songs.shuffled() + others).shuffled(), colorFor(a.name))
            }

        mixes += Mix("all", r.getString(R.string.music_shuffle_all), r.getQuantityString(R.plurals.songs_count, songs.size, songs.size), songs.shuffled(), colorFor("all"))
        return mixes
    }
}

/** Grid of mix tiles: big album collages with the mix name on a colour band, as in MixRadio. */
@Composable
fun MixesGrid(mixes: List<Mix>, onPlay: (Mix) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(mixes, key = { _, m -> m.id }, span = { _, m -> GridItemSpan(if (m.featured) 2 else 1) }) { i, m ->
            MixTile(m, Modifier.slideInStaggered(i / 2)) { onPlay(m) }
        }
    }
}

@Composable
private fun MixTile(mix: Mix, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (mix.featured) 2f else 1f)
            .background(mix.color)
            .wpClickable(onClick = onClick),
    ) {
        Collage(mix, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to mix.color.copy(alpha = 0.95f))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            WpText(
                mix.title,
                style = (if (mix.featured) WpType.ExtraLarge else WpType.Large).copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            WpText(mix.subtitle, style = WpType.Small, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(6.dp),
        ) {
            WpIcon(WpIcons.Play, tint = Color.White, size = 18.dp)
        }
    }
}

@Composable
private fun Collage(mix: Mix, modifier: Modifier) {
    val covers = mix.covers
    when {
        covers.isEmpty() -> Unit
        covers.size < 4 || mix.featured -> Row(modifier) {
            covers.take(if (mix.featured) 4 else 1).forEach { Thumbnail(it, Modifier.weight(1f).fillMaxSize(), sizePx = 384) }
        }
        else -> Column(modifier) {
            for (row in 0..1) {
                Row(Modifier.weight(1f)) {
                    for (c in 0..1) Thumbnail(covers[row * 2 + c], Modifier.weight(1f).fillMaxSize(), sizePx = 256)
                }
            }
        }
    }
}
