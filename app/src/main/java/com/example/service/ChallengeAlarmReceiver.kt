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
            ACTION_TIME_UP -> store.expireIfDue()?.let { showFinished(context, it) }
            ACTION_DONE -> store.finish(ChallengeResult.DONE)?.let { cancelAlarm(context); showFinished(context, it) }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val expired = store.expireIfDue()
                if (expired != null) showFinished(context, expired)
                else store.state.value.active?.let { schedule(context, it) }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "challenges"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_TIME_UP = "com.example.challenge.TIME_UP"
        private const val ACTION_DONE = "com.example.challenge.DONE"

        /** Shows the ongoing countdown and arms the deadline alarm. */
        fun schedule(context: Context, challenge: Challenge) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = broadcast(context, ACTION_TIME_UP, 1)
            // Exact when the system allows it; otherwise Android may deliver it a few minutes late.
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, challenge.endAt, pending)
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, challenge.endAt, pending)
                }
            } catch (e: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, challenge.endAt, pending)
            }
            notify(context, ongoing(context, challenge))
        }

        /** The user ended the challenge in the app: no alarm, no countdown left behind. */
        fun cancel(context: Context) {
            cancelAlarm(context)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun cancelAlarm(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(broadcast(context, ACTION_TIME_UP, 1))
        }

        private fun showFinished(context: Context, challenge: Challenge) {
            val done = challenge.result == ChallengeResult.DONE
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(if (done) R.drawable.ic_widget_star else R.drawable.ic_widget_flame)
                .setContentTitle(context.getString(if (done) R.string.challenge_notif_done_title else R.string.challenge_notif_time_up_title))
                .setContentText(challenge.goal)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            notify(context, notification)
        }

        private fun ongoing(context: Context, challenge: Challenge) = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_flame)
            .setContentTitle(context.getString(R.string.challenge_notif_running_title))
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
            .addAction(0, context.getString(R.string.challenge_goal_reached), broadcast(context, ACTION_DONE, 2))
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
