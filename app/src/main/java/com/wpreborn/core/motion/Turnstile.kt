package com.wpreborn.core.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.max
import kotlin.math.min

/**
 * Turnstile transition, as implemented by the Windows Phone Toolkit (TurnstileTransition):
 * the page rotates around the left edge of the screen like a revolving door.
 *
 *  - ForwardIn:   RotationY -80 -> 0, 350 ms, exponential ease-out
 *  - ForwardOut:  RotationY 0 -> 50,  250 ms, exponential ease-in
 *  - BackwardIn:  RotationY 50 -> 0,  350 ms, exponential ease-out
 *  - BackwardOut: RotationY 0 -> -80, 250 ms, exponential ease-in
 */
enum class TurnstileMode(val from: Float, val to: Float, val entering: Boolean) {
    ForwardIn(-80f, 0f, true),
    ForwardOut(0f, 50f, false),
    BackwardIn(50f, 0f, true),
    BackwardOut(0f, -80f, false),
    None(0f, 0f, true);

    val duration get() = if (entering) WpDurations.TurnstileIn else WpDurations.TurnstileOut
}

/** Sign applied to rotationY so that positive Silverlight angles swing the right edge away. */
const val TURNSTILE_SIGN = -1f

/** Maximum number of feather steps; elements further down move together with the last step. */
private const val MAX_FEATHER = 6
private const val FEATHER_SPAN = MAX_FEATHER * WpDurations.FeatherStagger / 2

@Stable
class TurnstileState(initialMode: TurnstileMode = TurnstileMode.None) {
    var mode by mutableStateOf(initialMode)
    /** Linear 0..1 progress of the current [mode], including the feather span. */
    val progress = Animatable(if (initialMode == TurnstileMode.None) 1f else 0f)

    suspend fun run(mode: TurnstileMode) {
        this.mode = mode
        progress.snapTo(0f)
        progress.animateTo(1f, tween(mode.duration + FEATHER_SPAN, easing = LinearEasing))
        if (mode.entering) this.mode = TurnstileMode.None
    }

    internal fun applyTo(scope: GraphicsLayerScope, featherIndex: Int, pivotX: Float) = with(scope) {
        val m = mode
        if (m == TurnstileMode.None) return@with
        val elapsed = progress.value * (m.duration + FEATHER_SPAN)
        val delay = min(featherIndex, MAX_FEATHER) * (WpDurations.FeatherStagger / 2)
        val local = ((elapsed - delay) / m.duration).coerceIn(0f, 1f)
        val eased = if (m.entering) WpEasing.ExpOut.transform(local) else WpEasing.ExpIn.transform(local)
        transformOrigin = TransformOrigin(pivotX, 0.5f)
        cameraDistance = 14f * density
        rotationY = (m.from + (m.to - m.from) * eased) * TURNSTILE_SIGN
        alpha = when {
            m.entering && local <= 0f -> 0f
            !m.entering && local >= 1f -> 0f
            else -> 1f
        }
    }
}

val LocalTurnstile = staticCompositionLocalOf<TurnstileState?> { null }

/** Applies the turnstile rotation to a whole page, hinged on its left edge. */
fun Modifier.turnstile(state: TurnstileState?): Modifier =
    if (state == null) this else this.graphicsLayer { state.applyTo(this, 0, 0f) }

/**
 * Turnstile for an individual element (tile, list row) hinged on the left edge of the
 * screen. [featherIndex] delays the element so a page "feathers" in from top to bottom.
 * When an element uses this, its page should not use [turnstile] as well.
 */
fun Modifier.turnstileItem(featherIndex: Int): Modifier = composed {
    val state = LocalTurnstile.current
    if (state == null) {
        this
    } else {
        val geometry = remember { FloatArray(2) }
        this
            .onGloballyPositioned {
                geometry[0] = it.positionInRoot().x
                geometry[1] = it.size.width.toFloat()
            }
            .graphicsLayer { state.applyTo(this, featherIndex, -geometry[0] / max(1f, geometry[1])) }
    }
}

/** Whether the page content applies [turnstileItem] itself (feathering) or rotates as a block. */
val LocalFeathered = staticCompositionLocalOf { false }

/**
 * A navigation host that plays the turnstile transition between pages: the old page
 * turns out, then the new page turns in. The state of every page in the back stack is
 * saved, so returning to a page restores its scroll position like the phone did.
 */
@Composable
fun <K : Any> TurnstileHost(
    target: K,
    isBack: Boolean,
    modifier: Modifier = Modifier,
    animateFirst: Boolean = true,
    feathered: Boolean = false,
    content: @Composable (K) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val state = remember { TurnstileState(if (animateFirst) TurnstileMode.ForwardIn else TurnstileMode.None) }
    var shown by remember { mutableStateOf(target) }
    var first by remember { mutableStateOf(true) }
    val back by rememberUpdatedState(isBack)

    LaunchedEffect(target) {
        if (first) {
            first = false
            if (animateFirst) state.run(TurnstileMode.ForwardIn)
            return@LaunchedEffect
        }
        if (shown != target) {
            val goingBack = back
            state.run(if (goingBack) TurnstileMode.BackwardOut else TurnstileMode.ForwardOut)
            if (goingBack) holder.removeState(shown)
            shown = target
            state.run(if (goingBack) TurnstileMode.BackwardIn else TurnstileMode.ForwardIn)
        }
    }

    CompositionLocalProvider(LocalTurnstile provides state, LocalFeathered provides feathered) {
        Box(modifier.fillMaxSize().then(if (feathered) Modifier else Modifier.turnstile(state))) {
            key(shown) {
                holder.SaveableStateProvider(shown) { content(shown) }
            }
        }
    }
}
