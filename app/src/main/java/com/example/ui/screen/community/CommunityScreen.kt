package com.example.ui.screen.community

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.FriendChallengeState
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.screen.challenge.FriendDuelIncomingCard
import com.example.ui.screen.challenge.FriendDuelVictoryCelebration
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.LoadingState
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.StatTile
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.IslandColors
import com.example.ui.theme.LocalFocusStyle
import com.example.ui.components.IslandRenderer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.abs
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import java.text.NumberFormat

/** How the signed-in user relates to someone in a list. Decides the row action. */
private enum class Relation { ME, FRIEND, SENT, RECEIVED, NONE }

private class Relations(
    val me: String,
    friends: List<UserProfile>,
    val sent: List<FriendRequest>,
    val received: List<FriendRequest>
) {
    private val friendIds = friends.mapTo(HashSet()) { it.uid }
    fun of(uid: String) = when {
        uid == me -> Relation.ME
        uid in friendIds -> Relation.FRIEND
        received.any { it.fromUid == uid } -> Relation.RECEIVED
        sent.any { it.toUid == uid } -> Relation.SENT
        else -> Relation.NONE
    }
}

private class CommunityActions(
    val onAdd: (uid: String, name: String) -> Unit,
    val onAccept: (FriendRequest) -> Unit,
    val onDecline: (FriendRequest) -> Unit,
    val onCancel: (FriendRequest) -> Unit,
    val onRemove: (UserProfile) -> Unit
)

