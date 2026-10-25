package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.PomodoroTimerService
import com.example.ui.navigation.AppNavGraph
import com.example.ui.navigation.NavRequest
import com.example.ui.navigation.TopLevel
import com.example.ui.screen.settings.SettingsViewModel
import com.example.ui.theme.AppIcon
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStyle
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Latest deep-link request (notification / widget / app shortcut). */
    private val navRequest = mutableStateOf<NavRequest?>(null)
    private var openedFocusModeForCurrentLock = false

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The timer still works without it; Settings explains how to enable notifications. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableShowOnLockScreen()
        AppLanguage.applyLocale(this)
        enableEdgeToEdge()

        if (savedInstanceState == null) handleDeepLink(intent)
        requestNotificationPermissionIfNeeded()

        // Background statistics sync (never throws; app-scoped so rotation can't cancel it).
        FocusFlowApplication.instance.let { app ->
            app.applicationScope.launch { app.sessionRepository.syncWithFirestore() }
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.state.collectAsState()
            FocusIslandTheme(
                themeMode = ThemeMode.from(settings.themeMode),
                dynamicColor = settings.dynamicColor,
                style = settings.appStyle,
                language = settings.appLanguage
            ) {
                AppNavGraph(
                    settingsViewModel = settingsViewModel,
                    navRequest = navRequest.value,
                    onConsumeNavRequest = { navRequest.value = null }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val isLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val timerState = PomodoroTimerService.state.value
        if (!isLocked) {
            openedFocusModeForCurrentLock = false
            if (navRequest.value?.openFocusMode == true) {
                navRequest.value = null
            }
        } else if (!openedFocusModeForCurrentLock && (timerState.isRunning || !timerState.isUntouched)) {
            openedFocusModeForCurrentLock = true
            navRequest.value = NavRequest(tab = TopLevel.FOCUS, openFocusMode = true)
        }
    }

    override fun onResume() {
        super.onResume()
        val isLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (!isLocked) {
            openedFocusModeForCurrentLock = false
            if (navRequest.value?.openFocusMode == true) {
                navRequest.value = null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableShowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    override fun onStop() {
        super.onStop()
        // The launcher icon follows the style; switched once the app is out of sight.
        if (!isChangingConfigurations) AppIcon.apply(this, AppStyle.read(this))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Supports `focusisland://app/<focus|study|progress|exams|community>` and the legacy
     * `target_screen` extra used by the review reminder notification.
     */
    private fun handleDeepLink(intent: Intent?) {
        intent ?: return
        val key = intent.data?.takeIf { it.scheme == DEEP_LINK_SCHEME }?.lastPathSegment
            ?: intent.getStringExtra(EXTRA_TARGET_SCREEN)
        val tab = TopLevel.fromKey(key) ?: return
        navRequest.value = NavRequest(tab = tab, progressTab = if (key == "exams") 1 else 0)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val DEEP_LINK_SCHEME = "focusisland"
        const val EXTRA_TARGET_SCREEN = "target_screen"
    }
}
