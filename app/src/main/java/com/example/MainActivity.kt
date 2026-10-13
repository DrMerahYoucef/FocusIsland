package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.example.ui.navigation.AppNavGraph
import com.example.ui.navigation.NavRequest
import com.example.ui.navigation.TopLevel
import com.example.ui.screen.settings.SettingsViewModel
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Latest deep-link request (notification / widget / app shortcut). */
    private val navRequest = mutableStateOf<NavRequest?>(null)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The timer still works without it; Settings explains how to enable notifications. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
                dynamicColor = settings.dynamicColor
            ) {
                AppNavGraph(settingsViewModel = settingsViewModel, navRequest = navRequest.value)
            }
        }
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
