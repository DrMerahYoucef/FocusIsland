package com.example.ui.screen.challenge

import android.media.AudioManager
import android.media.ToneGenerator
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.challenge.Challenge
import com.example.data.challenge.ChallengeResult
import com.example.data.challenge.ChallengeState
import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.FriendChallengeState
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.Celebration
import com.example.ui.designsystem.playSuccessSound
import com.example.ui.theme.LocalFocusStyle
import com.example.ui.theme.styledString
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.PhaseRing
import com.example.ui.designsystem.RingClock
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.TimerDisplayStyle
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A challenge waiting for its 5-second countdown: either a duration or a fixed end time. */
private data class PendingChallenge(val goal: String, val minutes: Int, val endAt: Long) {
    fun endAt(nowMs: Long) = if (minutes > 0) ChallengeTime.endAfter(nowMs, minutes) else endAt
}

private val PendingSaver = listSaver<PendingChallenge?, Any>(
    save = { p -> if (p == null) emptyList() else listOf(p.goal, p.minutes, p.endAt) },
    restore = { l -> if (l.isEmpty()) null else PendingChallenge(l[0] as String, l[1] as Int, l[2] as Long) }
)

private const val COUNTDOWN_FROM = 5

/**
 * Challenges screen split into two dedicated tabs:
 * - Tab 0 (Personal): Set a personal goal and deadline, 5-second countdown with sound, beat the clock, and view personal history.
 * - Tab 1 (Study Group): Full-screen Messenger-style group study chat room with collapsible horloge leaderboard, emoji reactions, and focus nudges.
 */
@Composable
fun ChallengesScreen(viewModel: ChallengeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val friendState by viewModel.friendState.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }
    var showStudyGroupSheet by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable(stateSaver = PendingSaver) { mutableStateOf(null) }
    var celebrated by remember { mutableStateOf<Challenge?>(null) }
    var confirmGiveUp by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val tones = rememberTones()
    val context = LocalContext.current
    val focusStyle = LocalFocusStyle.current

    val now by produceState(System.currentTimeMillis(), state.active?.id) {
        while (state.active != null) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
    }
    LaunchedEffect(now, state.active?.id, state.active?.result) {
        val active = state.active ?: return@LaunchedEffect
        if (now >= active.endAt && active.result != ChallengeResult.FAILED) {
            viewModel.timeUp()
        }
    }

    Box(modifier.fillMaxSize()) {
        ChallengesContent(
            state = state,
            friendState = friendState,
            nowMs = now,
            celebrated = celebrated,
            onNew = { showNew = true },
            onGoalReached = {
                celebrated = viewModel.goalReached()
                playSuccessSound(context, focusStyle.successSound)
            },
            onGiveUp = { confirmGiveUp = true },
            onStopOvertime = viewModel::stopOvertime,
            onDismissCelebration = { celebrated = null },
            onClearHistory = { confirmClear = true },
            onOpenStudyGroupFriends = { showStudyGroupSheet = true },
            onAcceptFriendDuel = viewModel::acceptFriendChallenge,
            onDeclineFriendDuel = viewModel::declineFriendChallenge,
            onCancelFriendDuel = viewModel::cancelOutgoingFriendChallenge,
            onSurrenderFriendDuel = viewModel::surrenderFriendChallenge,
            onSendStudyGroupMessage = viewModel::sendStudyGroupMessage,
            onNudgeStudyGroupFriend = viewModel::nudgeStudyGroupFriend,
            onClearStudyGroupMessages = viewModel::clearStudyGroupMessages,
            onDeleteStudyGroupChallenge = viewModel::deleteStudyGroupChallenge,
            onResendStudyGroupInvite = viewModel::resendStudyGroupInvite,
            onAddStudyGroupTime = { duel, addedMinutes ->
                val highestActive = duel.acceptedMembers().maxOfOrNull { it.minutes } ?: 0
                val baseTarget = if (duel.targetMinutes > 0) {
                    duel.targetMinutes
                } else {
                    if (addedMinutes <= highestActive) highestActive else 0
                }
                val newTarget = (baseTarget + addedMinutes).coerceIn(1, ChallengeTime.MAX_MINUTES)
                val addedFmt = formatMinutesForNotice(context, addedMinutes)
                val newTargetFmt = formatMinutesForNotice(context, newTarget)
                val notice = context.getString(R.string.study_group_add_time_chat_notice, addedFmt, newTargetFmt)
                viewModel.addStudyGroupTime(duel, addedMinutes, notice)
            }
        )
        Celebration(trigger = celebrated?.id)
        FriendDuelVictoryCelebration(
            win = friendState.uncelebratedWin,
            myUid = friendState.myUid,
            onAcknowledge = viewModel::acknowledgeFriendWin
        )
        pending?.let { p ->
            StartCountdown(
                tones = tones,
                onFinished = {
                    viewModel.start(p.goal, p.endAt(System.currentTimeMillis()))
                    pending = null
                },
                onCancel = { pending = null }
            )
        }
    }

    if (showNew) {
        NewChallengeSheet(
            onStart = { showNew = false; celebrated = null; pending = it },
            onDismiss = { showNew = false }
        )
    }
    if (showStudyGroupSheet) {
        val currentNowMs = System.currentTimeMillis()
        val activeDuel = friendState.activeDuel
        val existingUids = activeDuel
            ?.activeAndInvitedMembers()
            ?.filter {
                it.roleStatus == com.example.data.challenge.StudyMemberRoleStatus.ACTIVE ||
                    it.isInviteValid(currentNowMs, activeDuel.createdAt)
            }
            ?.mapTo(HashSet()) { it.uid }
            .orEmpty()
        StudyGroupFriendsSheet(
            friends = friendState.availableFriends,
            alreadyInGroupUids = existingUids,
            isAddingToExisting = friendState.activeDuel != null,
            onConfirmSelection = { chosen, targetMinutes ->
                showStudyGroupSheet = false
                viewModel.startOrInviteStudyGroup(chosen, targetMinutes)
            },
            onDismiss = { showStudyGroupSheet = false }
        )
    }
    if (confirmGiveUp) {
        ConfirmDialog(
            title = stringResource(R.string.challenge_give_up_title),
            body = stringResource(R.string.challenge_give_up_body),
            confirmLabel = stringResource(R.string.challenge_give_up),
            dismissLabel = stringResource(R.string.focus_keep_going),
            onConfirm = { confirmGiveUp = false; viewModel.giveUp() },
            onDismiss = { confirmGiveUp = false },
            destructive = true
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.challenges_clear_title),
            body = stringResource(R.string.challenges_clear_body),
            confirmLabel = stringResource(R.string.challenges_clear),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = { confirmClear = false; viewModel.clearHistory() },
            onDismiss = { confirmClear = false },
            destructive = true
        )
    }
}

