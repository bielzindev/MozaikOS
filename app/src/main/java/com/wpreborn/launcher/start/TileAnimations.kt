package com.wpreborn.launcher.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
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
            angle.animateTo(180f, tween(800, easing = WpEasing.CubicInOut))
            delay(5000L + rnd.nextLong(3000))
            angle.animateTo(360f, tween(800, easing = WpEasing.CubicInOut))
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
 * Cycle tile template (Photos): images slide up one after another.
 */
@Composable
fun <T> CycleTile(
    items: List<T>,
    enabled: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    var index by remember(items) { mutableIntStateOf(0) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(items, enabled) {
        if (!enabled || items.size < 2) return@LaunchedEffect
        val rnd = Random(seed)
        delay(1500L + rnd.nextLong(3000))
        while (true) {
            slide.snapTo(0f)
            slide.animateTo(1f, tween(900, easing = WpEasing.CubicInOut))
            index = (index + 1) % items.size
            slide.snapTo(0f)
            delay(4500L + rnd.nextLong(2500))
        }
    }
    if (items.isEmpty()) return
    val current = items[index % items.size]
    val next = items[(index + 1) % items.size]
    Box(modifier.fillMaxSize().clipToBounds()) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -slide.value * size.height }) { content(current) }
        if (slide.value > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - slide.value) * size.height }) { content(next) }
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
