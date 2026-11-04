package com.wpreborn.apps.music

import android.Manifest
import android.media.audiofx.Visualizer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpToggle
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.rememberPlayback
import com.wpreborn.permissions.rememberPermissionState
import com.wpreborn.services.music.AudioEffects
import com.wpreborn.services.music.EqDevice
import com.wpreborn.services.music.EqPreset
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The equalizer: on/off, presets, one vertical slider per band of the phone's equalizer and a
 * pre-amp. The curve above shows the response; with the microphone permission it also shows
 * the live spectrum of the music (Android's Visualizer needs it). Changes are heard at once.
 */
@Composable
fun EqualizerScreen() {
    val context = LocalContext.current
    val settings by AudioEffects.settings.collectAsState()
    val device by AudioEffects.device.collectAsState()
    val supported by AudioEffects.supported.collectAsState()
    val playback = rememberPlayback(includeExternal = false)
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        WpText(stringResource(R.string.music_title).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(top = 14.dp))
        WpText(stringResource(R.string.eq_title), style = WpType.Header, modifier = Modifier.padding(bottom = 8.dp))

        val dev = device
        if (!supported) {
            WpText(stringResource(R.string.eq_unsupported), style = WpType.Large, color = Wp.palette.subtle)
            return@Column
        }
        if (dev == null) {
            WpText(stringResource(R.string.eq_play_first), style = WpType.Large, color = Wp.palette.subtle)
            return@Column
        }

        WpToggle(stringResource(R.string.eq_title), settings.enabled, onText = on, offText = off) { AudioEffects.setEnabled(it) }

        // Response curve + live spectrum.
        val spectrum = rememberSpectrum(enabled = playback?.own == true && playback.playing)
        ResponseView(dev, settings.bands, settings.enabled, spectrum.bars, Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp))
        if (!spectrum.permitted) {
            WpText(
                stringResource(R.string.eq_spectrum_enable),
                style = WpType.Small,
                color = Wp.accent,
                modifier = Modifier.wpClickable { spectrum.request() }.padding(vertical = 4.dp),
            )
        }

        // Presets.
        WpText(stringResource(R.string.eq_presets), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqPreset.entries.forEach { p ->
                val selected = settings.preset == p.id
                Box(
                    Modifier
                        .border(2.dp, if (selected) Wp.accent else Wp.palette.divider)
                        .background(if (selected) Wp.accent else Color.Transparent)
                        .wpClickable { AudioEffects.setPreset(p) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    WpText(presetLabel(p), style = WpType.Normal, color = if (selected) Color.White else Wp.palette.foreground)
                }
            }
            if (settings.preset == null) {
                Box(Modifier.background(Wp.accent).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    WpText(stringResource(R.string.eq_custom), style = WpType.Normal, color = Color.White)
                }
            }
        }

        // Bands.
        Row(
            Modifier.fillMaxWidth().height(230.dp).padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            dev.centersHz.forEachIndexed { i, hz ->
                val db = settings.bands.getOrElse(i) { 0f }
                BandSlider(db, dev.minDb, dev.maxDb, freqLabel(hz), settings.enabled, Modifier.weight(1f)) { AudioEffects.setBand(i, it) }
            }
        }

        // Pre-amp.
        WpText(
            stringResource(R.string.eq_preamp) + "  " + "%+.1f dB".format(settings.preampDb),
            style = WpType.Subtle,
            color = Wp.palette.subtle,
            modifier = Modifier.padding(top = 16.dp),
        )
        WpSlider((settings.preampDb + 10f) / 20f, { AudioEffects.setPreamp((it * 20f - 10f).let { v -> (v * 2).roundToInt() / 2f }) })
        Spacer(Modifier.height(12.dp))
        WpButton(stringResource(R.string.eq_reset)) {
            AudioEffects.setPreset(EqPreset.Flat)
            AudioEffects.setPreamp(0f)
        }
    }
}

@Composable
private fun presetLabel(p: EqPreset) = stringResource(
    when (p) {
        EqPreset.Flat -> R.string.eq_flat
        EqPreset.Pop -> R.string.eq_pop
        EqPreset.Rock -> R.string.eq_rock
        EqPreset.Jazz -> R.string.eq_jazz
        EqPreset.Classical -> R.string.eq_classical
        EqPreset.Electronic -> R.string.eq_electronic
        EqPreset.BassBoost -> R.string.eq_bass
        EqPreset.Vocal -> R.string.eq_vocal
    },
)

private fun freqLabel(hz: Float) = if (hz >= 1000) "%.1fk".format(hz / 1000).replace(".0k", "k") else hz.roundToInt().toString()

