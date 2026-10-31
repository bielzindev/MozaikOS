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
    Box(Modifier.fillMaxSize()) {
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
            null -> AndroidAppTile(app, tile.size, inputs, seed)
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
    WpIcon(icon, Modifier.align(Alignment.Center), tint = Color.White, size = iconSizeFor(size))
}

/** Iconic tile with a counter to the right of the glyph (Phone, Messaging). */
@Composable
private fun BoxScope.CountTile(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, size: TileSize) {
    if (count <= 0) {
        IconTile(icon, size)
        return
    }
    if (size == TileSize.Small) {
        WpIcon(icon, Modifier.align(Alignment.Center), tint = Color.White, size = 26.dp)
        WpText(
            count.coerceAtMost(99).toString(),
            style = WpType.Small.copy(fontSize = 12.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
        )
        return
    }
    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
        WpIcon(icon, tint = Color.White, size = iconSizeFor(size))
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

@Composable
private fun MusicTile(size: TileSize, playing: NowPlaying?, artUri: Uri?, live: Boolean, seed: Int) {
    if (!live || playing == null || size == TileSize.Small) {
        Box(Modifier.fillMaxSize()) { IconTile(WpIcons.Music, size) }
        return
    }
    FlipTile(enabled = true, seed = seed, front = {
        Box(Modifier.fillMaxSize()) {
            AlbumArt(playing.art, artUri)
        }
    }, back = {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                if (size != TileSize.Medium) {
                    Box(Modifier.fillMaxHeight().weight(1f)) {
                        AlbumArt(playing.art, artUri)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.Top) {
                    WpText(playing.title, style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    WpText(playing.artist, style = WpType.Small, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    })
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

@Composable
private fun AndroidAppTile(app: AppEntry?, size: TileSize, inputs: TileInputs, seed: Int) {
    val iconDp = iconSizeFor(size) + if (size == TileSize.Small) 0.dp else 4.dp
    val front: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            if (app != null) AppIcon(app, iconDp, Modifier.align(Alignment.Center))
            if (inputs.notificationCount > 0) {
                WpText(
                    inputs.notificationCount.coerceAtMost(99).toString(),
                    style = if (size == TileSize.Small) WpType.Small.copy(fontSize = 12.sp) else WpType.Large,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = if (size == TileSize.Small) 2.dp else 4.dp),
                )
            }
        }
    }
    val recent = inputs.recent
    if (recent.isEmpty() || size == TileSize.Small || !inputs.liveEnabled) {
        front()
        return
    }
    // Social live tile: the front shows the icon and counter; the back cycles through the
    // latest notifications with the sender's picture, like the WP People-style app tiles.
    FlipTile(enabled = true, seed = seed, front = front, back = {
        CycleTile(recent, enabled = recent.size > 1, seed = seed + 7) { n -> SocialFace(n, size) }
    })
}

@Composable
private fun SocialFace(n: WpNotification, size: TileSize) {
    val context = LocalContext.current
    val avatar by produceState<ImageBitmap?>(null, n.key) {
        value = n.picture?.asImageBitmap() ?: try {
            n.largeIcon?.loadDrawable(context)?.let { d ->
                val w = d.intrinsicWidth.coerceIn(48, 256)
                val h = d.intrinsicHeight.coerceIn(48, 256)
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
                    val c = android.graphics.Canvas(b)
                    d.setBounds(0, 0, w, h)
                    d.draw(c)
                }.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }
    val image = avatar
    if (size == TileSize.Medium && image != null) {
        // Medium: the sender's photo fills the tile, name and message on a scrim.
        Box(Modifier.fillMaxSize()) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                WpText(n.title, style = WpType.Small.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                WpText(n.text, style = WpType.Small, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    Row(Modifier.fillMaxSize()) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxHeight().aspectRatio(1f), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
            WpText(n.title, style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(n.text, style = WpType.Small, color = Color.White, maxLines = if (size == TileSize.Medium) 4 else 3, overflow = TextOverflow.Ellipsis)
        }
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
