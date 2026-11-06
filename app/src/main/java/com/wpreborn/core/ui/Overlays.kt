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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
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
 * What the Windows Phone MessageBox and context menu did on the way out: the panel slides back up
 * and fades while the dimmed page clears, and only then is the action taken. [close] plays that
 * (once) and calls [then] afterwards; [scrim] is the dimming's strength, in and out.
 */
private class OverlayExit(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val closingState: androidx.compose.runtime.MutableState<Boolean>,
) {
    val closing get() = closingState.value

    fun close(then: () -> Unit) {
        if (closingState.value) return
        closingState.value = true
        scope.launch {
            delay(190)
            then()
        }
    }
}

@Composable
private fun rememberOverlayExit(): OverlayExit {
    val scope = rememberCoroutineScope()
    val closing = remember { mutableStateOf(false) }
    return remember { OverlayExit(scope, closing) }
}

/**
 * The Windows Phone MessageBox: a chrome panel anchored to the top of the screen with a
 * semibold title, a message and up to two buttons, over a dimmed page. It swivels in.
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
    val exit = rememberOverlayExit()
    BackHandler { exit.close(onDismiss) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(if (shown && !exit.closing) 0.6f else 0f, tween(200), label = "scrim")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .clickable(remember { MutableInteractionSource() }, null) { exit.close(onDismiss) },
    ) {
        AnimatedVisibility(
            visible = !exit.closing,
            enter = EnterTransition.None,
            exit = fadeOut(tween(160)) + slideOutVertically(tween(190, easing = WpEasing.ExpIn)) { -it / 4 },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .swivelIn()
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
                    WpButton(confirm, Modifier.weight(1f), onClick = { exit.close(onConfirm) })
                    if (dismiss != null) WpButton(dismiss, Modifier.weight(1f), onClick = { exit.close(onDismiss) })
                }
            }
        }
    }
}

data class ContextMenuItem(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The 8.1 context menu: a full-width chrome panel that swivels open next to the element
 * that was long-pressed, everything else dimmed.
 *
 * @param anchorY window Y (px) of the long-pressed element's bottom edge.
 */
@Composable
fun WpContextMenu(items: List<ContextMenuItem>, anchorY: Float, onDismiss: () -> Unit) {
    val exit = rememberOverlayExit()
    BackHandler { exit.close(onDismiss) }
    val density = LocalDensity.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(if (shown && !exit.closing) 0.5f else 0f, tween(200), label = "scrim")
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .clickable(remember { MutableInteractionSource() }, null) { exit.close(onDismiss) },
    ) {
        val menuHeight = with(density) { (items.size * 52 + 24).dp.toPx() }
        val maxY = with(density) { maxHeight.toPx() }
        val y = if (anchorY + menuHeight < maxY) anchorY else (anchorY - menuHeight).coerceAtLeast(0f)
        AnimatedVisibility(
            visible = !exit.closing,
            enter = EnterTransition.None,
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180, easing = WpEasing.ExpIn)) { -it / 5 },
            modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(0, y.toInt()) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .swivelIn()
                    .background(Wp.palette.chrome)
                    .padding(vertical = 12.dp),
            ) {
                items.forEachIndexed { index, item ->
                    WpText(
                        item.label.lowercase(),
                        style = WpType.Large,
                        color = if (item.enabled) Wp.palette.foreground else Wp.palette.disabled,
                        modifier = Modifier
                            // The entries come in one after the other, a little behind the panel.
                            .slideInStaggered(index + 1, distanceDp = 18f)
                            .fillMaxWidth()
                            .height(52.dp)
                            .wpClickable(enabled = item.enabled, tilt = false) {
                                exit.close {
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
