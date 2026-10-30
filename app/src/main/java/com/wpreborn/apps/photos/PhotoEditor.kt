package com.wpreborn.apps.photos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.ImageLoader
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Crop presets of the editor; Original keeps the picture as it is. */
enum class CropPreset(val ratio: Float?) { Original(null), Square(1f), FourThree(4f / 3f), SixteenNine(16f / 9f) }

/** Colour filters, in the spirit of the Lumia "Creative Studio" looks. */
enum class PhotoFilter { None, Vivid, BlackWhite, Sepia, Cool, Warm }

/** Everything the user changed; applied identically to the preview and the saved file. */
data class PhotoEdit(
    val rotation: Int = 0,
    val crop: CropPreset = CropPreset.Original,
    val autoFix: Boolean = false,
    val filter: PhotoFilter = PhotoFilter.None,
    /** -1..1 */
    val brightness: Float = 0f,
    /** -1..1 */
    val contrast: Float = 0f,
    /** -1..1 */
    val saturation: Float = 0f,
)

object PhotoEditing {

    fun colorMatrix(edit: PhotoEdit): android.graphics.ColorMatrix {
        val m = android.graphics.ColorMatrix()
        var brightness = edit.brightness * 60f
        var contrast = 1f + edit.contrast * 0.6f
        var saturation = 1f + edit.saturation
        if (edit.autoFix) {
            brightness += 8f
            contrast *= 1.12f
            saturation *= 1.18f
        }
        m.setSaturation(saturation.coerceAtLeast(0f))
        val t = (1f - contrast) * 128f + brightness
        m.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val filter = when (edit.filter) {
            PhotoFilter.None -> null
            PhotoFilter.Vivid -> android.graphics.ColorMatrix().apply { setSaturation(1.5f) }
            PhotoFilter.BlackWhite -> android.graphics.ColorMatrix().apply { setSaturation(0f) }
            PhotoFilter.Sepia -> android.graphics.ColorMatrix().apply {
                setSaturation(0f)
                postConcat(android.graphics.ColorMatrix().apply { setScale(1f, 0.89f, 0.71f, 1f) })
            }
            PhotoFilter.Cool -> android.graphics.ColorMatrix().apply { setScale(0.9f, 1f, 1.15f, 1f) }
            PhotoFilter.Warm -> android.graphics.ColorMatrix().apply { setScale(1.12f, 1.02f, 0.88f, 1f) }
        }
        filter?.let { m.postConcat(it) }
        return m
    }

