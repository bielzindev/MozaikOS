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
) {
    val context = LocalContext.current
    val controller = remember { MusicController.get(context) }
    val playback = rememberPlayback()
    val accent = Wp.accent
    val recent = remember(col) { col?.songs?.sortedByDescending { it.dateAdded }?.take(40).orEmpty() }
    val bgSong = recent.firstOrNull() ?: col?.songs?.firstOrNull()

    fun playMe() {
        val songs = col?.songs.orEmpty()
        if (songs.isEmpty()) return
        // "Play me": your radio - favourites first, then everything, shuffled.
        val fav = Favorites.get(context).ids.value
        val picked = (songs.filter { it.id.toString() in fav }.shuffled() + songs.shuffled()).distinctBy { it.id }
        controller.playSongs(picked, 0, shuffle = false)
        onNowPlaying()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Background: the picture of what's playing, black and white and dimmed.
        Art(playback, bgSong, Modifier.fillMaxSize(), grey = true, size = 720)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        Column(Modifier.fillMaxSize()) {
            // Header: the title in two weights (never cut) and the round button under it.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp)) {
                WpText("ZUNEX", style = WpType.Header.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false)
                WpText("Mix", style = WpType.Header.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Light), color = Color.White, maxLines = 1, softWrap = false)
            }
            Row(Modifier.padding(start = 20.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).border(2.dp, Color.White, CircleShape).wpClickable(onClick = onClassic),
                    contentAlignment = Alignment.Center,
                ) { WpIcon(WpIcons.More, tint = Color.White, size = 18.dp) }
                Spacer(Modifier.width(10.dp))
                WpText(stringResource(R.string.mix_classic), style = WpType.Small, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.wpClickable(onClick = onClassic))
            }

            // The canvas: 7 rows of square cells sized to the screen, scrolling sideways.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val g = 6.dp
                val rows = 7
                val labelSpace = 44.dp
                val cell = ((maxHeight - labelSpace - g * (rows - 1)) / rows).coerceIn(44.dp, 92.dp)
                val step = cell + g
                fun x(c: Int) = step * c
                fun y(r: Int) = step * r
                fun w(n: Int) = step * n - g

                // Mixes on two columns of large tiles with coloured squares in the gaps.
                val mixSlots = listOf(4 to 1, 6 to 0, 4 to 3, 6 to 2, 4 to 5, 6 to 4)
                val libraryCol = 9
                val columns = libraryCol + 2
                Box(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp),
                ) {
                    Box(Modifier.width(w(columns)).fillMaxHeight()) {
                        // What's playing, with its time in big numerals (3 × 3).
                        Box(Modifier.offset(x(0), y(0)).size(w(3)).background(Color.DarkGray).wpClickable(onClick = onNowPlaying)) {
                            Art(playback, bgSong, Modifier.fillMaxSize(), grey = false)
                            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))))
                            WpText(
                                if (playback != null) fmt(playback.positionMs) else "0:00",
                                style = WpType.Header.copy(fontSize = (cell.value * 0.75f).sp, lineHeight = (cell.value * 0.8f).sp, fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
                            )
                        }
                        // Shuffle (1 × 1) beside "play me".
                        Box(
                            Modifier.offset(x(0), y(3)).size(w(1)).background(accent.copy(alpha = 0.7f)).wpClickable {
                                col?.songs?.takeIf { it.isNotEmpty() }?.let { controller.playSongs(it.shuffled(), 0, shuffle = true); onNowPlaying() }
                            },
                            contentAlignment = Alignment.Center,
                        ) { WpIcon(WpIcons.Shuffle, tint = Color.White, size = cell * 0.4f) }
                        // "play me" (3 × 3).
                        Box(Modifier.offset(x(1), y(3)).size(w(3)).background(accent).wpClickable { playMe() }.padding(10.dp)) {
                            WpIcon(WpIcons.Play, tint = Color.White, size = cell * 1.1f, modifier = Modifier.align(Alignment.Center))
                            WpText(stringResource(R.string.mix_play_me), style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = Color.White, modifier = Modifier.align(Alignment.BottomStart))
                        }
                        // A quiet square to close the group.
                        Box(Modifier.offset(x(0), y(5)).size(w(1)).background(accent.copy(alpha = 0.4f)))

                        // Coloured squares in the free cells around the mixes.
                        listOf(5 to 0, 6 to 6, 4 to 0).forEachIndexed { i, (c, r) ->
                            Box(Modifier.offset(x(c), y(r)).size(w(1)).background(accent.copy(alpha = 0.45f + 0.15f * i)))
                        }
                        mixes.take(mixSlots.size).forEachIndexed { i, m ->
                            val (c, r) = mixSlots[i]
                            MixTile(m, Modifier.offset(x(c), y(r)).size(w(2))) {
                                controller.playSongs(m.songs, 0)
                                onNowPlaying()
                            }
                        }

                        // The library (2 × 1 each).
                        listOf(
                            R.string.music_artists to 1, R.string.music_albums to 2, R.string.music_songs to 3,
                            R.string.music_playlists to 4, R.string.music_mixes to 0,
                        ).forEachIndexed { i, (label, page) ->
                            Box(
                                Modifier.offset(x(libraryCol), y(i)).size(w(2), w(1)).background(if (i % 2 == 0) accent else accent.copy(alpha = 0.8f))
                                    .wpClickable { onLibrary(page) }.padding(8.dp),
                            ) {
                                WpText(stringResource(label), style = WpType.Normal.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                            }
                        }

                        // "recently added", under the grid.
                        WpText(
                            stringResource(R.string.mix_recent),
                            style = WpType.Large.copy(fontSize = 20.sp),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.offset(x(0), y(rows) + 4.dp).wpClickable {
                                if (recent.isNotEmpty()) { controller.playSongs(recent, 0); onNowPlaying() }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MixTile(m: Mix, modifier: Modifier, onClick: () -> Unit) {
    val art by rememberThumbnail(m.songs.firstOrNull()?.albumArtUri, 384)
    Box(modifier.background(m.color).wpClickable(onClick = onClick)) {
        art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(m.color.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
            WpText(m.title, style = WpType.Normal.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The player of the new interface (beta), after MixRadio's: the picture in black and white
 * behind everything, the cover with the title, round buttons, an accent progress line and
 * large round play / next buttons.
 */
@Composable
fun MixNowPlaying(onEqualizer: () -> Unit) {
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
    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
            WpText(playback.album.ifBlank { stringResource(R.string.music_title) }.uppercase(), style = WpType.AppTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundButton(WpIcons.Shuffle, filled = playback.shuffle, accent = accent, size = 40.dp) { controller.toggleShuffle() }
                RoundButton(
                    if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) WpIcons.RepeatOne else WpIcons.Repeat,
                    filled = playback.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF, accent = accent, size = 40.dp,
                ) { controller.cycleRepeat() }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
                Art(playback, null, Modifier.size(150.dp).background(accent), grey = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).height(150.dp)) {
                    WpText(playback.title, style = WpType.Large.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    WpText(playback.artist, style = WpType.Normal, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val fav = playback.mediaId != null && playback.mediaId in favorites
                        RoundButton(Glyphs.Heart, filled = fav, accent = accent, size = 42.dp) { playback.mediaId?.let { Favorites.get(context).toggle(it) } }
                        RoundButton(WpIcons.Previous, filled = false, accent = accent, size = 42.dp) { controller.previous() }
                        RoundButton(WpIcons.Sound, filled = true, accent = accent, size = 42.dp, onClick = onEqualizer)
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
    }
}

/** MixRadio's round buttons: a white ring, or filled with the accent when on. */
@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, accent: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .background(if (filled) accent else Color.Transparent, CircleShape)
            .border(2.dp, if (filled) accent else Color.White, CircleShape)
            .wpClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint = Color.White, size = size * 0.5f)
    }
}
