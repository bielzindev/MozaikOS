package com.wpreborn.launcher.start

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.AppRepository
import com.wpreborn.data.apps.BuiltInApp
import com.wpreborn.data.media.NowPlaying
import com.wpreborn.data.notifications.WpNotification
import com.wpreborn.data.tiles.TileSize
import com.wpreborn.data.tiles.TileSpec
import com.wpreborn.launcher.LiveData
import java.util.Calendar
import java.util.Date

/** Everything a tile needs to render its live face. */
data class TileInputs(
    val live: LiveData,
    val notificationCount: Int,
    /** Latest notifications of the app (newest first, up to three) for social live tiles. */
    val recent: List<WpNotification>,
    val nowPlaying: NowPlaying?,
    val nowPlayingArt: Uri?,
    val liveEnabled: Boolean,
)

/**
 * The face of a tile. Built-in apps get their real live tile templates; Android apps show
 * their icon, the unread count from their notifications and flip to the latest one.
 */
@Composable
fun TileFace(tile: TileSpec, app: AppEntry?, inputs: TileInputs, tileSize: Dp, seed: Int) {
    val small = tile.size == TileSize.Small
    val builtIn = app?.builtIn
    // A ZUNEX app's glyph takes the colour chosen for its icon.
    val iconTint = rememberIconCustom(app).tintColor()
    Box(Modifier.fillMaxSize()) {
      androidx.compose.runtime.CompositionLocalProvider(LocalTileIconTint provides iconTint) {
        when (builtIn) {
            BuiltInApp.Phone -> CountTile(WpIcons.Phone, inputs.live.missedCalls, tile.size)
            BuiltInApp.Messaging -> CountTile(WpIcons.Messaging, inputs.live.unreadSms, tile.size)
            BuiltInApp.People -> PeopleTile(tile.size, inputs.live.contactPhotos, inputs.liveEnabled, seed)
            BuiltInApp.Photos -> PhotosTile(tile.size, inputs.live.photos, inputs.liveEnabled, seed)
            BuiltInApp.Music -> MusicTile(tile.size, inputs.nowPlaying, inputs.nowPlayingArt, inputs.liveEnabled, seed)
            BuiltInApp.Calendar -> CalendarTile(tile.size, inputs.live, inputs.liveEnabled, seed)
            BuiltInApp.Settings -> IconTile(WpIcons.Settings, tile.size)
            BuiltInApp.Keyboard -> IconTile(WpIcons.Keypad, tile.size)
            BuiltInApp.Camera -> IconTile(WpIcons.Camera, tile.size)
            BuiltInApp.Calculator -> IconTile(com.wpreborn.core.icons.Glyphs.Calculator, tile.size)
            BuiltInApp.Notes -> IconTile(com.wpreborn.core.icons.Glyphs.Note, tile.size)
            BuiltInApp.Weather -> WeatherTile(tile.size, inputs.liveEnabled)
            BuiltInApp.Store -> StoreTile(tile.size)
            BuiltInApp.Alarms -> AlarmsTile(tile.size)
            BuiltInApp.Clock -> IconTile(WpIcons.Clock, tile.size)
            BuiltInApp.Recorder -> IconTile(com.wpreborn.core.icons.Glyphs.Microphone, tile.size)
            BuiltInApp.Flashlight -> IconTile(WpIcons.Flashlight, tile.size)
            BuiltInApp.Maps -> IconTile(com.wpreborn.core.icons.Glyphs.MapPin, tile.size)
            BuiltInApp.News -> NewsTile(tile.size, inputs.liveEnabled, seed)
            BuiltInApp.Browser -> IconTile(WpIcons.Globe, tile.size)
            null -> AndroidAppTile(app, tile.size, inputs, seed)
        }
      }
        val showLabel = !small && !(builtIn == BuiltInApp.Photos && inputs.live.photos.isNotEmpty() && inputs.liveEnabled) &&
            !(builtIn == BuiltInApp.Calendar && tile.size != TileSize.Medium && inputs.live.events.isNotEmpty() && inputs.liveEnabled)
        if (showLabel && app != null) {
            WpText(
                app.label,
                style = WpType.TileLabel,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp, end = 8.dp),
            )
        }
    }
}

private fun iconSizeFor(size: TileSize): Dp = when (size) {
    TileSize.Small -> 30.dp
    TileSize.Medium -> 50.dp
    TileSize.Wide -> 50.dp
    TileSize.Large -> 72.dp
}

