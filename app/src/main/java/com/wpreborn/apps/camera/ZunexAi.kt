package com.wpreborn.apps.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.wpreborn.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ZUNEX AI for the camera, on the phone (ML Kit, no internet):
 * - scene recognition in the viewfinder (image labelling of a small preview frame);
 * - per-scene photo processing: white balance, exposure, tone curve, vibrance with skin
 *   protection and sharpening, tuned for each kind of scene;
 * - AI portrait: the person is separated from the background (selfie segmentation) and the
 *   background is blurred.
 */
enum class Scene(val label: Int) {
    General(R.string.camera_scene_general),
    People(R.string.camera_scene_people),
    Food(R.string.camera_scene_food),
    Landscape(R.string.camera_scene_landscape),
    Sky(R.string.camera_scene_sky),
    Plants(R.string.camera_scene_plants),
    Animal(R.string.camera_scene_animal),
    Sunset(R.string.camera_scene_sunset),
    Night(R.string.camera_scene_night),
}

private val FOOD = setOf("food", "dessert", "fruit", "cake", "pizza", "vegetable", "bread", "cookie", "cuisine", "sushi", "pasta", "baked goods", "meal", "dish", "coffee", "juice", "drink")
private val LAND = setOf("mountain", "beach", "lake", "river", "waterfall", "sea", "ocean", "landscape", "desert", "field", "hill", "coast", "canyon", "glacier", "skyline", "building", "bridge", "city")
private val SKY = setOf("sky", "cloud", "rainbow")
private val PLANTS = setOf("flower", "plant", "tree", "garden", "leaf", "grass", "forest", "petal", "flora", "moss", "jungle")
private val ANIMAL = setOf("dog", "cat", "bird", "horse", "pet", "animal", "fish", "butterfly", "insect", "cattle", "duck", "rabbit")
private val PEOPLE = setOf("selfie", "smile", "beard", "hair", "skin", "eyelash", "person", "muscle", "hand", "tattoo", "lipstick", "child", "baby", "team", "crowd")
private val SUNSET = setOf("sunset", "sunrise", "dusk", "afterglow")
private val NIGHT = setOf("night", "darkness", "fireworks", "moon", "star", "neon")

fun sceneOf(labels: List<ImageLabel>, brightness: Float): Scene {
    if (brightness in 0f..0.16f) return Scene.Night
    for (l in labels.sortedByDescending { it.confidence }) {
        val t = l.text.lowercase()
        when (t) {
            in PEOPLE -> return Scene.People
            in FOOD -> return Scene.Food
            in SUNSET -> return Scene.Sunset
            in NIGHT -> return Scene.Night
            in ANIMAL -> return Scene.Animal
            in PLANTS -> return Scene.Plants
            in LAND -> return Scene.Landscape
            in SKY -> return Scene.Sky
        }
    }
    return Scene.General
}

