package com.example.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.example.FocusOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows [FocusOverlayActivity] when a launch-blocked app comes to the foreground during focus.
 * Runs inside the timer's foreground service (no second foreground service, which Android 12+
 * would refuse to start from the background when a break ends).
 */
class AppLaunchMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val blockedPackages: Flow<Set<String>>
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var blocked = emptySet<String>()
            launch { blockedPackages.collect { blocked = it } } // live: edits apply mid-session
            val power = context.getSystemService(PowerManager::class.java)
            var lastShown: String? = null
            while (isActive) {
                delay(POLL_MS)
                if (power?.isInteractive == false) continue // screen off: nothing to block
                val pkg = foregroundPackage() ?: continue
                when {
                    pkg !in blocked -> lastShown = null
                    pkg == lastShown || pkg in allowedThisSession -> Unit
                    else -> {
                        lastShown = pkg
                        showOverlay(pkg)
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        allowedThisSession.clear()
    }

    private fun foregroundPackage(): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION") // ACTIVITY_RESUMED (API 29) has the same value
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = event.packageName
        }
        return last
    }

    private fun showOverlay(pkg: String) {
        if (!Settings.canDrawOverlays(context)) return
        context.startActivity(
            Intent(context, FocusOverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(FocusOverlayActivity.EXTRA_BLOCKED_PACKAGE, pkg)
        )
    }

    companion object {
        private const val POLL_MS = 1_000L
        private const val LOOKBACK_MS = 5_000L

        /** Apps the user chose to "open anyway"; cleared when the focus phase ends. */
        private val allowedThisSession: MutableSet<String> = ConcurrentHashMap.newKeySet()

        fun allowForCurrentSession(pkg: String) {
            if (pkg.isNotBlank()) allowedThisSession += pkg
        }
    }
}
