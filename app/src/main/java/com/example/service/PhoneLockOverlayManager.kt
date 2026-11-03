package com.example.service

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
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
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.example.FocusFlowApplication
import com.example.MainActivity
import com.example.R
import com.example.data.challenge.Challenge
import com.example.data.challenge.ChallengeQuotes
import com.example.ui.screen.challenge.ChallengeTime
import com.example.ui.theme.AppStyle
import com.example.ui.theme.FocusStyles
import com.example.ui.theme.ThemeMode
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
 * System window shield (`TYPE_APPLICATION_OVERLAY`) and crash-proof permitted app
 * launcher for strict Lock Phone personal challenges.
 *
 * Modes:
 * 1. **In-App Foreground Mode**: While `MainActivity` (`StrictPhoneLockOverlay`) is in the
 *    foreground, no redundant `WindowManager` surface is stacked on top of `MainActivity`
 *    (preventing dual-surface `FrameEvents` fence conflicts).
 * 2. **Full-Screen Shield Mode**: Whenever the user leaves `MainActivity` for a non-permitted
 *    app or the Home launcher, covers the screen immediately and brings `MainActivity` back.
 * 3. **Floating Return Pill Mode**: While the user is inside a permitted app, displays a
 *    compact non-blocking top pill (`🔒 44:12 · Return to lock screen`).
 */
object PhoneLockOverlayManager {

    private const val TAG = "PhoneLockOverlay"
    private const val PREFS_NAME = "focusflow_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    /** Synthetic package identifier ensuring a Phone/Dialer option is always available and safe to launch. */
    const val SYNTHETIC_PHONE_DIALER_PKG = "system.phone.dialer"

    /** Strict single-flight cooldown (ms) preventing rapid multi-tap crashes on Phone or other permitted apps. */
    const val LAUNCH_COOLDOWN_MS = 1_500L

    /** Grace window (ms) while Android transitions from the lock screen into the permitted app. */
    private const val LAUNCH_GRACE_WINDOW_MS = 3_500L

