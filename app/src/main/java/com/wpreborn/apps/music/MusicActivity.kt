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
import com.wpreborn.core.ui.WpMessageBox
import androidx.compose.foundation.border
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
    data object Equalizer : MusicPage
    /** The classic collection pivot opened from the new interface, on one of its pages. */
    data class Library(val page: Int) : MusicPage
    data object Search : MusicPage
    data object Favorites : MusicPage
    data object Mixes : MusicPage
    data object PlayMeIntro : MusicPage
}

/**
 * Music, modelled on Xbox Music for Windows Phone 8.1: a "collection" pivot (artists,
 * albums, songs, playlists), detail pages and the now playing screen. Playback runs in
 * [com.wpreborn.services.music.MusicService] with lock screen and notification controls.
 */
class MusicActivity : WpActivity() {

    /** Bumped when the Playing Now bar (or the notification) asks for the player again. */
    private var nowPlayingRequests by mutableStateOf(0)

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
        // Deleted songs leave the queue too, so the player never points at a missing file.
        val songActions = rememberSongActions(onDeleted = { controller.removeSongs(it) }) { reload++ }

        val favoriteIds by remember { com.wpreborn.data.media.Favorites.get(context) }.ids.collectAsState()
        val mixes = remember(collection, favoriteIds) {
            collection?.let { MixBuilder.build(context, it, com.wpreborn.data.media.PlayStats.get(context).counts(), favoriteIds) }.orEmpty()
        }
        val scope = rememberCoroutineScope()
        val playMeLabel = stringResource(R.string.mix_play_me).uppercase()
        val nav = rememberNavigator<MusicPage>(if (intent?.getBooleanExtra(EXTRA_NOW_PLAYING, false) == true) MusicPage.NowPlaying else MusicPage.Collection)
        // Back out of a page whose artist, album or playlist is gone (its songs were deleted);
        // only the page on top acts, so one deletion never pops two pages.
        fun gone(page: MusicPage) {
            if (nav.current.page == page && !nav.pop()) nav.replace(MusicPage.Collection)
        }
        // Nothing left to play (the playing song or the whole queue was deleted): leave the
        // player instead of showing an empty screen.
        var hadMedia by remember { mutableStateOf(false) }
        LaunchedEffect(ui.hasMedia) {
            if (ui.hasMedia) {
                hadMedia = true
            } else if (hadMedia) {
                hadMedia = false
                if (nav.current.page == MusicPage.NowPlaying && !nav.pop()) nav.replace(MusicPage.Collection)
            }
        }
        // Opened by Android or another app: a song to play, or a voice "play ...".
        LaunchedEffect(Unit) { if (incoming == null) incoming = intent?.takeIf { it.action == Intent.ACTION_VIEW || it.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH } }
        val inc = incoming
        LaunchedEffect(inc, collection) {
            val i = inc ?: return@LaunchedEffect
            val col = collection
            when (i.action) {
                Intent.ACTION_VIEW -> {
                    val uri = i.data ?: run { incoming = null; return@LaunchedEffect }
                    val id = if (uri.authority == "media") runCatching { android.content.ContentUris.parseId(uri) }.getOrNull() else null
                    // A song of the library: wait for it to load, then play it (the radio follows).
                    if (id != null && col == null && perm.granted) return@LaunchedEffect
                    val song = id?.let { sid -> col?.songs?.firstOrNull { it.id == sid } }
                    if (song != null) {
                        controller.playSongs(listOf(song))
                    } else {
                        val name = runCatching {
                            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
                        controller.playUri(uri, name.substringBeforeLast('.'))
                    }
                    incoming = null
                    if (nav.current.page != MusicPage.NowPlaying) nav.navigate(MusicPage.NowPlaying)
                }
                android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                    if (col == null) return@LaunchedEffect
                    val q = i.getStringExtra(android.app.SearchManager.QUERY).orEmpty().trim().lowercase()
                    val list = if (q.isEmpty()) {
                        col.songs.shuffled()
                    } else {
                        (col.songs.filter { it.title.lowercase().contains(q) || it.album.lowercase().contains(q) } +
                            col.songs.filter { it.artist.lowercase().contains(q) }).distinct()
                    }
                    incoming = null
                    if (list.isNotEmpty()) {
                        controller.playSongs(list)
                        if (nav.current.page != MusicPage.NowPlaying) nav.navigate(MusicPage.NowPlaying)
                    }
                }
                else -> incoming = null
            }
        }
        LaunchedEffect(nowPlayingRequests) {
            if (nowPlayingRequests > 0 && nav.current.page != MusicPage.NowPlaying) nav.navigate(MusicPage.NowPlaying)
        }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                WpNavHost(nav) { page ->
                    val col = collection
                    when (page) {
                        MusicPage.Collection -> if (perm.granted) {
                            MixHome(
                                col, mixes,
                                onNowPlaying = { nav.navigate(MusicPage.NowPlaying) },
                                onLibrary = { nav.navigate(MusicPage.Library(it)) },
                                onSearch = { nav.navigate(MusicPage.Search) },
                                onMixes = { nav.navigate(MusicPage.Mixes) },
                                onFavorites = { nav.navigate(MusicPage.Favorites) },
                                onPlayMeIntro = { nav.navigate(MusicPage.PlayMeIntro) },
                                onChanged = { reload++ },
                            )
                        } else {
                            CollectionPage(perm.granted, col, nav, controller, ui, songActions, onReload = { reload++ }) {
                                PermissionGate(perm, stringResource(R.string.perm_music), Modifier.padding(start = 20.dp)) {}
                            }
                        }
                        is MusicPage.Library -> CollectionPage(perm.granted, col, nav, controller, ui, songActions, onReload = { reload++ }, initialPage = page.page) {
                            PermissionGate(perm, stringResource(R.string.perm_music), Modifier.padding(start = 20.dp)) {}
                        }
                        // When the artist, album or playlist no longer exists (its last song was
                        // deleted or retagged) the page goes back instead of staying blank.
                        is MusicPage.ArtistPage -> col?.artists?.firstOrNull { it.name == page.name }?.let { a ->
                            MixArtistPage(
                                col!!, a,
                                onAlbum = { nav.navigate(MusicPage.AlbumPage(it)) },
                                onArtist = { nav.navigate(MusicPage.ArtistPage(it)) },
                                onPlay = { list, i, source -> controller.playSongs(list, i, source = source); nav.navigate(MusicPage.NowPlaying) },
                            )
                        }
                            ?: GoneBack(col != null) { gone(page) }
                        is MusicPage.AlbumPage -> col?.albums?.firstOrNull { it.id == page.id }?.let { AlbumDetail(it, controller, songActions) }
                            ?: GoneBack(col != null) { gone(page) }
                        is MusicPage.PlaylistPage -> col?.playlists?.firstOrNull { it.id == page.id }?.let { p ->
                            PlaylistDetail(p, col.songsOf(p), controller, onDeleted = { reload++; nav.pop() }, onChanged = { reload++ }, allSongs = col.songs)
                        } ?: GoneBack(col != null) { gone(page) }
                        MusicPage.NowPlaying -> MixNowPlaying(
                            onEqualizer = { nav.navigate(MusicPage.Equalizer) },
                            onChanged = { reload++ },
                            onAlbum = { id -> col?.songs?.firstOrNull { it.id.toString() == id }?.let { s -> col.albums.firstOrNull { a -> a.songs.any { it.id == s.id } }?.let { nav.navigate(MusicPage.AlbumPage(it.id)) } } },
                            onArtist = { id -> col?.songs?.firstOrNull { it.id.toString() == id }?.artist?.takeIf { it.isNotBlank() }?.let { nav.navigate(MusicPage.ArtistPage(it)) } },
                        )
                        MusicPage.Equalizer -> EqualizerScreen()
                        MusicPage.NewPlaylist -> NewPlaylistPage(library) { reload++; nav.pop() }
                        MusicPage.Favorites -> FavoritesPage(
                            col,
                            onArtist = { nav.navigate(MusicPage.ArtistPage(it)) },
                            onPlay = { list, i -> controller.playSongs(list, i); nav.navigate(MusicPage.NowPlaying) },
                        )
                        MusicPage.Mixes -> MixesPage(
                            mixes,
                            onPlay = { m -> controller.playSongs(m.songs, 0, source = m.title.uppercase()); nav.navigate(MusicPage.NowPlaying) },
                            onSave = { m -> scope.launch { val lib = MusicLibrary(context); val p = lib.createPlaylist(m.title); lib.addToPlaylist(p.id, m.songs.map { it.id }); reload++ } },
                        )
                        MusicPage.PlayMeIntro -> PlayMeIntro(col) {
                            val c = col
                            if (c != null && startPlayMe(context, c, playMeLabel)) nav.replace(MusicPage.NowPlaying)
                        }
                        MusicPage.Search -> SearchPage(
                            col,
                            onPlay = { list, i -> controller.playSongs(list, i); nav.navigate(MusicPage.NowPlaying) },
                            onArtist = { nav.navigate(MusicPage.ArtistPage(it)) },
                            onAlbum = { nav.navigate(MusicPage.AlbumPage(it)) },
                            onPlaylist = { nav.navigate(MusicPage.PlaylistPage(it)) },
                        )
                    }
                }
            }
        }
        // Dialogs last, so they cover the list instead of being covered by it.
        songActions.Dialogs()
        }
    }

    private var resumes by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        resumes++
    }

    /** A song or a "play ..." Android sent (handled once the library is there). */
    private var incoming by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW || intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) incoming = intent
        if (intent.getBooleanExtra(EXTRA_NOW_PLAYING, false)) nowPlayingRequests++
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
    songActions: SongActions,
    onReload: () -> Unit,
    initialPage: Int = 0,
    gate: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val pivot = rememberPivotState(5, initialPage)
    val favorites by remember { com.wpreborn.data.media.Favorites.get(context) }.ids.collectAsState()
    val external by com.wpreborn.data.media.ExternalMediaWatcher.state.collectAsState()
    val mixes = remember(col, favorites) { col?.let { MixBuilder.build(context, it, com.wpreborn.data.media.PlayStats.get(context).counts(), favorites) }.orEmpty() }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    var addTo by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()
    // Multi-selection (like the 8.1 Music app's "select"): the pivot page it belongs to
    // (2 albums, 3 songs, 4 playlists, -1 none) and the chosen ids.
    var selKind by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmPlaylists by remember { mutableStateOf(false) }
    var addMany by remember { mutableStateOf<List<Song>?>(null) }
    var creatingMix by remember { mutableStateOf(false) }
    val playMeTitle = stringResource(R.string.mix_play_me).uppercase()
    val selecting = selKind >= 0
    fun clearSel() {
        selKind = -1
        selected = emptySet()
    }
    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }
    androidx.activity.compose.BackHandler(enabled = selecting) { clearSel() }
    androidx.compose.runtime.LaunchedEffect(pivot.selected) { if (selKind >= 0 && pivot.selected != selKind) clearSel() }
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
                ),
                state = pivot,
                modifier = Modifier.weight(1f),
            ) { page ->
                // Pages: mixes, artists, albums, songs, playlists
                val index = page - 1
                when {
                    !granted -> gate()
                    col == null -> LoadingDots(Modifier.padding(top = 24.dp))
                    col.songs.isEmpty() && index != 3 -> CenteredMessage(stringResource(R.string.music_empty), Modifier.padding(start = 20.dp))
                    page == 0 -> MixesGrid(mixes) { m ->
                        if (m.id == "playme") {
                            // The same "play me" as the new interface (favourite artists, votes).
                            if (com.wpreborn.data.media.FavoriteArtists.get(context).artists.value.isEmpty() &&
                                com.wpreborn.data.media.PlayStats.get(context).counts().isEmpty()
                            ) {
                                nav.navigate(MusicPage.PlayMeIntro)
                            } else if (startPlayMe(context, col, playMeTitle)) {
                                nav.navigate(MusicPage.NowPlaying)
                            }
                        } else {
                            controller.playSongs(m.songs, 0, source = m.title.uppercase())
                            nav.navigate(MusicPage.NowPlaying)
                        }
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
                    index == 1 -> AlbumGrid(
                        col.albums,
                        selected = if (selKind == 2) selected else null,
                        onToggle = { toggle(it) },
                        onSelect = { selKind = 2; selected = setOf(it) },
                    ) { nav.navigate(MusicPage.AlbumPage(it.id)) }
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
                                checked = if (selKind == 3) s.id.toString() in selected else null,
                                onLongClick = if (selKind == 3) null else ({ menuY = bottom - top; menuSong = s }),
                            ) {
                                if (selKind == 3) {
                                    toggle(s.id.toString())
                                } else {
                                    controller.playSongs(col.songs, i)
                                    nav.navigate(MusicPage.NowPlaying)
                                }
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
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .slideInStaggered(i)
                                        .wpCombinedClickable(onLongClick = if (selKind == 4) null else ({ selKind = 4; selected = setOf(p.id) })) {
                                            if (selKind == 4) toggle(p.id) else nav.navigate(MusicPage.PlaylistPage(p.id))
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (selKind == 4) {
                                        SelectBox(p.id in selected)
                                        Spacer(Modifier.width(14.dp))
                                    }
                                    Column {
                                        WpText(p.name.lowercase(), style = WpType.ExtraLarge, maxLines = 1)
                                        WpText(context.resources.getQuantityString(R.plurals.songs_count, p.songIds.size, p.songIds.size), style = WpType.Subtle, color = Wp.palette.subtle)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val page = pivot.selected
            val userPlaylists = col?.playlists.orEmpty().filterNot { it.system }
            WpAppBar(
                buttons = if (selecting && col != null) buildList {
                    add(
                        AppBarButton(WpIcons.Delete, stringResource(R.string.delete), enabled = selected.isNotEmpty()) {
                            when (selKind) {
                                3 -> songActions.deleteMany(col.songs.filter { it.id.toString() in selected }) { clearSel() }
                                2 -> songActions.deleteMany(col.albums.filter { it.id.toString() in selected }.flatMap { it.songs }) { clearSel() }
                                4 -> confirmPlaylists = true
                            }
                        },
                    )
                    if (selKind == 3 || selKind == 2) {
                        add(
                            AppBarButton(WpIcons.Edit, stringResource(R.string.music_edit_tags), enabled = selected.isNotEmpty()) {
                                val list = if (selKind == 3) col.songs.filter { it.id.toString() in selected }
                                else col.albums.filter { it.id.toString() in selected }.flatMap { it.songs }
                                songActions.editTags(list)
                                clearSel()
                            },
                        )
                    }
                    if (selKind == 3) {
                        add(
                            AppBarButton(WpIcons.Playlist, stringResource(R.string.music_add_queue), enabled = selected.isNotEmpty()) {
                                controller.addToQueue(col.songs.filter { it.id.toString() in selected })
                                clearSel()
                            },
                        )
                    }
                    if (selKind == 3) {
                        add(
                            AppBarButton(WpIcons.Add, stringResource(R.string.music_add_to_playlist), enabled = selected.isNotEmpty() && userPlaylists.isNotEmpty()) {
                                addMany = col.songs.filter { it.id.toString() in selected }
                            },
                        )
                    }
                    add(
                        AppBarButton(WpIcons.SelectAll, stringResource(R.string.music_select_all)) {
                            val all = when (selKind) {
                                3 -> col.songs.map { it.id.toString() }
                                2 -> col.albums.map { it.id.toString() }
                                else -> col.playlists.map { it.id }
                            }.toSet()
                            selected = if (selected.size == all.size) emptySet() else all
                        },
                    )
                    add(AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { clearSel() })
                } else if (granted && col != null) {
                    listOf(AppBarButton(WpIcons.Search, stringResource(R.string.music_search)) { nav.navigate(MusicPage.Search) }) +
                        if (page in 2..4) listOf(AppBarButton(WpIcons.SelectAll, stringResource(R.string.music_select)) { selKind = page; selected = emptySet() }) else emptyList()
                } else {
                    emptyList()
                },
                menuItems = listOf(
                    AppBarMenuItem(stringResource(R.string.refresh)) { onReload() },
                    AppBarMenuItem(stringResource(R.string.mix_create)) { creatingMix = true },
                    AppBarMenuItem(stringResource(R.string.mix_favorites)) { nav.navigate(MusicPage.Favorites) },
                ) + listOf(
                    AppBarMenuItem(stringResource(if (MusicLibrary(context).hideUnknown) R.string.music_show_unknown else R.string.music_hide_unknown)) {
                        val lib = MusicLibrary(context)
                        lib.hideUnknown = !lib.hideUnknown
                        onReload()
                    },
                ),
            )
        }
        menuSong?.let { s ->
            WpContextMenu(
                items = listOf(
                    ContextMenuItem(stringResource(R.string.music_play)) { controller.playSongs(listOf(s)) },
                    ContextMenuItem(stringResource(R.string.music_play_next)) { controller.playNext(listOf(s)) },
                    ContextMenuItem(stringResource(R.string.music_add_queue)) { controller.addToQueue(listOf(s)) },
                    ContextMenuItem(stringResource(R.string.music_add_to_playlist), enabled = !col?.playlists.orEmpty().none { !it.system }) { addTo = s },
                    ContextMenuItem(stringResource(R.string.music_rename)) { songActions.rename(s) },
                    ContextMenuItem(stringResource(R.string.music_edit_tags)) { songActions.editTags(listOf(s)) },
                    ContextMenuItem(stringResource(R.string.delete)) { songActions.delete(s) },
                    ContextMenuItem(stringResource(R.string.music_select)) { selKind = 3; selected = setOf(s.id.toString()) },
                ),
                anchorY = menuY,
                onDismiss = { menuSong = null },
            )
        }
        addMany?.let { list ->
            WpContextMenu(
                items = col?.playlists.orEmpty().filterNot { it.system }.map { p ->
                    ContextMenuItem(p.name) {
                        scope.launch {
                            MusicLibrary(context).addToPlaylist(p.id, list.map { it.id })
                            clearSel()
                            onReload()
                        }
                    }
                },
                anchorY = menuY,
                onDismiss = { addMany = null },
            )
        }
        if (creatingMix && col != null) {
            ArtistChooser(col, limit = 3, title = stringResource(R.string.mix_create)) { chosen ->
                creatingMix = false
                if (chosen.isNotEmpty()) {
                    val list = com.wpreborn.data.media.PlayMe.fromArtists(col, chosen, com.wpreborn.data.media.Votes.get(context).votes.value)
                    if (list.isNotEmpty()) { controller.playSongs(list, 0, source = chosen.joinToString(" + ").uppercase()); nav.navigate(MusicPage.NowPlaying) }
                }
            }
        }
                if (confirmPlaylists) {
            WpMessageBox(
                title = stringResource(R.string.music_delete_playlists_title),
                message = context.resources.getQuantityString(R.plurals.music_delete_playlists_message, selected.size, selected.size),
                confirm = stringResource(R.string.delete),
                onConfirm = {
                    confirmPlaylists = false
                    val ids = selected
                    scope.launch {
                        MusicLibrary(context).deletePlaylists(ids)
                        clearSel()
                        onReload()
                    }
                },
                dismiss = stringResource(R.string.cancel),
                onDismiss = { confirmPlaylists = false },
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
private fun SongRow(s: Song, modifier: Modifier, current: Boolean = false, checked: Boolean? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .wpCombinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            SelectBox(checked)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            WpText(s.title, style = WpType.Large, color = if (current) Wp.accent else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(s.artist, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current) WpIcon(WpIcons.MusicNote, tint = Wp.accent, size = 18.dp, modifier = Modifier.padding(horizontal = 6.dp))
        WpText(WpFormat.duration(s.durationMs), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    selected: Set<String>? = null,
    onToggle: (String) -> Unit = {},
    onSelect: (String) -> Unit = {},
    onOpen: (Album) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(albums, key = { _, a -> a.id }) { i, a ->
            val id = a.id.toString()
            Column(
                Modifier.slideInStaggered(i / 2).wpCombinedClickable(onLongClick = if (selected != null) null else ({ onSelect(id) })) {
                    if (selected != null) onToggle(id) else onOpen(a)
                },
            ) {
                Box {
                    AlbumCover(a.artUri, Modifier.fillMaxWidth().aspectRatio(1f))
                    if (selected != null) {
                        if (id in selected) Box(Modifier.matchParentSize().border(3.dp, Wp.accent))
                        SelectBox(id in selected, Modifier.align(Alignment.TopEnd).padding(6.dp), onImage = true)
                    }
                }
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
private fun ArtistDetail(artist: Artist, nav: WpNavigator<MusicPage>, controller: MusicController, col: MusicCollection) {
    val context = LocalContext.current
    val favStore = remember { com.wpreborn.data.media.FavoriteArtists.get(context) }
    val favs by favStore.artists.collectAsState()
    val bio by androidx.compose.runtime.produceState<ArtistBio?>(null, artist.name) { value = ArtistBios.load(context, artist.name) }
    val plays = remember { com.wpreborn.data.media.PlayStats.get(context).counts() }
    // Xbox Music's artist page: a pivot of albums, songs and about.
    Column(Modifier.fillMaxSize()) {
        WpPivot(
            title = artist.name,
            headers = listOf(stringResource(R.string.music_albums), stringResource(R.string.music_songs), stringResource(R.string.artist_about)),
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
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
                }
                1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
                    itemsIndexed(artist.songs, key = { _, s -> "s${s.id}" }) { i, s ->
                        SongRow(s, Modifier.slideInStaggered(i)) { controller.playSongs(artist.songs, i); nav.navigate(MusicPage.NowPlaying) }
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    val photo by com.wpreborn.apps.news.rememberWebImage(bio?.image, 720)
                    photo?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxWidth().aspectRatio((it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.62f, 2.2f)), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter) }
                    val b = bio
                    if (b != null) {
                        WpText(b.text, style = WpType.Normal.copy(lineHeight = 24.sp), modifier = Modifier.padding(top = 10.dp))
                        WpText(stringResource(R.string.artist_source), style = WpType.Small, color = Wp.accent, modifier = Modifier.padding(top = 8.dp).wpClickable {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(b.page)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        })
                    } else {
                        WpText(stringResource(R.string.artist_no_bio), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 10.dp))
                    }
                    val top = artist.songs.maxByOrNull { plays[it.id] ?: 0 }?.takeIf { (plays[it.id] ?: 0) > 0 }
                    WpText(stringResource(R.string.music_songs).lowercase() + ": " + artist.songs.size + "   " + stringResource(R.string.music_albums).lowercase() + ": " + artist.albums.size, style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 16.dp))
                    top?.let { WpText(stringResource(R.string.artist_most_played) + ": " + it.title, style = WpType.Subtle, color = Wp.palette.subtle) }
                }
            }
        }
        val fav = artist.name in favs
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Play, stringResource(R.string.music_play_artist)) { controller.playSongs(artist.songs, source = artist.name.uppercase()); nav.navigate(MusicPage.NowPlaying) },
                AppBarButton(WpIcons.Shuffle, stringResource(R.string.artist_play_mix)) {
                    val list = com.wpreborn.data.media.PlayMe.fromArtists(col, listOf(artist.name), com.wpreborn.data.media.Votes.get(context).votes.value)
                    if (list.isNotEmpty()) { controller.playSongs(list, source = artist.name.uppercase()); nav.navigate(MusicPage.NowPlaying) }
                },
                AppBarButton(if (fav) WpIcons.Star else WpIcons.StarOutline, stringResource(if (fav) R.string.mix_remove_artist else R.string.mix_add_artist)) {
                    if (fav) favStore.remove(listOf(artist.name)) else favStore.add(listOf(artist.name))
                },
            ),
        )
    }
}

@Composable
private fun AlbumDetail(album: Album, controller: MusicController, songActions: SongActions) {
    var menu by remember { mutableStateOf<Song?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { top = it.boundsInWindow().top }) {
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
                var bottom by remember { mutableFloatStateOf(0f) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .slideInStaggered(i)
                        .onGloballyPositioned { bottom = it.boundsInWindow().bottom }
                        .wpCombinedClickable(onLongClick = { menuY = bottom - top; menu = s }) { controller.playSongs(album.songs, i) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WpText(s.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    WpText(WpFormat.duration(s.durationMs), style = WpType.Subtle, color = Wp.palette.subtle)
                }
            }
        }
    }
    menu?.let { song ->
        WpContextMenu(
            items = listOf(
                ContextMenuItem(stringResource(R.string.music_play)) { controller.playSongs(listOf(song)) },
                ContextMenuItem(stringResource(R.string.music_play_next)) { controller.playNext(listOf(song)) },
                ContextMenuItem(stringResource(R.string.music_add_queue)) { controller.addToQueue(listOf(song)) },
                ContextMenuItem(stringResource(R.string.music_rename)) { songActions.rename(song) },
                ContextMenuItem(stringResource(R.string.music_edit_tags)) { songActions.editTags(listOf(song)) },
                ContextMenuItem(stringResource(R.string.delete)) { songActions.delete(song) },
            ),
            anchorY = menuY,
            onDismiss = { menu = null },
        )
    }
    }
}

@Composable
private fun PlaylistDetail(p: Playlist, songs: List<Song>, controller: MusicController, onDeleted: () -> Unit, onChanged: () -> Unit, allSongs: List<Song> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Positions in the playlist (a song can be in it twice), for "remove".
    val entries = remember(p, songs) { val byId = songs.associateBy { it.id }; p.songIds.mapIndexedNotNull { i, id -> byId[id]?.let { i to it } } }
    var sel by remember(p.id) { mutableStateOf<Set<Int>?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = sel != null) { sel = null }
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.music_playlists), p.name)
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            if (songs.isEmpty()) item {
                CenteredMessage(stringResource(R.string.music_playlist_empty))
                if (!p.system) {
                    WpText(
                        stringResource(R.string.music_add_songs),
                        style = WpType.Large,
                        color = Wp.accent,
                        modifier = Modifier.padding(top = 12.dp).wpClickable { picking = true }.padding(vertical = 6.dp),
                    )
                }
            }
            itemsIndexed(entries, key = { _, e -> "${e.first}-${e.second.id}" }) { i, (pos, s) ->
                SongRow(
                    s,
                    Modifier.slideInStaggered(i),
                    checked = sel?.let { pos in it },
                    onLongClick = if (p.system || sel != null) null else ({ sel = setOf(pos) }),
                ) {
                    val cur = sel
                    if (cur != null) {
                        sel = if (pos in cur) cur - pos else cur + pos
                    } else {
                        controller.playSongs(entries.map { it.second }, i)
                    }
                }
            }
        }
        val selection = sel
        if (selection != null) {
            WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Delete, stringResource(R.string.music_remove_from_playlist), enabled = selection.isNotEmpty()) {
                        scope.launch {
                            MusicLibrary(context).removeFromPlaylist(p.id, selection)
                            sel = null
                            onChanged()
                        }
                    },
                    AppBarButton(WpIcons.SelectAll, stringResource(R.string.music_select_all)) {
                        sel = if (selection.size == entries.size) emptySet() else entries.map { it.first }.toSet()
                    },
                    AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { sel = null },
                ),
            )
            return@Column
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Play, stringResource(R.string.music_play), enabled = songs.isNotEmpty()) { controller.playSongs(songs) },
                AppBarButton(WpIcons.Shuffle, stringResource(R.string.music_shuffle), enabled = songs.isNotEmpty()) { controller.playSongs(songs.shuffled(), shuffle = true) },
            ) + if (!p.system) listOf(
                AppBarButton(WpIcons.Add, stringResource(R.string.music_add_songs)) { picking = true },
                AppBarButton(WpIcons.SelectAll, stringResource(R.string.music_select), enabled = songs.isNotEmpty()) { sel = emptySet() },
                AppBarButton(WpIcons.Delete, stringResource(R.string.delete)) {
                    scope.launch {
                        MusicLibrary(context).deletePlaylist(p.id)
                        onDeleted()
                    }
                },
            ) else emptyList(),
            menuItems = if (!p.system) listOf(
                com.wpreborn.core.ui.AppBarMenuItem(stringResource(R.string.music_rename_playlist)) { renaming = true },
            ) else emptyList(),
        )
        if (picking) {
            SongPicker(allSongs, already = p.songIds.toSet(), onDismiss = { picking = false }) { ids ->
                picking = false
                if (ids.isNotEmpty()) scope.launch {
                    MusicLibrary(context).addToPlaylist(p.id, ids)
                    onChanged()
                }
            }
        }
        if (renaming) {
            NameDialog(stringResource(R.string.music_rename_playlist), p.name, onDismiss = { renaming = false }) { name ->
                renaming = false
                scope.launch {
                    MusicLibrary(context).renamePlaylist(p.id, name)
                    onChanged()
                }
            }
        }
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