private const val TAB_PERSONAL = 0
private const val TAB_STUDY_GROUP = 1

/** Stateless layout (previewable) with two tabs: Personal Challenge and Study Group Chat Room. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChallengesContent(
    state: ChallengeState,
    nowMs: Long,
    celebrated: Challenge?,
    onNew: () -> Unit,
    onGoalReached: () -> Unit,
    onGiveUp: () -> Unit,
    onDismissCelebration: () -> Unit,
    onClearHistory: () -> Unit,
    onStopOvertime: () -> Unit = {},
    friendState: FriendChallengeState = FriendChallengeState(),
    onOpenStudyGroupFriends: () -> Unit = {},
    onAcceptFriendDuel: (FriendChallenge) -> Unit = {},
    onDeclineFriendDuel: (FriendChallenge) -> Unit = {},
    onCancelFriendDuel: (FriendChallenge) -> Unit = {},
    onSurrenderFriendDuel: (FriendChallenge) -> Unit = {},
    onSendStudyGroupMessage: (FriendChallenge, String) -> Unit = { _, _ -> },
    onNudgeStudyGroupFriend: (FriendChallenge, String, String) -> Unit = { _, _, _ -> },
    onClearStudyGroupMessages: (FriendChallenge) -> Unit = {},
    onDeleteStudyGroupChallenge: (FriendChallenge) -> Unit = {},
    onResendStudyGroupInvite: (FriendChallenge, String, String) -> Unit = { _, _, _ -> },
    onAddStudyGroupTime: (FriendChallenge, Int) -> Unit = { _, _ -> }
) {
    val hasActiveOrInviteGroup = friendState.activeDuel != null || friendState.incomingPending.isNotEmpty()
    val isImeVisible = WindowInsets.isImeVisible
    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            if (hasActiveOrInviteGroup && state.active == null) TAB_STUDY_GROUP else TAB_PERSONAL
        )
    }
    var lastAutoSwitchedDuelId by rememberSaveable { mutableStateOf("") }

    // Automatically switch to the Study Group tab when a new Study Group becomes active
    LaunchedEffect(friendState.activeDuel?.id) {
        val activeId = friendState.activeDuel?.id.orEmpty()
        if (activeId.isNotEmpty() && activeId != lastAutoSwitchedDuelId) {
            lastAutoSwitchedDuelId = activeId
            selectedTab = TAB_STUDY_GROUP
        } else if (activeId.isEmpty()) {
            lastAutoSwitchedDuelId = ""
        }
    }

    var selectedHistoryDuel by remember { mutableStateOf<FriendChallenge?>(null) }
    var selectedPersonalHistory by remember { mutableStateOf<Challenge?>(null) }
    val hideTabsForChatKeyboard = isImeVisible && selectedTab == TAB_STUDY_GROUP && friendState.activeDuel != null

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !hideTabsForChatKeyboard,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == TAB_PERSONAL,
                    onClick = { selectedTab = TAB_PERSONAL },
                    modifier = Modifier.testTag("challenges_tab_personal"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.challenges_tab_personal),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.active != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == TAB_STUDY_GROUP,
                    onClick = { selectedTab = TAB_STUDY_GROUP },
                    modifier = Modifier.testTag("challenges_tab_study_group"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.challenges_tab_study_group),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasActiveOrInviteGroup) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.extendedColors.success)
                                )
                            }
                        }
                    }
                )
            }
        }

        if (selectedTab == TAB_PERSONAL) {
            PersonalChallengeTabContent(
                state = state,
                friendState = friendState,
                nowMs = nowMs,
                celebrated = celebrated,
                onNew = onNew,
                onGoalReached = onGoalReached,
                onGiveUp = onGiveUp,
                onStopOvertime = onStopOvertime,
                onDismissCelebration = onDismissCelebration,
                onClearHistory = onClearHistory,
                onAcceptFriendDuel = { duel ->
                    onAcceptFriendDuel(duel)
                    selectedTab = TAB_STUDY_GROUP
                },
                onDeclineFriendDuel = onDeclineFriendDuel,
                onHistoryChallengeClick = { selectedPersonalHistory = it },
                onHistoryDuelClick = { selectedHistoryDuel = it },
                modifier = Modifier.weight(1f)
            )
        } else {
            StudyGroupTabContent(
                friendState = friendState,
                onOpenStudyGroupFriends = onOpenStudyGroupFriends,
                onAcceptFriendDuel = onAcceptFriendDuel,
                onDeclineFriendDuel = onDeclineFriendDuel,
                onCancelFriendDuel = onCancelFriendDuel,
                onSurrenderFriendDuel = onSurrenderFriendDuel,
                onSendStudyGroupMessage = onSendStudyGroupMessage,
                onNudgeStudyGroupFriend = onNudgeStudyGroupFriend,
                onClearStudyGroupMessages = onClearStudyGroupMessages,
                onDeleteStudyGroupChallenge = onDeleteStudyGroupChallenge,
                onResendStudyGroupInvite = onResendStudyGroupInvite,
                onAddStudyGroupTime = onAddStudyGroupTime,
                onHistoryDuelClick = { selectedHistoryDuel = it },
                modifier = Modifier.weight(1f)
            )
        }
    }

    selectedHistoryDuel?.let { duel ->
        ChallengePodiumSheet(
            duel = duel,
            myUid = friendState.myUid,
            onDismiss = { selectedHistoryDuel = null }
        )
    }

    selectedPersonalHistory?.let { ch ->
        PersonalChallengeSummarySheet(
            challenge = ch,
            onDismiss = { selectedPersonalHistory = null }
        )
    }
}

@Composable
private fun PersonalChallengeTabContent(
    state: ChallengeState,
    friendState: FriendChallengeState,
    nowMs: Long,
    celebrated: Challenge?,
    onNew: () -> Unit,
    onGoalReached: () -> Unit,
    onGiveUp: () -> Unit,
    onStopOvertime: () -> Unit,
    onDismissCelebration: () -> Unit,
    onClearHistory: () -> Unit,
    onAcceptFriendDuel: (FriendChallenge) -> Unit,
    onDeclineFriendDuel: (FriendChallenge) -> Unit,
    onHistoryChallengeClick: (Challenge) -> Unit = {},
    onHistoryDuelClick: (FriendChallenge) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lastFailed = state.history.firstOrNull()?.takeIf {
        it.result == ChallengeResult.FAILED && it.finishedAt != null && nowMs - it.finishedAt < RECENT_MS && state.active == null
    }
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxSize()
    ) {
        // Show incoming Study Group invitations at the top so the user never misses an invite
        items(friendState.incomingPending, key = { "in_duel_personal_" + it.id }) { incoming ->
            FriendDuelIncomingCard(
                challenge = incoming,
                myUid = friendState.myUid,
                onAccept = onAcceptFriendDuel,
                onDecline = onDeclineFriendDuel
            )
        }

        item {
            when {
                state.active != null -> ActiveChallengeCard(
                    challenge = state.active,
                    nowMs = nowMs,
                    onGoalReached = onGoalReached,
                    onGiveUp = onGiveUp,
                    onStopOvertime = onStopOvertime,
                    onNew = onNew
                )
                celebrated != null -> ResultCard(celebrated, success = true, onDismiss = onDismissCelebration, onNew = onNew)
                lastFailed != null -> ResultCard(lastFailed, success = false, onDismiss = null, onNew = onNew)
                else -> Button(
                    onClick = onNew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.buttonHeight)
                        .testTag("personal_challenge_new_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.challenge_new))
                }
            }
        }

        if (state.history.isEmpty() && friendState.history.isEmpty()) {
            if (state.active == null) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Flag,
                        title = stringResource(R.string.challenges_empty_title),
                        body = stringResource(R.string.challenges_empty_body)
                    )
                }
            }
        } else {
            if (state.history.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.challenges_history)) {
                        TextButton(onClick = onClearHistory) { Text(stringResource(R.string.challenges_clear)) }
                    }
                }
                item {
                    val total = state.history.size
                    val done = state.doneCount
                    Text(
                        pluralStringResource(R.plurals.challenges_stats, done, done, total, done * 100 / total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.history, key = { it.id }) {
                    HistoryRow(
                        challenge = it,
                        activeChallenge = state.active,
                        nowMs = nowMs,
                        onClick = { onHistoryChallengeClick(it) }
                    )
                }
            }
            if (friendState.history.isNotEmpty()) {
                item(key = "personal_tab_friend_history_header") {
                    SectionHeader(stringResource(R.string.friend_duel_history_header))
                }
                items(friendState.history, key = { "personal_tab_duel_hist_" + it.id }) { duel ->
                    FriendDuelHistoryRow(
                        duel = duel,
                        myUid = friendState.myUid,
                        onClick = { onHistoryDuelClick(duel) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyGroupTabContent(
    friendState: FriendChallengeState,
    onOpenStudyGroupFriends: () -> Unit,
    onAcceptFriendDuel: (FriendChallenge) -> Unit,
    onDeclineFriendDuel: (FriendChallenge) -> Unit,
    onCancelFriendDuel: (FriendChallenge) -> Unit,
    onSurrenderFriendDuel: (FriendChallenge) -> Unit,
    onSendStudyGroupMessage: (FriendChallenge, String) -> Unit,
    onNudgeStudyGroupFriend: (FriendChallenge, String, String) -> Unit,
    onClearStudyGroupMessages: (FriendChallenge) -> Unit,
    onDeleteStudyGroupChallenge: (FriendChallenge) -> Unit,
    onResendStudyGroupInvite: (FriendChallenge, String, String) -> Unit = { _, _, _ -> },
    onAddStudyGroupTime: (FriendChallenge, Int) -> Unit = { _, _ -> },
    onHistoryDuelClick: (FriendChallenge) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showHistorySheetFromActive by rememberSaveable { mutableStateOf(false) }
    val activeStudyGroup = friendState.activeDuel
    val isImeVisible = WindowInsets.isImeVisible
    if (activeStudyGroup != null) {
        // Dedicated full-screen chat room layout with unified compact header & direct keyboard docking
        Column(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            if (!isImeVisible) {
                friendState.incomingPending.forEach { incoming ->
                    FriendDuelIncomingCard(
                        challenge = incoming,
                        myUid = friendState.myUid,
                        onAccept = onAcceptFriendDuel,
                        onDecline = onDeclineFriendDuel
                    )
                }
            }

            FriendDuelLiveCard(
                duel = activeStudyGroup,
                myUid = friendState.myUid,
                onSurrender = onSurrenderFriendDuel,
                onSendMessage = onSendStudyGroupMessage,
                onNudgeFriend = onNudgeStudyGroupFriend,
                onResendInvite = onResendStudyGroupInvite,
                onAddTime = onAddStudyGroupTime,
                onClearMessages = onClearStudyGroupMessages,
                onDeleteGroup = onDeleteStudyGroupChallenge,
                onAddFriends = onOpenStudyGroupFriends,
                nudgeCooldownUntil = friendState.nudgeCooldownUntil,
                onOpenDetails = if (friendState.history.isNotEmpty()) {
                    { showHistorySheetFromActive = true }
                } else null,
                fullScreenChat = true,
                modifier = Modifier.weight(1f)
            )
        }
        if (showHistorySheetFromActive && friendState.history.isNotEmpty()) {
            val latestCompleted = friendState.history.first()
            ChallengePodiumSheet(
                duel = latestCompleted,
                myUid = friendState.myUid,
                onDismiss = { showHistorySheetFromActive = false }
            )
        }
    } else {
        val currentNowMs = System.currentTimeMillis()
        val freshInvites = friendState.incomingPending.filter { !it.isDeclinedOfferFor(friendState.myUid, currentNowMs) }
        val declinedOffers = friendState.incomingPending.filter { it.isDeclinedOfferFor(friendState.myUid, currentNowMs) }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = modifier.fillMaxSize()
        ) {
            // Fresh incoming Study Group invitations
            items(freshInvites, key = { "in_duel_" + it.id }) { incoming ->
                FriendDuelIncomingCard(
                    challenge = incoming,
                    myUid = friendState.myUid,
                    onAccept = onAcceptFriendDuel,
                    onDecline = onDeclineFriendDuel
                )
            }

            // Declined invitations that remain offered for 30 minutes
            if (declinedOffers.isNotEmpty()) {
                item(key = "pending_declined_invites_header") {
                    SectionHeader(stringResource(R.string.study_group_pending_invites_header))
                }
                items(declinedOffers, key = { "in_duel_declined_" + it.id }) { incoming ->
                    FriendDuelIncomingCard(
                        challenge = incoming,
                        myUid = friendState.myUid,
                        onAccept = onAcceptFriendDuel,
                        onDecline = onDeclineFriendDuel
                    )
                }
            }

            // Outgoing pending friend duel invitations
            items(friendState.outgoingPending, key = { "out_duel_" + it.id }) { outgoing ->
                val opponent = outgoing.opponentName.ifBlank { stringResource(R.string.community_unnamed) }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.study_group_title)) },
                        supportingContent = { Text(stringResource(R.string.friend_duel_waiting_accept, opponent)) },
                        trailingContent = {
                            TextButton(onClick = { onCancelFriendDuel(outgoing) }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }

            // Launch a new Study Group button
            item(key = "launch_study_group_button") {
                Button(
                    onClick = onOpenStudyGroupFriends,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.buttonHeight)
                        .testTag("launch_study_group_button")
                ) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.study_group_new_button))
                }
            }

            if (friendState.history.isEmpty() && friendState.incomingPending.isEmpty() && friendState.outgoingPending.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Groups,
                        title = stringResource(R.string.study_group_empty_room_title),
                        body = stringResource(R.string.study_group_empty_room_body)
                    )
                }
            } else if (friendState.history.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.friend_duel_history_header))
                }
                items(friendState.history, key = { "duel_hist_" + it.id }) { duel ->
                    FriendDuelHistoryRow(
                        duel = duel,
                        myUid = friendState.myUid,
                        onClick = { onHistoryDuelClick(duel) }
                    )
                }
            }
        }
    }
}

private const val RECENT_MS = 10 * 60_000L

@Composable
private fun ActiveChallengeCard(
    challenge: Challenge,
    nowMs: Long,
    onGoalReached: () -> Unit,
    onGiveUp: () -> Unit,
    onStopOvertime: () -> Unit,
    onNew: () -> Unit
) {
    val view = LocalView.current
    val isOvertime = nowMs >= challenge.endAt || challenge.result == ChallengeResult.FAILED

    if (isOvertime) {
        val overtimeMs = (nowMs - challenge.endAt).coerceAtLeast(0L)
        val overtimeClock = "+${ChallengeTime.clock(overtimeMs)}"
        val totalElapsedClock = ChallengeTime.clock((nowMs - challenge.startAt).coerceAtLeast(challenge.durationMs))
        val plannedText = durationText((challenge.durationMs / 60_000L).toInt().coerceAtLeast(1))
        val overtimeLabel = stringResource(R.string.challenge_overtime_label)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(
                        stringResource(R.string.challenge_overtime_badge),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
                Text(
                    challenge.goal,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                PhaseRing(
                    progress = 1f,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(Spacing.focusRingCompact)
                        .semantics(mergeDescendants = true) { contentDescription = "$overtimeLabel $overtimeClock" }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RingClock(overtimeClock, MaterialTheme.colorScheme.error)
                        Text(overtimeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    stringResource(R.string.challenge_total_elapsed, totalElapsedClock, plannedText),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    styledString(R.string.challenge_time_up_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onNew,
                    modifier = Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxWidth().heightIn(min = Spacing.buttonHeight)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.challenge_new))
                }
                OutlinedButton(
                    onClick = onStopOvertime,
                    modifier = Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxWidth().heightIn(min = Spacing.buttonHeight)
                ) {
                    Text(stringResource(R.string.challenge_stop_overtime))
                }
            }
        }
        return
    }

    val remaining = (challenge.endAt - nowMs).coerceAtLeast(0L)
    val clock = ChallengeTime.clock(remaining)
    val leftLabel = stringResource(R.string.challenge_time_left)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(R.string.challenge_in_progress), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                challenge.goal,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            PhaseRing(
                progress = if (challenge.durationMs > 0) remaining.toFloat() / challenge.durationMs else 0f,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(Spacing.focusRingCompact)
                    .semantics(mergeDescendants = true) { contentDescription = "$leftLabel $clock" }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingClock(clock, MaterialTheme.colorScheme.onSurface)
                    Text(leftLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.challenge_ends_at_short, formatTime(challenge.endAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onGoalReached()
                },
                modifier = Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxWidth().heightIn(min = Spacing.buttonHeight)
            ) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.challenge_goal_reached))
            }
            TextButton(onClick = onGiveUp) { Text(stringResource(R.string.challenge_give_up)) }
        }
    }
}

/** Shown right after a challenge ends: success (confetti) or time up. */
@Composable
private fun ResultCard(challenge: Challenge, success: Boolean, onDismiss: (() -> Unit)?, onNew: () -> Unit) {
    val container = if (success) MaterialTheme.extendedColors.successContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (success) MaterialTheme.extendedColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
    val spare = ((challenge.endAt - (challenge.finishedAt ?: challenge.endAt)) / 60_000L).toInt()
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                if (success) Icons.Rounded.EmojiEvents else Icons.Rounded.HourglassBottom,
                contentDescription = null,
                modifier = Modifier.size(Spacing.iconLg)
            )
            Text(
                styledString(if (success) R.string.challenge_success_title else R.string.challenge_time_up_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(challenge.goal, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                if (success && spare < 1) styledString(R.string.challenge_success_body_tight)
                else if (success) styledString(R.string.challenge_success_body, durationText(spare))
                else styledString(R.string.challenge_time_up_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.challenge_dismiss)) }
                Button(onClick = onNew) { Text(stringResource(R.string.challenge_new)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    challenge: Challenge,
    activeChallenge: Challenge? = null,
    nowMs: Long = 0L,
    onClick: (() -> Unit)? = null
) {
    val done = challenge.result == ChallengeResult.DONE
    val day = remember(challenge.startAt) {
        DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(challenge.startAt).atZone(ZoneId.systemDefault()))
    }
    val plannedDurationText = durationText((challenge.durationMs / 60_000L).toInt().coerceAtLeast(1))
    val totalMs = if (activeChallenge?.id == challenge.id && nowMs > challenge.endAt) {
        (nowMs - challenge.startAt).coerceAtLeast(challenge.durationMs)
    } else {
        challenge.actualDurationMs
    }
    val hasOvertime = !done && totalMs >= challenge.durationMs + 1_000L

    ListItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("personal_challenge_history_row_${challenge.id}"),
        leadingContent = {
            Icon(
                if (done) Icons.Rounded.EmojiEvents else Icons.Rounded.HourglassBottom,
                contentDescription = null,
                tint = if (done) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.error
            )
        },
        headlineContent = { Text(challenge.goal, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (hasOvertime) {
                    stringResource(R.string.challenge_history_overtime, day, plannedDurationText, ChallengeTime.clock(totalMs))
                } else {
                    "$day · $plannedDurationText"
                }
            )
        },
        trailingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (done) MaterialTheme.extendedColors.successContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (done) MaterialTheme.extendedColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    stringResource(if (done) R.string.challenge_done else R.string.challenge_failed),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalChallengeSummarySheet(
    challenge: Challenge,
    onDismiss: () -> Unit
) {
    val done = challenge.result == ChallengeResult.DONE
    val day = remember(challenge.startAt) {
        DateTimeFormatter.ofPattern("EEE d MMM yyyy")
            .format(Instant.ofEpochMilli(challenge.startAt).atZone(ZoneId.systemDefault()))
    }
    val plannedDurationText = durationText((challenge.durationMs / 60_000L).toInt().coerceAtLeast(1))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("personal_challenge_summary_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding()
                .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                shape = CircleShape,
                color = if (done) MaterialTheme.extendedColors.successContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (done) MaterialTheme.extendedColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (done) Icons.Rounded.EmojiEvents else Icons.Rounded.HourglassBottom,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = if (done) MaterialTheme.extendedColors.successContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (done) MaterialTheme.extendedColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    text = if (done) {
                        "${stringResource(R.string.challenge_podium_first)} · ${stringResource(R.string.challenge_done)}"
                    } else {
                        stringResource(R.string.challenge_failed)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = 4.dp)
                )
            }
            Text(
                text = stringResource(R.string.personal_challenge_summary_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = challenge.goal,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$day · $plannedDurationText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.buttonHeight)
            ) {
                Text(stringResource(R.string.challenge_dismiss))
            }
        }
    }
}

