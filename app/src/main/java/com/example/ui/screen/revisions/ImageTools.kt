package com.example.ui.screen.revisions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photo helpers for the capture → crop → card flow. Everything heavy runs off the main thread,
 * and photos are downsampled on decode: a 50 MP camera photo would otherwise need ~200 MB.
 */
object ImageTools {
    private const val TAG = "ImageTools"

    /** Longest side kept for cropping and OCR. Plenty for text recognition and Gemini. */
    const val MAX_SIDE_PX = 2560

    private const val CAPTURE_PREFIX = "capture_"
    private const val GALLERY_PREFIX = "gallery_"
    private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

    /** New file in the cache for a camera photo. */
    fun newCaptureFile(context: Context): File = File(context.cacheDir, "$CAPTURE_PREFIX${System.currentTimeMillis()}.jpg")

    /** Copies a picked image into the cache so the crop screen can reopen it after process death. */
    suspend fun copyUriToCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(context.cacheDir, "$GALLERY_PREFIX${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            out
        }.onFailure { Log.e(TAG, "Copy failed", it) }.getOrNull()
    }

    /** Deletes camera/gallery copies left behind by abandoned crops (older than a day). */
    suspend fun purgeStaleCaptures(context: Context) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
        context.cacheDir.listFiles()?.forEach { f ->
            if ((f.name.startsWith(CAPTURE_PREFIX) || f.name.startsWith(GALLERY_PREFIX)) && f.lastModified() < cutoff) f.delete()
        }
    }

    /** Best-effort delete of the source photo once a card has been made from it. */
    fun deleteQuietly(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** Decodes [path] downsampled to [maxSide] and upright according to its EXIF orientation. */
    suspend fun loadUpright(path: String, maxSide: Int = MAX_SIDE_PX): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val scaled = scaleDown(decoded, maxSide)
            val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            rotate(scaled, degrees)
        }.onFailure { Log.e(TAG, "Decode failed", it) }.getOrNull()
    }

    /** Rotates clockwise by [degrees]; returns the same bitmap for 0. */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return bitmap
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1), true)
    }

    /** Rectangle crop; coordinates are fractions (0..1) of the bitmap size. */
    fun cropRect(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val x = (left * w).toInt().coerceIn(0, w - 1)
        val y = (top * h).toInt().coerceIn(0, h - 1)
        val cw = ((right - left) * w).toInt().coerceIn(1, w - x)
        val ch = ((bottom - top) * h).toInt().coerceIn(1, h - y)
        return Bitmap.createBitmap(bitmap, x, y, cw, ch)
    }

    /** Free-shape crop: keeps what is inside the drawn outline, transparent elsewhere. */
    fun cropFreeShape(bitmap: Bitmap, points: List<Offset>): Bitmap {
        if (points.size < 3) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val path = Path().apply {
            moveTo(points[0].x * w, points[0].y * h)
            for (i in 1 until points.size) lineTo(points[i].x * w, points[i].y * h)
            close()
        }
        val masked = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(masked).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            drawPath(path, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            drawBitmap(bitmap, 0f, 0f, paint)
        }
        @Suppress("DEPRECATION") // the one-argument overload needs API 31; minSdk is 26
        val b = RectF().also { path.computeBounds(it, true) }
        val bx = b.left.toInt().coerceIn(0, w - 1)
        val by = b.top.toInt().coerceIn(0, h - 1)
        return Bitmap.createBitmap(masked, bx, by, b.width().toInt().coerceIn(1, w - bx), b.height().toInt().coerceIn(1, h - by))
    }

    /**
     * Page-corners crop with perspective correction: the four corners (top-left, top-right,
     * bottom-right, bottom-left) are mapped onto a straight rectangle, so a page photographed
     * at an angle comes out flat, which also helps text recognition.
     */
    fun cropCorners(bitmap: Bitmap, tl: Offset, tr: Offset, br: Offset, bl: Offset): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val p = listOf(tl, tr, br, bl).map { Offset(it.x * w, it.y * h) }
        val outW = max(dist(p[0], p[1]), dist(p[3], p[2])).roundToInt().coerceIn(1, MAX_SIDE_PX)
        val outH = max(dist(p[0], p[3]), dist(p[1], p[2])).roundToInt().coerceIn(1, MAX_SIDE_PX)
        val src = floatArrayOf(p[0].x, p[0].y, p[1].x, p[1].y, p[2].x, p[2].y, p[3].x, p[3].y)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
            // Degenerate shape (three corners in a line): fall back to its bounding box.
            val xs = listOf(tl.x, tr.x, br.x, bl.x)
            val ys = listOf(tl.y, tr.y, br.y, bl.y)
            return cropRect(bitmap, xs.min(), ys.min(), xs.max().coerceAtLeast(xs.min() + 0.01f), ys.max().coerceAtLeast(ys.min() + 0.01f))
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
}
