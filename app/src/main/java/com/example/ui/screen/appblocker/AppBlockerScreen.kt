package com.example.ui.screen.appblocker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.db.entity.BlockedAppEntity
import com.example.ui.designsystem.BannerTone
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.InfoBanner
import com.example.ui.designsystem.LoadingState
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.extendedColors

/** Which system access each blocking feature needs. */
private data class BlockerAccess(val notifications: Boolean, val usage: Boolean, val overlay: Boolean) {
    val launchReady get() = usage && overlay
}

/**
 * App blocker: per app, "Hide notifications" and/or "Block opening" during focus sessions.
 * Missing system access is shown at the top with one button each, and re-checked when the user
 * comes back from system settings. Apps you block are listed first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockerScreen(
    onBack: () -> Unit,
    viewModel: AppBlockerViewModel = viewModel()
) {
    val context = LocalContext.current
    val blocked by viewModel.blockedApps.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var access by remember { mutableStateOf(readAccess(context)) }
    LifecycleResumeEffect(Unit) { access = readAccess(context); onPauseOrDispose { } }

    val byPackage = remember(blocked) { blocked.associateBy { it.packageName } }
    val visible = remember(apps, byPackage, query) {
        apps.orEmpty()
            .filter { query.isBlank() || it.appName.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<InstalledAppInfo> { it.packageName in byPackage }.thenBy { it.appName.lowercase() })
    }
    val needsNotifAccess = blocked.any { it.blockNotifications } && !access.notifications
    val needsLaunchAccess = blocked.any { it.blockLaunch } && !access.launchReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_app_blocker)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                item {
                    Text(
                        stringResource(R.string.blocker_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
                if (needsNotifAccess || needsLaunchAccess) {
                    item {
                        InfoBanner(
                            text = stringResource(R.string.blocker_not_working),
                            tone = BannerTone.Warning
                        )
                    }
                }
                item { AccessCard(access) { openAccessScreen(context, it) } }
                item {
                    SectionHeader(
                        if (blocked.isEmpty()) stringResource(R.string.blocker_apps)
                        else pluralStringResource(R.plurals.blocker_apps_blocked, blocked.size, blocked.size)
                    )
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.blocker_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.community_clear)) } }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                when {
                    apps == null -> item { Box(Modifier.fillMaxWidth().padding(Spacing.xl)) { LoadingState() } }
                    visible.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.blocker_no_match_title),
                            body = stringResource(R.string.blocker_no_match_body)
                        )
                    }
                    else -> items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            rule = byPackage[app.packageName],
                            onRowClick = { viewModel.toggleAppBlocked(app.packageName, app.appName) },
                            onNotifications = { viewModel.toggleNotifBlock(app.packageName, app.appName, it) },
                            onLaunch = { viewModel.toggleLaunchBlock(app.packageName, app.appName, it) }
                        )
                    }
                }
            }
        }
    }
}

private enum class AccessKind { NOTIFICATIONS, USAGE, OVERLAY }

/** The three system permissions, each with what it enables and a button while missing. */
@Composable
private fun AccessCard(access: BlockerAccess, onOpen: (AccessKind) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            AccessRow(Icons.Rounded.NotificationsOff, R.string.blocker_access_notifications, R.string.blocker_access_notifications_body, access.notifications) { onOpen(AccessKind.NOTIFICATIONS) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AccessRow(Icons.Rounded.QueryStats, R.string.blocker_access_usage, R.string.blocker_access_usage_body, access.usage) { onOpen(AccessKind.USAGE) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AccessRow(Icons.Rounded.Layers, R.string.blocker_access_overlay, R.string.blocker_access_overlay_body, access.overlay) { onOpen(AccessKind.OVERLAY) }
        }
    }
}

@Composable
private fun AccessRow(icon: ImageVector, title: Int, body: Int, granted: Boolean, onAllow: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        trailingContent = {
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.blocker_access_granted), tint = MaterialTheme.extendedColors.success)
            } else {
                FilledTonalButton(onClick = onAllow) { Text(stringResource(R.string.blocker_allow)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** App icon and name, then two chips: hide its notifications, block opening it. */
@Composable
private fun AppRow(
    app: InstalledAppInfo,
    rule: BlockedAppEntity?,
    onNotifications: (Boolean) -> Unit,
    onLaunch: (Boolean) -> Unit,
    onRowClick: () -> Unit = {}
) {
    val notif = rule?.blockNotifications == true
    val launch = rule?.blockLaunch == true
    ListItem(
        modifier = Modifier.clickable(onClick = onRowClick),
        leadingContent = {
            val icon = app.icon
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(Spacing.xl + Spacing.xs))
            else Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(Spacing.xl + Spacing.xs))
        },
        headlineContent = { Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilterChip(
                    selected = notif,
                    onClick = { onNotifications(!notif) },
                    label = { Text(stringResource(R.string.blocker_hide_notifications), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null, modifier = Modifier.size(Spacing.iconSm - Spacing.xxs)) }
                )
                FilterChip(
                    selected = launch,
                    onClick = { onLaunch(!launch) },
                    label = { Text(stringResource(R.string.blocker_block_opening), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(Spacing.iconSm - Spacing.xxs)) }
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (notif || launch) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent
        )
    )
}

// ---------------------------------------------------------------- system access

private fun readAccess(context: Context) = BlockerAccess(
    notifications = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true,
    usage = hasUsageAccess(context),
    overlay = Settings.canDrawOverlays(context)
)

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun openAccessScreen(context: Context, kind: AccessKind) {
    val intent = when (kind) {
        AccessKind.NOTIFICATIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        AccessKind.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        AccessKind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Some phones lack the specific screen: fall back to the app's system page.
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@ThemePreviews
@Composable
private fun AppBlockerPiecesPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AccessCard(BlockerAccess(notifications = true, usage = false, overlay = false)) {}
            AppRow(InstalledAppInfo("com.a", "Instagram", null), BlockedAppEntity("com.a", "Instagram", "", blockNotifications = true, blockLaunch = false), {}, {})
            AppRow(InstalledAppInfo("com.b", "YouTube", null), null, {}, {})
        }
    }
}
