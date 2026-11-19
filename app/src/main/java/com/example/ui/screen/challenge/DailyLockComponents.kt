package com.example.ui.screen.challenge

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.challenge.DailyLockSchedule
import com.example.data.challenge.DailyLockTime
import com.example.ui.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Form controls for creating a recurring daily Lock Phone interval (start time -> end time)
 * inside [NewChallengeSheet] when Lock Phone -> Daily interval is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DailyLockIntervalForm(
    label: String,
    onLabelChange: (String) -> Unit,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartTimeChanged: (Int, Int) -> Unit,
    onEndTimeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }

    val startTime = remember(startHour, startMinute) { LocalTime.of(startHour, startMinute) }
    val endTime = remember(endHour, endMinute) { LocalTime.of(endHour, endMinute) }
    val durationMin = remember(startHour, startMinute, endHour, endMinute) {
        DailyLockTime.durationMinutes(startHour, startMinute, endHour, endMinute)
    }
    val overnight = remember(startHour, startMinute, endHour, endMinute) {
        DailyLockTime.isOvernight(startHour, startMinute, endHour, endMinute)
    }
    val isValid = durationMin > 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { onLabelChange(it.take(60)) },
            label = { Text(stringResource(R.string.lock_phone_daily_label_optional)) },
            placeholder = { Text(stringResource(R.string.lock_phone_daily_label_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lock_phone_daily_label_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            DailyTimePickerCard(
                caption = stringResource(R.string.lock_phone_daily_from),
                timeText = formatLocalTime(startTime),
                onClick = { pickingStart = true },
                testTag = "lock_phone_daily_start_button",
                modifier = Modifier.weight(1f)
            )
            DailyTimePickerCard(
                caption = stringResource(R.string.lock_phone_daily_to),
                timeText = formatLocalTime(endTime),
                onClick = { pickingEnd = true },
                testTag = "lock_phone_daily_end_button",
                modifier = Modifier.weight(1f)
            )
        }

        if (isValid) {
            val formattedDuration = formatDurationCompact(durationMin)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockClock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(
                            if (overnight) R.string.lock_phone_daily_summary_overnight else R.string.lock_phone_daily_summary,
                            formattedDuration
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.lock_phone_daily_invalid_range),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (pickingStart || pickingEnd) {
        val context = LocalContext.current
        val initialHour = if (pickingStart) startHour else endHour
        val initialMinute = if (pickingStart) startMinute else endMinute
        val picker = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { pickingStart = false; pickingEnd = false },
            title = {
                Text(
                    stringResource(
                        if (pickingStart) R.string.lock_phone_daily_pick_start else R.string.lock_phone_daily_pick_end
                    )
                )
            },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pickingStart) {
                            onStartTimeChanged(picker.hour, picker.minute)
                        } else {
                            onEndTimeChanged(picker.hour, picker.minute)
                        }
                        pickingStart = false
                        pickingEnd = false
                    }
                ) {
                    Text(stringResource(R.string.challenge_dismiss))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false; pickingEnd = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DailyTimePickerCard(
    caption: String,
    timeText: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 68.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Displays all recurring daily Lock Phone schedules on the Personal Challenges tab,
 * allowing users to toggle each interval on/off, add a new daily interval, or delete existing ones.
 */
@Composable
internal fun DailyLockSchedulesSection(
    schedules: List<DailyLockSchedule>,
    nowMs: Long,
    onAddSchedule: () -> Unit,
    onToggleSchedule: (String, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_lock_schedules_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockClock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.lock_phone_daily_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.lock_phone_daily_section_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.size(Spacing.xs))
                FilledTonalButton(
                    onClick = onAddSchedule,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("daily_lock_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.lock_phone_daily_add_button))
                }
            }

            if (schedules.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                schedules.forEachIndexed { index, schedule ->
                    DailyLockScheduleRow(
                        schedule = schedule,
                        nowMs = nowMs,
                        onToggle = { enabled -> onToggleSchedule(schedule.id, enabled) },
                        onDelete = { onDeleteSchedule(schedule.id) }
                    )
                    if (index < schedules.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyLockScheduleRow(
    schedule: DailyLockSchedule,
    nowMs: Long,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val startStr = formatLocalTime(schedule.startTime)
    val endStr = formatLocalTime(schedule.endTime)
    val rangeStr = "$startStr – $endStr"
    val occ = remember(schedule, nowMs / 1000L) {
        DailyLockTime.nextOccurrence(schedule, nowMs)
    }
    val statusText = when {
        !schedule.enabled -> stringResource(R.string.lock_phone_daily_disabled)
        occ != null && occ.isActiveAt(nowMs) -> stringResource(R.string.lock_phone_daily_active_now, endStr)
        occ != null && occ.isWarningAt(nowMs) -> stringResource(
            R.string.lock_phone_daily_locks_in,
            ChallengeTime.clock(occ.remainingBeforeLockMs(nowMs))
        )
        else -> stringResource(R.string.lock_phone_daily_next_at, startStr)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("daily_lock_schedule_row_${schedule.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = rangeStr,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (schedule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            val title = schedule.label.ifBlank { stringResource(R.string.lock_phone_daily_default_label) }
            Text(
                text = "$title · ${formatDurationCompact(schedule.durationMinutes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (occ != null && (occ.isWarningAt(nowMs) || occ.isActiveAt(nowMs))) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (occ != null && occ.isWarningAt(nowMs)) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Switch(
            checked = schedule.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.testTag("daily_lock_toggle_${schedule.id}")
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(48.dp)
                .testTag("daily_lock_delete_${schedule.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.lock_phone_daily_delete_a11y),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun formatLocalTime(time: LocalTime): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    return time.format(formatter)
}

@Composable
private fun formatDurationCompact(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> stringResource(R.string.duration_h_m, h, m)
        h > 0 -> stringResource(R.string.duration_h, h)
        else -> stringResource(R.string.duration_m, m)
    }
}
