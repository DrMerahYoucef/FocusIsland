package com.example.ui.screen.timer

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.LastSound
import com.example.data.challenge.Challenge
import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.FriendChallengeState
import com.example.ui.screen.challenge.ChallengeTime
import com.example.ui.screen.challenge.ChallengeViewModel
import com.example.ui.screen.challenge.FriendDuelIncomingCard
import com.example.ui.screen.challenge.FriendDuelLiveCard
import com.example.ui.screen.challenge.FriendDuelVictoryCelebration
import com.example.ui.screen.radio.PlaybackStatus
import com.example.ui.screen.radio.StationArt
import com.example.ui.designsystem.RingClock
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.RadioStation
import com.example.service.Phase
import com.example.service.PomodoroTimerService
import com.example.service.TimerState
import com.example.ui.designsystem.BannerTone
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.InfoBanner
import com.example.ui.designsystem.PhaseRing
import com.example.ui.designsystem.StatTile
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.screen.radio.RadioViewModel
import com.example.ui.screen.settings.SettingsViewModel
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.phaseColor
import kotlinx.coroutines.delay
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import com.example.ui.designsystem.Celebration
import com.example.ui.theme.LocalFocusStyle

/** Session counts arriving this soon after opening come from loading, not from a finished session. */
private const val PLANTED_GRACE_MS = 2_000L
private const val PLANTED_SHOWN_MS = 4_600L
private const val AUTO_FOCUS_MODE_DELAY_MS = 20_000L
internal const val CONFIRM_END_AFTER_MS = 60_000L

