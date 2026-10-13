package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.FocusFlowApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** Silently removes notifications from apps the user blocked, during focus phases only. */
class FocusNotificationListenerService : NotificationListenerService() {

    private var scope: CoroutineScope? = null

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    override fun onListenerConnected() {
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { s ->
            // Live list: changes in the App blocker apply immediately.
            FocusFlowApplication.instance.database.blockedAppDao().getAllBlocked()
                .map { apps -> apps.filter { it.blockNotifications }.mapTo(HashSet()) { it.packageName } }
                .onEach { blockedPackages = it }
                .launchIn(s)
        }
    }

    override fun onListenerDisconnected() {
        scope?.cancel()
        scope = null
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val state = PomodoroTimerService.state.value
        if (!state.isRunning || state.phase != Phase.FOCUS) return
        if (sbn.packageName in blockedPackages) {
            runCatching { cancelNotification(sbn.key) }
        }
    }
}
