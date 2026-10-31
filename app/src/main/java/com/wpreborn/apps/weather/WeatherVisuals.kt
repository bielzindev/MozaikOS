package com.wpreborn.apps.weather

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.wpreborn.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** WMO weather code groups used by Open-Meteo. */
enum class Sky(@StringRes val label: Int) {
    Clear(R.string.weather_clear),
    PartlyCloudy(R.string.weather_partly_cloudy),
    Cloudy(R.string.weather_cloudy),
    Fog(R.string.weather_fog),
    Drizzle(R.string.weather_drizzle),
    Rain(R.string.weather_rain),
    Showers(R.string.weather_showers),
    Snow(R.string.weather_snow),
    Storm(R.string.weather_storm),
    ;

    companion object {
        fun of(code: Int) = when (code) {
            0 -> Clear
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            in 51..57 -> Drizzle
            in 61..67 -> Rain
            in 80..82 -> Showers
            in 71..77, 85, 86 -> Snow
            in 95..99 -> Storm
            else -> Cloudy
        }
    }
}

/** Background colours of the MSN Weather style hub, by sky and time of day. */
fun skyColors(code: Int, isDay: Boolean): List<Color> = when (Sky.of(code)) {
    Sky.Clear -> if (isDay) listOf(Color(0xFF1E88E5), Color(0xFF0B4F9C)) else listOf(Color(0xFF0B1E3F), Color(0xFF02060F))
    Sky.PartlyCloudy -> if (isDay) listOf(Color(0xFF3D8BD1), Color(0xFF1C4C80)) else listOf(Color(0xFF17253D), Color(0xFF060B14))
    Sky.Cloudy, Sky.Fog -> if (isDay) listOf(Color(0xFF6E7F8F), Color(0xFF3B4650)) else listOf(Color(0xFF2B323A), Color(0xFF0E1114))
    Sky.Drizzle, Sky.Rain, Sky.Showers -> if (isDay) listOf(Color(0xFF4A6074), Color(0xFF1F2B36)) else listOf(Color(0xFF1C2631), Color(0xFF080C10))
    Sky.Snow -> if (isDay) listOf(Color(0xFF8FA9C2), Color(0xFF4F6880)) else listOf(Color(0xFF33414F), Color(0xFF10151B))
    Sky.Storm -> listOf(Color(0xFF3A3550), Color(0xFF110F1A))
}

/**
 * Colour weather icon for the Weather app: Meteocons "fill" set by Bas Milius (MIT licence,
 * see third_party/meteocons), rendered to PNG.
 */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(weatherIconRes(code, isDay)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

fun weatherIconRes(code: Int, isDay: Boolean): Int = when (Sky.of(code)) {
    Sky.Clear -> if (isDay) R.drawable.wx_clear_day else R.drawable.wx_clear_night
    Sky.PartlyCloudy -> if (isDay) R.drawable.wx_partly_cloudy_day else R.drawable.wx_partly_cloudy_night
    Sky.Cloudy -> if (code == 3) R.drawable.wx_overcast else R.drawable.wx_cloudy
    Sky.Fog -> if (isDay) R.drawable.wx_fog_day else R.drawable.wx_fog_night
    Sky.Drizzle -> R.drawable.wx_drizzle
    Sky.Rain -> R.drawable.wx_rain
    Sky.Showers -> if (isDay) R.drawable.wx_partly_cloudy_day_rain else R.drawable.wx_partly_cloudy_night_rain
    Sky.Snow -> R.drawable.wx_snow
    Sky.Storm -> R.drawable.wx_thunderstorms_rain
}

/** Monochrome line glyph, for live tiles (Windows Phone tiles are white on the tile colour). */
@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(size)) { drawGlyph(Sky.of(code), isDay, color) }
}

