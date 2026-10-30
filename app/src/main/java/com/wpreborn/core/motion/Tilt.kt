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
 * The Windows Phone "TiltEffect": a pressed element tilts away from the finger, pushing the
 * touched corner into the screen, and springs back on release. It observes touches in the
 * Initial pass without consuming them, so it can be combined with any click handler.
 *
 * @param maxAngle maximum rotation, in degrees (the toolkit used ~0.3 rad scaled by size).
 * @param depression scale applied while pressed.
 */
fun Modifier.tilt(enabled: Boolean = true, maxAngle: Float = 9f, depression: Float = 0.975f): Modifier =
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
                        fun target(p: Offset) {
                            val nx = ((p.x - w / 2f) / (w / 2f)).coerceIn(-1f, 1f)
                            val ny = ((p.y - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)
                            // Large elements tilt less, like the toolkit's size-scaled angle.
                            val scale = (220f * density / maxOf(w, h)).coerceIn(0.25f, 1f)
                            launch { rx.snapTo(-ny * maxAngle * scale) }
                            launch { ry.snapTo(nx * maxAngle * scale) }
                        }
                        target(down.position)
                        launch { sc.animateTo(depression, tween(WpDurations.Tilt)) }
                        waitForUpOrCancellation(PointerEventPass.Initial)
                        launch { rx.animateTo(0f, tween(WpDurations.Tilt)) }
                        launch { ry.animateTo(0f, tween(WpDurations.Tilt)) }
                        launch { sc.animateTo(1f, tween(WpDurations.Tilt)) }
                    }
                }
            }
            .graphicsLayer {
                rotationX = rx.value
                rotationY = ry.value
                scaleX = sc.value
                scaleY = sc.value
                cameraDistance = 12f * density
            }
    }
