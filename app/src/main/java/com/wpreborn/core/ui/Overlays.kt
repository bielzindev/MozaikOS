package com.wpreborn.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.drawBehind
import com.wpreborn.core.motion.turnstile
import androidx.compose.ui.graphics.graphicsLayer
import com.wpreborn.core.motion.swivelWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.wpreborn.core.motion.WpEasing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.motion.swivelIn
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType

/**
 * The motion of the Windows Phone popups: they swivel in (the toolkit's SwivelTransition) and
 * leave by the same motion backwards, over the dimmed page. [progress] runs 0 to 1 on the way in
 * and back to 0 on the way out; [close] plays that and then runs what was asked for.
 */
@androidx.compose.runtime.Stable
private class OverlayMotion(private val scope: kotlinx.coroutines.CoroutineScope) {
    val progress = androidx.compose.animation.core.Animatable(0f)
    var closing by mutableStateOf(false)
        private set

    suspend fun open() {
        progress.animateTo(1f, tween(com.wpreborn.core.motion.WpDurations.SlideIn, easing = WpEasing.ExpOut))
    }

    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(com.wpreborn.core.motion.WpDurations.TurnstileOut, easing = WpEasing.ExpIn))
            then()
        }
    }
}

@Composable
private fun rememberOverlayMotion(): OverlayMotion {
    val scope = rememberCoroutineScope()
    val motion = remember { OverlayMotion(scope) }
    LaunchedEffect(Unit) { motion.open() }
    return motion
}

/** The dimmed page behind a popup, as strong as the popup is in. */
private fun Modifier.dimmed(max: Float, motion: OverlayMotion): Modifier = this.drawBehind {
    drawRect(Color.Black.copy(alpha = max * motion.progress.value.coerceIn(0f, 1f)))
}

/** Where a [WpPanelOverlay]'s panel sits: dropping from the top or the middle, or the bottom menu. */
enum class PanelEdge { Top, Center, Bottom }

/**
 * A panel over a dimmed page for the menus and small dialogs that aren't a message box or a context
 * menu. A top or middle panel swivels like those (and leaves the same way backwards); a bottom menu
 * rises from the bottom edge like the application bar's menu, and goes back down. Back, a tap
 * outside and the panel's own buttons all play the way out before anything happens: [content] gets
 * `close { … }`, which animates out and then runs the block.
 */
@Composable
fun WpPanelOverlay(
    onDismiss: () -> Unit,
    edge: PanelEdge = PanelEdge.Top,
    scrim: Float = 0.6f,
    background: Color = Wp.palette.chrome,
    padding: PaddingValues = PaddingValues(),
    maxHeight: Dp = Dp.Unspecified,
    scroll: Boolean = false,
    insetTop: Boolean = false,
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit,
) {
    val motion = rememberOverlayMotion()
    BackHandler { motion.close(onDismiss) }
    val align = when (edge) {
        PanelEdge.Top -> Alignment.TopCenter
        PanelEdge.Center -> Alignment.Center
        PanelEdge.Bottom -> Alignment.BottomCenter
    }
    Box(
        Modifier
            .fillMaxSize()
            .dimmed(scrim, motion)
            .clickable(remember { MutableInteractionSource() }, null) { motion.close(onDismiss) },
    ) {
        Column(
            Modifier
                .align(align)
                .then(
                    if (edge == PanelEdge.Bottom) {
                        Modifier.graphicsLayer { translationY = (1f - motion.progress.value) * size.height }
                    } else {
                        Modifier.swivelWith { motion.progress.value }
                    },
                )
                .fillMaxWidth()
                .then(if (maxHeight != Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier)
                .background(background)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .then(if (insetTop) Modifier.statusBarsPadding() else Modifier)
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(padding),
        ) { content { action -> motion.close(action) } }
    }
}

/**
 * A full page over the one below (the music queue): it comes in with the turnstile, as a page
 * does in Windows Phone, and leaves with the backward turnstile. [content] gets `close { … }`
 * like [WpPanelOverlay].
 */
@Composable
fun WpFullScreenOverlay(
    onDismiss: () -> Unit,
    background: Color = Wp.palette.background,
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val turn = remember { com.wpreborn.core.motion.TurnstileState(com.wpreborn.core.motion.TurnstileMode.ForwardIn) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { turn.run(com.wpreborn.core.motion.TurnstileMode.ForwardIn) }
    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            turn.run(com.wpreborn.core.motion.TurnstileMode.BackwardOut)
            then()
        }
    }
    BackHandler { close(onDismiss) }
    Column(
        Modifier
            .fillMaxSize()
            .turnstile(turn)
            .background(background)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) { content { action -> close(action) } }
}

