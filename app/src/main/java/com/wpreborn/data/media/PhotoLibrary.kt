package com.wpreborn.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Photo(val id: Long, val dateTaken: Long, val bucketId: Long, val bucket: String, val isVideo: Boolean, val durationMs: Long = 0) {
    val uri: Uri
        get() = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )
}

data class PhotoAlbum(val id: Long, val name: String, val photos: List<Photo>)

/** Reads photos and videos from MediaStore. Requires READ_MEDIA_IMAGES (and READ_MEDIA_VIDEO for videos). */
class PhotoLibrary(private val context: Context) {

    /** Pictures, newest first; with [videos], videos too (when that permission is granted). */
    suspend fun load(limit: Int = 5000, videos: Boolean = false): List<Photo> = withContext(Dispatchers.IO) {
        val result = ArrayList<Photo>()
        fun read(uri: android.net.Uri, video: Boolean) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.DURATION,
            )
            try {
                context.contentResolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n < limit) {
                        val taken = c.getLong(1).takeIf { it > 0 } ?: (c.getLong(2) * 1000)
                        result += Photo(c.getLong(0), taken, c.getLong(3), c.getString(4) ?: "", isVideo = video, durationMs = if (video) c.getLong(5) else 0)
                        n++
                    }
                }
            } catch (_: Exception) {
            }
        }
        read(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        if (videos) read(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        result.sortedByDescending { it.dateTaken }.take(limit)
    }

    fun albums(photos: List<Photo>): List<PhotoAlbum> =
        photos.groupBy { it.bucketId }
            .map { (id, list) -> PhotoAlbum(id, list.first().bucket, list) }
            .sortedWith(compareByDescending<PhotoAlbum> { it.name.equals("Camera", true) }.thenBy { it.name.lowercase() })
}