/** Community tab: Leaderboard (default) and Friends (archipelago, requests, friends). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel,
    modifier: Modifier = Modifier
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val pending by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val sent by viewModel.sentRequests.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val shareListening by viewModel.shareListening.collectAsStateWithLifecycle()
    val friendChallengeState by viewModel.friendChallengeState.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is CommunityMessage.RequestSent -> context.getString(R.string.community_msg_request_sent, message.name)
                is CommunityMessage.NowFriends -> context.getString(R.string.community_msg_now_friends, message.name)
                is CommunityMessage.FriendRemoved -> context.getString(R.string.community_msg_removed, message.name)
                is CommunityMessage.ChallengeSent -> context.getString(R.string.community_msg_challenge_sent, message.name)
                CommunityMessage.ChallengeAccepted -> context.getString(R.string.community_msg_challenge_accepted)
                CommunityMessage.RequestDeclined -> context.getString(R.string.community_msg_declined)
                CommunityMessage.RequestCancelled -> context.getString(R.string.community_msg_cancelled)
                CommunityMessage.Failed -> context.getString(R.string.community_msg_failed)
            }
            snackbar.showSnackbar(text)
        }
    }

    val relations = Relations(viewModel.currentUid, friends, sent, pending)
    val actions = remember(viewModel) {
        CommunityActions(
            onAdd = viewModel::sendFriendRequest,
            onAccept = viewModel::acceptRequest,
            onDecline = viewModel::declineRequest,
            onCancel = viewModel::cancelRequest,
            onRemove = viewModel::removeFriend
        )
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var selectedFriendId by rememberSaveable { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.community_tab_leaderboard)) })
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        BadgedBox(badge = { if (pending.isNotEmpty()) Badge { Text(pending.size.toString()) } }) {
                            Text(stringResource(R.string.community_tab_friends))
                        }
                    }
                )
            }
            when (tab) {
                0 -> LeaderboardPane(
                    state = leaderboard,
                    sort = sort,
                    onSort = viewModel::setSort,
                    myProfile = myProfile,
                    relations = relations,
                    actions = actions,
                    incomingDuels = friendChallengeState.incomingPending,
                    onAcceptDuel = viewModel::acceptFriendChallenge,
                    onDeclineDuel = viewModel::declineFriendChallenge,
                    onEntryClick = { entry ->
                        if (entry.uid != viewModel.currentUid) {
                            selectedFriendId = entry.uid
                        }
                    }
                )
                else -> FriendsPane(
                    me = myProfile,
                    friends = friends,
                    relations = relations,
                    actions = actions,
                    shareListening = shareListening,
                    onShareListening = viewModel::setShareListening,
                    onAddFriends = { showSearch = true },
                    onFriendClick = { selectedFriendId = it.uid },
                    incomingDuels = friendChallengeState.incomingPending,
                    onAcceptDuel = viewModel::acceptFriendChallenge,
                    onDeclineDuel = viewModel::declineFriendChallenge
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        FriendDuelVictoryCelebration(
            win = friendChallengeState.uncelebratedWin,
            myUid = friendChallengeState.myUid.ifEmpty { viewModel.currentUid },
            onAcknowledge = viewModel::acknowledgeFriendWin
        )
    }

    if (showSearch) {
        AddFriendsSheet(
            results = searchResults,
            searching = searching,
            relations = relations,
            actions = actions,
            onSearch = viewModel::searchUsers,
            onDismiss = {
                showSearch = false
                viewModel.clearSearch()
            }
        )
    }

    val selectedProfile = remember(selectedFriendId, friends, leaderboard.entries) {
        val id = selectedFriendId ?: return@remember null
        friends.firstOrNull { it.uid == id }
            ?: leaderboard.entries.firstOrNull { it.uid == id }?.let {
                UserProfile(
                    uid = it.uid,
                    username = it.username,
                    treeCount = it.treeCount,
                    totalMinutes = it.totalMinutes,
                    points = it.points,
                    currentStreak = it.currentStreak
                )
            }
    }

    selectedProfile?.let { friend ->
        val isFriend = relations.of(friend.uid) == Relation.FRIEND
        val activeDuel = friendChallengeState.activeDuel
        val currentNowMs = System.currentTimeMillis()
        val isAlreadyInGroup = activeDuel?.hasActiveParticipant(friend.uid) == true
        val hasValidPendingInviteInGroup = activeDuel?.hasValidInvite(friend.uid, currentNowMs) == true
        val canAddToExistingGroup = activeDuel != null && !isAlreadyInGroup && !hasValidPendingInviteInGroup
        val isPendingChallenge = hasValidPendingInviteInGroup ||
            friendChallengeState.outgoingPending.any { it.hasValidInvite(friend.uid, currentNowMs) }
        FriendSheet(
            friend = friend,
            isFriend = isFriend,
            hasActiveDuel = isAlreadyInGroup,
            canAddToStudyGroup = canAddToExistingGroup,
            isPendingChallenge = isPendingChallenge,
            onChallenge = {
                viewModel.challengeFriend(friend.uid, friend.username)
                selectedFriendId = null
            },
            onRemove = {
                actions.onRemove(friend)
                selectedFriendId = null
            },
            onDismiss = { selectedFriendId = null }
        )
    }
}

// ====================================================================== Leaderboard

@Composable
private fun LeaderboardPane(
    state: LeaderboardState,
    sort: LeaderboardSort,
    onSort: (LeaderboardSort) -> Unit,
    myProfile: UserProfile?,
    relations: Relations,
    actions: CommunityActions,
    incomingDuels: List<FriendChallenge> = emptyList(),
    onAcceptDuel: (FriendChallenge) -> Unit = {},
    onDeclineDuel: (FriendChallenge) -> Unit = {},
    onEntryClick: (LeaderboardEntry) -> Unit = {}
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val visible = remember(state.entries, filter) {
        state.entries.withIndex().filter { filter.isBlank() || it.value.username.contains(filter.trim(), ignoreCase = true) }
    }
    val myRank = state.entries.indexOfFirst { it.uid == relations.me }.takeIf { it >= 0 }?.plus(1)

    LazyColumn(
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        modifier = Modifier.fillMaxSize()
    ) {
        if (incomingDuels.isNotEmpty()) {
            items(incomingDuels, key = { "lb_duel_" + it.id }) { incoming ->
                FriendDuelIncomingCard(
                    challenge = incoming,
                    myUid = relations.me,
                    onAccept = onAcceptDuel,
                    onDecline = onDeclineDuel,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                MyRankCard(rank = myRank, sort = sort, me = myProfile, loading = state.loading)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    LeaderboardSort.entries.forEach { option ->
                        FilterChip(
                            selected = option == sort,
                            onClick = { onSort(option) },
                            label = { Text(stringResource(option.label())) },
                            leadingIcon = if (option == sort) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.community_filter_names)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (filter.isNotEmpty()) {
                        {
                            IconButton(onClick = { filter = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.community_clear))
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        when {
            state.loading -> item { LoadingState(Modifier.height(Spacing.sceneHeight)) }
            state.failed -> item {
                EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.community_leaderboard_error_title),
                    body = stringResource(R.string.community_unavailable_body)
                )
            }
            visible.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Rounded.EmojiEvents,
                    title = stringResource(if (filter.isBlank()) R.string.community_leaderboard_empty_title else R.string.community_no_match_title),
                    body = stringResource(if (filter.isBlank()) R.string.community_leaderboard_empty_body else R.string.community_no_match_body)
                )
            }
            else -> items(visible, key = { it.value.uid }) { (index, entry) ->
                LeaderboardRow(
                    rank = index + 1,
                    entry = entry,
                    sort = sort,
                    relation = relations.of(entry.uid),
                    request = relations.received.firstOrNull { it.fromUid == entry.uid } ?: relations.sent.firstOrNull { it.toUid == entry.uid },
                    actions = actions,
                    onClick = { onEntryClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun MyRankCard(rank: Int?, sort: LeaderboardSort, me: UserProfile?, loading: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.community_your_rank), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = when {
                        loading -> "…"
                        rank != null -> stringResource(R.string.community_rank_value, rank)
                        else -> stringResource(R.string.community_not_ranked)
                    },
                    style = if (rank != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium
                )
            }
            if (me != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(metricValue(sort, me.points, me.totalMinutes, me.treeCount, me.currentStreak), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(sort.label()), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    sort: LeaderboardSort,
    relation: Relation,
    request: FriendRequest?,
    actions: CommunityActions,
    onClick: () -> Unit = {}
) {
    val isMe = relation == Relation.ME
    val name = entry.username.ifBlank { stringResource(R.string.community_unnamed) }
    val streak = pluralStringResource(R.plurals.community_streak_days, entry.currentStreak, entry.currentStreak)
    ListItem(
        modifier = if (!isMe) {
            Modifier
                .clickable(onClickLabel = stringResource(R.string.friend_duel_challenge_short), onClick = onClick)
                .testTag("leaderboard_row_${entry.uid}")
        } else {
            Modifier.testTag("leaderboard_row_me")
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isMe) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RankLabel(rank)
                Avatar(name)
            }
        },
        headlineContent = {
            Text(
                text = if (isMe) stringResource(R.string.community_you_suffix, name) else name,
                fontWeight = if (isMe) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.community_row_summary,
                    metricValue(sort, entry.points, entry.totalMinutes, entry.treeCount, entry.currentStreak),
                    stringResource(sort.label()).lowercase(),
                    streak
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = { RelationAction(relation, entry.uid, name, request, actions) }
    )
}

@Composable
private fun RankLabel(rank: Int) {
    val description = stringResource(R.string.community_rank_value, rank)
    Box(
        modifier = Modifier.widthIn(min = Spacing.lg).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (rank <= 3) {
            Icon(
                Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = if (rank == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.icon)
            )
        } else {
            Text(rank.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Trailing action for a person, by relation. Every icon button has a spoken label. */
