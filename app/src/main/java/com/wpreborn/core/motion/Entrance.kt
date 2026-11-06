package com.wpreborn.core.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The staggered "slide in from the right" used when pivot items and list rows appear:
 * each row starts a little later than the previous one and decelerates exponentially.
 */
fun Modifier.slideInStaggered(index: Int, key: Any? = Unit, distanceDp: Float = 80f): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(minOf(index, 10) * 30L)
        launch { progress.animateTo(1f, tween(WpDurations.SlideIn, easing = WpEasing.ExpOut)) }
    }
    this.graphicsLayer {
        val p = progress.value
        translationX = (1f - p) * distanceDp * density
        alpha = if (p <= 0f) 0f else minOf(1f, p * 2.5f)
    }
}

/** The vertical "slide up and fade" (SlideTransition SlideUpFadeIn) used by message dialogs and menus. */
fun Modifier.slideUpFadeIn(key: Any? = Unit, distanceDp: Float = 30f): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(WpDurations.SlideIn, easing = WpEasing.ExpOut)) }
    this.graphicsLayer {
        val p = progress.value
        translationY = (1f - p) * distanceDp * density
        alpha = p
    }
}

/** The "swivel" used by full-screen pickers and the context menu: rotate around the X axis from the top. */
fun Modifier.swivelIn(key: Any? = Unit): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(WpDurations.SlideIn, easing = WpEasing.ExpOut)) }
    this.graphicsLayer {
        val p = progress.value
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        cameraDistance = 10f * density
        rotationX = (1f - p) * -60f
        alpha = minOf(1f, p * 2f)
    }
}

/**
 * The entrance of a list's first rows: the first screenful slides in one row after another
 * (like the Windows Phone toolkit's feather), rows that appear later while scrolling don't.
 */
fun Modifier.listEntrance(index: Int): Modifier = if (index < 8) slideInStaggered(index, distanceDp = 60f) else this
