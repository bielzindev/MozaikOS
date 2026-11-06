package com.wpreborn.launcher.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.wpreborn.core.motion.WpDurations
import com.wpreborn.core.motion.WpEasing
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Flip tile template: the tile rotates around its horizontal axis to reveal the back face,
 * stays there for a while and flips back. Each tile starts at a random moment so the Start
 * screen never flips in lockstep, just like real live tiles.
 */
@Composable
fun FlipTile(
    enabled: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(enabled, seed) {
        if (!enabled) {
            angle.snapTo(0f)
            return@LaunchedEffect
        }
        val rnd = Random(seed)
        delay(2000L + rnd.nextLong(6000))
        while (true) {
            angle.animateTo(180f, flipSpec())
            delay(5000L + rnd.nextLong(3000))
            angle.animateTo(360f, flipSpec())
            angle.snapTo(0f)
            delay(6000L + rnd.nextLong(6000))
        }
    }
    val a = angle.value % 360f
    val showBack = a > 90f && a < 270f
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationX = a
                cameraDistance = 16f * density
            },
    ) {
        if (showBack) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationX = 180f }) { back() }
        } else {
            front()
        }
    }
}

/**
 * Cycle tile template (Photos, News): pictures slide up one after another.
 *
 * One ever-growing position drives it (its whole part is the picture shown, its fraction how
 * far the next one has come up), so there is never a frame with the wrong picture. Each
 * picture keeps its own composition (keyed) as it moves from "next" to "current", so it
 * doesn't reload or fade in again, and the next one is already loaded (just below, clipped).
 */
@Composable
fun <T> CycleTile(
    items: List<T>,
    enabled: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val pos = remember(items) { Animatable(0f) }
    LaunchedEffect(items, enabled) {
        if (!enabled || items.size < 2) return@LaunchedEffect
        val rnd = Random(seed)
        delay(1500L + rnd.nextLong(3000))
        while (true) {
            val target = kotlin.math.floor(pos.value) + 1f
            pos.animateTo(target, if (com.wpreborn.core.motion.WpMotion.classic) tween(900, easing = WpEasing.CubicInOut) else tween(WpDurations.Flip, easing = WpEasing.CubicOut))
            delay(4500L + rnd.nextLong(2500))
        }
    }
    if (items.isEmpty()) return
    val p = pos.value
    val whole = kotlin.math.floor(p).toInt()
    val frac = p - whole
    val n = items.size
    val shown = if (n < 2) listOf(0 to whole) else listOf(0 to whole, 1 to whole + 1)
    Box(modifier.fillMaxSize().clipToBounds()) {
        shown.forEach { (slot, i) ->
            val item = items[Math.floorMod(i, n)]
            key(i) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = (slot - frac) * size.height },
                ) { content(item) }
            }
        }
    }
}

/** A single cell of the People mosaic that flips to a different face now and then. */
@Composable
fun <T> FlippingCell(pool: List<T>, start: Int, seed: Int, enabled: Boolean, content: @Composable (T) -> Unit) {
    var index by remember(pool) { mutableIntStateOf(start) }
    val angle = remember { Animatable(0f) }
    LaunchedEffect(pool, enabled) {
        if (!enabled || pool.size < 2) return@LaunchedEffect
        val rnd = Random(seed)
        delay(3000L + rnd.nextLong(12000))
        while (true) {
            angle.animateTo(90f, tween(350, easing = WpEasing.ExpIn))
            index = (index + 7) % pool.size
            angle.snapTo(-90f)
            angle.animateTo(0f, tween(450, easing = WpEasing.ExpOut))
            delay(6000L + rnd.nextLong(14000))
        }
    }
    if (pool.isEmpty()) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationX = angle.value
                cameraDistance = 12f * density
            },
    ) { content(pool[index % pool.size]) }
}

/** The flip: classic 800 ms ease in-out, or the HubTile's 850 ms CubicEase out. */
private fun flipSpec(): androidx.compose.animation.core.TweenSpec<Float> =
    if (com.wpreborn.core.motion.WpMotion.classic) tween(800, easing = WpEasing.CubicInOut) else tween(WpDurations.Flip, easing = WpEasing.CubicOut)
