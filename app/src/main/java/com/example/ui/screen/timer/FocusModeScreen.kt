package com.example.ui.screen.timer

import com.example.ui.designsystem.RingClock
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.FocusFlowApplication
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.IntOffset
import com.example.R
import com.example.data.db.entity.SessionEntity
import com.example.service.Phase
import com.example.service.PomodoroTimerService
import com.example.service.TimerState
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.PhaseRing
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.screen.radio.RadioViewModel
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.FocusModePalette
import com.example.ui.theme.LagoonDarkColors
import com.example.ui.theme.Spacing
import com.example.ui.theme.extendedColors
import com.example.ui.theme.phaseColor
import com.example.ui.theme.phaseTextureBackground
import com.example.ui.theme.pomodoroCurrentMinuteFraction
import com.example.ui.theme.pomodoroMinutesPassed
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private const val IDLE_DIM_MS = 8_000L
private const val BURN_IN_SHIFT_MS = 60_000L
private const val BURN_IN_PX = 12
private const val SWIPE_UP_PX = 100f
private val MILESTONES = listOf(25, 50, 75)

/**
 * Focus mode: a calm, true-black screen for long sessions. Keeps the screen on, hides the system
 * bars, dims itself after 10 s without touch, and restores brightness, bars and orientation on exit.
 * Gestures: double-tap exits, long-press starts or pauses, swipe up shows today's progress,
 * drag the right edge to change brightness. Every gesture also has a visible button or a TalkBack action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(
    viewModel: TimerViewModel,
    onDismiss: () -> Unit,
    onOpenRadio: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val state by viewModel.timerState.collectAsState()
    val today by viewModel.today.collectAsState()
    val friendRepo = remember { FocusFlowApplication.instance.friendChallengeRepository }
    val friendState by friendRepo.state.collectAsState()
    val radio: RadioViewModel = viewModel()
    val radioPlaying by radio.isPlaying.collectAsState()
    val station by radio.currentStation.collectAsState()
    val stations by radio.displayedStations.collectAsState()
    val favourites by radio.favouriteIds.collectAsState()

    val battery by rememberBatteryLevel(context)
    val powerSave = remember(context) { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }

    // ---- window: screen on, immersive, brightness and orientation, all restored on exit
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f) }
    var landscapeLock by rememberSaveable { mutableStateOf<Boolean?>(null) }
    DisposableEffect(activity) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
                it.attributes = it.attributes.apply { screenBrightness = originalBrightness }
            }
            runCatching { activity?.requestedOrientation = originalOrientation }
        }
    }
    fun setBrightness(value: Float) {
        brightness = value.coerceIn(0.01f, 1f)
        activity?.window?.let { it.attributes = it.attributes.apply { screenBrightness = brightness } }
    }
    fun toggleRotation(currentlyLandscape: Boolean) {
        val next = !currentlyLandscape
        landscapeLock = next
        runCatching {
            activity?.requestedOrientation =
                if (next) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    // ---- standby: after a few seconds without touch only the clock stays, slightly dimmed and
    // shifted a little every minute (OLED burn-in). The first tap brings the controls back.
    // With TalkBack on, the controls never hide.
    val touchExploration = remember(context) {
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(lastTouch) { idle = false; delay(IDLE_DIM_MS); idle = !touchExploration }
    val chromeAlpha by animateFloatAsState(if (idle) 0f else 1f, tween(600), label = "chrome")
    val clockAlpha by animateFloatAsState(if (idle) 0.75f else 1f, tween(600), label = "clock")
    val shift by produceState(IntOffset.Zero, idle) {
        value = IntOffset.Zero
        while (idle) {
            delay(BURN_IN_SHIFT_MS)
            value = IntOffset((-BURN_IN_PX..BURN_IN_PX).random(), (-BURN_IN_PX..BURN_IN_PX).random())
        }
    }

    // ---- milestones: a soft flash and a tick at 25 / 50 / 75 % of a running focus session
    val done = if (state.phaseDurationMs > 0) (100 - state.remainingMs * 100 / state.phaseDurationMs).toInt().coerceIn(0, 100) else 0
    var lastMilestone by remember { mutableIntStateOf(MILESTONES.lastOrNull { done >= it } ?: 0) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(done) {
        val reached = MILESTONES.lastOrNull { done >= it } ?: 0
        if (reached > lastMilestone && state.isRunning && state.phase == Phase.FOCUS) {
            flash = true
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        lastMilestone = reached
    }
    LaunchedEffect(flash) { if (flash) { delay(800); flash = false } }
    val flashAlpha by animateFloatAsState(if (flash) 0.12f else 0f, tween(400), label = "flash")

    var showSounds by rememberSaveable { mutableStateOf(false) }
    var showToday by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    val startPause = {
        when {
            state.isRunning -> viewModel.pauseTimer()
            state.isUntouched -> viewModel.startTimer()
            else -> viewModel.resumeTimer() // a paused session resumes instead of restarting
        }
    }
    val end = {
        val elapsed = state.phaseDurationMs - state.remainingMs
        if (state.phase == Phase.FOCUS && elapsed > CONFIRM_END_AFTER_MS) confirmEnd = true else viewModel.stopTimer()
    }
    val soundLabel = when {
        radioPlaying && station != null -> station!!.name
        state.currentAmbientId != PomodoroTimerService.AMBIENT_NONE -> stringResource(ambientLabel(state.currentAmbientId))
        else -> stringResource(R.string.sound_choose)
    }
    val duelSummaryLine = friendState.activeDuel?.let { duel ->
        val friendName = duel.friendName(friendState.myUid).ifBlank { stringResource(R.string.community_unnamed) }
        stringResource(
            R.string.friend_duel_history_score,
            duel.myMinutes(friendState.myUid),
            friendName,
            duel.friendMinutes(friendState.myUid)
        )
    }

    val exitLabel = stringResource(R.string.focus_mode_exit)
    val todayLabel = stringResource(R.string.focus_mode_today)

    FocusIslandTheme(darkTheme = true) {
        MaterialTheme(colorScheme = LagoonDarkColors.copy(background = FocusModePalette.background, surface = FocusModePalette.background)) {
            var dragY by remember { mutableFloatStateOf(0f) }
            val cycleColor = phaseColor(state.phase)
            val minutesPassed = state.pomodoroMinutesPassed()
            val currentMinuteFraction = state.pomodoroCurrentMinuteFraction()
            Box(
                Modifier
                    .fillMaxSize()
                    .phaseTextureBackground(
                        cycleColor = cycleColor,
                        baseColor = FocusModePalette.background,
                        isDark = true,
                        minutesPassed = minutesPassed,
                        currentMinuteFraction = currentMinuteFraction
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { lastTouch = System.currentTimeMillis() },
                            onDoubleTap = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onDismiss() },
                            onLongPress = { lastTouch = System.currentTimeMillis(); view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); startPause() }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragY = 0f },
                            onVerticalDrag = { change, amount ->
                                lastTouch = System.currentTimeMillis()
                                dragY += amount
                                if (dragY < -SWIPE_UP_PX && !showToday) { change.consume(); showToday = true }
                            }
                        )
                    }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(exitLabel) { onDismiss(); true },
                            CustomAccessibilityAction(todayLabel) { showToday = true; true }
                        )
                    }
            ) {
                val landscape = landscapeLock ?: (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE)
                Column(Modifier.fillMaxSize().safeDrawingPadding().offset { shift }) {
                    TopBar(
                        modifier = Modifier.alpha(chromeAlpha),
                        battery = battery,
                        powerSave = powerSave,
                        onExit = onDismiss,
                        onRotate = { toggleRotation(landscape) }
                    )
                    FocusModeContent(
                        state = state,
                        today = today,
                        soundLabel = soundLabel,
                        landscape = landscape,
                        onStartPause = { lastTouch = System.currentTimeMillis(); startPause() },
                        onSkip = viewModel::skipPhase,
                        onEnd = end,
                        onSounds = { showSounds = true },
                        clockAlpha = clockAlpha,
                        chromeAlpha = chromeAlpha,
                        duelSummaryLine = duelSummaryLine,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.focus_mode_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm).alpha(chromeAlpha)
                    )
                }
                BrightnessStrip(
                    value = brightness,
                    onChange = { lastTouch = System.currentTimeMillis(); setBrightness(it) },
                    modifier = Modifier.align(Alignment.CenterEnd).safeDrawingPadding().alpha(chromeAlpha)
                )
                // While the controls are hidden, a tap only wakes them (no accidental pause or exit by an
                // invisible button); double-tap and long-press keep working.
                if (idle) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { lastTouch = System.currentTimeMillis() },
                                onDoubleTap = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onDismiss() },
                                onLongPress = { lastTouch = System.currentTimeMillis(); view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); startPause() }
                            )
                        }
                    )
                }
                if (flashAlpha > 0f) Box(Modifier.fillMaxSize().background(FocusModePalette.flash.copy(alpha = flashAlpha)))
                com.example.ui.screen.challenge.FriendDuelVictoryCelebration(
                    win = friendState.uncelebratedWin,
                    myUid = friendState.myUid,
                    onAcknowledge = friendRepo::acknowledgeCelebration
                )
            }

            if (showSounds) {
                ModalBottomSheet(onDismissRequest = { showSounds = false }) {
                    SoundsSheet(
                        currentAmbientId = state.currentAmbientId,
                        radioPlaying = radioPlaying,
                        currentStationId = station?.id,
                        favourites = stations.filter { it.id in favourites },
                        onSelectAmbient = { id -> if (radioPlaying) radio.pausePlayback(context); viewModel.setAmbientSound(id) },
                        onSelectStation = { s -> viewModel.setAmbientSound(PomodoroTimerService.AMBIENT_NONE); radio.selectStation(s, context) },
                        onSoundOff = { if (radioPlaying) radio.pausePlayback(context); viewModel.setAmbientSound(PomodoroTimerService.AMBIENT_NONE) },
                        onBrowseStations = { showSounds = false; onOpenRadio() }
                    )
                }
            }
            if (showToday) {
                ModalBottomSheet(onDismissRequest = { showToday = false }) {
                    TodaySheet(percentDone = done, isFocus = state.phase == Phase.FOCUS)
                }
            }
            if (confirmEnd) {
                ConfirmDialog(
                    title = stringResource(R.string.focus_end_title),
                    body = stringResource(R.string.focus_end_body),
                    confirmLabel = stringResource(R.string.focus_end_confirm),
                    dismissLabel = stringResource(R.string.focus_keep_going),
                    onConfirm = { confirmEnd = false; viewModel.stopTimer() },
                    onDismiss = { confirmEnd = false },
                    destructive = true
                )
            }
        }
    }
}

@Composable
private fun TopBar(battery: Int?, powerSave: Boolean, onExit: () -> Unit, onRotate: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onExit) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.focus_mode_exit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        if (battery != null) {
            Icon(
                if (powerSave) Icons.Rounded.BatterySaver else Icons.Rounded.BatteryStd,
                contentDescription = null,
                tint = if (powerSave) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.iconSm)
            )
            Text(
                stringResource(if (powerSave) R.string.focus_mode_battery_saver else R.string.focus_mode_battery, battery),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xxs, end = Spacing.xs)
            )
        }
        IconButton(onClick = onRotate) {
            Icon(Icons.Rounded.ScreenRotation, contentDescription = stringResource(R.string.focus_mode_rotate), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The ring with the time, as large as the screen allows, then today's line, the sound and the
 * controls (beside the ring in landscape). [chromeAlpha] fades everything but the clock.
 */
