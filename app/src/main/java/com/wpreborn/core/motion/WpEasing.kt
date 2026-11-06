package com.wpreborn.core.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.exp

/**
 * Silverlight's ExponentialEase, the curve used by the Windows Phone Toolkit transitions
 * (turnstile, slide, swivel) with Exponent = 6.
 */
class ExponentialEaseOut(private val exponent: Float = 6f) : Easing {
    private val norm = 1f - exp(-exponent)
    override fun transform(fraction: Float): Float = (1f - exp(-exponent * fraction)) / norm
}

class ExponentialEaseIn(private val exponent: Float = 6f) : Easing {
    private val norm = exp(exponent) - 1f
    override fun transform(fraction: Float): Float = (exp(exponent * fraction) - 1f) / norm
}

/**
 * The curves of the Windows Phone animations. ExpOut/ExpIn: the toolkit's ExponentialEase
 * (Exponent 6). The cubic béziers are the Windows Phone 8.1 animation library's own (WinJS
 * Animations.js, MIT): the turnstile's in and out curves and the standard decelerate curve.
 */
object WpEasing {
    val ExpOut: Easing = ExponentialEaseOut(6f)
    val ExpIn: Easing = ExponentialEaseIn(6f)
    /** SwivelTransition ForwardOut: ExponentialEase EaseIn, Exponent 15. */
    val ExpIn15: Easing = ExponentialEaseIn(15f)
    /** SlideInEffect: ExponentialEase EaseOut, Exponent 5. */
    val ExpOut5: Easing = ExponentialEaseOut(5f)
    /** ContextMenu background zoom: ExponentialEase EaseInOut (Exponent 2, Silverlight's default). */
    val ExpInOut2: Easing = Easing { t ->
        val e = 2f
        if (t < 0.5f) ExponentialEaseIn(e).transform(t * 2f) / 2f else 0.5f + ExponentialEaseOut(e).transform(t * 2f - 1f) / 2f
    }
    /** turnstileForwardIn / BackwardIn: cubic-bezier(0.01, 0.975, 0.4775, 0.9775). */
    val TurnstileIn: Easing = CubicBezierEasing(0.01f, 0.975f, 0.4775f, 0.9775f)
    /** turnstileForwardOut / BackwardOut: cubic-bezier(0.4925, 0.01, 0.7675, -0.01). */
    val TurnstileOut: Easing = CubicBezierEasing(0.4925f, 0.01f, 0.7675f, -0.01f)
    /** The library's standard deceleration (pointer down/up, popups, panels, peek): cubic-bezier(0.1, 0.9, 0.2, 1). */
    val Decelerate: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
    /** HubTile flip and peek: CubicEase EaseOut. */
    val CubicOut: Easing = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }
    val QuarticOut: Easing = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) * (1f - t) }
    val CubicInOut: Easing = Easing { t ->
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }
}

/** Durations from the Windows Phone animation library and toolkit storyboards. */
object WpDurations {
    /** Turnstile in / out of Windows Phone 8.1 (WinJS turnstile*: 300 ms in, 128 ms out). */
    const val TurnstileIn = 300
    const val TurnstileOut = 128
    /** Delay between elements of a feathered turnstile (WinJS staggerDelay(0, 50, 1, 1000)). */
    const val FeatherStagger = 50
    /** HubTile flip and peek storyboards: 0.85 s. */
    const val Flip = 850
    /** TiltEffect: release starts 200 ms after the finger lifts and takes 100 ms. */
    const val TiltReturnDelay = 200
    const val Tilt = 100
    /** SwivelTransition ForwardIn 0.35 s, ForwardOut 0.25 s. */
    const val SwivelIn = 350
    const val SwivelOut = 250
    const val SlideIn = 350
}
