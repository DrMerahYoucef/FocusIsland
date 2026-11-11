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
 * Manages the `dynamicSpot`-style **Dynamic Island** at the top center of the screen for:
 * 1. Live Pomodoro Focus / Short Break / Long Break countdowns (running or paused).
 * 2. Incoming app notifications (Study Group focus nudges, Challenge invitations, and Study Group chat alerts).
 *
 * Supports two synchronized states with fluid spring open/close morph animations:
 * - **Collapsed Pill (`COMPACT`)**: Pure-black pill (`#000000`) aligned with the top camera cutout,
 *   featuring the circular Focus Island palm-tree emblem on the left and live tabular timer on the right.
 * - **Expanded Card (`EXPANDED`)**: Full-width rounded pure-black card (`#000000`, `38dp` radius)
 *   with lagoon-teal header (`Focus Island • Dans 48 min`), title (`En focus · 48:31`), subtitle (`48:30`),
 *   and 3 dark-charcoal pill buttons (`Pause` / `Reprendre`, `Passer`, `Terminer`).
 *
 * Tapping the compact pill expands the card; tapping anywhere outside the expanded card (or swiping up)
 * smoothly collapses it back to the compact pill.
 */
object FloatingFocusBubbleManager {

    private const val TAG = "DynamicIslandManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    enum class NotificationKind {
        NONE,
        NUDGE,
        INVITE,
        MESSAGE
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
        val notificationSubtitle: String = ""
    ) {
        val isVisible: Boolean
            get() = isTimerActive || notificationKind != NotificationKind.NONE
    }

    private val _islandState = MutableStateFlow(DynamicIslandUiState())
    val islandState: StateFlow<DynamicIslandUiState> = _islandState.asStateFlow()

    // Callbacks for active notification actions
    @Volatile private var nudgeReturnCallback: (() -> Unit)? = null
    @Volatile private var inviteAcceptCallback: (() -> Unit)? = null
    @Volatile private var inviteDenyCallback: (() -> Unit)? = null
    @Volatile private var inviteHideCallback: (() -> Unit)? = null

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
     * Shows the Dynamic Island and briefly expands it so the user sees the pause status and controls.
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
        val target = if (s.notificationKind == NotificationKind.INVITE || s.notificationKind == NotificationKind.MESSAGE) {
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
     * `Settings.canDrawOverlays(context)` is granted and the app is in the background.
     * While `MainActivity` is in the foreground, `DynamicSpotIslandOverlay` renders the island in Compose.
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
                    updateWindowOutsideTouchFlags(wm, state.isExpanded)
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

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(6f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val wrapper = FrameLayout(appContext).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8f), dp(2f), dp(8f), dp(12f))
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
            elevation = dp(14f).toFloat()
            clipChildren = true
            clipToPadding = true
        }

        // ---- 1. Compact Pill Content (Left circular island icon + Center cutout gap + Right MM:SS)
        val compactRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5f), dp(4f), dp(13f), dp(4f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val compactIconBadge = createIslandEmblemView(appContext, sizeDp = 26f)
        compactRow.addView(compactIconBadge)

        val centerBridgeSpacer = View(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        compactRow.addView(centerBridgeSpacer)

        val compactTimerTv = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
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

        val topInfoRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                onExpandedCardBodyClicked(appContext)
            }
        }

        val expandedIconBadge = createIslandEmblemView(appContext, sizeDp = 50f).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(16f)
        }
        topInfoRow.addView(expandedIconBadge)

        val textColumn = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val headerTv = TextView(appContext).apply {
            setTextColor(Color.parseColor("#38D9C0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        expandedHeaderView = headerTv
        textColumn.addView(headerTv)

        val titleTv = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
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

        expandedContentView = expandedCol
        card.addView(expandedCol)

        // Touch handling:
        // - MotionEvent.ACTION_OUTSIDE collapses the expanded card
        // - Tapping the compact pill expands the card
        // - Swiping up on the expanded card collapses the card
        val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop
        var downY = 0f
        wrapper.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                if (_islandState.value.isExpanded) {
                    collapseIsland(appContext)
                }
                return@setOnTouchListener false
            }
            false
        }

        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    if (_islandState.value.isExpanded && dy < -touchSlop * 1.5f) {
                        collapseIsland(appContext)
                        return@setOnTouchListener true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dy = abs(event.rawY - downY)
                    if (dy <= touchSlop && !_islandState.value.isExpanded) {
                        expandIsland(appContext)
                    }
                    true
                }
                else -> false
            }
        }

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
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(img)
        }
    }

    private fun bindSystemOverlayTexts(context: Context, state: DynamicIslandUiState) {
        compactClockView?.text = when (state.notificationKind) {
            NotificationKind.NUDGE, NotificationKind.INVITE, NotificationKind.MESSAGE ->
                if (state.isTimerActive) state.clockText else "•••"
            NotificationKind.NONE -> state.clockText
        }

        val phaseName = context.getString(
            when (state.phase) {
                Phase.FOCUS -> R.string.timer_notif_focus
                Phase.SHORT_BREAK -> R.string.timer_notif_short_break
                Phase.LONG_BREAK -> R.string.timer_notif_long_break
            }
        )

        when (state.notificationKind) {
            NotificationKind.INVITE -> {
                expandedHeaderView?.text = context.getString(R.string.dynamic_island_header_now)
                expandedTitleView?.text = state.notificationTitle
                expandedSubtitleView?.text = state.notificationSubtitle
                btnPrimaryView?.text = context.getString(R.string.invite_capsule_hide)
                btnSecondaryView?.text = context.getString(R.string.invite_capsule_deny)
                btnTertiaryView?.text = context.getString(R.string.invite_capsule_accept)
            }
            NotificationKind.NUDGE, NotificationKind.MESSAGE -> {
                expandedHeaderView?.text = context.getString(R.string.dynamic_island_header_now)
                expandedTitleView?.text = state.notificationTitle
                expandedSubtitleView?.text = state.notificationSubtitle
                btnPrimaryView?.text = context.getString(R.string.dynamic_island_action_dismiss)
                btnSecondaryView?.text = context.getString(R.string.dynamic_island_action_open)
                btnTertiaryView?.text = context.getString(R.string.timer_action_resume)
            }
            NotificationKind.NONE -> {
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
            }
        }
    }

    private fun updateWindowOutsideTouchFlags(wm: WindowManager, expanded: Boolean) {
        val wrapper = rootWrapperView ?: return
        val params = windowParams ?: return
        val density = wrapper.context.resources.displayMetrics.density
        val targetY = ((if (expanded) 10f else 6f) * density).roundToInt()
        if (params.y != targetY) {
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
