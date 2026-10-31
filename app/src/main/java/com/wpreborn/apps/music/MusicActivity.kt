package com.wpreborn.apps.music

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.motion.tilt
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.rememberPivotState
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.media.Album
import com.wpreborn.data.media.Artist
import com.wpreborn.data.media.MusicCollection
import com.wpreborn.data.media.MusicLibrary
import com.wpreborn.data.media.Playlist
import com.wpreborn.data.media.Song
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.WpNavigator
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.rememberPermissionState
import com.wpreborn.services.music.MusicController
import com.wpreborn.services.music.PlayerUi
import kotlinx.coroutines.launch

private sealed interface MusicPage {
    data object Collection : MusicPage
    data class ArtistPage(val name: String) : MusicPage
    data class AlbumPage(val id: Long) : MusicPage
    data class PlaylistPage(val id: String) : MusicPage
    data object NowPlaying : MusicPage
    data object NewPlaylist : MusicPage
}

/**
 * Music, modelled on Xbox Music for Windows Phone 8.1: a "collection" pivot (artists,
 * albums, songs, playlists), detail pages and the now playing screen. Playback runs in
 * [com.wpreborn.services.music.MusicService] with lock screen and notification controls.
 */
class MusicActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val perm = rememberPermissionState(Perms.AUDIO)
        val library = remember { MusicLibrary(context) }
        var collection by remember { mutableStateOf<MusicCollection?>(null) }
        var reload by remember { mutableStateOf(0) }
        LaunchedEffect(perm.granted, reload) { if (perm.granted) collection = library.load() }
        val controller = remember { MusicController.get(context) }
        LaunchedEffect(Unit) { controller.connect() }
        val ui by controller.ui.collectAsState()
        val external by com.wpreborn.data.media.ExternalMediaWatcher.state.collectAsState()

        val nav = rememberNavigator<MusicPage>(if (intent?.getBooleanExtra(EXTRA_NOW_PLAYING, false) == true) MusicPage.NowPlaying else MusicPage.Collection)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                WpNavHost(nav) { page ->
                    val col = collection
                    when (page) {
                        MusicPage.Collection -> CollectionPage(perm.granted, col, nav, controller, ui, external, onReload = { reload++ }) {
                            PermissionGate(perm, stringResource(R.string.perm_music), Modifier.padding(start = 20.dp)) {}
                        }
                        is MusicPage.ArtistPage -> col?.artists?.firstOrNull { it.name == page.name }?.let { ArtistDetail(it, nav, controller) }
                        is MusicPage.AlbumPage -> col?.albums?.firstOrNull { it.id == page.id }?.let { AlbumDetail(it, controller) }
                        is MusicPage.PlaylistPage -> col?.playlists?.firstOrNull { it.id == page.id }?.let { p ->
                            PlaylistDetail(p, col.songsOf(p), controller, onDeleted = { reload++; nav.pop() })
                        }
                        MusicPage.NowPlaying -> NowPlayingScreen(ui, external, controller)
                        MusicPage.NewPlaylist -> NewPlaylistPage(library) { reload++; nav.pop() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_NOW_PLAYING = "now_playing"
    }
}

@Composable
private fun CollectionPage(
    granted: Boolean,
    col: MusicCollection?,
    nav: WpNavigator<MusicPage>,
    controller: MusicController,
    ui: PlayerUi,
    external: com.wpreborn.data.media.NowPlaying?,
    onReload: () -> Unit,
    gate: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val pivot = rememberPivotState(6)
    val mixes = remember(col) { col?.let { MixBuilder.build(context, it, com.wpreborn.data.media.PlayStats.get(context).counts()) }.orEmpty() }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    var addTo by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().onGloballyPositioned { top = it.boundsInWindow().top }) {
        Column(Modifier.fillMaxSize()) {
            WpPivot(
                title = stringResource(R.string.music_title),
                headers = listOf(
                    stringResource(R.string.music_mixes),
                    stringResource(R.string.music_artists),
                    stringResource(R.string.music_albums),
                    stringResource(R.string.music_songs),
                    stringResource(R.string.music_playlists),
                    "spotify",
                ),
                state = pivot,
                modifier = Modifier.weight(1f),
            ) { page ->
                // Pages: mixes, artists, albums, songs, playlists, spotify
                val index = page - 1
                when {
                    page == 5 -> SpotifyPage(external) { nav.navigate(MusicPage.NowPlaying) }
                    !granted -> gate()
                    col == null -> LoadingDots(Modifier.padding(top = 24.dp))
                    col.songs.isEmpty() && index != 3 -> CenteredMessage(stringResource(R.string.music_empty), Modifier.padding(start = 20.dp))
                    page == 0 -> MixesGrid(mixes) { m ->
                        controller.playSongs(m.songs, 0)
                        nav.navigate(MusicPage.NowPlaying)
                    }
                    index == 0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, bottom = 16.dp)) {
                        itemsIndexed(col.artists, key = { _, a -> a.name }) { i, a ->
                            WpText(
                                a.name.lowercase(),
                                style = WpType.ExtraLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .slideInStaggered(i)
                                    .wpClickable { nav.navigate(MusicPage.ArtistPage(a.name)) }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                    index == 1 -> AlbumGrid(col.albums) { nav.navigate(MusicPage.AlbumPage(it.id)) }
                    index == 2 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
                        item {
                            WpText(
                                stringResource(R.string.music_shuffle_all),
                                style = WpType.Large,
                                color = Wp.accent,
                                modifier = Modifier.wpClickable { controller.playSongs(col.songs.shuffled(), 0, shuffle = true); nav.navigate(MusicPage.NowPlaying) }.padding(vertical = 6.dp),
                            )
                        }
                        itemsIndexed(col.songs, key = { _, s -> s.id }) { i, s ->
                            var bottom by remember { mutableFloatStateOf(0f) }
                            SongRow(
                                s,
                                Modifier.onGloballyPositioned { bottom = it.boundsInWindow().bottom }.slideInStaggered(i),
                                current = ui.mediaId == s.id.toString(),
                                onLongClick = { menuY = bottom - top; menuSong = s },
                            ) {
                                controller.playSongs(col.songs, i)
                                nav.navigate(MusicPage.NowPlaying)
                            }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize().padding(start = 20.dp)) {
                        WpText(
                            stringResource(R.string.music_new_playlist),
                            style = WpType.Large,
                            color = Wp.accent,
                            modifier = Modifier.wpClickable { nav.navigate(MusicPage.NewPlaylist) }.padding(vertical = 6.dp),
                        )
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(col.playlists, key = { _, p -> p.id }) { i, p ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .slideInStaggered(i)
                                        .wpClickable { nav.navigate(MusicPage.PlaylistPage(p.id)) }
                                        .padding(vertical = 6.dp),
                                ) {
                                    WpText(p.name.lowercase(), style = WpType.ExtraLarge, maxLines = 1)
                                    WpText(context.resources.getQuantityString(R.plurals.songs_count, p.songIds.size, p.songIds.size), style = WpType.Subtle, color = Wp.palette.subtle)
                                }
                            }
                        }
                    }
                }
            }
            if (ui.hasMedia || external != null) MiniPlayer(ui, external, controller) { nav.navigate(MusicPage.NowPlaying) }
            WpAppBar(
                buttons = emptyList(),
                menuItems = listOf(AppBarMenuItem(stringResource(R.string.refresh)) { onReload() }),
            )
        }
        menuSong?.let { s ->
            WpContextMenu(
                items = listOf(
                    ContextMenuItem(stringResource(R.string.music_play)) { controller.playSongs(listOf(s)) },
                    ContextMenuItem(stringResource(R.string.music_add_to_playlist), enabled = !col?.playlists.orEmpty().none { !it.system }) { addTo = s },
                ),
                anchorY = menuY,
                onDismiss = { menuSong = null },
            )
        }
        addTo?.let { s ->
            WpContextMenu(
                items = col?.playlists.orEmpty().filterNot { it.system }.map { p ->
                    ContextMenuItem(p.name) {
                        scope.launch {
                            MusicLibrary(context).addToPlaylist(p.id, listOf(s.id))
                            onReload()
                        }
                    }
                },
                anchorY = menuY,
                onDismiss = { addTo = null },
            )
        }
    }
}

