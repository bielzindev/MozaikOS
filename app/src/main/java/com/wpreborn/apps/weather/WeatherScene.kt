package com.wpreborn.apps.weather

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Time of day for the sky: daylight, golden hour around sunrise/sunset, or night. */
enum class SkyPhase { Day, Golden, Night }

fun skyPhase(isDay: Boolean, sunrise: Long?, sunset: Long?, now: Long = System.currentTimeMillis()): SkyPhase {
    val window = 45 * 60_000L
    if (sunrise != null && abs(now - sunrise) < window) return SkyPhase.Golden
    if (sunset != null && abs(now - sunset) < window) return SkyPhase.Golden
    return if (isDay) SkyPhase.Day else SkyPhase.Night
}

private class Drop(val x: Float, val speed: Float, val length: Float, val phase: Float)
private class Flake(val x: Float, val size: Float, val speed: Float, val sway: Float, val phase: Float)
private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
private class Cloud(val x: Float, val y: Float, val scale: Float)

/**
 * A living sky behind the weather: gradient by condition and time of day, the sun with
 * slowly turning rays or the moon and twinkling stars, three layers of drifting clouds
 * (parallax), a landscape that gets wet in the rain, falling rain or snow, fog banks and
 * lightning. Everything is drawn in one canvas from a single clock (only the drawing is
 * invalidated each frame), colours cross-fade when the conditions change, and the scene
 * stays still when the system's animations are turned off.
 */
