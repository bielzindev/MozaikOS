package com.wpreborn.launcher.actioncenter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app's icon as a square, the Metro way: without Android's rounded mask. Apps with a
 * monochrome (themed) icon show it in white on [accent]; others show their artwork square.
 */
@Composable
fun SquareAppIcon(packageName: String, size: Dp, accent: Color, modifier: Modifier = Modifier, plain: Boolean = false) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName, px, accent) {
        value = withContext(Dispatchers.IO) { runCatching { render(context, packageName, px, accent.toArgb(), plain) }.getOrNull() }
    }
    icon?.let { Image(it, null, modifier.size(size)) }
}

private fun render(context: Context, pkg: String, px: Int, accent: Int, plain: Boolean): ImageBitmap {
    val d = context.packageManager.getApplicationIcon(pkg)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (plain) {
        // A white Metro glyph: the monochrome icon, else the silhouette of the artwork.
        val white = android.graphics.Color.WHITE
        if (d is AdaptiveIconDrawable) {
            val glyph = (if (Build.VERSION.SDK_INT >= 33) d.monochrome else null) ?: d.foreground
            glyph?.let { it.mutate().setTint(white); layer(it, c, px) }
        } else {
            d.mutate().setTint(white)
            d.setBounds(0, 0, px, px)
            d.draw(c)
        }
        return bmp.asImageBitmap()
    }
    if (d is AdaptiveIconDrawable) {
        // Layers are 108 dp with the visible 72 dp in the middle: crop to that, no mask.
        val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
        if (mono != null) {
            c.drawColor(accent)
            mono.mutate().setTint(android.graphics.Color.WHITE)
            layer(mono, c, px)
        } else {
            d.background?.let { layer(it, c, px) }
            d.foreground?.let { layer(it, c, px) }
        }
    } else {
        d.setBounds(0, 0, px, px)
        d.draw(c)
    }
    return bmp.asImageBitmap()
}

private fun layer(d: Drawable, c: Canvas, px: Int) {
    val extra = px / 4
    d.setBounds(-extra, -extra, px + extra, px + extra)
    d.draw(c)
}
