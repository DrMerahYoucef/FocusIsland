package com.example.ui.screen.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.StudyFriendOption
import com.example.data.challenge.StudyGroupMember
import com.example.data.challenge.StudyGroupMessage
import com.example.data.challenge.StudyMemberRoleStatus
import com.example.data.challenge.StudyMemberTimerState
import com.example.ui.designsystem.Celebration
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.playSuccessSound
import com.example.ui.theme.LocalFocusStyle
import com.example.ui.theme.Spacing
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val QUICK_STUDY_EMOJIS = listOf("🔥", "💪", "📚", "🎯", "☕", "👏", "⚡", "🚀")

/**
 * Incoming friend / study group challenge card shown in Community and Challenges screens.
 * - Fresh invitations (`INVITED`) show a prominent banner with a live 30-minute countdown, Decline, and Accept & Start.
 * - Declined invitations (`DECLINED`) stay offered for the remaining 30-minute window in a compact Pending Invites row.
 * - Expired invitations (`>= 30m`) hide automatically.
 */
@Composable
fun FriendDuelIncomingCard(
    challenge: FriendChallenge,
    onAccept: (FriendChallenge) -> Unit,
    onDecline: (FriendChallenge) -> Unit,
    myUid: String = "",
    modifier: Modifier = Modifier
) {
    val now by produceState(System.currentTimeMillis(), challenge.id) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
    }

    val inviteMember = remember(challenge.members, myUid, challenge.challengerUid, challenge.opponentUid) {
        val all = challenge.allMembers()
        if (myUid.isNotEmpty()) {
            all.firstOrNull { it.uid == myUid }
        } else {
            all.firstOrNull { it.uid != challenge.challengerUid }
        }
    }

    val remainingMs = inviteMember?.inviteRemainingMs(now, challenge.createdAt) ?: 0L
    if (remainingMs <= 0L) return

    val countdownText = ChallengeTime.clock(remainingMs)
    val challenger = challenge.challengerName.ifBlank { stringResource(R.string.community_unnamed) }
    val isDeclinedOffer = inviteMember?.roleStatus == StudyMemberRoleStatus.DECLINED
    val targetLabel = if (challenge.isTargetHoursChallenge) durationText(challenge.targetMinutes) else null

    if (isDeclinedOffer) {
        // Compact Pending Invite row with live 30m countdown so the user can still accept after declining
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier
                .fillMaxWidth()
                .testTag("pending_declined_invite_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (targetLabel != null) {
                                stringResource(R.string.study_group_pending_invite_target_from, challenger, targetLabel)
                            } else {
                                stringResource(R.string.study_group_pending_invite_open_from, challenger)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.study_group_invite_expires_in, countdownText),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.xs))
                FilledTonalButton(
                    onClick = { onAccept(challenge) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.sm,
                        vertical = 6.dp
                    ),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("accept_friend_duel_button")
                ) {
                    Text(
                        text = stringResource(R.string.community_accept),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().testTag("incoming_friend_duel_card")
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(Spacing.icon)
                )
                Text(
                    text = stringResource(R.string.friend_duel_incoming_title, challenger),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.study_group_invite_expires_in, countdownText),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(
                text = if (targetLabel != null) {
                    stringResource(R.string.friend_duel_incoming_body_target, targetLabel)
                } else {
                    stringResource(R.string.friend_duel_incoming_body_open)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End)
            ) {
                TextButton(
                    onClick = { onDecline(challenge) },
                    modifier = Modifier.testTag("decline_friend_duel_button")
                ) {
                    Text(stringResource(R.string.friend_duel_decline))
                }
                Button(
                    onClick = { onAccept(challenge) },
                    modifier = Modifier.testTag("accept_friend_duel_button")
                ) {
                    Text(stringResource(R.string.friend_duel_accept_start))
                }
            }
        }
    }
}

