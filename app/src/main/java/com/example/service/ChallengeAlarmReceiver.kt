package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.FocusFlowApplication
import com.example.MainActivity
import com.example.R
import com.example.data.challenge.Challenge
import com.example.data.challenge.ChallengeResult

/**
 * Challenge notifications: an ongoing one with a live countdown and a "Goal reached" action,
 * then "Time's up" from an alarm when the deadline passes. Also re-arms the alarm after a reboot.
 */
class ChallengeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = FocusFlowApplication.instance.challengeStore
        when (intent.action) {
            ACTION_TIME_UP -> {
                store.expireIfDue()?.let {
                    PhoneLockOverlayManager.dismissShield(context)
                    PomodoroTimerService.syncPhoneLock(context)
                    showFinished(context, it)
                }
                scheduleDailyLocks(context)
            }
            ACTION_DONE -> store.finish(ChallengeResult.DONE)?.let {
                PhoneLockOverlayManager.dismissShield(context)
                cancelAlarm(context)
                showFinished(context, it)
            }
            ACTION_DAILY_LOCK_WARNING -> {
                PomodoroTimerService.syncDailyLockWarning(context)
                scheduleDailyLocks(context)
            }
            ACTION_DAILY_LOCK_START -> {
                activateDailyLockIfDue(context)
                scheduleDailyLocks(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val expired = store.expireIfDue()
                if (expired != null) {
                    PhoneLockOverlayManager.dismissShield(context)
                    showFinished(context, expired)
                } else {
                    store.state.value.active?.let {
                        schedule(context, it)
                        if (it.isLockPhone) PomodoroTimerService.syncPhoneLock(context)
                    }
                }
                scheduleDailyLocks(context)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "challenges"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_TIME_UP = "com.example.challenge.TIME_UP"
        private const val ACTION_DONE = "com.example.challenge.DONE"
        internal const val ACTION_DAILY_LOCK_WARNING = "com.example.challenge.DAILY_LOCK_WARNING"
        internal const val ACTION_DAILY_LOCK_START = "com.example.challenge.DAILY_LOCK_START"
        private const val REQ_TIME_UP = 1
        private const val REQ_DONE = 2
        private const val REQ_DAILY_WARNING = 11
        private const val REQ_DAILY_START = 12

        /** Shows the ongoing countdown and arms the deadline alarm. */
        fun schedule(context: Context, challenge: Challenge) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            setAlarmCompat(alarm, challenge.endAt, broadcast(context, ACTION_TIME_UP, REQ_TIME_UP))
            notify(context, ongoing(context, challenge))
        }

        /** The user ended the challenge in the app: no alarm, no countdown left behind. */
        fun cancel(context: Context) {
            cancelAlarm(context)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        /**
         * Arms the soonest 5-minute pre-lock warning alarm and auto-lock activation alarm across
         * all enabled [com.example.data.challenge.DailyLockSchedule] intervals.
         * If the current time is already inside an enabled interval or its 5-minute warning window,
         * triggers the lock or live warning countdown immediately.
         */
        fun scheduleDailyLocks(context: Context) {
            val appContext = context.applicationContext
            val store = runCatching { FocusFlowApplication.instance.challengeStore }.getOrNull() ?: return
            val alarm = appContext.getSystemService(AlarmManager::class.java)
            val nowMs = System.currentTimeMillis()
            val schedules = store.state.value.dailySchedules.filter { it.enabled && it.isValid }

            alarm?.cancel(broadcast(appContext, ACTION_DAILY_LOCK_WARNING, REQ_DAILY_WARNING))
            alarm?.cancel(broadcast(appContext, ACTION_DAILY_LOCK_START, REQ_DAILY_START))

            if (schedules.isEmpty()) {
                FloatingFocusBubbleManager.clearDailyLockWarning(appContext)
                PomodoroTimerService.syncDailyLockWarning(appContext)
                return
            }

            val occurrences = schedules.mapNotNull {
                com.example.data.challenge.DailyLockTime.nextOccurrence(it, nowMs)
            }
            if (occurrences.isEmpty()) {
                FloatingFocusBubbleManager.clearDailyLockWarning(appContext)
                PomodoroTimerService.syncDailyLockWarning(appContext)
                return
            }

            // 1. If we are currently inside an active interval and no strict lock is running, lock immediately.
            val activeNow = occurrences.filter { it.isActiveAt(nowMs) }.minByOrNull { it.endAtMs }
            if (activeNow != null) {
                activateDailyLockIfDue(appContext)
            }

            // 2. If we are inside a 5-minute warning window, start the live countdown in PomodoroTimerService.
            val warningNow = occurrences.filter { it.isWarningAt(nowMs) }.minByOrNull { it.effectiveStartMs }
            if (warningNow != null) {
                PomodoroTimerService.syncDailyLockWarning(appContext)
            } else {
                PomodoroTimerService.syncDailyLockWarning(appContext)
            }

            // 3. Arm the soonest future warning alarm and soonest future start alarm.
            if (alarm != null) {
                val nextWarningAt = occurrences
                    .map { it.warningAtMs }
                    .filter { it > nowMs }
                    .minOrNull()
                if (nextWarningAt != null) {
                    setAlarmCompat(alarm, nextWarningAt, broadcast(appContext, ACTION_DAILY_LOCK_WARNING, REQ_DAILY_WARNING))
                }

                val nextStartAt = occurrences
                    .map { it.effectiveStartMs }
                    .filter { it > nowMs }
                    .minOrNull()
                if (nextStartAt != null) {
                    setAlarmCompat(alarm, nextStartAt, broadcast(appContext, ACTION_DAILY_LOCK_START, REQ_DAILY_START))
                }
            }
        }

        /** Starts a strict Lock Phone challenge if any enabled daily schedule is currently due. */
        fun activateDailyLockIfDue(context: Context): Boolean {
            val appContext = context.applicationContext
            val store = runCatching { FocusFlowApplication.instance.challengeStore }.getOrNull() ?: return false
            val nowMs = System.currentTimeMillis()
            val activeChallenge = store.state.value.active
            if (activeChallenge != null && activeChallenge.isLockPhone && nowMs < activeChallenge.endAt) {
                return false
            }

            val due = store.state.value.dailySchedules
                .mapNotNull { com.example.data.challenge.DailyLockTime.nextOccurrence(it, nowMs) }
                .filter { it.isActiveAt(nowMs) }
                .minByOrNull { it.endAtMs }
                ?: return false

            store.clearDailyScheduleSnooze(due.schedule.id)
            val label = due.schedule.label.ifBlank { appContext.getString(R.string.lock_phone_daily_default_label) }
            val challenge = store.start(
                goal = label,
                endAt = due.endAtMs,
                type = com.example.data.challenge.PersonalChallengeType.LOCK_PHONE,
                allowedPackages = due.schedule.allowedPackages.ifEmpty { store.getDefaultAllowedPackages() }
            )
            FloatingFocusBubbleManager.clearDailyLockWarning(appContext)
            schedule(appContext, challenge)
            PhoneLockOverlayManager.reLockImmediately(appContext)
            PomodoroTimerService.syncPhoneLock(appContext)
            return true
        }

        private fun setAlarmCompat(alarm: AlarmManager, triggerAtMs: Long, pending: PendingIntent) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                }
            } catch (e: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            }
        }

        private fun cancelAlarm(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(broadcast(context, ACTION_TIME_UP, REQ_TIME_UP))
        }

        private fun showFinished(context: Context, challenge: Challenge) {
            val done = challenge.result == ChallengeResult.DONE
            val titleRes = when {
                challenge.isLockPhone && done -> R.string.lock_phone_completed_title
                done -> R.string.challenge_notif_done_title
                else -> R.string.challenge_notif_time_up_title
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(if (done) R.drawable.ic_widget_star else R.drawable.ic_widget_flame)
                .setContentTitle(context.getString(titleRes))
                .setContentText(challenge.goal)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            notify(context, notification)
        }

        private fun ongoing(context: Context, challenge: Challenge) = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_flame)
            .setContentTitle(
                context.getString(
                    if (challenge.isLockPhone) R.string.lock_phone_title else R.string.challenge_notif_running_title
                )
            )
            .setContentText(challenge.goal)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setWhen(challenge.endAt)
            .setTimeoutAfter((challenge.endAt - System.currentTimeMillis()).coerceAtLeast(1L))
            .apply {
                if (!challenge.isLockPhone) {
                    addAction(0, context.getString(R.string.challenge_goal_reached), broadcast(context, ACTION_DONE, 2))
                }
            }
            .build()

        private fun notify(context: Context, notification: android.app.Notification) {
            createChannel(context)
            // Without notification permission the in-app countdown still works.
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.challenge_channel_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.challenge_channel_description) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ChallengeAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TARGET_SCREEN, "challenges")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