/** Focus tab: the timer, its controls, sound and today's progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToBatterySaver: () -> Unit,
    onNavigateToRadio: () -> Unit = {},
    onNavigateToChallenges: () -> Unit = {},
    onNavigateToStyles: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val challengeViewModel: ChallengeViewModel = viewModel()
    val challenges by challengeViewModel.state.collectAsState()
    val friendState by challengeViewModel.friendState.collectAsState()
    val lastAmbient by viewModel.lastAmbient.collectAsState()
    val lastSoundKind by viewModel.lastSoundKind.collectAsState()
    val state by viewModel.timerState.collectAsState()
    val today by viewModel.today.collectAsState()
    val nextExam by viewModel.nextExam.collectAsState()
    val settings by settingsViewModel.state.collectAsState()
    val radioViewModel: RadioViewModel = viewModel()
    val radioPlaying by radioViewModel.isPlaying.collectAsState()
    val station by radioViewModel.currentStation.collectAsState()
    val stations by radioViewModel.displayedStations.collectAsState()
    val favouriteIds by radioViewModel.favouriteIds.collectAsState()
    val radioStatus by radioViewModel.status.collectAsState()

    var showAutoResumeBanner by remember { mutableStateOf(false) }
    LaunchedEffect(state.lastAutoResumedAt) {
        if (state.lastAutoResumedAt > 0L && System.currentTimeMillis() - state.lastAutoResumedAt < 10_000L) {
            showAutoResumeBanner = true
            delay(4_500L)
            showAutoResumeBanner = false
        }
    }

    // DND access can be granted in system settings while we're away: re-check on resume.
    var hasDndAccess by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        hasDndAccess = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        onPauseOrDispose { }
    }

    var showSounds by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    // While a session runs the screen stays on here, so the switch to Focus mode below can happen
    // (otherwise the phone may sleep first). Focus mode itself keeps the screen on.
    val hostView = LocalView.current
    DisposableEffect(state.isRunning) {
        hostView.keepScreenOn = state.isRunning
        onDispose { hostView.keepScreenOn = false }
    }

    // Settings (on by default): open Focus mode after 20 s without interaction while running.
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastInteraction, state.isRunning, settings.autoFocusMode) {
        if (state.isRunning && settings.autoFocusMode) {
            delay(AUTO_FOCUS_MODE_DELAY_MS)
            onNavigateToBatterySaver()
        }
    }

    // The sound card shows what plays now, otherwise the last radio or ambient sound used.
    val ambientOn = state.currentAmbientId != PomodoroTimerService.AMBIENT_NONE
    val lastStation = station
    val sound = when {
        radioPlaying && lastStation != null -> SoundCard.Radio(lastStation, radioStatus)
        ambientOn -> SoundCard.Ambient(state.currentAmbientId, on = true, timerRunning = state.isRunning)
        lastSoundKind == LastSound.KIND_AMBIENT && lastAmbient != null -> SoundCard.Ambient(lastAmbient!!, on = false, timerRunning = state.isRunning)
        lastStation != null -> SoundCard.Radio(lastStation, radioStatus)
        lastAmbient != null -> SoundCard.Ambient(lastAmbient!!, on = false, timerRunning = state.isRunning)
        else -> SoundCard.None
    }

    // A finished session plants a tree on the island: celebrate it here, in the style's way.
    val composedAt = remember { SystemClock.elapsedRealtime() }
    var lastSessions by remember { mutableStateOf<Int?>(null) }
    var planted by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(today.sessions) {
        val previous = lastSessions
        lastSessions = today.sessions
        // Ignore the first values (loading from the database), only a real +1 counts.
        if (previous != null && today.sessions == previous + 1 && SystemClock.elapsedRealtime() - composedAt > PLANTED_GRACE_MS) {
            planted = System.currentTimeMillis()
        }
    }
    LaunchedEffect(planted) {
        if (planted != null) {
            delay(PLANTED_SHOWN_MS)
            planted = null
        }
    }

    Box(modifier.fillMaxSize()) {
    FocusContent(
        state = state,
        today = today,
        nextExam = nextExam,
        sound = sound,
        challenge = challenges.active,
        showDndBanner = settings.blockNotifications && !hasDndAccess,
        showStyleTip = !settings.styleTipSeen,
        onOpenStyles = {
            settingsViewModel.dismissStyleTip()
            onNavigateToStyles()
        },
        onDismissStyleTip = settingsViewModel::dismissStyleTip,
        friendState = friendState,
        showAutoResumeBanner = showAutoResumeBanner,
        onAcceptFriendChallenge = challengeViewModel::acceptFriendChallenge,
        onDeclineFriendChallenge = challengeViewModel::declineFriendChallenge,
        onSurrenderFriendChallenge = challengeViewModel::surrenderFriendChallenge,
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent()
                    lastInteraction = System.currentTimeMillis()
                }
            }
        },
        onStartPause = {
            when {
                state.isRunning -> viewModel.pauseTimer()
                state.isUntouched -> viewModel.startTimer()
                else -> viewModel.resumeTimer()
            }
        },
        onSkip = viewModel::skipPhase,
        onEnd = {
            val elapsed = state.phaseDurationMs - state.remainingMs
            if (state.phase == Phase.FOCUS && elapsed > CONFIRM_END_AFTER_MS) confirmEnd = true else viewModel.stopTimer()
        },
        onOpenFocusMode = onNavigateToBatterySaver,
        onOpenSounds = { showSounds = true },
        onToggleSound = {
            when (sound) {
                is SoundCard.Radio -> radioViewModel.togglePlayback(context)
                is SoundCard.Ambient -> if (sound.on) {
                    viewModel.setAmbientSound(PomodoroTimerService.AMBIENT_NONE)
                } else {
                    if (radioPlaying) radioViewModel.pausePlayback(context)
                    viewModel.setAmbientSound(sound.id)
                }
                SoundCard.None -> showSounds = true
            }
        },
        onOpenChallenges = onNavigateToChallenges,
        onAllowDnd = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    )
    Celebration(trigger = planted)
    FriendDuelVictoryCelebration(
        win = friendState.uncelebratedWin,
        myUid = friendState.myUid,
        onAcknowledge = challengeViewModel::acknowledgeFriendWin
    )
    AnimatedVisibility(
        visible = planted != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.sm, start = Spacing.md, end = Spacing.md)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = Spacing.xxs,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(
                stringResource(LocalFocusStyle.current.plantedMessage),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }
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

    if (showSounds) {
        ModalBottomSheet(onDismissRequest = { showSounds = false }) {
            SoundsSheet(
                currentAmbientId = state.currentAmbientId,
                radioPlaying = radioPlaying,
                currentStationId = station?.id,
                favourites = stations.filter { it.id in favouriteIds },
                onSelectAmbient = { id ->
                    if (radioPlaying) radioViewModel.pausePlayback(context)
                    viewModel.setAmbientSound(id)
                },
                onSelectStation = { s ->
                    viewModel.setAmbientSound(PomodoroTimerService.AMBIENT_NONE)
                    radioViewModel.selectStation(s, context)
                },
                onSoundOff = {
                    if (radioPlaying) radioViewModel.pausePlayback(context)
                    viewModel.setAmbientSound(PomodoroTimerService.AMBIENT_NONE)
                },
                onBrowseStations = {
                    showSounds = false
                    onNavigateToRadio()
                }
            )
        }
    }
}

/** Stateless Focus layout (previewable). */
@Composable
fun FocusContent(
    state: TimerState,
    today: FocusToday,
    nextExam: NextExam?,
    sound: SoundCard,
    challenge: Challenge?,
    showDndBanner: Boolean,
    onStartPause: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onOpenFocusMode: () -> Unit,
    onOpenSounds: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenChallenges: () -> Unit,
    onAllowDnd: () -> Unit,
    modifier: Modifier = Modifier,
    showStyleTip: Boolean = false,
    onOpenStyles: () -> Unit = {},
    onDismissStyleTip: () -> Unit = {},
    friendState: FriendChallengeState = FriendChallengeState(),
    showAutoResumeBanner: Boolean = false,
    onAcceptFriendChallenge: (FriendChallenge) -> Unit = {},
    onDeclineFriendChallenge: (FriendChallenge) -> Unit = {},
    onSurrenderFriendChallenge: (FriendChallenge) -> Unit = {}
) {
    val view = LocalView.current
    val ringColor = phaseColor(state.phase)
    val cycle = state.sessionsBeforeLongBreak.coerceAtLeast(1)

    // The timer never scrolls away: it takes the space left; the extras below switch in place.
    val dial = @Composable { m: Modifier -> TimerDial(state, today, ringColor, m) }
    val dots = @Composable { CycleDots(done = state.sessionCount % cycle, total = cycle, current = ringColor) }
    val controls = @Composable {
        Column(Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onStartPause()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.buttonHeight)
            ) {
                Icon(if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    stringResource(
                        when {
                            state.isRunning -> R.string.focus_pause
                            !state.isUntouched -> R.string.focus_resume
                            state.phase == Phase.FOCUS -> R.string.focus_start
                            else -> R.string.focus_start_break
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilledTonalButton(onClick = onSkip, modifier = Modifier.weight(1f).heightIn(min = Spacing.minTouchTarget)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
                    Spacer(Modifier.size(Spacing.xxs))
                    Text(stringResource(R.string.focus_skip), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onEnd,
                    enabled = !state.isUntouched || state.phase != Phase.FOCUS,
                    modifier = Modifier.weight(1f).heightIn(min = Spacing.minTouchTarget)
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
                    Spacer(Modifier.size(Spacing.xxs))
                    Text(stringResource(R.string.focus_end), maxLines = 1)
                }
                OutlinedButton(onClick = onOpenFocusMode, modifier = Modifier.weight(1f).heightIn(min = Spacing.minTouchTarget)) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
                    Spacer(Modifier.size(Spacing.xxs))
                    Text(stringResource(R.string.focus_mode), maxLines = 1)
                }
            }
        }
    }
    val friendDuelPanel = @Composable {
        friendState.incomingPending.firstOrNull()?.let { incoming ->
            FriendDuelIncomingCard(
                challenge = incoming,
                onAccept = onAcceptFriendChallenge,
                onDecline = onDeclineFriendChallenge,
                modifier = Modifier.widthIn(max = Spacing.formMaxWidth)
            )
        }
        friendState.activeDuel?.let { activeDuel ->
            FriendDuelLiveCard(
                duel = activeDuel,
                myUid = friendState.myUid,
                onSurrender = onSurrenderFriendChallenge,
                onOpenDetails = onOpenChallenges,
                compact = true,
                modifier = Modifier.widthIn(max = Spacing.formMaxWidth)
            )
        }
    }
    val extras = @Composable {
        FocusExtras(today, nextExam, sound, challenge, friendState.activeDuel, onOpenSounds, onToggleSound, onOpenChallenges)
    }
    val banner = @Composable {
        when {
            state.isAutoPausedByAppSwitch -> {
                InfoBanner(
                    text = stringResource(R.string.focus_auto_paused_banner),
                    tone = BannerTone.Warning
                )
            }
            showAutoResumeBanner -> {
                InfoBanner(
                    text = stringResource(R.string.focus_auto_resumed_banner)
                )
            }
            showDndBanner -> {
                InfoBanner(
                    text = stringResource(R.string.focus_dnd_banner),
                    tone = BannerTone.Warning,
                    actionLabel = stringResource(R.string.action_allow),
                    onAction = onAllowDnd
                )
            }
            showStyleTip -> {
                InfoBanner(
                    text = stringResource(R.string.style_tip),
                    actionLabel = stringResource(R.string.style_tip_action),
                    onAction = onOpenStyles,
                    onDismiss = onDismissStyleTip
                )
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: timer on the left, everything else on the right (only that side scrolls if short).
            Row(Modifier.fillMaxSize().padding(horizontal = Spacing.screenCompact, vertical = Spacing.sm)) {
                dial(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically)
                ) {
                    banner()
                    friendDuelPanel()
                    dots()
                    controls()
                    extras()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = Spacing.screenCompact, vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                banner()
                friendDuelPanel()
                dial(Modifier.weight(1f).fillMaxWidth())
                dots()
                controls()
                extras()
            }
        }
    }
}

/** The day dial with the timer ring inside, as large as the space allows (dial dropped when small). */
@Composable
private fun TimerDial(state: TimerState, today: FocusToday, ringColor: Color, modifier: Modifier) {
    val progress = if (state.phaseDurationMs > 0) state.remainingMs.toFloat() / state.phaseDurationMs else 1f
    val time = formatClock(state.remainingMs)
    val phaseText = stringResource(phaseLabel(state.phase))
    val round = (state.sessionCount % state.sessionsBeforeLongBreak.coerceAtLeast(1)) + 1
    val focusElapsedMin = (state.phaseDurationMs - state.remainingMs) / 60_000f
    val liveStart = if (state.phase == Phase.FOCUS && !state.isUntouched) {
        java.time.LocalTime.now().let { it.hour * 60f + it.minute } - focusElapsedMin
    } else null
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight, Spacing.focusDialMax)
        val withDial = size >= Spacing.focusDialMin
        Box(Modifier.size(size)) {
            if (withDial) {
                DayDial(
                    blocks = today.blocks,
                    liveStartMinute = liveStart,
                    sessionColor = MaterialTheme.colorScheme.primary,
                    liveColor = ringColor,
                    modifier = Modifier.matchParentSize()
                )
            }
            PhaseRing(
                progress = progress,
                color = ringColor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (withDial) Spacing.dayDialInset else Spacing.none)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$phaseText, $time"
                        liveRegion = LiveRegionMode.Polite
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = MaterialTheme.shapes.small, color = ringColor.copy(alpha = 0.14f)) {
                        Text(
                            phaseText,
                            style = MaterialTheme.typography.labelLarge,
                            color = ringColor,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                        )
                    }
                    RingClock(time, MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.focus_round, round, state.sessionsBeforeLongBreak),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private enum class FocusPanel { SOUND, CHALLENGE, TODAY }

/** Sound, Challenge and Today share one place under the timer, switched by a segmented row. */
@Composable
private fun FocusExtras(
    today: FocusToday,
    nextExam: NextExam?,
    sound: SoundCard,
    challenge: Challenge?,
    activeFriendDuel: FriendChallenge?,
    onOpenSounds: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenChallenges: () -> Unit
) {
    val hasAnyChallenge = challenge != null || activeFriendDuel != null
    var panel by rememberSaveable { mutableStateOf(if (hasAnyChallenge) FocusPanel.CHALLENGE else FocusPanel.SOUND) }
    val labels = listOf(
        FocusPanel.SOUND to R.string.sound_title,
        FocusPanel.CHALLENGE to R.string.challenge_card_title,
        FocusPanel.TODAY to R.string.focus_panel_today
    )
    Column(Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, (p, label) ->
                SegmentedButton(
                    selected = panel == p,
                    onClick = { panel = p },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    icon = {
                        // A running challenge shows a flag even when another panel is open.
                        if (p == FocusPanel.CHALLENGE && hasAnyChallenge && panel != p) {
                            Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                        } else {
                            SegmentedButtonDefaults.Icon(active = panel == p)
                        }
                    }
                ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = Spacing.focusPanelHeight).animateContentSize(), contentAlignment = Alignment.Center) {
            when (panel) {
                FocusPanel.SOUND -> SoundCardView(sound = sound, onOpen = onOpenSounds, onToggle = onToggleSound)
                FocusPanel.CHALLENGE -> ChallengeEntry(challenge = challenge, onOpen = onOpenChallenges)
                FocusPanel.TODAY -> TodayPanel(today, nextExam)
            }
        }
    }
}

/** Today's sessions, minutes and streak in one compact card, with the next exam under it. */
@Composable
private fun TodayPanel(today: FocusToday, nextExam: NextExam?) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth()) {
                TodayValue(today.sessions.toString(), pluralStringResource(R.plurals.focus_sessions_today_label, today.sessions), Icons.Rounded.TaskAlt, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                TodayValue(today.minutes.toString(), stringResource(R.string.focus_minutes_today_label), Icons.Rounded.Timer, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                TodayValue(today.streakDays.toString(), pluralStringResource(R.plurals.focus_streak_label, today.streakDays), Icons.Rounded.LocalFireDepartment, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
            nextExam?.let { exam ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(Spacing.iconSm), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.size(Spacing.xs))
                    Text(
                        if (exam.daysLeft == 0) stringResource(R.string.focus_next_exam_today, exam.name)
                        else pluralStringResource(R.plurals.focus_next_exam, exam.daysLeft, exam.name, exam.daysLeft),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayValue(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Spacing.iconSm))
            Spacer(Modifier.size(Spacing.xxs))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What the sound card shows: the radio, an ambient sound, or nothing chosen yet. */
sealed interface SoundCard {
    data class Radio(val station: RadioStation, val status: PlaybackStatus) : SoundCard
    data class Ambient(val id: String, val on: Boolean, val timerRunning: Boolean) : SoundCard
    data object None : SoundCard
}

/** Now playing (or last played): tap the card to choose a sound, the button plays or pauses. */
@Composable
private fun SoundCardView(sound: SoundCard, onOpen: () -> Unit, onToggle: () -> Unit) {
    val playing = when (sound) {
        is SoundCard.Radio -> sound.status == PlaybackStatus.PLAYING || sound.status == PlaybackStatus.BUFFERING
        is SoundCard.Ambient -> sound.on
        SoundCard.None -> false
    }
    val title = when (sound) {
        is SoundCard.Radio -> sound.station.name
        is SoundCard.Ambient -> stringResource(ambientLabel(sound.id))
        SoundCard.None -> stringResource(R.string.sound_none_title)
    }
    val subtitle = stringResource(
        when (sound) {
            is SoundCard.Radio -> when (sound.status) {
                PlaybackStatus.PLAYING -> R.string.radio_status_playing
                PlaybackStatus.BUFFERING -> R.string.radio_status_connecting
                PlaybackStatus.ERROR -> R.string.sound_radio_error_short
                else -> R.string.radio_status_paused
            }
            is SoundCard.Ambient -> when {
                !sound.on -> R.string.radio_status_paused
                sound.timerRunning -> R.string.sound_ambient_playing
                else -> R.string.sound_ambient_waiting
            }
            SoundCard.None -> R.string.sound_none_body
        }
    )
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            when (sound) {
                is SoundCard.Radio -> StationArt(sound.station, playing = false, size = Spacing.soundArt)
                else -> Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(Spacing.soundArt)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (sound is SoundCard.None) Icons.Rounded.MusicNote else Icons.Rounded.GraphicEq, contentDescription = null)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                Text(stringResource(R.string.sound_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(Spacing.minTouchTarget)) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.radio_pause else R.string.radio_play)
                )
            }
        }
    }
}

