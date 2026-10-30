package com.wpreborn.core.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.data.system.SystemStatus
import java.util.Date

/**
 * The Windows Phone system tray: signal strength bars, data/Wi-Fi indicators on the left,
 * battery and clock on the right, drawn in the foreground color on the page background.
 */
@Composable
fun WpStatusBar(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val context = LocalContext.current
    val status = remember { SystemStatus.get(context) }
    val battery by status.battery.collectAsState()
    val net by status.network.collectAsState()
    val radios by status.radios.collectAsState()
    val now by status.clock.collectAsState()
    val fg = if (color == Color.Unspecified) Wp.palette.foreground else color
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    Row(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radios.airplane) {
            WpIcon(WpIcons.Airplane, tint = fg, size = 15.dp)
        } else {
            SignalBars(if (net.cellLevel < 0) 0 else net.cellLevel + 1, fg, noSim = net.cellLevel < 0)
            if (net.mobileData && !net.wifiConnected) {
                Spacer(Modifier.width(4.dp))
                WpText("4G", style = WpType.Small.copy(fontSize = 11.sp), color = fg)
            }
        }
        if (net.wifiConnected) {
            Spacer(Modifier.width(8.dp))
            WifiBars(net.wifiLevel, fg)
        }
        if (radios.bluetooth) {
            Spacer(Modifier.width(6.dp))
            WpIcon(WpIcons.Bluetooth, tint = fg, size = 14.dp)
        }
        if (radios.ringerMode == android.media.AudioManager.RINGER_MODE_VIBRATE) {
            Spacer(Modifier.width(6.dp))
            WpIcon(WpIcons.Vibrate, tint = fg, size = 14.dp)
        }
        Spacer(Modifier.weight(1f))
        BatteryGlyph(battery.level, battery.charging, fg)
        Spacer(Modifier.width(8.dp))
        WpText(time, style = WpType.Small.copy(fontSize = 13.sp), color = fg)
    }
}

@Composable
fun SignalBars(level: Int, color: Color, noSim: Boolean = false) {
    Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
        val bars = 5
        val gap = 1.5.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = size.height * (i + 1) / bars
            val active = !noSim && i < level
            drawRect(
                color = if (active) color else color.copy(alpha = 0.3f),
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
            )
        }
    }
}

@Composable
fun WifiBars(level: Int, color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        // Wi-Fi in WP 8.1 is a quarter "fan" made of three arcs plus a dot.
        val cx = size.width / 2
        val base = size.height
        val stroke = 1.6.dp.toPx()
        for (i in 1..3) {
            val r = size.height * i / 3.2f
            drawArc(
                color = if (level >= i + 1 || (i == 1 && level >= 1)) color else color.copy(alpha = 0.3f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, base - r),
                size = Size(r * 2, r * 2),
                style = Stroke(stroke),
            )
        }
        drawCircle(color, radius = stroke, center = Offset(cx, base - stroke))
    }
}

@Composable
fun BatteryGlyph(level: Int, charging: Boolean, color: Color) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 24.dp, height = 11.dp)) {
            val stroke = 1.2.dp.toPx()
            val tip = 2.dp.toPx()
            drawRect(color, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - tip - stroke, size.height - stroke), style = Stroke(stroke))
            drawRect(color, topLeft = Offset(size.width - tip, size.height * 0.3f), size = Size(tip, size.height * 0.4f))
            val inner = size.width - tip - stroke * 4
            drawRect(
                color,
                topLeft = Offset(stroke * 2, stroke * 2),
                size = Size(inner * (level.coerceIn(0, 100) / 100f), size.height - stroke * 4),
            )
        }
        if (charging) {
            Canvas(Modifier.size(width = 8.dp, height = 11.dp)) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.6f, 0f)
                    lineTo(0f, size.height * 0.58f)
                    lineTo(size.width * 0.45f, size.height * 0.58f)
                    lineTo(size.width * 0.35f, size.height)
                    lineTo(size.width, size.height * 0.42f)
                    lineTo(size.width * 0.55f, size.height * 0.42f)
                    close()
                }
                drawPath(p, Color.Black.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
                drawPath(p, color)
            }
        }
    }
}
