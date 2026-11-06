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
 * The Windows Phone 8.1 turnstile, with the animation library's own numbers (WinJS
 * Animations.js turnstileForwardIn/Out, turnstileBackwardIn/Out): the page turns like a
 * revolving door hinged 40 px left of the screen, at the screen's vertical middle.
 *
 *  - ForwardIn:   rotateY 80 -> 0,   300 ms, cubic-bezier(0.01, 0.975, 0.4775, 0.9775), perspective 600
 *  - ForwardOut:  rotateY 0 -> -50,  128 ms, cubic-bezier(0.4925, 0.01, 0.7675, -0.01), perspective 600
 *  - BackwardIn:  rotateY -50 -> 0,  300 ms, in curve, perspective 600
 *  - BackwardOut: rotateY 0 -> 80,   128 ms, out curve, perspective 800
 *
 * Opacity: visible from the first frame when coming in, until the last frame when going out.
 * Several elements (tiles, list rows) are staggered 50 ms apart, as WinJS does.
 */
enum class TurnstileMode(val from: Float, val to: Float, val entering: Boolean, val perspectivePx: Float) {
    ForwardIn(80f, 0f, true, 600f),
    ForwardOut(0f, -50f, false, 600f),
    BackwardIn(-50f, 0f, true, 600f),
    BackwardOut(0f, 80f, false, 800f),
    None(0f, 0f, true, 600f);

    val duration get() = if (entering) WpDurations.TurnstileIn else WpDurations.TurnstileOut
}

/** Kept for code that still names it: the 8.1 angles above are already in Compose's direction. */
const val TURNSTILE_SIGN = 1f

/** Maximum number of feather steps; elements further down move together with the last step. */
private const val MAX_FEATHER = 10
private const val FEATHER_STEP = WpDurations.FeatherStagger
private const val FEATHER_SPAN = MAX_FEATHER * FEATHER_STEP

/** The hinge: 40 px (of a 400 px wide phone screen) left of the screen's edge. */
const val TURNSTILE_HINGE_FRACTION = -0.1f

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

    /** [pivotX]/[pivotY]: the hinge as a fraction of the element (it may lie outside it). */
    internal fun applyTo(scope: GraphicsLayerScope, featherIndex: Int, pivotX: Float, pivotY: Float = 0.5f) = with(scope) {
        val m = mode
        if (m == TurnstileMode.None) return@with
        val elapsed = progress.value * (m.duration + FEATHER_SPAN)
        val delay = min(featherIndex, MAX_FEATHER) * FEATHER_STEP
        val local = ((elapsed - delay) / m.duration).coerceIn(0f, 1f)
        val eased = if (m.entering) WpEasing.TurnstileIn.transform(local) else WpEasing.TurnstileOut.transform(local)
        transformOrigin = TransformOrigin(pivotX, pivotY)
        // CSS perspective in px ≈ dp; Compose's camera distance is in inches of a 72 dpi camera.
        cameraDistance = m.perspectivePx / 72f * density
        rotationY = m.from + (m.to - m.from) * eased
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
    if (state == null) this else this.graphicsLayer { state.applyTo(this, 0, TURNSTILE_HINGE_FRACTION) }

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
        // Where the element is on the screen, so all of them turn around the same hinge: 40 px left
        // of the screen, at its vertical middle (WinJS collectTurnstileTransformOrigins).
        val geometry = remember { FloatArray(4) }
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val dens = androidx.compose.ui.platform.LocalDensity.current.density
        val screenW = config.screenWidthDp * dens
        val screenH = config.screenHeightDp * dens
        this
            .onGloballyPositioned {
                val p = it.positionInRoot()
                geometry[0] = p.x
                geometry[1] = p.y
                geometry[2] = it.size.width.toFloat()
                geometry[3] = it.size.height.toFloat()
            }
            .graphicsLayer {
                val w = max(1f, geometry[2])
                val h = max(1f, geometry[3])
                val pivotX = (TURNSTILE_HINGE_FRACTION * screenW - geometry[0]) / w
                val pivotY = (screenH / 2f - geometry[1]) / h
                state.applyTo(this, featherIndex, pivotX, pivotY)
            }
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
    /**
     * Key under which each page's saveable state is stored. It must be a type Android can put
     * in a Bundle (String, Int, ...), otherwise SaveableStateHolder throws.
     */
    stateKey: (K) -> Any = { it.toString() },
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
            if (goingBack) holder.removeState(stateKey(shown))
            shown = target
            state.run(if (goingBack) TurnstileMode.BackwardIn else TurnstileMode.ForwardIn)
        }
    }

    CompositionLocalProvider(LocalTurnstile provides state, LocalFeathered provides feathered) {
        Box(modifier.fillMaxSize().then(if (feathered) Modifier else Modifier.turnstile(state))) {
            key(shown) {
                holder.SaveableStateProvider(stateKey(shown)) { content(shown) }
            }
        }
    }
}
