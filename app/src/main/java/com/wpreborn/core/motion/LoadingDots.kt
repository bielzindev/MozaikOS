package com.wpreborn.core.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wpreborn.core.theme.Wp

/**
 * The indeterminate ProgressBar of Windows Phone: five small square dots that accelerate in
 * from the left, slow down crossing the middle and accelerate out to the right.
 */
@Composable
fun LoadingDots(modifier: Modifier = Modifier, color: Color = Wp.accent) {
    val transition = rememberInfiniteTransition(label = "dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val dot = 4.dp.toPx()
        for (i in 0 until 5) {
            val local = ((t * 1.6f) - i * 0.08f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            // Fast - slow - fast: a cubic that flattens around the middle.
            val c = local - 0.5f
            val x = 0.5f + 4f * c * c * c + c * 0.25f
            drawRect(color, topLeft = Offset(x * size.width - dot / 2, 0f), size = Size(dot, dot))
        }
    }
}