@Composable
private fun RelationAction(
    relation: Relation,
    uid: String,
    name: String,
    request: FriendRequest?,
    actions: CommunityActions
) {
    when (relation) {
        Relation.ME -> Unit
        Relation.FRIEND -> Icon(
            Icons.Rounded.TaskAlt,
            contentDescription = stringResource(R.string.community_is_friend, name),
            tint = MaterialTheme.colorScheme.primary
        )
        Relation.SENT -> TextButton(onClick = { request?.let(actions.onCancel) }) {
            Text(stringResource(R.string.community_cancel_request))
        }
        Relation.RECEIVED -> Row {
            IconButton(onClick = { request?.let(actions.onDecline) }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.community_decline_from, name))
            }
            IconButton(onClick = { request?.let(actions.onAccept) }) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.community_accept_from, name), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Relation.NONE -> IconButton(onClick = { actions.onAdd(uid, name) }) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.community_add_named, name), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ====================================================================== Friends

@Composable
private fun FriendsPane(
    me: UserProfile?,
    friends: List<UserProfile>,
    relations: Relations,
    actions: CommunityActions,
    shareListening: Boolean,
    onShareListening: (Boolean) -> Unit,
    onAddFriends: () -> Unit,
    onFriendClick: (UserProfile) -> Unit,
    incomingDuels: List<FriendChallenge> = emptyList(),
    onAcceptDuel: (FriendChallenge) -> Unit = {},
    onDeclineDuel: (FriendChallenge) -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxSize()
    ) {
        if (incomingDuels.isNotEmpty()) {
            items(incomingDuels, key = { "fr_duel_" + it.id }) { incoming ->
                FriendDuelIncomingCard(
                    challenge = incoming,
                    myUid = relations.me,
                    onAccept = onAcceptDuel,
                    onDecline = onDeclineDuel
                )
            }
        }
        item {
            FilledTonalButton(onClick = onAddFriends, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.community_add_friends))
            }
        }

        if (relations.received.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.community_requests_header)) }
            items(relations.received, key = { "in_" + it.fromUid }) { request ->
                RequestCard(request, actions)
            }
        }

        item {
            ArchipelagoCard(me = me, friends = friends, onFriendClick = onFriendClick)
        }

        item { SectionHeader(stringResource(R.string.community_friends_header)) }
        if (friends.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Groups,
                    title = stringResource(R.string.community_no_friends_title),
                    body = stringResource(R.string.community_no_friends_body),
                    actionLabel = stringResource(R.string.community_add_friends),
                    onAction = onAddFriends
                )
            }
        } else {
            items(friends, key = { "f_" + it.uid }) { friend -> FriendRow(friend, onClick = { onFriendClick(friend) }) }
        }

        if (relations.sent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.community_sent_header)) }
            items(relations.sent, key = { "out_" + it.toUid }) { request ->
                val name = request.toName.ifBlank { stringResource(R.string.community_unnamed) }
                ListItem(
                    leadingContent = { Avatar(name) },
                    headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(stringResource(R.string.community_waiting_answer)) },
                    trailingContent = { RelationAction(Relation.SENT, request.toUid, name, request, actions) }
                )
            }
        }

        item { ShareListeningRow(shareListening, onShareListening) }
    }
}

