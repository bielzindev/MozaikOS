package com.wpreborn.apps.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
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
    content: @Composable () -> Unit,
) {
    val bg = if (background == Color.Unspecified) Wp.palette.background else background
    val wpBar = Wp.settings.wpStatusBar && showStatusBar
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
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
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