@Composable
private fun BoxScope.IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, size: TileSize) {
    WpIcon(icon, Modifier.align(Alignment.Center), tint = LocalTileIconTint.current ?: Color.White, size = iconSizeFor(size))
}

/** Iconic tile with a counter to the right of the glyph (Phone, Messaging). */
@Composable
private fun BoxScope.CountTile(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, size: TileSize) {
    if (count <= 0) {
        IconTile(icon, size)
        return
    }
    if (size == TileSize.Small) {
        WpIcon(icon, Modifier.align(Alignment.Center), tint = LocalTileIconTint.current ?: Color.White, size = 26.dp)
        WpText(
            count.coerceAtMost(99).toString(),
            style = WpType.Small.copy(fontSize = 12.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
        )
        return
    }
    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
        WpIcon(icon, tint = LocalTileIconTint.current ?: Color.White, size = iconSizeFor(size))
        Spacer(Modifier.width(8.dp))
        WpText(count.coerceAtMost(99).toString(), style = WpType.TileCount, color = Color.White)
    }
}

@Composable
private fun PeopleTile(size: TileSize, faces: List<Uri>, live: Boolean, seed: Int) {
    if (!live || faces.size < 3) {
        Box(Modifier.fillMaxSize()) { IconTile(WpIcons.People, size) }
        return
    }
    val cols = when (size) {
        TileSize.Small -> 1
        TileSize.Medium -> 3
        TileSize.Wide -> 6
        TileSize.Large -> 6
    }
    val rows = when (size) {
        TileSize.Small -> 1
        TileSize.Medium, TileSize.Wide -> 3
        TileSize.Large -> 6
    }
    Column(Modifier.fillMaxSize()) {
        for (r in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        FlippingCell(faces, i % faces.size, seed * 31 + i, live) { uri ->
                            Thumbnail(uri, Modifier.fillMaxSize(), sizePx = 128)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotosTile(size: TileSize, photos: List<Uri>, live: Boolean, seed: Int) {
    if (!live || photos.isEmpty()) {
        Box(Modifier.fillMaxSize()) { IconTile(WpIcons.Photos, size) }
        return
    }
    CycleTile(photos, live, seed) { uri ->
        Box(Modifier.fillMaxSize()) {
            Thumbnail(uri, Modifier.fillMaxSize(), sizePx = 512)
            if (size != TileSize.Small) {
                WpText(
                    LocalContext.current.getString(R.string.app_photos),
                    style = WpType.TileLabel,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp),
                )
            }
        }
    }
}

/**
 * Music: a live tile in all sizes, like MixRadio's. Playing: the cover with the song and
 * artist (medium flips between the cover and the text; small shows the cover). Idle: the
 * covers of your favourite and most played artists slide by.
 */
@Composable
private fun MusicTile(size: TileSize, playing: NowPlaying?, artUri: Uri?, live: Boolean, seed: Int) {
    if (!live) {
        Box(Modifier.fillMaxSize()) { IconTile(WpIcons.Music, size) }
        return
    }
    if (playing == null) {
        IdleMusicTile(size, seed)
        return
    }
    if (size == TileSize.Small) {
        Box(Modifier.fillMaxSize()) { AlbumArt(playing.art, artUri) }
        return
    }
    @Composable
    fun Caption(modifier: Modifier) {
        Column(modifier) {
            WpText(playing.title, style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            WpText(playing.artist, style = WpType.Small, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (size == TileSize.Medium) {
        FlipTile(enabled = true, seed = seed, front = {
            KenBurns { ArtistImage(playing.artist) { AlbumArt(playing.art, artUri) } }
        }, back = {
            Box(Modifier.fillMaxSize()) {
                AlbumArt(playing.art, artUri)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                Caption(Modifier.padding(8.dp))
            }
        })
        return
    }
    // Wide / large, like Xbox Music's tile on 8.1: the artist's picture slowly panning and
    // zooming ("visualisations"), the song and artist over a shade at the bottom.
    Box(Modifier.fillMaxSize()) {
        KenBurns { ArtistImage(playing.artist) { AlbumArt(playing.art, artUri) } }
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        Caption(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 24.dp))
    }
}

@Composable
private fun IdleMusicTile(size: TileSize, seed: Int) {
    val context = LocalContext.current
    val covers by produceState<List<Uri>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val col = com.wpreborn.data.media.MusicLibrary(context).load()
                val fav = com.wpreborn.data.media.FavoriteArtists.get(context).artists.value
                val plays = com.wpreborn.data.media.PlayStats.get(context).counts()
                val top = col.artists.sortedByDescending { a -> (if (a.name in fav) 1000 else 0) + a.songs.sumOf { plays[it.id] ?: 0 } }
                (top.flatMap { it.albums } + col.albums.sortedByDescending { it.songs.maxOfOrNull { s -> s.dateAdded } ?: 0 })
                    .distinctBy { it.id }.take(8).map { it.artUri }
            }.getOrDefault(emptyList())
        }
    }
    if (covers.isEmpty() || size == TileSize.Small) {
        Box(Modifier.fillMaxSize()) { IconTile(WpIcons.Music, size) }
        return
    }
    CycleTile(covers, enabled = covers.size > 1, seed = seed) { uri ->
        KenBurns {
            Thumbnail(uri, Modifier.fillMaxSize(), sizePx = 512) {
                Box(Modifier.fillMaxSize()) { IconTile(WpIcons.Music, size) }
            }
        }
    }
}

/** Slow pan and zoom of a picture (the "visualisations" of the 8.1 music tiles). */
@Composable
private fun KenBurns(content: @Composable () -> Unit) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "kenburns")
    val p by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(14000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "kenburns-p",
    )
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val s = 1.08f + 0.14f * p
                scaleX = s
                scaleY = s
                translationX = (p - 0.5f) * size.width * 0.08f
                translationY = (0.5f - p) * size.height * 0.05f
            },
        ) { content() }
    }
}

