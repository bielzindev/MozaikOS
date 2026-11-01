package com.wpreborn.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal image loader for MediaStore content: thumbnails through
 * ContentResolver.loadThumbnail (API 29+) with an in-memory LRU cache.
 */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(uri: Uri, size: Int): Bitmap? = cache.get("$uri@$size")

    fun cachedFull(uri: Uri, maxSide: Int): Bitmap? = cache.get("$uri#full$maxSide")

    suspend fun thumbnail(context: Context, uri: Uri, size: Int): Bitmap? {
        val key = "$uri@$size"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bmp = try {
                context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } catch (_: Exception) {
                decodeSampled(context, uri, size)
            }
            bmp?.also { cache.put(key, it) }
        }
    }

    /** Decodes a full image scaled down to roughly [maxSide] pixels, honouring EXIF rotation. */
    suspend fun full(context: Context, uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri#full$maxSide"
        cache.get(key) ?: try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxOf(1f, maxOf(w, h) / maxSide.toFloat())
                decoder.setTargetSize((w / scale).toInt().coerceAtLeast(1), (h / scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.also { cache.put(key, it) }
        } catch (_: Exception) {
            decodeSampled(context, uri, maxSide)
        }
    }

    /** A picture at up to [maxSide] pixels for close zoom; not cached, in graphics memory when possible. */
    suspend fun large(context: Context, uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxOf(1f, maxOf(w, h) / maxSide.toFloat())
                decoder.setTargetSize((w / scale).toInt().coerceAtLeast(1), (h / scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, size: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun rememberThumbnail(uri: Uri?, sizePx: Int = 256): State<ImageBitmap?> {
    val context = LocalContext.current
    val initial = remember(uri, sizePx) { uri?.let { ImageLoader.cached(it, sizePx)?.asImageBitmap() } }
    return produceState(initial, uri, sizePx) {
        // When the picture changes (next song...), the state still holds the previous one:
        // always load the new one (keeping the old visible meanwhile), or clear it if none.
        val cached = uri?.let { ImageLoader.cached(it, sizePx) }
        value = when {
            uri == null -> null
            cached != null -> cached.asImageBitmap()
            else -> ImageLoader.thumbnail(context, uri, sizePx)?.asImageBitmap()
        }
    }
}

/** A thumbnail that fades in once loaded, with [placeholder] shown meanwhile. */
@Composable
fun Thumbnail(
    uri: Uri?,
    modifier: Modifier = Modifier,
    sizePx: Int = 256,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    val bmp by rememberThumbnail(uri, sizePx)
    Box(modifier) {
        val image = bmp
        if (image == null) {
            placeholder()
        } else {
            val alpha = remember(image) { Animatable(0f) }
            LaunchedEffect(image) { alpha.animateTo(1f, tween(250)) }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}
