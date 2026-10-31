package com.wpreborn.apps.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.ui.WpStatusBar
import com.wpreborn.launcher.actioncenter.ActionCenter
import com.wpreborn.launcher.actioncenter.ActionCenterHandle
import com.wpreborn.launcher.actioncenter.ActionCenterState
import com.wpreborn.launcher.volume.VolumeOverlay
import com.wpreborn.launcher.volume.VolumeState

/** System chrome shared by the launcher and every built-in app. */
class ChromeState {
    val actionCenter = ActionCenterState()
    val volume = VolumeState()
    /** Screens that already show the player (the full player, the camera) hide the Playing Now bar. */
    var hidePlayingNow by androidx.compose.runtime.mutableStateOf(false)
}

val LocalChrome = staticCompositionLocalOf<ChromeState?> { null }

/**
 * Root of every Windows Phone screen: page background, the WP status bar (or plain insets
 * when the user prefers the Android one), the Action Center pull-down and the volume flyout.
 */
@Composable
fun WpChrome(
    chrome: ChromeState = remember { ChromeState() },
    background: Color = Color.Unspecified,
    showStatusBar: Boolean = true,
    statusBarColor: Color = Color.Unspecified,
    imePadding: Boolean = true,
    showPlayingNow: Boolean = true,
    content: @Composable () -> Unit,
) {
    val bg = if (background == Color.Unspecified) Wp.palette.background else background
    val wpBar = Wp.settings.wpStatusBar && showStatusBar
    // Follow the status bar and theme switches live (Personalize / Settings) without a restart.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val settings = Wp.settings
    androidx.compose.runtime.LaunchedEffect(settings.wpStatusBar, settings.darkTheme) {
        activity?.let { WpActivity.applySystemBars(it, settings) }
    }
    CompositionLocalProvider(LocalChrome provides chrome) {
        Box(Modifier.fillMaxSize().background(bg)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (imePadding) Modifier.windowInsetsPadding(WindowInsets.ime) else Modifier),
            ) {
                if (wpBar) {
                    WpStatusBar(Modifier.topSystemPadding(), color = statusBarColor)
                } else {
                    Box(Modifier.statusBarsPadding().topSystemPadding())
                }
                // The system-wide Playing Now bar sits under every screen while music plays.
                val playback = com.wpreborn.data.media.rememberPlayback()
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                val imeOpen = WindowInsets.isImeVisible
                val bar = showPlayingNow && !chrome.hidePlayingNow && !imeOpen && playback != null
                Box(
                    Modifier.weight(1f).fillMaxWidth().then(
                        if (bar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier.navigationBarsPadding(),
                    ),
                ) { content() }
                androidx.compose.animation.AnimatedVisibility(
                    visible = bar,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                ) {
                    // Keep showing the last track while the bar slides away.
                    val last = remember { arrayOfNulls<com.wpreborn.data.media.Playback>(1) }
                    if (playback != null) last[0] = playback
                    last[0]?.let { PlayingNowBar(it, Modifier.navigationBarsPadding()) }
                }
            }
            if (wpBar) {
                ActionCenterHandle(chrome.actionCenter, Modifier.align(Alignment.TopCenter).topSystemPadding())
            }
            ActionCenter(chrome.actionCenter)
            VolumeOverlay(chrome.volume)
        }
    }
}

/** Top padding for the status bar area: the (possibly hidden) status bar or the display cutout. */
@Composable
fun Modifier.topSystemPadding(): Modifier = this.windowInsetsPadding(
    WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top),
)
