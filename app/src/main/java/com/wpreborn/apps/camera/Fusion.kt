package com.wpreborn.apps.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ZUNEX Fusion: multi-frame computational photography, the technique behind Google's HDR+
 * and Apple's Smart HDR / Deep Fusion.
 *
 * The camera streams full-resolution frames (YUV). On the shutter a burst of the next frames
 * is kept (5-8, a fraction of a second; slightly underexposed when the sky would burn out).
 * The sharpest early frame is the reference; every other frame is aligned to it
 * (coarse-to-fine search) and merged per tile where it matches, while tiles that moved
 * (people, cars, leaves) keep the reference only, so nothing ghosts. Averaging N frames cuts
 * noise by about the square root of N, kept in 12-bit precision so the shadows can be lifted
 * without banding. A local tone map then brings up the shadows and holds the highlights.
 */
class YuvFrame(val w: Int, val h: Int, val y: ByteArray, val uv: ByteArray, val rotation: Int)

class FusionAnalyzer(private val onScene: (Scene) -> Unit) : ImageAnalysis.Analyzer {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
    private var last = 0L
    private val lock = Any()
    private var want = 0
    private val frames = ArrayList<YuvFrame>()
    private var onDone: ((List<YuvFrame>) -> Unit)? = null

    /** Average brightness (0..1) and share of burnt-out pixels in the latest frame. */
    @Volatile var brightness = 0.5f
        private set
    @Volatile var clipped = 0f
        private set

    /** Keeps the next [count] frames, then calls [done] (on the analysis thread). */
    fun burst(count: Int, done: (List<YuvFrame>) -> Unit) {
        synchronized(lock) {
            frames.clear()
            onDone = done
            want = count
        }
    }

    override fun analyze(image: ImageProxy) {
        try {
            var finished: Pair<List<YuvFrame>, (List<YuvFrame>) -> Unit>? = null
            synchronized(lock) {
                if (want > 0) {
                    runCatching { frames += copy(image) }
                    want--
                    if (want == 0) {
                        val d = onDone
                        onDone = null
                        if (d != null) finished = frames.toList() to d
                        frames.clear()
                    }
                }
            }
            finished?.let { (f, d) ->
                d(f)
                return
            }
            if (want > 0) return
            val now = System.currentTimeMillis()
            if (now - last < 900) return
            last = now
            val small = preview(image) ?: return
            labeler.process(InputImage.fromBitmap(small, image.imageInfo.rotationDegrees))
                .addOnSuccessListener { onScene(sceneOf(it, brightness)) }
        } finally {
            image.close()
        }
    }

    fun close() = runCatching { labeler.close() }

