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
 * The motion of the Windows Phone popups, from the Windows Phone Toolkit storyboards (Ms-PL):
 * SwivelForwardIn (RotationX -45 -> 0 in 0.35 s, ExponentialEase EaseOut, Exponent 6, opacity 1 at
 * once) and SwivelForwardOut (RotationX 0 -> 90 in 0.25 s, ExponentialEase EaseIn, Exponent 15,
 * opacity 0 at the end), turning round the panel's middle (PlaneProjection's default centre).
 * [close] plays the way out and then runs what was asked for.
 */
@androidx.compose.runtime.Stable
private class OverlayMotion(private val scope: kotlinx.coroutines.CoroutineScope) {
    val enter = androidx.compose.animation.core.Animatable(0f)
    val leave = androidx.compose.animation.core.Animatable(0f)
    var closing by mutableStateOf(false)
        private set

    suspend fun open() {
        enter.animateTo(1f, tween(com.wpreborn.core.motion.WpDurations.SwivelIn, easing = androidx.compose.animation.core.LinearEasing))
    }

    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            leave.animateTo(1f, tween(com.wpreborn.core.motion.WpDurations.SwivelOut, easing = androidx.compose.animation.core.LinearEasing))
            then()
        }
    }

    /** Compose's rotationX for the toolkit's RotationX (the axes have opposite signs). */
    fun rotationX(): Float =
        if (leave.value > 0f) -90f * WpEasing.ExpIn15.transform(leave.value)
        else 45f * (1f - WpEasing.ExpOut.transform(enter.value))

    fun alpha(): Float = if (leave.value >= 1f) 0f else 1f

    /** The classic Mozaik motion: 0..1 in (exponential out), back to 0 on the way out (exponential in). */
    fun classicProgress(): Float =
        if (leave.value > 0f) 1f - WpEasing.ExpIn.transform(leave.value) else WpEasing.ExpOut.transform(enter.value)
}

@Composable
private fun rememberOverlayMotion(): OverlayMotion {
    val scope = rememberCoroutineScope()
    val motion = remember { OverlayMotion(scope) }
    LaunchedEffect(Unit) { motion.open() }
    return motion
}

/** The swivel on a panel: [motion] drives it, turning round its middle like a PlaneProjection. */
private fun Modifier.swivel(motion: OverlayMotion): Modifier = this.graphicsLayer {
    cameraDistance = 10f * density
    if (com.wpreborn.core.motion.WpMotion.classic) {
        // Classic: hinged at the top, from -60°.
        val p = motion.classicProgress()
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        rotationX = (1f - p) * -60f
        alpha = minOf(1f, p * 2f)
    } else {
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
        rotationX = motion.rotationX()
        alpha = motion.alpha()
    }
}

/** The dimmed page behind a popup: there at once, gone when the popup has left. */
private fun Modifier.dimmed(max: Float, motion: OverlayMotion): Modifier = this.drawBehind {
    val f = if (com.wpreborn.core.motion.WpMotion.classic) motion.classicProgress() else 1f - motion.leave.value
    drawRect(Color.Black.copy(alpha = max * f.coerceIn(0f, 1f)))
}

/** Where a [WpPanelOverlay]'s panel sits: swivelling at the top or in the middle, or the bottom menu. */
enum class PanelEdge { Top, Center, Bottom }