@Composable
internal fun FocusModeContent(
    state: TimerState,
    today: FocusToday,
    soundLabel: String,
    landscape: Boolean,
    onStartPause: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onSounds: () -> Unit,
    modifier: Modifier = Modifier,
    clockAlpha: Float = 1f,
    chromeAlpha: Float = 1f,
    duelSummaryLine: String? = null
) {
    val details = @Composable {
        Column(
            Modifier.alpha(chromeAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                pluralStringResource(R.plurals.focus_mode_today_line, today.sessions, today.sessions, today.minutes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (duelSummaryLine != null) {
                Text(
                    duelSummaryLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                onClick = onSounds,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Spacing.iconSm))
                    Spacer(Modifier.size(Spacing.xs))
                    Text(soundLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = Spacing.focusModeSoundMax))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                OutlinedIconButton(onClick = onEnd, enabled = !state.isUntouched || state.isRunning, modifier = Modifier.size(Spacing.xxl + Spacing.xs)) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.focus_end_confirm))
                }
                // Dark teal rather than the bright primary: this screen is meant to glow as little as possible.
                FilledIconButton(
                    onClick = onStartPause,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(Spacing.xxl * 2)
                ) {
                    Icon(
                        if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (state.isRunning) R.string.focus_pause else if (state.isUntouched) R.string.focus_start else R.string.focus_resume),
                        modifier = Modifier.size(Spacing.iconLg + Spacing.xs)
                    )
                }
                OutlinedIconButton(onClick = onSkip, modifier = Modifier.size(Spacing.xxl + Spacing.xs)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.focus_skip))
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (landscape) {
            val ring = minOf(maxHeight * 0.92f, maxWidth * 0.48f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.alpha(clockAlpha)) { TimerRing(state, ring) }
                details()
            }
        } else {
            val ring = minOf(maxWidth * 0.86f, maxHeight * 0.58f, Spacing.focusDialMax)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Box(Modifier.alpha(clockAlpha)) { TimerRing(state, ring) }
                details()
            }
        }
    }
}