@Composable
private fun RequestCard(request: FriendRequest, actions: CommunityActions) {
    val name = request.fromName.ifBlank { stringResource(R.string.community_unnamed) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name)
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.community_wants_to_be_friends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End)
        ) {
            TextButton(onClick = { actions.onDecline(request) }) { Text(stringResource(R.string.community_decline)) }
            Button(onClick = { actions.onAccept(request) }) { Text(stringResource(R.string.community_accept)) }
        }
    }
}

@Composable
private fun FriendRow(friend: UserProfile, onClick: () -> Unit) {
    val name = friend.username.ifBlank { stringResource(R.string.community_unnamed) }
    ListItem(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.community_open_profile), onClick = onClick),
        leadingContent = { Avatar(name) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (friend.currentRadio.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(Spacing.iconSm), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(Spacing.xxs))
                    Text(stringResource(R.string.community_listening_to, friend.currentRadio), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(
                    stringResource(
                        R.string.community_friend_summary,
                        formatInt(friend.points),
                        pluralStringResource(R.plurals.community_streak_days, friend.currentStreak, friend.currentStreak)
                    )
                )
            }
        }
    )
}

@Composable
private fun ShareListeningRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        leadingContent = { Icon(Icons.Rounded.Headphones, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.community_share_listening)) },
        supportingContent = { Text(stringResource(R.string.community_share_listening_body)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }
    )
}

// ====================================================================== Archipelago

/** Distance between neighbouring islands in the archipelago, in dp of the world. */
internal const val ARCHIPELAGO_SPACING = 150f
private const val MY_ISLAND_DP = 56f
private const val FRIEND_ISLAND_DP = 46f
private const val MIN_ZOOM = 0.3f
private const val MAX_ZOOM = 3f

/**
 * Where everyone's island sits in the world (dp): you at the centre (index 0), friends on rings
 * of 6, 12, 18… around you, flattened a little so the sea reads as seen from above at an angle.
 * Any number of friends fits; nothing overlaps.
 */
