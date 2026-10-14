package com.example.ui.components

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.R
import com.example.ui.components.IslandRenderer.drawLagoonScene
import com.example.ui.theme.AppStyle
import com.example.ui.theme.FocusStyles
import com.example.ui.theme.WallpaperTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Draws your island as the phone wallpaper: day or night, in your style, one tree per completed session. */
object WallpaperHelper {

    private const val PREFS = "focusflow_prefs"

    /** Completed sessions, as last published by the app (the key name predates the island). */
    private const val KEY_SESSION_COUNT = "last_synced_tree_count"

    fun lastSessionCount(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SESSION_COUNT, 0)

    /** Draws the scene straight onto an Android [canvas] (live wallpaper, bitmaps). */
    fun drawScene(context: Context, canvas: android.graphics.Canvas, width: Int, height: Int, theme: WallpaperTheme, sessions: Int) {
        CanvasDrawScope().draw(Density(context), LayoutDirection.Ltr, Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
            // The island of the style chosen in the app (read each time, so the live wallpaper follows).
            drawLagoonScene(sessions, FocusStyles.of(AppStyle.read(context)).island(theme == WallpaperTheme.DARK))
        }
    }

    fun renderBitmap(context: Context, width: Int, height: Int, theme: WallpaperTheme, sessions: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        drawScene(context, android.graphics.Canvas(bitmap), bitmap.width, bitmap.height, theme, sessions)
        return bitmap
    }

    /**
     * Renders and sets the wallpaper off the main thread (a full-screen bitmap is heavy).
     * Returns null on success, or a message to show.
     */
    suspend fun setIslandWallpaper(
        context: Context,
        theme: WallpaperTheme,
        setHomeScreen: Boolean,
        setLockScreen: Boolean,
        sessions: Int
    ): String? = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        if (!setHomeScreen && !setLockScreen) return@withContext app.getString(R.string.wallpaper_pick_screen)
        try {
            val metrics = app.resources.displayMetrics
            val bitmap = renderBitmap(app, metrics.widthPixels, metrics.heightPixels, theme, sessions)
            val flags = (if (setHomeScreen) WallpaperManager.FLAG_SYSTEM else 0) or (if (setLockScreen) WallpaperManager.FLAG_LOCK else 0)
            WallpaperManager.getInstance(app).setBitmap(bitmap, null, true, flags)
            null
        } catch (e: Exception) {
            android.util.Log.e("WallpaperHelper", "Failed to set wallpaper", e)
            app.getString(R.string.wallpaper_failed)
        }
    }
}
