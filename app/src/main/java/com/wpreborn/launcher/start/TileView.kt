package com.wpreborn.launcher.start

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.Image
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
    val latestNotification: WpNotification?,
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconDp = iconSizeFor(size) + if (size == TileSize.Small) 4.dp else 6.dp
    val px = with(density) { iconDp.roundToPx() }
    val icon by produceState<ImageBitmap?>(
        app?.let { AppRepository.get(context).cachedIcon(it, px)?.asImageBitmap() },
        app?.key, px,
    ) {
        if (app != null && value == null) value = AppRepository.get(context).icon(app, px)?.asImageBitmap()
    }
    val front: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            icon?.let { Image(it, null, Modifier.size(iconDp).align(Alignment.Center)) }
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
    val n = inputs.latestNotification
    if (n != null && size != TileSize.Small && inputs.liveEnabled) {
        FlipTile(enabled = true, seed = seed, front = front, back = {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                WpText(n.title, style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                WpText(n.text, style = WpType.Small, color = Color.White, maxLines = if (size == TileSize.Medium) 3 else 2, overflow = TextOverflow.Ellipsis)
            }
        })
    } else {
        front()
    }
}
