package com.wpreborn.apps.music

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import com.wpreborn.core.motion.tilt
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.apps.common.LocalChrome
import com.wpreborn.core.icons.Glyphs
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.rememberThumbnail
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.Favorites
import com.wpreborn.data.media.MusicCollection
import com.wpreborn.data.media.Playback
import com.wpreborn.data.media.Song
import com.wpreborn.data.media.rememberPlayback
import com.wpreborn.data.store.AppManager
import com.wpreborn.data.apps.BuiltInApp
import com.wpreborn.services.music.MusicController
import kotlinx.coroutines.launch

/** Music app versions from this one on offer the new interface (released through the Store). */
const val MIX_UI_VERSION = 31

/** Which interface the Music app shows: the classic pivot or the new "mix" one (beta). */
object MusicUi {
    fun available(context: Context) = AppManager.get(context).state(BuiltInApp.Music).versionCode >= MIX_UI_VERSION

    fun isMix(context: Context): Boolean {
        if (!available(context)) return false
        val p = context.getSharedPreferences("music", Context.MODE_PRIVATE)
        // After the update the new interface comes on once; then it's the user's choice.
        if (!p.contains("mixUi")) p.edit().putBoolean("mixUi", true).apply()
        return p.getBoolean("mixUi", true)
    }

    fun setMix(context: Context, on: Boolean) =
        context.getSharedPreferences("music", Context.MODE_PRIVATE).edit().putBoolean("mixUi", on).apply()
}

private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** The picture of what's playing (or a song), in black and white if asked. */
@Composable
private fun Art(playback: Playback?, fallback: Song?, modifier: Modifier, grey: Boolean, size: Int = 512) {
    val fromBitmap = remember(playback?.artBitmap) { playback?.artBitmap?.asImageBitmap() }
    val uri = playback?.artUri ?: fallback?.albumArtUri
    val loaded by rememberThumbnail(if (fromBitmap == null) uri else null, size)
    val img: ImageBitmap? = fromBitmap ?: loaded
    Box(modifier.clipToBounds()) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if (grey) GREY else null)
    }
}

/**
 * The new home of the Music app (beta), in the style of Nokia MixRadio on Windows Phone 8.1:
 * a large title, then a canvas of tiles on a grid (like Start: every tile takes whole cells,
 * nothing overlaps) that scrolls sideways - the song playing with its time in big numerals,
 * the "play me" tile that starts your own radio, mixes between coloured squares, and the
 * library - over the black-and-white picture of what's playing.
 */
