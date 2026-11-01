package com.example.service

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.example.FocusFlowApplication
import com.example.data.challenge.Challenge
import com.example.data.challenge.ChallengeQuotes
import com.example.ui.screen.challenge.ChallengeTime
import com.example.ui.theme.AppStyle
import com.example.ui.theme.FocusStyles
import com.example.ui.theme.ThemeMode
import java.lang.ref.WeakReference

/**
 * Unwraps any [ContextWrapper] chain (such as locale wrappers) to locate the underlying [Activity].
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Persistent, full-screen system window shield (`TYPE_APPLICATION_OVERLAY`) for strict
 * Lock Phone personal challenges.
 *
 * Displays only the countdown counter and the session's motivational quote, while sitting
 * on top of Recent Apps, the Home launcher, and any app opened from the Notification Drawer.
 */
object PhoneLockOverlayManager {

    private const val TAG = "PhoneLockOverlay"
    private const val PREFS_NAME = "focusflow_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var shieldView: View? = null
    private var shieldParams: WindowManager.LayoutParams? = null
    private var ringView: CountdownRingView? = null
    private var clockTextView: TextView? = null
    private var renderedChallengeId: Long = -1L

    @Volatile
    private var pinnedActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var hostActivityRef: WeakReference<Activity>? = null

    val isShieldAttached: Boolean
        get() = shieldView != null

    /**
     * Registers the currently active [Activity] so [dismissShield] and [tryStopScreenPinning]
     * can always unpin the screen even when triggered from a background service or alarm.
     */
    fun registerHostActivity(activity: Activity) {
        hostActivityRef = WeakReference(activity)
    }