// ---------------------------------------------------------------- new challenge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewChallengeSheet(onStart: (PendingChallenge) -> Unit, onDismiss: () -> Unit) {
    var goal by rememberSaveable { mutableStateOf("") }
    var byEndTime by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableStateOf("0") }
    var minutes by rememberSaveable { mutableStateOf("45") }
    val defaultEnd = remember { LocalTime.now().plusHours(1).withSecond(0).withNano(0) }
    var endHour by rememberSaveable { mutableIntStateOf(defaultEnd.hour) }
    var endMinute by rememberSaveable { mutableIntStateOf(defaultEnd.minute) }
    var pickTime by rememberSaveable { mutableStateOf(false) }

    val totalMinutes = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    val durationOk = totalMinutes in 1..ChallengeTime.MAX_MINUTES
    val canStart = goal.isNotBlank() && (byEndTime || durationOk)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(R.string.challenge_new), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it.take(GOAL_MAX) },
                label = { Text(stringResource(R.string.challenge_goal)) },
                placeholder = { Text(stringResource(R.string.challenge_goal_hint)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            val modes = listOf(R.string.challenge_mode_duration, R.string.challenge_mode_end_time)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = byEndTime == (i == 1),
                        onClick = { byEndTime = i == 1 },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                    ) { Text(stringResource(label)) }
                }
            }
            if (!byEndTime) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ChallengeTime.quickMinutes.forEach { q ->
                        FilterChip(
                            selected = totalMinutes == q,
                            onClick = { hours = (q / 60).toString(); minutes = (q % 60).toString() },
                            label = { Text(durationText(q)) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    NumberField(hours, { hours = it }, R.string.challenge_hours, Modifier.weight(1f))
                    NumberField(minutes, { minutes = it }, R.string.challenge_minutes, Modifier.weight(1f))
                }
                if (!durationOk) {
                    Text(stringResource(R.string.challenge_duration_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                val end = LocalTime.of(endHour, endMinute)
                val tomorrow = !end.isAfter(LocalTime.now())
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.buttonHeight)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(if (tomorrow) R.string.challenge_ends_tomorrow else R.string.challenge_ends_today, formatTime(end)))
                }
            }
            Button(
                onClick = {
                    val endAt = if (byEndTime) ChallengeTime.endAtClock(System.currentTimeMillis(), LocalTime.of(endHour, endMinute)) else 0L
                    onStart(PendingChallenge(goal.trim(), if (byEndTime) 0 else totalMinutes, endAt))
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.buttonHeight)
            ) {
                Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.challenge_start))
            }
        }
    }

    if (pickTime) {
        val context = LocalContext.current
        val picker = rememberTimePickerState(endHour, endMinute, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.challenge_end_at)) },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = { endHour = picker.hour; endMinute = picker.minute; pickTime = false }) {
                    Text(stringResource(R.string.challenge_dismiss))
                }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private const val GOAL_MAX = 120

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: Int, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(4)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

