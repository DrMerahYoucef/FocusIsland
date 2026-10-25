package com.example

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.auth.AuthRepository
import com.example.data.challenge.FriendChallengeRepository
import com.example.data.db.AppDatabase
import com.example.data.repository.BlockedAppRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.RevisionRepository
import com.example.data.repository.SessionRepository
import com.example.service.PomodoroTimerService
import com.example.service.RevisionReminderWorker
import com.example.service.RevisionSyncWorker
import com.example.widget.WidgetRefreshScheduler
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusFlowApplication : Application() {

    /** Lives as long as the process. For work that must outlive a screen (sync, widget refresh). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this) }
    val sessionRepository by lazy {
        SessionRepository(
            sessionDao = database.sessionDao(),
            appScope = applicationScope,
            onSessionsChanged = { WidgetRefreshScheduler.refreshNow(this) }
        )
    }
    val examRepository by lazy { ExamRepository(database.examDao()) }
    val blockedAppRepository by lazy { BlockedAppRepository(database.blockedAppDao()) }
    val revisionRepository by lazy { RevisionRepository(database) }
    val challengeStore by lazy { com.example.data.challenge.ChallengeStore(this) }
    val friendChallengeRepository by lazy {
        FriendChallengeRepository(
            context = this,
            sessionDao = database.sessionDao(),
            appScope = applicationScope
        )
    }
    val geminiModels by lazy { com.example.data.repository.GeminiModels(getSharedPreferences("focusflow_prefs", MODE_PRIVATE)) }

    /** Account + guest mode. After a real sign-in, local sessions and cards are pushed to the account. */
    val authRepository by lazy {
        AuthRepository(this, applicationScope) {
            sessionRepository.syncWithFirestore()
            RevisionSyncWorker.scheduleSyncWork(this)
        }
    }

    private var startedActivitiesCount = 0
    private var autoPauseJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Firebase is configured from google-services.json by the Google Services plugin.
        // FirebaseInitProvider normally initializes it already; this is a safety net only.
        try {
            if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
        }

        // Eagerly initialize friendChallengeRepository so active challenge listeners and auto-start work app-wide
        runCatching { friendChallengeRepository }

        registerAppSwitchAutoPause()

        try {
            RevisionReminderWorker.createNotificationChannel(this)
            RevisionSyncWorker.schedulePeriodicSyncWork(this)
            WidgetRefreshScheduler.scheduleNextMidnight(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background work", e)
        }
    }

    /**
     * Automatically pauses the Pomodoro timer when the user leaves Focus Island for another app
     * (while the device screen is interactive and unlocked), and automatically resumes it when returning.
     * Locking the phone keeps the Pomodoro running uninterrupted.
     */
    private fun registerAppSwitchAutoPause() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit

            override fun onActivityStarted(activity: Activity) {
                autoPauseJob?.cancel()
                autoPauseJob = null
                val wasBackground = startedActivitiesCount == 0
                startedActivitiesCount++
                if (wasBackground && PomodoroTimerService.state.value.isAutoPausedByAppSwitch) {
                    runCatching {
                        val intent = Intent(this@FocusFlowApplication, PomodoroTimerService::class.java)
                            .setAction(PomodoroTimerService.ACTION_AUTO_RESUME)
                        ContextCompat.startForegroundService(this@FocusFlowApplication, intent)
                    }.onFailure {
                        Log.w(TAG, "Failed to auto-resume Pomodoro timer", it)
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivitiesCount = (startedActivitiesCount - 1).coerceAtLeast(0)
                if (startedActivitiesCount == 0 && !activity.isChangingConfigurations) {
                    autoPauseJob?.cancel()
                    autoPauseJob = applicationScope.launch {
                        // Wait briefly so pressing the Power button to lock the screen has time to
                        // transition PowerManager.isInteractive to false or engage KeyguardManager.
                        kotlinx.coroutines.delay(400L)
                        if (startedActivitiesCount == 0 && shouldAutoPauseForAppSwitch(this@FocusFlowApplication)) {
                            if (PomodoroTimerService.state.value.isRunning) {
                                runCatching {
                                    val intent = Intent(this@FocusFlowApplication, PomodoroTimerService::class.java)
                                        .setAction(PomodoroTimerService.ACTION_AUTO_PAUSE)
                                    startService(intent)
                                }.onFailure {
                                    Log.w(TAG, "Failed to auto-pause Pomodoro timer", it)
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    internal fun shouldAutoPauseForAppSwitch(context: android.content.Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        val isScreenOn = pm?.isInteractive == true
        if (!isScreenOn) return false
        val km = context.getSystemService(android.app.KeyguardManager::class.java)
        val isLocked = km?.isKeyguardLocked == true || km?.isDeviceLocked == true
        return !isLocked
    }

    companion object {
        private const val TAG = "FocusFlowApplication"

        lateinit var instance: FocusFlowApplication
            private set
    }
}