@Composable
fun WeatherScene(code: Int, isDay: Boolean, sunrise: Long?, sunset: Long?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sky = Sky.of(code)
    val phase = skyPhase(isDay, sunrise, sunset)
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val clock = remember { mutableFloatStateOf(12f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { clock.floatValue = 12f + (it - start) / 1000f }
    }

    // Colours cross-fade between conditions.
    val (topTarget, bottomTarget) = skyGradient(sky, phase)
    val top by animateColorAsState(topTarget, tween(1500), label = "top")
    val bottom by animateColorAsState(bottomTarget, tween(1500), label = "bottom")
    val cloudiness by animateFloatAsState(
        when (sky) {
            Sky.Clear -> 0f
            Sky.PartlyCloudy -> 0.45f
            Sky.Fog -> 0.6f
            Sky.Cloudy, Sky.Drizzle, Sky.Snow -> 0.85f
            Sky.Rain, Sky.Showers -> 0.95f
            Sky.Storm -> 1f
        },
        tween(1500), label = "clouds",
    )
    val rain by animateFloatAsState(
        when (sky) { Sky.Drizzle -> 0.35f; Sky.Rain -> 0.7f; Sky.Showers -> 0.85f; Sky.Storm -> 1f; else -> 0f }, tween(1500), label = "rain",
    )
    val snow by animateFloatAsState(if (sky == Sky.Snow) 1f else 0f, tween(1500), label = "snow")
    val fog by animateFloatAsState(if (sky == Sky.Fog) 1f else 0f, tween(1500), label = "fog")
    val sunAlpha by animateFloatAsState(if (phase != SkyPhase.Night) (1f - cloudiness * 0.9f).coerceAtLeast(0f) else 0f, tween(1500), label = "sun")
    val moonAlpha by animateFloatAsState(if (phase == SkyPhase.Night) (1f - cloudiness * 0.8f).coerceAtLeast(0f) else 0f, tween(1500), label = "moon")
    val wet = rain > 0.05f
    val storm = sky == Sky.Storm

    val seeds = remember {
        val r = Random(7)
        Triple(
            List(160) { Drop(r.nextFloat(), 0.8f + r.nextFloat() * 0.9f, 0.025f + r.nextFloat() * 0.035f, r.nextFloat()) },
            List(110) { Flake(r.nextFloat(), 1.5f + r.nextFloat() * 3.5f, 0.05f + r.nextFloat() * 0.08f, 0.01f + r.nextFloat() * 0.03f, r.nextFloat() * 6.28f) },
            List(80) { Star(r.nextFloat(), r.nextFloat() * 0.6f, 0.6f + r.nextFloat() * 1.6f, r.nextFloat() * 6.28f) },
        )
    }
    val clouds = remember {
        val r = Random(11)
        List(3) { layer -> List(4) { Cloud(r.nextFloat(), 0.08f + layer * 0.08f + r.nextFloat() * 0.12f, 0.7f + layer * 0.25f + r.nextFloat() * 0.3f) } }
    }

    Canvas(modifier) {
        val t = clock.floatValue
        val w = size.width
        val h = size.height

        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        // Stars and moon.
        if (moonAlpha > 0.01f) {
            seeds.third.forEach { s ->
                val tw = 0.55f + 0.45f * sin(t * 1.3f + s.phase)
                drawCircle(Color.White.copy(alpha = moonAlpha * tw * 0.9f), s.r, Offset(s.x * w, s.y * h))
            }
            drawMoon(Offset(w * 0.78f, h * 0.16f), w * 0.07f, moonAlpha, t)
        }
        // Sun.
        if (sunAlpha > 0.01f) {
            val golden = phase == SkyPhase.Golden
            drawSun(Offset(w * 0.8f, if (golden) h * 0.34f else h * 0.14f), w * 0.09f, sunAlpha, t, golden)
        }

        // Clouds, far to near; storm clouds are darker.
        val cloudColor = when {
            storm -> Color(0xFF3B3F4C)
            phase == SkyPhase.Night -> Color(0xFF5B6475)
            sky == Sky.Rain || sky == Sky.Showers -> Color(0xFFAAB3BE)
            phase == SkyPhase.Golden -> Color(0xFFFFD9C2)
            else -> Color.White
        }
        if (cloudiness > 0.02f) {
            clouds.forEachIndexed { layer, list ->
                val speed = 0.004f + layer * 0.006f
                val alpha = cloudiness * (0.35f + layer * 0.22f)
                list.forEachIndexed { i, c ->
                    if (i.toFloat() / list.size > cloudiness + 0.2f) return@forEachIndexed
                    val x = ((c.x + t * speed) % 1.4f) - 0.2f
                    drawCloud(Offset(x * w, c.y * h), w * 0.16f * c.scale, cloudColor.copy(alpha = alpha.coerceAtMost(0.95f)))
                }
            }
        }

        // Fog banks.
        if (fog > 0.01f) {
            for (k in 0 until 5) {
                val y = h * (0.45f + k * 0.1f)
                val dx = sin(t * 0.08f + k) * w * 0.1f
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f * fog), Color.Transparent), startX = dx - w * 0.2f, endX = dx + w * 1.2f),
                    topLeft = Offset(0f, y),
                    size = Size(w, h * 0.07f),
                )
            }
        }

        // Landscape: two rows of hills; wet ground reflects the sky in the rain.
        drawHills(w, h, phase, wet)

        // Rain.
        if (rain > 0.01f) {
            val count = (seeds.first.size * rain).toInt()
            val wind = if (storm) 0.18f else 0.08f
            val c = Color.White.copy(alpha = 0.35f + 0.25f * rain)
            for (i in 0 until count) {
                val d = seeds.first[i]
                val y = ((d.phase + t * d.speed * 1.6f) % 1.1f) - 0.05f
                val x = (d.x + y * wind) % 1f
                drawLine(c, Offset(x * w, y * h), Offset((x + d.length * wind) * w, (y + d.length) * h), 1.6f, StrokeCap.Round)
            }
            // Splashes on the ground.
            for (i in 0 until count / 4) {
                val d = seeds.first[i]
                val p = (t * 2f + d.phase * 7f) % 1f
                drawCircle(Color.White.copy(alpha = (1f - p) * 0.3f), 2f + p * 6f, Offset(d.x * w, h * (0.9f + d.length)), style = Stroke(1f))
            }
        }

        // Snow.
        if (snow > 0.01f) {
            seeds.second.forEach { f ->
                val y = ((f.phase / 6.28f + t * f.speed) % 1.1f) - 0.05f
                val x = f.x + sin(t * 0.8f + f.phase) * f.sway
                drawCircle(Color.White.copy(alpha = 0.85f * snow), f.size * density / 2f, Offset(x * w, y * h))
            }
        }

        // Lightning: a flash and a bolt every few seconds.
        if (storm) {
            val cycle = t % 7.3f
            if (cycle < 0.35f) {
                val k = 1f - cycle / 0.35f
                val flicker = if (cycle in 0.08f..0.14f) 0.3f else 1f
                drawRect(Color.White.copy(alpha = 0.35f * k * flicker))
                drawBolt(w * (0.25f + ((t / 7.3f).toInt() % 3) * 0.22f), h, Color.White.copy(alpha = k))
            }
        }
    }
}

private fun skyGradient(sky: Sky, phase: SkyPhase): Pair<Color, Color> = when (phase) {
    SkyPhase.Night -> when (sky) {
        Sky.Storm -> Color(0xFF171624) to Color(0xFF07070C)
        Sky.Clear, Sky.PartlyCloudy -> Color(0xFF0B1A38) to Color(0xFF020611)
        else -> Color(0xFF1B222C) to Color(0xFF06080B)
    }
    SkyPhase.Golden -> when (sky) {
        Sky.Clear, Sky.PartlyCloudy -> Color(0xFF3E5FA8) to Color(0xFFF2915A)
        Sky.Storm -> Color(0xFF3A3550) to Color(0xFF8A5A52)
        else -> Color(0xFF5B6478) to Color(0xFFC08A70)
    }
    SkyPhase.Day -> when (sky) {
        Sky.Clear -> Color(0xFF1C7FE0) to Color(0xFF7DC1F5)
        Sky.PartlyCloudy -> Color(0xFF3C86CF) to Color(0xFF9CC7EB)
        Sky.Cloudy, Sky.Fog -> Color(0xFF6D7C8B) to Color(0xFFA7B2BD)
        Sky.Drizzle, Sky.Rain, Sky.Showers -> Color(0xFF475A6C) to Color(0xFF7D8C99)
        Sky.Snow -> Color(0xFF8DA6BF) to Color(0xFFD5E0EA)
        Sky.Storm -> Color(0xFF34324A) to Color(0xFF5D5D6E)
    }
}

