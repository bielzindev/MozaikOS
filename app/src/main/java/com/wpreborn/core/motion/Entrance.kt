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
 * The Windows Phone Toolkit's SlideInEffect (Ms-PL), used when pivot items and list rows come in:
 * row [index] starts (index + 1) × 50 px (of a 480 px wide screen) to the right, stays there for
 * 350 ms, moves linearly to 90 % of that by 420 ms and then glides home with an ExponentialEase
 * (EaseOut, Exponent 5) by 1050 ms. Rows further down start further away, so they come in one
 * after the other. ([distanceDp] is kept for callers; the toolkit's offsets are used.)
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.slideInStaggered(index: Int, key: Any? = Unit, distanceDp: Float = 80f): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(SLIDE_END, easing = androidx.compose.animation.core.LinearEasing))
    }
    this.graphicsLayer {
        val from = (minOf(index, 12) + 1) * 50f * screenWidthDp / 480f * density
        val t = progress.value * SLIDE_END
        translationX = when {
            t <= SLIDE_BEGIN -> from
            t <= SLIDE_BREAK -> from + (from * 0.9f - from) * ((t - SLIDE_BEGIN) / (SLIDE_BREAK - SLIDE_BEGIN))
            else -> from * 0.9f * (1f - WpEasing.ExpOut5.transform((t - SLIDE_BREAK) / (SLIDE_END - SLIDE_BREAK)))
        }
    }
}

private const val SLIDE_BEGIN = 350f
private const val SLIDE_BREAK = 420f
private const val SLIDE_END = 1050

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

/**
 * The swivel of [swivelIn] driven by [progress] (0 = turned away, 1 = in place), so the same
 * motion plays backwards when something leaves: the Windows Phone toolkit's swivel in and out.
 */
fun Modifier.swivelWith(progress: () -> Float): Modifier = this.graphicsLayer {
    val p = progress()
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
    cameraDistance = 10f * density
    rotationX = (1f - p) * -60f
    alpha = minOf(1f, p * 2f)
}