    /**
     * Checks whether the device is currently in Android Screen Pinning (`LockTask`) mode.
     */
    fun isScreenPinned(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /**
     * Starts Android Screen Pinning (`startLockTask()`) on [activity] if not already pinned.
     * Screen Pinning disables pulling down the Notification Drawer, Quick Settings, and Recent Apps
     * at the OS level.
     */
    fun tryStartScreenPinning(activity: Activity) {
        pinnedActivityRef = WeakReference(activity)
        hostActivityRef = WeakReference(activity)
        val block = Runnable {
            runCatching {
                if (!isScreenPinned(activity)) {
                    activity.startLockTask()
                }
            }.onFailure {
                Log.w(TAG, "startLockTask failed: ${it.message}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block.run()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Stops Android Screen Pinning (`stopLockTask()`) when the lock timer finishes.
     * Automatically resolves the passed [activity], the tracked [pinnedActivityRef], and
     * [hostActivityRef] so the app never remains stuck in pinned mode.
     */
    fun tryStopScreenPinning(activity: Activity? = null) {
        val block = Runnable {
            val candidates = listOfNotNull(
                activity,
                pinnedActivityRef?.get(),
                hostActivityRef?.get()
            ).distinct()
            for (act in candidates) {
                runCatching {
                    if (isScreenPinned(act)) {
                        act.stopLockTask()
                    } else {
                        // Fallback in case lockTaskModeState is transiently stale
                        runCatching { act.stopLockTask() }
                    }
                }.onFailure {
                    Log.w(TAG, "stopLockTask failed: ${it.message}")
                }
            }
            pinnedActivityRef = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block.run()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Synchronizes the full-screen `TYPE_APPLICATION_OVERLAY` shield with the current active
     * Lock Phone challenge. Called by [PomodoroTimerService] and [AppLaunchMonitor].
     */
    fun syncWithActiveChallenge(context: Context) {
        val appContext = context.applicationContext
        val store = runCatching { FocusFlowApplication.instance.challengeStore }.getOrNull()
        val active = store?.state?.value?.active
        val now = System.currentTimeMillis()

        if (active == null || !active.isLockPhone || now >= active.endAt) {
            if (active != null && active.isLockPhone && now >= active.endAt) {
                runCatching { store.expireIfDue() }
                ChallengeAlarmReceiver.cancel(appContext)
            }
            tryStopScreenPinning(context.findActivity())
            dismissShield(appContext)
            return
        }

        if (!Settings.canDrawOverlays(appContext)) return
        showOrUpdateShield(appContext, active, now)
    }

    /**
     * Immediately forces the full-screen system overlay shield to cover the screen.
     */
    fun reLockImmediately(context: Context) {
        val appContext = context.applicationContext
        val store = runCatching { FocusFlowApplication.instance.challengeStore }.getOrNull()
        val active = store?.state?.value?.active
        val now = System.currentTimeMillis()
        if (active == null || !active.isLockPhone || now >= active.endAt) {
            tryStopScreenPinning(context.findActivity())
            dismissShield(appContext)
            return
        }
        showOrUpdateShield(appContext, active, now)
    }

    /**
     * Removes the system overlay shield completely and unpins the screen when the Lock Phone session finishes.
     */
    fun dismissShield(context: Context) {
        val callerActivity = context.findActivity()
        tryStopScreenPinning(callerActivity)
        val appContext = context.applicationContext
        mainHandler.post {
            tryStopScreenPinning(callerActivity)
            val view = shieldView ?: return@post
            shieldView = null
            shieldParams = null
            ringView = null
            clockTextView = null
            renderedChallengeId = -1L
            runCatching {
                appContext.getSystemService(WindowManager::class.java)?.removeViewImmediate(view)
            }
        }
    }

    private fun showOrUpdateShield(context: Context, challenge: Challenge, nowMs: Long) {
        val appContext = context.applicationContext
        mainHandler.post {
            runCatching {
                if (!Settings.canDrawOverlays(appContext)) return@post
                val wm = appContext.getSystemService(WindowManager::class.java) ?: return@post
                val remainingMs = (challenge.endAt - nowMs).coerceAtLeast(0L)
                if (remainingMs <= 0L) {
                    FocusFlowApplication.instance.challengeStore.expireIfDue()
                    ChallengeAlarmReceiver.cancel(appContext)
                    dismissShield(appContext)
                    return@post
                }

                val existing = shieldView
                val existingParams = shieldParams
                if (existing != null && existingParams != null && renderedChallengeId == challenge.id) {
                    val desiredFlags = fullScreenLockFlags()
                    if (existing.visibility != View.VISIBLE ||
                        existingParams.width != WindowManager.LayoutParams.MATCH_PARENT ||
                        existingParams.height != WindowManager.LayoutParams.MATCH_PARENT ||
                        existingParams.flags != desiredFlags
                    ) {
                        existing.visibility = View.VISIBLE
                        existingParams.width = WindowManager.LayoutParams.MATCH_PARENT
                        existingParams.height = WindowManager.LayoutParams.MATCH_PARENT
                        existingParams.flags = desiredFlags
                        runCatching { wm.updateViewLayout(existing, existingParams) }
                    }
                    clockTextView?.text = ChallengeTime.clock(remainingMs)
                    val progress = if (challenge.durationMs > 0L) {
                        (remainingMs.toFloat() / challenge.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    ringView?.setProgress(progress)
                    return@post
                }

                existing?.let { old ->
                    runCatching { wm.removeViewImmediate(old) }
                }

                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType,
                    fullScreenLockFlags(),
                    PixelFormat.OPAQUE
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                val builtView = buildShieldView(appContext, challenge, remainingMs)
                wm.addView(builtView, params)
                shieldView = builtView
                shieldParams = params
                renderedChallengeId = challenge.id
            }.onFailure {
                Log.w(TAG, "Could not show/update phone lock shield: ${it.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun fullScreenLockFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON

    private fun resolveActiveColorScheme(context: Context): ColorScheme {
        val style = AppStyle.read(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = ThemeMode.from(prefs.getString(KEY_THEME_MODE, null))
        val isDark = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
        return FocusStyles.of(style).colors(isDark)
    }

    private fun buildShieldView(context: Context, challenge: Challenge, remainingMs: Long): View {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val scheme = resolveActiveColorScheme(context)

        val rootFrame = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event != null && (
                        event.keyCode == KeyEvent.KEYCODE_BACK ||
                            event.keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                            event.keyCode == KeyEvent.KEYCODE_MENU
                        )
                ) {
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus) {
                    @Suppress("DEPRECATION")
                    runCatching { context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) }
                }
            }
        }.apply {
            setBackgroundColor(scheme.background.toArgb())
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, _ -> true }
        }

        val contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(32))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Countdown Counter (Ring + Clock only)
        val ringContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(240)).apply {
                bottomMargin = dp(28)
            }
        }
        val initialProgress = if (challenge.durationMs > 0L) {
            (remainingMs.toFloat() / challenge.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val countdownRing = CountdownRingView(
            context = context,
            trackColor = scheme.surfaceVariant.toArgb(),
            progressColor = scheme.primary.toArgb(),
            strokePx = dp(12).toFloat()
        ).apply {
            setProgress(initialProgress)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        ringView = countdownRing
        ringContainer.addView(countdownRing)

        val clockTv = TextView(context).apply {
            text = ChallengeTime.clock(remainingMs)
            setTextColor(scheme.onBackground.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        clockTextView = clockTv
        ringContainer.addView(clockTv)
        contentCol.addView(ringContainer)

        // 2. Motivational Quote Card
        val quoteText = challenge.quoteText.ifBlank { ChallengeQuotes.quotes.first().quote }
        val quoteAuthor = challenge.quoteAuthor.ifBlank { ChallengeQuotes.quotes.first().author }
        val quoteCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(scheme.surfaceContainerHigh.toArgb())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val quoteBodyTv = TextView(context).apply {
            text = "“$quoteText”"
            setTextColor(scheme.onSurface.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.ITALIC)
            gravity = Gravity.CENTER
        }
        quoteCard.addView(quoteBodyTv)
        if (quoteAuthor.isNotBlank()) {
            val authorTv = TextView(context).apply {
                text = "— $quoteAuthor"
                setTextColor(scheme.primary.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            }
            quoteCard.addView(authorTv)
        }
        contentCol.addView(quoteCard)
        rootFrame.addView(contentCol)

        // Top-edge status bar swipe blocker strip
        val topSwipeInterceptor = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(40),
                Gravity.TOP
            )
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_MOVE
                ) {
                    @Suppress("DEPRECATION")
                    runCatching { context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) }
                }
                true
            }
        }
        rootFrame.addView(topSwipeInterceptor)

        return rootFrame
    }

    private class CountdownRingView(
        context: Context,
        trackColor: Int,
        progressColor: Int,
        private val strokePx: Float
    ) : View(context) {

        private var progress: Float = 1f
        private val oval = RectF()

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = trackColor
            strokeCap = Paint.Cap.ROUND
        }

        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = progressColor
            strokeCap = Paint.Cap.ROUND
        }

        fun setProgress(value: Float) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped != progress) {
                progress = clamped
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = strokePx / 2f + 4f
            oval.set(pad, pad, width - pad, height - pad)
            canvas.drawOval(oval, trackPaint)
            canvas.drawArc(oval, -90f, 360f * progress, false, progressPaint)
        }
    }
}
