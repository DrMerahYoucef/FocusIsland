package com.example.ui.screen.settings

import android.app.NotificationManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.example.ui.theme.AppStyle
import com.example.ui.theme.LocalFocusStyle
import com.example.ui.components.IslandRenderer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Facebook
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.BuildConfig
import com.example.FocusFlowApplication
import com.example.R
import com.example.data.auth.AccountState
import com.example.data.backup.BackupTaskState
import com.example.data.backup.CardBackupStateHolder
import com.example.data.repository.GeminiOcrEngine
import com.example.service.CardBackupService
import com.example.service.DayNightLiveWallpaperService
import com.example.ui.components.WallpaperHelper
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.navigation.Routes
import com.example.ui.screen.auth.AuthMode
import com.example.ui.screen.revisions.RevisionsViewModel
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WallpaperTheme
import com.example.widget.ExamCountdownWidgetReceiver
import com.example.widget.ExamMatrixWidgetReceiver
import kotlinx.coroutines.launch

private const val DEVELOPER_PHONE = "+213558460474"
private const val DEVELOPER_WHATSAPP = "https://wa.me/213558460474"
private const val DEVELOPER_FACEBOOK = "https://www.facebook.com/youcef.Merahh/"
private const val GEMINI_KEY_PAGE = "https://aistudio.google.com/app/apikey"

