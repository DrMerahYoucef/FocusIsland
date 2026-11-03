package com.example.ui.screen.challenge

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.FocusFlowApplication
import com.example.R
import com.example.data.challenge.Challenge
import com.example.data.challenge.ChallengeQuotes
import com.example.service.PhoneLockOverlayManager
import com.example.service.findActivity
import com.example.ui.designsystem.PhaseRing
import com.example.ui.designsystem.RingClock
import com.example.ui.theme.Spacing
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APP_ICON_PX = 72

internal data class PermittedAppEntry(
    val packageName: String,
    val appName: String,
    val iconBitmap: ImageBitmap?,
    val isPhoneDialer: Boolean = false
)

private object PermittedAppCatalogCache {
    @Volatile
    var entries: List<PermittedAppEntry>? = null

    fun findByPackage(pkg: String): PermittedAppEntry? =
        entries?.firstOrNull { it.packageName == pkg }
}

internal fun loadPermittedAppCatalog(context: Context): List<PermittedAppEntry> {
    PermittedAppCatalogCache.entries?.let { cached ->
        if (cached.isNotEmpty()) return cached
    }
    val pm = context.packageManager
    val selfPkg = context.packageName
    val byPackage = LinkedHashMap<String, PermittedAppEntry>()

    // 1. Ensure the system Phone / Dialer app is always discoverable & safe
    val defaultDialerPkg = runCatching {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull()?.takeIf { it.isNotBlank() && it != selfPkg }
        ?: runCatching {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            pm.queryIntentActivities(dialIntent, PackageManager.MATCH_ALL)
                .firstOrNull { it.activityInfo?.packageName != null && it.activityInfo.packageName != selfPkg }
                ?.activityInfo?.packageName
        }.getOrNull()
        ?: PhoneLockOverlayManager.SYNTHETIC_PHONE_DIALER_PKG

    val phoneLabel = PhoneLockOverlayManager.resolveAppLabel(context, pm, defaultDialerPkg)
    val phoneBitmap = runCatching {
        PhoneLockOverlayManager.resolveAppIcon(pm, defaultDialerPkg)
            ?.toBitmap(APP_ICON_PX, APP_ICON_PX)
            ?.asImageBitmap()
    }.getOrNull()
    byPackage[defaultDialerPkg] = PermittedAppEntry(
        packageName = defaultDialerPkg,
        appName = phoneLabel,
        iconBitmap = phoneBitmap,
        isPhoneDialer = true
    )

    // 2. Query all launchable installed applications and pre-render small ImageBitmaps off the UI thread
    runCatching {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == selfPkg || byPackage.containsKey(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString().trim() }.getOrDefault("")
                .ifBlank { PhoneLockOverlayManager.resolveAppLabel(context, pm, pkg) }
            val bitmap = runCatching {
                info.loadIcon(pm)?.toBitmap(APP_ICON_PX, APP_ICON_PX)?.asImageBitmap()
            }.getOrNull()
            val isDialer = PhoneLockOverlayManager.isDialerOrCallPackage(context, pkg)
            byPackage[pkg] = PermittedAppEntry(
                packageName = pkg,
                appName = label,
                iconBitmap = bitmap,
                isPhoneDialer = isDialer
            )
        }
    }

    val sorted = byPackage.values.sortedWith(
        compareByDescending<PermittedAppEntry> { it.isPhoneDialer }
            .thenBy { it.appName.lowercase() }
    )
    PermittedAppCatalogCache.entries = sorted
    return sorted
}

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
 * Minimal, distraction-free full-screen phone lock UI displaying the countdown counter,
 * the session motivational quote, and (when configured) a compact Permitted Apps icon row
 * directly below the quote card.
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Intercept Back press strictly: no early exit allowed
    BackHandler(enabled = true) {
        // Intentionally no-op
    }

    // Enter sticky immersive mode (hide status bar and navigation bar) while the lock is active,
    // and coordinate with PhoneLockOverlayManager so we never stack duplicate full-screen surfaces.
    DisposableEffect(view, lifecycleOwner) {
        val activity = view.context.findActivity() ?: context.findActivity()
        activity?.let { PhoneLockOverlayManager.registerHostActivity(it) }
        PhoneLockOverlayManager.onLockScreenInForeground(context)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PhoneLockOverlayManager.onLockScreenInForeground(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val window = activity?.window
        val controller = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else null
        controller?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            val active = runCatching { FocusFlowApplication.instance.challengeStore.state.value.active }.getOrNull()
            if (active == null || !active.isLockPhone || System.currentTimeMillis() >= active.endAt) {
                PhoneLockOverlayManager.tryStopScreenPinning(activity)
                PhoneLockOverlayManager.dismissShield(context)
            }
        }
    }

    // Only engage OS-level Screen Pinning (startLockTask) when no external apps are permitted;
    // when permitted apps are selected, OS LockTask blocks external app launches, so we unpin
    // and rely on PhoneLockOverlayManager + AppLaunchMonitor.
    LaunchedEffect(challenge.id, challenge.allowedPackages) {
        val activity = view.context.findActivity() ?: context.findActivity()
        if (activity != null) {
            if (challenge.allowedPackages.isEmpty()) {
                PhoneLockOverlayManager.tryStartScreenPinning(activity)
            } else {
                PhoneLockOverlayManager.tryStopScreenPinning(activity)
            }
        }
    }

    val remainingMs = (challenge.endAt - nowMs).coerceAtLeast(0L)
    LaunchedEffect(remainingMs <= 0L) {
        if (remainingMs <= 0L) {
            val activity = view.context.findActivity() ?: context.findActivity()
            PhoneLockOverlayManager.tryStopScreenPinning(activity)
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
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically)
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

            // 3. Compact Permitted Apps Icon Row below the quote
            if (challenge.allowedPackages.isNotEmpty()) {
                PermittedAppsCompactRow(
                    allowedPackages = challenge.allowedPackages,
                    onLaunchApp = { pkg ->
                        PhoneLockOverlayManager.launchPermittedAppSafely(context, pkg)
                    }
                )
            }
        }
    }
}