/** Windows Phone check box for multi-selection (accent fill and a check when ticked). */
@Composable
private fun SelectBox(checked: Boolean, modifier: Modifier = Modifier, onImage: Boolean = false) {
    Box(
        modifier
            .size(if (onImage) 24.dp else 26.dp)
            .background(if (checked) Wp.accent else if (onImage) Color.Black.copy(alpha = 0.35f) else Color.Transparent)
            .border(2.dp, if (onImage) Color.White else Wp.palette.foreground),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) WpIcon(WpIcons.Check, tint = Color.White, size = 18.dp)
    }
}

/** A page whose artist / album / playlist is gone: go back (once the library is loaded). */
@Composable
private fun GoneBack(loaded: Boolean, back: () -> Unit) {
    if (loaded) androidx.compose.runtime.LaunchedEffect(Unit) { back() }
}

/** A name box in a Windows Phone dialog (playlist names). */
@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).wpClickable(onClick = onDismiss)) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Wp.palette.chrome).wpClickable { }.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 20.dp)) {
            WpText(title, style = WpType.Large)
            WpTextBox(name, { name = it }, Modifier.fillMaxWidth().padding(top = 12.dp))
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WpButton(stringResource(R.string.save), Modifier.weight(1f)) { if (name.isNotBlank()) onSave(name.trim()) }
                WpButton(stringResource(R.string.cancel), Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

/** Pick songs to add to a playlist: search box and check boxes, like the 8.1 "add songs". */
@Composable
private fun SongPicker(all: List<Song>, already: Set<Long>, onDismiss: () -> Unit, onDone: (List<Long>) -> Unit) {
    var q by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(listOf<Long>()) }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val shown = remember(all, q) {
        val n = q.trim().lowercase()
        all.filter { n.isEmpty() || it.title.lowercase().contains(n) || it.artist.lowercase().contains(n) || it.album.lowercase().contains(n) }
    }
    Column(Modifier.fillMaxSize().background(Wp.palette.background)) {
        Column(Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp)) {
            WpText(stringResource(R.string.music_title).uppercase(), style = WpType.AppTitle)
            WpText(stringResource(R.string.music_add_songs), style = WpType.Header, maxLines = 1, softWrap = false)
            WpTextBox(q, { q = it }, Modifier.fillMaxWidth().padding(top = 4.dp), placeholder = stringResource(R.string.music_search_hint))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            items(shown.size, key = { shown[it].id }) { i ->
                val s = shown[i]
                val on = s.id in chosen
                Row(Modifier.fillMaxWidth().wpClickable { chosen = if (on) chosen - s.id else chosen + s.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectBox(on)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        WpText(s.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (s.id in already) Wp.palette.subtle else Wp.palette.foreground)
                        WpText(s.artist, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.music_add_songs), enabled = chosen.isNotEmpty()) { onDone(chosen) },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel), onClick = onDismiss),
            ),
        )
    }
}
