package com.example.ui.screen.challenge

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.challenge.Challenge
import com.example.data.challenge.ChallengeQuotes
import com.example.service.PhoneLockOverlayManager
import com.example.ui.designsystem.PhaseRing
import com.example.ui.designsystem.RingClock
import com.example.ui.theme.Spacing
import com.example.ui.theme.extendedColors

internal fun hasUsageAccessPermission(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = ops.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

internal fun hasOverlayPermission(context: Context): Boolean =
    Settings.canDrawOverlays(context)

internal fun hasNotificationListenerPermission(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

/**
 * Minimal, distraction-free full-screen phone lock UI displaying ONLY the countdown counter
 * and the single session motivational quote.
 */
@Composable
fun StrictPhoneLockOverlay(
    challenge: Challenge,
    nowMs: Long,
    onTimeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Intercept Back press strictly: no early exit allowed
    BackHandler(enabled = true) {
        // Intentionally no-op
    }

    // Enter sticky immersive mode (hide status bar and navigation bar) while the lock is active
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else null
        controller?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Automatically request Android Screen Pinning when the lock screen opens
    LaunchedEffect(challenge.id) {
        (view.context as? Activity)?.let { PhoneLockOverlayManager.tryStartScreenPinning(it) }
    }

    val remainingMs = (challenge.endAt - nowMs).coerceAtLeast(0L)
    LaunchedEffect(remainingMs <= 0L) {
        if (remainingMs <= 0L) {
            (view.context as? Activity)?.let { PhoneLockOverlayManager.tryStopScreenPinning(it) }
            PhoneLockOverlayManager.dismissShield(context)
            onTimeUp()
        }
    }

    val clockText = ChallengeTime.clock(remainingMs)
    val quoteText = challenge.quoteText.ifBlank { ChallengeQuotes.quotes.first().quote }
    val quoteAuthor = challenge.quoteAuthor.ifBlank { ChallengeQuotes.quotes.first().author }

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("strict_phone_lock_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically)
        ) {
            // 1. Countdown Counter
            PhaseRing(
                progress = if (challenge.durationMs > 0L) {
                    remainingMs.toFloat() / challenge.durationMs.toFloat()
                } else 0f,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(Spacing.focusRingCompact)
                    .testTag("phone_lock_countdown_clock")
                    .semantics(mergeDescendants = true) {
                        contentDescription = clockText
                    }
            ) {
                RingClock(clockText, MaterialTheme.colorScheme.onSurface)
            }

            // 2. Single Random Motivational Quote Card for this lock session
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .widthIn(max = Spacing.formMaxWidth)
                    .fillMaxWidth()
                    .testTag("phone_lock_quote_card")
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "“$quoteText”",
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("phone_lock_quote_text")
                    )
                    if (quoteAuthor.isNotBlank()) {
                        Text(
                            text = "— $quoteAuthor",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact permission readiness check shown in the Lock Phone setup sheet only if
 * Usage Access, Display Over Other Apps, or Notification Access is not yet granted.
 */
@Composable
internal fun LockPermissionsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usageGranted = remember(resumeTick) { hasUsageAccessPermission(context) }
    val overlayGranted = remember(resumeTick) { hasOverlayPermission(context) }
    val notifGranted = remember(resumeTick) { hasNotificationListenerPermission(context) }

    if (!usageGranted || !overlayGranted || !notifGranted) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.lock_phone_permissions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.lock_phone_permissions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                PermissionRowCompact(
                    title = stringResource(R.string.blocker_access_usage),
                    granted = usageGranted,
                    onAllow = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                )
                PermissionRowCompact(
                    title = stringResource(R.string.blocker_access_overlay),
                    granted = overlayGranted,
                    onAllow = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                )
                PermissionRowCompact(
                    title = stringResource(R.string.blocker_access_notifications),
                    granted = notifGranted,
                    onAllow = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRowCompact(
    title: String,
    granted: Boolean,
    onAllow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.blocker_access_granted),
                    tint = MaterialTheme.extendedColors.success,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.blocker_access_granted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.extendedColors.success
                )
            }
        } else {
            FilledTonalButton(
                onClick = onAllow,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.blocker_allow),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
