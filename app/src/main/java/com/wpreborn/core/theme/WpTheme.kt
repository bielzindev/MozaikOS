package com.wpreborn.core.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.wpreborn.data.prefs.SettingsRepository
import com.wpreborn.data.prefs.WpSettings

val LocalPalette = staticCompositionLocalOf { WpPalette.Dark }
val LocalAccent = staticCompositionLocalOf { AccentColor.Cobalt.color }
val LocalSettings = staticCompositionLocalOf { WpSettings() }

object Wp {
    val palette: WpPalette @Composable get() = LocalPalette.current
    val accent: Color @Composable get() = LocalAccent.current
    val settings: WpSettings @Composable get() = LocalSettings.current
}

/**
 * Root theme. Reads the persisted theme/accent and exposes them through composition locals.
 * Theme and accent changes cross-fade, like the theme switch on the phone.
 */
@Composable
fun WpTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings by repo.settings.collectAsState(initial = repo.cached)
    val target = if (settings.darkTheme) WpPalette.Dark else WpPalette.Light
    val anim = tween<Color>(durationMillis = 300)
    val bg by animateColorAsState(target.background, anim, label = "bg")
    val fg by animateColorAsState(target.foreground, anim, label = "fg")
    val accent by animateColorAsState(settings.accent.color, anim, label = "accent")
    val palette = target.copy(background = bg, foreground = fg)
    com.wpreborn.core.motion.WpMotion.classic = !settings.wpMotion
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalAccent provides accent,
        LocalSettings provides settings,
        LocalTextSelectionColors provides TextSelectionColors(accent, accent.copy(alpha = 0.4f)),
        content = content,
    )
}
