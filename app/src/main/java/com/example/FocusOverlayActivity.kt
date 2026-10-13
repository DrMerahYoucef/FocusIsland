package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.example.service.AppLaunchMonitor
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode

/**
 * Shown over an app blocked during focus. "Back to focus" opens Focus Island on the Focus tab;
 * system back does the same (it used to reveal the blocked app). "Open anyway" lets the app
 * through until the current focus phase ends.
 */
class FocusOverlayActivity : ComponentActivity() {

    private var blockedPackage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (!android.provider.Settings.canDrawOverlays(this)) {
            finish()
            return
        }
        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()

        val prefs = getSharedPreferences("focusflow_prefs", MODE_PRIVATE)
        val themeMode = ThemeMode.from(prefs.getString("theme_mode", null))
        val dynamicColor = prefs.getBoolean("dynamic_color", false)

        setContent {
            FocusIslandTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val appName = remember(blockedPackage) { appLabel(blockedPackage) }
                BackHandler(onBack = ::backToFocus)
                FocusOverlayScreen(
                    appName = appName ?: stringResource(R.string.blocked_this_app),
                    onBackToFocus = ::backToFocus,
                    onOpenAnyway = {
                        AppLaunchMonitor.allowForCurrentSession(blockedPackage)
                        finish()
                    }
                )
            }
        }
    }

    /** singleTop: another blocked app opened while this screen is still up. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
    }

    private fun appLabel(pkg: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun backToFocus() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TARGET_SCREEN, "focus")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }
}

/** Calm, full-screen reminder: lock badge, which app is blocked, a short nudge, two choices. */
@Composable
fun FocusOverlayScreen(appName: String, onBackToFocus: () -> Unit, onOpenAnyway: () -> Unit) {
    val nudges = stringArrayResource(R.array.blocked_nudges)
    val nudge = remember { nudges.random() } // stable while the screen is shown
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.lg), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = Spacing.formMaxWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(Spacing.illustration)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(Spacing.xxl))
                    }
                }
                Text(
                    stringResource(R.string.blocked_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.blocked_body, appName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    nudge,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBackToFocus, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md).height(Spacing.buttonHeight)) {
                    Text(stringResource(R.string.blocked_back_to_focus))
                }
                TextButton(onClick = onOpenAnyway) { Text(stringResource(R.string.blocked_open_anyway)) }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun FocusOverlayPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        FocusOverlayScreen(appName = "Instagram", onBackToFocus = {}, onOpenAnyway = {})
    }
}
