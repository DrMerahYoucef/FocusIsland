package com.example.ui.screen.island

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.IslandMilestone
import com.example.ui.components.IslandProgression
import com.example.ui.components.IslandRenderer
import com.example.ui.designsystem.SectionHeader
import com.example.ui.screen.analytics.AnalyticsViewModel
import com.example.ui.theme.LocalFocusStyle
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.Spacing
import java.text.NumberFormat

/**
 * Interactive Island Explorer & 12-Milestone Civilization Roadmap.
 * Lets users inspect their island up close (pan/zoom, day/night toggle, stage preview)
 * and see how both sessions and points unlock new eras, trees, palaces, birds, and wonders.
 */
@Composable
fun IslandExplorerScreen(
    analyticsViewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val analyticsState by analyticsViewModel.state.collectAsState()
    val sessions = analyticsState.totals.completedSessions
    val points = analyticsState.totals.points
    IslandExplorerContent(
        sessions = sessions,
        points = points,
        modifier = modifier
    )
}

@Composable
fun IslandExplorerContent(
    sessions: Int,
    points: Int,
    modifier: Modifier = Modifier
) {
    val systemDark = LocalIsDarkTheme.current
    var isNightMode by rememberSaveable { mutableStateOf(systemDark) }
    // 0 = Live Island, 1..12 = Previewing specific milestone id
    var previewMilestoneId by rememberSaveable { mutableIntStateOf(0) }

    val liveUnits = IslandProgression.effectiveUnits(sessions, points)
    val previewMilestone = IslandProgression.milestones.firstOrNull { it.id == previewMilestoneId }
    val displayedSessions = sessions
    val displayedPoints = previewMilestone?.requiredPoints ?: points
    val displayedUnits = IslandProgression.effectiveUnits(displayedSessions, displayedPoints)

    val currentEra = IslandProgression.eraFor(sessions, points)
    val displayedEra = IslandProgression.eraForUnits(displayedUnits)
    val unlockedCount = IslandProgression.unlockedCount(liveUnits)
    val islandLevel = IslandProgression.islandLevel(liveUnits)
    val plantedTrees = IslandProgression.treeCountForUnits(liveUnits)
    val nextMilestone = IslandProgression.nextMilestone(liveUnits)
    val progressToNext = IslandProgression.progressToNext(sessions, points)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("island_explorer_list"),
        contentPadding = PaddingValues(
            start = Spacing.screenCompact,
            end = Spacing.screenCompact,
            top = Spacing.sm,
            bottom = Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            InteractiveIslandViewport(
                sessions = displayedSessions,
                points = displayedPoints,
                eraTitle = stringResource(displayedEra.titleRes),
                previewTitle = previewMilestone?.let { stringResource(it.titleRes) },
                isNight = isNightMode,
                onToggleNight = { isNightMode = !isNightMode },
                onExitPreview = { previewMilestoneId = 0 }
            )
        }

        item {
            StageScrubberRow(
                previewMilestoneId = previewMilestoneId,
                liveUnits = liveUnits,
                onSelectMilestone = { previewMilestoneId = it }
            )
        }

        item {
            CivilizationSummaryCard(
                eraTitle = stringResource(currentEra.titleRes),
                eraDesc = stringResource(currentEra.descRes),
                islandLevel = islandLevel,
                plantedTrees = plantedTrees,
                unlockedCount = unlockedCount,
                sessions = sessions,
                points = points,
                nextMilestone = nextMilestone,
                progressToNext = progressToNext
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                SectionHeader(stringResource(R.string.island_roadmap_header))
                Text(
                    text = stringResource(R.string.island_roadmap_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(IslandProgression.milestones, key = { it.id }) { milestone ->
            val unlocked = milestone.isUnlocked(liveUnits)
            val isPreviewing = previewMilestoneId == milestone.id
            MilestoneRoadmapCard(
                milestone = milestone,
                unlocked = unlocked,
                isPreviewing = isPreviewing,
                onClick = {
                    previewMilestoneId = if (isPreviewing) 0 else milestone.id
                }
            )
        }
    }
}

@Composable
private fun InteractiveIslandViewport(
    sessions: Int,
    points: Int,
    eraTitle: String,
    previewTitle: String?,
    isNight: Boolean,
    onToggleNight: () -> Unit,
    onExitPreview: () -> Unit
) {
    val focusStyle = LocalFocusStyle.current
    val islandColors = remember(focusStyle, isNight) { focusStyle.island(isNight) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val hasFireworks = points >= IslandProgression.CELESTIAL_AT
    val fireworkPhase = if (hasFireworks) {
        val transition = rememberInfiniteTransition(label = "sky_fireworks")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "firework_phase"
        )
        phase
    } else {
        -1f
    }

    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(276.dp)
            .clip(MaterialTheme.shapes.large)
            .testTag("island_viewport_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            val newZoom = (zoom * zoomChange).coerceIn(0.8f, 3.2f)
                            val maxPanX = size.width * (newZoom - 0.6f).coerceAtLeast(0.2f) * 0.5f
                            val maxPanY = size.height * (newZoom - 0.6f).coerceAtLeast(0.2f) * 0.5f
                            zoom = newZoom
                            pan = Offset(
                                x = (pan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                y = (pan.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                            )
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }
            ) {
                val w = size.width
                val h = size.height
                val horizon = h * 0.52f
                with(IslandRenderer) {
                    drawSkyAndSea(horizon, Offset(w * 0.80f, h * 0.22f), h * 0.08f, islandColors)
                    drawIsland(
                        center = Offset(w * 0.48f, h * 0.83f),
                        radius = radius(minOf(w * 0.29f, h * 0.46f), sessions, points),
                        sessions = sessions,
                        colors = islandColors,
                        points = points,
                        fireworkPhase = fireworkPhase
                    )
                }
            }

            // Top-Start Era / Preview Badge
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Icon(
                        imageVector = if (previewTitle != null) Icons.Rounded.Visibility else Icons.Rounded.Park,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Spacing.iconSm)
                    )
                    Text(
                        text = previewTitle?.let { stringResource(R.string.island_preview_badge, it) } ?: eraTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Top-End Day / Night Toggle Chip
            AssistChip(
                onClick = onToggleNight,
                label = {
                    Text(stringResource(if (isNight) R.string.island_night_mode else R.string.island_day_mode))
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.iconSm)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                ),
                border = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .testTag("island_day_night_toggle")
            )

            // Bottom-Start "Back to My Island" pill when previewing a milestone
            if (previewTitle != null) {
                AssistChip(
                    onClick = onExitPreview,
                    label = { Text(stringResource(R.string.island_return_live)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.sm)
                        .testTag("island_exit_preview_button")
                )
            }

            // Bottom-End Recenter button when panned/zoomed
            if (zoom != 1f || pan != Offset.Zero) {
                FilledTonalIconButton(
                    onClick = {
                        zoom = 1f
                        pan = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.xs)
                        .testTag("island_recenter_button")
                ) {
                    Icon(
                        Icons.Rounded.CenterFocusStrong,
                        contentDescription = stringResource(R.string.community_archipelago_recenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun StageScrubberRow(
    previewMilestoneId: Int,
    liveUnits: Int,
    onSelectMilestone: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        FilterChip(
            selected = previewMilestoneId == 0,
            onClick = { onSelectMilestone(0) },
            label = { Text(stringResource(R.string.island_live_badge)) },
            leadingIcon = {
                Icon(Icons.Rounded.Park, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
            },
            modifier = Modifier.testTag("stage_chip_live")
        )
        IslandProgression.milestones.forEach { ms ->
            val unlocked = ms.isUnlocked(liveUnits)
            FilterChip(
                selected = previewMilestoneId == ms.id,
                onClick = { onSelectMilestone(if (previewMilestoneId == ms.id) 0 else ms.id) },
                label = { Text("#${ms.id} · ${stringResource(ms.titleRes)}") },
                leadingIcon = {
                    Icon(
                        imageVector = if (unlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.iconSm)
                    )
                },
                modifier = Modifier.testTag("stage_chip_${ms.id}")
            )
        }
    }
}

@Composable
private fun CivilizationSummaryCard(
    eraTitle: String,
    eraDesc: String,
    islandLevel: Int,
    plantedTrees: Int,
    unlockedCount: Int,
    sessions: Int,
    points: Int,
    nextMilestone: IslandMilestone?,
    progressToNext: Float
) {
    val nf = remember { NumberFormat.getIntegerInstance() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = eraTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = eraDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.island_effective_power, islandLevel),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
            }

            // Metrics pill strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                SummaryBadge(
                    icon = Icons.Rounded.Forest,
                    text = stringResource(R.string.island_trees_planted, plantedTrees, IslandProgression.MAX_TREES),
                    modifier = Modifier.weight(1f)
                )
                SummaryBadge(
                    icon = Icons.Rounded.EmojiEvents,
                    text = stringResource(R.string.island_landmarks_unlocked, unlockedCount, IslandProgression.milestones.size),
                    modifier = Modifier.weight(1f)
                )
            }

            if (nextMilestone != null) {
                val remPoints = (nextMilestone.requiredPoints - points).coerceAtLeast(0)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(R.string.island_next_milestone_header)}: ${stringResource(nextMilestone.titleRes)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(progressToNext * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressToNext },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = stringResource(
                                R.string.island_remaining_to_unlock,
                                nf.format(remPoints)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.island_all_unlocked_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.island_all_unlocked_body, IslandProgression.MAX_TREES),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Text(
                text = "${nf.format(points)} ${stringResource(R.string.stats_points).lowercase()} · ${nf.format(sessions)} ${stringResource(R.string.stats_sessions).lowercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryBadge(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Spacing.iconSm)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MilestoneRoadmapCard(
    milestone: IslandMilestone,
    unlocked: Boolean,
    isPreviewing: Boolean,
    onClick: () -> Unit
) {
    val nf = remember { NumberFormat.getIntegerInstance() }
    val containerColor = when {
        isPreviewing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        unlocked -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val border = if (isPreviewing) {
        BorderStroke(Spacing.selectedBorder, MaterialTheme.colorScheme.primary)
    } else if (unlocked) {
        BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    } else {
        BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("milestone_card_${milestone.id}")
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                shape = CircleShape,
                color = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = milestoneIcon(milestone.id),
                        contentDescription = null,
                        tint = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "${milestone.id}. ${stringResource(milestone.titleRes)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (unlocked) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.island_milestone_unlocked),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(milestone.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.island_req_sessions_or_points,
                            nf.format(milestone.requiredPoints)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "· " + stringResource(R.string.island_milestone_trees_bonus, milestone.treesAtMilestone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun milestoneIcon(id: Int): ImageVector = when (id) {
    1 -> Icons.Rounded.Park
    2 -> Icons.Rounded.LocalFireDepartment
    3 -> Icons.Rounded.Cottage
    4 -> Icons.Rounded.Sailing
    5 -> Icons.Rounded.Lightbulb
    6 -> Icons.Rounded.Air
    7 -> Icons.Rounded.Landscape
    8 -> Icons.Rounded.Water
    9 -> Icons.Rounded.AccountBalance
    10 -> Icons.Rounded.Flight
    11 -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.Stars
}
