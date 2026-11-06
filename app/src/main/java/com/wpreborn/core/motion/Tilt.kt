package com.wpreborn.core.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The Windows Phone TiltEffect, ported from the Windows Phone Toolkit (TiltEffect.cs, Ms-PL):
 * the pressed element tilts away from the finger and sinks into the screen, following the finger
 * while it stays down, and goes back 200 ms after it lifts, in 100 ms.
 *
 *  - touch point normalized to 0..1 inside the element; magnitude = |x - 0.5| + |y - 0.5|
 *  - angle = magnitude × MaxAngle (0.3 rad), split between the axes by how far the touch is
 *    horizontally vs vertically from the centre
 *  - depression = (1 - magnitude) × MaxDepression (25 px): deepest at the centre. On the
 *    phone's projection that is a ~2.5 % shrink, the same as the 8.1 library's pointerDown
 *    (scale 0.975), so it is drawn as a scale.
 *
 * It observes touches in the Initial pass without consuming them, so it can be combined with
 * any click handler. [maxAngle] and [depression] are kept for callers; the toolkit's values
 * are used.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.tilt(enabled: Boolean = true, maxAngle: Float = 0f, depression: Float = 0f): Modifier =
    if (!enabled) this else composed {
        val rx = remember { Animatable(0f) }
        val ry = remember { Animatable(0f) }
        val sc = remember { Animatable(1f) }
        this
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        fun apply(p: Offset) {
                            val nx = (p.x / w).coerceIn(0f, 1f)
                            val ny = (p.y / h).coerceIn(0f, 1f)
                            val xMag = kotlin.math.abs(nx - 0.5f)
                            val yMag = kotlin.math.abs(ny - 0.5f)
                            val magnitude = xMag + yMag
                            val xContribution = if (magnitude > 0f) xMag / magnitude else 0f
                            val angle = magnitude * MAX_TILT_RAD * 180f / kotlin.math.PI.toFloat()
                            // The touched side goes into the screen (Compose's axes are the
                            // toolkit's PlaneProjection axes with the opposite sign).
                            val y = angle * xContribution * kotlin.math.sign(nx - 0.5f)
                            val x = -angle * (1f - xContribution) * kotlin.math.sign(ny - 0.5f)
                            val scale = 1f - PRESSED_SHRINK * (1f - magnitude)
                            launch { rx.snapTo(x) }
                            launch { ry.snapTo(y) }
                            launch { sc.snapTo(scale) }
                        }
                        apply(down.position)
                        // Follow the finger until it lifts or the gesture is taken (a scroll).
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val p = ch.position
                            if (p.x < 0f || p.y < 0f || p.x > w || p.y > h) break
                            apply(p)
                        }
                        // The toolkit's return: after 200 ms, back to rest in 100 ms.
                        val back = tween<Float>(WpDurations.Tilt, delayMillis = WpDurations.TiltReturnDelay, easing = androidx.compose.animation.core.LinearEasing)
                        launch { rx.animateTo(0f, back) }
                        launch { ry.animateTo(0f, back) }
                        launch { sc.animateTo(1f, back) }
                    }
                }
            }
            .graphicsLayer {
                rotationX = rx.value
                rotationY = ry.value
                scaleX = sc.value
                scaleY = sc.value
                cameraDistance = 8f * density
            }
    }

/** TiltEffect.MaxAngle: 0.3 radians at a corner. */
private const val MAX_TILT_RAD = 0.3f
/** TiltEffect.MaxDepression (25 px) seen through the phone's projection: about 2.5 % smaller at the centre. */
private const val PRESSED_SHRINK = 0.025f