// ---------------------------------------------------------------- countdown

/** 5, 4, 3, 2, 1 with a beep each second, then "Go!" with a higher tone. Back or Cancel stops it. */
@Composable
private fun StartCountdown(tones: Tones, onFinished: () -> Unit, onCancel: () -> Unit) {
    val view = LocalView.current
    var count by remember { mutableIntStateOf(COUNTDOWN_FROM) }
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        for (n in COUNTDOWN_FROM downTo 1) {
            count = n
            tones.tick()
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            delay(1_000L)
        }
        count = 0
        tones.go()
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        delay(600L)
        onFinished()
    }
    val a11y = if (count > 0) stringResource(R.string.challenge_countdown_a11y, count) else stringResource(R.string.challenge_go)
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.challenge_get_ready), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = count,
                transitionSpec = { (scaleIn(tween(250), initialScale = 1.6f) + fadeIn(tween(250))) togetherWith fadeOut(tween(200)) },
                label = "countdown",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive; contentDescription = a11y }
            ) { n ->
                Text(
                    if (n > 0) n.toString() else stringResource(R.string.challenge_go),
                    style = TimerDisplayStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.size(Spacing.xl))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** Short beeps from the system tone generator (no sound files); silently absent if unavailable. */
private class Tones(private val generator: ToneGenerator?) {
    fun tick() { generator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
    fun go() { generator?.startTone(ToneGenerator.TONE_PROP_ACK, 400) }
    fun release() { generator?.release() }
}

@Composable
private fun rememberTones(): Tones {
    val tones = remember { Tones(runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()) }
    DisposableEffect(Unit) { onDispose { tones.release() } }
    return tones
}

// ---------------------------------------------------------------- formatting

@Composable
internal fun durationText(minutes: Int): String =
    if (minutes >= 60) {
        if (minutes % 60 == 0) stringResource(R.string.duration_h, minutes / 60)
        else stringResource(R.string.duration_h_m, minutes / 60, minutes % 60)
    } else stringResource(R.string.duration_m, minutes)

private fun formatMinutesForNotice(context: android.content.Context, minutes: Int): String =
    if (minutes >= 60) {
        if (minutes % 60 == 0) context.getString(R.string.duration_h, minutes / 60)
        else context.getString(R.string.duration_h_m, minutes / 60, minutes % 60)
    } else context.getString(R.string.duration_m, minutes)

private fun formatTime(epochMs: Long): String =
    formatTime(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime())

private fun formatTime(time: LocalTime): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time)

// ---------------------------------------------------------------- previews

private val previewNow = System.currentTimeMillis()
private val previewHistory = listOf(
    Challenge(1, "Finish the elbow fractures chapter", previewNow - 7_200_000, previewNow - 3_600_000, previewNow - 4_000_000, ChallengeResult.DONE),
    Challenge(2, "Tidy the flat", previewNow - 90_000_000, previewNow - 86_400_000, previewNow - 86_400_000, ChallengeResult.FAILED)
)

@ThemePreviews
@Composable
private fun ChallengesActivePreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface {
            ChallengesContent(
                state = ChallengeState(Challenge(9, "Revise 30 cards of anatomy", previewNow - 600_000, previewNow + 1_800_000), previewHistory),
                nowMs = previewNow, celebrated = null,
                onNew = {}, onGoalReached = {}, onGiveUp = {}, onDismissCelebration = {}, onClearHistory = {}
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ChallengesEmptyPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface {
            ChallengesContent(
                state = ChallengeState(), nowMs = previewNow, celebrated = null,
                onNew = {}, onGoalReached = {}, onGiveUp = {}, onDismissCelebration = {}, onClearHistory = {}
            )
        }
    }
}