private fun DrawScope.drawGlyph(sky: Sky, isDay: Boolean, c: Color) {
    val u = size.minDimension / 48f
    val stroke = Stroke(width = 2.6f * u, cap = StrokeCap.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)

    fun sun(cx: Float, cy: Float, r: Float, rays: Boolean = true) {
        drawCircle(c, r * u, p(cx, cy), style = stroke)
        if (rays) for (k in 0 until 8) {
            val a = k * PI / 4
            drawLine(c, p(cx + (r + 4) * cos(a).toFloat(), cy + (r + 4) * sin(a).toFloat()), p(cx + (r + 8) * cos(a).toFloat(), cy + (r + 8) * sin(a).toFloat()), stroke.width, StrokeCap.Round)
        }
    }

    fun moon(cx: Float, cy: Float, r: Float) {
        val path = Path().apply {
            addArc(androidx.compose.ui.geometry.Rect(p(cx - r, cy - r), Size(2 * r * u, 2 * r * u)), 60f, 260f)
            val inner = r * 0.78f
            val ox = cx + r * 0.45f
            val oy = cy - r * 0.35f
            arcTo(androidx.compose.ui.geometry.Rect(p(ox - inner, oy - inner), Size(2 * inner * u, 2 * inner * u)), 230f, -250f, false)
        }
        drawPath(path, c, style = stroke)
    }

    fun cloud(dx: Float, dy: Float, scale: Float = 1f) {
        val path = Path().apply {
            fun q(x: Float, y: Float) = p(dx + x * scale, dy + y * scale)
            val a = q(10f, 34f)
            moveTo(a.x, a.y)
            val r1 = 6f * scale * u
            arcTo(androidx.compose.ui.geometry.Rect(q(4f, 22f), Size(2 * r1, 2 * r1)), 90f, 180f, false)
            val r2 = 9f * scale * u
            arcTo(androidx.compose.ui.geometry.Rect(q(10f, 13f), Size(2 * r2, 2 * r2)), 180f, 160f, false)
            val r3 = 7f * scale * u
            arcTo(androidx.compose.ui.geometry.Rect(q(28f, 20f), Size(2 * r3, 2 * r3)), 250f, 200f, false)
            close()
        }
        drawPath(path, c, style = stroke)
    }

    when (sky) {
        Sky.Clear -> if (isDay) sun(24f, 24f, 9f) else moon(24f, 24f, 13f)
        Sky.PartlyCloudy -> {
            if (isDay) sun(17f, 17f, 6f) else moon(17f, 15f, 8f)
            cloud(6f, 6f, 0.9f)
        }
        Sky.Cloudy -> cloud(2f, 2f)
        Sky.Fog -> {
            cloud(2f, -4f)
            for (k in 0..2) drawLine(c, p(8f + k * 2, 38f + k * 4), p(40f - k * 2, 38f + k * 4), stroke.width, StrokeCap.Round)
        }
        Sky.Drizzle, Sky.Rain, Sky.Showers -> {
            cloud(2f, -4f)
            val n = if (sky == Sky.Drizzle) 3 else 4
            for (k in 0 until n) {
                val x = 14f + k * 7f
                drawLine(c, p(x, 36f), p(x - 3f, if (sky == Sky.Drizzle) 40f else 44f), stroke.width, StrokeCap.Round)
            }
            if (sky == Sky.Showers && isDay) sun(38f, 9f, 3.5f, rays = false)
        }
        Sky.Snow -> {
            cloud(2f, -4f)
            for (k in 0 until 3) {
                val x = 15f + k * 9f
                drawCircle(c, 1.8f * u, p(x, 38f))
                drawCircle(c, 1.8f * u, p(x + 4.5f, 44f))
            }
        }
        Sky.Storm -> {
            cloud(2f, -4f)
            val bolt = Path().apply {
                val a = p(25f, 32f); moveTo(a.x, a.y)
                p(19f, 41f).let { lineTo(it.x, it.y) }
                p(24f, 41f).let { lineTo(it.x, it.y) }
                p(21f, 47f).let { lineTo(it.x, it.y) }
            }
            drawPath(bolt, c, style = stroke)
        }
    }
}
