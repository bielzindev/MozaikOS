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
 * The entrance of list rows and page content in Windows Phone 8.1 (the animation library's
 * enterContent / EntranceThemeTransition): each of the first rows comes 40 px (of a 400 px wide
 * screen) from the right in 550 ms with the standard deceleration (cubic-bezier .1,.9,.2,1),
 * fading in over the first 170 ms, 33 ms after the row above (at most 250 ms).
 *
 * Only the rows that are on screen when the page (or [key]) appears move: a row created later,
 * while the list scrolls, just shows up in place, as on the phone. ([distanceDp] is kept for callers.)
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.slideInStaggered(index: Int, key: Any? = Unit, distanceDp: Float = 80f): Modifier = composed {
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val animate = remember(key) { EntranceWave.joins(index) }
    val progress = remember(key) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(key) {
        if (!animate) return@LaunchedEffect
        delay(minOf(index * 33L, 250L))
        progress.animateTo(1f, tween(ENTER_MS, easing = androidx.compose.animation.core.LinearEasing))
    }
    this.graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            val offset = 40f * screenWidthDp / 400f * density
            translationX = (1f - WpEasing.Decelerate.transform(p)) * offset
            alpha = (p * ENTER_MS / FADE_MS).coerceIn(0f, 1f)
        }
    }
}

private const val ENTER_MS = 550
private const val FADE_MS = 170f

/**
 * Which rows take part in an entrance: the first 8, created within 600 ms of the first one of the
 * wave. A row composed later (scrolling) starts no movement of its own.
 */
internal object EntranceWave {
    private var startedAt = 0L

    fun joins(index: Int): Boolean {
        if (index >= 8) return false
        val now = android.os.SystemClock.uptimeMillis()
        if (now - startedAt <= 600L) return true
        // A new page or list appearing: its top row starts the wave.
        return if (index == 0) {
            startedAt = now
            true
        } else {
            false
        }
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