@Composable
fun MixHome(
    col: MusicCollection?,
    mixes: List<Mix>,
    onNowPlaying: () -> Unit,
    onLibrary: (Int) -> Unit,
    onClassic: () -> Unit,
    onChanged: () -> Unit = {},
    onSearch: () -> Unit = {},
    onMixes: () -> Unit = {},
    onFavorites: () -> Unit = {},
    onPlayMeIntro: () -> Unit = {},
) {
    val context = LocalContext.current
    val controller = remember { MusicController.get(context) }
    val playback = rememberPlayback()
    val accent = Wp.accent
    val recent = remember(col) { col?.songs?.sortedByDescending { it.dateAdded }?.take(40).orEmpty() }
    val bgSong = recent.firstOrNull() ?: col?.songs?.firstOrNull()

    val canvasScroll = rememberScrollState()
    var homeMenu by remember { mutableStateOf(false) }
    var createMix by remember { mutableStateOf(false) }
    val playMeLabel = stringResource(R.string.mix_play_me).uppercase()
    var newPlaylist by remember { mutableStateOf(false) }

    fun playMe() {
        val c = col ?: return
        if (c.songs.isEmpty()) return
        if (com.wpreborn.data.media.FavoriteArtists.get(context).artists.value.isEmpty() &&
            com.wpreborn.data.media.PlayStats.get(context).counts().isEmpty()
        ) {
            onPlayMeIntro()
            return
        }
        if (startPlayMe(context, c, playMeLabel)) onNowPlaying()
    }

    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        // Background: the picture of what's playing, black and white and dimmed.
        // Panorama parallax: the background moves slower than the tiles, as on Windows 8.
        Art(playback, bgSong, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f; translationX = size.width * 0.12f - canvasScroll.value * 0.18f }, grey = true, size = 720)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        Column(Modifier.fillMaxSize()) {
            // Header: the title in two weights and a round button under it, as on MixRadio.
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 6.dp, end = 18.dp)) {
                WpText("ZUNEX", style = WpType.Header.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false)
                WpText("Mix", style = WpType.Header.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Light), color = Color.White, maxLines = 1, softWrap = false)
            }
            // Round buttons under the title: search, and the menu.
            Row(Modifier.padding(start = 18.dp, top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(30.dp).border(2.dp, Color.White, CircleShape).wpClickable(onClick = onSearch),
                    contentAlignment = Alignment.Center,
                ) { WpIcon(WpIcons.Search, tint = Color.White, size = 16.dp) }
                Box(
                    Modifier.size(30.dp).border(2.dp, Color.White, CircleShape).wpClickable { homeMenu = true },
                    contentAlignment = Alignment.Center,
                ) { WpIcon(WpIcons.More, tint = Color.White, size = 16.dp) }
            }

            // The canvas, measured on MixRadio: a unit of ~18.5% of the screen width. First
            // group: the playing cover (2.6 units) with its time, "play me" (2 units) stepped in
            // under it, a small square stepped again. Then a column alternating a small cover and
            // a large mix (2 units, caption on an accent band), and the library peeking in.
            // One colour only - the accent - for every tile that isn't a picture.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val g = 6.dp
                val labelSpace = 64.dp
                val u = minOf(maxWidth * 0.18f, (maxHeight - labelSpace) / 6.4f).coerceAtLeast(48.dp)
                val art = u * 2.6f
                val big = u * 2f
                val small = u * 1.15f
                val xB = art + g * 1.8f
                val xC = xB + big + g * 1.8f
                val canvasWidth = xC + big * 2 + g
                val bottom = maxHeight - labelSpace
                Box(Modifier.fillMaxSize().horizontalScroll(canvasScroll).padding(start = 18.dp, end = 18.dp)) {
                    Box(Modifier.width(canvasWidth).fillMaxHeight()) {
                        // What's playing, with its time in huge numerals running off the bottom.
                        Box(Modifier.size(art).slideInStaggered(0).tilt().clipToBounds().background(Color.DarkGray).wpClickable(onClick = onNowPlaying)) {
                            Art(playback, bgSong, Modifier.fillMaxSize(), grey = false)
                            WpText(
                                if (playback != null) fmt(playback.positionMs) else "0:00",
                                style = WpType.Header.copy(fontSize = (art.value * 0.5f).sp, lineHeight = (art.value * 0.5f).sp, fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = art * 0.13f),
                            )
                        }
                        // "play me"
                        Box(Modifier.offset(art - big, art + g).size(big).slideInStaggered(1).tilt().background(accent).wpClickable { playMe() }.padding(8.dp)) {
                            WpIcon(WpIcons.Play, tint = Color.White, size = big * 0.45f, modifier = Modifier.align(Alignment.Center).offset(y = (-6).dp))
                            WpText(stringResource(R.string.mix_play_me).lowercase(), style = WpType.Normal.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                        }
                        // Small square stepped once more: shuffle everything.
                        Box(
                            Modifier.offset(art - small, art + big + g * 2).size(small).slideInStaggered(2).tilt().background(accent.copy(alpha = 0.7f)).wpClickable {
                                col?.songs?.takeIf { it.isNotEmpty() }?.let { controller.playSongs(it.shuffled(), 0, shuffle = true); onNowPlaying() }
                            },
                            contentAlignment = Alignment.Center,
                        ) { WpIcon(WpIcons.Shuffle, tint = Color.White, size = small * 0.38f) }

                        // Second column: small cover, large mix, small cover, large mix... down the screen.
                        var y = 0.dp
                        var i = 0
                        var smallTurn = true
                        while (y + (if (smallTurn) small else big) <= bottom && i < 12) {
                            val m = mixes.getOrNull(i)
                            if (smallTurn) {
                                SmallSquare(m, accent, Modifier.offset(xB, y).size(small).slideInStaggered(3 + i / 2).tilt()) { mm -> controller.playSongs(mm.songs, 0); onNowPlaying() }
                                y += small + g
                            } else {
                                if (m != null) {
                                    MixTile(m, accent, Modifier.offset(xB, y).size(big).slideInStaggered(3 + i / 2).tilt()) { controller.playSongs(m.songs, 0); onNowPlaying() }
                                } else {
                                    Box(Modifier.offset(xB, y).size(big).background(accent.copy(alpha = 0.55f)))
                                }
                                y += big + g
                            }
                            i++
                            smallTurn = !smallTurn
                        }

                        // Third section, as on MixRadio: the hub tiles - mixes, create a mix,
                        // favourites (wide), my music, playlists.
                        @Composable
                        fun Hub(x: Dp, y: Dp, w: Dp, label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, plus: Boolean = false, onClick: () -> Unit) {
                            Box(Modifier.offset(x, y).size(w, big).slideInStaggered(7).tilt().background(accent).wpClickable(onClick = onClick).padding(8.dp)) {
                                Box(Modifier.align(Alignment.Center).offset(y = (-8).dp)) {
                                    WpIcon(icon, tint = Color.White, size = big * 0.38f)
                                    if (plus) {
                                        Box(
                                            Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp).size(big * 0.17f).background(Color.White, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { WpIcon(WpIcons.Add, tint = accent, size = big * 0.12f) }
                                    }
                                }
                                WpText(stringResource(label).lowercase(), style = WpType.Normal.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                            }
                        }
                        val x2 = xC + big + g
                        Hub(xC, 0.dp, big, R.string.music_mixes, WpIcons.Music) { onMixes() }
                        Hub(x2, 0.dp, big, R.string.mix_create, WpIcons.Music, plus = true) { createMix = true }
                        Hub(xC, big + g, big * 2 + g, R.string.mix_favorites, WpIcons.Star) { onFavorites() }
                        Hub(xC, (big + g) * 2, big, R.string.mix_my_music, WpIcons.MusicNote) { onLibrary(1) }
                        Hub(x2, (big + g) * 2, big, R.string.music_playlists, WpIcons.Playlist) { onLibrary(4) }
                    }
                }
                // "recently added", at the bottom left like MixRadio's "recently played".
                WpText(
                    stringResource(R.string.mix_recent).lowercase(),
                    style = WpType.Large.copy(fontSize = 22.sp, fontWeight = FontWeight.Light),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp).wpClickable {
                        if (recent.isNotEmpty()) { controller.playSongs(recent, 0); onNowPlaying() }
                    },
                )
            }
        }
        if (homeMenu) {
            MenuOverlay(
                listOf(
                    stringResource(R.string.music_search) to onSearch,
                    stringResource(R.string.mix_create) to { createMix = true; Unit },
                    stringResource(R.string.music_new_playlist) to { newPlaylist = true; Unit },
                    stringResource(R.string.mix_classic) to onClassic,
                ),
            ) { homeMenu = false }
        }
        if (createMix && col != null) {
            ArtistPicker(col) { chosen ->
                createMix = false
                if (chosen.isNotEmpty()) {
                    val list = com.wpreborn.data.media.PlayMe.fromArtists(col, chosen, com.wpreborn.data.media.Votes.get(context).votes.value)
                    if (list.isNotEmpty()) {
                        controller.playSongs(list, 0, source = chosen.joinToString(" + ").uppercase())
                        onNowPlaying()
                    }
                }
            }
        }
        if (newPlaylist) PlaylistPicker(emptyList(), startWithNew = true, onDone = { newPlaylist = false; onChanged() }) { newPlaylist = false }
    }
}