    /** Applies rotation, crop and colour to [source], producing a new bitmap. */
    fun render(source: Bitmap, edit: PhotoEdit): Bitmap {
        val rotated = if (edit.rotation % 360 != 0) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(edit.rotation.toFloat()) }, true)
        } else {
            source
        }
        val ratio = edit.crop.ratio
        val cropped = if (ratio == null) {
            rotated
        } else {
            val w = rotated.width
            val h = rotated.height
            // Fit the ratio in the picture, keeping its orientation (landscape stays landscape).
            val target = if (w >= h) ratio else 1f / ratio
            val (cw, ch) = if (w / h.toFloat() > target) ((h * target).toInt() to h) else (w to (w / target).toInt())
            Bitmap.createBitmap(rotated, (w - cw) / 2, (h - ch) / 2, cw.coerceAtLeast(1), ch.coerceAtLeast(1))
        }
        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(cropped, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix(edit)) })
        return out
    }

    /** Saves a new JPEG in Pictures/ZUNEX. The original photo is never modified. */
    suspend fun saveCopy(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ZUNEX_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZUNEX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        try {
            val uri = resolver.insert(collection, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * The photo editor of the Photos app: rotate, crop presets, auto-fix, filters and
 * brightness/contrast/saturation sliders. Saving writes a copy, the original stays intact.
 */
@Composable
fun PhotoEditorPage(photo: Photo, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview by produceState<Bitmap?>(null, photo.id) { value = ImageLoader.full(context, photo.uri, 1280) }
    var edit by remember { mutableStateOf(PhotoEdit()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tool by remember { mutableIntStateOf(0) }
    val rendered by produceState<ImageBitmap?>(null, preview, edit) {
        val src = preview ?: return@produceState
        value = withContext(Dispatchers.Default) { PhotoEditing.render(src, edit).asImageBitmap() }
    }
    val savedText = stringResource(R.string.photos_saved)
    val failedText = stringResource(R.string.photos_save_failed)

    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_photos), stringResource(R.string.photos_edit))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            val image = rendered
            if (image == null) LoadingDots() else Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (saving) LoadingDots(Modifier.align(Alignment.TopCenter))
        }
        message?.let { WpText(it, style = WpType.Subtle, color = Wp.accent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            val labels = listOf(
                stringResource(R.string.photos_brightness),
                stringResource(R.string.photos_contrast),
                stringResource(R.string.photos_saturation),
            )
            Row {
                labels.forEachIndexed { i, label ->
                    WpText(
                        label,
                        style = WpType.Normal,
                        color = if (tool == i) Wp.palette.foreground else Wp.palette.inactive,
                        modifier = Modifier.wpClickable(tilt = false) { tool = i }.padding(end = 16.dp, bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            var slider by remember(tool) {
                mutableFloatStateOf(
                    when (tool) {
                        0 -> edit.brightness
                        1 -> edit.contrast
                        else -> edit.saturation
                    },
                )
            }
            WpSlider(
                (slider + 1f) / 2f,
                { v ->
                    slider = v * 2f - 1f
                    edit = when (tool) {
                        0 -> edit.copy(brightness = slider)
                        1 -> edit.copy(contrast = slider)
                        else -> edit.copy(saturation = slider)
                    }
                },
            )
            Row(Modifier.fillMaxWidth()) {
                WpText(stringResource(filterLabel(edit.filter)), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.weight(1f))
                WpText(stringResource(cropLabel(edit.crop)), style = WpType.Small, color = Wp.palette.subtle)
                Spacer(Modifier.width(4.dp))
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.RotationLock, stringResource(R.string.photos_rotate)) { edit = edit.copy(rotation = (edit.rotation + 90) % 360) },
                AppBarButton(WpIcons.SelectAll, stringResource(R.string.photos_crop)) {
                    edit = edit.copy(crop = CropPreset.entries[(edit.crop.ordinal + 1) % CropPreset.entries.size])
                },
                AppBarButton(WpIcons.Palette, stringResource(R.string.photos_filter)) {
                    edit = edit.copy(filter = PhotoFilter.entries[(edit.filter.ordinal + 1) % PhotoFilter.entries.size])
                },
                AppBarButton(WpIcons.Check, stringResource(R.string.save), enabled = preview != null && !saving) {
                    val src = preview
                    if (src != null) {
                    saving = true
                    scope.launch {
                        // Re-decode at a larger size for the saved copy.
                        val full = ImageLoader.full(context, photo.uri, 4096) ?: src
                        val result = withContext(Dispatchers.Default) { PhotoEditing.render(full, edit) }
                        val uri = PhotoEditing.saveCopy(context, result)
                        saving = false
                        message = if (uri != null) savedText else failedText
                        if (uri != null) onSaved()
                    }
                    }
                },
            ),
            menuItems = listOf(
                AppBarMenuItem(stringResource(if (edit.autoFix) R.string.photos_autofix_off else R.string.photos_autofix)) { edit = edit.copy(autoFix = !edit.autoFix) },
                AppBarMenuItem(stringResource(R.string.photos_reset)) { edit = PhotoEdit() },
            ),
        )
    }
}

private fun filterLabel(f: PhotoFilter) = when (f) {
    PhotoFilter.None -> R.string.filter_none
    PhotoFilter.Vivid -> R.string.filter_vivid
    PhotoFilter.BlackWhite -> R.string.filter_bw
    PhotoFilter.Sepia -> R.string.filter_sepia
    PhotoFilter.Cool -> R.string.filter_cool
    PhotoFilter.Warm -> R.string.filter_warm
}

private fun cropLabel(c: CropPreset) = when (c) {
    CropPreset.Original -> R.string.crop_original
    CropPreset.Square -> R.string.crop_square
    CropPreset.FourThree -> R.string.crop_4_3
    CropPreset.SixteenNine -> R.string.crop_16_9
}