internal fun archipelagoLayout(friends: Int, spacing: Float = ARCHIPELAGO_SPACING): List<Offset> {
    val spots = mutableListOf(Offset.Zero)
    var ring = 1
    while (spots.size <= friends) {
        val slots = 6 * ring
        for (j in 0 until slots) {
            if (spots.size > friends) break
            val angle = 2.0 * Math.PI * j / slots + ring * 0.45
            spots += Offset((kotlin.math.cos(angle) * ring * spacing).toFloat(), (kotlin.math.sin(angle) * ring * spacing * 0.62).toFloat())
        }
        ring++
    }
    return spots
}

/**
 * The archipelago: an endless lagoon at night. Every completed session plants a palm and grows
 * the island. Drag in any direction, pinch to zoom, tap a friend's island to open their profile;
 * it opens zoomed to show everyone. The friends list below is the accessible alternative.
 */
@Composable
internal fun ArchipelagoCard(me: UserProfile?, friends: List<UserProfile>, onFriendClick: (UserProfile) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val unit = density.density
    val labelSize = with(density) { 12.sp.toPx() }
    val description = pluralStringResource(R.plurals.community_archipelago_a11y, friends.size, friends.size)
    val colors = LocalFocusStyle.current.islandNight
    val spots = remember(friends.size) { archipelagoLayout(friends.size) }
    // null = "show everyone" (computed from the card size); set once the user moves or zooms.
    var zoom by remember { mutableStateOf<Float?>(null) }
    var camera by remember { mutableStateOf<Offset?>(null) }

    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(Spacing.archipelagoHeight).clipToBounds()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val horizon = heightPx * 0.24f
            val anchor = Offset(widthPx / 2f, (horizon + heightPx) / 2f)
            // Fit: the box around every island (with room for palms and names) inside the sea area.
            val fit = remember(spots, widthPx, heightPx) {
                val minX = spots.minOf { it.x } - FRIEND_ISLAND_DP * 1.5f
                val maxX = spots.maxOf { it.x } + FRIEND_ISLAND_DP * 1.5f
                val minY = spots.minOf { it.y } - MY_ISLAND_DP * 1.6f
                val maxY = spots.maxOf { it.y } + MY_ISLAND_DP * 0.4f
                val z = minOf(widthPx / ((maxX - minX) * unit), (heightPx - horizon) / ((maxY - minY) * unit)).coerceIn(MIN_ZOOM, 1.4f)
                z to Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
            }
            val currentFit by rememberUpdatedState(fit)
            val z = zoom ?: fit.first
            val cam = camera ?: fit.second
            fun toScreen(world: Offset, zoomNow: Float, camNow: Offset) = anchor + (world - camNow) * (zoomNow * unit)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val z0 = zoom ?: currentFit.first
                            val c0 = camera ?: currentFit.second
                            val z1 = (z0 * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            // The point under the fingers stays under the fingers.
                            val worldAtFingers = c0 + (centroid - anchor) / (z0 * unit)
                            camera = worldAtFingers - (centroid + pan - anchor) / (z1 * unit)
                            zoom = z1
                        }
                    }
                    .pointerInput(friends, spots) {
                        detectTapGestures { tap ->
                            val z0 = zoom ?: currentFit.first
                            val world = (camera ?: currentFit.second) + (tap - anchor) / (z0 * unit)
                            friends.forEachIndexed { i, friend ->
                                val spot = spots[i + 1]
                                val r = IslandRenderer.radius(FRIEND_ISLAND_DP, friend.treeCount)
                                val inside = kotlin.math.abs(world.x - spot.x) < r &&
                                    world.y in (spot.y - IslandRenderer.crownHeight(r, friend.treeCount))..(spot.y + r * .2f)
                                if (inside) onFriendClick(friend)
                            }
                        }
                    }
            ) {
                with(IslandRenderer) {
                    drawSkyAndSea(horizon, Offset(size.width - 56f, 52f), 20f, colors)
                    // Wave marks move with the view, so the sea feels endless.
                    drawSeaMarks(horizon, cam * (z * unit), ARCHIPELAGO_SPACING * 0.5f * z * unit, colors)
                }
                val people = buildList {
                    add(Triple(spots[0], MY_ISLAND_DP to (me?.treeCount ?: 0), me?.username.orEmpty()))
                    friends.forEachIndexed { i, f -> add(Triple(spots[i + 1], FRIEND_ISLAND_DP to f.treeCount, f.username)) }
                }.sortedBy { it.first.y }
                people.forEach { (spot, size, name) ->
                    val center = toScreen(spot, z, cam)
                    val r = IslandRenderer.radius(size.first * z * unit, size.second)
                    // Skip islands far outside the card.
                    if (center.x + r * 1.5f < 0f || center.x - r * 1.5f > this.size.width || center.y - r * 1.6f > this.size.height || center.y + r < 0f) return@forEach
                    drawIslandWithLabel(center, r, size.second, name, labelSize, colors)
                }
            }
            Text(
                text = stringResource(R.string.community_archipelago_caption),
                style = MaterialTheme.typography.labelMedium,
                color = colors.label,
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm)
            )
            if (zoom != null || camera != null) {
                FilledTonalIconButton(
                    onClick = { zoom = null; camera = null },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xs)
                ) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = stringResource(R.string.community_archipelago_recenter))
                }
            }
        }
    }
}