/**
 * The Windows Phone MessageBox: a chrome panel anchored to the top of the screen with a
 * semibold title, a message and up to two buttons, over a dimmed page. It swivels in and,
 * when closed, swivels out the same way.
 */
@Composable
fun WpMessageBox(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String? = null,
    onDismiss: () -> Unit,
) {
    val motion = rememberOverlayMotion()
    BackHandler { motion.close(onDismiss) }
    Box(
        Modifier
            .fillMaxSize()
            .dimmed(0.6f, motion)
            .clickable(remember { MutableInteractionSource() }, null) { motion.close(onDismiss) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .swivelWith { motion.progress.value }
                .background(Wp.palette.chrome)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        ) {
            WpText(title, style = WpType.Large.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            Spacer(Modifier.height(12.dp))
            WpText(message, style = WpType.Normal)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WpButton(confirm, Modifier.weight(1f), onClick = { motion.close(onConfirm) })
                if (dismiss != null) WpButton(dismiss, Modifier.weight(1f), onClick = { motion.close(onDismiss) })
            }
        }
    }
}

data class ContextMenuItem(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The 8.1 context menu: a full-width chrome panel that swivels open next to the element
 * that was long-pressed (and swivels shut the same way), everything else dimmed.
 *
 * @param anchorY window Y (px) of the long-pressed element's bottom edge.
 */
@Composable
fun WpContextMenu(items: List<ContextMenuItem>, anchorY: Float, onDismiss: () -> Unit) {
    val motion = rememberOverlayMotion()
    BackHandler { motion.close(onDismiss) }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .dimmed(0.5f, motion)
            .clickable(remember { MutableInteractionSource() }, null) { motion.close(onDismiss) },
    ) {
        val menuHeight = with(density) { (items.size * 52 + 24).dp.toPx() }
        val maxY = with(density) { maxHeight.toPx() }
        val y = if (anchorY + menuHeight < maxY) anchorY else (anchorY - menuHeight).coerceAtLeast(0f)
        Column(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(0, y.toInt()) }
                .fillMaxWidth()
                .swivelWith { motion.progress.value }
                .background(Wp.palette.chrome)
                .padding(vertical = 12.dp),
        ) {
            items.forEach { item ->
                WpText(
                    item.label.lowercase(),
                    style = WpType.Large,
                    color = if (item.enabled) Wp.palette.foreground else Wp.palette.disabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .wpClickable(enabled = item.enabled, tilt = false) {
                            motion.close {
                                onDismiss()
                                item.onClick()
                            }
                        }
                        .padding(start = 20.dp, top = 10.dp),
                )
            }
        }
    }
}

/**
 * The jump list: tapping a group letter zooms out to a grid of all letters; letters with
 * no items are dimmed.
 */
@Composable
fun JumpList(
    letters: List<String>,
    available: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Wp.palette.background.copy(alpha = 0.92f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(letters) { l ->
                val enabled = l in available
                Box(Modifier.slideInStaggered(letters.indexOf(l) / 4)) {
                    LetterTile(
                        l,
                        size = 78.dp,
                        filled = true,
                        enabled = enabled,
                        modifier = if (enabled) Modifier.wpClickable { onPick(l) } else Modifier,
                    )
                }
            }
        }
    }
}

/** A small "page" scaffold: app title + big header, content below. */
@Composable
fun WpPageHeader(appTitle: String?, header: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp)) {
        if (appTitle != null) WpText(appTitle.uppercase(), style = WpType.AppTitle)
        if (header != null) {
            WpText(
                header.lowercase(),
                style = WpType.Header,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp).slideInStaggered(0, distanceDp = 40f),
            )
        }
    }
}

@Composable
fun HSpacer(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun VSpacer(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 8.dp, end = 20.dp), contentAlignment = Alignment.TopStart) {
        WpText(text, style = WpType.Large, color = Wp.palette.subtle)
    }
}