/** The artist's picture (Wikipedia, cached by the Music app), else [fallback]. */
@Composable
private fun ArtistImage(artist: String, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val bio by produceState<com.wpreborn.apps.music.ArtistBio?>(null, artist) {
        value = if (artist.isBlank()) null else com.wpreborn.apps.music.ArtistBios.load(context, artist)
    }
    val img by com.wpreborn.apps.news.rememberWebImage(bio?.image, 720)
    val i = img
    if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else fallback()
}

@Composable
private fun AlbumArt(bitmap: Bitmap?, uri: Uri?) {
    val image: ImageBitmap? = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else if (uri != null) {
        Thumbnail(uri, Modifier.fillMaxSize(), sizePx = 512) {
            Box(Modifier.fillMaxSize()) { IconTile(WpIcons.Music, TileSize.Medium) }
        }
    } else {
        Box(Modifier.fillMaxSize()) { IconTile(WpIcons.Music, TileSize.Medium) }
    }
}

@Composable
private fun CalendarTile(size: TileSize, live: LiveData, enabled: Boolean, seed: Int) {
    val context = LocalContext.current
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
    val weekday = DateFormat.format("EEEE", cal).toString().lowercase()
    val next = live.events.firstOrNull()
    if (size == TileSize.Small) {
        WpText(day, style = WpType.TileCount, color = Color.White, modifier = Modifier.padding(6.dp))
        return
    }
    @Composable
    fun DateFace() {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
                WpText(day, style = WpType.Header.copy(fontSize = 44.sp, lineHeight = 46.sp), color = Color.White)
                WpText(weekday, style = WpType.TileLabel, color = Color.White, modifier = Modifier.padding(bottom = 22.dp))
            }
        }
    }
    @Composable
    fun EventFace() {
        val e = next ?: return
        val time = if (e.allDay) context.getString(R.string.calendar_all_day) else DateFormat.getTimeFormat(context).format(Date(e.begin))
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth(if (size == TileSize.Medium) 1f else 0.7f)) {
                WpText(e.title, style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.location.isNotBlank()) WpText(e.location, style = WpType.Small, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                WpText(time, style = WpType.Small, color = Color.White)
            }
            if (size != TileSize.Medium) {
                Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.Bottom) {
                    WpText(weekday, style = WpType.TileLabel, color = Color.White, modifier = Modifier.padding(end = 6.dp, bottom = 6.dp))
                    WpText(day, style = WpType.Header.copy(fontSize = 34.sp, lineHeight = 36.sp), color = Color.White)
                }
            }
        }
    }
    if (next == null || !enabled) {
        DateFace()
    } else if (size == TileSize.Medium) {
        FlipTile(enabled = true, seed = seed, front = { DateFace() }, back = { EventFace() })
    } else {
        EventFace()
    }
}