/** Small island in a friend's sheet. */
@Composable
private fun IslandThumb(sessions: Int) {
    val colors = LocalFocusStyle.current.islandNight
    Canvas(Modifier.size(Spacing.islandThumb).clip(MaterialTheme.shapes.small)) {
        with(IslandRenderer) {
            drawSkyAndSea(size.height * .45f, Offset(size.width * .8f, size.height * .18f), size.width * .05f, colors)
            drawIsland(Offset(size.width / 2f, size.height * .86f), radius(size.width * .42f, sessions), sessions, colors)
        }
    }
}

private fun DrawScope.drawIslandWithLabel(center: Offset, r: Float, sessions: Int, label: String, labelPx: Float, colors: IslandColors) {
    with(IslandRenderer) { drawIsland(center, r, sessions, colors) }
    if (label.isBlank() || labelPx <= 0f) return
    drawContext.canvas.nativeCanvas.drawText(
        label.take(14),
        center.x,
        center.y - IslandRenderer.crownHeight(r, sessions) - 6f,
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = colors.label.toArgb()
            textSize = labelPx
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 1f, 1f, colors.labelShadow.toArgb())
        }
    )
}

// ====================================================================== Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendsSheet(
    results: List<UserProfile>,
    searching: Boolean,
    relations: Relations,
    actions: CommunityActions,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        onSearch(query)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.md).navigationBarsPadding()) {
            Text(
                stringResource(R.string.community_add_friends),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.community_search_label)) },
                supportingText = { Text(stringResource(R.string.community_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (searching) {
                    { CircularProgressIndicator(strokeWidth = Spacing.progressStroke, modifier = Modifier.size(Spacing.iconSm)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = Spacing.sceneHeight * 1.5f)) {
                if (query.trim().length >= CommunityViewModel.MIN_SEARCH && !searching && results.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.community_search_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.md)
                        )
                    }
                }
                items(results, key = { it.uid }) { user ->
                    val name = user.username.ifBlank { stringResource(R.string.community_unnamed) }
                    val request = relations.received.firstOrNull { it.fromUid == user.uid } ?: relations.sent.firstOrNull { it.toUid == user.uid }
                    ListItem(
                        leadingContent = { Avatar(name) },
                        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { RelationAction(relations.of(user.uid), user.uid, name, request, actions) }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendSheet(
    friend: UserProfile,
    isFriend: Boolean = true,
    hasActiveDuel: Boolean = false,
    canAddToStudyGroup: Boolean = false,
    isPendingChallenge: Boolean = false,
    onChallenge: () -> Unit = {},
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val name = friend.username.ifBlank { stringResource(R.string.community_unnamed) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .navigationBarsPadding()
                .padding(bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IslandThumb(friend.treeCount)
                Spacer(Modifier.size(Spacing.md))
                Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.community_sort_points), formatInt(friend.points), Icons.Rounded.Star, Modifier.weight(1f))
                StatTile(stringResource(R.string.community_sort_focus_time), focusTime(friend.totalMinutes), Icons.Rounded.Schedule, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.community_sort_sessions), formatInt(friend.treeCount), Icons.Rounded.TaskAlt, Modifier.weight(1f))
                StatTile(
                    pluralStringResource(R.plurals.focus_streak_label, friend.currentStreak),
                    formatInt(friend.currentStreak),
                    Icons.Rounded.LocalFireDepartment,
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            if (friend.currentRadio.isNotBlank()) {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(friend.currentRadio) },
                    supportingContent = { Text(stringResource(R.string.community_listening_now)) }
                )
            }
            Button(
                onClick = onChallenge,
                enabled = !hasActiveDuel && !isPendingChallenge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.buttonHeight)
                    .testTag("challenge_friend_button")
            ) {
                Icon(
                    imageVector = if (canAddToStudyGroup) Icons.Rounded.Groups else Icons.Rounded.SportsScore,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    when {
                        isPendingChallenge -> stringResource(R.string.friend_duel_waiting_accept, name)
                        hasActiveDuel -> stringResource(R.string.friend_duel_already_active)
                        canAddToStudyGroup -> stringResource(R.string.study_group_add_friend_button, name)
                        else -> stringResource(R.string.friend_duel_challenge_button, name)
                    }
                )
            }
            if (isFriend) {
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
                ) {
                    Icon(Icons.Rounded.PersonRemove, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.community_remove_friend))
                }
            }
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.community_remove_title, name),
            body = stringResource(R.string.community_remove_body),
            confirmLabel = stringResource(R.string.community_remove_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
            destructive = true
        )
    }
}

