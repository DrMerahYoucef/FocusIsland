package com.example.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.FocusFlowApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manages the `dynamicSpot`-style **Dynamic Island** positioned cleanly just below the system
 * status bar and camera cutout when Focus Island is in the background:
 * 1. Live Pomodoro Focus / Short Break / Long Break countdowns (running or paused).
 * 2. Incoming app notifications (Study Group focus nudges, Challenge invitations, and Study Group chat alerts).
 *
 * Supports two synchronized states with fluid spring open/close morph animations:
 * - **Collapsed Pill (`COMPACT`)**: Pure-black pill (`#000000`) positioned safely below the status bar
 *   and camera cutout, featuring the circular Focus Island palm-tree emblem on the left and live tabular timer on the right.
 * - **Expanded Card (`EXPANDED`)**: Full-width rounded pure-black card (`#000000`, `38dp` radius)
 *   with lagoon-teal header (`Focus Island • Dans 48 min`), title (`En focus · 48:31`), subtitle (`48:30`),
 *   and 3 dark-charcoal pill buttons (`Pause` / `Reprendre`, `Passer`, `Terminer`).
 *
 * Tapping the compact pill expands the card; tapping anywhere outside the expanded card (or swiping up)
 * smoothly collapses it back to the compact pill.
 */
object FloatingFocusBubbleManager {

    private const val TAG = "DynamicIslandManager"
    private const val PREFS_NAME = "dynamic_island_prefs"
    private const val KEY_TOP_OFFSET_X = "top_offset_x_px"
    private const val KEY_TOP_OFFSET_Y = "top_offset_y_px"
    private val mainHandler = Handler(Looper.getMainLooper())

    enum class NotificationKind {
        NONE,
        NUDGE,
        INVITE,
        MESSAGE,
        LOCK_WARNING
    }

    data class DynamicIslandUiState(
        val isTimerActive: Boolean = false,
        val phase: Phase = Phase.FOCUS,
        val isRunning: Boolean = false,
        val isAutoPaused: Boolean = false,
        val remainingMs: Long = 0L,
        val clockText: String = "25:00",
        val remainingMinutes: Int = 25,
        val isExpanded: Boolean = false,
        val notificationKind: NotificationKind = NotificationKind.NONE,
        val notificationSender: String = "",
        val notificationTitle: String = "",
        val notificationSubtitle: String = "",
        val lockWarningScheduleId: String = "",
        val lockWarningRangeText: String = "",
        val lockWarningRemainingMs: Long = 0L
    ) {
        val isVisible: Boolean
            get() = isTimerActive || notificationKind != NotificationKind.NONE

        val isLockWarningUnder30Sec: Boolean
            get() = notificationKind == NotificationKind.LOCK_WARNING &&
                lockWarningRemainingMs in 1L..29_999L
    }

    private val _islandState = MutableStateFlow(DynamicIslandUiState())
    val islandState: StateFlow<DynamicIslandUiState> = _islandState.asStateFlow()

    // Callbacks for active notification actions
    @Volatile private var nudgeReturnCallback: (() -> Unit)? = null
    @Volatile private var inviteAcceptCallback: (() -> Unit)? = null
    @Volatile private var inviteDenyCallback: (() -> Unit)? = null
    @Volatile private var inviteHideCallback: (() -> Unit)? = null
    @Volatile private var lockWarningAddOneMinCallback: (() -> Unit)? = null
    @Volatile private var autoExpandedUnder30ForLockAtMs: Long = 0L

    // System WindowManager overlay views & animators
    @Volatile private var rootWrapperView: FrameLayout? = null
    @Volatile private var islandCardView: FrameLayout? = null
    @Volatile private var islandBackgroundDrawable: GradientDrawable? = null
    @Volatile private var compactContentView: View? = null
    @Volatile private var compactClockView: TextView? = null
    @Volatile private var expandedContentView: View? = null
    @Volatile private var expandedHeaderView: TextView? = null
    @Volatile private var expandedTitleView: TextView? = null
    @Volatile private var expandedSubtitleView: TextView? = null
    @Volatile private var btnPrimaryView: TextView? = null
    @Volatile private var btnSecondaryView: TextView? = null
    @Volatile private var btnTertiaryView: TextView? = null
    @Volatile private var windowParams: WindowManager.LayoutParams? = null
    @Volatile private var morphAnimator: ValueAnimator? = null
    @Volatile private var nudgeAutoCollapseRunnable: Runnable? = null

    // Saved top-area drag position (x is offset from center; y is clamped to top safe band)
    @Volatile private var savedTopOffsetX: Int? = null
    @Volatile private var savedTopOffsetY: Int? = null
    @Volatile private var isUserDragging: Boolean = false

    val isPausedBubbleShowing: Boolean
        get() = _islandState.value.isTimerActive && !_islandState.value.isRunning

    val isNudgeBannerShowing: Boolean
        get() = _islandState.value.notificationKind == NotificationKind.NUDGE

    val isInviteBannerShowing: Boolean
        get() = _islandState.value.notificationKind == NotificationKind.INVITE

    // ------------------------------------------------------------------ Public API