/**
 * Live Multi-Friend Study Group Challenge card / chat room shown on the Challenges screen:
 * - Features a collapsible member leaderboard header with horloge (clock) icons for accepted members
 * - Displays pending (30m countdown) and expired (one-tap Resend) status chips for invited friends
 * - Lets members invite more friends to the group anytime
 * - Provides a Nudge button next to paused/idle friends to trigger a notification sound & vibration on their phone
 * - Includes a Messenger-style real-time group chat room that fills available space in the Study Group tab
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendDuelLiveCard(
    duel: FriendChallenge,
    myUid: String,
    onSurrender: (FriendChallenge) -> Unit,
    onSendMessage: (FriendChallenge, String) -> Unit = { _, _ -> },
    onNudgeFriend: (FriendChallenge, String, String) -> Unit = { _, _, _ -> },
    onResendInvite: (FriendChallenge, String, String) -> Unit = { _, _, _ -> },
    onClearMessages: (FriendChallenge) -> Unit = {},
    onDeleteGroup: (FriendChallenge) -> Unit = {},
    onAddFriends: (() -> Unit)? = null,
    nudgeCooldownUntil: Map<String, Long> = emptyMap(),
    onOpenDetails: (() -> Unit)? = null,
    compact: Boolean = false,
    fullScreenChat: Boolean = false,
    modifier: Modifier = Modifier
) {
    var confirmSurrender by rememberSaveable { mutableStateOf(false) }
    var confirmClearChat by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteGroup by rememberSaveable { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var messageDraft by rememberSaveable(duel.id) { mutableStateOf("") }
    // Default to collapsed in full-screen chat room so the conversation takes center stage
    var leaderboardExpanded by rememberSaveable(duel.id) { mutableStateOf(!fullScreenChat) }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && fullScreenChat) {
            leaderboardExpanded = false
        }
    }

    val isCreator = remember(duel.challengerUid, myUid) {
        myUid.isEmpty() || duel.isChallenger(myUid) || duel.challengerUid == "local_user"
    }

    val now by produceState(System.currentTimeMillis(), duel.id) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
    }
    val remainingMs = (duel.endAt - now).coerceAtLeast(0L)
    val timeLeftText = ChallengeTime.clock(remainingMs)

    // Only show accepted (ACTIVE) members in the horloge leaderboard
    val members = remember(duel.members, duel.challengerMinutes, duel.opponentMinutes, myUid) {
        duel.acceptedMembers().sortedWith(
            compareByDescending<StudyGroupMember> { it.minutes }
                .thenByDescending { it.uid == myUid }
                .thenBy { it.name.lowercase() }
        )
    }
    // Invited friends who haven't accepted yet (shown as pending 30m or expired/resend status chips)
    val pendingOrExpiredMembers = remember(duel.members) {
        duel.pendingOrExpiredInviteMembers()
    }
    val membersByUid = remember(members) {
        members.associateBy { it.uid }
    }

    val youLabel = stringResource(R.string.friend_duel_you)
    val summaryParts = mutableListOf<String>()
    for (m in members.take(3)) {
        val name = if (m.uid == myUid) youLabel else m.name.ifBlank { "?" }.split(" ").first()
        summaryParts.add("$name (${formatStudyMinutesText(m.minutes)})")
    }
    val horlogeSummaryText = summaryParts.joinToString(" · ")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullScreenChat) Modifier.fillMaxHeight() else Modifier)
            .testTag("active_friend_duel_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullScreenChat) Modifier.fillMaxHeight() else Modifier)
                .padding(if (compact || fullScreenChat) Spacing.xs else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            // Unified Compact Top Bar: Avatar cluster + Title/Horloge Summary Toggle + Add Friends + Overflow Menu
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left/Center clickable area toggles the collapsible Horloge member leaderboard
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !compact) { leaderboardExpanded = !leaderboardExpanded }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                            .testTag("study_group_leaderboard_toggle")
                    ) {
                        if (members.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                members.take(3).forEach { member ->
                                    MessengerMemberAvatar(
                                        name = member.name,
                                        isFocusing = member.isFocusing,
                                        sizeDp = 28
                                    )
                                }
                            }
                        } else {
                            Icon(
                                Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.study_group_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isCreator) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.study_group_creator_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = if (duel.isTargetHoursChallenge) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    contentColor = if (duel.isTargetHoursChallenge) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = if (duel.isTargetHoursChallenge) {
                                            stringResource(R.string.study_group_mode_badge_target, durationText(duel.targetMinutes))
                                        } else {
                                            stringResource(R.string.study_group_mode_badge_open)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                                if (!isImeVisible) {
                                    Text(
                                        text = "· $timeLeftText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (leaderboardExpanded) {
                                        stringResource(R.string.study_group_leaderboard_toggle_hide)
                                    } else {
                                        horlogeSummaryText.ifEmpty {
                                            stringResource(R.string.study_group_leaderboard_toggle_show, members.size)
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!compact) {
                                    Icon(
                                        imageVector = if (leaderboardExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = if (leaderboardExpanded) {
                                            stringResource(R.string.study_group_leaderboard_toggle_hide)
                                        } else {
                                            stringResource(R.string.study_group_leaderboard_toggle_show, members.size)
                                        },
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Right compact actions: Add Friends + More Actions Menu (Clear Chat, Leave, Delete)
                    if (!compact) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (onAddFriends != null) {
                                FilledTonalIconButton(
                                    onClick = onAddFriends,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("study_group_add_friends_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.PersonAdd,
                                        contentDescription = stringResource(R.string.study_group_add_friends),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { moreMenuExpanded = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("study_group_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(R.string.study_group_title),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = moreMenuExpanded,
                                    onDismissRequest = { moreMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (leaderboardExpanded) {
                                                    stringResource(R.string.study_group_leaderboard_toggle_hide)
                                                } else {
                                                    stringResource(R.string.study_group_leaderboard_toggle_show, members.size)
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Schedule, contentDescription = null)
                                        },
                                        onClick = {
                                            moreMenuExpanded = false
                                            leaderboardExpanded = !leaderboardExpanded
                                        }
                                    )
                                    if (onAddFriends != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.study_group_add_friends)) },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                                            },
                                            onClick = {
                                                moreMenuExpanded = false
                                                onAddFriends()
                                            }
                                        )
                                    }
                                    if (isCreator && duel.messages.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.study_group_clear_chat)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.DeleteSweep,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                moreMenuExpanded = false
                                                confirmClearChat = true
                                            },
                                            modifier = Modifier.testTag("study_group_clear_chat_button")
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.study_group_leave)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Flag,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            moreMenuExpanded = false
                                            confirmSurrender = true
                                        },
                                        modifier = Modifier.testTag("surrender_friend_duel_button")
                                    )
                                    if (isCreator) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.study_group_delete_group),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.DeleteForever,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                moreMenuExpanded = false
                                                confirmDeleteGroup = true
                                            },
                                            modifier = Modifier.testTag("study_group_delete_group_button")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pending (30m countdown) & Expired (Resend) status chips for invited friends (auto-hidden when keyboard is open)
            if (pendingOrExpiredMembers.isNotEmpty() && !compact && !isImeVisible) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_group_pending_invites_row")
                ) {
                    pendingOrExpiredMembers.forEach { invMember ->
                        val invName = invMember.name.ifBlank { stringResource(R.string.community_unnamed) }
                        val isValid = invMember.isInviteValid(now, duel.createdAt)
                        if (isValid) {
                            val remClock = ChallengeTime.clock(invMember.inviteRemainingMs(now, duel.createdAt))
                            val chipLabel = if (invMember.roleStatus == StudyMemberRoleStatus.DECLINED) {
                                stringResource(R.string.study_group_declined_status_chip, invName, remClock)
                            } else {
                                stringResource(R.string.study_group_pending_status_chip, invName, remClock)
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.testTag("pending_invite_chip_${invMember.uid}")
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
                                )
                            }
                        } else {
                            // 30-minute invite expired -> one-tap Resend chip
                            Surface(
                                onClick = { onResendInvite(duel, invMember.uid, invName) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.testTag("expired_invite_chip_${invMember.uid}")
                            ) {
                                Text(
                                    text = stringResource(R.string.study_group_expired_status_chip, invName),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Collapsible Accepted Members Live Progress List (auto-collapsed when keyboard opens)
            AnimatedVisibility(
                visible = (compact || leaderboardExpanded) && !(fullScreenChat && isImeVisible),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val memberScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fullScreenChat) Modifier.heightIn(max = 185.dp).verticalScroll(memberScroll)
                            else Modifier
                        ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    members.forEachIndexed { index, member ->
                        val isMe = member.uid == myUid || (myUid.isEmpty() && member.uid == duel.challengerUid)
                        val displayName = if (isMe) {
                            stringResource(R.string.friend_duel_you)
                        } else {
                            member.name.ifBlank { stringResource(R.string.community_unnamed) }
                        }
                        val cooldownEnd = nudgeCooldownUntil[member.uid] ?: 0L
                        val onCooldown = now < cooldownEnd
                        val cooldownRemainingSec = if (onCooldown) {
                            (((cooldownEnd - now).coerceAtLeast(0L) + 999L) / 1000L).toInt()
                        } else 0
                        StudyGroupMemberRow(
                            rank = index + 1,
                            member = member,
                            displayName = displayName,
                            isMe = isMe,
                            targetMinutes = duel.targetMinutes,
                            onCooldown = onCooldown,
                            cooldownRemainingSec = cooldownRemainingSec,
                            onNudge = {
                                onNudgeFriend(duel, member.uid, displayName)
                            }
                        )
                    }
                }
            }

            if (!compact) {
                // Messenger-Style Real-Time Group Chat Section (takes 100% of remaining vertical height)
                MessengerGroupChatSection(
                    duel = duel,
                    myUid = myUid,
                    membersByUid = membersByUid,
                    messageDraft = messageDraft,
                    onDraftChange = { messageDraft = it },
                    onSendMessage = { text ->
                        onSendMessage(duel, text)
                    },
                    isImeVisible = isImeVisible,
                    fillSpace = fullScreenChat,
                    modifier = if (fullScreenChat) Modifier.weight(1f) else Modifier
                )
            } else if (onOpenDetails != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenDetails) {
                        Text(stringResource(R.string.challenges_history))
                    }
                    OutlinedButton(
                        onClick = { confirmSurrender = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("surrender_friend_duel_button")
                    ) {
                        Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
                        Spacer(Modifier.size(Spacing.xxs))
                        Text(stringResource(R.string.study_group_leave))
                    }
                }
            }
        }
    }

    if (confirmSurrender) {
        ConfirmDialog(
            title = stringResource(R.string.study_group_leave_title),
            body = stringResource(R.string.study_group_leave_body),
            confirmLabel = stringResource(R.string.study_group_leave),
            dismissLabel = stringResource(R.string.focus_keep_going),
            onConfirm = {
                confirmSurrender = false
                onSurrender(duel)
            },
            onDismiss = { confirmSurrender = false },
            destructive = true
        )
    }

    if (confirmClearChat) {
        ConfirmDialog(
            title = stringResource(R.string.study_group_clear_chat_title),
            body = stringResource(R.string.study_group_clear_chat_body),
            confirmLabel = stringResource(R.string.study_group_clear_chat),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                confirmClearChat = false
                onClearMessages(duel)
            },
            onDismiss = { confirmClearChat = false },
            destructive = true
        )
    }

    if (confirmDeleteGroup) {
        ConfirmDialog(
            title = stringResource(R.string.study_group_delete_group_title),
            body = stringResource(R.string.study_group_delete_group_body),
            confirmLabel = stringResource(R.string.study_group_delete_group),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                confirmDeleteGroup = false
                onDeleteGroup(duel)
            },
            onDismiss = { confirmDeleteGroup = false },
            destructive = true
        )
    }
}

/**
 * Messenger-style group chat widget with grouped consecutive chat bubbles, standalone jumbo emojis,
 * and a keyboard-docked quick-emoji + message composer.
 */
