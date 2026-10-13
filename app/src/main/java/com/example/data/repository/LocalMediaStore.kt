package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class LocalCardMediaType { IMAGE, AUDIO }

/**
 * Owns the folder that holds photo and voice card files. Backups restore into the same folder.
 * All functions do disk IO on Dispatchers.IO, so they are safe to call from the main thread.
 */
class LocalMediaStore(context: Context) {

    private val appContext = context.applicationContext

    val mediaDir: File
        get() = mediaDir(appContext)

    suspend fun savePhoto(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longestSide <= MAX_PHOTO_DIMENSION) bitmap else {
            val scale = MAX_PHOTO_DIMENSION.toFloat() / longestSide
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
        val file = File(mediaDir, "img_${UUID.randomUUID()}.jpg")
        file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        if (scaled !== bitmap) scaled.recycle()
        file.absolutePath
    }

    /** Moves a finished recording into the media folder, keeping its real extension. */
    suspend fun saveAudioFrom(tempRecordingFile: File): String = withContext(Dispatchers.IO) {
        require(tempRecordingFile.exists() && tempRecordingFile.length() > 0L) { "Recording is empty" }
        val extension = tempRecordingFile.extension.ifBlank { "m4a" }
        val file = File(mediaDir, "audio_${UUID.randomUUID()}.$extension")
        tempRecordingFile.copyTo(file, overwrite = true)
        tempRecordingFile.delete()
        file.absolutePath
    }

    /** Deletes a media file, but only if it lives inside this app's private storage. */
    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        deleteInternal(appContext, path)
    }

    suspend fun deleteAll(paths: Collection<String>) = withContext(Dispatchers.IO) {
        paths.forEach { deleteInternal(appContext, it) }
    }

    companion object {
        const val MEDIA_DIR_NAME = "revision_media"
        private const val MAX_PHOTO_DIMENSION = 1600
        private const val JPEG_QUALITY = 82

        fun mediaDir(context: Context): File =
            File(context.applicationContext.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }

        fun mimeTypeFor(path: String): String = when (File(path).extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "3gp" -> "audio/3gpp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }

        private fun deleteInternal(context: Context, path: String?) {
            if (path.isNullOrBlank()) return
            val file = File(path)
            val root = context.applicationContext.filesDir.canonicalPath
            if (file.canonicalPath.startsWith(root) && file.isFile) file.delete()
        }
    }
}