/**
 * All settings on one scrolling page, grouped: Account, Focus timer, Study & AI, Appearance,
 * Wallpaper, Home screen, Backup & data, About. Complex edits open dialogs; actions report
 * their result in a snackbar. Shown inside the Settings route's app bar (see AppNavGraph).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun notify(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val revisions: RevisionsViewModel = viewModel()
    val study by revisions.uiState.collectAsStateWithLifecycle()
    val srs = study.srsSettings
    val account by FocusFlowApplication.instance.authRepository.state.collectAsStateWithLifecycle()
    val backup by CardBackupStateHolder.state.collectAsStateWithLifecycle()

    val app = FocusFlowApplication.instance
    val geminiInfo by app.geminiModels.info.collectAsState()
    val examCount by remember { app.database.examDao().getAllExams() }.collectAsState(initial = emptyList())
    val blocked by remember { app.database.blockedAppDao().getAllBlocked() }.collectAsState(initial = emptyList())
    val sessionCount by remember { app.sessionRepository.getSessionCount(0L, Long.MAX_VALUE) }.collectAsState(initial = 0)

    // DND access can change while the user is in system settings.
    var dndAllowed by remember { mutableStateOf(hasDndAccess(context)) }
    LifecycleResumeEffect(Unit) { dndAllowed = hasDndAccess(context); onPauseOrDispose { } }

    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var importUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var applyingWallpaper by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val msgKeySaved = stringResource(R.string.settings_gemini_key_saved)
    val msgKeyRemoved = stringResource(R.string.settings_gemini_key_removed)
    val msgBackupSaved = stringResource(R.string.backup_file_saved)
    val msgFailed = stringResource(R.string.study_media_failed)
    val msgNoScreen = stringResource(R.string.wallpaper_pick_screen)
    val msgWallpaperDone = stringResource(R.string.wallpaper_applied)
    val msgPinUnsupported = stringResource(R.string.widget_pin_unsupported)
    val msgPromptSaved = stringResource(R.string.settings_prompt_saved)

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = (backup as? BackupTaskState.ExportCompleted)?.backupFile
        if (uri != null && file != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
            }.isSuccess
            notify(if (ok) msgBackupSaved else msgFailed)
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { importUri = uri; dialog = SettingsDialog.IMPORT }
    }

    val wallpaperDark = if (state.followSystemTheme) {
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    } else state.wallpaperTheme == WallpaperTheme.DARK

    fun applyWallpaper(onDone: (Boolean, String?) -> Unit) {
        val dark = wallpaperDark
        scope.launch {
            val error = WallpaperHelper.setIslandWallpaper(
                context = context,
                theme = if (dark) WallpaperTheme.DARK else WallpaperTheme.LIGHT,
                setHomeScreen = state.wallpaperHomeScreen,
                setLockScreen = state.wallpaperLockScreen,
                sessions = sessionCount
            )
            onDone(error == null, error)
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // ------------------------------------------------ account
            SettingsGroup(stringResource(R.string.settings_account)) {
                when (val a = account) {
                    is AccountState.SignedIn -> {
                        SettingsRow(
                            Icons.Rounded.AccountCircle,
                            title = a.displayName?.takeIf { it.isNotBlank() } ?: a.email.orEmpty(),
                            summary = if (a.displayName.isNullOrBlank()) stringResource(R.string.settings_signed_in) else a.email
                        )
                        SettingsDivider()
                        SettingsRow(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.settings_sign_out), onClick = { dialog = SettingsDialog.SIGN_OUT })
                    }
                    else -> {
                        SettingsRow(
                            Icons.Rounded.PersonAdd,
                            title = stringResource(R.string.settings_guest_title),
                            summary = stringResource(R.string.settings_guest_body),
                            onClick = { navController?.navigate(Routes.auth(AuthMode.CREATE_ACCOUNT)) },
                            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
                        )
                        SettingsDivider()
                        SettingsRow(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.community_guest_sign_in), onClick = { navController?.navigate(Routes.auth(AuthMode.SIGN_IN)) })
                    }
                }
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.DeleteForever,
                    title = stringResource(if (account is AccountState.SignedIn) R.string.settings_delete_account else R.string.settings_erase_device),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { dialog = SettingsDialog.DELETE_ACCOUNT },
                    trailing = if (deleting) { { CircularProgressIndicator(strokeWidth = Spacing.progressStroke, modifier = Modifier.size(Spacing.iconSm)) } } else null
                )
            }

            // ------------------------------------------------ focus timer
            SettingsGroup(stringResource(R.string.settings_focus)) {
                val min = { m: Int -> context.resources.getQuantityString(R.plurals.settings_minutes, m, m) }
                StepperRow(Icons.Rounded.Timer, stringResource(R.string.settings_focus_length), min(state.focusMin),
                    // Steps of 5 minutes, fine-grained below 5.
                    { viewModel.updateFocusMin(if (state.focusMin > 5) state.focusMin - 5 else state.focusMin - 1) },
                    { viewModel.updateFocusMin(if (state.focusMin >= 5) state.focusMin + 5 else state.focusMin + 1) },
                    canDecrease = state.focusMin > SettingsState.FOCUS_RANGE.first, canIncrease = state.focusMin < SettingsState.FOCUS_RANGE.last)
                SettingsDivider()
                StepperRow(Icons.Rounded.Coffee, stringResource(R.string.settings_short_break), min(state.shortBreakMin),
                    { viewModel.updateShortBreakMin(state.shortBreakMin - 1) }, { viewModel.updateShortBreakMin(state.shortBreakMin + 1) },
                    canDecrease = state.shortBreakMin > SettingsState.BREAK_RANGE.first, canIncrease = state.shortBreakMin < SettingsState.BREAK_RANGE.last)
                SettingsDivider()
                StepperRow(Icons.Rounded.Landscape, stringResource(R.string.settings_long_break), min(state.longBreakMin),
                    { viewModel.updateLongBreakMin(state.longBreakMin - 1) }, { viewModel.updateLongBreakMin(state.longBreakMin + 1) },
                    canDecrease = state.longBreakMin > SettingsState.BREAK_RANGE.first, canIncrease = state.longBreakMin < SettingsState.BREAK_RANGE.last)
                SettingsDivider()
                StepperRow(Icons.Rounded.Repeat, stringResource(R.string.settings_rounds), state.sessionsBeforeLong.toString(),
                    { viewModel.updateSessionsBeforeLong(state.sessionsBeforeLong - 1) }, { viewModel.updateSessionsBeforeLong(state.sessionsBeforeLong + 1) },
                    canDecrease = state.sessionsBeforeLong > SettingsState.ROUNDS_RANGE.first, canIncrease = state.sessionsBeforeLong < SettingsState.ROUNDS_RANGE.last,
                    summary = stringResource(R.string.settings_rounds_body))
                SettingsDivider()
                SwitchRow(
                    Icons.Rounded.DoNotDisturbOn,
                    stringResource(R.string.settings_dnd),
                    stringResource(if (state.blockNotifications && !dndAllowed) R.string.settings_dnd_needs_access else R.string.settings_dnd_body),
                    checked = state.blockNotifications,
                    onCheckedChange = { on ->
                        viewModel.updateBlockNotifications(on)
                        if (on && !dndAllowed) openDndAccess(context)
                    }
                )
                SettingsDivider()
                SwitchRow(Icons.Rounded.Vibration, stringResource(R.string.settings_vibrate), stringResource(R.string.settings_vibrate_body), state.vibrateOnComplete, viewModel::updateVibrateOnComplete)
                SettingsDivider()
                SwitchRow(Icons.Rounded.Fullscreen, stringResource(R.string.settings_auto_focus_mode), stringResource(R.string.settings_auto_focus_mode_body), state.autoFocusMode, viewModel::updateAutoFocusMode)
                SettingsDivider()
                StepperRow(
                    Icons.Rounded.Shuffle, stringResource(R.string.settings_sound_rotation),
                    if (state.ambientRotationMin == 0) stringResource(R.string.settings_off) else min(state.ambientRotationMin),
                    { viewModel.updateAmbientRotationMin(state.ambientRotationMin - 1) }, { viewModel.updateAmbientRotationMin(state.ambientRotationMin + 1) },
                    canDecrease = state.ambientRotationMin > SettingsState.ROTATION_RANGE.first, canIncrease = state.ambientRotationMin < SettingsState.ROTATION_RANGE.last,
                    summary = stringResource(R.string.settings_sound_rotation_body)
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.Block, stringResource(R.string.settings_app_blocker),
                    pluralStringResource(R.plurals.settings_apps_blocked, blocked.size, blocked.size),
                    onClick = { navController?.navigate(Routes.APP_BLOCKER) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
                )
            }

            // ------------------------------------------------ study & AI
            SettingsGroup(stringResource(R.string.settings_study)) {
                StepperRow(
                    Icons.Rounded.Style, stringResource(R.string.settings_new_cards), srs.newCardsPerDay.toString(),
                    { revisions.updateSrsSettings(srs.copy(newCardsPerDay = (srs.newCardsPerDay - 5).coerceAtLeast(5))) },
                    { revisions.updateSrsSettings(srs.copy(newCardsPerDay = (srs.newCardsPerDay + 5).coerceAtMost(100))) },
                    canDecrease = srs.newCardsPerDay > 5, canIncrease = srs.newCardsPerDay < 100,
                    summary = stringResource(R.string.settings_new_cards_body)
                )
                SettingsDivider()
                SwitchRow(
                    Icons.Rounded.NotificationsActive, stringResource(R.string.settings_reminder),
                    stringResource(R.string.settings_reminder_body, "%02d:%02d".format(srs.reminderHour, srs.reminderMinute)),
                    checked = srs.notificationsEnabled,
                    onCheckedChange = { revisions.updateSrsSettings(srs.copy(notificationsEnabled = it)) }
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.Key, stringResource(R.string.settings_gemini_key),
                    stringResource(if (srs.geminiApiKey.isBlank()) R.string.settings_gemini_key_none else R.string.settings_gemini_key_set),
                    onClick = { dialog = SettingsDialog.GEMINI_KEY }
                )
                SettingsDivider()
                SettingsRow(Icons.Rounded.Memory, stringResource(R.string.settings_gemini_models), geminiModelLabel(srs.selectedGeminiModel), onClick = { dialog = SettingsDialog.GEMINI_MODELS })
                SettingsDivider()
                SettingsRow(
                    Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.settings_prompt),
                    stringResource(if (srs.customPromptOverride != null) R.string.settings_prompt_custom else R.string.settings_prompt_default),
                    onClick = { dialog = SettingsDialog.PROMPT }
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.Event, stringResource(R.string.progress_tab_exams),
                    pluralStringResource(R.plurals.settings_exams_count, examCount.size, examCount.size),
                    onClick = { navController?.navigate(Routes.EXAMS) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
                )
            }

            // ------------------------------------------------ appearance & privacy
            SettingsGroup(stringResource(R.string.settings_appearance)) {
                SettingsRow(
                    Icons.Rounded.AutoAwesome, stringResource(R.string.style_title),
                    stringResource(state.appStyle.label),
                    onClick = { navController?.navigate(Routes.STYLE) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
                )
                SettingsDivider()
                Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Icon(Icons.Rounded.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    }
                    val modes = listOf(ThemeMode.SYSTEM to R.string.settings_theme_system, ThemeMode.LIGHT to R.string.settings_theme_light, ThemeMode.DARK to R.string.settings_theme_dark)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = ThemeMode.from(state.themeMode) == mode,
                                onClick = { viewModel.updateThemeMode(mode.key) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                            ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                // Wallpaper colours replace the style's own colours, so they only make sense with Lagoon.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && state.appStyle == AppStyle.LAGOON) {
                    SettingsDivider()
                    SwitchRow(Icons.Rounded.Palette, stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_body), state.dynamicColor, viewModel::updateDynamicColor)
                }
                SettingsDivider()
                SwitchRow(Icons.Rounded.Swipe, stringResource(R.string.settings_swipe), stringResource(R.string.settings_swipe_body), state.swipeToNavigate, viewModel::updateSwipeToNavigate)
                SettingsDivider()
                SwitchRow(Icons.Rounded.Radio, stringResource(R.string.community_share_listening), stringResource(R.string.community_share_listening_body), state.shareListening, viewModel::updateShareListening)
            }

            // ------------------------------------------------ wallpaper
            SettingsGroup(stringResource(R.string.settings_wallpaper)) {
                WallpaperPreview(sessions = sessionCount, dark = wallpaperDark)
                SettingsDivider()
                SwitchRow(Icons.Rounded.Contrast, stringResource(R.string.wallpaper_follow_system), stringResource(R.string.wallpaper_follow_system_body), state.followSystemTheme, viewModel::updateFollowSystemTheme)
                if (!state.followSystemTheme) {
                    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                        val choices = listOf(WallpaperTheme.LIGHT to R.string.wallpaper_day, WallpaperTheme.DARK to R.string.wallpaper_night)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            choices.forEachIndexed { i, (theme, label) ->
                                SegmentedButton(
                                    selected = state.wallpaperTheme == theme,
                                    onClick = { viewModel.updateWallpaperTheme(theme) },
                                    shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                                    icon = { SegmentedButtonDefaults.Icon(active = state.wallpaperTheme == theme) { Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) } }
                                ) { Text(stringResource(label)) }
                            }
                        }
                    }
                }
                SettingsDivider()
                SwitchRow(Icons.Rounded.Smartphone, stringResource(R.string.wallpaper_home), null, state.wallpaperHomeScreen, viewModel::updateWallpaperHomeScreen)
                SettingsDivider()
                SwitchRow(Icons.Rounded.Lock, stringResource(R.string.wallpaper_lock), null, state.wallpaperLockScreen, viewModel::updateWallpaperLockScreen)
                SettingsDivider()
                SwitchRow(Icons.Rounded.Sync, stringResource(R.string.wallpaper_auto_sync), stringResource(R.string.wallpaper_auto_sync_body), state.autoSyncWallpaper, onCheckedChange = { on ->
                    viewModel.updateAutoSyncWallpaper(on)
                    if (on) applyWallpaper { ok, err -> notify(if (ok) msgWallpaperDone else err ?: msgFailed) }
                })
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (!state.wallpaperHomeScreen && !state.wallpaperLockScreen) { notify(msgNoScreen); return@Button }
                            applyingWallpaper = true
                            applyWallpaper { ok, err -> applyingWallpaper = false; notify(if (ok) msgWallpaperDone else err ?: msgFailed) }
                        },
                        enabled = !applyingWallpaper,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (applyingWallpaper) CircularProgressIndicator(strokeWidth = Spacing.progressStroke, modifier = Modifier.size(Spacing.iconSm))
                        else Text(stringResource(R.string.wallpaper_apply_now), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(onClick = { openLiveWallpaper(context) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.wallpaper_live), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // ------------------------------------------------ widgets
            SettingsGroup(stringResource(R.string.settings_widgets)) {
                SettingsRow(Icons.Rounded.Widgets, stringResource(R.string.widget_countdown), stringResource(R.string.widget_countdown_body),
                    onClick = { if (!pinWidget(context, ExamCountdownWidgetReceiver::class.java)) notify(msgPinUnsupported) })
                SettingsDivider()
                SettingsRow(Icons.Rounded.CalendarMonth, stringResource(R.string.widget_calendar), stringResource(R.string.widget_calendar_body),
                    onClick = { if (!pinWidget(context, ExamMatrixWidgetReceiver::class.java)) notify(msgPinUnsupported) })
            }

            // ------------------------------------------------ backup & data
            SettingsGroup(stringResource(R.string.settings_backup)) {
                SettingsRow(Icons.Rounded.CloudUpload, stringResource(R.string.backup_create), stringResource(R.string.backup_create_body),
                    onClick = { dialog = SettingsDialog.EXPORT })
                SettingsDivider()
                SettingsRow(Icons.Rounded.Restore, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_body),
                    onClick = { pickBackup.launch("*/*") })
                BackupStatusCard(
                    state = backup,
                    onSave = { (backup as? BackupTaskState.ExportCompleted)?.let { saveBackup.launch(it.backupFile.name) } },
                    onSend = {
                        (backup as? BackupTaskState.ExportCompleted)?.let {
                            if (!shareFile(context, it.backupFile, context.getString(R.string.backup_send_title))) notify(msgFailed)
                        }
                    },
                    onDismiss = { CardBackupStateHolder.reset() }
                )
                SettingsDivider()
                SettingsRow(Icons.Rounded.DeleteSweep, stringResource(R.string.settings_reset), stringResource(R.string.settings_reset_body),
                    titleColor = MaterialTheme.colorScheme.error, onClick = { dialog = SettingsDialog.RESET })
            }

            // ------------------------------------------------ about
            SettingsGroup(stringResource(R.string.settings_about)) {
                SettingsRow(Icons.Rounded.Info, stringResource(R.string.app_name), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                SettingsDivider()
                SettingsRow(Icons.Rounded.AutoAwesome, stringResource(R.string.settings_developer_name), stringResource(R.string.settings_developer_bio))
                SettingsDivider()
                SettingsRow(Icons.Rounded.Phone, stringResource(R.string.settings_contact_whatsapp), DEVELOPER_PHONE,
                    onClick = { if (!openUrl(context, DEVELOPER_WHATSAPP)) notify(msgFailed) })
                SettingsDivider()
                SettingsRow(Icons.Rounded.Facebook, stringResource(R.string.settings_contact_facebook), null,
                    onClick = { if (!openUrl(context, DEVELOPER_FACEBOOK)) notify(msgFailed) })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    // ---------------------------------------------------------------- dialogs
    when (dialog) {
        SettingsDialog.GEMINI_KEY -> GeminiKeyDialog(
            current = srs.geminiApiKey,
            onSave = { key ->
                revisions.setGeminiApiKey(key)
                dialog = null
                notify(if (key.isBlank()) msgKeyRemoved else msgKeySaved)
            },
            onGetKey = { if (!openUrl(context, GEMINI_KEY_PAGE)) notify(msgFailed) },
            onTest = { key -> if (app.geminiModels.refresh(key, force = true)) app.geminiModels.info.value.models.size else null },
            onDismiss = { dialog = null }
        )
        SettingsDialog.GEMINI_MODELS -> {
            // Opening the dialog checks Google's list if it is older than 12 hours.
            LaunchedEffect(Unit) { app.geminiModels.refresh(srs.geminiApiKey) }
            GeminiModelsDialog(
                apiKey = srs.geminiApiKey,
                info = geminiInfo,
                selected = srs.selectedGeminiModel,
                custom = srs.customGeminiModels,
                onSelect = revisions::setSelectedGeminiModel,
                onAdd = revisions::addCustomGeminiModel,
                onRemove = revisions::removeCustomGeminiModel,
                onRefresh = { scope.launch { app.geminiModels.refresh(srs.geminiApiKey, force = true) } },
                onDismiss = { dialog = null }
            )
        }
        SettingsDialog.PROMPT -> PromptEditorDialog(
            current = srs.customPromptOverride ?: GeminiOcrEngine.VERBATIM_PROMPT,
            defaultPrompt = GeminiOcrEngine.VERBATIM_PROMPT,
            onSave = { prompt ->
                revisions.updateSrsSettings(srs.copy(customPromptOverride = prompt))
                dialog = null
                notify(msgPromptSaved)
            },
            onDismiss = { dialog = null }
        )
        SettingsDialog.EXPORT -> ExportDialog(
            decks = study.decks,
            totalCards = study.totalCount,
            onStart = { type, deckId ->
                dialog = null
                CardBackupService.startExport(context = context, cardTypeFilter = type, deckIdFilter = deckId)
            },
            onDismiss = { dialog = null }
        )
        SettingsDialog.IMPORT -> {
            val uri = importUri
            if (uri != null) {
                ImportDialog(
                    onStart = { mode ->
                        dialog = null
                        CardBackupService.startImport(context = context, importUri = uri, importMode = mode)
                    },
                    onDismiss = { dialog = null }
                )
            }
        }
        SettingsDialog.RESET -> ConfirmDialog(
            title = stringResource(R.string.settings_reset_title),
            body = stringResource(R.string.settings_reset_confirm_body),
            confirmLabel = stringResource(R.string.settings_reset_confirm),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.resetAllData { ok, error -> notify(if (ok) context.getString(R.string.reset_done) else error.orEmpty()) }
            },
            onDismiss = { dialog = null }
        )
        SettingsDialog.SIGN_OUT -> ConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            body = stringResource(R.string.settings_sign_out_body),
            confirmLabel = stringResource(R.string.settings_sign_out),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                dialog = null
                FocusFlowApplication.instance.authRepository.signOut()
                navController?.navigate(Routes.auth(AuthMode.SIGN_IN)) { popUpTo(0) { inclusive = true } }
            },
            onDismiss = { dialog = null }
        )
        SettingsDialog.DELETE_ACCOUNT -> {
            val signedIn = account is AccountState.SignedIn
            ConfirmDialog(
                title = stringResource(if (signedIn) R.string.settings_delete_account_title else R.string.settings_erase_device_title),
                body = stringResource(if (signedIn) R.string.settings_delete_account_body else R.string.settings_erase_device_body),
                confirmLabel = stringResource(if (signedIn) R.string.settings_delete_account_confirm else R.string.settings_erase_device_confirm),
                dismissLabel = stringResource(R.string.cancel),
                destructive = true,
                onConfirm = {
                    dialog = null
                    deleting = true
                    viewModel.deleteAccount { ok, error ->
                        deleting = false
                        if (ok) {
                            navController?.navigate(Routes.auth(AuthMode.SIGN_IN)) { popUpTo(0) { inclusive = true } }
                        } else {
                            notify(error ?: msgFailed)
                        }
                    }
                },
                onDismiss = { dialog = null }
            )
        }
        null -> {}
    }
}

