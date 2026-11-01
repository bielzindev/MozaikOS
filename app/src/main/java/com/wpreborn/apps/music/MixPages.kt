package com.wpreborn.apps.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.rememberThumbnail
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.media.FavoriteArtists
import com.wpreborn.data.media.MusicCollection
import com.wpreborn.data.media.PlayMe
import com.wpreborn.data.media.PlayStats
import com.wpreborn.data.media.Song
import com.wpreborn.data.media.Votes
import com.wpreborn.services.music.MusicController
import kotlinx.coroutines.delay

/** A square picture of an artist: a cover of theirs, or the accent with a person glyph. */
@Composable
internal fun ArtistPicture(col: MusicCollection, name: String, modifier: Modifier) {
    val uri = remember(col, name) { col.artists.firstOrNull { it.name == name }?.albums?.firstOrNull()?.artUri }
    val img by rememberThumbnail(uri, 256)
    Box(modifier.background(Wp.accent), contentAlignment = Alignment.Center) {
        WpIcon(WpIcons.Person, tint = Color.White, size = 30.dp)
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/**
 * MixRadio's "favorites": a pivot of artists (the ones you added, plus "added for you" from
 * what you play and like) and what's new (recently added songs of those artists).
 */
@Composable
fun FavoritesPage(col: MusicCollection?, onArtist: (String) -> Unit, onPlay: (List<Song>, Int) -> Unit) {
    val context = LocalContext.current
    val store = remember { FavoriteArtists.get(context) }
    val chosen by store.artists.collectAsState()
    val votes by remember { Votes.get(context) }.votes.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf<Set<String>?>(null) }
    if (col == null) return
    val learned = remember(col, chosen, votes) {
        PlayMe.artistScores(col, PlayStats.get(context).counts(), com.wpreborn.data.media.Favorites.get(context).ids.value, votes)
            .entries.filter { it.value > 2 && it.key !in chosen }.sortedByDescending { it.value }.take(10).map { it.key }
    }
    val artists = chosen.sorted() + learned
    val news = remember(col, artists) { col.songs.filter { it.artist in artists }.sortedByDescending { it.dateAdded }.take(40) }
    Column(Modifier.fillMaxSize()) {
        WpPivot(
            title = stringResource(R.string.mix_favorites),
            headers = listOf(stringResource(R.string.music_artists), stringResource(R.string.mix_whats_new)),
            modifier = Modifier.weight(1f),
        ) { page ->
            if (page == 0) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                    if (artists.isEmpty()) item { WpText(stringResource(R.string.mix_fav_empty), style = WpType.Large, color = Wp.palette.subtle) }
                    items(artists, key = { it }) { a ->
                        val sel = selecting
                        Row(
                            Modifier.fillMaxWidth().wpCombinedClickable(onLongClick = { if (a in chosen) selecting = setOf(a) }) {
                                if (sel != null) { if (a in chosen) selecting = if (a in sel) sel - a else sel + a } else onArtist(a)
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtistPicture(col, a, Modifier.size(78.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                WpText(a, style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = if (sel != null && a in sel) Wp.accent else Wp.palette.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (a !in chosen) WpText(stringResource(R.string.mix_added_for_you), style = WpType.Small, color = Wp.palette.subtle)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                    if (news.isEmpty()) item { WpText(stringResource(R.string.mix_fav_empty), style = WpType.Large, color = Wp.palette.subtle) }
                    items(news.size) { i ->
                        val s = news[i]
                        Column(Modifier.fillMaxWidth().wpClickable { onPlay(news, i) }.padding(vertical = 5.dp)) {
                            WpText(s.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            WpText(s.artist, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
                        }
                    }
                }
            }
        }
        val sel = selecting
        WpAppBar(
            buttons = if (sel == null) listOf(
                AppBarButton(WpIcons.Add, stringResource(R.string.mix_add_artist)) { adding = true },
                AppBarButton(WpIcons.SelectAll, stringResource(R.string.music_select), enabled = chosen.isNotEmpty()) { selecting = emptySet() },
            ) else listOf(
                AppBarButton(WpIcons.Delete, stringResource(R.string.mix_remove_artist), enabled = sel.isNotEmpty()) { store.remove(sel); selecting = null },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { selecting = null },
            ),
        )
    }
    if (adding) ArtistChooser(col, limit = 50, already = chosen) { picked -> adding = false; store.add(picked) }
}

/** Choose artists from the collection (for favourites). */
@Composable
fun ArtistChooser(col: MusicCollection, limit: Int, already: Set<String> = emptySet(), onDone: (List<String>) -> Unit) {
    var chosen by remember { mutableStateOf(listOf<String>()) }
    androidx.activity.compose.BackHandler { onDone(emptyList()) }
    val artists = remember(col) { col.artists.map { it.name }.filter { it.isNotBlank() && it != com.wpreborn.data.media.MusicLibrary.UNKNOWN && it !in already } }
    Column(Modifier.fillMaxSize().background(Wp.palette.background).padding(start = 20.dp, top = 14.dp, end = 16.dp)) {
        WpText(stringResource(R.string.mix_favorites).uppercase(), style = WpType.AppTitle)
        WpText(stringResource(R.string.mix_add_artist), style = WpType.Header, maxLines = 1, softWrap = false)
        LazyColumn(Modifier.weight(1f)) {
            items(artists, key = { it }) { a ->
                val on = a in chosen
                Row(Modifier.fillMaxWidth().wpClickable { chosen = if (on) chosen - a else if (chosen.size < limit) chosen + a else chosen }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtistPicture(col, a, Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    WpText(a, style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = if (on) Wp.accent else Wp.palette.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WpButton(stringResource(R.string.ok), Modifier.weight(1f)) { onDone(chosen) }
            WpButton(stringResource(R.string.cancel), Modifier.weight(1f)) { onDone(emptyList()) }
        }
    }
}

/** MixRadio's mixes page: a grid of covers with captions on the accent; press and hold to save as a playlist. */
@Composable
fun MixesPage(mixes: List<Mix>, onPlay: (Mix) -> Unit, onSave: (Mix) -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 14.dp, end = 16.dp)) {
        WpText(stringResource(R.string.music_mixes).uppercase(), style = WpType.AppTitle)
        WpText(stringResource(R.string.music_mixes), style = WpType.Header, maxLines = 1, softWrap = false)
        WpText(stringResource(R.string.mix_mixes_hint), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(mixes, key = { it.id }) { m ->
                val art by rememberThumbnail(m.songs.firstOrNull()?.albumArtUri, 384)
                Box(Modifier.aspectRatio(1f).background(Wp.accent).wpCombinedClickable(onLongClick = { onSave(m) }) { onPlay(m) }) {
                    art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Wp.accent.copy(alpha = 0.82f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        WpText(m.title, style = WpType.Normal.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** MixRadio's Play Me introduction: add favourite artists, then play a mix that learns. */
@Composable
fun PlayMeIntro(col: MusicCollection?, onPlay: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FavoriteArtists.get(context) }
    val chosen by store.artists.collectAsState()
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, end = 20.dp)) {
        WpText(stringResource(R.string.mix_play_me), style = WpType.Large.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold))
        WpText(stringResource(R.string.mix_intro), style = WpType.Normal, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            val first = chosen.firstOrNull()
            if (col != null && first != null) ArtistPicture(col, first, Modifier.size(130.dp))
            else Box(Modifier.size(130.dp).background(Wp.accent.copy(alpha = 0.5f)))
            Box(Modifier.offset(140.dp, 78.dp).size(52.dp).background(Wp.accent.copy(alpha = 0.45f)))
            Box(
                Modifier.offset(98.dp, 140.dp).size(92.dp).background(Wp.accent).wpClickable { adding = true },
                contentAlignment = Alignment.Center,
            ) { WpIcon(WpIcons.Add, tint = Color.White, size = 48.dp) }
        }
        if (chosen.isNotEmpty()) WpText(chosen.sorted().joinToString(", "), style = WpType.Small, color = Wp.palette.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp))
        Box(
            Modifier.border(2.dp, Wp.accent).background(Wp.accent).wpClickable(onClick = onPlay).padding(horizontal = 30.dp, vertical = 6.dp),
        ) { WpText(stringResource(R.string.mix_play_me).lowercase(), style = WpType.Normal, color = Color.White) }
    }
    if (adding && col != null) ArtistChooser(col, limit = 50, already = chosen) { picked -> adding = false; store.add(picked) }
}

/** The banner after a vote, as on MixRadio: "Great track!" or "Not for you?", with ok. */
@Composable
fun VoteBanner(up: Boolean, onDismiss: () -> Unit) {
    LaunchedEffect(up) {
        delay(5000)
        onDismiss()
    }
    Box(Modifier.fillMaxWidth().background(Wp.accent).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
        Column(Modifier.fillMaxWidth().padding(end = 80.dp)) {
            WpText(stringResource(if (up) R.string.mix_vote_up_title else R.string.mix_vote_down_title), style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            WpText(stringResource(if (up) R.string.mix_vote_up else R.string.mix_vote_down), style = WpType.Normal, color = Color.White, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            Box(Modifier.border(2.dp, Color.White).wpClickable(onClick = onDismiss).padding(horizontal = 60.dp, vertical = 6.dp)) {
                WpText(stringResource(R.string.ok), style = WpType.Normal, color = Color.White)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(64.dp).border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { WpIcon(ThumbUpIcon, tint = Color.White, size = 30.dp, modifier = Modifier.rotate(if (up) 0f else 180f)) }
    }
}

/** Shares the playing song and the next two (the files), like MixRadio's "share three songs". */
fun shareThree(context: android.content.Context, ids: List<Long>) {
    if (ids.isEmpty()) return
    val uris = ArrayList(ids.take(3).map { android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) })
    runCatching {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).setType("audio/*")
            .putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