@Composable
internal fun PermittedAppsCompactRow(
    allowedPackages: Set<String>,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val appEntries by produceState(
        initialValue = allowedPackages.map { pkg ->
            PermittedAppCatalogCache.findByPackage(pkg) ?: PermittedAppEntry(
                packageName = pkg,
                appName = PhoneLockOverlayManager.resolveAppLabel(context, pm, pkg),
                iconBitmap = null,
                isPhoneDialer = PhoneLockOverlayManager.isDialerOrCallPackage(context, pkg)
            )
        },
        key1 = allowedPackages,
        key2 = context
    ) {
        value = withContext(Dispatchers.IO) {
            allowedPackages.map { pkg ->
                PermittedAppCatalogCache.findByPackage(pkg) ?: run {
                    val bitmap = runCatching {
                        PhoneLockOverlayManager.resolveAppIcon(pm, pkg)
                            ?.toBitmap(APP_ICON_PX, APP_ICON_PX)
                            ?.asImageBitmap()
                    }.getOrNull()
                    PermittedAppEntry(
                        packageName = pkg,
                        appName = PhoneLockOverlayManager.resolveAppLabel(context, pm, pkg),
                        iconBitmap = bitmap,
                        isPhoneDialer = PhoneLockOverlayManager.isDialerOrCallPackage(context, pkg)
                    )
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .widthIn(max = Spacing.formMaxWidth)
            .testTag("phone_lock_permitted_apps_row")
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appEntries.forEach { entry ->
                Surface(
                    onClick = { onLaunchApp(entry.packageName) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .widthIn(min = 64.dp)
                        .testTag("permitted_app_item_${entry.packageName}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PermittedAppIconBadge(entry = entry, sizeDp = 34)
                        Text(
                            text = entry.appName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 72.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermittedAppIconBadge(entry: PermittedAppEntry, sizeDp: Int = 36) {
    val bitmap = entry.iconBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = entry.appName,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(sizeDp.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (entry.isPhoneDialer) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = entry.appName,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size((sizeDp * 0.55f).dp)
                    )
                } else {
                    Text(
                        text = entry.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Permitted Apps selector card shown inside [NewChallengeSheet] when Lock Phone mode is active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LockPermittedAppsSection(
    selectedPackages: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // Warm up the app catalog in the background so tapping "Choose apps" opens with zero delay
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            loadPermittedAppCatalog(context)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("lock_phone_permitted_apps_card")
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lock_phone_necessary_apps_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedPackages.isEmpty()) {
                            stringResource(R.string.lock_phone_no_apps_selected)
                        } else {
                            pluralStringResource(
                                R.plurals.lock_phone_necessary_apps_count,
                                selectedPackages.size,
                                selectedPackages.size
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                FilledTonalButton(
                    onClick = { showPicker = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("lock_phone_choose_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.lock_phone_choose_apps_button),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (selectedPackages.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedPackages.forEach { pkg ->
                        val label = remember(pkg, context) {
                            PermittedAppCatalogCache.findByPackage(pkg)?.appName
                                ?: PhoneLockOverlayManager.resolveAppLabel(context, pm, pkg)
                        }
                        InputChip(
                            selected = true,
                            onClick = { onSelectionChanged(selectedPackages - pkg) },
                            label = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.testTag("selected_permitted_chip_$pkg")
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.lock_phone_permitted_apps_saved_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showPicker) {
        PermittedAppsPickerDialog(
            initialSelected = selectedPackages,
            onConfirm = { updated ->
                onSelectionChanged(updated)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
internal fun PermittedAppsPickerDialog(
    initialSelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val catalog by produceState(
        initialValue = PermittedAppCatalogCache.entries.orEmpty(),
        key1 = context
    ) {
        if (value.isEmpty()) {
            value = withContext(Dispatchers.IO) {
                loadPermittedAppCatalog(context)
            }
        }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var draftSelected by remember { mutableStateOf(initialSelected) }

    val filteredCatalog = remember(catalog, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) catalog
        else catalog.filter {
            it.appName.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.lock_phone_picker_title),
                    style = MaterialTheme.typography.titleLarge
                )
                if (draftSelected.isNotEmpty()) {
                    TextButton(
                        onClick = { draftSelected = emptySet() },
                        modifier = Modifier.testTag("permitted_picker_clear_button")
                    ) {
                        Text(stringResource(R.string.lock_phone_picker_clear))
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.lock_phone_picker_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permitted_picker_search_input")
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("permitted_picker_list")
                ) {
                    items(filteredCatalog, key = { it.packageName }) { entry ->
                        val isChecked = entry.packageName in draftSelected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draftSelected = if (isChecked) {
                                        draftSelected - entry.packageName
                                    } else {
                                        draftSelected + entry.packageName
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("permitted_picker_row_${entry.packageName}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            PermittedAppIconBadge(entry = entry, sizeDp = 36)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (entry.isPhoneDialer) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    draftSelected = if (checked) {
                                        draftSelected + entry.packageName
                                    } else {
                                        draftSelected - entry.packageName
                                    }
                                }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(draftSelected) },
                modifier = Modifier.testTag("permitted_picker_save_button")
            ) {
                Text(stringResource(R.string.lock_phone_picker_save, draftSelected.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