private enum class SettingsDialog { GEMINI_KEY, GEMINI_MODELS, PROMPT, EXPORT, IMPORT, RESET, SIGN_OUT, DELETE_ACCOUNT }

// ---------------------------------------------------------------- system helpers

private fun hasDndAccess(context: Context): Boolean =
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted

private fun openDndAccess(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openUrl(context: Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

/** What "Apply now" sets: your island, with a palm for each completed session. */
@Composable
private fun WallpaperPreview(sessions: Int, dark: Boolean) {
    val island = LocalFocusStyle.current.island(dark)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            Modifier
                .height(Spacing.wallpaperPreviewHeight)
                .aspectRatio(9f / 19.5f)
                .clip(MaterialTheme.shapes.medium)
        ) {
            with(IslandRenderer) { drawLagoonScene(sessions, island) }
        }
        Spacer(Modifier.size(Spacing.md))
        Text(
            stringResource(R.string.wallpaper_preview_body, IslandRenderer.HUT_AT, IslandRenderer.LIGHTHOUSE_AT, sessions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openLiveWallpaper(context: Context) {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, DayNightLiveWallpaperService::class.java))
    runCatching { context.startActivity(direct) }
        .recoverCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
}

/** Asks the launcher to add a widget. False when the launcher can't (then: long-press the home screen). */
private fun pinWidget(context: Context, receiver: Class<*>): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return runCatching { manager.requestPinAppWidget(ComponentName(context, receiver), null, null) }.getOrDefault(false)
}

private fun shareFile(context: Context, file: java.io.File, title: String): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess
