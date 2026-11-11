package com.example.ui.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.StudyGroupNudge
import com.example.service.FloatingFocusBubbleManager
import com.example.service.Phase
import com.example.service.PomodoroTimerService

private val IslandBlack = Color(0xFF000000)
private val IslandBorder = Color(0xFF1F2428)
private val IslandAccentTeal = Color(0xFF38D9C0)
private val IslandSubtitleSilver = Color(0xFFB0B5BA)
private val IslandButtonCharcoal = Color(0xFF2B2B2F)
private val IslandButtonLabelWhite = Color(0xFFF3F4F6)
private val IslandEmblemBg = Color(0xFFDCF5F2)

/**
 * In-app `dynamicSpot`-style Dynamic Island overlay rendered at the top center of the screen
 * whenever system overlay (`SYSTEM_ALERT_WINDOW`) is not already drawing over the window.
 *
 * Features:
 * - **Compact Pill**: Circular Focus Island palm-tree emblem on the left, camera cutout gap in the center,
 *   and bold white countdown (`48:10`) on the right.
 * - **Expanded Card**: Smooth spring morph transition to the full rounded black card with
 *   `Focus Island • Dans 48 min`, `En focus · 48:31`, `48:30`, and 3 charcoal pill buttons
 *   (`Pause` / `Reprendre`, `Passer`, `Terminer` or notification actions).
 * - **Interactions**: Tap compact pill to expand; tap anywhere outside (or swipe up) to collapse.
 */
@Composable
fun DynamicSpotIslandOverlay(
    activeInvite: FriendChallenge?,
    activeNudge: StudyGroupNudge?,
    myUid: String,
    isPhoneLockActive: Boolean,
    isOnFocusSaverRoute: Boolean,
    onAcceptInvite: (FriendChallenge) -> Unit,
    onDeclineInvite: (FriendChallenge) -> Unit,
    onHideInvite: (FriendChallenge) -> Unit,
    onReturnFromNudge: () -> Unit,
    onDismissNudge: () -> Unit,
    onOpenFocusTab: () -> Unit,
    onOpenChallengesTab: () -> Unit,
    modifier: Modifier = Modifier,
    isAppInForeground: Boolean = true,
    timerState: com.example.service.TimerState = PomodoroTimerService.state.collectAsStateWithLifecycle().value
) {
    val context = LocalContext.current
    val islandState by FloatingFocusBubbleManager.islandState.collectAsStateWithLifecycle()

    // Keep FloatingFocusBubbleManager synced with live timer state so the background overlay is ready when leaving the app
    LaunchedEffect(
        timerState.isRunning,
        timerState.isUntouched,
        timerState.remainingMs,
        timerState.phase,
        timerState.isAutoPausedByAppSwitch
    ) {
        FloatingFocusBubbleManager.syncTimerState(context, timerState)
    }

    // Never show the Dynamic Island while the app is open in the foreground
    if (isAppInForeground || isPhoneLockActive || isOnFocusSaverRoute) return

    val isVisible = islandState.isVisible || activeInvite != null || activeNudge != null
    val isExpanded = islandState.isExpanded || activeInvite != null || activeNudge != null

    Box(modifier = modifier.fillMaxSize()) {
        // Outside-tap scrim: when expanded, tapping anywhere outside collapses the Dynamic Island card
        if (isVisible && isExpanded) {
            val outsideInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dynamic_island_outside_scrim")
                    .clickable(
                        interactionSource = outsideInteraction,
                        indication = null
                    ) {
                        if (activeNudge != null) {
                            onDismissNudge()
                        }
                        FloatingFocusBubbleManager.collapseIsland(context)
                    }
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = 0.72f,
                transformOrigin = TransformOrigin(0.5f, 0f)
            ) + fadeIn(tween(180)),
            exit = scaleOut(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                targetScale = 0.72f,
                transformOrigin = TransformOrigin(0.5f, 0f)
            ) + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 4.dp)
        ) {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
            val targetExpandedWidth = (screenWidthDp - 24.dp).coerceIn(280.dp, 380.dp)

            val transition = updateTransition(targetState = isExpanded, label = "DynamicIslandMorph")
            val cardWidth by transition.animateDp(
                transitionSpec = {
                    if (targetState) {
                        spring(dampingRatio = 0.78f, stiffness = 360f)
                    } else {
                        spring(dampingRatio = 0.88f, stiffness = 440f)
                    }
                },
                label = "IslandWidth"
            ) { expanded -> if (expanded) targetExpandedWidth else 168.dp }

            val cardHeight by transition.animateDp(
                transitionSpec = {
                    if (targetState) {
                        spring(dampingRatio = 0.78f, stiffness = 360f)
                    } else {
                        spring(dampingRatio = 0.88f, stiffness = 440f)
                    }
                },
                label = "IslandHeight"
            ) { expanded -> if (expanded) 170.dp else 36.dp }

            val cornerRadius by transition.animateDp(
                transitionSpec = { spring(dampingRatio = 0.85f, stiffness = 400f) },
                label = "IslandCornerRadius"
            ) { expanded -> if (expanded) 38.dp else 50.dp }

            val compactAlpha by transition.animateFloat(
                transitionSpec = { tween(durationMillis = if (targetState) 110 else 180) },
                label = "CompactAlpha"
            ) { expanded -> if (expanded) 0f else 1f }

            val expandedAlpha by transition.animateFloat(
                transitionSpec = {
                    tween(
                        durationMillis = if (targetState) 210 else 110,
                        delayMillis = if (targetState) 65 else 0
                    )
                },
                label = "ExpandedAlpha"
            ) { expanded -> if (expanded) 1f else 0f }

            val semanticTag = when {
                activeInvite != null -> "challenge_invite_floating_capsule"
                activeNudge != null -> "nudge_toast_banner"
                isExpanded -> "dynamic_island_expanded_card"
                else -> "dynamic_island_pill"
            }

            Surface(
                shape = RoundedCornerShape(cornerRadius),
                color = IslandBlack,
                contentColor = Color.White,
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, IslandBorder),
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .testTag(semanticTag)
                    .pointerInput(isExpanded) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (isExpanded && dragAmount < -12f) {
                                if (activeNudge != null) onDismissNudge()
                                FloatingFocusBubbleManager.collapseIsland(context)
                            } else if (!isExpanded && dragAmount > 12f) {
                                FloatingFocusBubbleManager.expandIsland(context)
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isExpanded) {
                            FloatingFocusBubbleManager.expandIsland(context)
                        }
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (compactAlpha > 0.01f) {
                        CompactDynamicIslandContent(
                            clockText = islandState.clockText,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(compactAlpha)
                        )
                    }
                    if (expandedAlpha > 0.01f) {
                        ExpandedDynamicIslandContent(
                            islandState = islandState,
                            activeInvite = activeInvite,
                            activeNudge = activeNudge,
                            onBodyClick = {
                                FloatingFocusBubbleManager.collapseIsland(context)
                                if (activeInvite != null || islandState.notificationKind == FloatingFocusBubbleManager.NotificationKind.INVITE) {
                                    onOpenChallengesTab()
                                } else {
                                    onOpenFocusTab()
                                }
                            },
                            onPrimaryClick = {
                                when {
                                    activeInvite != null -> {
                                        onHideInvite(activeInvite)
                                        FloatingFocusBubbleManager.hideInviteCapsule(context)
                                    }
                                    activeNudge != null -> {
                                        onDismissNudge()
                                        FloatingFocusBubbleManager.hideNudgeBanner(context)
                                    }
                                    else -> {
                                        val action = if (islandState.isRunning) {
                                            PomodoroTimerService.ACTION_PAUSE
                                        } else {
                                            PomodoroTimerService.ACTION_RESUME
                                        }
                                        runCatching {
                                            val intent = Intent(context, PomodoroTimerService::class.java).setAction(action)
                                            ContextCompat.startForegroundService(context, intent)
                                        }
                                    }
                                }
                            },
                            onSecondaryClick = {
                                when {
                                    activeInvite != null -> {
                                        onDeclineInvite(activeInvite)
                                        FloatingFocusBubbleManager.hideInviteCapsule(context)
                                    }
                                    activeNudge != null -> {
                                        onDismissNudge()
                                        FloatingFocusBubbleManager.hideNudgeBanner(context)
                                        onOpenFocusTab()
                                    }
                                    else -> {
                                        runCatching {
                                            val intent = Intent(context, PomodoroTimerService::class.java)
                                                .setAction(PomodoroTimerService.ACTION_SKIP)
                                            ContextCompat.startForegroundService(context, intent)
                                        }
                                    }
                                }
                            },
                            onTertiaryClick = {
                                when {
                                    activeInvite != null -> {
                                        onAcceptInvite(activeInvite)
                                        FloatingFocusBubbleManager.hideInviteCapsule(context)
                                    }
                                    activeNudge != null -> {
                                        onReturnFromNudge()
                                        FloatingFocusBubbleManager.hideNudgeBanner(context)
                                    }
                                    else -> {
                                        FloatingFocusBubbleManager.collapseIsland(context)
                                        runCatching {
                                            val intent = Intent(context, PomodoroTimerService::class.java)
                                                .setAction(PomodoroTimerService.ACTION_STOP)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(expandedAlpha)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDynamicIslandContent(
    clockText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(start = 5.dp, end = 13.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FocusIslandCircularEmblem(size = 26.dp)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = clockText,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            modifier = Modifier.testTag("dynamic_island_compact_clock")
        )
    }
}

@Composable
private fun ExpandedDynamicIslandContent(
    islandState: FloatingFocusBubbleManager.DynamicIslandUiState,
    activeInvite: FriendChallenge?,
    activeNudge: StudyGroupNudge?,
    onBodyClick: () -> Unit,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onTertiaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phaseLabel = stringResource(
        when (islandState.phase) {
            Phase.FOCUS -> R.string.timer_notif_focus
            Phase.SHORT_BREAK -> R.string.timer_notif_short_break
            Phase.LONG_BREAK -> R.string.timer_notif_long_break
        }
    )

    val headerText: String
    val titleText: String
    val subtitleText: String
    val primaryLabel: String
    val secondaryLabel: String
    val tertiaryLabel: String
    val primaryTag: String
    val secondaryTag: String
    val tertiaryTag: String

    when {
        activeInvite != null || islandState.notificationKind == FloatingFocusBubbleManager.NotificationKind.INVITE -> {
            val challenger = activeInvite?.challengerName?.ifBlank { null }
                ?: islandState.notificationSender.ifBlank { stringResource(R.string.community_unnamed) }
            headerText = stringResource(R.string.dynamic_island_header_now)
            titleText = stringResource(R.string.invite_capsule_title, challenger)
            subtitleText = islandState.notificationSubtitle.ifBlank {
                stringResource(R.string.invite_capsule_subtitle_open)
            }
            primaryLabel = stringResource(R.string.invite_capsule_hide)
            secondaryLabel = stringResource(R.string.invite_capsule_deny)
            tertiaryLabel = stringResource(R.string.invite_capsule_accept)
            primaryTag = "invite_capsule_hide_button"
            secondaryTag = "invite_capsule_deny_button"
            tertiaryTag = "invite_capsule_accept_button"
        }
        activeNudge != null || islandState.notificationKind == FloatingFocusBubbleManager.NotificationKind.NUDGE -> {
            val sender = activeNudge?.fromName?.ifBlank { null }
                ?: islandState.notificationSender.ifBlank { stringResource(R.string.community_unnamed) }
            headerText = stringResource(R.string.dynamic_island_header_now)
            titleText = stringResource(R.string.nudge_banner_title, sender)
            subtitleText = stringResource(R.string.nudge_banner_message)
            primaryLabel = stringResource(R.string.dynamic_island_action_dismiss)
            secondaryLabel = stringResource(R.string.dynamic_island_action_open)
            tertiaryLabel = stringResource(R.string.timer_action_resume)
            primaryTag = "dynamic_island_nudge_dismiss_button"
            secondaryTag = "dynamic_island_nudge_open_button"
            tertiaryTag = "dynamic_island_nudge_resume_button"
        }
        islandState.notificationKind == FloatingFocusBubbleManager.NotificationKind.MESSAGE -> {
            headerText = stringResource(R.string.dynamic_island_header_now)
            titleText = islandState.notificationTitle
            subtitleText = islandState.notificationSubtitle
            primaryLabel = stringResource(R.string.dynamic_island_action_dismiss)
            secondaryLabel = stringResource(R.string.dynamic_island_action_open)
            tertiaryLabel = stringResource(R.string.timer_action_resume)
            primaryTag = "dynamic_island_msg_dismiss_button"
            secondaryTag = "dynamic_island_msg_open_button"
            tertiaryTag = "dynamic_island_msg_resume_button"
        }
        else -> {
            headerText = if (islandState.isRunning) {
                stringResource(R.string.dynamic_island_header_in_min, islandState.remainingMinutes)
            } else {
                stringResource(R.string.dynamic_island_header_paused)
            }
            titleText = "$phaseLabel · ${islandState.clockText}"
            subtitleText = if (islandState.isAutoPaused) {
                stringResource(R.string.focus_mode_paused)
            } else {
                islandState.clockText
            }
            primaryLabel = stringResource(
                if (islandState.isRunning) R.string.timer_action_pause else R.string.timer_action_resume
            )
            secondaryLabel = stringResource(R.string.timer_action_skip)
            tertiaryLabel = stringResource(R.string.timer_action_end)
            primaryTag = "dynamic_island_pause_resume_button"
            secondaryTag = "dynamic_island_skip_button"
            tertiaryTag = "dynamic_island_end_button"
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBodyClick
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FocusIslandCircularEmblem(size = 50.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = headerText,
                    color = IslandAccentTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("dynamic_island_header_text")
                )
                Text(
                    text = titleText,
                    color = Color.White,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("dynamic_island_title_text")
                )
                Text(
                    text = subtitleText,
                    color = IslandSubtitleSilver,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("dynamic_island_subtitle_text")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DynamicSpotActionPill(
                label = primaryLabel,
                onClick = onPrimaryClick,
                testTag = primaryTag,
                modifier = Modifier.weight(1f)
            )
            DynamicSpotActionPill(
                label = secondaryLabel,
                onClick = onSecondaryClick,
                testTag = secondaryTag,
                modifier = Modifier.weight(1f)
            )
            DynamicSpotActionPill(
                label = tertiaryLabel,
                onClick = onTertiaryClick,
                testTag = tertiaryTag,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DynamicSpotActionPill(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = IslandButtonCharcoal,
        contentColor = IslandButtonLabelWhite,
        modifier = modifier
            .height(42.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                color = IslandButtonLabelWhite,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FocusIslandCircularEmblem(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(IslandEmblemBg),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
