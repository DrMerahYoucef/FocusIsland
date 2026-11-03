package com.example.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.FocusFlowApplication
import com.example.FocusOverlayActivity
import com.example.data.challenge.Challenge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Monitors foreground app changes and enforces:
 * 1. Focus session app launch blocking ([FocusOverlayActivity]).
 * 2. Strict Lock Phone personal challenges via [PhoneLockOverlayManager] (full-screen
 *    `TYPE_APPLICATION_OVERLAY` shield over Recent Apps, Home, and any app launched from the
 *    Notification Drawer) plus [FocusOverlayActivity] fallback.
 */
class AppLaunchMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val blockedPackages: Flow<Set<String>>
) {
    private var job: Job? = null

    @Volatile
    private var currentBlocked: Set<String> = emptySet()

    init {
        scope.launch {
            blockedPackages.collect { currentBlocked = it }
        }
    }

    private fun activePhoneLockChallenge(): Challenge? {
        val store = runCatching { FocusFlowApplication.instance.challengeStore }.getOrNull() ?: return null
        val active = store.state.value.active ?: return null
        if (!active.isLockPhone) return null
        val now = System.currentTimeMillis()
        if (now >= active.endAt) {
            runCatching { store.expireIfDue() }
            PhoneLockOverlayManager.dismissShield(context)
            return null
        }
        return active
    }

    fun isPackageBlocked(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == context.packageName) return false
        val phoneLock = activePhoneLockChallenge()
        if (phoneLock != null) {
            return !PhoneLockOverlayManager.isPackagePermittedForActiveLock(context, pkg, phoneLock)
        }
        return pkg in currentBlocked && pkg !in allowedThisSession
    }

    /**
     * Synchronously checks if the current foreground app is in the blocked list (or blocked by Lock Phone).
     * If it is blocked, hides any floating paused bubble, shows the lock overlay, and returns true
     * so the Pomodoro timer stays running uninterrupted.
     */
    fun checkAndBlockForegroundApp(): Boolean {
        val phoneLock = activePhoneLockChallenge()
        val pkg = foregroundPackage()
        if (phoneLock != null) {
            FloatingFocusBubbleManager.hidePausedBubble(context)
            val isPermitted = when {
                PhoneLockOverlayManager.isWithinLaunchGraceWindow() -> true
                pkg == context.packageName -> false
                pkg != null -> PhoneLockOverlayManager.isPackagePermittedForActiveLock(context, pkg, phoneLock)
                else -> PhoneLockOverlayManager.hasActivePermittedSession() &&
                    !FocusFlowApplication.instance.isAppInForeground
            }
            if (isPermitted) {
                PhoneLockOverlayManager.showFloatingPillForPermittedApp(context, phoneLock)
                return false
            }
            PhoneLockOverlayManager.reLockImmediately(context)
            if (pkg != null && pkg != context.packageName) {
                showOverlay(pkg, isPhoneLock = true)
            }
            return true
        }
        if (pkg == null) return false
        if (isPackageBlocked(pkg)) {
            FloatingFocusBubbleManager.hidePausedBubble(context)
            showOverlay(pkg, isPhoneLock = false)
            return true
        }
        return false
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val power = context.getSystemService(PowerManager::class.java)
            var lastShown: String? = null
            while (isActive) {
                val phoneLock = activePhoneLockChallenge()
                delay(if (phoneLock != null) POLL_LOCK_PHONE_MS else POLL_MS)
                if (power?.isInteractive == false) continue // screen off: nothing to block

                if (phoneLock != null) {
                    FloatingFocusBubbleManager.hidePausedBubble(context)
                    val pkg = foregroundPackage()
                    val permittedNow = when {
                        PhoneLockOverlayManager.isWithinLaunchGraceWindow() -> true
                        pkg == context.packageName -> false
                        pkg != null -> PhoneLockOverlayManager.isPackagePermittedForActiveLock(context, pkg, phoneLock)
                        else -> PhoneLockOverlayManager.hasActivePermittedSession() &&
                            !FocusFlowApplication.instance.isAppInForeground
                    }
                    if (permittedNow) {
                        PhoneLockOverlayManager.showFloatingPillForPermittedApp(context, phoneLock)
                    } else {
                        PhoneLockOverlayManager.reLockImmediately(context)
                        if (pkg != null && pkg != context.packageName) {
                            showOverlay(pkg, isPhoneLock = true)
                        }
                    }
                    continue
                } else if (PhoneLockOverlayManager.isShieldAttached) {
                    PhoneLockOverlayManager.dismissShield(context)
                }

                val pkg = foregroundPackage() ?: continue
                if (pkg == context.packageName) {
                    lastShown = null
                    continue
                }
                val blocked = currentBlocked
                when {
                    pkg !in blocked -> lastShown = null
                    pkg in allowedThisSession -> Unit
                    else -> {
                        FloatingFocusBubbleManager.hidePausedBubble(context)
                        if (PomodoroTimerService.state.value.isAutoPausedByAppSwitch) {
                            runCatching {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PomodoroTimerService::class.java)
                                        .setAction(PomodoroTimerService.ACTION_AUTO_RESUME)
                                )
                            }
                        }
                        if (pkg != lastShown) {
                            lastShown = pkg
                            showOverlay(pkg, isPhoneLock = false)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        allowedThisSession.clear()
        PhoneLockOverlayManager.dismissShield(context)
    }

    internal fun foregroundPackage(): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION") // ACTIVITY_RESUMED (API 29) has the same value
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = event.packageName
            }
        }
        if (last != null) return last
        return runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - LOOKBACK_MS, now)
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        }.getOrNull()
    }

    private fun showOverlay(pkg: String, isPhoneLock: Boolean = false) {
        if (!Settings.canDrawOverlays(context)) return
        runCatching {
            context.startActivity(
                Intent(context, FocusOverlayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(FocusOverlayActivity.EXTRA_BLOCKED_PACKAGE, pkg)
                    .putExtra(FocusOverlayActivity.EXTRA_PHONE_LOCK_MODE, isPhoneLock)
            )
        }
    }

    companion object {
        private const val POLL_MS = 250L
        private const val POLL_LOCK_PHONE_MS = 120L
        private const val LOOKBACK_MS = 60_000L

        /** Apps the user chose to "open anyway"; cleared when the focus phase ends. */
        private val allowedThisSession: MutableSet<String> = ConcurrentHashMap.newKeySet()

        fun allowForCurrentSession(pkg: String) {
            if (pkg.isBlank()) return
            allowedThisSession += pkg
        }
    }
}