/** A small square of the mix column: the cover of a mix, or a translucent accent square. */
@Composable
private fun SmallSquare(m: Mix?, accent: Color, modifier: Modifier, onPlay: (Mix) -> Unit) {
    // Live: the square flips now and then to another cover of its mix, like MixRadio's tiles.
    val covers = remember(m) { m?.songs?.map { it.albumArtUri }?.distinct()?.take(6).orEmpty() }
    Box(modifier.background(accent.copy(alpha = 0.55f)).then(if (m != null) Modifier.wpClickable { onPlay(m) } else Modifier)) {
        if (covers.isNotEmpty()) {
            com.wpreborn.launcher.start.FlippingCell(covers, 0, (m?.id ?: "").hashCode(), enabled = covers.size > 1) { uri ->
                val art by rememberThumbnail(uri, 256)
                Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.55f))) {
                    art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
        }
    }
}

@Composable
private fun MixTile(m: Mix, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    // Live: the covers of the mix's songs slide up one after another (cycle tile).
    val covers = remember(m) { m.songs.map { it.albumArtUri }.distinct().take(6) }
    Box(modifier.background(accent).wpClickable(onClick = onClick)) {
        if (covers.isNotEmpty()) {
            com.wpreborn.launcher.start.CycleTile(covers, enabled = covers.size > 1, seed = m.id.hashCode()) { uri ->
                val art by rememberThumbnail(uri, 384)
                Box(Modifier.fillMaxSize().background(accent)) {
                    art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
        }
        // Caption on an accent band, as on MixRadio's mix tiles.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(accent.copy(alpha = 0.82f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
            WpText(m.title, style = WpType.Normal.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The player of the new interface (beta), after MixRadio's: the picture in black and white
 * behind everything, the cover with the title, round buttons, an accent progress line and
 * large round play / next buttons.
 */
@Composable
fun MixNowPlaying(onEqualizer: () -> Unit, onChanged: () -> Unit = {}, onAlbum: (String) -> Unit = {}, onArtist: (String) -> Unit = {}) {
    val context = LocalContext.current
    val controller = remember { MusicController.get(context) }
    val playback = rememberPlayback()
    val favorites by remember { Favorites.get(context) }.ids.collectAsState()
    val accent = Wp.accent
    val chrome = LocalChrome.current
    DisposableEffect(chrome) {
        chrome?.hidePlayingNow = true
        onDispose { chrome?.hidePlayingNow = false }
    }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var menu by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    var playlistFor by remember { mutableStateOf<List<Long>?>(null) }
    var sleepMenu by remember { mutableStateOf(false) }
    val votesStore = remember { com.wpreborn.data.media.Votes.get(context) }
    val votes by votesStore.votes.collectAsState()
    val source by controller.source.collectAsState()
    var banner by remember { mutableStateOf<Boolean?>(null) }
    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        Art(playback, null, Modifier.fillMaxSize(), grey = true, size = 720)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        if (playback == null) {
            WpText(stringResource(R.string.music_nothing_playing), style = WpType.Large, color = Color.White, modifier = Modifier.padding(20.dp))
            return@Box
        }
        // Top: what's playing from, and the shuffle / repeat buttons on the right.
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
            WpIcon(Glyphs.Radio, tint = accent, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            WpText((source ?: playback.album.ifBlank { stringResource(R.string.music_title) }).uppercase(), style = WpType.AppTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundButton(WpIcons.Shuffle, filled = playback.shuffle, accent = accent, size = 40.dp) { controller.toggleShuffle() }
                RoundButton(
                    if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) WpIcons.RepeatOne else WpIcons.Repeat,
                    filled = playback.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF, accent = accent, size = 40.dp,
                ) { controller.cycleRepeat() }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 18.dp)) {
            // Each new song slides in from the right (Windows 8 content transition).
            androidx.compose.animation.AnimatedContent(
                targetState = playback.trackKey,
                transitionSpec = {
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(380, easing = com.wpreborn.core.motion.WpEasing.CubicInOut)) { it / 3 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300))) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
                },
                label = "track",
            ) { _ ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
                Art(playback, null, Modifier.size(150.dp).background(accent), grey = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).height(150.dp)) {
                    WpText(playback.title, style = WpType.Large.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    WpText(playback.artist, style = WpType.Normal, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val fav = playback.mediaId != null && playback.mediaId in favorites
                        // MixRadio's three: thumbs up, thumbs down (skips, and never again in
                        // "play me"), and the star (favourites).
                        val vote = playback.mediaId?.let { votes[it] } ?: 0
                        RoundButton(ThumbUpIcon, filled = vote == 1, accent = accent, size = 42.dp) {
                            playback.mediaId?.let { votesStore.vote(it, 1); if (votesStore.votes.value[it] == 1) banner = true }
                        }
                        RoundButton(ThumbUpIcon, filled = vote == -1, accent = accent, size = 42.dp, rotate = 180f) {
                            playback.mediaId?.let { id ->
                                votesStore.vote(id, -1)
                                if (votesStore.votes.value[id] == -1) { controller.next(); banner = false }
                            }
                        }
                        RoundButton(WpIcons.Star, filled = fav, accent = accent, size = 42.dp) { playback.mediaId?.let { Favorites.get(context).toggle(it) } }
                    }
                }
            }
            }
            Spacer(Modifier.height(14.dp))
            // Progress: accent over a dim line, the times under it.
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                WpSlider(
                    seeking ?: playback.progress,
                    { seeking = it },
                    Modifier.fillMaxWidth(),
                    onValueChangeFinished = {
                        seeking?.let { controller.seekTo((it * playback.durationMs).toLong()) }
                        seeking = null
                    },
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                WpText(fmt(seeking?.let { (it * playback.durationMs).toLong() } ?: playback.positionMs), style = WpType.Small, color = Color.White, modifier = Modifier.weight(1f))
                WpText(fmt(playback.durationMs), style = WpType.Small, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                RoundButton(if (playback.playing) WpIcons.Pause else WpIcons.Play, filled = false, accent = accent, size = 64.dp) { controller.togglePlay() }
                Spacer(Modifier.width(28.dp))
                RoundButton(WpIcons.Next, filled = false, accent = accent, size = 64.dp) { controller.next() }
            }
        }
        // "..." at the bottom right, as on MixRadio's player.
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp).size(40.dp).wpClickable { menu = true },
            contentAlignment = Alignment.Center,
        ) { WpIcon(WpIcons.More, tint = Color.White, size = 22.dp) }

        if (queue) QueueEditor(playback, accent) { queue = false }
        if (sleepMenu) SleepMenu { sleepMenu = false }
        banner?.let { up -> Box(Modifier.align(Alignment.TopCenter)) { VoteBanner(up) { banner = null } } }
        if (menu) {
            val fade = com.wpreborn.services.music.Crossfade.seconds(context)
            MenuOverlay(
                listOf(
                    stringResource(R.string.music_up_next) to { queue = true; Unit },
                    stringResource(R.string.music_add_to_playlist) to { playback.mediaId?.toLongOrNull()?.let { playlistFor = listOf(it) }; Unit },
                    stringResource(R.string.music_save_queue) to { playlistFor = controller.queueIds(); Unit },
                    (if (fade == 0) stringResource(R.string.music_crossfade_off) else stringResource(R.string.music_crossfade_on, fade)) to {
                        com.wpreborn.services.music.Crossfade.cycle(context)
                        Unit
                    },
                    sleepLabel() to { sleepMenu = true; Unit },
                    stringResource(R.string.mix_share_three) to {
                        shareThree(context, listOfNotNull(playback.mediaId?.toLongOrNull()) + controller.upNext(2).mapNotNull { it.mediaId.toLongOrNull() })
                    },
                    stringResource(R.string.music_go_album) to { playback.mediaId?.let(onAlbum); Unit },
                    stringResource(R.string.music_go_artist) to { playback.mediaId?.let(onArtist); Unit },
                    stringResource(R.string.eq_title) to onEqualizer,
                ),
            ) { menu = false }
        }
        playlistFor?.let { ids -> PlaylistPicker(ids, onDone = { playlistFor = null; onChanged() }) { playlistFor = null } }
    }
}

