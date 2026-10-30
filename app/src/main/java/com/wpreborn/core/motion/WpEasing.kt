package com.wpreborn.core.motion

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

/** Quadratic/cubic ease used by the tile flip and peek animations. */
object WpEasing {
    val ExpOut: Easing = ExponentialEaseOut(6f)
    val ExpIn: Easing = ExponentialEaseIn(6f)
    val QuarticOut: Easing = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) * (1f - t) }
    val CubicInOut: Easing = Easing { t ->
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }
}

/** Durations taken from the Windows Phone Toolkit's TransitionFrame storyboards. */
object WpDurations {
    const val TurnstileIn = 350
    const val TurnstileOut = 250
    const val FeatherStagger = 40
    const val Flip = 600
    const val Tilt = 120
    const val SlideIn = 350
}
