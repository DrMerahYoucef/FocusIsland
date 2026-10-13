package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Keeps both widgets current on every brand: an immediate refresh whenever exams or sessions
 * change, the launcher's own hourly update (updatePeriodMillis, honoured even where battery savers
 * block app alarms), a wake just after midnight for the day counts, and the system broadcasts
 * that change dates or language.
 */
object WidgetRefreshScheduler {

    private const val REQUEST_CODE = 9901
    const val ACTION_AUTO_UPDATE = "com.example.ACTION_WIDGET_AUTO_UPDATE"

    /** Broadcasts after which a widget redraws itself (all deliverable to manifest receivers on Android 8+). */
    val REFRESH_ACTIONS = setOf(
        ACTION_AUTO_UPDATE,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_LOCALE_CHANGED
    )

    private val providers = listOf(ExamCountdownWidgetReceiver::class.java, ExamMatrixWidgetReceiver::class.java)

    fun ids(context: Context, provider: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider)) }.getOrDefault(IntArray(0))

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ExamCountdownWidgetReceiver::class.java).setAction(ACTION_AUTO_UPDATE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** One inexact wake just after local midnight. Battery cost is negligible. */
    fun scheduleNextMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC, next.timeInMillis, pendingIntent(context)) }
    }

    /** Stops the midnight wake once neither widget is on the home screen. */
    fun cancelIfNoWidgets(context: Context) {
        if (providers.any { ids(context, it).isNotEmpty() }) return
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context)) }
    }

    /**
     * Redraws both widgets now. The update broadcast must carry the widget ids: without them
     * AppWidgetProvider ignores it (the previous version sent none, so changes didn't show).
     */
    fun refreshNow(context: Context) {
        providers.forEach { provider ->
            val ids = ids(context, provider)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, provider)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            }
        }
    }
}
