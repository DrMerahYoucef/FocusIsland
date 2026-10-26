package com.example.ui.screen.challenge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.StudyFriendOption
import com.example.data.challenge.StudyGroupMember
import com.example.data.challenge.StudyGroupMessage
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

private val QUICK_STUDY_EMOJIS = listOf("🔥", "💪", "📚", "🎯", "☕", "👏", "⚡", "🚀")

/**
 * Incoming friend / study group challenge card shown in Community and Challenges screens.
 * Accepting joins the study group and starts the Pomodoro timer automatically.
 */
@Composable
fun FriendDuelIncomingCard(
    challenge: FriendChallenge,
    onAccept: (FriendChallenge) -> Unit,
    onDecline: (FriendChallenge) -> Unit,
    modifier: Modifier = Modifier
) {
    val challenger = challenge.challengerName.ifBlank { stringResource(R.string.community_unnamed) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().testTag("incoming_friend_duel_card")
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
            }
            Text(
                text = stringResource(R.string.friend_duel_incoming_body),
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
 * Live Multi-Friend Study Group Challenge card shown on the Challenges screen:
 * - Displays all members' focus progress and live timer status (Focusing vs Paused)
 * - Lets members invite more friends to the group anytime
 * - Provides a Nudge button next to paused/idle friends to trigger a notification sound & vibration on their phone
 * - Includes a Messenger-style real-time group chat whose messages are automatically deleted when the challenge ends or is deleted by its creator
 */
@Composable
fun FriendDuelLiveCard(
    duel: FriendChallenge,
    myUid: String,
    onSurrender: (FriendChallenge) -> Unit,
    onSendMessage: (FriendChallenge, String) -> Unit = { _, _ -> },
    onNudgeFriend: (FriendChallenge, String, String) -> Unit = { _, _, _ -> },
    onClearMessages: (FriendChallenge) -> Unit = {},
    onDeleteGroup: (FriendChallenge) -> Unit = {},
    onAddFriends: (() -> Unit)? = null,
    nudgeCooldownUntil: Map<String, Long> = emptyMap(),
    onOpenDetails: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var confirmSurrender by rememberSaveable { mutableStateOf(false) }
    var confirmClearChat by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteGroup by rememberSaveable { mutableStateOf(false) }
    var messageDraft by rememberSaveable(duel.id) { mutableStateOf("") }

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

    val members = remember(duel.members, duel.challengerMinutes, duel.opponentMinutes, myUid) {
        duel.activeAndInvitedMembers().sortedWith(
            compareByDescending<StudyGroupMember> { it.minutes }
                .thenByDescending { it.uid == myUid }
                .thenBy { it.name.lowercase() }
        )
    }
    val membersByUid = remember(members) {
        members.associateBy { it.uid }
    }
    val maxMemberMins = remember(members) {
        (members.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(30)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().testTag("active_friend_duel_card")
    ) {
        Column(
            modifier = Modifier.padding(if (compact) Spacing.sm else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header: Study Group Title + Add Friends button
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
                    Icon(
                        Icons.Rounded.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Spacing.icon)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Text(
                                text = stringResource(R.string.study_group_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
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
                                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.friend_duel_subtitle_ends_midnight, timeLeftText),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onAddFriends != null && !compact) {
                    FilledTonalButton(
                        onClick = onAddFriends,
                        modifier = Modifier.testTag("study_group_add_friends_button")
                    ) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(Spacing.iconSm)
                        )
                        Spacer(Modifier.size(Spacing.xxs))
                        Text(stringResource(R.string.study_group_add_friends))
                    }
                }
            }

            // Members Live Progress List
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                        maxMinutes = maxMemberMins,
                        onCooldown = onCooldown,
                        cooldownRemainingSec = cooldownRemainingSec,
                        onNudge = {
                            onNudgeFriend(duel, member.uid, displayName)
                        }
                    )
                }
            }

            if (!compact) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Messenger-Style Real-Time Group Chat Section
                MessengerGroupChatSection(
                    duel = duel,
                    myUid = myUid,
                    isCreator = isCreator,
                    members = members,
                    membersByUid = membersByUid,
                    messageDraft = messageDraft,
                    onDraftChange = { messageDraft = it },
                    onSendMessage = { text ->
                        onSendMessage(duel, text)
                    },
                    onRequestClearChat = { confirmClearChat = true }
                )
            }

            // Actions row (Abandon / Leave or Creator Delete Challenge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenDetails != null) {
                    TextButton(onClick = onOpenDetails) {
                        Text(stringResource(R.string.challenges_history))
                    }
                } else if (isCreator && !compact) {
                    TextButton(
                        onClick = { confirmDeleteGroup = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("study_group_delete_group_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(Spacing.iconSm)
                        )
                        Spacer(Modifier.size(Spacing.xxs))
                        Text(stringResource(R.string.study_group_delete_group))
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
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
 * Messenger-style group chat widget with member avatars, active green dots, grouped consecutive
 * chat bubbles, standalone jumbo emojis, and an ephemeral auto-delete notice.
 */
@Composable
private fun MessengerGroupChatSection(
    duel: FriendChallenge,
    myUid: String,
    isCreator: Boolean,
    members: List<StudyGroupMember>,
    membersByUid: Map<String, StudyGroupMember>,
    messageDraft: String,
    onDraftChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onRequestClearChat: () -> Unit
) {
    val visibleMessages = remember(duel.messages) { duel.messages.takeLast(30) }
    val chatScrollState = rememberScrollState()

    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.id) {
        if (visibleMessages.isNotEmpty()) {
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("study_group_messenger_container")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Messenger-style top chat bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    // Member avatar cluster with green active dots
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        members.take(4).forEach { member ->
                            MessengerMemberAvatar(
                                name = member.name,
                                isFocusing = member.isFocusing,
                                sizeDp = 28
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = stringResource(R.string.study_group_chat_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.study_group_chat_ephemeral_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isCreator && visibleMessages.isNotEmpty()) {
                    IconButton(
                        onClick = onRequestClearChat,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("study_group_clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.study_group_clear_chat),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Messages Stream
            if (visibleMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(
                        text = "💬",
                        fontSize = 26.sp
                    )
                    Text(
                        text = stringResource(R.string.study_group_chat_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.study_group_chat_ephemeral_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Quick emoji reactions bar + Messenger pill composer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
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
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Messenger-style rounded pill composer + Send / Quick Fire button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                                fontSize = 20.sp
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
private fun StudyGroupMemberRow(
    rank: Int,
    member: StudyGroupMember,
    displayName: String,
    isMe: Boolean,
    maxMinutes: Int,
    onCooldown: Boolean,
    cooldownRemainingSec: Int = 0,
    onNudge: () -> Unit
) {
    val fraction = (member.minutes.toFloat() / maxMinutes.coerceAtLeast(1)).coerceIn(0.06f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (member.minutes == 0) 0.04f else fraction,
        animationSpec = tween(durationMillis = 400),
        label = "memberProgress"
    )
    val isFocusing = member.timerState == StudyMemberTimerState.FOCUSING

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isMe) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.friend_duel_minutes_fmt, member.minutes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

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
                                .heightIn(min = 36.dp)
                                .testTag("nudge_friend_${member.uid}")
                        ) {
                            Icon(
                                Icons.Rounded.NotificationsActive,
                                contentDescription = stringResource(R.string.study_group_nudge_a11y, displayName),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            val nudgeLabel = if (onCooldown) {
                                "${stringResource(R.string.study_group_nudge_sent)} (${cooldownRemainingSec}s)"
                            } else {
                                stringResource(R.string.study_group_nudge_button)
                            }
                            Text(
                                text = nudgeLabel,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Individual progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .background(
                            if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                )
            }
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
    onConfirmSelection: (List<StudyFriendOption>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectableFriends = remember(friends, alreadyInGroupUids) {
        friends.filter { it.uid !in alreadyInGroupUids }
    }
    val selectedUids = remember { mutableStateListOf<String>() }

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
                    if (chosen.isNotEmpty()) {
                        onConfirmSelection(chosen)
                    }
                },
                enabled = selectedUids.isNotEmpty(),
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
        val bodyText = if (win.didFriendSurrender(myUid)) {
            stringResource(R.string.friend_duel_won_by_surrender_body, friendName, myMins, friendMins)
        } else {
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
                text = "$dayText · ${stringResource(R.string.friend_duel_history_score, myMins, friendName, friendMins)}",
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