    /**
     * Synchronizes the Dynamic Island with the latest [TimerState] from [PomodoroTimerService].
     */
    fun syncTimerState(context: Context, timerState: TimerState) {
        val active = timerState.isRunning || !timerState.isUntouched || timerState.isAutoPausedByAppSwitch
        val totalSecs = ((timerState.remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val clock = "%02d:%02d".format(totalSecs / 60L, totalSecs % 60L)
        val mins = ((timerState.remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)

        _islandState.update { prev ->
            val keepExpanded = if (!active && prev.notificationKind == NotificationKind.NONE) {
                false
            } else {
                prev.isExpanded
            }
            prev.copy(
                isTimerActive = active,
                phase = timerState.phase,
                isRunning = timerState.isRunning,
                isAutoPaused = timerState.isAutoPausedByAppSwitch,
                remainingMs = timerState.remainingMs,
                clockText = clock,
                remainingMinutes = mins,
                isExpanded = keepExpanded
            )
        }
        syncSystemOverlay(context.applicationContext)
    }

    /**
     * Expands the Dynamic Island from the compact pill into the full `dynamicSpot` card.
     */
    fun expandIsland(context: Context) {
        if (!_islandState.value.isVisible) return
        if (_islandState.value.isExpanded) return
        _islandState.update { it.copy(isExpanded = true) }
        syncSystemOverlay(context.applicationContext, animateMorphTransition = true)
    }

    /**
     * Collapses the Dynamic Island card back into the compact top pill (or hides it if only a transient
     * notification was showing and no timer is active).
     */
    fun collapseIsland(context: Context) {
        val current = _islandState.value
        if (!current.isExpanded) return
        if (current.notificationKind == NotificationKind.NUDGE || current.notificationKind == NotificationKind.MESSAGE) {
            _islandState.update {
                it.copy(
                    isExpanded = false,
                    notificationKind = NotificationKind.NONE,
                    notificationSender = "",
                    notificationTitle = "",
                    notificationSubtitle = ""
                )
            }
        } else {
            _islandState.update { it.copy(isExpanded = false) }
        }
        syncSystemOverlay(context.applicationContext, animateMorphTransition = true)
    }

    /**
     * Called when the Pomodoro timer is auto-paused due to switching to another app.
     */
    fun showPausedBubble(context: Context, remainingClockText: String) {
        val timerState = PomodoroTimerService.state.value
        val mins = ((timerState.remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        _islandState.update {
            it.copy(
                isTimerActive = true,
                phase = timerState.phase,
                isRunning = false,
                isAutoPaused = true,
                remainingMs = timerState.remainingMs,
                clockText = remainingClockText,
                remainingMinutes = mins
            )
        }
        syncSystemOverlay(context.applicationContext)
    }

    /**
     * Clears the auto-paused flag on the Dynamic Island while keeping the live timer pill if the timer is still active.
     */
    fun hidePausedBubble(context: Context) {
        val timerState = PomodoroTimerService.state.value
        val stillActive = timerState.isRunning || !timerState.isUntouched
        _islandState.update {
            it.copy(
                isTimerActive = stillActive,
                isRunning = timerState.isRunning,
                isAutoPaused = false,
                isExpanded = if (!stillActive && it.notificationKind == NotificationKind.NONE) false else it.isExpanded
            )
        }
        syncSystemOverlay(context.applicationContext)
    }

    /**
     * Pops open the Dynamic Island in `EXPANDED` mode for a Study Group "Return to Focus" nudge.
     */
    fun showNudgeBanner(
        context: Context,
        fromFriendName: String,
        onReturnClicked: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        nudgeReturnCallback = onReturnClicked
        val sender = fromFriendName.ifBlank { appContext.getString(R.string.community_unnamed) }
        _islandState.update {
            it.copy(
                isExpanded = true,
                notificationKind = NotificationKind.NUDGE,
                notificationSender = sender,
                notificationTitle = appContext.getString(R.string.nudge_banner_title, sender),
                notificationSubtitle = appContext.getString(R.string.nudge_banner_message)
            )
        }
        syncSystemOverlay(appContext, animateMorphTransition = true)
        scheduleAutoCollapseNotification(appContext, 6_000L)
    }

    fun hideNudgeBanner(context: Context) {
        nudgeAutoCollapseRunnable?.let { mainHandler.removeCallbacks(it) }
        nudgeAutoCollapseRunnable = null
        nudgeReturnCallback = null
        if (_islandState.value.notificationKind == NotificationKind.NUDGE) {
            _islandState.update {
                it.copy(
                    isExpanded = false,
                    notificationKind = NotificationKind.NONE,
                    notificationSender = "",
                    notificationTitle = "",
                    notificationSubtitle = ""
                )
            }
            syncSystemOverlay(context.applicationContext, animateMorphTransition = true)
        }
    }

    /**
     * Pops open the Dynamic Island in `EXPANDED` mode for an incoming Study Group / Friend Challenge invitation.
     */
    fun showInviteCapsule(
        context: Context,
        fromFriendName: String,
        subtitleText: String,
        onAcceptClicked: () -> Unit,
        onDenyClicked: () -> Unit,
        onHideClicked: () -> Unit
    ) {
        val appContext = context.applicationContext
        inviteAcceptCallback = onAcceptClicked
        inviteDenyCallback = onDenyClicked
        inviteHideCallback = onHideClicked
        val sender = fromFriendName.ifBlank { appContext.getString(R.string.community_unnamed) }
        _islandState.update {
            it.copy(
                isExpanded = true,
                notificationKind = NotificationKind.INVITE,
                notificationSender = sender,
                notificationTitle = appContext.getString(R.string.invite_capsule_title, sender),
                notificationSubtitle = subtitleText
            )
        }
        syncSystemOverlay(appContext, animateMorphTransition = true)
    }

    fun hideInviteCapsule(context: Context) {
        inviteAcceptCallback = null
        inviteDenyCallback = null
        inviteHideCallback = null
        if (_islandState.value.notificationKind == NotificationKind.INVITE) {
            _islandState.update {
                it.copy(
                    isExpanded = false,
                    notificationKind = NotificationKind.NONE,
                    notificationSender = "",
                    notificationTitle = "",
                    notificationSubtitle = ""
                )
            }
            syncSystemOverlay(context.applicationContext, animateMorphTransition = true)
        }
    }

    /**
     * Pops open the Dynamic Island in `EXPANDED` mode for an incoming Study Group chat message.
     */
    fun showMessageNotification(
        context: Context,
        fromFriendName: String,
        messageText: String
    ) {
        val appContext = context.applicationContext
        val sender = fromFriendName.ifBlank { appContext.getString(R.string.community_unnamed) }
        _islandState.update {
            it.copy(
                isExpanded = true,
                notificationKind = NotificationKind.MESSAGE,
                notificationSender = sender,
                notificationTitle = appContext.getString(R.string.study_group_message_notif_title, sender),
                notificationSubtitle = messageText
            )
        }
        syncSystemOverlay(appContext, animateMorphTransition = true)
        scheduleAutoCollapseNotification(appContext, 5_000L)
    }

    /**
     * Updates the Dynamic Island with a live 5-minute pre-lock countdown for an upcoming Daily Lock interval.
     * Whenever remaining time drops below 30 seconds, the Dynamic Island automatically morphs open and shows
     * the unlimited `+1 min` snooze button.
     */
    fun updateDailyLockWarning(
        context: Context,
        scheduleId: String,
        scheduleLabel: String,
        rangeText: String,
        lockAtMs: Long,
        remainingMs: Long,
        onAddOneMinute: () -> Unit
    ) {
        val appContext = context.applicationContext
        lockWarningAddOneMinCallback = onAddOneMinute
        val safeRemaining = remainingMs.coerceAtLeast(0L)
        val totalSecs = ((safeRemaining + 999L) / 1000L).coerceAtLeast(0L)
        val countdownClock = "%02d:%02d".format(totalSecs / 60L, totalSecs % 60L)
        val resolvedLabel = scheduleLabel.ifBlank { appContext.getString(R.string.lock_phone_daily_default_label) }
        val title = appContext.getString(R.string.dynamic_island_lock_warning_title, countdownClock)
        val subtitle = appContext.getString(R.string.dynamic_island_lock_warning_subtitle, resolvedLabel, rangeText)

        val enteringUnder30 = safeRemaining in 1L..29_999L && autoExpandedUnder30ForLockAtMs != lockAtMs
        val wasNotLockWarning = _islandState.value.notificationKind != NotificationKind.LOCK_WARNING
        if (enteringUnder30) {
            autoExpandedUnder30ForLockAtMs = lockAtMs
        }

        val shouldExpandNow = enteringUnder30 || wasNotLockWarning
        _islandState.update { prev ->
            val nextExpanded = if (shouldExpandNow) true else prev.isExpanded
            prev.copy(
                isExpanded = nextExpanded,
                notificationKind = NotificationKind.LOCK_WARNING,
                notificationSender = resolvedLabel,
                notificationTitle = title,
                notificationSubtitle = subtitle,
                lockWarningScheduleId = scheduleId,
                lockWarningRangeText = rangeText,
                lockWarningRemainingMs = safeRemaining
            )
        }
        syncSystemOverlay(appContext, animateMorphTransition = shouldExpandNow)
    }

    fun clearDailyLockWarning(context: Context) {
        lockWarningAddOneMinCallback = null
        autoExpandedUnder30ForLockAtMs = 0L
        if (_islandState.value.notificationKind == NotificationKind.LOCK_WARNING) {
            _islandState.update {
                it.copy(
                    isExpanded = false,
                    notificationKind = NotificationKind.NONE,
                    notificationSender = "",
                    notificationTitle = "",
                    notificationSubtitle = "",
                    lockWarningScheduleId = "",
                    lockWarningRangeText = "",
                    lockWarningRemainingMs = 0L
                )
            }
            syncSystemOverlay(context.applicationContext, animateMorphTransition = true)
        }
    }

    private fun scheduleAutoCollapseNotification(context: Context, delayMs: Long) {
        nudgeAutoCollapseRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val current = _islandState.value
            if (current.notificationKind == NotificationKind.NUDGE ||
                current.notificationKind == NotificationKind.MESSAGE
            ) {
                nudgeReturnCallback?.invoke()
                nudgeReturnCallback = null
                _islandState.update {
                    it.copy(
                        isExpanded = false,
                        notificationKind = NotificationKind.NONE,
                        notificationSender = "",
                        notificationTitle = "",
                        notificationSubtitle = ""
                    )
                }
                syncSystemOverlay(context.applicationContext, animateMorphTransition = true)
            }
        }
        nudgeAutoCollapseRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    // ------------------------------------------------------------------ Actions

    internal fun onPrimaryActionClicked(context: Context) {
        val appContext = context.applicationContext
        val s = _islandState.value
        when (s.notificationKind) {
            NotificationKind.LOCK_WARNING -> {
                if (s.isLockWarningUnder30Sec) {
                    val cb = lockWarningAddOneMinCallback
                    if (cb != null) {
                        cb.invoke()
                    } else if (s.lockWarningScheduleId.isNotEmpty()) {
                        PomodoroTimerService.snoozeDailyLock(appContext, s.lockWarningScheduleId)
                    }
                } else {
                    collapseIsland(appContext)
                }
            }
            NotificationKind.INVITE -> {
                val cb = inviteHideCallback
                hideInviteCapsule(appContext)
                cb?.invoke()
            }
            NotificationKind.NUDGE, NotificationKind.MESSAGE -> {
                hideNudgeBanner(appContext)
            }
            NotificationKind.NONE -> {
                val action = if (s.isRunning) PomodoroTimerService.ACTION_PAUSE else PomodoroTimerService.ACTION_RESUME
                runCatching {
                    val intent = Intent(appContext, PomodoroTimerService::class.java).setAction(action)
                    ContextCompat.startForegroundService(appContext, intent)
                }
            }
        }
    }

    internal fun onSecondaryActionClicked(context: Context) {
        val appContext = context.applicationContext
        val s = _islandState.value
        when (s.notificationKind) {
            NotificationKind.LOCK_WARNING -> {
                collapseIsland(appContext)
            }
            NotificationKind.INVITE -> {
                val cb = inviteDenyCallback
                hideInviteCapsule(appContext)
                cb?.invoke()
            }
            NotificationKind.NUDGE, NotificationKind.MESSAGE -> {
                hideNudgeBanner(appContext)
                openMainActivityTab(appContext, if (s.notificationKind == NotificationKind.MESSAGE) "challenges" else "focus")
            }
            NotificationKind.NONE -> {
                runCatching {
                    val intent = Intent(appContext, PomodoroTimerService::class.java)
                        .setAction(PomodoroTimerService.ACTION_SKIP)
                    ContextCompat.startForegroundService(appContext, intent)
                }
            }
        }
    }

    internal fun onTertiaryActionClicked(context: Context) {
        val appContext = context.applicationContext
        val s = _islandState.value
        when (s.notificationKind) {
            NotificationKind.LOCK_WARNING -> {
                collapseIsland(appContext)
                openMainActivityTab(appContext, "challenges")
            }
            NotificationKind.INVITE -> {
                val cb = inviteAcceptCallback
                hideInviteCapsule(appContext)
                cb?.invoke()
                openMainActivityTab(appContext, "challenges")
            }
            NotificationKind.NUDGE, NotificationKind.MESSAGE -> {
                val cb = nudgeReturnCallback
                hideNudgeBanner(appContext)
                cb?.invoke()
                returnToFocusAndStartTimer(appContext)
            }
            NotificationKind.NONE -> {
                _islandState.update { it.copy(isExpanded = false, isTimerActive = false, isRunning = false) }
                runCatching {
                    val intent = Intent(appContext, PomodoroTimerService::class.java)
                        .setAction(PomodoroTimerService.ACTION_STOP)
                    appContext.startService(intent)
                }
                syncSystemOverlay(appContext, animateMorphTransition = true)
            }
        }
    }

    internal fun onExpandedCardBodyClicked(context: Context) {
        val appContext = context.applicationContext
        val s = _islandState.value
        val target = if (s.notificationKind == NotificationKind.INVITE ||
            s.notificationKind == NotificationKind.MESSAGE ||
            s.notificationKind == NotificationKind.LOCK_WARNING
        ) {
            "challenges"
        } else {
            "focus"
        }
        collapseIsland(appContext)
        openMainActivityTab(appContext, target)
    }

    internal fun returnToFocusApp(context: Context) {
        hidePausedBubble(context)
        runCatching {
            val resumeIntent = Intent(context, PomodoroTimerService::class.java)
                .setAction(PomodoroTimerService.ACTION_AUTO_RESUME)
            ContextCompat.startForegroundService(context, resumeIntent)
        }
        openMainActivityTab(context, "focus")
    }

    internal fun returnToFocusAndStartTimer(context: Context) {
        hidePausedBubble(context)
        hideNudgeBanner(context)
        runCatching {
            val startIntent = Intent(context, PomodoroTimerService::class.java)
                .setAction(PomodoroTimerService.ACTION_START)
            ContextCompat.startForegroundService(context, startIntent)
        }
        openMainActivityTab(context, "focus")
    }

    private fun openMainActivityTab(context: Context, tabKey: String) {
        runCatching {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TARGET_SCREEN, tabKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(openAppIntent)
        }
    }

    // ------------------------------------------------------------------ System WindowManager Overlay

    private fun isPhoneLockActive(): Boolean {
        val active = runCatching { FocusFlowApplication.instance.challengeStore.state.value.active }.getOrNull() ?: return false
        return active.isLockPhone && System.currentTimeMillis() < active.endAt
    }

    /**
     * Synchronizes the system-level `TYPE_APPLICATION_OVERLAY` Dynamic Island window when
     * `Settings.canDrawOverlays(context)` is granted and Focus Island is in the background.
     */
    fun syncSystemOverlay(context: Context, animateMorphTransition: Boolean = false) {
        val appContext = context.applicationContext
        mainHandler.post {
            runCatching {
                val state = _islandState.value
                val canDraw = Settings.canDrawOverlays(appContext)
                val inForeground = runCatching { FocusFlowApplication.instance.isAppInForeground }.getOrDefault(false)
                if (!canDraw || inForeground || !state.isVisible || isPhoneLockActive()) {
                    dismissSystemOverlay(appContext)
                    return@post
                }

                val wm = appContext.getSystemService(WindowManager::class.java) ?: return@post
                if (rootWrapperView == null || islandCardView == null) {
                    createAndAttachSystemOverlay(appContext, wm, state)
                } else {
                    bindSystemOverlayTexts(appContext, state)
                    updateWindowPosition(wm, state.isExpanded)
                    if (animateMorphTransition) {
                        animateSystemIslandMorph(appContext, state.isExpanded)
                    } else if (morphAnimator?.isRunning != true) {
                        applySystemIslandDimensionsImmediate(appContext, state.isExpanded)
                    }
                }
            }.onFailure {
                Log.w(TAG, "Dynamic Island overlay sync failed: ${it.message}")
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndAttachSystemOverlay(
        appContext: Context,
        wm: WindowManager,
        state: DynamicIslandUiState
    ) {
        val density = appContext.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).roundToInt()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        loadSavedTopOffset(appContext)
        val dims = computeDimensions(appContext)
        val initialX = if (state.isExpanded) 0 else clampHorizontalOffset(appContext, savedTopOffsetX ?: 0, dims.compactW)
        val initialY = clampTopBandY(appContext, savedTopOffsetY ?: dp(4f))

        // CRITICAL: Do NOT set FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS.
        // Without those flags, Android automatically places y=0 in the safe application display
        // frame right below the system status bar and physical camera cutout, where the status bar
        // never masks the Dynamic Island or blocks touch events.
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = initialX
            y = initialY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        // Custom root FrameLayout that catches MotionEvent.ACTION_OUTSIDE before child dispatch
        val wrapper = object : FrameLayout(appContext) {
            override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                if (ev?.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (_islandState.value.isExpanded) {
                        collapseIsland(appContext)
                    }
                    return false
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(6f), dp(2f), dp(6f), dp(10f))
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            setStroke(dp(1f).coerceAtLeast(1), Color.parseColor("#1F2428"))
            cornerRadius = dp(if (state.isExpanded) 38f else 999f).toFloat()
        }
        islandBackgroundDrawable = bgDrawable

        val card = FrameLayout(appContext).apply {
            background = bgDrawable
            elevation = dp(12f).toFloat()
            clipChildren = true
            clipToPadding = true
            isClickable = true
            isFocusable = false
        }

        val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop.toFloat().coerceAtLeast(dp(6f).toFloat())

        fun attachTopAreaDragAndTapListener(
            targetView: View,
            allowHorizontalDrag: () -> Boolean,
            allowSwipeUpCollapse: () -> Boolean,
            onTap: () -> Unit
        ) {
            var downRawX = 0f
            var downRawY = 0f
            var startParamX = 0
            var startParamY = 0
            var dragging = false

            targetView.setOnTouchListener { _, event ->
                val lp = windowParams ?: return@setOnTouchListener false
                val root = rootWrapperView ?: return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startParamX = lp.x
                        startParamY = lp.y
                        dragging = false
                        isUserDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            dragging = true
                            isUserDragging = true
                        }
                        if (dragging) {
                            val currentDims = computeDimensions(appContext)
                            val expanded = _islandState.value.isExpanded
                            val currentW = if (expanded) currentDims.expandedW else currentDims.compactW
                            if (allowHorizontalDrag()) {
                                lp.x = clampHorizontalOffset(appContext, (startParamX + dx).roundToInt(), currentW)
                            }
                            lp.y = clampTopBandY(appContext, (startParamY + dy).roundToInt())
                            runCatching { wm.updateViewLayout(root, lp) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dy = event.rawY - downRawY
                        val wasDragging = dragging
                        dragging = false
                        isUserDragging = false
                        if (wasDragging) {
                            if (allowSwipeUpCollapse() && dy < -dp(26f) && lp.y <= dp(6f)) {
                                collapseIsland(appContext)
                            } else {
                                saveTopOffset(
                                    appContext,
                                    offsetX = if (allowHorizontalDrag()) lp.x else (savedTopOffsetX ?: 0),
                                    offsetY = lp.y
                                )
                            }
                        } else {
                            onTap()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        isUserDragging = false
                        true
                    }
                    else -> false
                }
            }
        }

        // ---- 1. Compact Pill Content (Left circular island icon + Center space + Right MM:SS)
        val compactRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5f), dp(4f), dp(13f), dp(4f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = false
        }
        attachTopAreaDragAndTapListener(
            targetView = compactRow,
            allowHorizontalDrag = { !_islandState.value.isExpanded },
            allowSwipeUpCollapse = { false },
            onTap = { expandIsland(appContext) }
        )
        attachTopAreaDragAndTapListener(
            targetView = card,
            allowHorizontalDrag = { !_islandState.value.isExpanded },
            allowSwipeUpCollapse = { _islandState.value.isExpanded },
            onTap = {
                if (!_islandState.value.isExpanded) {
                    expandIsland(appContext)
                }
            }
        )

        val compactIconBadge = createIslandEmblemView(appContext, sizeDp = 26f).apply {
            isClickable = false
            isFocusable = false
        }
        compactRow.addView(compactIconBadge)

        val centerBridgeSpacer = View(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            isClickable = false
            isFocusable = false
        }
        compactRow.addView(centerBridgeSpacer)

        val compactTimerTv = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            isClickable = false
            isFocusable = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fontFeatureSettings = "tnum"
            }
        }
        compactClockView = compactTimerTv
        compactRow.addView(compactTimerTv)
        compactContentView = compactRow
        card.addView(compactRow)

        // ---- 2. Expanded dynamicSpot Card Content
        val expandedCol = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val swipeDetector = GestureDetector(
            appContext,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val startY = e1?.rawY ?: e2.rawY
                    if (e2.rawY - startY < -dp(18f) || velocityY < -350f) {
                        collapseIsland(appContext)
                        return true
                    }
                    return false
                }
            }
        )

        val topInfoRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
        }
        attachTopAreaDragAndTapListener(
            targetView = topInfoRow,
            allowHorizontalDrag = { false },
            allowSwipeUpCollapse = { true },
            onTap = { onExpandedCardBodyClicked(appContext) }
        )

        val expandedIconBadge = createIslandEmblemView(appContext, sizeDp = 50f).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(16f)
            isClickable = false
        }
        topInfoRow.addView(expandedIconBadge)

        val textColumn = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = false
        }