@Composable
private fun MessengerGroupChatSection(
    duel: FriendChallenge,
    myUid: String,
    membersByUid: Map<String, StudyGroupMember>,
    messageDraft: String,
    onDraftChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    isImeVisible: Boolean = false,
    fillSpace: Boolean = false,
    modifier: Modifier = Modifier
) {
    val visibleMessages = remember(duel.messages) { duel.messages.takeLast(50) }
    val chatScrollState = rememberScrollState()

    // Scroll to the latest message whenever a new message arrives OR when the keyboard opens
    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.id, isImeVisible) {
        if (visibleMessages.isNotEmpty()) {
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillSpace) Modifier.fillMaxHeight() else Modifier)
            .testTag("study_group_messenger_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillSpace) Modifier.fillMaxHeight() else Modifier)
        ) {
            // Messages Stream (occupies all available height above the composer)
            if (visibleMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fillSpace) Modifier.weight(1f) else Modifier.padding(vertical = Spacing.sm)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!isImeVisible) {
                            Text(
                                text = "💬",
                                fontSize = 22.sp
                            )
                        }
                        Text(
                            text = stringResource(R.string.study_group_chat_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (!isImeVisible) {
                            Text(
                                text = stringResource(R.string.study_group_chat_ephemeral_notice),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fillSpace) Modifier.weight(1f)
                            else Modifier.heightIn(min = 140.dp, max = 260.dp)
                        )
                        .verticalScroll(chatScrollState)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    visibleMessages.forEachIndexed { index, msg ->
                        val isMe = msg.senderUid == myUid || (myUid.isEmpty() && msg.senderUid == duel.challengerUid)
                        val prev = visibleMessages.getOrNull(index - 1)
                        val next = visibleMessages.getOrNull(index + 1)
                        val isFirstInGroup = prev == null || prev.isNudgeAlert || prev.senderUid != msg.senderUid
                        val isLastInGroup = next == null || next.isNudgeAlert || next.senderUid != msg.senderUid
                        val senderMember = membersByUid[msg.senderUid]

                        MessengerChatBubbleRow(
                            message = msg,
                            isMe = isMe,
                            isFirstInGroup = isFirstInGroup,
                            isLastInGroup = isLastInGroup,
                            senderIsFocusing = senderMember?.isFocusing == true
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Quick emoji reactions bar + Messenger pill composer (docked directly to bottom / keyboard)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QUICK_STUDY_EMOJIS.forEach { emoji ->
                        Surface(
                            onClick = { onSendMessage(emoji) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.testTag("quick_emoji_$emoji")
                        ) {
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Messenger-style rounded pill composer + Send / Quick Fire button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = onDraftChange,
                        placeholder = {
                            Text(
                                stringResource(R.string.study_group_chat_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val trimmed = messageDraft.trim()
                                if (trimmed.isNotEmpty()) {
                                    onSendMessage(trimmed)
                                    onDraftChange("")
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("study_group_message_input")
                    )

                    FilledIconButton(
                        onClick = {
                            val trimmed = messageDraft.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendMessage(trimmed)
                                onDraftChange("")
                            } else {
                                onSendMessage("🔥")
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("study_group_send_button")
                    ) {
                        if (messageDraft.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.study_group_chat_send)
                            )
                        } else {
                            Text(
                                text = "🔥",
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessengerMemberAvatar(
    name: String,
    isFocusing: Boolean,
    sizeDp: Int = 28
) {
    val initial = remember(name) {
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }
    Box(modifier = Modifier.size(sizeDp.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .size(sizeDp.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (isFocusing) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.extendedColors.success)
                    .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            )
        }
    }
}

private fun isEmojiOnlyMessage(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 8) return false
    if (trimmed in QUICK_STUDY_EMOJIS) return true
    return trimmed.none { it.isLetterOrDigit() }
}

@Composable
private fun MessengerChatBubbleRow(
    message: StudyGroupMessage,
    isMe: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    senderIsFocusing: Boolean
) {
    val timeText = remember(message.createdAt) {
        if (message.createdAt > 0L) {
            DateTimeFormatter.ofPattern("HH:mm")
                .format(Instant.ofEpochMilli(message.createdAt).atZone(ZoneId.systemDefault()))
        } else ""
    }

    if (message.isNudgeAlert) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = if (timeText.isNotEmpty()) "${message.text} · $timeText" else message.text,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                )
            }
        }
        return
    }

    val emojiOnly = remember(message.text) { isEmojiOnlyMessage(message.text) }
    val bubbleShape = remember(isMe, isFirstInGroup, isLastInGroup) {
        if (isMe) {
            RoundedCornerShape(
                topStart = 18.dp,
                bottomStart = 18.dp,
                topEnd = if (isFirstInGroup) 18.dp else 4.dp,
                bottomEnd = if (isLastInGroup) 18.dp else 4.dp
            )
        } else {
            RoundedCornerShape(
                topStart = if (isFirstInGroup) 18.dp else 4.dp,
                bottomStart = if (isLastInGroup) 18.dp else 4.dp,
                topEnd = 18.dp,
                bottomEnd = 18.dp
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirstInGroup) 4.dp else 0.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // Sender name + timestamp above first message in a friend's burst
        if (!isMe && isFirstInGroup) {
            Text(
                text = if (timeText.isNotEmpty()) "${message.senderName} · $timeText" else message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp, bottom = 2.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMe) {
                if (isLastInGroup) {
                    MessengerMemberAvatar(
                        name = message.senderName,
                        isFocusing = senderIsFocusing,
                        sizeDp = 28
                    )
                } else {
                    Spacer(modifier = Modifier.width(28.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (emojiOnly) {
                // Messenger-style jumbo emoji without heavy bubble background
                Text(
                    text = message.text.trim(),
                    fontSize = 28.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else {
                Surface(
                    shape = bubbleShape,
                    color = if (isMe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isMe) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.widthIn(max = 260.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (isMe && isLastInGroup && timeText.isNotEmpty()) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatStudyMinutesText(minutes: Int): String {
    val hrs = minutes / 60
    val rem = minutes % 60
    return if (hrs > 0) {
        stringResource(R.string.study_group_hours_mins_fmt, hrs, rem)
    } else {
        stringResource(R.string.friend_duel_minutes_fmt, minutes)
    }
}

/**
 * Member row in the Study Group leaderboard.
 * Uses horloge (clock) icons to represent hours and quarter-hours focused (1 icon = 1h, ¾ = 45m, ½ = 30m, ¼ = 15m)
 * plus exact time text instead of a relative progress bar.
 */
@Composable
private fun StudyGroupMemberRow(
    rank: Int,
    member: StudyGroupMember,
    displayName: String,
    isMe: Boolean,
    targetMinutes: Int = 0,
    onCooldown: Boolean,
    cooldownRemainingSec: Int = 0,
    onNudge: () -> Unit
) {
    val isFocusing = member.timerState == StudyMemberTimerState.FOCUSING

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isMe) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("study_group_member_row_${member.uid}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (rank == 1 && member.minutes > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (rank == 1 && member.minutes > 0) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = rank.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Live timer state badge (Focusing vs Paused)
                    Surface(
                        shape = CircleShape,
                        color = if (isFocusing) {
                            MaterialTheme.extendedColors.successContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        },
                        contentColor = if (isFocusing) {
                            MaterialTheme.extendedColors.onSuccessContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (isFocusing) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (isFocusing) R.string.study_group_status_focusing else R.string.study_group_status_paused
                                ),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // If this friend's progress is paused or idle, allow sending a sound + vibration Nudge!
                if (!isMe && member.isPausedOrIdle) {
                    FilledTonalButton(
                        onClick = onNudge,
                        enabled = !onCooldown,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Spacing.sm,
                            vertical = 4.dp
                        ),
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .testTag("nudge_friend_${member.uid}")
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = stringResource(R.string.study_group_nudge_a11y, displayName),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val nudgeLabel = if (onCooldown) {
                            "${stringResource(R.string.study_group_nudge_sent)} (${cooldownRemainingSec}s)"
                        } else {
                            stringResource(R.string.study_group_nudge_button)
                        }
                        Text(
                            text = nudgeLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Horloge (clock) icons row: 1 full clock per hour, ¾ for 45m, ½ for 30m, ¼ for 15m + exact time text
            FocusHorlogeIconsRow(
                minutes = member.minutes,
                isMe = isMe,
                targetMinutes = targetMinutes
            )
            if (targetMinutes > 0) {
                val progress = (member.minutes.toFloat() / targetMinutes.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

/**
 * Displays horloge (clock) icons representing hours and quarter-hour fractions focused:
 * - 1 full horloge icon per full hour (60 min)
 * - ¾ horloge icon for 45–59 min remainder
 * - ½ horloge icon for 30–44 min remainder
 * - ¼ horloge icon for 15–29 min remainder
 * - Starter slice horloge icon for 1–14 min when < 1 hour
 * - Empty outline horloge icon for 0 min
 * Followed by the exact time text (e.g., "1h 36m" or "44 min").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusHorlogeIconsRow(
    minutes: Int,
    isMe: Boolean,
    targetMinutes: Int = 0,
    modifier: Modifier = Modifier
) {
    val safeMinutes = minutes.coerceAtLeast(0)
    val fullHours = safeMinutes / 60
    val remainderMins = safeMinutes % 60
    val quarterFraction = when {
        remainderMins >= 45 -> 0.75f
        remainderMins >= 30 -> 0.50f
        remainderMins >= 15 -> 0.25f
        remainderMins > 0 && fullHours == 0 -> (remainderMins / 60f).coerceAtLeast(0.12f)
        else -> 0f
    }

    val activeColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("horloge_progress_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (safeMinutes == 0) {
                HorlogeFractionIcon(
                    fraction = 0f,
                    activeColor = trackColor,
                    trackColor = trackColor.copy(alpha = 0.5f),
                    size = 18.dp
                )
            } else {
                val visibleFullHours = fullHours.coerceAtMost(8)
                repeat(visibleFullHours) {
                    HorlogeFractionIcon(
                        fraction = 1f,
                        activeColor = activeColor,
                        trackColor = trackColor,
                        size = 18.dp
                    )
                }
                if (fullHours > 8) {
                    Surface(
                        shape = CircleShape,
                        color = activeColor.copy(alpha = 0.18f),
                        contentColor = activeColor
                    ) {
                        Text(
                            text = "+${fullHours - 8}h",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (quarterFraction > 0f) {
                    HorlogeFractionIcon(
                        fraction = quarterFraction,
                        activeColor = activeColor,
                        trackColor = trackColor,
                        size = 18.dp
                    )
                }
            }
        }

        Spacer(Modifier.width(Spacing.xs))

        Text(
            text = if (targetMinutes > 0) {
                "${formatStudyMinutesText(safeMinutes)} / ${durationText(targetMinutes)}"
            } else {
                formatStudyMinutesText(safeMinutes)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Custom Canvas-drawn Horloge (clock) icon showing a full hour (`1.0f`),
 * three-quarter hour (`0.75f`), half hour (`0.50f`), quarter hour (`0.25f`), or empty (`0.0f`).
 */
@Composable
fun HorlogeFractionIcon(
    fraction: Float,
    activeColor: Color,
    trackColor: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(size)) {
        val strokePx = 1.6.dp.toPx()
        val radius = (this.size.minDimension - strokePx) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Subtle clock face background
        drawCircle(
            color = if (clamped >= 1f) activeColor.copy(alpha = 0.22f) else trackColor.copy(alpha = 0.16f),
            radius = radius,
            center = center
        )

        // Filled pie sector for the focused fraction (12 o'clock = -90 degrees)
        if (clamped > 0f) {
            val pieInset = strokePx * 0.9f
            val pieDiameter = (radius - pieInset) * 2f
            drawArc(
                color = if (clamped >= 1f) activeColor.copy(alpha = 0.82f) else activeColor.copy(alpha = 0.72f),
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = true,
                topLeft = Offset(center.x - pieDiameter / 2f, center.y - pieDiameter / 2f),
                size = Size(pieDiameter, pieDiameter)
            )
        }

        // Outer clock bezel ring
        drawCircle(
            color = if (clamped > 0f) activeColor else trackColor,
            radius = radius,
            center = center,
            style = Stroke(width = strokePx)
        )

        // 12, 3, 6, 9 o'clock cardinal ticks
        val tickLength = radius * 0.2f
        for (angleDeg in listOf(-90.0, 0.0, 90.0, 180.0)) {
            val rad = Math.toRadians(angleDeg)
            val startR = radius - strokePx * 0.5f
            val endR = radius - tickLength
            drawLine(
                color = if (clamped > 0f) activeColor else trackColor,
                start = Offset(
                    x = center.x + (startR * cos(rad)).toFloat(),
                    y = center.y + (startR * sin(rad)).toFloat()
                ),
                end = Offset(
                    x = center.x + (endR * cos(rad)).toFloat(),
                    y = center.y + (endR * sin(rad)).toFloat()
                ),
                strokeWidth = strokePx * 0.7f,
                cap = StrokeCap.Round
            )
        }

        // Clock hands: 12 o'clock reference hand + minute hand pointing to the fraction angle
        val handColor = if (clamped >= 0.75f) Color.White else activeColor
        val minuteAngleDeg = if (clamped > 0f && clamped < 1f) {
            -90.0 + 360.0 * clamped
        } else {
            0.0 // 3 o'clock hand for full hour icon so it looks like a classic clock
        }
        val minuteRad = Math.toRadians(minuteAngleDeg)

        // Vertical 12 o'clock hand
        drawLine(
            color = handColor,
            start = center,
            end = Offset(center.x, center.y - radius * 0.55f),
            strokeWidth = strokePx * 0.85f,
            cap = StrokeCap.Round
        )

        // Sweep / hour hand
        drawLine(
            color = handColor,
            start = center,
            end = Offset(
                x = center.x + (radius * 0.5f * cos(minuteRad)).toFloat(),
                y = center.y + (radius * 0.5f * sin(minuteRad)).toFloat()
            ),
            strokeWidth = strokePx * 0.85f,
            cap = StrokeCap.Round
        )

        // Center pin
        drawCircle(
            color = handColor,
            radius = strokePx * 0.7f,
            center = center
        )
    }
}

private val QUICK_TARGET_HOURS_MINUTES = listOf(60, 120, 180, 240, 360)

/**
 * Reusable selector for choosing between an Open Whole-Day Challenge (`targetMinutes == 0`)
 * and a Target-Hours Race (`targetMinutes > 0`) with quick hour chips (1h, 2h, 3h, 4h, 6h)
 * plus custom hours & minutes inputs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChallengeInviteModeSelector(
    isTargetMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    hoursInput: String,
    onHoursChange: (String) -> Unit,
    minutesInput: String,
    onMinutesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMinutes = (hoursInput.toIntOrNull() ?: 0) * 60 + (minutesInput.toIntOrNull() ?: 0)
    val durationValid = totalMinutes in 1..ChallengeTime.MAX_MINUTES

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag("challenge_invite_mode_selector")
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.challenge_invite_mode_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isTargetMode,
                    onClick = { onModeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("invite_mode_open_button")
                ) {
                    Text(
                        text = stringResource(R.string.challenge_invite_mode_open),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = isTargetMode,
                    onClick = { onModeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("invite_mode_target_button")
                ) {
                    Text(
                        text = stringResource(R.string.challenge_invite_mode_target),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(
                visible = isTargetMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        QUICK_TARGET_HOURS_MINUTES.forEach { presetMins ->
                            FilterChip(
                                selected = totalMinutes == presetMins,
                                onClick = {
                                    onHoursChange((presetMins / 60).toString())
                                    onMinutesChange((presetMins % 60).toString())
                                },
                                label = { Text(durationText(presetMins)) },
                                modifier = Modifier.testTag("quick_target_chip_$presetMins")
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(
                            value = hoursInput,
                            onValueChange = { v -> onHoursChange(v.filter(Char::isDigit).take(2)) },
                            label = { Text(stringResource(R.string.challenge_hours)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("target_hours_input")
                        )
                        OutlinedTextField(
                            value = minutesInput,
                            onValueChange = { v -> onMinutesChange(v.filter(Char::isDigit).take(2)) },
                            label = { Text(stringResource(R.string.challenge_minutes)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("target_minutes_input")
                        )
                    }
                    if (!durationValid) {
                        Text(
                            text = stringResource(R.string.challenge_duration_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = if (isTargetMode && durationValid) {
                    stringResource(R.string.challenge_invite_target_desc, durationText(totalMinutes))
                } else {
                    stringResource(R.string.challenge_invite_open_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Multi-friend selection bottom sheet to start a new Study Group or invite additional friends.
 * Fully safe-area and 3-button navigation bar compliant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyGroupFriendsSheet(
    friends: List<StudyFriendOption>,
    alreadyInGroupUids: Set<String> = emptySet(),
    isAddingToExisting: Boolean = false,
    onConfirmSelection: (List<StudyFriendOption>, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val selectableFriends = remember(friends, alreadyInGroupUids) {
        friends.filter { it.uid !in alreadyInGroupUids }
    }
    val selectedUids = remember { mutableStateListOf<String>() }
    var isTargetMode by rememberSaveable { mutableStateOf(false) }
    var hoursInput by rememberSaveable { mutableStateOf("2") }
    var minutesInput by rememberSaveable { mutableStateOf("0") }
    val targetTotalMinutes = (hoursInput.toIntOrNull() ?: 0) * 60 + (minutesInput.toIntOrNull() ?: 0)
    val durationValid = !isTargetMode || targetTotalMinutes in 1..ChallengeTime.MAX_MINUTES

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.study_group_select_friends_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.study_group_select_friends_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isAddingToExisting) {
                ChallengeInviteModeSelector(
                    isTargetMode = isTargetMode,
                    onModeChange = { isTargetMode = it },
                    hoursInput = hoursInput,
                    onHoursChange = { hoursInput = it },
                    minutesInput = minutesInput,
                    onMinutesChange = { minutesInput = it }
                )
            }

            if (selectableFriends.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.study_group_no_friends_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    selectableFriends.forEach { friend ->
                        val isSelected = friend.uid in selectedUids
                        val name = friend.username.ifBlank { stringResource(R.string.community_unnamed) }
                        ListItem(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    if (isSelected) selectedUids.remove(friend.uid) else selectedUids.add(friend.uid)
                                },
                            leadingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (friend.uid !in selectedUids) selectedUids.add(friend.uid)
                                        } else {
                                            selectedUids.remove(friend.uid)
                                        }
                                    }
                                )
                            },
                            headlineContent = {
                                Text(name, fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.friend_duel_minutes_fmt, friend.todayMinutes) +
                                        " · " + stringResource(
                                            if (friend.timerState == StudyMemberTimerState.FOCUSING) {
                                                R.string.study_group_status_focusing
                                            } else {
                                                R.string.study_group_status_paused
                                            }
                                        )
                                )
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val chosen = selectableFriends.filter { it.uid in selectedUids }
                    if (chosen.isNotEmpty() && durationValid) {
                        onConfirmSelection(chosen, if (!isAddingToExisting && isTargetMode) targetTotalMinutes else 0)
                    }
                },
                enabled = selectedUids.isNotEmpty() && durationValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.buttonHeight)
                    .testTag("confirm_study_group_friends_button")
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(
                        if (isAddingToExisting) R.string.study_group_add_selected else R.string.study_group_launch_selected,
                        selectedUids.size
                    )
                )
            }
        }
    }
}

/**
 * Celebrates a Friend Challenge victory with confetti, themed sound, and a victory modal.
 */
@Composable
fun FriendDuelVictoryCelebration(
    win: FriendChallenge?,
    myUid: String,
    onAcknowledge: (String) -> Unit
) {
    val context = LocalContext.current
    val focusStyle = LocalFocusStyle.current
    LaunchedEffect(win?.id) {
        if (win != null) {
            playSuccessSound(context, focusStyle.successSound)
        }
    }
    Celebration(trigger = win?.id)
    if (win != null) {
        val friendName = win.friendName(myUid).ifBlank { stringResource(R.string.community_unnamed) }
        val myMins = win.myMinutes(myUid)
        val friendMins = win.friendMinutes(myUid)
        val bodyText = when {
            win.didFriendSurrender(myUid) ->
                stringResource(R.string.friend_duel_won_by_surrender_body, friendName, myMins, friendMins)
            win.isTargetHoursChallenge ->
                stringResource(R.string.friend_duel_won_by_target_body, durationText(win.targetMinutes), friendName, myMins, friendMins)
            else ->
                stringResource(R.string.friend_duel_won_by_minutes_body, friendName, myMins, friendMins)
        }
        AlertDialog(
            onDismissRequest = { onAcknowledge(win.id) },
            icon = {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.success,
                    modifier = Modifier.size(Spacing.iconLg)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.friend_duel_won_title),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAcknowledge(win.id) },
                    modifier = Modifier.testTag("acknowledge_victory_button")
                ) {
                    Text(stringResource(R.string.challenge_dismiss))
                }
            }
        )
    }
}

/**
 * Single row in the Friend Duels History section on the Challenges/Défi screen.
 */
@Composable
fun FriendDuelHistoryRow(
    duel: FriendChallenge,
    myUid: String
) {
    val friendName = duel.friendName(myUid).ifBlank { stringResource(R.string.community_unnamed) }
    val myMins = duel.myMinutes(myUid)
    val friendMins = duel.friendMinutes(myUid)
    val won = duel.didIWin(myUid)
    val surrendered = duel.didISurrender(myUid)
    val tied = duel.winnerUid == "tie"
    val noWinner = duel.winnerUid == "none"

    val dayText = remember(duel.createdAt, duel.dayKey) {
        if (duel.createdAt > 0L) {
            DateTimeFormatter.ofPattern("EEE d MMM")
                .format(Instant.ofEpochMilli(duel.createdAt).atZone(ZoneId.systemDefault()))
        } else duel.dayKey
    }

    val (badgeLabel, badgeContainer, badgeContent) = when {
        won -> Triple(
            stringResource(R.string.friend_duel_badge_won),
            MaterialTheme.extendedColors.successContainer,
            MaterialTheme.extendedColors.onSuccessContainer
        )
        surrendered -> Triple(
            stringResource(R.string.friend_duel_badge_surrendered),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        noWinner -> Triple(
            stringResource(R.string.friend_duel_badge_no_winner),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        tied -> Triple(
            stringResource(R.string.friend_duel_badge_tie),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Triple(
            stringResource(R.string.friend_duel_badge_lost),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    val modePrefix = if (duel.isTargetHoursChallenge) {
        stringResource(R.string.study_group_mode_badge_target, durationText(duel.targetMinutes))
    } else {
        stringResource(R.string.study_group_mode_badge_open)
    }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = if (won) Icons.Rounded.EmojiEvents else Icons.Rounded.SportsScore,
                contentDescription = null,
                tint = if (won) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                text = "${stringResource(R.string.friend_duel_you)} vs $friendName",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = "$dayText · $modePrefix · ${stringResource(R.string.friend_duel_history_score, myMins, friendName, friendMins)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = badgeContainer,
                contentColor = badgeContent
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
        }
    )
}
