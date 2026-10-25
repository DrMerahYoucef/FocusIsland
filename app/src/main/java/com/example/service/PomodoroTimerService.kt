package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.FocusFlowApplication
import com.example.MainActivity
import com.example.R
import com.example.data.db.entity.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pomodoro engine. The single source of truth is [state] plus the phase end time, which is
 * measured on the monotonic clock and persisted, so the countdown never drifts and survives
 * process death. App-launch blocking runs inside this service (see [AppLaunchMonitor]).
 */
class PomodoroTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var launchMonitor: AppLaunchMonitor
    private var settings = TimerSettings()
    private var lastAmbientRotationElapsed = 0L
    private var lastAutoPausedElapsed = 0L
    private var exoPlayer: ExoPlayer? = null

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // If an app-switch auto-pause raced with pressing the Power button to lock the phone,
                // resume immediately so locking the phone never pauses the Pomodoro.
                if (_state.value.isAutoPausedByAppSwitch &&
                    SystemClock.elapsedRealtime() - lastAutoPausedElapsed < 3_000L
                ) {
                    autoResumeFromAppSwitch()
                }
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        serviceAlive = true
        createNotificationChannel()
        loadPersisted(this)
        settings = TimerSettings.load(this)
        runCatching {
            registerReceiver(screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        launchMonitor = AppLaunchMonitor(
            context = this,
            scope = scope,
            blockedPackages = FocusFlowApplication.instance.database.blockedAppDao().getAllBlocked()
                .map { apps -> apps.filter { it.blockLaunch }.mapTo(HashSet()) { it.packageName } }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = TimerSettings.load(this)
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> {
                _state.update { it.copy(isAutoPausedByAppSwitch = false) }
                start()
            }
            ACTION_PAUSE -> {
                _state.update { it.copy(isAutoPausedByAppSwitch = false) }
                pause()
            }
            ACTION_AUTO_PAUSE -> autoPauseForAppSwitch()
            ACTION_AUTO_RESUME -> autoResumeFromAppSwitch()
            ACTION_SKIP -> {
                _state.update { it.copy(isAutoPausedByAppSwitch = false) }
                skip()
            }
            ACTION_STOP -> {
                stopTimer()
                stopSelf()
            }
            ACTION_SET_AMBIENT -> setAmbientSound(intent.getStringExtra(EXTRA_AMBIENT_ID) ?: AMBIENT_NONE)
            else -> { // ACTION_RESTORE or a system restart after the process was killed
                if (_state.value.isRunning) start() else if (!_state.value.isUntouched) showPausedForeground() else stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceAlive = false
        runCatching { unregisterReceiver(screenOffReceiver) }
        tickerJob?.cancel()
        launchMonitor.stop()
        restoreDnd()
        releaseWakeLock()
        exoPlayer?.release()
        exoPlayer = null
        persist(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ timer engine

    private fun start() {
        val s = _state.value
        if (s.isRunning && tickerJob?.isActive == true) {
            startForegroundCompat()
            return
        }
        val remaining = if (s.isRunning) remainingNow() else s.remainingMs
        endsAtElapsed = SystemClock.elapsedRealtime() + remaining
        _state.update { it.copy(isRunning = true, remainingMs = remaining) }
        persist(this)

        if (!startForegroundCompat()) {
            // Android refused a foreground service (e.g. restarted in the background): keep progress, pause.
            pauseInternal()
            stopSelf()
            return
        }
        acquireWakeLock(remaining)
        if (s.phase == Phase.FOCUS) focusProtectionOn()
        syncAmbientPlayback()
        startTicker()
    }

    private fun pause() {
        if (!_state.value.isRunning) return
        pauseInternal()
        updateNotification()
    }

    private fun pauseInternal() {
        tickerJob?.cancel()
        _state.update { it.copy(isRunning = false, remainingMs = remainingNow()) }
        persist(this)
        releaseWakeLock()
        focusProtectionOff()
        syncAmbientPlayback()
    }

    private fun skip() {
        val s = _state.value
        tickerJob?.cancel()
        val remaining = if (s.isRunning) remainingNow() else s.remainingMs
        if (s.phase == Phase.FOCUS) {
            val doneSecs = ((s.phaseDurationMs - remaining) / 1000).toInt()
            if (doneSecs > MIN_PARTIAL_SECS) saveSession(doneSecs, completed = false)
            focusSecsBeforePhase += doneSecs
        }
        advancePhase(completedFocus = false)
    }

    private fun autoPauseForAppSwitch() {
        if (!_state.value.isRunning) return
        // Double-check screen & keyguard state so locking the phone never pauses the Pomodoro
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (pm?.isInteractive == false || km?.isKeyguardLocked == true || km?.isDeviceLocked == true) {
            return
        }
        lastAutoPausedElapsed = SystemClock.elapsedRealtime()
        pauseInternal()
        _state.update { it.copy(isAutoPausedByAppSwitch = true) }
        persist(this)
        updateNotification()
    }

    private fun autoResumeFromAppSwitch() {
        if (_state.value.isRunning || !_state.value.isAutoPausedByAppSwitch) return
        _state.update {
            it.copy(
                isAutoPausedByAppSwitch = false,
                lastAutoResumedAt = System.currentTimeMillis()
            )
        }
        start()
    }

    private fun stopTimer() {
        tickerJob?.cancel()
        releaseWakeLock()
        focusProtectionOff()
        focusSecsBeforePhase = 0L
        _state.update {
            settings.applyTo(it).copy(
                phase = Phase.FOCUS,
                isRunning = false,
                remainingMs = settings.focusMs,
                totalFocusSecs = 0L,
                isAutoPausedByAppSwitch = false
            )
        }
        persist(this)
        syncAmbientPlayback()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val remaining = remainingNow()
                _state.update { s ->
                    val focusSecs = if (s.phase == Phase.FOCUS) (s.phaseDurationMs - remaining) / 1000 else 0L
                    s.copy(remainingMs = remaining, totalFocusSecs = focusSecsBeforePhase + focusSecs)
                }
                maybeRotateAmbient()
                if (remaining <= 0L) {
                    onPhaseFinished()
                    return@launch
                }
                updateNotification()
                delay((remaining % 1000L).takeIf { it > 0L } ?: 1000L)
            }
        }
    }

    private fun onPhaseFinished() {
        val finished = _state.value.phase
        if (finished == Phase.FOCUS) {
            val secs = (_state.value.phaseDurationMs / 1000).toInt()
            saveSession(secs, completed = true)
            focusSecsBeforePhase += secs
        }
        vibrate()
        playAlertSound()
        advancePhase(completedFocus = finished == Phase.FOCUS)
    }

    /** Moves to the next phase and keeps running if the timer was running. */
    private fun advancePhase(completedFocus: Boolean) {
        val s = _state.value
        val wasRunning = s.isRunning
        if (s.phase == Phase.FOCUS) focusProtectionOff()

        val count = s.sessionCount + if (completedFocus) 1 else 0
        val next = when (s.phase) {
            Phase.FOCUS -> if (count > 0 && count % settings.sessionsBeforeLong == 0) Phase.LONG_BREAK else Phase.SHORT_BREAK
            Phase.SHORT_BREAK, Phase.LONG_BREAK -> Phase.FOCUS
        }
        _state.update {
            val withDurations = settings.applyTo(it)
            withDurations.copy(phase = next, sessionCount = count, isRunning = false, remainingMs = withDurations.durationOf(next))
        }
        if (wasRunning) {
            start()
        } else {
            persist(this)
            updateNotification()
        }
    }

    private fun remainingNow(): Long = (endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private fun saveSession(durationSecs: Int, completed: Boolean) {
        if (durationSecs <= 0) return
        val session = SessionEntity(
            date = System.currentTimeMillis(),
            durationSeconds = durationSecs,
            completed = completed,
            focusScore = durationSecs / 60
        )
        val app = FocusFlowApplication.instance
        app.applicationScope.launch { app.sessionRepository.insert(session) }
    }

    // ------------------------------------------------------------------ focus protection

    private fun focusProtectionOn() {
        if (settings.dndEnabled) enableDnd()
        launchMonitor.start()
    }

    private fun focusProtectionOff() {
        restoreDnd()
        launchMonitor.stop()
    }

    /** Uses "priority only" (alarms still ring) and remembers the user's own mode to restore it. */
    private fun enableDnd() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        val prefs = timerPrefs(this)
        if (!prefs.contains(KEY_DND_PREVIOUS)) prefs.edit { putInt(KEY_DND_PREVIOUS, nm.currentInterruptionFilter) }
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
            .onSuccess { _state.update { it.copy(isDndActive = true) } }
    }

    private fun restoreDnd() {
        val prefs = timerPrefs(this)
        if (prefs.contains(KEY_DND_PREVIOUS)) {
            val previous = prefs.getInt(KEY_DND_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL)
                .takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } ?: NotificationManager.INTERRUPTION_FILTER_ALL
            prefs.edit { remove(KEY_DND_PREVIOUS) }
            val nm = getSystemService(NotificationManager::class.java)
            // Only undo our own change; if the user switched DND mode meanwhile, leave theirs.
            if (nm != null && nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
            ) {
                runCatching { nm.setInterruptionFilter(previous) }
            }
        }
        _state.update { it.copy(isDndActive = false) }
    }

    // ------------------------------------------------------------------ foreground + notification

    private fun startForegroundCompat(): Boolean = try {
        val type = when {
            Build.VERSION.SDK_INT >= 34 ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not enter foreground", e)
        false
    }

    private fun showPausedForeground() {
        if (!startForegroundCompat()) stopSelf()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
    }

    internal fun buildNotification(): Notification {
        val s = _state.value
        val phaseTitle = getString(
            when (s.phase) {
                Phase.FOCUS -> R.string.timer_notif_focus
                Phase.SHORT_BREAK -> R.string.timer_notif_short_break
                Phase.LONG_BREAK -> R.string.timer_notif_long_break
            }
        )
        val clockText = formatClock(s.remainingMs)
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val totalSec = (s.phaseDurationMs / 1000L).toInt().coerceAtLeast(1)
        val elapsedSec = ((s.phaseDurationMs - s.remainingMs).coerceAtLeast(0L) / 1000L).toInt().coerceIn(0, totalSec)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_clock)
            .setContentTitle("$phaseTitle · $clockText")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(totalSec, elapsedSec, false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (s.isRunning) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + s.remainingMs)
                .setContentText(resources.getQuantityString(R.plurals.timer_notif_sessions, s.sessionCount, s.sessionCount))
                .addAction(0, getString(R.string.timer_action_pause), servicePendingIntent(ACTION_PAUSE, 1))
        } else {
            val pausedText = if (s.isAutoPausedByAppSwitch) {
                getString(R.string.timer_notif_auto_paused, clockText)
            } else {
                getString(R.string.timer_notif_paused, clockText)
            }
            builder.setShowWhen(false)
                .setContentText(pausedText)
                .addAction(0, getString(R.string.timer_action_resume), servicePendingIntent(ACTION_RESUME, 2))
        }
        return builder
            .addAction(0, getString(R.string.timer_action_skip), servicePendingIntent(ACTION_SKIP, 3))
            .addAction(0, getString(R.string.timer_action_end), servicePendingIntent(ACTION_STOP, 4))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, PomodoroTimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.timer_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.timer_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    // ------------------------------------------------------------------ alerts + wake lock

    private fun vibrate() {
        if (!settings.vibrate) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    }

    private fun playAlertSound() {
        runCatching {
            RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
    }

    /** Keeps the CPU awake while running so the phase ends on time with the screen off. */
    private fun acquireWakeLock(remainingMs: Long) {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusIsland:Timer")
            ?.apply {
                setReferenceCounted(false)
                acquire(remainingMs + 60_000L)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    // ------------------------------------------------------------------ ambient sound

    private fun maybeRotateAmbient() {
        val current = _state.value.currentAmbientId
        if (current == AMBIENT_NONE || settings.ambientRotationMin <= 0) return
        val now = SystemClock.elapsedRealtime()
        if (lastAmbientRotationElapsed == 0L) lastAmbientRotationElapsed = now
        if (now - lastAmbientRotationElapsed >= settings.ambientRotationMin * 60_000L) {
            lastAmbientRotationElapsed = now
            AMBIENT_IDS.filter { it != current }.randomOrNull()?.let { setAmbientSound(it) }
        }
    }

    private fun setAmbientSound(ambientId: String) {
        lastAmbientRotationElapsed = SystemClock.elapsedRealtime()
        val current = _state.value.currentAmbientId
        val player = exoPlayer
        getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit { putString(KEY_SELECTED_AMBIENT, ambientId) }

        when {
            ambientId == AMBIENT_NONE -> scope.launch {
                if (player != null && player.isPlaying) fade(player, from = 1f, to = 0f, durationMs = 1000L)
                player?.pause()
                _state.update { it.copy(currentAmbientId = AMBIENT_NONE) }
            }
            current != AMBIENT_NONE && current != ambientId && player != null && player.isPlaying -> scope.launch {
                fade(player, from = 1f, to = 0f, durationMs = 1500L)
                player.pause()
                _state.update { it.copy(currentAmbientId = ambientId) }
                syncAmbientPlayback()
                fade(player, from = 0f, to = 1f, durationMs = 1500L)
            }
            else -> {
                _state.update { it.copy(currentAmbientId = ambientId) }
                syncAmbientPlayback()
                exoPlayer?.volume = 1f
            }
        }
    }

    private suspend fun fade(player: ExoPlayer, from: Float, to: Float, durationMs: Long) {
        val steps = 20
        for (i in 0..steps) {
            player.volume = from + (to - from) * i / steps
            delay(durationMs / steps)
        }
    }

    private fun syncAmbientPlayback() {
        val id = _state.value.currentAmbientId
        val url = ambientUrl(id)
        if (id == AMBIENT_NONE || !_state.value.isRunning || url == null) {
            exoPlayer?.pause()
            return
        }
        val player = getOrCreatePlayer()
        if (player.currentMediaItem?.mediaId == id) {
            if (!player.isPlaying) player.play()
        } else {
            player.setMediaItem(ambientItem(id, url, fallback = false))
            player.prepare()
            player.play()
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer = exoPlayer ?: ExoPlayer.Builder(this)
        .setAudioAttributes(
            AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val id = _state.value.currentAmbientId
                    val fallback = ambientFallbackUrl(id) ?: return
                    if (currentMediaItem?.localConfiguration?.uri?.toString() == fallback) return // fallback failed too
                    Log.w(TAG, "Ambient stream failed, using fallback: ${error.errorCodeName}")
                    setMediaItem(ambientItem(id, fallback, fallback = true))
                    prepare()
                    play()
                }
            })
        }
        .also { exoPlayer = it }

    private fun ambientItem(id: String, url: String, fallback: Boolean) = MediaItem.Builder()
        .setMediaId(id)
        .setUri(url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(ambientName(id)).setArtist(getString(R.string.sound_ambient)).build())
        .build()

    // ponytail: hotlinked third-party streams; move to bundled/own-hosted audio in Phase 3a.
    private fun ambientUrl(id: String): String? = when (id) {
        "rain" -> "https://www.soundjay.com/nature/rain-01.mp3"
        "white_noise" -> "https://ice1.somafm.com/darkzone-128-mp3"
        "campfire" -> "https://ia800301.us.archive.org/5/items/CampfireSounds/campfire.mp3"
        "stream" -> "https://ia800204.us.archive.org/11/items/foreststream/forest_stream.mp3"
        "space" -> "https://ice1.somafm.com/deepspaceone-128-mp3"
        else -> null
    }

    private fun ambientFallbackUrl(id: String): String? = when (id) {
        "rain" -> "https://ice1.somafm.com/thistle-128-mp3"
        "white_noise" -> "https://ice1.somafm.com/dronezone-128-mp3"
        "campfire" -> "https://ice1.somafm.com/bootliquor-128-mp3"
        "stream" -> "https://ice1.somafm.com/suburbsofgoa-128-mp3"
        "space" -> "https://ice1.somafm.com/missioncontrol-128-mp3"
        else -> null
    }

    private fun ambientName(id: String): String = getString(
        when (id) {
            "rain" -> R.string.ambient_rain
            "white_noise" -> R.string.ambient_white_noise
            "campfire" -> R.string.ambient_campfire
            "stream" -> R.string.ambient_stream
            "space" -> R.string.ambient_space
            else -> R.string.sound_off
        }
    )

    // ------------------------------------------------------------------ settings + persistence

    /** User preferences read from the shared settings file (keys unchanged). */
    private data class TimerSettings(
        val focusMs: Long = 25 * 60_000L,
        val shortMs: Long = 5 * 60_000L,
        val longMs: Long = 15 * 60_000L,
        val sessionsBeforeLong: Int = 4,
        val dndEnabled: Boolean = false,
        val vibrate: Boolean = true,
        val ambientRotationMin: Int = 5
    ) {
        fun applyTo(s: TimerState) = s.copy(
            focusDurationMs = focusMs,
            shortBreakDurationMs = shortMs,
            longBreakDurationMs = longMs,
            sessionsBeforeLongBreak = sessionsBeforeLong
        )

        companion object {
            fun load(context: Context): TimerSettings {
                val p = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                return TimerSettings(
                    focusMs = p.getInt("focus_duration_min", 25).coerceAtLeast(1) * 60_000L,
                    shortMs = p.getInt("short_break_min", 5).coerceAtLeast(1) * 60_000L,
                    longMs = p.getInt("long_break_min", 15).coerceAtLeast(1) * 60_000L,
                    sessionsBeforeLong = p.getInt("sessions_before_long", 4).coerceAtLeast(1),
                    dndEnabled = p.getBoolean("block_notifications", false),
                    vibrate = p.getBoolean("vibrate_on_complete", true),
                    ambientRotationMin = p.getInt("ambient_rotation_min", 5)
                )
            }
        }
    }

    companion object {
        private const val TAG = "PomodoroTimerService"

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_AUTO_PAUSE = "ACTION_AUTO_PAUSE"
        const val ACTION_AUTO_RESUME = "ACTION_AUTO_RESUME"
        const val ACTION_SKIP = "ACTION_SKIP"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SET_AMBIENT = "ACTION_SET_AMBIENT"
        const val ACTION_RESTORE = "ACTION_RESTORE"
        const val EXTRA_AMBIENT_ID = "EXTRA_AMBIENT_ID"

        const val AMBIENT_NONE = "none"
        val AMBIENT_IDS = listOf("rain", "white_noise", "campfire", "stream", "space")

        private const val NOTIF_ID = 1010
        private const val CHANNEL_ID = "focusflow_timer_channel"
        private const val MIN_PARTIAL_SECS = 30

        private const val SETTINGS_PREFS = "focusflow_prefs"
        private const val KEY_SELECTED_AMBIENT = "selected_ambient_id"
        private const val STATE_PREFS = "focusflow_timer_state"
        private const val KEY_DND_PREVIOUS = "dnd_previous_filter"

        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state.asStateFlow()

        val isRunning: Boolean get() = _state.value.isRunning

        @Volatile private var endsAtElapsed = 0L
        @Volatile private var focusSecsBeforePhase = 0L
        @Volatile private var restored = false
        @Volatile private var serviceAlive = false

        private fun timerPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

        /**
         * Loads the persisted timer once per process. Call from the UI at startup: if a session
         * was running when the process died, the service is restarted and the countdown resumes.
         */
        fun restoreIfNeeded(context: Context) {
            loadPersisted(context)
            if (_state.value.isRunning && !serviceAlive) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, PomodoroTimerService::class.java).setAction(ACTION_RESTORE)
                    )
                }.onFailure { Log.w(TAG, "Could not restore running timer", it) }
            }
        }

        /** When the timer is idle and untouched, picks up new durations from Settings. */
        fun applyIdleSettings(context: Context) {
            loadPersisted(context)
            if (!_state.value.isUntouched) return
            val s = TimerSettings.load(context)
            _state.update { s.applyTo(it).let { u -> u.copy(remainingMs = u.durationOf(u.phase)) } }
        }

        @Synchronized
        private fun loadPersisted(context: Context) {
            if (restored) return
            restored = true
            val settings = TimerSettings.load(context)
            val ambient = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SELECTED_AMBIENT, AMBIENT_NONE) ?: AMBIENT_NONE
            val p = timerPrefs(context)
            if (!p.contains("phase")) {
                _state.value = settings.applyTo(TimerState(remainingMs = settings.focusMs, currentAmbientId = ambient))
                return
            }
            val phase = runCatching { Phase.valueOf(p.getString("phase", null)!!) }.getOrDefault(Phase.FOCUS)
            val base = TimerState(
                phase = phase,
                sessionCount = p.getInt("session_count", 0),
                focusDurationMs = p.getLong("focus_ms", settings.focusMs),
                shortBreakDurationMs = p.getLong("short_ms", settings.shortMs),
                longBreakDurationMs = p.getLong("long_ms", settings.longMs),
                sessionsBeforeLongBreak = p.getInt("sessions_before_long", settings.sessionsBeforeLong),
                currentAmbientId = ambient
            )
            focusSecsBeforePhase = p.getLong("focus_secs_before_phase", 0L)
            val savedRemaining = p.getLong("remaining_ms", base.phaseDurationMs).coerceIn(0L, base.phaseDurationMs)
            val wasRunning = p.getBoolean("running", false)
            val savedEndsAt = p.getLong("ends_at_elapsed", 0L)
            val liveRemaining = savedEndsAt - SystemClock.elapsedRealtime()
            // After a reboot the monotonic clock restarts, so the saved end time is meaningless: resume paused.
            val rebooted = liveRemaining > base.phaseDurationMs
            val autoPaused = p.getBoolean("auto_paused", false)
            _state.value = if (wasRunning && !rebooted) {
                endsAtElapsed = savedEndsAt
                base.copy(isRunning = true, remainingMs = liveRemaining.coerceAtLeast(0L), isAutoPausedByAppSwitch = false)
            } else {
                base.copy(isRunning = false, remainingMs = savedRemaining, isAutoPausedByAppSwitch = autoPaused)
            }
        }

        private fun persist(context: Context) {
            val s = _state.value
            timerPrefs(context).edit {
                putString("phase", s.phase.name)
                putInt("session_count", s.sessionCount)
                putBoolean("running", s.isRunning)
                putBoolean("auto_paused", s.isAutoPausedByAppSwitch)
                putLong("remaining_ms", s.remainingMs)
                putLong("ends_at_elapsed", endsAtElapsed)
                putLong("focus_ms", s.focusDurationMs)
                putLong("short_ms", s.shortBreakDurationMs)
                putLong("long_ms", s.longBreakDurationMs)
                putInt("sessions_before_long", s.sessionsBeforeLongBreak)
                putLong("focus_secs_before_phase", focusSecsBeforePhase)
            }
        }

        private fun formatClock(ms: Long): String {
            val total = (ms + 999) / 1000
            return "%02d:%02d".format(total / 60, total % 60)
        }
    }
}
