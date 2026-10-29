package com.example.ui.screen.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.data.db.entity.SessionEntity
import com.example.data.stats.FocusTotals
import com.example.data.stats.StatsCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStyle
import com.example.ui.theme.WallpaperTheme
import com.example.R
import com.example.data.db.DatabaseSeeder
import com.example.data.repository.LocalMediaStore
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsState(
    val focusMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    val sessionsBeforeLong: Int = 4,
    val blockNotifications: Boolean = false,
    val vibrateOnComplete: Boolean = true,
    val themeMode: String = "system",
    val followSystemTheme: Boolean = true,
    val wallpaperTheme: WallpaperTheme = WallpaperTheme.LIGHT,
    val autoSyncWallpaper: Boolean = false,
    val wallpaperHomeScreen: Boolean = true,
    val wallpaperLockScreen: Boolean = false,
    val ambientRotationMin: Int = 5,
    val swipeToNavigate: Boolean = true,
    val dynamicColor: Boolean = false,
    val autoFocusMode: Boolean = true,
    val shareListening: Boolean = false,
    val appStyle: AppStyle = AppStyle.LAGOON,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    /** The "choose your style" tip on the Focus tab was opened or dismissed. */
    val styleTipSeen: Boolean = true
) {
    companion object {
        val FOCUS_RANGE = 1..180
        val BREAK_RANGE = 1..60
        val ROUNDS_RANGE = 1..12
        /** 0 = never switch to another ambient sound. */
        val ROTATION_RANGE = 0..60
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("focusflow_prefs", Context.MODE_PRIVATE)
    private val sessionRepo = FocusFlowApplication.instance.sessionRepository
    private val database = FocusFlowApplication.instance.database

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val focusTotals: StateFlow<FocusTotals> = sessionRepo.getAllSessions()
        .map { sessions -> StatsCalculator.totals(sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusTotals(0, 0, 0, 0, 0, 0))

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val savedThemeStr = sharedPrefs.getString("wallpaper_theme", "LIGHT") ?: "LIGHT"
        val savedTheme = try {
            WallpaperTheme.valueOf(savedThemeStr)
        } catch (e: Exception) {
            WallpaperTheme.LIGHT
        }

        _state.update {
            SettingsState(
                focusMin = sharedPrefs.getInt("focus_duration_min", 25),
                shortBreakMin = sharedPrefs.getInt("short_break_min", 5),
                longBreakMin = sharedPrefs.getInt("long_break_min", 15),
                sessionsBeforeLong = sharedPrefs.getInt("sessions_before_long", 4),
                blockNotifications = sharedPrefs.getBoolean("block_notifications", false),
                vibrateOnComplete = sharedPrefs.getBoolean("vibrate_on_complete", true),
                themeMode = sharedPrefs.getString("theme_mode", "system") ?: "system",
                followSystemTheme = sharedPrefs.getBoolean("follow_system_theme", true),
                wallpaperTheme = savedTheme,
                autoSyncWallpaper = sharedPrefs.getBoolean("auto_sync_wallpaper", false),
                wallpaperHomeScreen = sharedPrefs.getBoolean("wallpaper_home_screen", true),
                wallpaperLockScreen = sharedPrefs.getBoolean("wallpaper_lock_screen", false),
                ambientRotationMin = sharedPrefs.getInt("ambient_rotation_min", 5),
                swipeToNavigate = sharedPrefs.getBoolean("swipe_to_navigate", true),
                dynamicColor = sharedPrefs.getBoolean("dynamic_color", false),
                autoFocusMode = sharedPrefs.getBoolean("auto_focus_mode", true),
                shareListening = sharedPrefs.getBoolean(KEY_SHARE_LISTENING, false),
                appStyle = AppStyle.from(sharedPrefs.getString(AppStyle.PREF_KEY, null)),
                appLanguage = AppLanguage.from(sharedPrefs.getString(AppLanguage.PREF_KEY, null)),
                styleTipSeen = sharedPrefs.getBoolean(AppStyle.PREF_TIP_SEEN, false)
            )
        }
    }

    fun updateAmbientRotationMin(raw: Int) {
        val value = raw.coerceIn(SettingsState.ROTATION_RANGE)
        sharedPrefs.edit().putInt("ambient_rotation_min", value).apply()
        _state.update { it.copy(ambientRotationMin = value) }
    }

    fun updateThemeMode(value: String) {
        sharedPrefs.edit().putString("theme_mode", value).apply()
        _state.update { it.copy(themeMode = value) }
        // Let's also trigger an app widget update so the widget theme matches the app selection immediately
        com.example.widget.ExamCountdownWidgetReceiver.triggerWidgetUpdate(getApplication())
    }

    fun updateAppStyle(value: AppStyle) {
        sharedPrefs.edit().putString(AppStyle.PREF_KEY, value.key).putBoolean(AppStyle.PREF_TIP_SEEN, true).apply()
        _state.update { it.copy(appStyle = value, styleTipSeen = true) }
        // Widgets and the wallpaper read the style from the same preferences.
        com.example.widget.ExamCountdownWidgetReceiver.triggerWidgetUpdate(getApplication())
    }

    fun updateAppLanguage(value: AppLanguage) {
        sharedPrefs.edit().putString(AppLanguage.PREF_KEY, value.key).apply()
        _state.update { it.copy(appLanguage = value) }
        com.example.widget.ExamCountdownWidgetReceiver.triggerWidgetUpdate(getApplication())
    }

    fun dismissStyleTip() {
        sharedPrefs.edit().putBoolean(AppStyle.PREF_TIP_SEEN, true).apply()
        _state.update { it.copy(styleTipSeen = true) }
    }

    fun updateFollowSystemTheme(value: Boolean) {
        sharedPrefs.edit().putBoolean("follow_system_theme", value).apply()
        _state.update { it.copy(followSystemTheme = value) }
    }

    fun updateWallpaperTheme(value: WallpaperTheme) {
        sharedPrefs.edit().putString("wallpaper_theme", value.name).apply()
        _state.update { it.copy(wallpaperTheme = value) }
    }

    fun updateFocusMin(raw: Int) {
        val value = raw.coerceIn(SettingsState.FOCUS_RANGE)
        sharedPrefs.edit().putInt("focus_duration_min", value).apply()
        _state.update { it.copy(focusMin = value) }
    }

    fun updateShortBreakMin(raw: Int) {
        val value = raw.coerceIn(SettingsState.BREAK_RANGE)
        sharedPrefs.edit().putInt("short_break_min", value).apply()
        _state.update { it.copy(shortBreakMin = value) }
    }

    fun updateLongBreakMin(raw: Int) {
        val value = raw.coerceIn(SettingsState.BREAK_RANGE)
        sharedPrefs.edit().putInt("long_break_min", value).apply()
        _state.update { it.copy(longBreakMin = value) }
    }

    fun updateSessionsBeforeLong(raw: Int) {
        val value = raw.coerceIn(SettingsState.ROUNDS_RANGE)
        sharedPrefs.edit().putInt("sessions_before_long", value).apply()
        _state.update { it.copy(sessionsBeforeLong = value) }
    }

    fun updateBlockNotifications(value: Boolean) {
        sharedPrefs.edit().putBoolean("block_notifications", value).apply()
        _state.update { it.copy(blockNotifications = value) }
    }

    fun updateVibrateOnComplete(value: Boolean) {
        sharedPrefs.edit().putBoolean("vibrate_on_complete", value).apply()
        _state.update { it.copy(vibrateOnComplete = value) }
    }

    fun updateAutoSyncWallpaper(value: Boolean) {
        sharedPrefs.edit().putBoolean("auto_sync_wallpaper", value).apply()
        _state.update { it.copy(autoSyncWallpaper = value) }
    }

    fun updateWallpaperHomeScreen(value: Boolean) {
        sharedPrefs.edit().putBoolean("wallpaper_home_screen", value).apply()
        _state.update { it.copy(wallpaperHomeScreen = value) }
    }

    fun updateWallpaperLockScreen(value: Boolean) {
        sharedPrefs.edit().putBoolean("wallpaper_lock_screen", value).apply()
        _state.update { it.copy(wallpaperLockScreen = value) }
    }

    /** Opens Focus mode after 20 s without touching the screen during a running session. */
    fun updateAutoFocusMode(value: Boolean) {
        sharedPrefs.edit().putBoolean("auto_focus_mode", value).apply()
        _state.update { it.copy(autoFocusMode = value) }
    }

    fun updateDynamicColor(value: Boolean) {
        sharedPrefs.edit().putBoolean("dynamic_color", value).apply()
        _state.update { it.copy(dynamicColor = value) }
    }

    /**
     * Opt-in: friends see the station you play (read by RadioPlayerService and Community, same key).
     * Turning it off also clears what friends currently see.
     */
    fun updateShareListening(value: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_SHARE_LISTENING, value).apply()
        _state.update { it.copy(shareListening = value) }
        if (!value) {
            val uid = Firebase.auth.currentUser?.uid ?: return
            viewModelScope.launch {
                runCatching { Firebase.firestore.collection("users").document(uid).update("currentRadio", "").await() }
            }
        }
    }

    fun updateSwipeToNavigate(value: Boolean) {
        sharedPrefs.edit().putBoolean("swipe_to_navigate", value).apply()
        _state.update { it.copy(swipeToNavigate = value) }
    }

    /**
     * Clears what the confirmation dialog promises: focus sessions (and therefore stats and
     * streaks) and exam countdowns. Flashcards, radio stations and blocked apps are kept.
     * Online sessions are cleared first; otherwise the next sync would bring them back.
     */
    fun resetAllData(onCompleted: (success: Boolean, error: String?) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val user = Firebase.auth.currentUser
            if (user != null && !user.isAnonymous) {
                val remote = runCatching {
                    val db = Firebase.firestore
                    deleteCollection(db.collection("users").document(user.uid).collection("sessions"))
                    val zero = mapOf(
                        "treeCount" to 0,
                        "totalMinutes" to 0,
                        "points" to 0,
                        "currentStreak" to 0
                    )
                    db.collection("users").document(user.uid).set(zero, SetOptions.merge()).await()
                    db.collection("leaderboard").document(user.uid).set(zero, SetOptions.merge()).await()
                }
                if (remote.isFailure) {
                    android.util.Log.e(TAG, "Remote reset failed", remote.exceptionOrNull())
                    onCompleted(false, app.getString(R.string.reset_error_sync))
                    return@launch
                }
            }
            sessionRepo.deleteAllLocal()
            database.examDao().deleteAll()
            com.example.widget.ExamCountdownWidgetReceiver.triggerWidgetUpdate(app)
            onCompleted(true, null)
        }
    }

    /**
     * Deletes the account and everything stored for it online (sessions, synced cards, profile,
     * leaderboard entry, friend links and requests), then wipes this device.
     * Firebase only allows deletion shortly after sign-in, so that is checked BEFORE deleting data.
     */
    fun deleteAccount(onCompleted: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val user = Firebase.auth.currentUser
                if (user != null && user.isAnonymous) {
                    // Guests have nothing online: just drop the anonymous user (best effort).
                    runCatching { user.delete().await() }
                } else if (user != null) {
                    val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
                    if (System.currentTimeMillis() - lastSignIn > RECENT_LOGIN_WINDOW_MS) {
                        onCompleted(false, app.getString(R.string.delete_account_reauth))
                        return@launch
                    }
                    val uid = user.uid
                    val db = Firebase.firestore
                    val userDoc = db.collection("users").document(uid)

                    // Best effort for data shared with other users (depends on security rules).
                    runCatching {
                        val friends = (userDoc.get().await().get("friends") as? List<*>).orEmpty().mapNotNull { it?.toString() }
                        friends.forEach { friendUid ->
                            db.collection("users").document(friendUid).update("friends", FieldValue.arrayRemove(uid)).await()
                        }
                    }
                    runCatching {
                        for (field in listOf("fromUid", "toUid")) {
                            db.collection("friend_requests").whereEqualTo(field, uid).get().await()
                                .documents.forEach { it.reference.delete().await() }
                        }
                    }

                    deleteCollection(userDoc.collection("sessions"))
                    deleteCollection(userDoc.collection("revision_notes"))
                    db.collection("leaderboard").document(uid).delete().await()
                    userDoc.delete().await()
                    user.delete().await()
                }

                // Wipe this device, then restore the app's default content.
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                    DatabaseSeeder.seedAll(database.openHelper.writableDatabase)
                    LocalMediaStore.mediaDir(app).deleteRecursively()
                    java.io.File(app.filesDir, "revisions_media").deleteRecursively()
                }
                sharedPrefs.edit().clear().apply()
                FocusFlowApplication.instance.authRepository.signOut() // also leaves guest mode
                com.example.widget.ExamCountdownWidgetReceiver.triggerWidgetUpdate(app)
                onCompleted(true, null)
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                onCompleted(false, app.getString(R.string.delete_account_reauth))
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to delete account", e)
                onCompleted(false, e.localizedMessage ?: app.getString(R.string.delete_account_failed))
            }
        }
    }

    /** Deletes every document of a (sub)collection in batches (Firestore has no recursive delete on clients). */
    private suspend fun deleteCollection(ref: CollectionReference) {
        while (true) {
            val page = ref.limit(400).get().await()
            if (page.isEmpty) return
            val batch = ref.firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val RECENT_LOGIN_WINDOW_MS = 5 * 60_000L
        const val KEY_SHARE_LISTENING = "share_listening"
    }
}