/** Entry to Challenges; while one runs, its goal and the time left. */
@Composable
private fun ChallengeEntry(challenge: Challenge?, onOpen: () -> Unit) {
    val now by produceState(System.currentTimeMillis(), challenge?.id) {
        while (challenge != null) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
    }
    val container = if (challenge != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.medium, color = container, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            overlineContent = if (challenge != null) { { Text(stringResource(R.string.challenge_in_progress)) } } else null,
            headlineContent = { Text(challenge?.goal ?: stringResource(R.string.challenge_card_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    if (challenge != null) stringResource(R.string.challenge_card_running, ChallengeTime.clock(challenge.endAt - now))
                    else stringResource(R.string.challenge_card_body)
                )
            },
            leadingContent = { Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = container)
        )
    }
}

/** Where we are in the cycle before the long break (the ring already says it in words). */
@Composable
private fun CycleDots(done: Int, total: Int, current: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.clearAndSetSemantics { }) {
        repeat(total) { i ->
            val shape = androidx.compose.foundation.shape.CircleShape
            Box(
                Modifier
                    .size(Spacing.sm)
                    .then(
                        if (i < done) Modifier.background(current, shape)
                        else Modifier.border(Spacing.dotBorder, if (i == done) current else MaterialTheme.colorScheme.outlineVariant, shape)
                    )
            )
        }
    }
}