/**
 * Android apps, in the Windows Phone 8 tile templates that messaging apps (WhatsApp,
 * Messaging, mail) used:
 * - Iconic: the app's glyph with the unread count to its right (small and medium);
 * - medium flips to its back: the conversation and its latest message (Flip template);
 * - wide shows the latest conversation in three lines of text, with the small glyph and
 *   count in the bottom-right corner (Iconic wide);
 * - large lists the latest three conversations.
 */
@Composable
private fun AndroidAppTile(app: AppEntry?, size: TileSize, inputs: TileInputs, seed: Int) {
    val count = inputs.notificationCount
    val recent = if (inputs.liveEnabled) inputs.recent else emptyList()
    when {
        size == TileSize.Small -> Box(Modifier.fillMaxSize()) { IconicFace(app, size, count) }
        recent.isEmpty() -> Box(Modifier.fillMaxSize()) { IconicFace(app, size, count) }
        size == TileSize.Medium -> FlipTile(enabled = true, seed = seed, front = {
            Box(Modifier.fillMaxSize()) { IconicFace(app, size, count) }
        }, back = {
            FlipBack(recent.first())
        })
        size == TileSize.Wide -> WideIconic(app, recent.first(), count)
        else -> LargeIconic(app, recent, count)
    }
}

private fun countText(count: Int) = if (count > 99) "99+" else count.toString()

/** Glyph, and the count to its right in light numerals, centred as one group. */
@Composable
private fun BoxScope.IconicFace(app: AppEntry?, size: TileSize, count: Int) {
    val icon = when (size) {
        TileSize.Small -> if (count > 0) 26.dp else 32.dp
        TileSize.Medium -> 54.dp
        else -> 60.dp
    }
    Row(Modifier.align(Alignment.Center).padding(bottom = if (size == TileSize.Small) 0.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app != null) AppIcon(app, icon)
        if (count > 0) {
            Spacer(Modifier.width(if (size == TileSize.Small) 3.dp else 8.dp))
            WpText(
                countText(count),
                style = if (size == TileSize.Small) WpType.Normal.copy(fontSize = 16.sp, lineHeight = 18.sp) else WpType.TileCount.copy(fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Light),
                color = Color.White,
            )
        }
    }
}

