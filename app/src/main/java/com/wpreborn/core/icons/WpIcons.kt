package com.wpreborn.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Monochrome glyphs in the spirit of Segoe UI Symbol / Segoe WP app bar icons. They are drawn
 * on a 48x48 grid with geometric primitives and are always tinted by the caller.
 */
object WpIcons {

    private class Scope(val builder: ImageVector.Builder) {
        fun fill(block: PathBuilder.() -> Unit) {
            builder.path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero, pathBuilder = block)
        }

        fun fillEvenOdd(block: PathBuilder.() -> Unit) {
            builder.path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd, pathBuilder = block)
        }

        fun stroke(width: Float = 3f, cap: StrokeCap = StrokeCap.Butt, block: PathBuilder.() -> Unit) {
            builder.path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = cap,
                strokeLineJoin = StrokeJoin.Miter,
                pathBuilder = block,
            )
        }
    }

    private fun icon(name: String, block: Scope.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 48.dp, 48.dp, 48f, 48f)
        Scope(b).block()
        return b.build()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
    }

    private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1); lineTo(x2, y2)
    }

    /** Classic handset, used by the Phone tile and call buttons. */
    val Phone = icon("phone") {
        fill {
            moveTo(13.2f, 21.6f)
            curveTo(16.1f, 27.3f, 20.7f, 31.9f, 26.4f, 34.8f)
            lineTo(30.8f, 30.4f)
            curveTo(31.4f, 29.8f, 32.2f, 29.6f, 32.9f, 29.9f)
            curveTo(35.1f, 30.6f, 37.5f, 31f, 40f, 31f)
            curveTo(41.1f, 31f, 42f, 31.9f, 42f, 33f)
            lineTo(42f, 40f)
            curveTo(42f, 41.1f, 41.1f, 42f, 40f, 42f)
            curveTo(21.2f, 42f, 6f, 26.8f, 6f, 8f)
            curveTo(6f, 6.9f, 6.9f, 6f, 8f, 6f)
            lineTo(15f, 6f)
            curveTo(16.1f, 6f, 17f, 6.9f, 17f, 8f)
            curveTo(17f, 10.5f, 17.4f, 12.9f, 18.1f, 15.1f)
            curveTo(18.4f, 15.8f, 18.2f, 16.6f, 17.6f, 17.2f)
            close()
        }
    }

    val Messaging = icon("messaging") {
        fill {
            moveTo(6f, 9f); lineTo(42f, 9f); lineTo(42f, 34f); lineTo(19f, 34f)
            lineTo(11f, 41f); lineTo(11f, 34f); lineTo(6f, 34f); close()
        }
    }

    val People = icon("people") {
        fill { circle(31f, 14f, 6.5f) }
        fill {
            moveTo(24f, 25f); curveTo(26f, 23.4f, 28.4f, 22.5f, 31f, 22.5f)
            curveTo(37.6f, 22.5f, 43f, 27.5f, 43f, 37f); lineTo(36f, 37f)
            curveTo(35.6f, 31.6f, 32f, 27.6f, 27f, 26.4f); close()
        }
        fill { circle(18f, 18f, 7.5f) }
        fill {
            moveTo(4f, 42f); curveTo(4f, 32.5f, 10f, 27f, 18f, 27f)
            curveTo(26f, 27f, 32f, 32.5f, 32f, 42f); close()
        }
    }

    val Person = icon("person") {
        fill { circle(24f, 16f, 8.5f) }
        fill {
            moveTo(8f, 44f); curveTo(8f, 33f, 15f, 27f, 24f, 27f)
            curveTo(33f, 27f, 40f, 33f, 40f, 44f); close()
        }
    }

    val Music = icon("music") {
        stroke(3.2f) {
            moveTo(10f, 31f); lineTo(10f, 25f)
            arcTo(14f, 14f, 0f, false, true, 38f, 25f)
            lineTo(38f, 31f)
        }
        fill { rect(7f, 28f, 9f, 13f) }
        fill { rect(32f, 28f, 9f, 13f) }
    }

    val MusicNote = icon("note") {
        fill { circle(15f, 35f, 6f) }
        fill { rect(18.5f, 8f, 3f, 27f) }
        fill { moveTo(21.5f, 8f); lineTo(36f, 13f); lineTo(36f, 19f); lineTo(21.5f, 14f); close() }
    }

    val Photos = icon("photos") {
        stroke(3f) { rect(6.5f, 10.5f, 35f, 27f) }
        fill { moveTo(9f, 35f); lineTo(19f, 23f); lineTo(26f, 30f); lineTo(31f, 25f); lineTo(39f, 35f); close() }
        fill { circle(32f, 18f, 3f) }
    }

    val Camera = icon("camera") {
        fillEvenOdd {
            moveTo(5f, 15f); lineTo(15f, 15f); lineTo(18f, 10f); lineTo(30f, 10f); lineTo(33f, 15f)
            lineTo(43f, 15f); lineTo(43f, 39f); lineTo(5f, 39f); close()
            circle(24f, 27f, 8f)
        }
    }

    val Calendar = icon("calendar") {
        stroke(3f) { rect(7.5f, 9.5f, 33f, 31f) }
        fill { rect(7.5f, 9.5f, 33f, 7f) }
        for (r in 0 until 3) for (c in 0 until 4) {
            fill { rect(11f + c * 7.5f, 20f + r * 6.5f, 4f, 4f) }
        }
    }

    val Settings = icon("settings") {
        fillEvenOdd {
            val teeth = 8
            val outer = 19f
            val inner = 15f
            val cx = 24f
            val cy = 24f
            for (i in 0 until teeth * 4) {
                val a = (i / (teeth * 4f)) * 2 * PI - PI / 2
                val r = if ((i % 4) == 1 || (i % 4) == 2) outer else inner
                val x = cx + (r * cos(a)).toFloat()
                val y = cy + (r * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
            circle(cx, cy, 6.5f)
        }
    }

    val Search = icon("search") {
        stroke(3f) { circle(27f, 20f, 11f) }
        stroke(4f) { line(19f, 28f, 8f, 40f) }
    }

    val Add = icon("add") {
        stroke(3f) { line(24f, 10f, 24f, 38f); line(10f, 24f, 38f, 24f) }
    }

    val Close = icon("close") {
        stroke(3f) { line(13f, 13f, 35f, 35f); line(35f, 13f, 13f, 35f) }
    }

    val Check = icon("check") {
        stroke(3f) { moveTo(11f, 25f); lineTo(20f, 34f); lineTo(38f, 15f) }
    }

    val More = icon("more") {
        fill { circle(12f, 24f, 2.6f); circle(24f, 24f, 2.6f); circle(36f, 24f, 2.6f) }
    }

    val Back = icon("back") {
        stroke(3f) { line(38f, 24f, 11f, 24f); moveTo(22f, 13f); lineTo(11f, 24f); lineTo(22f, 35f) }
    }

    val Forward = icon("forward") {
        stroke(3f) { line(10f, 24f, 37f, 24f); moveTo(26f, 13f); lineTo(37f, 24f); lineTo(26f, 35f) }
    }

    val Delete = icon("delete") {
        stroke(3f) { line(9f, 13.5f, 39f, 13.5f); moveTo(19f, 13f); lineTo(19f, 8.5f); lineTo(29f, 8.5f); lineTo(29f, 13f) }
        stroke(3f) { moveTo(13f, 14f); lineTo(15f, 40.5f); lineTo(33f, 40.5f); lineTo(35f, 14f) }
        stroke(2.4f) { line(21f, 19f, 21f, 35f); line(27f, 19f, 27f, 35f) }
    }

    val Edit = icon("edit") {
        stroke(3f) { moveTo(31f, 9f); lineTo(39f, 17f); lineTo(18f, 38f); lineTo(9f, 39f); lineTo(10f, 30f); close() }
        stroke(3f) { line(27f, 13f, 35f, 21f) }
    }

    val Send = icon("send") {
        fill { moveTo(7f, 9f); lineTo(43f, 24f); lineTo(7f, 39f); lineTo(11f, 24f); close() }
    }

    val Keypad = icon("keypad") {
        for (r in 0 until 4) for (c in 0 until 3) {
            fill { rect(12f + c * 10f, 6f + r * 10f, 5f, 5f) }
        }
    }

    val Play = icon("play") {
        fill { moveTo(16f, 10f); lineTo(38f, 24f); lineTo(16f, 38f); close() }
    }

    val Pause = icon("pause") {
        fill { rect(14f, 11f, 6.5f, 26f); rect(27.5f, 11f, 6.5f, 26f) }
    }

    val Next = icon("next") {
        fill { moveTo(9f, 13f); lineTo(23f, 24f); lineTo(9f, 35f); close() }
        fill { moveTo(23f, 13f); lineTo(37f, 24f); lineTo(23f, 35f); close() }
        fill { rect(36f, 13f, 3.5f, 22f) }
    }

    val Previous = icon("previous") {
        fill { moveTo(39f, 13f); lineTo(25f, 24f); lineTo(39f, 35f); close() }
        fill { moveTo(25f, 13f); lineTo(11f, 24f); lineTo(25f, 35f); close() }
        fill { rect(8.5f, 13f, 3.5f, 22f) }
    }

    val Shuffle = icon("shuffle") {
        stroke(3f) {
            moveTo(6f, 15f); lineTo(14f, 15f); curveTo(24f, 15f, 24f, 33f, 34f, 33f); lineTo(40f, 33f)
            moveTo(6f, 33f); lineTo(14f, 33f); curveTo(24f, 33f, 24f, 15f, 34f, 15f); lineTo(40f, 15f)
        }
        fill { moveTo(37f, 10f); lineTo(43f, 15f); lineTo(37f, 20f); close() }
        fill { moveTo(37f, 28f); lineTo(43f, 33f); lineTo(37f, 38f); close() }
    }

    val Repeat = icon("repeat") {
        stroke(3f) {
            moveTo(9f, 27f); lineTo(9f, 16.5f); lineTo(35f, 16.5f)
            moveTo(39f, 21f); lineTo(39f, 31.5f); lineTo(13f, 31.5f)
        }
        fill { moveTo(34f, 11f); lineTo(40f, 16.5f); lineTo(34f, 22f); close() }
        fill { moveTo(14f, 26f); lineTo(8f, 31.5f); lineTo(14f, 37f); close() }
    }

    val RepeatOne = icon("repeat_one") {
        stroke(3f) {
            moveTo(9f, 27f); lineTo(9f, 16.5f); lineTo(35f, 16.5f)
            moveTo(39f, 21f); lineTo(39f, 31.5f); lineTo(13f, 31.5f)
        }
        fill { moveTo(34f, 11f); lineTo(40f, 16.5f); lineTo(34f, 22f); close() }
        fill { moveTo(14f, 26f); lineTo(8f, 31.5f); lineTo(14f, 37f); close() }
        fill { rect(23f, 20.5f, 3f, 8f) }
    }

    val Wifi = icon("wifi") {
        stroke(3.2f) {
            moveTo(6f, 19f); arcTo(26f, 26f, 0f, false, true, 42f, 19f)
            moveTo(12f, 25.5f); arcTo(17f, 17f, 0f, false, true, 36f, 25.5f)
            moveTo(18f, 32f); arcTo(8.5f, 8.5f, 0f, false, true, 30f, 32f)
        }
        fill { circle(24f, 38f, 3f) }
    }

    val Bluetooth = icon("bluetooth") {
        stroke(3f) {
            moveTo(13f, 15f); lineTo(34f, 33f); lineTo(24f, 42f); lineTo(24f, 6f); lineTo(34f, 15f); lineTo(13f, 33f)
        }
    }

    val Airplane = icon("airplane") {
        fill {
            moveTo(22f, 6f); curveTo(22f, 4.5f, 26f, 4.5f, 26f, 6f); lineTo(26f, 18f); lineTo(42f, 27f)
            lineTo(42f, 31f); lineTo(26f, 26f); lineTo(26f, 36f); lineTo(31f, 40f); lineTo(31f, 43f)
            lineTo(24f, 41f); lineTo(17f, 43f); lineTo(17f, 40f); lineTo(22f, 36f); lineTo(22f, 26f)
            lineTo(6f, 31f); lineTo(6f, 27f); lineTo(22f, 18f); close()
        }
    }

    val Brightness = icon("brightness") {
        fill { circle(24f, 24f, 7.5f) }
        stroke(3f) {
            for (i in 0 until 8) {
                val a = i * PI / 4
                line(
                    24f + (12 * cos(a)).toFloat(), 24f + (12 * sin(a)).toFloat(),
                    24f + (17.5 * cos(a)).toFloat(), 24f + (17.5 * sin(a)).toFloat(),
                )
            }
        }
    }

    val Flashlight = icon("flashlight") {
        stroke(3f) {
            moveTo(14f, 6.5f); lineTo(34f, 6.5f); lineTo(34f, 14f); lineTo(29f, 21f); lineTo(29f, 42f)
            lineTo(19f, 42f); lineTo(19f, 21f); lineTo(14f, 14f); close()
        }
        fill { rect(22.5f, 25f, 3f, 6f) }
    }

    val RotationLock = icon("rotation") {
        stroke(3f) { rect(15.5f, 6.5f, 17f, 30f) }
        stroke(2.6f) { moveTo(9f, 34f); arcTo(17f, 17f, 0f, false, false, 37f, 40f) }
        fill { moveTo(33f, 36f); lineTo(41f, 39f); lineTo(35f, 44f); close() }
    }

    val Volume = icon("volume") {
        fill { moveTo(6f, 18f); lineTo(14f, 18f); lineTo(24f, 9f); lineTo(24f, 39f); lineTo(14f, 30f); lineTo(6f, 30f); close() }
        stroke(3f) {
            moveTo(30f, 17f); arcTo(9f, 9f, 0f, false, true, 30f, 31f)
            moveTo(34f, 11f); arcTo(16f, 16f, 0f, false, true, 34f, 37f)
        }
    }

    val Mute = icon("mute") {
        fill { moveTo(6f, 18f); lineTo(14f, 18f); lineTo(24f, 9f); lineTo(24f, 39f); lineTo(14f, 30f); lineTo(6f, 30f); close() }
        stroke(3f) { line(30f, 18f, 42f, 30f); line(42f, 18f, 30f, 30f) }
    }

    val Vibrate = icon("vibrate") {
        stroke(3f) { rect(16.5f, 8.5f, 15f, 31f) }
        stroke(2.6f) {
            moveTo(10f, 15f); lineTo(7f, 19f); lineTo(10f, 24f); lineTo(7f, 29f); lineTo(10f, 33f)
            moveTo(38f, 15f); lineTo(41f, 19f); lineTo(38f, 24f); lineTo(41f, 29f); lineTo(38f, 33f)
        }
    }

    val Pin = icon("pin") {
        fill {
            moveTo(17f, 6f); lineTo(31f, 6f); lineTo(31f, 9f); lineTo(29f, 10f); lineTo(29f, 21f)
            lineTo(35f, 28f); lineTo(35f, 30f); lineTo(25.5f, 30f); lineTo(24f, 43f); lineTo(22.5f, 30f)
            lineTo(13f, 30f); lineTo(13f, 28f); lineTo(19f, 21f); lineTo(19f, 10f); lineTo(17f, 9f); close()
        }
    }

    val Unpin = icon("unpin") {
        fill {
            moveTo(17f, 6f); lineTo(31f, 6f); lineTo(31f, 9f); lineTo(29f, 10f); lineTo(29f, 21f)
            lineTo(35f, 28f); lineTo(35f, 30f); lineTo(25.5f, 30f); lineTo(24f, 43f); lineTo(22.5f, 30f)
            lineTo(13f, 30f); lineTo(13f, 28f); lineTo(19f, 21f); lineTo(19f, 10f); lineTo(17f, 9f); close()
        }
        stroke(3f) { line(8f, 8f, 40f, 40f) }
    }

    /** The diagonal arrow on the resize button of a tile in edit mode. */
    val Resize = icon("resize") {
        stroke(3f) { line(13f, 35f, 35f, 13f) }
        fill { moveTo(37f, 11f); lineTo(37f, 23f); lineTo(25f, 11f); close() }
    }

    val Palette = icon("palette") {
        fill { rect(9f, 9f, 13f, 13f); rect(26f, 9f, 13f, 13f); rect(9f, 26f, 13f, 13f) }
        stroke(2.5f) { rect(27.25f, 27.25f, 10.5f, 10.5f) }
    }

    val Star = icon("star") {
        fill {
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val r = if (i % 2 == 0) 19.0 else 8.0
                val x = 24f + (r * cos(a)).toFloat()
                val y = 25f + (r * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    val StarOutline = icon("star_outline") {
        stroke(2.6f) {
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val r = if (i % 2 == 0) 18.0 else 8.0
                val x = 24f + (r * cos(a)).toFloat()
                val y = 25f + (r * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    val Voicemail = icon("voicemail") {
        stroke(3f) { circle(13f, 25f, 7f); circle(35f, 25f, 7f); line(13f, 32f, 35f, 32f) }
    }

    val History = icon("history") {
        stroke(3f) { circle(24f, 24f, 16f) }
        stroke(3f) { moveTo(24f, 13f); lineTo(24f, 24f); lineTo(32f, 29f) }
    }

    val Mail = icon("mail") {
        stroke(3f) { rect(6.5f, 11.5f, 35f, 25f); moveTo(7f, 12f); lineTo(24f, 26f); lineTo(41f, 12f) }
    }

    val Folder = icon("folder") {
        fill { moveTo(5f, 10f); lineTo(19f, 10f); lineTo(22f, 14f); lineTo(43f, 14f); lineTo(43f, 39f); lineTo(5f, 39f); close() }
    }

    val Lock = icon("lock") {
        stroke(3.2f) { moveTo(16f, 21f); lineTo(16f, 15f); arcTo(8f, 8f, 0f, false, true, 32f, 15f); lineTo(32f, 21f) }
        fillEvenOdd { rect(11f, 21f, 26f, 20f); rect(22.5f, 27f, 3f, 8f) }
    }

    val Info = icon("info") {
        stroke(3f) { circle(24f, 24f, 17f) }
        fill { rect(22.5f, 21f, 3f, 14f); circle(24f, 15.5f, 2f) }
    }

    val Storage = icon("storage") {
        stroke(3f) { rect(8.5f, 10.5f, 31f, 27f); line(8.5f, 28f, 39.5f, 28f) }
        fill { circle(33f, 33f, 2f) }
    }

    val Display = icon("display") {
        stroke(3f) { rect(13.5f, 5.5f, 21f, 37f) }
        fill { rect(21f, 37f, 6f, 2.5f) }
    }

    val Apps = icon("apps") {
        fill { rect(9f, 9f, 13f, 13f); rect(26f, 9f, 13f, 13f); rect(9f, 26f, 13f, 13f); rect(26f, 26f, 13f, 13f) }
    }

    val Notification = icon("notification") {
        stroke(3f) { rect(8.5f, 8.5f, 31f, 23f); moveTo(14f, 32f); lineTo(14f, 40f); lineTo(22f, 32f) }
        fill { rect(14f, 15f, 20f, 2.5f); rect(14f, 22f, 14f, 2.5f) }
    }

    val Sound = icon("sound") {
        fill { moveTo(8f, 19f); lineTo(16f, 19f); lineTo(25f, 11f); lineTo(25f, 37f); lineTo(16f, 29f); lineTo(8f, 29f); close() }
        stroke(3f) { moveTo(31f, 18f); arcTo(8f, 8f, 0f, false, true, 31f, 30f) }
    }

    val Theme = icon("theme") {
        fill { rect(7f, 7f, 15f, 15f); rect(26f, 7f, 15f, 7f); rect(26f, 18f, 15f, 4f); rect(7f, 26f, 34f, 15f) }
    }

    val ArrowRightCircle = icon("arrow_right") {
        stroke(2.6f) { circle(24f, 24f, 20f) }
        stroke(2.8f) { line(14f, 24f, 33f, 24f); moveTo(25f, 16f); lineTo(33f, 24f); lineTo(25f, 32f) }
    }

    val CallOutgoing = icon("call_out") {
        stroke(3f) { line(12f, 36f, 34f, 14f); moveTo(19f, 14f); lineTo(34f, 14f); lineTo(34f, 29f) }
    }

    val CallIncoming = icon("call_in") {
        stroke(3f) { line(36f, 12f, 14f, 34f); moveTo(14f, 19f); lineTo(14f, 34f); lineTo(29f, 34f) }
    }

    val CallMissed = icon("call_missed") {
        stroke(3f) { moveTo(8f, 16f); lineTo(22f, 30f); lineTo(38f, 14f); moveTo(28f, 14f); lineTo(38f, 14f); lineTo(38f, 24f) }
    }

    val Speaker = icon("speaker") {
        stroke(3f) { rect(12.5f, 6.5f, 23f, 35f) }
        stroke(3f) { circle(24f, 28f, 6f) }
        fill { circle(24f, 14f, 2.5f) }
    }

    val Backspace = icon("backspace") {
        stroke(3f) { moveTo(16f, 12.5f); lineTo(42.5f, 12.5f); lineTo(42.5f, 35.5f); lineTo(16f, 35.5f); lineTo(5f, 24f); close() }
        stroke(3f) { line(23f, 18f, 35f, 30f); line(35f, 18f, 23f, 30f) }
    }

    val Contact = icon("contact") {
        stroke(3f) { rect(6.5f, 9.5f, 35f, 29f) }
        fill { circle(18f, 20f, 4.5f) }
        fill { moveTo(10f, 33f); curveTo(10f, 28f, 13f, 26f, 18f, 26f); curveTo(23f, 26f, 26f, 28f, 26f, 33f); close() }
        fill { rect(29f, 18f, 9f, 2.5f); rect(29f, 24f, 9f, 2.5f) }
    }

    val Sync = icon("sync") {
        stroke(3f) {
            moveTo(10f, 22f); arcTo(14f, 14f, 0f, false, true, 36f, 18f)
            moveTo(38f, 26f); arcTo(14f, 14f, 0f, false, true, 12f, 30f)
        }
        fill { moveTo(31f, 17f); lineTo(39f, 21f); lineTo(39f, 12f); close() }
        fill { moveTo(17f, 31f); lineTo(9f, 27f); lineTo(9f, 36f); close() }
    }

    val Clock = icon("clock") {
        stroke(3f) { circle(24f, 24f, 17f) }
        stroke(3f) { moveTo(24f, 13f); lineTo(24f, 24f); lineTo(31f, 24f) }
    }

    val Alarm = icon("alarm") {
        stroke(3f) { circle(24f, 26f, 15f) }
        stroke(3f) { moveTo(24f, 17f); lineTo(24f, 26f); lineTo(30f, 30f) }
        stroke(3f) { line(8f, 12f, 14f, 6f); line(40f, 12f, 34f, 6f) }
    }

    val Globe = icon("globe") {
        stroke(2.6f) {
            circle(24f, 24f, 17f)
            line(7f, 24f, 41f, 24f)
            moveTo(24f, 7f); curveTo(15f, 15f, 15f, 33f, 24f, 41f)
            moveTo(24f, 7f); curveTo(33f, 15f, 33f, 33f, 24f, 41f)
        }
    }

    val Store = icon("store") {
        stroke(3f) { rect(8.5f, 16.5f, 31f, 25f) }
        stroke(3f) { moveTo(17f, 17f); lineTo(17f, 13f); arcTo(7f, 7f, 0f, false, true, 31f, 13f); lineTo(31f, 17f) }
    }

    val Album = icon("album") {
        stroke(3f) { circle(24f, 24f, 17f) }
        fill { circle(24f, 24f, 4f) }
    }

    val Playlist = icon("playlist") {
        fill { rect(7f, 10f, 24f, 3f); rect(7f, 18f, 24f, 3f); rect(7f, 26f, 15f, 3f) }
        fill { circle(31f, 36f, 5f) }
        fill { rect(33.5f, 18f, 3f, 18f); moveTo(36.5f, 18f); lineTo(42f, 20f); lineTo(42f, 24f); lineTo(36.5f, 22f); close() }
    }

    val ZoomOut = icon("zoom") {
        stroke(3f) { circle(21f, 21f, 12f) }
        stroke(4f) { line(30f, 30f, 41f, 41f) }
        stroke(3f) { line(15f, 21f, 27f, 21f) }
    }

    val Share = icon("share") {
        fill { circle(12f, 24f, 5f); circle(35f, 12f, 5f); circle(35f, 36f, 5f) }
        stroke(2.6f) { line(12f, 24f, 35f, 12f); line(12f, 24f, 35f, 36f) }
    }

    val Attach = icon("attach") {
        stroke(2.6f) {
            moveTo(30f, 14f); lineTo(30f, 34f); arcTo(6f, 6f, 0f, false, true, 18f, 34f); lineTo(18f, 11f)
            arcTo(4f, 4f, 0f, false, true, 26f, 11f); lineTo(26f, 32f)
        }
    }

    val SelectAll = icon("select") {
        stroke(2.6f) { rect(8.5f, 8.5f, 12f, 12f); rect(27.5f, 8.5f, 12f, 12f); rect(8.5f, 27.5f, 12f, 12f); rect(27.5f, 27.5f, 12f, 12f) }
    }

    val Refresh = icon("refresh") {
        stroke(3f) { moveTo(36f, 15f); arcTo(14f, 14f, 0f, true, false, 38f, 26f) }
        fill { moveTo(31f, 9f); lineTo(39f, 9f); lineTo(39f, 17f); close() }
    }

    val Download = icon("download") {
        stroke(3f) { line(24f, 8f, 24f, 32f); moveTo(14f, 22f); lineTo(24f, 32f); lineTo(34f, 22f); line(9f, 40f, 39f, 40f) }
    }
}