        val headerTv = TextView(appContext).apply {
            setTextColor(Color.parseColor("#38D9C0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = false
        }
        expandedHeaderView = headerTv
        textColumn.addView(headerTv)

        val titleTv = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2f)
            }
        }
        expandedTitleView = titleTv
        textColumn.addView(titleTv)

        val subtitleTv = TextView(appContext).apply {
            setTextColor(Color.parseColor("#B0B5BA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(1f)
            }
        }
        expandedSubtitleView = subtitleTv
        textColumn.addView(subtitleTv)

        topInfoRow.addView(textColumn)
        expandedCol.addView(topInfoRow)

        val buttonsRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16f)
            }
        }

        fun createDarkActionPill(marginStartDp: Float, onClick: () -> Unit): TextView {
            return TextView(appContext).apply {
                setTextColor(Color.parseColor("#F3F4F6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(10f), dp(9f), dp(10f), dp(9f))
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(42f), 1f).apply {
                    marginStart = dp(marginStartDp)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(999f).toFloat()
                    setColor(Color.parseColor("#2B2B2F"))
                }
                setOnClickListener { onClick() }
            }
        }

        val btn1 = createDarkActionPill(0f) { onPrimaryActionClicked(appContext) }
        val btn2 = createDarkActionPill(10f) { onSecondaryActionClicked(appContext) }
        val btn3 = createDarkActionPill(10f) { onTertiaryActionClicked(appContext) }

        btnPrimaryView = btn1
        btnSecondaryView = btn2
        btnTertiaryView = btn3

        buttonsRow.addView(btn1)
        buttonsRow.addView(btn2)
        buttonsRow.addView(btn3)
        expandedCol.addView(buttonsRow)

        expandedCol.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            true
        }

        expandedContentView = expandedCol
        card.addView(expandedCol)

        wrapper.addView(card)
        rootWrapperView = wrapper
        islandCardView = card
        windowParams = params

        bindSystemOverlayTexts(appContext, state)
        applySystemIslandDimensionsImmediate(appContext, state.isExpanded)

        // Entrance spring pop-in animation
        card.pivotY = 0f
        card.scaleX = 0.72f
        card.scaleY = 0.72f
        card.alpha = 0f
        wm.addView(wrapper, params)
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(260L)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()
    }

    private fun createIslandEmblemView(context: Context, sizeDp: Float): FrameLayout {
        val density = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density).roundToInt()
        val sizePx = dp(sizeDp)
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#DCF5F2"))
            }
            clipChildren = true
            clipToOutline = true
            val img = ImageView(context).apply {
                setImageResource(R.drawable.ic_launcher_foreground)
                scaleType = ImageView.ScaleType.CENTER_CROP
                isClickable = false
                isFocusable = false
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(img)
        }
    }

    private fun styleActionPill(
        view: TextView?,
        visible: Boolean,
        highlightedAmber: Boolean = false
    ) {
        view ?: return
        view.visibility = if (visible) View.VISIBLE else View.GONE
        val bg = view.background as? GradientDrawable
        if (highlightedAmber) {
            bg?.setColor(Color.parseColor("#FFB74D"))
            view.setTextColor(Color.parseColor("#111315"))
            view.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        } else {
            bg?.setColor(Color.parseColor("#2B2B2F"))
            view.setTextColor(Color.parseColor("#F3F4F6"))
            view.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private fun bindSystemOverlayTexts(context: Context, state: DynamicIslandUiState) {
        val lockWarningClock = if (state.notificationKind == NotificationKind.LOCK_WARNING) {
            val totalSecs = ((state.lockWarningRemainingMs + 999L) / 1000L).coerceAtLeast(0L)
            "%02d:%02d".format(totalSecs / 60L, totalSecs % 60L)
        } else {
            state.clockText
        }

        compactClockView?.text = when (state.notificationKind) {
            NotificationKind.LOCK_WARNING -> lockWarningClock
            NotificationKind.NUDGE, NotificationKind.INVITE, NotificationKind.MESSAGE ->
                if (state.isTimerActive) state.clockText else "•••"
            NotificationKind.NONE -> state.clockText
        }
        compactClockView?.setTextColor(
            if (state.notificationKind == NotificationKind.LOCK_WARNING) Color.parseColor("#FFB74D") else Color.WHITE
        )

        val phaseName = context.getString(
            when (state.phase) {
                Phase.FOCUS -> R.string.timer_notif_focus
                Phase.SHORT_BREAK -> R.string.timer_notif_short_break
                Phase.LONG_BREAK -> R.string.timer_notif_long_break
            }
        )

        when (state.notificationKind) {
            NotificationKind.LOCK_WARNING -> {
                expandedHeaderView?.setTextColor(Color.parseColor("#FFB74D"))
                expandedHeaderView?.text = context.getString(R.string.dynamic_island_lock_warning_header)
                expandedTitleView?.text = state.notificationTitle
                expandedSubtitleView?.text = state.notificationSubtitle
                if (state.isLockWarningUnder30Sec) {
                    btnPrimaryView?.text = context.getString(R.string.dynamic_island_lock_warning_add_one_min)
                    btnSecondaryView?.text = context.getString(R.string.dynamic_island_lock_warning_hide)
                    btnTertiaryView?.text = context.getString(R.string.dynamic_island_action_open)
                    styleActionPill(btnPrimaryView, visible = true, highlightedAmber = true)
                    styleActionPill(btnSecondaryView, visible = true, highlightedAmber = false)
                    styleActionPill(btnTertiaryView, visible = true, highlightedAmber = false)
                } else {
                    btnPrimaryView?.text = context.getString(R.string.dynamic_island_lock_warning_hide)
                    btnTertiaryView?.text = context.getString(R.string.dynamic_island_action_open)
                    styleActionPill(btnPrimaryView, visible = true, highlightedAmber = false)
                    styleActionPill(btnSecondaryView, visible = false, highlightedAmber = false)
                    styleActionPill(btnTertiaryView, visible = true, highlightedAmber = false)
                }
            }
            NotificationKind.INVITE -> {
                expandedHeaderView?.setTextColor(Color.parseColor("#38D9C0"))
                expandedHeaderView?.text = context.getString(R.string.dynamic_island_header_now)
                expandedTitleView?.text = state.notificationTitle
                expandedSubtitleView?.text = state.notificationSubtitle
                btnPrimaryView?.text = context.getString(R.string.invite_capsule_hide)
                btnSecondaryView?.text = context.getString(R.string.invite_capsule_deny)
                btnTertiaryView?.text = context.getString(R.string.invite_capsule_accept)
                styleActionPill(btnPrimaryView, visible = true)
                styleActionPill(btnSecondaryView, visible = true)
                styleActionPill(btnTertiaryView, visible = true)
            }
            NotificationKind.NUDGE, NotificationKind.MESSAGE -> {
                expandedHeaderView?.setTextColor(Color.parseColor("#38D9C0"))
                expandedHeaderView?.text = context.getString(R.string.dynamic_island_header_now)
                expandedTitleView?.text = state.notificationTitle
                expandedSubtitleView?.text = state.notificationSubtitle
                btnPrimaryView?.text = context.getString(R.string.dynamic_island_action_dismiss)
                btnSecondaryView?.text = context.getString(R.string.dynamic_island_action_open)
                btnTertiaryView?.text = context.getString(R.string.timer_action_resume)
                styleActionPill(btnPrimaryView, visible = true)
                styleActionPill(btnSecondaryView, visible = true)
                styleActionPill(btnTertiaryView, visible = true)
            }
            NotificationKind.NONE -> {
                expandedHeaderView?.setTextColor(Color.parseColor("#38D9C0"))
                expandedHeaderView?.text = if (state.isRunning) {
                    context.getString(R.string.dynamic_island_header_in_min, state.remainingMinutes)
                } else {
                    context.getString(R.string.dynamic_island_header_paused)
                }
                expandedTitleView?.text = "$phaseName · ${state.clockText}"
                expandedSubtitleView?.text = if (state.isAutoPaused) {
                    context.getString(R.string.focus_mode_paused)
                } else {
                    state.clockText
                }
                btnPrimaryView?.text = context.getString(
                    if (state.isRunning) R.string.timer_action_pause else R.string.timer_action_resume
                )
                btnSecondaryView?.text = context.getString(R.string.timer_action_skip)
                btnTertiaryView?.text = context.getString(R.string.timer_action_end)
                styleActionPill(btnPrimaryView, visible = true)
                styleActionPill(btnSecondaryView, visible = true)
                styleActionPill(btnTertiaryView, visible = true)
            }
        }
    }

    private fun loadSavedTopOffset(context: Context) {
        if (savedTopOffsetX != null && savedTopOffsetY != null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val density = context.resources.displayMetrics.density
        savedTopOffsetX = prefs.getInt(KEY_TOP_OFFSET_X, 0)
        savedTopOffsetY = prefs.getInt(KEY_TOP_OFFSET_Y, (4f * density).roundToInt())
    }

    private fun saveTopOffset(context: Context, offsetX: Int, offsetY: Int) {
        val clampedY = clampTopBandY(context, offsetY)
        val dims = computeDimensions(context)
        val clampedX = clampHorizontalOffset(context, offsetX, dims.compactW)
        savedTopOffsetX = clampedX
        savedTopOffsetY = clampedY
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TOP_OFFSET_X, clampedX)
            .putInt(KEY_TOP_OFFSET_Y, clampedY)
            .apply()
    }

    internal fun clampTopBandY(context: Context, rawY: Int): Int {
        val density = context.resources.displayMetrics.density
        val minY = (2f * density).roundToInt()
        val maxY = (88f * density).roundToInt()
        return rawY.coerceIn(minY, maxY)
    }

    internal fun clampHorizontalOffset(context: Context, rawX: Int, viewWidthPx: Int): Int {
        val dm = context.resources.displayMetrics
        val marginPx = (12f * dm.density).roundToInt()
        val maxOffset = ((dm.widthPixels - viewWidthPx) / 2 - marginPx).coerceAtLeast(0)
        return rawX.coerceIn(-maxOffset, maxOffset)
    }

    private fun updateWindowPosition(wm: WindowManager, expanded: Boolean) {
        if (isUserDragging || morphAnimator?.isRunning == true) return
        val wrapper = rootWrapperView ?: return
        val params = windowParams ?: return
        val ctx = wrapper.context
        loadSavedTopOffset(ctx)
        val dims = computeDimensions(ctx)
        val targetX = if (expanded) 0 else clampHorizontalOffset(ctx, savedTopOffsetX ?: 0, dims.compactW)
        val targetY = clampTopBandY(ctx, savedTopOffsetY ?: (4f * ctx.resources.displayMetrics.density).roundToInt())
        if (params.x != targetX || params.y != targetY) {
            params.x = targetX
            params.y = targetY
            runCatching { wm.updateViewLayout(wrapper, params) }
        }
    }

    private fun computeDimensions(context: Context): QuadDimensions {
        val dm = context.resources.displayMetrics
        val density = dm.density
        fun dp(v: Float): Int = (v * density).roundToInt()
        val compactW = dp(168f)
        val compactH = dp(36f)
        val expandedW = (dm.widthPixels - dp(24f)).coerceAtMost(dp(380f)).coerceAtLeast(compactW + dp(40f))
        val expandedH = dp(170f)
        return QuadDimensions(
            compactW = compactW,
            compactH = compactH,
            expandedW = expandedW,
            expandedH = expandedH,
            compactRadius = dp(18f).toFloat(),
            expandedRadius = dp(38f).toFloat()
        )
    }

    private data class QuadDimensions(
        val compactW: Int,
        val compactH: Int,
        val expandedW: Int,
        val expandedH: Int,
        val compactRadius: Float,
        val expandedRadius: Float
    )

    private fun applySystemIslandDimensionsImmediate(context: Context, expanded: Boolean) {
        val card = islandCardView ?: return
        val dims = computeDimensions(context)
        val targetW = if (expanded) dims.expandedW else dims.compactW
        val targetH = if (expanded) dims.expandedH else dims.compactH
        val targetR = if (expanded) dims.expandedRadius else dims.compactRadius

        card.layoutParams = (card.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(targetW, targetH)).apply {
            width = targetW
            height = targetH
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        islandBackgroundDrawable?.cornerRadius = targetR
        compactContentView?.apply {
            alpha = if (expanded) 0f else 1f
            visibility = if (expanded) View.GONE else View.VISIBLE
        }
        expandedContentView?.apply {
            alpha = if (expanded) 1f else 0f
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
        card.requestLayout()
    }

    private fun animateSystemIslandMorph(context: Context, toExpanded: Boolean) {
        val card = islandCardView ?: return
        val compactView = compactContentView ?: return
        val expandedView = expandedContentView ?: return
        val bg = islandBackgroundDrawable ?: return
        val dims = computeDimensions(context)

        morphAnimator?.cancel()

        val startW = card.width.takeIf { it > 0 } ?: if (toExpanded) dims.compactW else dims.expandedW
        val startH = card.height.takeIf { it > 0 } ?: if (toExpanded) dims.compactH else dims.expandedH
        val endW = if (toExpanded) dims.expandedW else dims.compactW
        val endH = if (toExpanded) dims.expandedH else dims.compactH
        val startR = if (toExpanded) dims.compactRadius else dims.expandedRadius
        val endR = if (toExpanded) dims.expandedRadius else dims.compactRadius

        val startX = windowParams?.x ?: 0
        val endX = if (toExpanded) 0 else clampHorizontalOffset(context, savedTopOffsetX ?: 0, dims.compactW)
        val wm = context.getSystemService(WindowManager::class.java)

        compactView.visibility = View.VISIBLE
        expandedView.visibility = View.VISIBLE

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (toExpanded) 320L else 250L
            interpolator = if (toExpanded) OvershootInterpolator(0.82f) else DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val rawFraction = anim.animatedFraction
                val value = (anim.animatedValue as Float)
                val w = (startW + (endW - startW) * value).roundToInt().coerceAtLeast(dims.compactW / 2)
                val h = (startH + (endH - startH) * value).roundToInt().coerceAtLeast(dims.compactH / 2)
                val r = (startR + (endR - startR) * rawFraction).coerceAtLeast(0f)

                val lp = card.layoutParams as? ViewGroup.LayoutParams
                if (lp != null) {
                    lp.width = w
                    lp.height = h
                    card.layoutParams = lp
                }
                bg.cornerRadius = r

                val winLp = windowParams
                val root = rootWrapperView
                if (winLp != null && root != null && wm != null && !isUserDragging && startX != endX) {
                    val nextX = (startX + (endX - startX) * rawFraction).roundToInt()
                    if (winLp.x != nextX) {
                        winLp.x = nextX
                        runCatching { wm.updateViewLayout(root, winLp) }
                    }
                }

                if (toExpanded) {
                    compactView.alpha = (1f - rawFraction * 2.5f).coerceIn(0f, 1f)
                    expandedView.alpha = ((rawFraction - 0.28f) / 0.72f).coerceIn(0f, 1f)
                } else {
                    expandedView.alpha = (1f - rawFraction * 2.5f).coerceIn(0f, 1f)
                    compactView.alpha = ((rawFraction - 0.3f) / 0.7f).coerceIn(0f, 1f)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    compactView.visibility = if (toExpanded) View.GONE else View.VISIBLE
                    compactView.alpha = if (toExpanded) 0f else 1f
                    expandedView.visibility = if (toExpanded) View.VISIBLE else View.GONE
                    expandedView.alpha = if (toExpanded) 1f else 0f
                }
            })
        }
        morphAnimator = animator
        animator.start()
    }

    private fun dismissSystemOverlay(context: Context) {
        val wrapper = rootWrapperView ?: return
        val card = islandCardView
        morphAnimator?.cancel()
        morphAnimator = null
        rootWrapperView = null
        islandCardView = null
        islandBackgroundDrawable = null
        compactContentView = null
        compactClockView = null
        expandedContentView = null
        expandedHeaderView = null
        expandedTitleView = null
        expandedSubtitleView = null
        btnPrimaryView = null
        btnSecondaryView = null
        btnTertiaryView = null
        windowParams = null

        val wm = context.getSystemService(WindowManager::class.java)
        if (card != null && wrapper.isAttachedToWindow) {
            card.pivotY = 0f
            card.animate()
                .scaleX(0.68f)
                .scaleY(0.68f)
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    mainHandler.post {
                        runCatching { wm?.removeView(wrapper) }
                    }
                }
                .start()
        } else {
            mainHandler.post {
                runCatching { wm?.removeView(wrapper) }
            }
        }
    }
}