/** Recognises the scene in the viewfinder about once a second. */
class SceneDetector(private val onScene: (Scene) -> Unit) : ImageAnalysis.Analyzer {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - last < 900) {
            image.close()
            return
        }
        last = now
        val bmp = try {
            image.toBitmap()
        } catch (_: Throwable) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        image.close()
        val brightness = meanLuma(bmp)
        labeler.process(InputImage.fromBitmap(bmp, rotation))
            .addOnSuccessListener { onScene(sceneOf(it, brightness)) }
    }

    fun close() = runCatching { labeler.close() }

    private fun meanLuma(b: Bitmap): Float {
        var sum = 0L
        var n = 0
        val stepX = max(1, b.width / 40)
        val stepY = max(1, b.height / 30)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                sum += ((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 0.5f else sum / (n * 255f)
    }
}

private class Tuning(val wb: Float, val target: Float, val contrast: Float, val lift: Float, val vibrance: Float, val sharpen: Float, val warmth: Float, val skin: Boolean)

private fun tuning(scene: Scene): Tuning = when (scene) {
    Scene.People -> Tuning(wb = 0.35f, target = 0.48f, contrast = 0.10f, lift = 0.10f, vibrance = 0.10f, sharpen = 0.18f, warmth = 0.02f, skin = true)
    Scene.Food -> Tuning(0.15f, 0.50f, 0.20f, 0.06f, 0.32f, 0.35f, 0.04f, false)
    Scene.Landscape -> Tuning(0.35f, 0.45f, 0.26f, 0.10f, 0.32f, 0.50f, 0f, false)
    Scene.Sky -> Tuning(0.30f, 0.44f, 0.22f, 0.08f, 0.35f, 0.40f, -0.02f, false)
    Scene.Plants -> Tuning(0.30f, 0.46f, 0.22f, 0.08f, 0.36f, 0.45f, 0f, false)
    Scene.Animal -> Tuning(0.35f, 0.47f, 0.18f, 0.10f, 0.18f, 0.50f, 0f, false)
    Scene.Sunset -> Tuning(0.05f, 0.42f, 0.20f, 0.06f, 0.30f, 0.30f, 0.03f, false)
    Scene.Night -> Tuning(0.25f, 0.34f, 0.08f, 0.20f, 0.12f, 0.12f, 0f, false)
    Scene.General -> Tuning(0.35f, 0.46f, 0.20f, 0.10f, 0.22f, 0.38f, 0f, true)
}

/**
 * Processes a captured JPEG with the AI and saves it to Pictures/ZUNEX. Falls back to the
 * camera's own JPEG if anything fails (e.g. memory). Returns the saved picture.
 */
suspend fun saveAiPhoto(
    context: Context,
    jpeg: ByteArray,
    rotationDegrees: Int,
    mirror: Boolean,
    scene: Scene,
    portrait: Boolean,
    name: String,
): Uri? = withContext(Dispatchers.Default) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ZUNEX")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
    val original = runCatching { ExifInterface(ByteArrayInputStream(jpeg)) }.getOrNull()
    val processed = try {
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: throw IllegalStateException("decode")
        // Upright (and mirrored if asked), so the AI sees the scene the right way up.
        val upright = if (rotationDegrees != 0 || mirror) {
            val m = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (mirror) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val mask = if (portrait) personMask(upright) else null
        val out = enhance(upright, tuning(scene), mask)
        upright.recycle()
        out
    } catch (_: Throwable) {
        null
    }
    try {
        resolver.openOutputStream(uri)?.use { os ->
            if (processed != null) processed.compress(Bitmap.CompressFormat.JPEG, 95, os) else os.write(jpeg)
        }
        processed?.recycle()
        if (processed != null) {
            runCatching {
                resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                    val exif = ExifInterface(fd.fileDescriptor)
                    original?.let { o ->
                        for (tag in EXIF_TAGS) o.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
                    }
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "ZUNEX Camera AI")
                    exif.saveAttributes()
                }
            }
        } else if (rotationDegrees != 0 || mirror) {
            // The camera's JPEG is not turned: say how to show it.
            runCatching {
                resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                    val exif = ExifInterface(fd.fileDescriptor)
                    exif.resetOrientation()
                    exif.rotate(rotationDegrees)
                    if (mirror) exif.flipHorizontally()
                    exif.saveAttributes()
                }
            }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        uri
    } catch (_: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

private val EXIF_TAGS = listOf(
    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
    ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
)

/** Where the person is, as a small confidence map (0..1) in the picture's own proportions. */
private class Mask(val w: Int, val h: Int, val v: FloatArray)

private fun personMask(bmp: Bitmap): Mask? {
    val scale = 512f / max(bmp.width, bmp.height)
    val small = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * scale).roundToInt()), max(1, (bmp.height * scale).roundToInt()), true)
    val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
    return try {
        val result = Tasks.await(segmenter.process(InputImage.fromBitmap(small, 0)))
        val buf = result.buffer
        buf.rewind()
        val n = result.width * result.height
        val v = FloatArray(n)
        buf.asFloatBuffer().get(v, 0, n)
        // Nobody there: no blur.
        if (v.count { it > 0.6f } < n / 50) null else Mask(result.width, result.height, v)
    } catch (_: Throwable) {
        null
    } finally {
        small.recycle()
        runCatching { segmenter.close() }
    }
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun clamp255(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v

private suspend fun enhance(src: Bitmap, t: Tuning, mask: Mask?): Bitmap = coroutineScope {
    val w = src.width
    val h = src.height

    // Statistics on a small copy.
    val statsScale = 256f / max(w, h)
    val small = Bitmap.createScaledBitmap(src, max(1, (w * statsScale).roundToInt()), max(1, (h * statsScale).roundToInt()), true)
    val sp = IntArray(small.width * small.height)
    small.getPixels(sp, 0, small.width, 0, 0, small.width, small.height)
    small.recycle()
    var sr = 0.0
    var sg = 0.0
    var sb = 0.0
    for (p in sp) {
        sr += p shr 16 and 255
        sg += p shr 8 and 255
        sb += p and 255
    }
    val n = sp.size.coerceAtLeast(1)
    val mr = (sr / n).coerceAtLeast(1.0)
    val mg = (sg / n).coerceAtLeast(1.0)
    val mb = (sb / n).coerceAtLeast(1.0)
    val gray = (mr + mg + mb) / 3
    // Partial grey-world white balance (keeps the mood of warm or cool scenes), plus warmth.
    val gr = ((gray / mr).pow(t.wb.toDouble()) * (1 + t.warmth)).coerceIn(0.85, 1.2)
    val gg = (gray / mg).pow(t.wb.toDouble()).coerceIn(0.85, 1.2)
    val gb = ((gray / mb).pow(t.wb.toDouble()) * (1 - t.warmth)).coerceIn(0.85, 1.2)

    // Levels and exposure from the luminance histogram after white balance.
    val hist = IntArray(256)
    for (p in sp) {
        val r = min(255.0, (p shr 16 and 255) * gr)
        val g = min(255.0, (p shr 8 and 255) * gg)
        val b = min(255.0, (p and 255) * gb)
        hist[((r * 0.299 + g * 0.587 + b * 0.114).toInt()).coerceIn(0, 255)]++
    }
    fun percentile(q: Double): Int {
        var acc = 0
        val limit = (n * q).toInt()
        for (i in 0..255) {
            acc += hist[i]
            if (acc > limit) return i
        }
        return 255
    }
    val lo = min(percentile(0.004), 14) / 255f
    val hi = max(percentile(0.997), 205) / 255f
    var mean = 0.0
    for (i in 0..255) mean += hist[i] * ((i / 255f - lo) / (hi - lo)).coerceIn(0f, 1f)
    mean = (mean / n).coerceIn(0.02, 0.98)
    val gamma = (ln(t.target.toDouble()) / ln(mean)).coerceIn(0.62, 1.2)

    fun tone(x0: Float): Float {
        var x = ((x0 - lo) / (hi - lo)).coerceIn(0f, 1f)
        x = x.toDouble().pow(gamma).toFloat()
        // Shadow lift, then a gentle S-curve for depth.
        x += t.lift * 4f * x * (1 - x) * (1 - x) * (1 - x)
        x = (1 - t.contrast) * x + t.contrast * (x * x * (3 - 2 * x))
        return x.coerceIn(0f, 1f)
    }
    val lutR = IntArray(256) { (tone(min(1.0, it * gr / 255).toFloat()) * 255).roundToInt() }
    val lutG = IntArray(256) { (tone(min(1.0, it * gg / 255).toFloat()) * 255).roundToInt() }
    val lutB = IntArray(256) { (tone(min(1.0, it * gb / 255).toFloat()) * 255).roundToInt() }

    // Background for AI portrait: a strongly blurred small copy.
    val blurred = mask?.let { blurredSmall(src) }
    val bg = blurred?.px
    val bgW = blurred?.w ?: 0
    val bgH = blurred?.h ?: 0

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val stripe = 128
    val vib = t.vibrance
    val sharp = t.sharpen
    val jobs = (0 until h step stripe).map { y0 ->
        async(Dispatchers.Default) {
            val y1 = min(h, y0 + stripe)
            val top = max(0, y0 - 1)
            val bottom = min(h, y1 + 1)
            val rows = bottom - top
            val a = IntArray(rows * w)
            src.getPixels(a, 0, w, 0, top, w, rows)
            val luma = IntArray(rows * w)
            // Pass 1: colour and tone.
            for (i in a.indices) {
                val p = a[i]
                var r = lutR[p shr 16 and 255]
                var g = lutG[p shr 8 and 255]
                var b = lutB[p and 255]
                val y = (r * 77 + g * 150 + b * 29) shr 8
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                var f = 1f + vib * (1f - (mx - mn) / 255f)
                if (t.skin && r > g && g > b && r - b in 20..130 && r > 80) f = 1f + (f - 1f) * 0.3f
                r = clamp255(y + ((r - y) * f).toInt())
                g = clamp255(y + ((g - y) * f).toInt())
                b = clamp255(y + ((b - y) * f).toInt())
                a[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                luma[i] = (r * 77 + g * 150 + b * 29) shr 8
            }
            // Pass 2: sharpening (unsharp mask on luminance, with noise coring) and portrait.
            val o = IntArray((y1 - y0) * w)
            for (yy in y0 until y1) {
                val ry = yy - top
                val up = if (yy > 0) ry - 1 else ry
                val dn = if (yy < h - 1) ry + 1 else ry
                for (x in 0 until w) {
                    val i = ry * w + x
                    val p = a[i]
                    val l = if (x > 0) i - 1 else i
                    val rr = if (x < w - 1) i + 1 else i
                    val avg = (luma[l] + luma[rr] + luma[up * w + x] + luma[dn * w + x]) shr 2
                    var d = luma[i] - avg
                    d = if (d in -2..2) 0 else d
                    val add = (d * sharp * 2f).toInt()
                    var r = clamp255((p shr 16 and 255) + add)
                    var g = clamp255((p shr 8 and 255) + add)
                    var b = clamp255((p and 255) + add)
                    if (mask != null && bg != null && bgW > 0) {
                        val mx = (x * (mask.w - 1f) / max(1, w - 1)).toInt()
                        val my = (yy * (mask.h - 1f) / max(1, h - 1)).toInt()
                        val m = smooth(0.35f, 0.75f, mask.v[my * mask.w + mx])
                        if (m < 1f) {
                            val bx = (x * (bgW - 1f) / max(1, w - 1)).toInt()
                            val by = (yy * (bgH - 1f) / max(1, h - 1)).toInt()
                            val q = bg[by * bgW + bx]
                            r = (r * m + (q shr 16 and 255) * (1 - m)).toInt()
                            g = (g * m + (q shr 8 and 255) * (1 - m)).toInt()
                            b = (b * m + (q and 255) * (1 - m)).toInt()
                        }
                    }
                    o[(yy - y0) * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            synchronized(out) { out.setPixels(o, 0, w, 0, y0, w, y1 - y0) }
        }
    }
    jobs.awaitAll()
    out
}

/** A small (480 px) copy of the picture, blurred like a wide-aperture lens background. */
private class Small(val w: Int, val h: Int, val px: IntArray)

private fun blurredSmall(src: Bitmap): Small {
    val s = 480f / max(src.width, src.height)
    val bw = max(1, (src.width * s).roundToInt())
    val bh = max(1, (src.height * s).roundToInt())
    val small = Bitmap.createScaledBitmap(src, bw, bh, true)
    val px = IntArray(bw * bh)
    small.getPixels(px, 0, bw, 0, 0, bw, bh)
    small.recycle()
    val tmp = IntArray(px.size)
    val radius = 9
    repeat(3) {
        boxBlur(px, tmp, bw, bh, radius, horizontal = true)
        boxBlur(tmp, px, bw, bh, radius, horizontal = false)
    }
    return Small(bw, bh, px)
}

private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    for (line in 0 until lines) {
        var sr = 0
        var sg = 0
        var sb = 0
        fun at(k: Int): Int {
            val c = k.coerceIn(0, len - 1)
            return if (horizontal) src[line * w + c] else src[c * w + line]
        }
        for (k in -r..r) {
            val p = at(k)
            sr += p shr 16 and 255
            sg += p shr 8 and 255
            sb += p and 255
        }
        val div = 2 * r + 1
        for (k in 0 until len) {
            val idx = if (horizontal) line * w + k else k * w + line
            dst[idx] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = at(k + r + 1)
            val rem = at(k - r)
            sr += (add shr 16 and 255) - (rem shr 16 and 255)
            sg += (add shr 8 and 255) - (rem shr 8 and 255)
            sb += (add and 255) - (rem and 255)
        }
    }
}