@Composable
private fun TimerRing(state: TimerState, size: Dp) {
    val color = when (state.phase) {
        Phase.FOCUS -> MaterialTheme.colorScheme.primary
        Phase.SHORT_BREAK -> MaterialTheme.extendedColors.rest
        Phase.LONG_BREAK -> MaterialTheme.colorScheme.secondary
    }
    val fraction = if (state.phaseDurationMs > 0) state.remainingMs.toFloat() / state.phaseDurationMs else 1f
    val phase = stringResource(phaseLabel(state.phase))
    val time = formatClock(state.remainingMs)
    PhaseRing(
        progress = fraction,
        color = color,
        strokeWidth = Spacing.focusRingStroke,
        modifier = Modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = "$phase, $time"
                progressBarRangeInfo = ProgressBarRangeInfo(1f - fraction, 0f..1f)
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(phase, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.semantics { heading() })
            RingClock(time, MaterialTheme.colorScheme.onSurface)
            if (!state.isRunning && !state.isUntouched) {
                Text(stringResource(R.string.focus_mode_paused), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Drag up or down along the right edge to change the screen brightness of this screen only. */
@Composable
private fun BrightnessStrip(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.focus_mode_brightness)
    Column(
        modifier
            .fillMaxHeight()
            .width(Spacing.brightnessStrip)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, amount ->
                    change.consume()
                    onChange(value - amount / (size.height * 0.5f))
                }
            }
            .semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.LightMode, contentDescription = null, tint = FocusModePalette.trackFill, modifier = Modifier.size(Spacing.iconSm))
        Spacer(Modifier.size(Spacing.xs))
        Box(
            Modifier
                .fillMaxHeight(0.4f)
                .width(Spacing.brightnessTrack)
                .background(FocusModePalette.track, CircleShape),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(Modifier.fillMaxHeight(value.coerceIn(0.05f, 1f)).fillMaxWidth().background(FocusModePalette.trackFill, CircleShape))
        }
    }
}