    /** A 160 px RGB copy for the scene labeller, and the brightness statistics. */
    private fun preview(image: ImageProxy): Bitmap? = runCatching {
        val w = image.width
        val h = image.height
        val yp = image.planes[0]
        val up = image.planes[1]
        val vp = image.planes[2]
        val sw = 160
        val sh = max(1, sw * h / w)
        val px = IntArray(sw * sh)
        var sum = 0L
        var clip = 0
        for (j in 0 until sh) {
            val y = j * h / sh
            for (i in 0 until sw) {
                val x = i * w / sw
                val yy = yp.buffer.get(y * yp.rowStride + x).toInt() and 255
                val u = (up.buffer.get((y / 2) * up.rowStride + (x / 2) * up.pixelStride).toInt() and 255) - 128
                val v = (vp.buffer.get((y / 2) * vp.rowStride + (x / 2) * vp.pixelStride).toInt() and 255) - 128
                sum += yy
                if (yy >= 250) clip++
                val r = (yy + 1.402f * v).toInt().coerceIn(0, 255)
                val g = (yy - 0.344f * u - 0.714f * v).toInt().coerceIn(0, 255)
                val b = (yy + 1.772f * u).toInt().coerceIn(0, 255)
                px[j * sw + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        brightness = sum / (px.size * 255f)
        clipped = clip / px.size.toFloat()
        Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    private fun copy(image: ImageProxy): YuvFrame {
        val w = image.width and 1.inv()
        val h = image.height and 1.inv()
        val yp = image.planes[0]
        val yb = yp.buffer
        val y = ByteArray(w * h)
        for (r in 0 until h) {
            yb.position(r * yp.rowStride)
            yb.get(y, r * w, w)
        }
        val up = image.planes[1]
        val vp = image.planes[2]
        val ub = up.buffer
        val vb = vp.buffer
        val cw = w / 2
        val ch = h / 2
        val uv = ByteArray(cw * ch * 2)
        val rowU = ByteArray(up.rowStride)
        val rowV = ByteArray(vp.rowStride)
        val ups = up.pixelStride
        val vps = vp.pixelStride
        for (r in 0 until ch) {
            ub.position(r * up.rowStride)
            ub.get(rowU, 0, min(up.rowStride, ub.remaining()))
            vb.position(r * vp.rowStride)
            vb.get(rowV, 0, min(vp.rowStride, vb.remaining()))
            var o = r * cw * 2
            for (c in 0 until cw) {
                uv[o++] = rowU[c * ups]
                uv[o++] = rowV[c * vps]
            }
        }
        return YuvFrame(w, h, y, uv, image.imageInfo.rotationDegrees)
    }
}

/** Box-averaged copy at 1/[f] size. */
private fun down(y: ByteArray, w: Int, h: Int, f: Int): IntArray {
    val dw = w / f
    val dh = h / f
    val out = IntArray(dw * dh)
    for (j in 0 until dh) {
        for (i in 0 until dw) {
            var s = 0
            for (b in 0 until f) {
                val row = (j * f + b) * w + i * f
                for (a in 0 until f) s += y[row + a].toInt() and 255
            }
            out[j * dw + i] = s / (f * f)
        }
    }
    return out
}

/** Sharpness (gradient energy) of the centre, to pick the least shaken frame. */
private fun sharpness(f: YuvFrame): Long {
    var s = 0L
    val x0 = f.w / 4
    val x1 = f.w * 3 / 4
    var yy = f.h / 4
    while (yy < f.h * 3 / 4) {
        val row = yy * f.w
        var x = x0
        while (x < x1) {
            s += abs((f.y[row + x + 1].toInt() and 255) - (f.y[row + x].toInt() and 255)) +
                abs((f.y[row + f.w + x].toInt() and 255) - (f.y[row + x].toInt() and 255))
            x += 3
        }
        yy += 8
    }
    return s
}

/** Offset (dx, dy) that best aligns [f] to [ref]: coarse search at 1/8, refined at full size. */
private fun align(ref: YuvFrame, refSmall: IntArray, f: YuvFrame): Pair<Int, Int> {
    val dw = ref.w / 8
    val dh = ref.h / 8
    val small = down(f.y, f.w, f.h, 8)
    val r = 12
    var best = Long.MAX_VALUE
    var bx = 0
    var by = 0
    val x0 = dw / 5
    val x1 = dw * 4 / 5
    val y0 = dh / 5
    val y1 = dh * 4 / 5
    for (oy in -r..r) {
        for (ox in -r..r) {
            var s = 0L
            var j = y0
            while (j < y1) {
                val rr = j * dw
                val fr = (j + oy) * dw
                var i = x0
                while (i < x1) {
                    s += abs(refSmall[rr + i] - small[fr + i + ox])
                    i += 2
                }
                j += 2
            }
            if (s < best) {
                best = s
                bx = ox
                by = oy
            }
        }
    }
    // Refine at full size around the coarse result.
    var fx = bx * 8
    var fy = by * 8
    best = Long.MAX_VALUE
    var rx = fx
    var ry = fy
    val w = ref.w
    val h = ref.h
    for (oy in fy - 6..fy + 6) {
        for (ox in fx - 6..fx + 6) {
            var s = 0L
            var yy = h / 4
            while (yy < h * 3 / 4) {
                val ty = yy + oy
                if (ty in 0 until h) {
                    val rr = yy * w
                    val fr = ty * w
                    var x = w / 4
                    while (x < w * 3 / 4) {
                        val tx = x + ox
                        if (tx in 0 until w) s += abs((ref.y[rr + x].toInt() and 255) - (f.y[fr + tx].toInt() and 255))
                        x += 7
                    }
                }
                yy += 7
            }
            if (s < best) {
                best = s
                rx = ox
                ry = oy
            }
        }
    }
    fx = rx
    fy = ry
    return fx to fy
}

/** Noise level of the reference (from the quieter half of neighbour differences). */
private fun noiseSigma(f: YuvFrame): Float {
    val hist = IntArray(256)
    var n = 0
    var yy = 8
    while (yy < f.h - 8) {
        val row = yy * f.w
        var x = 8
        while (x < f.w - 8) {
            hist[abs((f.y[row + x + 1].toInt() and 255) - (f.y[row + x].toInt() and 255))]++
            n++
            x += 5
        }
        yy += 11
    }
    var acc = 0
    var p30 = 0
    for (i in 0..255) {
        acc += hist[i]
        if (acc >= n * 0.3) {
            p30 = i
            break
        }
    }
    // |X1 - X2| for Gaussian noise: 30th percentile ≈ 0.54 σ.
    return max(0.8f, p30 / 0.54f)
}

/** The merged picture: luminance and chroma in 12-bit fixed point (value × 16). */
class Fused(val w: Int, val h: Int, val y: ShortArray, val uv: ShortArray, val rotation: Int)

private const val TILE = 16

suspend fun fuse(frames: List<YuvFrame>): Fused = coroutineScope {
    val candidates = frames.take(min(3, frames.size))
    val ref = candidates.maxBy { sharpness(it) }
    val w = ref.w
    val h = ref.h
    val accY = ShortArray(w * h) { ((ref.y[it].toInt() and 255) shl 4).toShort() }
    val accUV = ShortArray(ref.uv.size) { ((ref.uv[it].toInt() and 255) shl 4).toShort() }
    val tw = (w + TILE - 1) / TILE
    val th = (h + TILE - 1) / TILE
    val total = FloatArray(tw * th) { 1f }
    val sigma = noiseSigma(ref)
    val lo = 1.25f * sigma + 1.5f
    val hi = 2.6f * sigma + 4f
    val refSmall = down(ref.y, w, h, 8)
    for (f in frames) {
        if (f === ref || f.w != w || f.h != h) continue
        val (dx, dy) = align(ref, refSmall, f)
        // Per-tile weights: 1 where the frame matches the reference, 0 where something moved.
        val weight = FloatArray(tw * th)
        (0 until th).map { ty ->
            async(Dispatchers.Default) {
                for (tx in 0 until tw) {
                    var s = 0
                    var n = 0
                    val yEnd = min(h, (ty + 1) * TILE)
                    val xEnd = min(w, (tx + 1) * TILE)
                    var yy = ty * TILE
                    while (yy < yEnd) {
                        val sy = yy + dy
                        if (sy in 0 until h) {
                            var x = tx * TILE
                            while (x < xEnd) {
                                val sx = x + dx
                                if (sx in 0 until w) {
                                    s += abs((ref.y[yy * w + x].toInt() and 255) - (f.y[sy * w + sx].toInt() and 255))
                                    n++
                                }
                                x += 2
                            }
                        }
                        yy += 2
                    }
                    weight[ty * tw + tx] = if (n < TILE) 0f else {
                        val d = s / n.toFloat()
                        ((hi - d) / (hi - lo)).coerceIn(0f, 1f)
                    }
                }
            }
        }.awaitAll()
        // Running weighted average; weights interpolated between tile centres (no seams).
        val stripe = 64
        (0 until h step stripe).map { y0 ->
            async(Dispatchers.Default) {
                for (yy in y0 until min(h, y0 + stripe)) {
                    val sy = yy + dy
                    if (sy !in 0 until h) continue
                    val gy = ((yy - TILE / 2f) / TILE).coerceIn(0f, th - 1f)
                    val ty0 = gy.toInt()
                    val ty1 = min(th - 1, ty0 + 1)
                    val fy = gy - ty0
                    for (x in 0 until w) {
                        val sx = x + dx
                        if (sx !in 0 until w) continue
                        val gx = ((x - TILE / 2f) / TILE).coerceIn(0f, tw - 1f)
                        val tx0 = gx.toInt()
                        val tx1 = min(tw - 1, tx0 + 1)
                        val fx = gx - tx0
                        val wt = (weight[ty0 * tw + tx0] * (1 - fx) + weight[ty0 * tw + tx1] * fx) * (1 - fy) +
                            (weight[ty1 * tw + tx0] * (1 - fx) + weight[ty1 * tw + tx1] * fx) * fy
                        if (wt <= 0.01f) continue
                        val tot = (total[ty0 * tw + tx0] * (1 - fx) + total[ty0 * tw + tx1] * fx) * (1 - fy) +
                            (total[ty1 * tw + tx0] * (1 - fx) + total[ty1 * tw + tx1] * fx) * fy
                        val i = yy * w + x
                        val v = (f.y[sy * w + sx].toInt() and 255) shl 4
                        accY[i] = ((accY[i] * tot + v * wt) / (tot + wt)).roundToInt().toShort()
                        // Chroma once per 2×2 block.
                        if (yy and 1 == 0 && x and 1 == 0) {
                            val ci = ((yy / 2) * (w / 2) + x / 2) * 2
                            val cs = (((sy / 2).coerceAtMost(h / 2 - 1)) * (w / 2) + (sx / 2).coerceAtMost(w / 2 - 1)) * 2
                            if (ci + 1 < accUV.size && cs + 1 < f.uv.size) {
                                val u = (f.uv[cs].toInt() and 255) shl 4
                                val vv = (f.uv[cs + 1].toInt() and 255) shl 4
                                accUV[ci] = ((accUV[ci] * tot + u * wt) / (tot + wt)).roundToInt().toShort()
                                accUV[ci + 1] = ((accUV[ci + 1] * tot + vv * wt) / (tot + wt)).roundToInt().toShort()
                            }
                        }
                    }
                }
            }
        }.awaitAll()
        // Tile totals, after the pixels (they read the totals before this frame).
        for (t in total.indices) total[t] += weight[t]
    }
    Fused(w, h, accY, accUV, ref.rotation)
}

/**
 * The merged picture as an upright RGB bitmap, with a local tone map: a soft, wide-blurred
 * brightness map decides how much each area is lifted (shadows) or held (highlights), so
 * detail and local contrast stay while the whole scene fits.
 */
suspend fun renderFused(f: Fused, mirror: Boolean, underexposed: Boolean): Bitmap = coroutineScope {
    val w = f.w
    val h = f.h
    // Brightness map at 1/16, blurred wide (about 100 px at full size).
    val bw = max(1, w / 16)
    val bh = max(1, h / 16)
    val base = FloatArray(bw * bh)
    for (j in 0 until bh) {
        for (i in 0 until bw) {
            var s = 0
            var n = 0
            var yy = j * 16
            while (yy < min(h, j * 16 + 16)) {
                var x = i * 16
                while (x < min(w, i * 16 + 16)) {
                    s += f.y[yy * w + x]
                    n++
                    x += 4
                }
                yy += 4
            }
            base[j * bw + i] = s / (max(1, n) * 16f * 255f)
        }
    }
    val tmp = FloatArray(base.size)
    repeat(2) {
        blur(base, tmp, bw, bh, 4, true)
        blur(tmp, base, bw, bh, 4, false)
    }
    var mean = 0.0
    for (v in base) mean += v
    mean = (mean / base.size).coerceIn(0.01, 0.99)
    // Exposure so the average lands near 0.45 after the curve; more when underexposed.
    fun curve(x: Float, k: Float): Float {
        val l = k * x
        val white = k
        return (l * (1 + l / (white * white)) / (1 + l)).coerceIn(0f, 1f)
    }
    var k = 1f
    var lo = 0.6f
    var hiK = 8f
    repeat(24) {
        k = (lo + hiK) / 2
        if (curve(mean.toFloat(), k) < 0.45f) lo = k else hiK = k
    }
    k = k.coerceIn(if (underexposed) 1.6f else 1f, 6f)
    val lut = FloatArray(1025) { i ->
        val b = max(i / 1024f, 0.003f)
        (curve(b, k) / b).coerceAtMost(8f)
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val stripe = 64
    (0 until h step stripe).map { y0 ->
        async(Dispatchers.Default) {
            val y1 = min(h, y0 + stripe)
            val px = IntArray((y1 - y0) * w)
            for (yy in y0 until y1) {
                val gy = (yy / 16f - 0.5f).coerceIn(0f, bh - 1f)
                val j0 = gy.toInt()
                val j1 = min(bh - 1, j0 + 1)
                val fy = gy - j0
                for (x in 0 until w) {
                    val gx = (x / 16f - 0.5f).coerceIn(0f, bw - 1f)
                    val i0 = gx.toInt()
                    val i1 = min(bw - 1, i0 + 1)
                    val fx = gx - i0
                    val b = (base[j0 * bw + i0] * (1 - fx) + base[j0 * bw + i1] * fx) * (1 - fy) +
                        (base[j1 * bw + i0] * (1 - fx) + base[j1 * bw + i1] * fx) * fy
                    val g = lut[(b * 1024).toInt().coerceIn(0, 1024)]
                    val yv = f.y[yy * w + x] / 16f * g
                    val ci = ((yy / 2) * (w / 2) + min(x / 2, w / 2 - 1)) * 2
                    val u = (f.uv[ci] / 16f - 128f) * g
                    val v = (f.uv[ci + 1] / 16f - 128f) * g
                    val r = (yv + 1.402f * v).toInt().coerceIn(0, 255)
                    val gg = (yv - 0.344f * u - 0.714f * v).toInt().coerceIn(0, 255)
                    val bb = (yv + 1.772f * u).toInt().coerceIn(0, 255)
                    px[(yy - y0) * w + x] = (0xFF shl 24) or (r shl 16) or (gg shl 8) or bb
                }
            }
            synchronized(out) { out.setPixels(px, 0, w, 0, y0, w, y1 - y0) }
        }
    }.awaitAll()
    if (f.rotation == 0 && !mirror) return@coroutineScope out
    val m = Matrix().apply {
        postRotate(f.rotation.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    Bitmap.createBitmap(out, 0, 0, w, h, m, true).also { if (it !== out) out.recycle() }
}

private fun blur(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    for (line in 0 until lines) {
        for (k in 0 until len) {
            var s = 0f
            var n = 0
            for (d in -r..r) {
                val c = (k + d).coerceIn(0, len - 1)
                s += if (horizontal) src[line * w + c] else src[c * w + line]
                n++
            }
            if (horizontal) dst[line * w + k] = s / n else dst[k * w + line] = s / n
        }
    }
}
