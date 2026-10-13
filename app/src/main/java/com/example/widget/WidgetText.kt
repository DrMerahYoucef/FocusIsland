package com.example.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.R
import com.example.data.stats.StatsCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Shared by both widgets: day counts, labels and the tap target. */
internal object WidgetText {

    /** Whole calendar days from today to the exam day (DST-safe; was a raw 24 h division). */
    fun daysUntil(examDate: Long, today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(today, StatsCalculator.localDay(examDate)).toInt().coerceAtLeast(0)

    /** "Today", "Tomorrow", "in 3 days" (long) or "Today", "1d", "3d" (short). */
    fun daysLabel(context: Context, days: Int, short: Boolean): String = when {
        days <= 0 -> context.getString(R.string.wgt_today)
        short -> context.getString(R.string.wgt_days_short, days)
        days == 1 -> context.getString(R.string.wgt_tomorrow)
        else -> context.resources.getQuantityString(R.plurals.wgt_in_days, days, days)
    }

    /** Opens the app on Progress > Exams (handled by MainActivity's target-screen extra). */
    fun openExams(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TARGET_SCREEN, "exams")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