private fun DrawScope.drawSun(c: Offset, r: Float, alpha: Float, t: Float, golden: Boolean) {
    val core = if (golden) Color(0xFFFFB46B) else Color(0xFFFFF3C4)
    // Breathing glow.
    val glow = r * (3.2f + 0.25f * sin(t * 0.9f))
    drawCircle(Brush.radialGradient(listOf(core.copy(alpha = 0.45f * alpha), Color.Transparent), center = c, radius = glow), glow, c)
    rotate(t * 6f, c) {
        for (k in 0 until 12) {
            val a = k * PI.toFloat() / 6
            val len = r * (1.6f + 0.25f * sin(t * 1.5f + k))
            drawLine(core.copy(alpha = 0.55f * alpha), Offset(c.x + cos(a) * r * 1.25f, c.y + sin(a) * r * 1.25f), Offset(c.x + cos(a) * len, c.y + sin(a) * len), r * 0.09f, StrokeCap.Round)
        }
    }
    drawCircle(core.copy(alpha = alpha), r, c)
}

private fun DrawScope.drawMoon(c: Offset, r: Float, alpha: Float, t: Float) {
    val glow = r * (3f + 0.2f * sin(t * 0.5f))
    drawCircle(Brush.radialGradient(listOf(Color(0x55DDE6FF).copy(alpha = 0.33f * alpha), Color.Transparent), center = c, radius = glow), glow, c)
    val moon = Path().apply {
        addOval(Rect(c, r))
        // Crescent: cut a second disc.
        op(this, Path().apply { addOval(Rect(Offset(c.x + r * 0.45f, c.y - r * 0.3f), r * 0.85f)) }, androidx.compose.ui.graphics.PathOperation.Difference)
    }
    drawPath(moon, Color(0xFFEFF2FF).copy(alpha = alpha))
}

private fun DrawScope.drawCloud(at: Offset, r: Float, color: Color) {
    val p = Path().apply {
        addOval(Rect(Offset(at.x, at.y + r * 0.25f), r * 0.75f))
        addOval(Rect(Offset(at.x + r * 0.8f, at.y), r))
        addOval(Rect(Offset(at.x + r * 1.7f, at.y + r * 0.3f), r * 0.7f))
        addRect(Rect(at.x, at.y + r * 0.25f, at.x + r * 1.7f, at.y + r * 1f))
    }
    drawPath(p, color)
}

private fun DrawScope.drawHills(w: Float, h: Float, phase: SkyPhase, wet: Boolean) {
    val far = when (phase) { SkyPhase.Night -> Color(0xFF0B1018); SkyPhase.Golden -> Color(0xFF3B2E3A); SkyPhase.Day -> Color(0xFF2F4A3A) }
    val near = when (phase) { SkyPhase.Night -> Color(0xFF05080C); SkyPhase.Golden -> Color(0xFF241B24); SkyPhase.Day -> Color(0xFF1F3427) }
    val back = Path().apply {
        moveTo(0f, h * 0.86f)
        cubicTo(w * 0.2f, h * 0.8f, w * 0.35f, h * 0.84f, w * 0.55f, h * 0.81f)
        cubicTo(w * 0.75f, h * 0.78f, w * 0.9f, h * 0.83f, w, h * 0.8f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(back, far.copy(alpha = 0.85f))
    val front = Path().apply {
        moveTo(0f, h * 0.9f)
        cubicTo(w * 0.25f, h * 0.87f, w * 0.5f, h * 0.93f, w * 0.75f, h * 0.89f)
        cubicTo(w * 0.88f, h * 0.87f, w * 0.95f, h * 0.9f, w, h * 0.89f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(front, near)
    if (wet) {
        // A wet sheen on the ground.
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), startY = h * 0.9f, endY = h),
            topLeft = Offset(0f, h * 0.9f),
            size = Size(w, h * 0.1f),
        )
    }
}

private fun DrawScope.drawBolt(x: Float, h: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, h * 0.18f)
        lineTo(x - h * 0.03f, h * 0.36f)
        lineTo(x + h * 0.01f, h * 0.36f)
        lineTo(x - h * 0.04f, h * 0.58f)
    }
    drawPath(p, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
}
