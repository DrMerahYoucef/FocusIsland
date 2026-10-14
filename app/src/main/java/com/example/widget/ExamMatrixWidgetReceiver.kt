package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.example.R
import com.example.ui.theme.AppStyle
import com.example.widget.theme.WidgetStyles
import com.example.data.db.AppDatabase
import com.example.data.db.entity.ExamEntity
import com.example.widget.theme.WidgetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ExamMatrixWidgetReceiver : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancelIfNoWidgets(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        WidgetRefreshScheduler.scheduleNextMidnight(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } catch (t: Throwable) {
                Log.e(TAG, "Widget render failed", t)
                runCatching {
                    val fallback = RemoteViews(context.packageName, WidgetStyles.matrixLayout(AppStyle.read(context)))
                    appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, fallback) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Resized: redraw the picture for the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in WidgetRefreshScheduler.REFRESH_ACTIONS) {
            onUpdate(context, AppWidgetManager.getInstance(context), WidgetRefreshScheduler.ids(context, javaClass))
        }
    }

    private suspend fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val upcomingExams = AppDatabase.getDatabase(context).examDao().getUpcomingExams(today.timeInMillis)
        val pendingIntent = WidgetText.openExams(context, 101)
        val nextExam = upcomingExams.firstOrNull()
        val a11yText = if (nextExam != null) {
            val daysLeft = WidgetText.daysUntil(nextExam.examDate)
            context.resources.getQuantityString(R.plurals.wgt_countdown_a11y, daysLeft, nextExam.name, daysLeft)
        } else context.getString(R.string.matrix_widget_label)

        for (widgetId in appWidgetIds) {
            val (widthPx, heightPx) = pictureSize(context, appWidgetManager.getAppWidgetOptions(widgetId))
            val views = RemoteViews(context.packageName, WidgetStyles.matrixLayout(AppStyle.read(context)))
            views.setImageViewBitmap(R.id.widget_matrix_image, generateMatrixBitmap(context, widthPx, heightPx, today, upcomingExams))
            views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
            views.setContentDescription(android.R.id.background, a11yText)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private const val TAG = "ExamMatrixWidget"

        /** Bitmaps sent to the launcher stay well under the size some phones refuse ("Can't load widget"). */
        private const val MAX_PIXELS = 700_000
        private const val MAX_SCALE = 2f

        /**
         * Picture size in px for the widget's current size in portrait (min width × max height, as
         * the launcher reports it), minus the card padding, at up to 2× density.
         */
        internal fun pictureSize(context: Context, options: Bundle): Pair<Int, Int> {
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 180
            val padDp = context.resources.getDimension(R.dimen.wgt_matrix_padding) / context.resources.displayMetrics.density * 2
            var scale = context.resources.displayMetrics.density.coerceAtMost(MAX_SCALE)
            val w = (wDp - padDp).coerceAtLeast(120f)
            val h = (hDp - padDp).coerceAtLeast(80f)
            if (w * h * scale * scale > MAX_PIXELS) scale = kotlin.math.sqrt(MAX_PIXELS / (w * h))
            return (w * scale).toInt() to (h * scale).toInt()
        }

        @JvmStatic
        fun triggerWidgetUpdate(context: Context) = WidgetRefreshScheduler.refreshNow(context)

        /** Twelve rows of day dots (one per month), today ringed, exams marked, the run-up tinted, and a footer. */
        internal fun generateMatrixBitmap(
            context: Context,
            widthPx: Int,
            heightPx: Int,
            todayCal: Calendar,
            upcomingExams: List<ExamEntity>
        ): Bitmap {
            val p = WidgetTheme.resolve(context)
            val w = widthPx.toFloat()
            val h = heightPx.toFloat()
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val footerH = (h * 0.16f).coerceIn(h * 0.12f, h * 0.22f)
            val gridBottom = h - footerH
            val rowH = gridBottom / 12f
            val labelSize = rowH * 0.6f
            val labelColW = labelSize * 2.9f
            val gridLeft = labelColW
            val dotStepX = (w - gridLeft) / 31f
            // Dots as big as both the row height and the column width allow.
            val unit = minOf(rowH, dotStepX) * 0.32f

            val monthLabels = java.text.DateFormatSymbols.getInstance().shortMonths.take(12).map { it.trimEnd('.').uppercase() }
            val currentYear = todayCal.get(Calendar.YEAR)
            val currentMonth = todayCal.get(Calendar.MONTH)
            val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)
            val yearProgress = todayDayOfYear / todayCal.getActualMaximum(Calendar.DAY_OF_YEAR).toFloat()

            val nextExam = upcomingExams.firstOrNull()
            val examDays = upcomingExams.mapNotNull { exam ->
                val cal = Calendar.getInstance().apply { timeInMillis = exam.examDate }
                if (cal.get(Calendar.YEAR) == currentYear) cal.get(Calendar.DAY_OF_YEAR) else null
            }.toSet()
            val nextExamDay = nextExam?.let { Calendar.getInstance().apply { timeInMillis = it.examDate } }
                ?.takeIf { it.get(Calendar.YEAR) == currentYear }?.get(Calendar.DAY_OF_YEAR)

            val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            val condensedBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            val monthActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent; textSize = labelSize; typeface = condensedBold }
            val monthDim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.onSurfaceVariant; textSize = labelSize * 0.95f; typeface = condensed }
            val dotPast = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.onSurfaceVariant; alpha = 110 }
            val dotFuture = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.track }
            val dotRunup = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accentSoft }
            val ring = { c: Int -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = unit * 0.35f } }
            val fill = { c: Int -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c } }
            val todayRing = ring(p.accent)
            val todayCore = fill(p.accent)
            val examRing = ring(p.critical)
            val examCore = fill(p.critical)

            val tempCal = Calendar.getInstance()
            for (m in 0..11) {
                val rowY = m * rowH + rowH / 2f
                canvas.drawText(monthLabels[m], 0f, rowY + labelSize * 0.36f, if (m == currentMonth) monthActive else monthDim)
                tempCal.set(currentYear, m, 1)
                val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (d in 1..daysInMonth) {
                    tempCal.set(currentYear, m, d)
                    val day = tempCal.get(Calendar.DAY_OF_YEAR)
                    val x = gridLeft + (d - 1) * dotStepX + dotStepX / 2f
                    when {
                        day in examDays -> { canvas.drawCircle(x, rowY, unit * 1.35f, examRing); canvas.drawCircle(x, rowY, unit * 0.7f, examCore) }
                        day == todayDayOfYear -> { canvas.drawCircle(x, rowY, unit * 1.3f, todayRing); canvas.drawCircle(x, rowY, unit * 0.55f, todayCore) }
                        nextExamDay != null && day > todayDayOfYear && day < nextExamDay -> canvas.drawCircle(x, rowY, unit * 0.85f, dotRunup)
                        day < todayDayOfYear -> canvas.drawCircle(x, rowY, unit * 0.75f, dotPast)
                        else -> canvas.drawCircle(x, rowY, unit * 0.75f, dotFuture)
                    }
                }
            }

            // Footer: year progress bar, next exam on the left, days left on the right.
            val trackH = (footerH * 0.08f).coerceAtLeast(2f)
            val trackY = gridBottom + footerH * 0.22f
            canvas.drawRoundRect(RectF(0f, trackY, w, trackY + trackH), trackH, trackH, fill(p.track))
            canvas.drawRoundRect(RectF(0f, trackY, w * yearProgress, trackY + trackH), trackH, trackH, fill(p.accent))

            val textSize = (footerH * 0.38f).coerceAtMost(rowH * 1.1f)
            val left = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.onSurface; this.textSize = textSize; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val right = Paint(left).apply { color = p.accent }
            val (leftText, rightText) = if (nextExam != null) {
                context.getString(R.string.wgt_matrix_next, nextExam.name) to
                    WidgetText.daysLabel(context, WidgetText.daysUntil(nextExam.examDate), short = false)
            } else {
                context.getString(R.string.wgt_matrix_year, (yearProgress * 100).toInt()) to context.getString(R.string.wgt_matrix_no_exams)
            }
            val baseline = gridBottom + footerH * 0.82f
            val rightW = right.measureText(rightText)
            canvas.drawText(rightText, w - rightW, baseline, right)
            // The exam name is cut with "…" if it would run into the day count.
            val room = w - rightW - textSize
            val shown = android.text.TextUtils.ellipsize(leftText, android.text.TextPaint(left), room, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, 0f, baseline, left)
            return bitmap
        }
    }
}