/**
 * A panel over a dimmed page for the menus and small dialogs that aren't a message box or a context
 * menu. A top or middle panel swivels like a message box (in and out); a bottom menu rises from the
 * bottom edge like the application bar's menu (the 8.1 library's edge UI: 367 ms, decelerating), and
 * goes back down. Back, a tap outside and the panel's own buttons all play the way out before anything
 * happens: [content] gets `close { … }`, which animates out and then runs the block.
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
    val scope = rememberCoroutineScope()
    // The bottom menu has its own slide (edge UI), separate from the swivel's clock.
    val classic = com.wpreborn.core.motion.WpMotion.classic
    val slide = remember { androidx.compose.animation.core.Animatable(if (edge == PanelEdge.Bottom && !classic) 0f else 1f) }
    LaunchedEffect(Unit) { if (edge == PanelEdge.Bottom && !classic) slide.animateTo(1f, tween(367, easing = com.wpreborn.core.motion.WpEasing.Decelerate)) }
    fun close(then: () -> Unit) {
        if (edge == PanelEdge.Bottom && !classic) {
            if (motion.closing) return
            scope.launch { slide.animateTo(0f, tween(367, easing = com.wpreborn.core.motion.WpEasing.Decelerate)) }
        }
        motion.close(then)
    }
    BackHandler { close(onDismiss) }
    val align = when (edge) {
        PanelEdge.Top -> Alignment.TopCenter
        PanelEdge.Center -> Alignment.Center
        PanelEdge.Bottom -> Alignment.BottomCenter
    }
    Box(
        Modifier
            .fillMaxSize()
            .dimmed(scrim, motion)
            .clickable(remember { MutableInteractionSource() }, null) { close(onDismiss) },
    ) {
        Column(
            Modifier
                .align(align)
                .then(
                    if (edge == PanelEdge.Bottom) {
                        Modifier.graphicsLayer {
                            val p = if (classic) motion.classicProgress() else slide.value
                            translationY = (1f - p) * size.height
                        }
                    } else {
                        Modifier.swivel(motion)
                    },
                )
                .fillMaxWidth()
                .then(if (maxHeight != Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier)
                .background(background)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .then(if (insetTop) Modifier.statusBarsPadding() else Modifier)
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(padding),
        ) { content { action -> close(action) } }
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
 * semibold title, a message and up to two buttons, over a dimmed page. It swivels in and out
 * (the toolkit's SwivelForwardIn / SwivelForwardOut).
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
                .swivel(motion)
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
 * The Windows Phone context menu, from the Windows Phone Toolkit (ContextMenu.cs and its
 * template in Generic.xaml, Ms-PL): a full-width panel next to the long-pressed element, in the
 * opposite of the theme (white with black text on the dark theme), over a page dimmed to 0.3 in
 * 0.42 s (ExponentialEase EaseInOut). It opens as a thin line that stretches across from the left
 * in 0.3 s and then drops open in 0.12 s (linear); it closes at once.
 *
 * @param anchorY window Y (px) of the long-pressed element's bottom edge.
 */
@Composable
fun WpContextMenu(items: List<ContextMenuItem>, anchorY: Float, onDismiss: () -> Unit) {
    if (com.wpreborn.core.motion.WpMotion.classic) {
        ClassicContextMenu(items, anchorY, onDismiss)
        return
    }
    BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val open = remember { androidx.compose.animation.core.Animatable(0f) }
    val fade = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(0.3f, tween(420, easing = WpEasing.ExpInOut2)) }
        open.animateTo(1f, tween(420, easing = androidx.compose.animation.core.LinearEasing))
    }
    val dark = Wp.palette.isDark
    val panel = if (dark) Color.White else Color.Black
    val text = if (dark) Color.Black else Color.White
    val border = if (dark) Color.Black else Color.White
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = fade.value)) }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    ) {
        val menuHeight = with(density) { (items.size * 52 + 50).dp.toPx() }
        val maxY = with(density) { maxHeight.toPx() }
        val below = anchorY + menuHeight < maxY
        val y = if (below) anchorY else (anchorY - menuHeight).coerceAtLeast(0f)
        Column(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(0, y.toInt()) }
                .fillMaxWidth()
                // ScaleX 0 -> 1 in the first 0.3 s, from the left edge.
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = (open.value * 420f / 300f).coerceIn(0f, 1f)
                },
        ) {
            if (below) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(border))
                Box(Modifier.fillMaxWidth().height(2.dp).background(panel))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    // ScaleY 0 until 0.3 s, then 0 -> 1 by 0.42 s.
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (below) 0f else 1f)
                        scaleY = ((open.value * 420f - 300f) / 120f).coerceIn(0f, 1f)
                    }
                    .background(panel)
                    .padding(top = 13.dp, bottom = 21.dp),
            ) {
                items.forEach { item ->
                    WpText(
                        item.label.lowercase(),
                        style = WpType.Large.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light),
                        color = if (item.enabled) text else text.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wpClickable(enabled = item.enabled, tilt = false) {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(start = 19.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }
            if (!below) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(panel))
                Box(Modifier.fillMaxWidth().height(2.dp).background(border))
            }
        }
    }
}

/** The classic Mozaik context menu: a chrome panel that swivels open and shut, the page dimmed. */
@Composable
private fun ClassicContextMenu(items: List<ContextMenuItem>, anchorY: Float, onDismiss: () -> Unit) {
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
                .swivel(motion)
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
        if (appTitle != null) WpText(appTitle.uppercase(), style = WpType.AppTitle, maxLines = 1, softWrap = false)
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
