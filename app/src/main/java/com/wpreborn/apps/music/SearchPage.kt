package com.wpreborn.apps.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.media.MusicCollection
import com.wpreborn.data.media.Song
import com.wpreborn.services.music.MusicController
import java.text.Normalizer

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/**
 * Search across the collection (songs, artists, albums, playlists) as you type; accents and
 * case don't matter. Press and hold a song for play next / add to queue.
 */
@Composable
fun SearchPage(
    col: MusicCollection?,
    onPlay: (List<Song>, Int) -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (Long) -> Unit,
    onPlaylist: (String) -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { MusicController.get(context) }
    var q by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Song?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val n = norm(q.trim())
    val songs = remember(col, n) { if (n.isEmpty()) emptyList() else col?.songs.orEmpty().filter { norm(it.title).contains(n) || norm(it.artist).contains(n) || norm(it.album).contains(n) }.take(60) }
    val artists = remember(col, n) { if (n.isEmpty()) emptyList() else col?.artists.orEmpty().filter { norm(it.name).contains(n) }.take(10) }
    val albums = remember(col, n) { if (n.isEmpty()) emptyList() else col?.albums.orEmpty().filter { norm(it.title).contains(n) || norm(it.artist).contains(n) }.take(10) }
    val playlists = remember(col, n) { if (n.isEmpty()) emptyList() else col?.playlists.orEmpty().filter { norm(it.name).contains(n) }.take(10) }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp)) {
                WpText(stringResource(R.string.music_title).uppercase(), style = WpType.AppTitle)
                WpText(stringResource(R.string.music_search), style = WpType.Header, maxLines = 1, softWrap = false)
                WpTextBox(q, { q = it }, Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = stringResource(R.string.music_search_hint), focusRequester = focus)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
                if (n.isNotEmpty() && songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()) {
                    item { WpText(stringResource(R.string.music_search_none), style = WpType.Large, color = Wp.palette.subtle) }
                }
                if (artists.isNotEmpty()) {
                    item { Group(stringResource(R.string.music_artists)) }
                    items(artists, key = { "a" + it.name }) { a ->
                        WpText(a.name.lowercase(), style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().wpClickable { onArtist(a.name) }.padding(vertical = 4.dp))
                    }
                }
                if (albums.isNotEmpty()) {
                    item { Group(stringResource(R.string.music_albums)) }
                    items(albums, key = { "al" + it.id }) { a ->
                        Column(Modifier.fillMaxWidth().wpClickable { onAlbum(a.id) }.padding(vertical = 5.dp)) {
                            WpText(a.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            WpText(a.artist, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1)
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    item { Group(stringResource(R.string.music_songs)) }
                    items(songs.size, key = { "s" + songs[it].id }) { i ->
                        val s = songs[i]
                        Column(Modifier.fillMaxWidth().wpCombinedClickable(onLongClick = { menu = s }) { onPlay(songs, i) }.padding(vertical = 5.dp)) {
                            WpText(s.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            WpText(listOf(s.artist, s.album).filter { it.isNotBlank() }.joinToString(" · "), style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item { Group(stringResource(R.string.music_playlists)) }
                    items(playlists, key = { "p" + it.id }) { p ->
                        WpText(p.name.lowercase(), style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().wpClickable { onPlaylist(p.id) }.padding(vertical = 4.dp))
                    }
                }
            }
        }
        menu?.let { s ->
            WpContextMenu(
                items = listOf(
                    ContextMenuItem(stringResource(R.string.music_play)) { controller.playSongs(listOf(s)) },
                    ContextMenuItem(stringResource(R.string.music_play_next)) { controller.playNext(listOf(s)) },
                    ContextMenuItem(stringResource(R.string.music_add_queue)) { controller.addToQueue(listOf(s)) },
                ),
                anchorY = 400f,
                onDismiss = { menu = null },
            )
        }
    }
}

@Composable
private fun Group(title: String) {
    WpText(title.lowercase(), style = WpType.GroupHeader, color = Wp.accent, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}