    private val KNOWN_DIALER_PACKAGES = setOf(
        SYNTHETIC_PHONE_DIALER_PKG,
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.contacts",
        "com.google.android.contacts"
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class OverlayMode { NONE, FULL_SHIELD, FLOATING_PILL }

    @Volatile
    private var currentMode: OverlayMode = OverlayMode.NONE

    @Volatile
    private var shieldView: View? = null
    private var shieldParams: WindowManager.LayoutParams? = null
    private var ringView: CountdownRingView? = null
    private var clockTextView: TextView? = null
    private var floatingPillTextView: TextView? = null
    private var renderedChallengeId: Long = -1L
    private var renderedAllowedPackages: Set<String> = emptySet()

    @Volatile
    private var pinnedActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var hostActivityRef: WeakReference<Activity>? = null

    private val launchInFlight = AtomicBoolean(false)
    private val lastLaunchAttemptMs = AtomicLong(0L)

    @Volatile
    private var launchGraceUntilWallMs: Long = 0L

    @Volatile
    private var activePermittedPackage: String? = null

    val isShieldAttached: Boolean
        get() = shieldView != null

    val isFloatingPillActive: Boolean
        get() = currentMode == OverlayMode.FLOATING_PILL && shieldView != null

    /**
     * Resets the single-flight launch cooldown state (used by unit tests and when a lock session ends).
     */
    fun resetLaunchGuardState() {
        launchInFlight.set(false)
        lastLaunchAttemptMs.set(0L)
        launchGraceUntilWallMs = 0L
        activePermittedPackage = null
    }

    /**
     * Returns true while Android is mid-transition into a newly launched permitted app.
     */
    fun isWithinLaunchGraceWindow(nowWallMs: Long = System.currentTimeMillis()): Boolean =
        nowWallMs < launchGraceUntilWallMs

    /**
     * Returns true when the user has launched a permitted external app from the lock screen
     * and has not yet returned to Focus Island or switched to an unpermitted app.
     */
    fun hasActivePermittedSession(nowWallMs: Long = System.currentTimeMillis()): Boolean =
        activePermittedPackage != null || isWithinLaunchGraceWindow(nowWallMs)

    /**
     * Called when `StrictPhoneLockOverlay` inside `MainActivity` is visible in the foreground.
     * Clears any active permitted-app session (once the launch grace window has passed) and removes
     * any redundant overlay window so `MainActivity` is the sole rendering surface.
     */
    fun onLockScreenInForeground(context: Context) {
        if (isWithinLaunchGraceWindow()) return
        activePermittedPackage = null
        removeShieldViewOnly(context.applicationContext)
    }

    /**
     * Resolves all system dialer / phone call packages on this device so permitting the Phone app
     * also permits the system InCallUI / Telecom / Dialer activity that actually opens.
     */
    fun resolveSystemDialerPackages(context: Context): Set<String> {
        val result = HashSet<String>(KNOWN_DIALER_PACKAGES)
        runCatching {
            val tm = context.getSystemService(TelecomManager::class.java)
            tm?.defaultDialerPackage?.takeIf { it.isNotBlank() }?.let { result += it }
        }
        runCatching {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val resolved = context.packageManager.queryIntentActivities(dialIntent, PackageManager.MATCH_ALL)
            resolved.forEach { info ->
                info.activityInfo?.packageName?.takeIf { it.isNotBlank() }?.let { result += it }
            }
        }
        return result
    }

    fun isDialerOrCallPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName in KNOWN_DIALER_PACKAGES) return true
        if (packageName.contains("dialer", ignoreCase = true) ||
            packageName.contains("incallui", ignoreCase = true) ||
            packageName.contains("telecom", ignoreCase = true)
        ) {
            return true
        }
        return packageName in resolveSystemDialerPackages(context)
    }

    /**
     * Checks whether [pkg] is permitted during the active Lock Phone challenge.
     * If any dialer/phone package is in [Challenge.allowedPackages], all system dialer/incall
     * packages are permitted so the Phone app never crashes or loops.
     */
    fun isPackagePermittedForActiveLock(
        context: Context,
        pkg: String?,
        challenge: Challenge? = runCatching { FocusFlowApplication.instance.challengeStore.state.value.active }.getOrNull()
    ): Boolean {
        if (pkg.isNullOrBlank() || challenge == null || !challenge.isLockPhone) return false
        if (pkg == "android" || pkg == "com.android.intentresolver") {
            return hasActivePermittedSession()
        }
        val allowed = challenge.allowedPackages
        if (allowed.isEmpty()) return false
        if (pkg in allowed) return true
        val dialerPermitted = allowed.any { isDialerOrCallPackage(context, it) }
        if (dialerPermitted && isDialerOrCallPackage(context, pkg)) {
            return true
        }
        return false
    }

    /**
     * Crash-proof, single-flight launcher for permitted apps on the Lock Phone screen.
     *
     * Protects against the classic detox-app multi-tap crash:
     * 1. Enforces an atomic [LAUNCH_COOLDOWN_MS] (1500ms) single-flight gate so rapid repeated
     *    clicks on the Phone app (or any permitted app) are ignored immediately.
     * 2. Posts execution to the main looper queue so `WindowManager` views are never torn down
     *    synchronously mid-touch-event (`FrameEvents` fence safety).
     * 3. Cleanly unpins Android Screen Pinning (`stopLockTask`) BEFORE launching the external
     *    Activity and starts the intent from the foreground `Activity` context.
     * 4. Transitions the overlay into a compact non-blocking Floating Return Pill.
     *
     * @return `true` if the launch was accepted, `false` if debounced/rejected by the cooldown gate.
     */
    fun launchPermittedAppSafely(
        context: Context,
        packageName: String,
        nowOverrideMs: Long? = null
    ): Boolean {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return false

        val nowTick = nowOverrideMs ?: run {
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed > 0L) elapsed else System.currentTimeMillis()
        }

        // Atomic single-flight & 1500ms cooldown check
        if (launchInFlight.get()) {
            Log.d(TAG, "Ignored rapid tap on $cleanPkg (launch already in flight)")
            return false
        }
        val prevAttempt = lastLaunchAttemptMs.get()
        if (prevAttempt > 0L && (nowTick - prevAttempt) in 0 until LAUNCH_COOLDOWN_MS) {
            Log.d(TAG, "Ignored rapid tap on $cleanPkg (cooldown active)")
            return false
        }
        if (!lastLaunchAttemptMs.compareAndSet(prevAttempt, nowTick)) {
            return false
        }
        if (!launchInFlight.compareAndSet(false, true)) {
            return false
        }

        val appContext = context.applicationContext
        val callerActivity = context.findActivity() ?: hostActivityRef?.get()
        activePermittedPackage = cleanPkg
        launchGraceUntilWallMs = System.currentTimeMillis() + LAUNCH_GRACE_WINDOW_MS

        // Always post to mainHandler so we never mutate WindowManager surfaces mid-input-frame
        mainHandler.post {
            try {
                // Step 1: Ensure screen is unpinned before starting external Activity
                stopScreenPinningImmediate(callerActivity)

                // Step 2: Resolve safe launch intent (with ACTION_DIAL fallback for Phone app)
                val starter: Context = callerActivity?.takeIf { !it.isFinishing && !it.isDestroyed } ?: appContext
                val launchIntent = buildSafeLaunchIntent(appContext, cleanPkg, useNewTask = starter === appContext)
                var launchedOk = false
                if (launchIntent != null) {
                    launchedOk = runCatching {
                        starter.startActivity(launchIntent)
                        true
                    }.getOrElse { err ->
                        Log.w(TAG, "Primary launch failed for $cleanPkg: ${err.message}")
                        if (isDialerOrCallPackage(appContext, cleanPkg)) {
                            runCatching {
                                val fallbackDial = Intent(Intent.ACTION_DIAL).apply {
                                    if (starter === appContext) {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                starter.startActivity(fallbackDial)
                                true
                            }.getOrDefault(false)
                        } else {
                            false
                        }
                    }
                }

                // Step 3: Show compact floating return pill if launched, otherwise clear permitted state
                val active = runCatching { FocusFlowApplication.instance.challengeStore.state.value.active }.getOrNull()
                if (launchedOk && active != null && active.isLockPhone) {
                    showFloatingPillInternal(appContext, active, System.currentTimeMillis())
                } else if (!launchedOk) {
                    activePermittedPackage = null
                    launchGraceUntilWallMs = 0L
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Guarded error during permitted app launch for $cleanPkg: ${t.message}")
            } finally {
                mainHandler.postDelayed({ launchInFlight.set(false) }, 350L)
            }
        }
        return true
    }

    internal fun buildSafeLaunchIntent(
        context: Context,
        packageName: String,
        useNewTask: Boolean = true
    ): Intent? {
        val pm = context.packageManager
        val baseFlags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        val flags = if (useNewTask) baseFlags or Intent.FLAG_ACTIVITY_NEW_TASK else baseFlags

        if (packageName != SYNTHETIC_PHONE_DIALER_PKG) {
            val direct = runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull()
            if (direct != null) {
                direct.addFlags(flags)
                return direct
            }
        }

        if (isDialerOrCallPackage(context, packageName)) {
            val defaultDialer = runCatching {
                context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            }.getOrNull()
            if (!defaultDialer.isNullOrBlank()) {
                val dialerLauncher = runCatching { pm.getLaunchIntentForPackage(defaultDialer) }.getOrNull()
                if (dialerLauncher != null) {
                    dialerLauncher.addFlags(flags)
                    return dialerLauncher
                }
            }
            return Intent(Intent.ACTION_DIAL).apply {
                addFlags(flags)
            }
        }
        return null
    }

    /**
     * Called when the user taps the Floating Return Pill or leaves a permitted app.
     */
    fun returnFromPermittedAppToLockScreen(context: Context) {
        val appContext = context.applicationContext
        launchGraceUntilWallMs = 0L
        activePermittedPackage = null
        removeShieldViewOnly(appContext)
        runCatching {
            val intent = Intent(appContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra("navigate_to_tab", "challenges")
            }
            appContext.startActivity(intent)
        }
    }

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
     * Starts Android Screen Pinning (`startLockTask()`) on [activity] ONLY when the active
     * Lock Phone challenge has zero permitted external apps (`allowedPackages.isEmpty()`).
     */
    fun tryStartScreenPinning(activity: Activity) {
        pinnedActivityRef = WeakReference(activity)
        hostActivityRef = WeakReference(activity)
        val block = Runnable {
            runCatching {
                val active = runCatching { FocusFlowApplication.instance.challengeStore.state.value.active }.getOrNull()
                if (active?.allowedPackages?.isNotEmpty() == true) {
                    stopScreenPinningImmediate(activity)
                    return@runCatching
                }
                if (hasActivePermittedSession()) return@runCatching
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

    private fun stopScreenPinningImmediate(activity: Activity? = null) {
        val candidates = listOfNotNull(
            activity,
            pinnedActivityRef?.get(),
            hostActivityRef?.get()
        ).distinct()
        for (act in candidates) {
            runCatching {
                if (isScreenPinned(act)) {
                    act.stopLockTask()
                }
            }.onFailure {
                Log.w(TAG, "stopLockTask failed: ${it.message}")
            }
        }
        pinnedActivityRef = null
    }

    /**
     * Stops Android Screen Pinning (`stopLockTask()`) when the lock timer finishes or before
     * launching a permitted app.
     */
    fun tryStopScreenPinning(activity: Activity? = null) {
        val block = Runnable { stopScreenPinningImmediate(activity) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block.run()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Synchronizes the `TYPE_APPLICATION_OVERLAY` window with the current active Lock Phone challenge.
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
            resetLaunchGuardState()
            tryStopScreenPinning(context.findActivity())
            dismissShield(appContext)
            return
        }

        if (!Settings.canDrawOverlays(appContext)) return
        if (hasActivePermittedSession(now)) {
            showFloatingPillForPermittedApp(appContext, active, now)
            return
        }

        val appInForeground = runCatching { FocusFlowApplication.instance.isAppInForeground }.getOrDefault(false)
        if (appInForeground) {
            // MainActivity (StrictPhoneLockOverlay) is already visible in the foreground
            removeShieldViewOnly(appContext)
        } else {
            showOrUpdateShield(appContext, active, now)
        }
    }

    /**
     * Keeps the compact Floating Return Pill visible and updated while the user is inside a permitted app.
     */
    fun showFloatingPillForPermittedApp(
        context: Context,
        challenge: Challenge,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val appContext = context.applicationContext
        mainHandler.post {
            showFloatingPillInternal(appContext, challenge, nowMs)
        }
    }

    /**
     * Immediately enforces the lock screen when the user leaves a permitted app or tries to exit Focus Island.
     */
    fun reLockImmediately(context: Context) {
        val appContext = context.applicationContext
        val store = runCatching { FocusFlowApplication.instance.challengeStore }.getOrNull()
        val active = store?.state?.value?.active
        val now = System.currentTimeMillis()
        if (active == null || !active.isLockPhone || now >= active.endAt) {
            resetLaunchGuardState()
            tryStopScreenPinning(context.findActivity())
            dismissShield(appContext)
            return
        }
        launchGraceUntilWallMs = 0L
        activePermittedPackage = null
        val appInForeground = runCatching { FocusFlowApplication.instance.isAppInForeground }.getOrDefault(false)
        if (appInForeground) {
            removeShieldViewOnly(appContext)
        } else {
            showOrUpdateShield(appContext, active, now)
        }
    }

    private fun removeShieldViewOnly(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
            val view = shieldView ?: return@post
            shieldView = null
            shieldParams = null
            ringView = null
            clockTextView = null
            floatingPillTextView = null
            currentMode = OverlayMode.NONE
            renderedChallengeId = -1L
            renderedAllowedPackages = emptySet()
            safeRemoveView(appContext, view)
        }
    }

    /**
     * Removes the system overlay shield completely and unpins the screen when the Lock Phone session finishes.
     */
    fun dismissShield(context: Context) {
        val callerActivity = context.findActivity()
        tryStopScreenPinning(callerActivity)
        resetLaunchGuardState()
        removeShieldViewOnly(context)
    }

    private fun safeRemoveView(context: Context, view: View?) {
        if (view == null) return
        runCatching {
            val wm = context.getSystemService(WindowManager::class.java) ?: return
            if (view.isAttachedToWindow || view.parent != null) {
                wm.removeView(view)
            }
        }
    }

    private fun showFloatingPillInternal(context: Context, challenge: Challenge, nowMs: Long) {
        runCatching {
            if (!Settings.canDrawOverlays(context)) return
            val wm = context.getSystemService(WindowManager::class.java) ?: return
            val remainingMs = (challenge.endAt - nowMs).coerceAtLeast(0L)
            if (remainingMs <= 0L) {
                FocusFlowApplication.instance.challengeStore.expireIfDue()
                ChallengeAlarmReceiver.cancel(context)
                dismissShield(context)
                return
            }

            val clockStr = ChallengeTime.clock(remainingMs)
            val pillLabel = context.getString(R.string.lock_phone_floating_return_pill, clockStr)

            val existing = shieldView
            if (existing != null && currentMode == OverlayMode.FLOATING_PILL && renderedChallengeId == challenge.id) {
                floatingPillTextView?.text = pillLabel
                return
            }

            safeRemoveView(context, existing)
            shieldView = null
            ringView = null
            clockTextView = null

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = context.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(12)
            }

            val scheme = resolveActiveColorScheme(context)
            val pillView = TextView(context).apply {
                text = pillLabel
                setTextColor(scheme.onPrimaryContainer.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(9), dp(18), dp(9))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(999).toFloat()
                    setColor(scheme.primaryContainer.toArgb())
                    setStroke(dp(1), scheme.primary.toArgb())
                }
                elevation = dp(6).toFloat()
                setOnClickListener {
                    mainHandler.post {
                        returnFromPermittedAppToLockScreen(context)
                    }
                }
            }

            wm.addView(pillView, params)
            shieldView = pillView
            shieldParams = params
            floatingPillTextView = pillView
            currentMode = OverlayMode.FLOATING_PILL
            renderedChallengeId = challenge.id
            renderedAllowedPackages = challenge.allowedPackages
        }.onFailure {
            Log.w(TAG, "Could not show floating return pill: ${it.message}")
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
                if (existing != null &&
                    existingParams != null &&
                    currentMode == OverlayMode.FULL_SHIELD &&
                    renderedChallengeId == challenge.id &&
                    renderedAllowedPackages == challenge.allowedPackages
                ) {
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
                        if (existing.isAttachedToWindow) {
                            runCatching { wm.updateViewLayout(existing, existingParams) }
                        }
                    }
                    clockTextView?.text = ChallengeTime.clock(remainingMs)
                    val progress = if (challenge.durationMs > 0L) {
                        (remainingMs.toFloat() / challenge.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    ringView?.setProgress(progress)
                    return@post
                }

                safeRemoveView(appContext, existing)
                shieldView = null
                floatingPillTextView = null

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
                currentMode = OverlayMode.FULL_SHIELD
                renderedChallengeId = challenge.id
                renderedAllowedPackages = challenge.allowedPackages
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
                if (!hasWindowFocus && !hasActivePermittedSession()) {
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

        // 1. Countdown Counter (Ring + Clock)
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

        // 3. Compact Permitted Apps Icon Row below the quote (when allowedPackages is non-empty)
        if (challenge.allowedPackages.isNotEmpty()) {
            val dockScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(20)
                }
            }
            val dockRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(scheme.surfaceContainerHigh.toArgb())
                }
            }
            val pm = context.packageManager
            challenge.allowedPackages.forEach { pkg ->
                val appLabel = resolveAppLabel(context, pm, pkg)
                val appIcon = resolveAppIcon(pm, pkg)
                val itemCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    minimumWidth = dp(64)
                    minimumHeight = dp(60)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        launchPermittedAppSafely(context, pkg)
                    }
                }
                if (appIcon != null) {
                    val iconView = ImageView(context).apply {
                        setImageDrawable(appIcon)
                        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                    }
                    itemCol.addView(iconView)
                } else {
                    val fallbackBadge = TextView(context).apply {
                        text = if (isDialerOrCallPackage(context, pkg)) "📞" else appLabel.take(1).uppercase()
                        setTextColor(scheme.onPrimaryContainer.toArgb())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(scheme.primaryContainer.toArgb())
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                    }
                    itemCol.addView(fallbackBadge)
                }
                val labelTv = TextView(context).apply {
                    text = appLabel
                    setTextColor(scheme.onSurface.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        dp(68),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(4)
                    }
                }
                itemCol.addView(labelTv)
                dockRow.addView(itemCol)
            }
            dockScroll.addView(dockRow)
            contentCol.addView(dockScroll)
        }

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

    internal fun resolveAppLabel(context: Context, pm: PackageManager, pkg: String): String {
        if (pkg == SYNTHETIC_PHONE_DIALER_PKG) {
            return context.getString(R.string.lock_phone_phone_app_default_name)
        }
        return runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
        }.getOrNull() ?: if (isDialerOrCallPackage(context, pkg)) {
            context.getString(R.string.lock_phone_phone_app_default_name)
        } else {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    internal fun resolveAppIcon(pm: PackageManager, pkg: String): android.graphics.drawable.Drawable? {
        if (pkg == SYNTHETIC_PHONE_DIALER_PKG) return null
        return runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
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
