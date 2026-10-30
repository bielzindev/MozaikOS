package com.wpreborn.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Photo(val id: Long, val dateTaken: Long, val bucketId: Long, val bucket: String, val isVideo: Boolean) {
    val uri: Uri
        get() = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )
}

data class PhotoAlbum(val id: Long, val name: String, val photos: List<Photo>)

/** Reads photos (and camera videos) from MediaStore. Requires READ_MEDIA_IMAGES. */
class PhotoLibrary(private val context: Context) {

    suspend fun load(limit: Int = 5000): List<Photo> = withContext(Dispatchers.IO) {
        val result = ArrayList<Photo>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        )
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && result.size < limit) {
                    val taken = c.getLong(1).takeIf { it > 0 } ?: (c.getLong(2) * 1000)
                    result += Photo(c.getLong(0), taken, c.getLong(3), c.getString(4) ?: "", isVideo = false)
                }
            }
        } catch (_: SecurityException) {
        }
        result.sortedByDescending { it.dateTaken }
    }

    fun albums(photos: List<Photo>): List<PhotoAlbum> =
        photos.groupBy { it.bucketId }
            .map { (id, list) -> PhotoAlbum(id, list.first().bucket, list) }
            .sortedWith(compareByDescending<PhotoAlbum> { it.name.equals("Camera", true) }.thenBy { it.name.lowercase() })
}