/** A Windows Phone menu: text items rising from the bottom over a dimmed screen. */
@Composable
fun MenuOverlay(items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).wpClickable(onClick = onDismiss)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xFF1F1F1F)).padding(vertical = 10.dp)) {
            items.forEach { (label, action) ->
                WpText(
                    label,
                    style = WpType.Large.copy(fontSize = 20.sp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().wpClickable { onDismiss(); action() }.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * "up next": the queue over the player, like Spotify's: tap a song to play it, move it
 * earlier or later, take it out, or clear everything after the current song.
 */
@Composable
fun QueueEditor(playback: Playback, accent: Color, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { MusicController.get(context) }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    // Re-read the queue whenever it changes (the player publishes its state).
    val ui by controller.ui.collectAsState()
    var version by remember { mutableStateOf(0) }
    val entries = remember(ui.queueSize, ui.index, ui.shuffle, version) { controller.upNext() }
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).padding(start = 20.dp, top = 14.dp, end = 8.dp)) {
        WpText(stringResource(R.string.music_title).uppercase(), style = WpType.AppTitle, color = Color.White)
        WpText(stringResource(R.string.music_up_next), style = WpType.Header, color = Color.White, maxLines = 1, softWrap = false)
        WpText(playback.title, style = WpType.Large, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        WpText(playback.artist, style = WpType.Small, color = Color.White.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.padding(bottom = 10.dp))
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
            if (entries.isEmpty()) item { WpText(stringResource(R.string.music_queue_end), style = WpType.Large, color = Color.White.copy(alpha = 0.6f)) }
            items(entries.size, key = { entries[it].index.toString() + entries[it].mediaId }) { i ->
                val e = entries[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).wpClickable { controller.playAt(e.index); onDismiss() }) {
                        WpText(e.title, style = WpType.Large.copy(fontSize = 20.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WpText(e.artist, style = WpType.Small, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!ui.shuffle) {
                        SmallIcon(WpIcons.Back, "↑", enabled = i > 0) { controller.move(e.index, -1); version++ }
                        SmallIcon(WpIcons.Forward, "↓", enabled = i < entries.lastIndex) { controller.move(e.index, 1); version++ }
                    }
                    SmallIcon(WpIcons.Close, "×", enabled = true) { controller.removeAt(e.index); version++ }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            WpText(stringResource(R.string.music_clear_queue), style = WpType.Normal, color = Color.White, modifier = Modifier.wpClickable { controller.clearUpNext(); version++ }.padding(10.dp))
            WpText(stringResource(R.string.ok), style = WpType.Normal, color = Color.White, modifier = Modifier.wpClickable(onClick = onDismiss).padding(10.dp))
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).then(if (enabled) Modifier.wpClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Back/Forward arrows turned to point up and down for "move".
        val rot = when (desc) { "↑" -> 90f; "↓" -> 90f; else -> 0f }
        WpIcon(icon, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), size = 20.dp, modifier = Modifier.rotate(rot))
    }
}

/** Sleep timer choices, as a menu. */
@Composable
fun SleepMenu(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now = remember { com.wpreborn.services.music.SleepTimer.state(context) }
    val items = buildList<Pair<String, () -> Unit>> {
        if (now != null) add(stringResource(R.string.music_sleep_off) to { com.wpreborn.services.music.SleepTimer.cancel(context); Unit })
        listOf(5, 15, 30, 45, 60).forEach { m -> add(stringResource(R.string.music_sleep_minutes, m) to { com.wpreborn.services.music.SleepTimer.set(context, m); Unit }) }
        add(stringResource(R.string.music_sleep_end) to { com.wpreborn.services.music.SleepTimer.endOfSong(context); Unit })
    }
    MenuOverlay(items, onDismiss)
}

/** The sleep timer's label for menus. */
@Composable
fun sleepLabel(): String {
    val context = LocalContext.current
    return when (val s = com.wpreborn.services.music.SleepTimer.state(context)) {
        null -> stringResource(R.string.music_sleep)
        -1 -> stringResource(R.string.music_sleep_until_end)
        else -> stringResource(R.string.music_sleep_left, s)
    }
}

/**
 * Adds songs to a playlist: the ZUNEX playlists, or "new playlist" with a name box
 * (also used on its own, with no songs, to just create a playlist).
 */
@Composable
fun PlaylistPicker(songIds: List<Long>, startWithNew: Boolean = false, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val library = remember { com.wpreborn.data.media.MusicLibrary(context) }
    val playlists = remember { library.userPlaylists() }
    var naming by remember { mutableStateOf(startWithNew || playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).wpClickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Wp.palette.chrome).wpClickable { }
                .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 20.dp),
        ) {
            if (naming) {
                WpText(stringResource(R.string.music_new_playlist), style = WpType.Large.copy(fontWeight = FontWeight.SemiBold))
                WpText(stringResource(R.string.music_playlist_name), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp))
                com.wpreborn.core.ui.WpTextBox(name, { name = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.wpreborn.core.ui.WpButton(stringResource(R.string.save), Modifier.weight(1f)) {
                        if (name.isNotBlank()) scope.launch {
                            val p = library.createPlaylist(name.trim())
                            if (songIds.isNotEmpty()) library.addToPlaylist(p.id, songIds)
                            onDone()
                        }
                    }
                    com.wpreborn.core.ui.WpButton(stringResource(R.string.cancel), Modifier.weight(1f), onClick = onDismiss)
                }
            } else {
                WpText(stringResource(R.string.music_add_to_playlist), style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(bottom = 8.dp))
                WpText(
                    stringResource(R.string.music_new_playlist),
                    style = WpType.Large,
                    color = Wp.accent,
                    modifier = Modifier.fillMaxWidth().wpClickable { naming = true }.padding(vertical = 8.dp),
                )
                playlists.forEach { p ->
                    WpText(
                        p.name,
                        style = WpType.Large,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().wpClickable {
                            scope.launch {
                                library.addToPlaylist(p.id, songIds)
                                onDone()
                            }
                        }.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** MixRadio's round buttons: a white ring, or filled with the accent when on. */
@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, accent: Color, size: Dp, rotate: Float = 0f, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .background(if (filled) accent else Color.Transparent, CircleShape)
            .border(2.dp, if (filled) accent else Color.White, CircleShape)
            .wpClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint = Color.White, size = size * 0.5f, modifier = Modifier.rotate(rotate))
    }
}

/** Thumb (Material Design "thumb up" path, Apache 2.0); rotated for thumbs down. */
internal val ThumbUpIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("thumb", 24.dp, 24.dp, 24f, 24f).addPath(
        androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M1,21h4V9H1v12zM23,10c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57 0.03,-0.32c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 7.59,7.59C7.22,7.95 7,8.45 7,9v10c0,1.1 0.9,2 2,2h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73v-2z",
        ).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.White),
    ).build()
}

/** "create a mix": choose up to three artists. */
@Composable
private fun ArtistPicker(col: MusicCollection, onDone: (List<String>) -> Unit) {
    var chosen by remember { mutableStateOf(listOf<String>()) }
    androidx.activity.compose.BackHandler { onDone(emptyList()) }
    val artists = remember(col) { col.artists.map { it.name }.filter { it.isNotBlank() && it != com.wpreborn.data.media.MusicLibrary.UNKNOWN } }
    Column(Modifier.fillMaxSize().background(Wp.palette.background).padding(start = 20.dp, top = 14.dp, end = 16.dp)) {
        WpText("ZUNEX MIX", style = WpType.AppTitle)
        WpText(stringResource(R.string.mix_create), style = WpType.Header, maxLines = 1, softWrap = false)
        WpText(stringResource(R.string.mix_create_hint), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 8.dp))
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
            items(artists.size) { i ->
                val a = artists[i]
                val on = a in chosen
                WpText(
                    a.lowercase(),
                    style = WpType.ExtraLarge,
                    color = if (on) Wp.accent else Wp.palette.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().wpClickable {
                        chosen = if (on) chosen - a else if (chosen.size < 3) chosen + a else chosen
                    }.padding(vertical = 4.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.wpreborn.core.ui.WpButton(stringResource(R.string.music_play), Modifier.weight(1f)) { onDone(chosen) }
            com.wpreborn.core.ui.WpButton(stringResource(R.string.cancel), Modifier.weight(1f)) { onDone(emptyList()) }
        }
    }
}

/**
 * Starts "play me" (see PlayMe): a radio seeded from one of your favourite artists; each call
 * seeds from the next one. Returns false when there's nothing to play.
 */
fun startPlayMe(context: android.content.Context, col: MusicCollection, label: String): Boolean {
    val prefs = context.getSharedPreferences("music", android.content.Context.MODE_PRIVATE)
    val seed = prefs.getInt("playMeSeed", 0)
    prefs.edit().putInt("playMeSeed", seed + 1).apply()
    val (artist, list) = com.wpreborn.data.media.PlayMe.build(
        col, com.wpreborn.data.media.PlayStats.get(context).counts(), Favorites.get(context).ids.value,
        com.wpreborn.data.media.Votes.get(context).votes.value, seed,
        chosen = com.wpreborn.data.media.FavoriteArtists.get(context).artists.value,
    )
    if (list.isEmpty()) return false
    MusicController.get(context).playSongs(list, 0, shuffle = false, source = label + if (artist.isNotBlank()) " · $artist" else "")
    return true
}