@Composable
private fun SongRow(s: Song, modifier: Modifier, current: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .wpCombinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            WpText(s.title, style = WpType.Large, color = if (current) Wp.accent else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(s.artist, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current) WpIcon(WpIcons.MusicNote, tint = Wp.accent, size = 18.dp, modifier = Modifier.padding(horizontal = 6.dp))
        WpText(WpFormat.duration(s.durationMs), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(albums, key = { _, a -> a.id }) { i, a ->
            Column(Modifier.slideInStaggered(i / 2).wpClickable { onOpen(a) }) {
                AlbumCover(a.artUri, Modifier.fillMaxWidth().aspectRatio(1f))
                WpText(a.title, style = WpType.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                WpText(a.artist, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlbumCover(uri: android.net.Uri?, modifier: Modifier) {
    Thumbnail(uri, modifier.background(Wp.palette.raised), sizePx = 512) {
        Box(Modifier.fillMaxSize().background(Wp.accent), contentAlignment = Alignment.Center) {
            WpIcon(WpIcons.MusicNote, tint = Color.White, size = 48.dp)
        }
    }
}

@Composable
private fun ArtistDetail(artist: Artist, nav: WpNavigator<MusicPage>, controller: MusicController) {
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.music_title), artist.name)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            item {
                WpText(
                    stringResource(R.string.music_play_artist),
                    style = WpType.Large,
                    color = Wp.accent,
                    modifier = Modifier.wpClickable { controller.playSongs(artist.songs) ; nav.navigate(MusicPage.NowPlaying) }.padding(vertical = 6.dp),
                )
                WpText(stringResource(R.string.music_albums), style = WpType.GroupHeader, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(artist.albums, key = { _, a -> "a${a.id}" }) { i, a ->
                Row(
                    Modifier.fillMaxWidth().slideInStaggered(i).wpClickable { nav.navigate(MusicPage.AlbumPage(a.id)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumCover(a.artUri, Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        WpText(a.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WpText(a.songs.size.toString(), style = WpType.Subtle, color = Wp.palette.subtle)
                    }
                }
            }
            item { WpText(stringResource(R.string.music_songs), style = WpType.GroupHeader, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp)) }
            itemsIndexed(artist.songs, key = { _, s -> "s${s.id}" }) { i, s ->
                SongRow(s, Modifier) { controller.playSongs(artist.songs, i); nav.navigate(MusicPage.NowPlaying) }
            }
        }
    }
}

@Composable
private fun AlbumDetail(album: Album, controller: MusicController) {
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(album.artist, album.title)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            item {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                    AlbumCover(album.artUri, Modifier.size(150.dp))
                    Spacer(Modifier.width(16.dp))
                    WpButton(stringResource(R.string.music_play)) { controller.playSongs(album.songs) }
                }
            }
            itemsIndexed(album.songs, key = { _, s -> s.id }) { i, s ->
                Row(
                    Modifier.fillMaxWidth().slideInStaggered(i).wpClickable { controller.playSongs(album.songs, i) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WpText(s.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    WpText(WpFormat.duration(s.durationMs), style = WpType.Subtle, color = Wp.palette.subtle)
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(p: Playlist, songs: List<Song>, controller: MusicController, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.music_playlists), p.name)
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            if (songs.isEmpty()) item { CenteredMessage(stringResource(R.string.music_playlist_empty)) }
            itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                SongRow(s, Modifier.slideInStaggered(i)) { controller.playSongs(songs, i) }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Play, stringResource(R.string.music_play), enabled = songs.isNotEmpty()) { controller.playSongs(songs) },
                AppBarButton(WpIcons.Shuffle, stringResource(R.string.music_shuffle), enabled = songs.isNotEmpty()) { controller.playSongs(songs.shuffled(), shuffle = true) },
            ) + if (!p.system) listOf(
                AppBarButton(WpIcons.Delete, stringResource(R.string.delete)) {
                    scope.launch {
                        MusicLibrary(context).deletePlaylist(p.id)
                        onDeleted()
                    }
                },
            ) else emptyList(),
        )
    }
}

@Composable
private fun NewPlaylistPage(library: MusicLibrary, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.music_title), stringResource(R.string.music_new_playlist))
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            WpText(stringResource(R.string.music_playlist_name), style = WpType.Subtle, color = Wp.palette.subtle)
            WpTextBox(name, { name = it }, Modifier.fillMaxWidth())
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.save), enabled = name.isNotBlank()) {
                    scope.launch {
                        library.createPlaylist(name.trim())
                        onCreated()
                    }
                },
            ),
        )
    }
}