/** Off / Ambient / Radio picker shown in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SoundsSheet(
    currentAmbientId: String,
    radioPlaying: Boolean,
    currentStationId: String?,
    favourites: List<RadioStation>,
    onSelectAmbient: (String) -> Unit,
    onSelectStation: (RadioStation) -> Unit,
    onSoundOff: () -> Unit,
    onBrowseStations: () -> Unit
) {
    var mode by rememberSaveable {
        mutableIntStateOf(
            when {
                radioPlaying -> 2
                currentAmbientId != PomodoroTimerService.AMBIENT_NONE -> 1
                else -> 0
            }
        )
    }
    val labels = listOf(R.string.sound_off, R.string.sound_ambient, R.string.sound_radio)
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(stringResource(R.string.sound_title), style = MaterialTheme.typography.titleLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = mode == index,
                    onClick = {
                        mode = index
                        if (index == 0) onSoundOff()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size)
                ) { Text(stringResource(label)) }
            }
        }
        when (mode) {
            0 -> Text(stringResource(R.string.sound_off_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            1 -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PomodoroTimerService.AMBIENT_IDS.forEach { id ->
                        FilterChip(
                            selected = currentAmbientId == id,
                            onClick = { onSelectAmbient(id) },
                            label = { Text(stringResource(ambientLabel(id))) }
                        )
                    }
                }
                Text(stringResource(R.string.sound_ambient_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                if (favourites.isEmpty()) {
                    Text(stringResource(R.string.sound_no_favourites), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                favourites.take(6).forEach { s ->
                    val playing = radioPlaying && s.id == currentStationId
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(s.country) },
                        leadingContent = {
                            Icon(
                                if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.Radio,
                                contentDescription = null,
                                tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.semantics { if (playing) contentDescription = "${s.name}, playing" },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        trailingContent = {
                            TextButton(onClick = { onSelectStation(s) }) { Text(stringResource(R.string.sound_play)) }
                        }
                    )
                }
                TextButton(onClick = onBrowseStations) { Text(stringResource(R.string.sound_browse_stations)) }
            }
        }
    }
}

internal fun phaseLabel(phase: Phase) = when (phase) {
    Phase.FOCUS -> R.string.phase_focus
    Phase.SHORT_BREAK -> R.string.phase_short_break
    Phase.LONG_BREAK -> R.string.phase_long_break
}

internal fun ambientLabel(id: String) = when (id) {
    "rain" -> R.string.ambient_rain
    "white_noise" -> R.string.ambient_white_noise
    "campfire" -> R.string.ambient_campfire
    "stream" -> R.string.ambient_stream
    "space" -> R.string.ambient_space
    else -> R.string.sound_off
}

internal fun formatClock(ms: Long): String {
    val total = (ms + 999) / 1000 // round up so 00:00 only shows at the very end
    return "%02d:%02d".format(total / 60, total % 60)
}

@ThemePreviews
@Composable
private fun FocusContentPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FocusContent(
                state = TimerState(remainingMs = 17 * 60_000L + 32_000L, isRunning = true, sessionCount = 2),
                today = FocusToday(
                    sessions = 3, minutes = 75, streakDays = 6,
                    blocks = listOf(DayBlock(540f, 565f, true), DayBlock(575f, 600f, true), DayBlock(780f, 792f, false), DayBlock(840f, 865f, true))
                ),
                nextExam = NextExam("Anatomy", 12),
                sound = SoundCard.Ambient("rain", on = true, timerRunning = true),
                challenge = null,
                showDndBanner = true,
                onStartPause = {}, onSkip = {}, onEnd = {}, onOpenFocusMode = {}, onOpenSounds = {},
                onToggleSound = {}, onOpenChallenges = {}, onAllowDnd = {}
            )
        }
    }
}
