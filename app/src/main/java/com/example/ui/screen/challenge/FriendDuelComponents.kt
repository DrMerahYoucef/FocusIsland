package com.example.ui.screen.challenge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.challenge.FriendChallenge
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

/**
 * Incoming friend challenge card shown in Community, Focus, and Challenges screens.
 * Accepting starts the Pomodoro timer automatically.
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
 * Live side-by-side Friend Duel progress widget shown next to the Pomodoro timer and in the Challenges screen.
 */
@Composable
fun FriendDuelLiveCard(
    duel: FriendChallenge,
    myUid: String,
    onSurrender: (FriendChallenge) -> Unit,
    onOpenDetails: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var confirmSurrender by rememberSaveable { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis(), duel.id) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
    }
    val remainingMs = (duel.endAt - now).coerceAtLeast(0L)
    val timeLeftText = ChallengeTime.clock(remainingMs)
    val myMins = duel.myMinutes(myUid)
    val friendMins = duel.friendMinutes(myUid)
    val friendName = duel.friendName(myUid).ifBlank { stringResource(R.string.community_unnamed) }

    val totalMins = (myMins + friendMins).coerceAtLeast(1)
    val targetFraction = if (myMins == 0 && friendMins == 0) 0.5f else (myMins.toFloat() / totalMins).coerceIn(0.08f, 0.92f)
    val animatedMyShare by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 450),
        label = "duelShare"
    )

    val leadText = when {
        myMins > friendMins -> stringResource(R.string.friend_duel_lead_you, myMins - friendMins)
        friendMins > myMins -> stringResource(R.string.friend_duel_lead_friend, friendName, friendMins - myMins)
        else -> stringResource(R.string.friend_duel_tied)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().testTag("active_friend_duel_card")
    ) {
        Column(
            modifier = Modifier.padding(if (compact) Spacing.sm else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Icon(
                        Icons.Rounded.SportsScore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Spacing.iconSm)
                    )
                    Text(
                        text = stringResource(R.string.friend_duel_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.friend_duel_subtitle_ends_midnight, timeLeftText),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Side-by-side score readout: You vs Friend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = stringResource(R.string.friend_duel_you),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.friend_duel_minutes_fmt, myMins),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = leadText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (myMins >= friendMins) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = friendName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.friend_duel_minutes_fmt, friendMins),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Comparative progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedMyShare)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenDetails != null) {
                    TextButton(onClick = onOpenDetails) {
                        Text(stringResource(R.string.challenges_history))
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
                    Text(stringResource(R.string.friend_duel_surrender))
                }
            }
        }
    }

    if (confirmSurrender) {
        ConfirmDialog(
            title = stringResource(R.string.friend_duel_surrender_title),
            body = stringResource(R.string.friend_duel_surrender_body, friendName),
            confirmLabel = stringResource(R.string.friend_duel_surrender),
            dismissLabel = stringResource(R.string.focus_keep_going),
            onConfirm = {
                confirmSurrender = false
                onSurrender(duel)
            },
            onDismiss = { confirmSurrender = false },
            destructive = true
        )
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
