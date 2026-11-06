package com.wpreborn.launcher.start

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.IconCustom
import com.wpreborn.data.apps.IconPack
import com.wpreborn.data.apps.IconPrefs
import com.wpreborn.data.tiles.TileSize
import com.wpreborn.data.tiles.TileSpec

/**
 * Everything about one tile in a single page, opened from the pencil on a tile in Start's edit
 * mode: a live preview at the tile's real shape, its size, its colour (a tap applies it at once
 * and the page stays open, so colours can be tried), a colour of your own, and the icon style
 * (Mozaik, Metro or the app's original icon) for apps. Everything is saved as it changes.
 */
@Composable
fun TileEditor(
    tile: TileSpec,
    app: AppEntry?,
    title: String,
    allowLarge: Boolean,
    onColor: (AccentColor?) -> Unit,
    onCustomColor: (Int) -> Unit,
    onSize: (TileSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { IconPrefs.get(context) }
    val custom = rememberIconCustom(app)
    val brand = rememberBrandColor(app)
    BackHandler(onBack = onDismiss)

    // The colour of your own: sliders start from the tile's colour; the preview follows them
    // while they move and the tile takes the colour when the finger lets go.
    val start = tile.fixedColor ?: brand ?: Wp.accent
    val hsv = remember(tile.id) { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toArgb(), it) } }
    var hue by remember(tile.id) { mutableFloatStateOf(hsv[0] / 360f) }
    var sat by remember(tile.id) { mutableFloatStateOf(hsv[1]) }
    var value by remember(tile.id) { mutableFloatStateOf(hsv[2]) }
    var sliding by remember(tile.id) { mutableStateOf(false) }
    val mine = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, sat, value)))
    val shown = if (sliding) mine else start

    Column(
        Modifier
            .fillMaxSize()
            .background(Wp.palette.background)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        WpText(stringResource(R.string.tile_editor_title).uppercase(), style = WpType.AppTitle)
        WpText(title.lowercase(), style = WpType.Header, maxLines = 1, modifier = Modifier.padding(bottom = 12.dp))

        // Live preview, at the shape of the tile.
        val (pw, ph) = when (tile.size) {
            TileSize.Small -> 80.dp to 80.dp
            TileSize.Medium -> 160.dp to 160.dp
            TileSize.Wide -> 320.dp to 160.dp
            TileSize.Large -> 250.dp to 250.dp
        }
        Box(Modifier.size(pw, ph).background(shown), contentAlignment = Alignment.Center) {
            if (app != null) {
                AppIcon(app, if (tile.size == TileSize.Small) 36.dp else if (custom.usesMetro()) 64.dp else 80.dp)
            } else {
                WpIcon(WpIcons.Folder, tint = Color.White, size = if (tile.size == TileSize.Small) 36.dp else 64.dp)
            }
        }

        Heading(stringResource(R.string.tile_size))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sizes = buildList {
                add(TileSize.Small to R.string.tile_size_small)
                add(TileSize.Medium to R.string.tile_size_medium)
                add(TileSize.Wide to R.string.tile_size_wide)
                if (allowLarge) add(TileSize.Large to R.string.tile_size_large)
            }
            sizes.forEach { (size, label) ->
                OptionBox(stringResource(label), tile.size == size, Modifier.weight(1f)) { onSize(size) }
            }
        }

        Heading(stringResource(R.string.tile_color))
        OptionBox(stringResource(R.string.tile_color_default), tile.color == null && tile.customColor == null, Modifier.width(180.dp)) {
            sliding = false
            onColor(null)
        }
        Spacer(Modifier.height(10.dp))
        AccentColor.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(c.color)
                            .then(if (tile.color == c && !sliding) Modifier.border(3.dp, Wp.palette.foreground) else Modifier)
                            .wpClickable {
                                sliding = false
                                onColor(c)
                            },
                    )
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Heading(stringResource(R.string.tile_color_custom))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(mine).then(if (tile.customColor != null && !sliding) Modifier.border(3.dp, Wp.palette.foreground) else Modifier))
            Spacer(Modifier.width(14.dp))
            WpText("#%06X".format(mine.toArgb() and 0xFFFFFF), style = WpType.Large)
        }
        val commit = {
            sliding = false
            onCustomColor(mine.toArgb())
        }
        WpText(stringResource(R.string.tile_color_hue), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
        WpSlider(hue, { hue = it; sliding = true }, onValueChangeFinished = commit)
        WpText(stringResource(R.string.tile_color_saturation), style = WpType.Small, color = Wp.palette.subtle)
        WpSlider(sat, { sat = it; sliding = true }, onValueChangeFinished = commit)
        WpText(stringResource(R.string.tile_color_brightness), style = WpType.Small, color = Wp.palette.subtle)
        WpSlider(value, { value = it; sliding = true }, onValueChangeFinished = commit)

        if (app != null) {
            Heading(stringResource(R.string.icon_pack))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionBox(stringResource(R.string.icon_pack_default), custom.pack == IconPack.Default, Modifier.weight(1f)) { prefs.set(app.key, custom.copy(pack = IconPack.Default)) }
                OptionBox(stringResource(R.string.icons_metro), custom.pack == IconPack.Metro, Modifier.weight(1f)) { prefs.set(app.key, custom.copy(pack = IconPack.Metro)) }
                OptionBox(stringResource(R.string.icons_original), custom.pack == IconPack.Original, Modifier.weight(1f)) { prefs.set(app.key, custom.copy(pack = IconPack.Original)) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WpButton(stringResource(R.string.icon_done), onClick = onDismiss)
            WpButton(stringResource(R.string.icon_reset)) {
                sliding = false
                onColor(null)
                if (app != null) prefs.set(app.key, IconCustom())
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Heading(text: String) {
    WpText(text.uppercase(), style = WpType.AppTitle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
}

@Composable
private fun OptionBox(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .border(2.dp, if (selected) Wp.accent else Wp.palette.foreground)
            .background(if (selected) Wp.accent else Color.Transparent)
            .wpClickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        WpText(label, style = WpType.Small, color = if (selected) Color.White else Wp.palette.foreground, maxLines = 2)
    }
}