/** Bottom strip with the current song (ours or Spotify's), like the now playing bar of Music on 8.1. */
@Composable
private fun MiniPlayer(ui: PlayerUi, external: com.wpreborn.data.media.NowPlaying?, controller: MusicController, onOpen: () -> Unit) {
    val own = ui.hasMedia && (ui.playing || external?.playing != true)
    val title = if (own) ui.title else external?.title.orEmpty()
    val artist = if (own) ui.artist else external?.artist.orEmpty()
    val playing = if (own) ui.playing else external?.playing == true
    Row(
        Modifier
            .fillMaxWidth()
            .background(Wp.palette.chrome)
            .wpClickable(tilt = false, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (own) {
            AlbumCover(ui.artUri, Modifier.size(48.dp))
        } else {
            Box(Modifier.size(48.dp).background(Color(0xFF1DB954)), contentAlignment = Alignment.Center) {
                val art = external?.art
                if (art != null) {
                    androidx.compose.foundation.Image(remember(art) { art.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    WpIcon(WpIcons.Music, tint = Color.White, size = 24.dp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            WpText(title, style = WpType.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(artist, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(if (playing) WpIcons.Pause else WpIcons.Play, size = 40.dp) {
            if (own) controller.togglePlay() else com.wpreborn.data.media.ExternalMediaWatcher.playPause()
        }
        Spacer(Modifier.width(8.dp))
        RoundButton(WpIcons.Next, size = 40.dp) {
            if (own) controller.next() else com.wpreborn.data.media.ExternalMediaWatcher.next()
        }
    }
}