/** Back of the medium tile: who wrote and what, in the tile's own text style. */
@Composable
private fun FlipBack(n: WpNotification) {
    Column(Modifier.fillMaxSize().padding(start = 9.dp, top = 7.dp, end = 9.dp, bottom = 24.dp)) {
        WpText(n.title, style = WpType.Normal.copy(fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        WpText(n.text, style = WpType.Normal.copy(fontSize = 15.sp, lineHeight = 19.sp), color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/** Corner group of the wide/large tiles: count, then the small glyph. */
@Composable
private fun BoxScope.CornerCount(app: AppEntry?, count: Int) {
    Row(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count > 0) {
            WpText(countText(count), style = WpType.Large.copy(fontSize = 20.sp, lineHeight = 22.sp), color = Color.White)
            Spacer(Modifier.width(5.dp))
        }
        if (app != null) AppIcon(app, 26.dp)
    }
}

@Composable
private fun WideIconic(app: AppEntry?, n: WpNotification, count: Int) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, end = 12.dp)) {
            WpText(n.title, style = WpType.Large.copy(fontSize = 21.sp, lineHeight = 26.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(n.text, style = WpType.Normal.copy(fontSize = 15.sp, lineHeight = 19.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        CornerCount(app, count)
    }
}

@Composable
private fun LargeIconic(app: AppEntry?, recent: List<WpNotification>, count: Int) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.take(3).forEach { n ->
                Column {
                    WpText(n.title, style = WpType.Large.copy(fontSize = 19.sp, lineHeight = 23.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    WpText(n.text, style = WpType.Normal.copy(fontSize = 14.sp, lineHeight = 18.sp), color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        CornerCount(app, count)
    }
}

/** Weather: temperature, sky and place; wide and large add the next days, like MSN Weather. */
@Composable
private fun BoxScope.WeatherTile(size: TileSize, live: Boolean) {
    val context = LocalContext.current
    val repo = remember { com.wpreborn.data.weather.WeatherRepository.get(context) }
    val forecasts by repo.forecasts.collectAsState()
    val places by repo.places.collectAsState()
    val f by repo.fahrenheit.collectAsState()
    val w = places.firstOrNull()?.let { forecasts[it.id] }
    if (!live || w == null) {
        IconTile(com.wpreborn.core.icons.Glyphs.Weather, size)
        return
    }
    // A soft sky of the current conditions behind the tile (same colours as the Weather app),
    // darkened at the bottom so the temperature, texts and label stay readable.
    val sky = com.wpreborn.apps.weather.skyColors(w.code, w.isDay)
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(sky))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))),
    )
    fun deg(c: Double) = com.wpreborn.data.weather.WeatherRepository.toUnit(c, f).let { kotlin.math.round(it).toInt() }.toString() + "°"
    if (size == TileSize.Small) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            com.wpreborn.apps.weather.WeatherGlyph(w.code, w.isDay, 26.dp)
            WpText(deg(w.temp), style = WpType.Small, color = Color.White)
        }
        return
    }
    Row(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 8.dp, bottom = 24.dp)) {
        Column(Modifier.weight(1f)) {
            WpText(deg(w.temp), style = WpType.TileCount.copy(fontSize = 40.sp, lineHeight = 44.sp), color = Color.White)
            com.wpreborn.apps.weather.WeatherGlyph(w.code, w.isDay, if (size == TileSize.Medium) 40.dp else 44.dp)
            w.days.firstOrNull()?.let { d ->
                WpText(deg(d.max) + " / " + deg(d.min), style = WpType.Small, color = Color.White)
            }
        }
        if (size != TileSize.Medium) {
            Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                w.days.drop(1).take(if (size == TileSize.Large) 6 else 3).forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WpText(
                            android.text.format.DateFormat.format("EEE", d.date).toString(),
                            style = WpType.Small,
                            color = Color.White,
                            modifier = Modifier.width(40.dp),
                        )
                        com.wpreborn.apps.weather.WeatherGlyph(d.code, true, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        WpText(deg(d.max) + " " + deg(d.min), style = WpType.Small, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Store: its bag, with the number of available updates like the 8.1 Store tile. */
@Composable
private fun BoxScope.StoreTile(size: TileSize) {
    val context = LocalContext.current
    val manager = remember { com.wpreborn.data.store.AppManager.get(context) }
    val states by manager.states.collectAsState()
    val updates = remember(states) { com.wpreborn.data.store.ZunexCatalog.apps.count { manager.hasUpdate(it) } }
    CountTile(com.wpreborn.core.icons.Glyphs.Bag, updates, size)
}

/** Alarms: the next alarm's time under the bell, like the 8.1 Alarms tile. */
@Composable
private fun BoxScope.AlarmsTile(size: TileSize) {
    val context = LocalContext.current
    val store = remember { com.wpreborn.data.alarms.AlarmStore.get(context) }
    val alarms by store.alarms.collectAsState()
    val next = remember(alarms) { alarms.filter { it.enabled && !it.timer }.minByOrNull { it.next() } }
    if (next == null || size == TileSize.Small) {
        IconTile(WpIcons.Alarm, size)
        return
    }
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        WpIcon(WpIcons.Alarm, tint = Color.White, size = 36.dp)
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(next.next()))
        WpText(time, style = WpType.Large, color = Color.White)
        if (next.label.isNotBlank()) WpText(next.label, style = WpType.Small, color = Color.White, maxLines = 1)
    }
}

/** News: the top stories' pictures with their headlines, cycling like the Bing News tile. */
@Composable
private fun NewsTile(size: TileSize, live: Boolean, seed: Int) {
    val context = LocalContext.current
    val repo = remember { com.wpreborn.data.news.NewsRepository.get(context) }
    val articles by repo.articles.collectAsState()
    androidx.compose.runtime.LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        while (true) {
            runCatching { repo.refresh() }
            kotlinx.coroutines.delay(20 * 60_000L)
        }
    }
    val top = remember(articles) { repo.inCategory(articles, com.wpreborn.data.news.NewsCategory.Top).filter { it.image != null }.take(6) }
    if (!live || top.isEmpty() || size == TileSize.Small) {
        Box(Modifier.fillMaxSize()) { IconTile(com.wpreborn.core.icons.Glyphs.News, size) }
        return
    }
    CycleTile(top, enabled = top.size > 1, seed = seed) { a ->
        Box(Modifier.fillMaxSize()) {
            com.wpreborn.apps.news.WebImage(a.image!!, Modifier.fillMaxSize(), if (size == TileSize.Medium) 360 else 720)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
            )
            WpText(
                a.title,
                style = WpType.Normal.copy(fontSize = if (size == TileSize.Medium) 14.sp else 16.sp, lineHeight = if (size == TileSize.Medium) 17.sp else 20.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
            )
        }
    }
}