/** A vertical slider: drag or tap the column; the value is shown above. */
@Composable
private fun BandSlider(db: Float, min: Float, max: Float, label: String, active: Boolean, modifier: Modifier, onChange: (Float) -> Unit) {
    val update by rememberUpdatedState(onChange)
    val shown by animateFloatAsState(db, spring(stiffness = 600f), label = "band")
    val accent = if (active) Wp.accent else Wp.palette.disabled
    val track = Wp.palette.divider
    val thumb = Wp.palette.foreground
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        WpText("%+d".format(db.roundToInt()), style = WpType.Small, color = Wp.palette.subtle)
        Canvas(
            Modifier
                .weight(1f)
                .width(40.dp)
                .padding(vertical = 8.dp)
                .pointerInput(min, max) {
                    fun valueAt(y: Float) = (max - (y / size.height).coerceIn(0f, 1f) * (max - min)).let { (it * 2).roundToInt() / 2f }
                    detectTapGestures { update(valueAt(it.y)) }
                }
                .pointerInput(min, max) {
                    fun valueAt(y: Float) = (max - (y / size.height).coerceIn(0f, 1f) * (max - min)).let { (it * 2).roundToInt() / 2f }
                    detectDragGestures { change, _ -> change.consume(); update(valueAt(change.position.y)) }
                },
        ) {
            val cx = size.width / 2
            val zeroY = size.height * (max / (max - min))
            val y = size.height * ((max - shown) / (max - min))
            drawLine(track, Offset(cx, 0f), Offset(cx, size.height), 4.dp.toPx(), StrokeCap.Round)
            drawLine(accent, Offset(cx, zeroY), Offset(cx, y), 4.dp.toPx(), StrokeCap.Round)
            drawLine(track, Offset(cx - 8.dp.toPx(), zeroY), Offset(cx + 8.dp.toPx(), zeroY), 1.dp.toPx())
            drawRect(thumb, Offset(cx - 10.dp.toPx(), y - 4.dp.toPx()), Size(20.dp.toPx(), 8.dp.toPx()))
        }
        WpText(label, style = WpType.Small, color = Wp.palette.subtle)
    }
}

/** Equalizer response (a smooth curve through the bands) over the live spectrum bars. */
@Composable
private fun ResponseView(dev: EqDevice, bands: List<Float>, enabled: Boolean, spectrum: List<Float>, modifier: Modifier) {
    val accent = Wp.accent
    val grid = Wp.palette.divider
    val animated = dev.centersHz.indices.map { i ->
        animateFloatAsState(if (enabled) bands.getOrElse(i) { 0f } else 0f, spring(stiffness = 300f), label = "r$i").value
    }
    Canvas(modifier.background(Wp.palette.raised)) {
        val w = size.width
        val h = size.height
        val minF = 20f
        val maxF = 20000f
        fun xOf(hz: Float) = (ln(hz / minF) / ln(maxF / minF)) * w
        fun yOf(db: Float) = h / 2 - db / (dev.maxDb - dev.minDb) * h * 0.9f
        // Grid: 0 dB and decades.
        drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), 1f)
        listOf(100f, 1000f, 10000f).forEach { drawLine(grid, Offset(xOf(it), 0f), Offset(xOf(it), h), 1f) }
        // Spectrum.
        if (spectrum.isNotEmpty()) {
            val bw = w / spectrum.size
            spectrum.forEachIndexed { i, v ->
                val bh = v.coerceIn(0f, 1f) * h
                drawRect(accent.copy(alpha = 0.35f), Offset(i * bw + 1, h - bh), Size(bw - 2, bh))
            }
        }
        // Curve: Catmull-Rom through the bands, flat to the edges.
        val pts = listOf(Offset(0f, yOf(animated.firstOrNull() ?: 0f))) +
            dev.centersHz.mapIndexed { i, hz -> Offset(xOf(hz), yOf(animated[i])) } +
            Offset(w, yOf(animated.lastOrNull() ?: 0f))
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[(i - 1).coerceAtLeast(0)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[(i + 2).coerceAtMost(pts.lastIndex)]
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
                    p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
                    p2.x, p2.y,
                )
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)))
        drawPath(path, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        dev.centersHz.forEachIndexed { i, hz -> drawCircle(Color.White, 4.dp.toPx(), Offset(xOf(hz), yOf(animated[i]))) }
    }
}

private class Spectrum(val bars: List<Float>, val permitted: Boolean, val request: () -> Unit)

/** Live FFT of the music through Android's Visualizer (needs RECORD_AUDIO). */
@Composable
private fun rememberSpectrum(enabled: Boolean): Spectrum {
    val perm = rememberPermissionState(arrayOf(Manifest.permission.RECORD_AUDIO))
    var bars by remember { mutableStateOf(emptyList<Float>()) }
    val session = AudioEffects.audioSession
    DisposableEffect(perm.granted, enabled, session) {
        var viz: Visualizer? = null
        if (perm.granted && enabled && session != 0) {
            viz = runCatching {
                Visualizer(session).apply {
                    setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
                    val rate = Visualizer.getMaxCaptureRate() / 2
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null) bars = fftBars(fft, samplingRate / 1000f, 28, bars)
                        }
                    }, rate, false, true)
                    setEnabled(true)
                }
            }.getOrNull()
        } else {
            bars = emptyList()
        }
        onDispose { runCatching { viz?.setEnabled(false); viz?.release() } }
    }
    return Spectrum(bars, perm.granted, perm::request)
}

/** Groups FFT bins into [count] log-spaced bars (0..1), smoothed with the previous frame. */
private fun fftBars(fft: ByteArray, sampleRateHz: Float, count: Int, previous: List<Float>): List<Float> {
    val n = fft.size / 2
    val nyquist = sampleRateHz / 2
    val out = FloatArray(count)
    for (b in 0 until count) {
        val f0 = 20f * (1000f).pow(b.toFloat() / count)
        val f1 = 20f * (1000f).pow((b + 1).toFloat() / count)
        val i0 = (f0 / nyquist * n).toInt().coerceIn(1, n - 1)
        val i1 = (f1 / nyquist * n).toInt().coerceIn(i0 + 1, n)
        var peak = 0f
        for (k in i0 until i1) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            peak = maxOf(peak, hypot(re, im))
        }
        val db = 20 * log10(peak.coerceAtLeast(1f))
        val v = (db / 42f).coerceIn(0f, 1f)
        val prev = previous.getOrElse(b) { 0f }
        out[b] = if (v > prev) v else prev * 0.8f + v * 0.2f
    }
    return out.toList()
}
