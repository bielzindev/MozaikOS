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

        val nav = rememberNavigator<MusicPage>(if (intent?.getBooleanExtra(EXTRA_NOW_PLAYING, false) == true) MusicPage.NowPlaying else MusicPage.Collection)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                WpNavHost(nav) { page ->
                    val col = collection
                    when (page) {
                        MusicPage.Collection -> CollectionPage(perm.granted, col, nav, controller, ui, onReload = { reload++ }) {
                            PermissionGate(perm, stringResource(R.string.perm_music), Modifier.padding(start = 20.dp)) {}
                        }
                        is MusicPage.ArtistPage -> col?.artists?.firstOrNull { it.name == page.name }?.let { ArtistDetail(it, nav, controller) }
                        is MusicPage.AlbumPage -> col?.albums?.firstOrNull { it.id == page.id }?.let { AlbumDetail(it, controller) }
                        is MusicPage.PlaylistPage -> col?.playlists?.firstOrNull { it.id == page.id }?.let { p ->
                            PlaylistDetail(p, col.songsOf(p), controller, onDeleted = { reload++; nav.pop() })
                        }
                        MusicPage.NowPlaying -> NowPlayingPage(ui, controller)
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
    onReload: () -> Unit,
    gate: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val pivot = rememberPivotState(4)
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
                    stringResource(R.string.music_artists),
                    stringResource(R.string.music_albums),
                    stringResource(R.string.music_songs),
                    stringResource(R.string.music_playlists),
                ),
                state = pivot,
                modifier = Modifier.weight(1f),
            ) { index ->
                when {
                    !granted -> gate()
                    col == null -> LoadingDots(Modifier.padding(top = 24.dp))
                    col.songs.isEmpty() && index != 3 -> CenteredMessage(stringResource(R.string.music_empty), Modifier.padding(start = 20.dp))
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
            if (ui.hasMedia) MiniPlayer(ui, controller) { nav.navigate(MusicPage.NowPlaying) }
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
private fun SongRow(s: Song, modifier: Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .wpCombinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        WpText(s.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
        WpText(s.artist, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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

/** Bottom strip with the current song, like the "now playing" bar of Music on 8.1. */
@Composable
private fun MiniPlayer(ui: PlayerUi, controller: MusicController, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Wp.palette.chrome)
            .wpClickable(tilt = false, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumCover(ui.artUri, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            WpText(ui.title, style = WpType.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(ui.artist, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(if (ui.playing) WpIcons.Pause else WpIcons.Play, size = 40.dp) { controller.togglePlay() }
        Spacer(Modifier.width(8.dp))
        RoundButton(WpIcons.Next, size = 40.dp) { controller.next() }
    }
}

@Composable
private fun NowPlayingPage(ui: PlayerUi, controller: MusicController) {
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.music_now_playing), null)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (!ui.hasMedia) {
                CenteredMessage(stringResource(R.string.music_nothing_playing))
                return@Column
            }
            // Big artist name behind the art, as Xbox Music did.
            WpText(
                ui.artist.lowercase(),
                style = WpType.Header.copy(fontSize = 40.sp),
                maxLines = 1,
                softWrap = false,
                color = Wp.palette.subtle,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            AlbumCover(ui.artUri, Modifier.fillMaxWidth(0.82f).aspectRatio(1f).tilt())
            Spacer(Modifier.height(14.dp))
            WpText(ui.title, style = WpType.ExtraLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            WpText(ui.album, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Progress(ui, controller)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                RoundButton(WpIcons.Previous, size = 56.dp) { controller.previous() }
                Spacer(Modifier.width(24.dp))
                RoundButton(if (ui.playing) WpIcons.Pause else WpIcons.Play, size = 72.dp) { controller.togglePlay() }
                Spacer(Modifier.width(24.dp))
                RoundButton(WpIcons.Next, size = 56.dp) { controller.next() }
            }
            if (ui.nextTitles.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                WpText(stringResource(R.string.music_up_next), style = WpType.GroupHeader, color = Wp.palette.subtle)
                ui.nextTitles.forEach { WpText(it, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 3.dp)) }
            }
            Spacer(Modifier.height(16.dp))
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Shuffle, stringResource(if (ui.shuffle) R.string.music_shuffle_on else R.string.music_shuffle)) { controller.toggleShuffle() },
                AppBarButton(
                    if (ui.repeatMode == Player.REPEAT_MODE_ONE) WpIcons.RepeatOne else WpIcons.Repeat,
                    stringResource(
                        when (ui.repeatMode) {
                            Player.REPEAT_MODE_ALL -> R.string.music_repeat_all
                            Player.REPEAT_MODE_ONE -> R.string.music_repeat_one
                            else -> R.string.music_repeat
                        },
                    ),
                ) { controller.cycleRepeat() },
            ),
        )
    }
}

@Composable
private fun Progress(ui: PlayerUi, controller: MusicController) {
    val fraction = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    var widthPx by remember { mutableFloatStateOf(1f) }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .onGloballyPositioned { widthPx = it.size.width.toFloat() }
                .pointerInput(ui.durationMs) {
                    detectTapGestures { controller.seekTo((it.x / widthPx * ui.durationMs).toLong()) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Wp.palette.raised))
            Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.001f)).height(4.dp).background(Wp.accent))
        }
        Row(Modifier.fillMaxWidth()) {
            WpText(WpFormat.duration(ui.positionMs), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.weight(1f))
            WpText(WpFormat.duration(ui.durationMs), style = WpType.Small, color = Wp.palette.subtle)
        }
    }
}
