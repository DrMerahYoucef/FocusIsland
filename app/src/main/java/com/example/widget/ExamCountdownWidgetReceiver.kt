package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.ui.theme.AppStyle
import com.example.widget.theme.WidgetStyles
import com.example.data.db.AppDatabase
import com.example.data.db.entity.ExamEntity
import com.example.data.stats.FocusTotals
import com.example.data.stats.StatsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Next exam (big day count) with up to two more, and the focus stats. Colours come from the layout's
 * resources, so the launcher shows the right light or dark version itself, on any brand.
 */
class ExamCountdownWidgetReceiver : AppWidgetProvider() {

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
                renderAll(context, appWidgetManager, appWidgetIds)
            } catch (t: Throwable) {
                // Never leave "Can't load widget": show the empty layout instead.
                Log.e(TAG, "Widget render failed", t)
                runCatching {
                    val fallback = RemoteViews(context.packageName, WidgetStyles.countdownLayout(AppStyle.read(context)))
                    appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, fallback) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            // The midnight alarm comes here; it redraws both widgets.
            WidgetRefreshScheduler.ACTION_AUTO_UPDATE -> {
                WidgetRefreshScheduler.refreshNow(context)
                WidgetRefreshScheduler.scheduleNextMidnight(context)
            }
            in WidgetRefreshScheduler.REFRESH_ACTIONS ->
                onUpdate(context, AppWidgetManager.getInstance(context), WidgetRefreshScheduler.ids(context, javaClass))
        }
    }

    private suspend fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val database = AppDatabase.getDatabase(context)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val exams = database.examDao().getUpcomingExams(today.timeInMillis)
        // Same numbers as the Stats screen and the leaderboard.
        val totals = StatsCalculator.totals(database.sessionDao().getAllSessionsList())
        ids.forEach { id ->
            val minHeight = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            manager.updateAppWidget(id, buildViews(context, exams, totals, compact = minHeight < COMPACT_HEIGHT_DP))
        }
    }

    companion object {
        private const val TAG = "ExamCountdownWidget"
        private const val COMPACT_HEIGHT_DP = 150

        /** The widget's content; also used to draw the preview pictures. */
        internal fun buildViews(context: Context, exams: List<ExamEntity>, totals: FocusTotals, compact: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, WidgetStyles.countdownLayout(AppStyle.read(context)))
            val focus = if (totals.focusMinutes >= 600) context.getString(R.string.wgt_hours_short, totals.focusMinutes / 60)
            else context.getString(R.string.wgt_minutes_short, totals.focusMinutes)
            views.setTextViewText(R.id.widget_stat_focus_value, focus)
            views.setTextViewText(R.id.widget_stat_points_value, totals.points.toString())
            views.setTextViewText(R.id.widget_stat_sessions_value, totals.completedSessions.toString())
            views.setTextViewText(R.id.widget_stat_streak_value, context.getString(R.string.wgt_days_short, totals.currentStreakDays))
            // On a 2-row-high widget the 2 × 2 grid only fits without its title.
            views.setViewVisibility(R.id.widget_stats_header, if (compact) View.GONE else View.VISIBLE)

            val next = exams.firstOrNull()
            if (next == null) {
                views.setViewVisibility(R.id.widget_days_number, View.GONE)
                views.setViewVisibility(R.id.widget_days_unit, View.GONE)
                views.setViewVisibility(R.id.widget_exam1_name, View.GONE)
                views.setViewVisibility(R.id.widget_no_exams_text, View.VISIBLE)
            } else {
                val days = WidgetText.daysUntil(next.examDate)
                views.setViewVisibility(R.id.widget_no_exams_text, View.GONE)
                views.setViewVisibility(R.id.widget_exam1_name, View.VISIBLE)
                views.setTextViewText(R.id.widget_exam1_name, next.name)
                views.setViewVisibility(R.id.widget_days_unit, View.VISIBLE)
                if (days <= 0) {
                    views.setViewVisibility(R.id.widget_days_number, View.GONE)
                    views.setTextViewText(R.id.widget_days_unit, context.getString(R.string.wgt_today))
                } else {
                    views.setViewVisibility(R.id.widget_days_number, View.VISIBLE)
                    views.setTextViewText(R.id.widget_days_number, days.toString())
                    views.setTextViewText(R.id.widget_days_unit, context.resources.getQuantityString(R.plurals.wgt_days_unit, days))
                }
            }
            // Two more exams when there is room (one on a short widget).
            listOf(R.id.widget_exam2_container to (R.id.widget_exam2_name to R.id.widget_exam2_days),
                R.id.widget_exam3_container to (R.id.widget_exam3_name to R.id.widget_exam3_days)
            ).forEachIndexed { i, (container, ids) ->
                val exam = exams.getOrNull(i + 1)?.takeIf { !(compact && i == 1) }
                views.setViewVisibility(container, if (exam != null) View.VISIBLE else View.GONE)
                if (exam != null) {
                    views.setTextViewText(ids.first, exam.name)
                    views.setTextViewText(ids.second, WidgetText.daysLabel(context, WidgetText.daysUntil(exam.examDate), short = true))
                }
            }

            views.setOnClickPendingIntent(android.R.id.background, WidgetText.openExams(context, 100))
            views.setContentDescription(
                android.R.id.background,
                if (next != null) {
                    val days = WidgetText.daysUntil(next.examDate)
                    context.resources.getQuantityString(R.plurals.wgt_countdown_a11y, days, next.name, days)
                } else context.getString(R.string.wgt_no_exams)
            )
            return views
        }

        @JvmStatic
        fun triggerWidgetUpdate(context: Context) = WidgetRefreshScheduler.refreshNow(context)
    }
}