// ---------------------------------------------------------------- today sheet

/** Swipe-up sheet: progress to the next milestone, today's totals, time of day, session log. */
@Composable
private fun TodaySheet(percentDone: Int, isFocus: Boolean) {
    val zone = ZoneId.systemDefault()
    val sessions by remember {
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        FocusFlowApplication.instance.sessionRepository.getSessionsInRange(start, end)
    }.collectAsState(initial = emptyList())
    val completed = remember(sessions) { sessions.filter { it.completed }.sortedByDescending { it.date } }
    TodayContent(percentDone, isFocus, completed)
}

@Composable
private fun TodayContent(percentDone: Int, isFocus: Boolean, completed: List<SessionEntity>) {
    val minutes = completed.sumOf { it.durationSeconds } / 60
    val nextMilestone = (MILESTONES + 100).first { percentDone < it || it == 100 }
    val blocks = remember(completed) { timeOfDayMinutes(completed) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LazyColumn(
        Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { Text(stringResource(R.string.focus_mode_today), style = MaterialTheme.typography.titleLarge) }
        if (isFocus) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.focus_mode_milestone, percentDone, nextMilestone), style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { percentDone / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TodayStat(completed.size.toString(), stringResource(R.string.wgt_stat_sessions), Modifier.weight(1f))
                TodayStat(minutesLabel(minutes), stringResource(R.string.wgt_stat_focus), Modifier.weight(1f))
                TodayStat("+$minutes", stringResource(R.string.wgt_stat_points), Modifier.weight(1f))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.focus_mode_time_of_day), style = MaterialTheme.typography.titleSmall)
                val max = (blocks.maxOfOrNull { it.second } ?: 0).coerceAtLeast(25)
                blocks.forEach { (label, mins) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(Spacing.xxl * 2))
                        LinearProgressIndicator(progress = { mins / max.toFloat() }, modifier = Modifier.weight(1f))
                        Text(minutesLabel(mins), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.width(Spacing.xxl + Spacing.sm))
                    }
                }
            }
        }
        if (completed.isEmpty()) {
            item { Text(stringResource(R.string.focus_mode_no_sessions), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { HorizontalDivider() }
            items(completed, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text(timeFormat.format(Date(s.date))) },
                    trailingContent = { Text(minutesLabel(s.durationSeconds / 60)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun minutesLabel(m: Int): String = LocalContext.current.resources.getQuantityString(R.plurals.settings_minutes, m, m)

@Composable
private fun TodayStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(Spacing.sm).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Minutes focused in the morning (6–11), afternoon (12–17), evening (18–23) and night (0–5). */
internal fun timeOfDayMinutes(sessions: List<SessionEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<Int, Int>> {
    val buckets = IntArray(4)
    sessions.forEach { s ->
        val hour = java.time.Instant.ofEpochMilli(s.date).atZone(zone).hour
        val index = when (hour) { in 6..11 -> 0; in 12..17 -> 1; in 18..23 -> 2; else -> 3 }
        buckets[index] += s.durationSeconds / 60
    }
    return listOf(
        R.string.focus_mode_morning to buckets[0],
        R.string.focus_mode_afternoon to buckets[1],
        R.string.focus_mode_evening to buckets[2],
        R.string.focus_mode_night to buckets[3]
    )
}

// ---------------------------------------------------------------- helpers

@Composable
private fun rememberBatteryLevel(context: Context): androidx.compose.runtime.State<Int?> {
    val level = remember { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (l >= 0 && s > 0) level.value = l * 100 / s
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return level
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@ThemePreviews
@Composable
private fun FocusModePreview() {
    FocusIslandTheme(darkTheme = true) {
        Surface(color = FocusModePalette.background) {
            FocusModeContent(
                state = TimerState(remainingMs = 17 * 60_000L + 32_000L, isRunning = true, sessionCount = 2),
                today = FocusToday(sessions = 3, minutes = 75, streakDays = 6),
                soundLabel = "Rain",
                landscape = false,
                onStartPause = {}, onSkip = {}, onEnd = {}, onSounds = {}
            )
        }
    }
}
