package com.example.ui.screen.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.stats.FocusTotals
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.LoadingState
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.StatTile
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
    onOpenIsland: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    AnalyticsContent(state, modifier, onOpenIsland)
}

@Composable
fun AnalyticsContent(
    state: AnalyticsState,
    modifier: Modifier = Modifier,
    onOpenIsland: () -> Unit = {}
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.isEmpty -> EmptyState(
            icon = Icons.Rounded.Insights,
            title = stringResource(R.string.stats_empty_title),
            body = stringResource(R.string.stats_empty_body),
            actionLabel = stringResource(R.string.island_explore_cta),
            onAction = onOpenIsland,
            modifier = modifier
        )
        else -> {
            var selected by rememberSaveable { mutableIntStateOf(state.history.lastIndex) }
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.screenCompact),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    IslandProgressBannerCard(
                        totals = state.totals,
                        onClick = onOpenIsland
                    )
                }
                item { SectionHeader(stringResource(R.string.stats_overview)) }
                item { OverviewGrid(state.totals, state.averageSessionMinutes) }
                item {
                    Column {
                        SectionHeader(
                            title = stringResource(R.string.stats_this_week),
                            action = { FocusDurationBadge(minutes = state.weekMinutes, testTag = "week_focus_badge") }
                        )
                        Text(
                            stringResource(R.string.stats_points_rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item { WeekChart(state.week) }
                item {
                    SectionHeader(
                        title = stringResource(R.string.stats_last_30_days),
                        action = { FocusDurationBadge(minutes = state.monthMinutes, testTag = "month_focus_badge") }
                    )
                }
                item { HistoryStrip(state.history, selected) { selected = it } }
                state.history.getOrNull(selected)?.let { day -> item { DayDetail(day) } }
                if (state.year2022Minutes > 0 || state.sinceJune2026Minutes > 0) {
                    item { SectionHeader(stringResource(R.string.stats_yearly_history)) }
                    item {
                        HistoricalPeriodsCard(
                            year2022Minutes = state.year2022Minutes,
                            sinceJune2026Minutes = state.sinceJune2026Minutes
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandProgressBannerCard(
    totals: FocusTotals,
    onClick: () -> Unit
) {
    val sessions = totals.completedSessions
    val points = totals.points
    val units = com.example.ui.components.IslandProgression.effectiveUnits(sessions, points)
    val era = com.example.ui.components.IslandProgression.eraFor(sessions, points)
    val trees = com.example.ui.components.IslandProgression.treeCountForUnits(units)
    val unlocked = com.example.ui.components.IslandProgression.unlockedCount(units)
    val dark = com.example.ui.theme.LocalIsDarkTheme.current
    val islandColors = com.example.ui.theme.LocalFocusStyle.current.island(dark)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_island_banner_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .width(96.dp)
                    .height(76.dp)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                with(com.example.ui.components.IslandRenderer) {
                    drawSkyAndSea(
                        size.height * 0.46f,
                         androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.20f),
                        size.width * 0.06f,
                        islandColors
                    )
                    drawIsland(
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.82f),
                        radius = radius(size.width * 0.36f, sessions, points),
                        sessions = sessions,
                        colors = islandColors,
                        points = points
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = stringResource(era.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${stringResource(R.string.island_trees_planted, trees, com.example.ui.components.IslandProgression.MAX_TREES)} · ${stringResource(R.string.island_landmarks_unlocked, unlocked, com.example.ui.components.IslandProgression.milestones.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.island_explore_cta),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FocusDurationBadge(minutes: Int, testTag: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = formatMinutes(minutes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HistoricalPeriodsCard(year2022Minutes: Int, sinceJune2026Minutes: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (year2022Minutes > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Spacing.iconSm)
                        )
                        Text(
                            text = stringResource(R.string.stats_period_2022),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    FocusDurationBadge(minutes = year2022Minutes, testTag = "year_2022_focus_badge")
                }
            }
            if (year2022Minutes > 0 && sinceJune2026Minutes > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (sinceJune2026Minutes > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Spacing.iconSm)
                        )
                        Text(
                            text = stringResource(R.string.stats_period_2026),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    FocusDurationBadge(minutes = sinceJune2026Minutes, testTag = "since_june_2026_focus_badge")
                }
            }
            if (year2022Minutes > 0) {
                Text(
                    text = stringResource(R.string.stats_period_gap_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OverviewGrid(totals: FocusTotals, averageMinutes: Int) {
    val tiles = listOf(
        Triple(stringResource(R.string.stats_focus_time), formatMinutes(totals.focusMinutes), Icons.Rounded.Timer),
        Triple(stringResource(R.string.stats_sessions), totals.completedSessions.toString(), Icons.Rounded.TaskAlt),
        Triple(stringResource(R.string.stats_current_streak), pluralStringResource(R.plurals.days_count, totals.currentStreakDays, totals.currentStreakDays), Icons.Rounded.LocalFireDepartment),
        Triple(stringResource(R.string.stats_best_streak), pluralStringResource(R.plurals.days_count, totals.longestStreakDays, totals.longestStreakDays), Icons.Rounded.EmojiEvents),
        Triple(stringResource(R.string.stats_points), totals.points.toString(), Icons.Rounded.Stars),
        Triple(stringResource(R.string.stats_average_session), formatMinutes(averageMinutes), Icons.Rounded.AvTimer)
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { (label, value, icon) -> StatTile(label, value, icon, Modifier.weight(1f)) }
            }
        }
    }
}

/** Seven bars, one per day; each column is announced as "Monday, 45 minutes". */
@Composable
private fun WeekChart(week: List<DayStat>) {
    val max = (week.maxOfOrNull { it.focusMinutes } ?: 0).coerceAtLeast(1)
    val today = LocalDate.now()
    val dayName = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val fullDay = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().height(180.dp).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            week.forEach { day ->
                val description = pluralStringResource(R.plurals.stats_day_minutes_a11y, day.focusMinutes, day.date.format(fullDay), day.focusMinutes)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (day.focusMinutes > 0) day.focusMinutes.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight((day.focusMinutes.toFloat() / max).coerceAtLeast(0.02f))
                                .clip(MaterialTheme.shapes.small)
                                .background(if (day.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                    Text(
                        day.date.format(dayName),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Scrollable 30-day strip; tap a day to see its details below. Opens scrolled to today. */
@Composable
private fun HistoryStrip(history: List<DayStat>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (history.size - 7).coerceAtLeast(0))
    val max = (history.maxOfOrNull { it.completedSessions } ?: 0).coerceAtLeast(1)
    val label = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        itemsIndexed(history, key = { _, d -> d.date.toEpochDay() }) { index, day ->
            val isSelected = index == selected
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .width(56.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
            ) {
                Column(Modifier.padding(vertical = Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(48.dp).width(16.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth()
                                .fillMaxHeight((day.completedSessions.toFloat() / max).coerceAtLeast(0.06f))
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(if (day.completedSessions > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Text(day.date.format(label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun DayDetail(day: DayStat) {
    val date = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(date, style = MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.stats_day_sessions, day.completedSessions, day.completedSessions), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.stats_day_minutes, formatMinutes(day.focusMinutes)), style = MaterialTheme.typography.bodyMedium)
            if (day.endedEarly > 0) {
                Text(
                    pluralStringResource(R.plurals.stats_day_ended_early, day.endedEarly, day.endedEarly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.duration_h_m, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_m, minutes)

@ThemePreviews
@Composable
private fun AnalyticsPreview() {
    val today = LocalDate.now()
    val history = (29L downTo 0L).map { DayStat(today.minusDays(it), (it % 4).toInt(), ((it % 4) * 25).toInt(), if (it % 7 == 0L) 1 else 0) }
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsContent(
                AnalyticsState(
                    isLoading = false,
                    totals = FocusTotals(42, 3, 1050, 1050, 4, 9),
                    averageSessionMinutes = 25,
                    week = history.takeLast(7),
                    history = history
                )
            )
        }
    }
}