// ====================================================================== Small pieces

/** Initial-letter avatar; decorative (the name is always next to it). */
@Composable
private fun Avatar(name: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.size(Spacing.avatar)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun LeaderboardSort.label(): Int = when (this) {
    LeaderboardSort.POINTS -> R.string.community_sort_points
    LeaderboardSort.FOCUS_TIME -> R.string.community_sort_focus_time
    LeaderboardSort.SESSIONS -> R.string.community_sort_sessions
    LeaderboardSort.STREAK -> R.string.community_sort_streak
}

@Composable
private fun metricValue(sort: LeaderboardSort, points: Int, minutes: Int, sessions: Int, streak: Int): String = when (sort) {
    LeaderboardSort.POINTS -> formatInt(points)
    LeaderboardSort.FOCUS_TIME -> focusTime(minutes)
    LeaderboardSort.SESSIONS -> formatInt(sessions)
    LeaderboardSort.STREAK -> formatInt(streak)
}

@Composable
private fun focusTime(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.duration_h_m, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_m, minutes)

private fun formatInt(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private const val SEARCH_DEBOUNCE_MS = 350L

// ====================================================================== Previews

private val previewFriends = listOf(
    UserProfile(uid = "a", username = "Amine", treeCount = 14, totalMinutes = 410, points = 410, currentStreak = 4, currentRadio = "Lofi Café"),
    UserProfile(uid = "b", username = "Lina", treeCount = 22, totalMinutes = 640, points = 640, currentStreak = 9)
)

private val previewActions = CommunityActions({ _, _ -> }, {}, {}, {}, {})

@ThemePreviews
@Composable
private fun LeaderboardPanePreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface {
            LeaderboardPane(
                state = LeaderboardState(
                    entries = listOf(
                        LeaderboardEntry("b", "Lina", 22, 640, 640, 9),
                        LeaderboardEntry("me", "Sara", 18, 520, 520, 6),
                        LeaderboardEntry("c", "Yacine", 12, 300, 300, 2),
                        LeaderboardEntry("d", "Nour", 9, 210, 210, 0)
                    ),
                    loading = false
                ),
                sort = LeaderboardSort.POINTS,
                onSort = {},
                myProfile = UserProfile(uid = "me", username = "Sara", points = 520),
                relations = Relations("me", previewFriends, listOf(FriendRequest("me", "c", "Sara", "Yacine", "pending")), emptyList()),
                actions = previewActions
            )
        }
    }
}

@ThemePreviews
@Composable
private fun FriendsPanePreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface {
            FriendsPane(
                me = UserProfile(uid = "me", username = "Sara", treeCount = 18),
                friends = previewFriends,
                relations = Relations("me", previewFriends, emptyList(), listOf(FriendRequest("d", "me", "Nour", "Sara", "pending"))),
                actions = previewActions,
                shareListening = false,
                onShareListening = {},
                onAddFriends = {},
                onFriendClick = {}
            )
        }
    }
}
