package com.example

import android.app.Application
import android.util.Log
import com.example.data.auth.AuthRepository
import com.example.data.db.AppDatabase
import com.example.data.repository.BlockedAppRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.RevisionRepository
import com.example.data.repository.SessionRepository
import com.example.service.RevisionReminderWorker
import com.example.service.RevisionSyncWorker
import com.example.widget.WidgetRefreshScheduler
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val geminiModels by lazy { com.example.data.repository.GeminiModels(getSharedPreferences("focusflow_prefs", MODE_PRIVATE)) }

    /** Account + guest mode. After a real sign-in, local sessions and cards are pushed to the account. */
    val authRepository by lazy {
        AuthRepository(this, applicationScope) {
            sessionRepository.syncWithFirestore()
            RevisionSyncWorker.scheduleSyncWork(this)
        }
    }

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

        try {
            RevisionReminderWorker.createNotificationChannel(this)
            RevisionSyncWorker.schedulePeriodicSyncWork(this)
            WidgetRefreshScheduler.scheduleNextMidnight(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background work", e)
        }
    }

    companion object {
        private const val TAG = "FocusFlowApplication"

        lateinit var instance: FocusFlowApplication
            private set
    }
}
