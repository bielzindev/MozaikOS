package com.wpreborn.launcher.start

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.wpreborn.core.icons.MetroIconPack
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.AppRepository
import com.wpreborn.data.apps.IconCustom
import com.wpreborn.data.apps.IconPack
import com.wpreborn.data.apps.IconPrefs
import com.wpreborn.data.apps.IconTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icon tint chosen for the app whose tile is being drawn (used by built-in live tiles). */
val LocalTileIconTint = staticCompositionLocalOf<Color?> { null }

/** The colour of the tile being drawn, for faces that paint shades of it (People). */
val LocalTileColor = staticCompositionLocalOf<Color?> { null }

/** The app's icon settings (per app, by its permanent key). */
@Composable
fun rememberIconCustom(app: AppEntry?): IconCustom {
    val context = LocalContext.current
    val all by remember { IconPrefs.get(context) }.all.collectAsState()
    return app?.let { all[it.key] } ?: IconCustom()
}

/**
 * The colour to draw the icon in, or null to keep its own colours. Icons keep their colours:
 * colour belongs to the tile (palette button in edit mode), so a saved tint is ignored.
 */
@Suppress("UnusedReceiverParameter")
fun IconCustom.tintColor(): Color? = null

/** Whether this app uses the Metro pack (its own choice, else the global one). */
@Composable
fun IconCustom.usesMetro(): Boolean = when (pack) {
    IconPack.Metro -> true
    IconPack.Original -> false
    IconPack.Default -> Wp.settings.metroIcons
}

/**
 * The icon of an app on a tile or in the app list, following the app's own icon settings.
 *
 * Metro: a ZUNEX glyph from [MetroIconPack] (white, or the chosen colour); apps outside the
 * pack show their own logo without the round mask. Original: the Android launcher icon.
 * A chosen colour recolours the artwork; "default" keeps its own colours.
 *
 * All loaded artwork is remembered per app ([AppEntry.key]), never per position, so tiles
 * that move keep their own icons.
 */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val custom = rememberIconCustom(app)
    val metro = custom.usesMetro()
    val chosen = custom.tintColor()
    val builtIn = app.builtIn
    if (builtIn != null && metro) {
        WpIcon(builtIn.icon, modifier, tint = chosen ?: tint, size = size)
        return
    }
    val glyph = remember(app.packageName, app.label, app.category) {
        if (builtIn != null) null else MetroIconPack.glyphFor(app.packageName, app.label, app.category)
    }
    // Chosen "Metro" for this app: the Mozaik glyph first.
    if (metro && glyph != null && custom.pack == IconPack.Metro) {
        WpIcon(glyph, modifier, tint = chosen ?: tint, size = size)
        return
    }
    val context = LocalContext.current
    val repo = remember { AppRepository.get(context) }
    val px = with(LocalDensity.current) { size.roundToPx() }
    val filter = chosen?.let { ColorFilter.tint(it) }
    if (metro) {
        // By default other apps use Android's own monochrome (themed) icon, drawn white like a
        // Metro glyph; only Mozaik's apps use the Metro glyphs. Without a monochrome icon: the
        // Metro glyph when there is one, else the app's logo without the round mask.
        var brand by remember(app.key, px) { mutableStateOf(repo.cachedBrandIcon(app, px)) }
        var loaded by remember(app.key, px) { mutableStateOf(brand != null) }
        LaunchedEffect(app.key, px) {
            if (brand == null) brand = repo.brandIcon(app, px)
            loaded = true
        }
        val b = brand
        when {
            b != null && b.mono -> Image(b.logo.asImageBitmap(), null, modifier.size(size), colorFilter = ColorFilter.tint(chosen ?: tint))
            glyph != null && loaded -> WpIcon(glyph, modifier, tint = chosen ?: tint, size = size)
            b != null -> Image(b.logo.asImageBitmap(), null, modifier.size(size), colorFilter = filter)
        }
        return
    }
    // Original: the app's artwork without the rounded mask (the tile carries its colour).
    var icon by remember(app.key, px) { mutableStateOf(if (builtIn == null) repo.cachedTileIcon(app, px) else null) }
    LaunchedEffect(app.key, px) {
        if (icon == null) {
            icon = if (builtIn == null) {
                repo.tileIcon(app, px) ?: repo.icon(app, px)
            } else {
                // A ZUNEX app shown with its Android launcher icon.
                withContext(Dispatchers.IO) {
                    runCatching { app.component?.let { context.packageManager.getActivityIcon(it).toBitmap(px, px) } }.getOrNull()
                }
            }
        }
    }
    icon?.let { bmp: Bitmap -> Image(bmp.asImageBitmap(), null, modifier.size(size), colorFilter = filter) }
}

/**
 * Tile colour for an Android app (null = the accent):
 * - original icon: the colour of the icon itself (its adaptive background, else its main
 *   vivid colour), so the tile and the icon are one colour, like the app's own brand tile;
 * - Metro: its brand colour (adaptive background) when "brand colours" is on; apps drawn
 *   with a ZUNEX glyph keep the accent.
 */
@Composable
fun rememberBrandColor(app: AppEntry?): Color? {
    val custom = rememberIconCustom(app)
    if (app == null || app.builtIn != null) return null
    val metro = custom.usesMetro()
    if (metro && !Wp.settings.brandColors) return null
    val hasGlyph = remember(app.packageName) { MetroIconPack.glyphFor(app.packageName, app.label, app.category) } != null
    if (metro && hasGlyph && custom.pack == IconPack.Metro) return null
    val context = LocalContext.current
    val repo = remember { AppRepository.get(context) }
    val px = with(LocalDensity.current) { 48.dp.roundToPx() }
    var brand by remember(app.key) { mutableStateOf(repo.cachedBrandIcon(app, px)) }
    LaunchedEffect(app.key) { if (brand == null) brand = repo.brandIcon(app, px) }
    val b = brand ?: return null
    // Drawn with a Metro glyph (no monochrome icon): the accent, as before.
    if (metro && hasGlyph && !b.mono) return null
    // A white monochrome glyph reads on the icon's main colour even when its background is white.
    return (if (metro && !b.mono) b.color else b.color ?: b.dominant)?.let { Color(it) }
}
