package com.wpreborn.launcher.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.wpreborn.core.icons.MetroIconPack
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.AppRepository

/**
 * The icon of an app on a tile or in the app list.
 *
 * Metro style (default): a ZUNEX glyph from [MetroIconPack]; otherwise the app's own
 * monochrome/silhouette icon in white; otherwise the first letter of its name, Metro-style.
 * Original style: the Android launcher icon.
 */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val builtIn = app.builtIn
    if (builtIn != null) {
        WpIcon(builtIn.icon, modifier, tint = tint, size = size)
        return
    }
    val metro = Wp.settings.metroIcons
    val glyph = remember(app.packageName, app.label, app.category) {
        MetroIconPack.glyphFor(app.packageName, app.label, app.category)
    }
    if (metro && glyph != null) {
        WpIcon(glyph, modifier, tint = tint, size = size)
        return
    }
    val context = LocalContext.current
    val repo = remember { AppRepository.get(context) }
    val px = with(LocalDensity.current) { size.roundToPx() }
    if (metro) {
        // Not in the pack: the app's own logo, without the round mask.
        val brand by produceState(repo.cachedBrandIcon(app, px), app.key, px) {
            if (value == null) value = repo.brandIcon(app, px)
        }
        brand?.let { Image(it.logo.asImageBitmap(), null, modifier.size(size)) }
        return
    }
    val icon by produceState<ImageBitmap?>(repo.cachedIcon(app, px)?.asImageBitmap(), app.key, px) {
        if (value == null) value = repo.icon(app, px)?.asImageBitmap()
    }
    icon?.let { Image(it, null, modifier.size(size)) }
}

/**
 * Tile colour for an app in the Metro style: its brand colour (the background of its
 * adaptive icon), or null to use the accent. Apps drawn with a ZUNEX glyph keep the accent.
 */
@Composable
fun rememberBrandColor(app: AppEntry?): Color? {
    if (app == null || app.builtIn != null || !Wp.settings.metroIcons || !Wp.settings.brandColors) return null
    if (remember(app.packageName) { MetroIconPack.glyphFor(app.packageName, app.label, app.category) } != null) return null
    val context = LocalContext.current
    val repo = remember { AppRepository.get(context) }
    val px = with(LocalDensity.current) { 48.dp.roundToPx() }
    val brand by produceState(repo.cachedBrandIcon(app, px), app.key) {
        if (value == null) value = repo.brandIcon(app, px)
    }
    return brand?.color?.let { Color(it) }
}
