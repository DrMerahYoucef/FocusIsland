package com.example.ui.screen.exams

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.entity.ExamEntity
import com.example.data.stats.StatsCalculator
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.widget.ExamCountdownWidgetReceiver
import com.example.widget.ExamMatrixWidgetReceiver
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Exam colour tags (stored as ARGB ints, unchanged format). Chosen to stay legible in light and dark. */
private val ExamColors = listOf(
    Color(0xFF1F6A64), Color(0xFF3F6FA0), Color(0xFF7A5900),
    Color(0xFF9C4234), Color(0xFF2E6B30), Color(0xFF6B4FA0)
)

private data class ExamDraft(val id: Long?, val name: String, val subject: String, val date: LocalDate?, val color: Int)

@Composable
fun ExamsScreen(viewModel: ExamsViewModel, modifier: Modifier = Modifier) {
    val exams by viewModel.exams.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var draft by remember { mutableStateOf<ExamDraft?>(null) }
    val deletedMessage = stringResource(R.string.exams_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val pinUnsupported = stringResource(R.string.exams_widget_manual)

    Box(modifier.fillMaxSize()) {
        ExamsContent(
            exams = exams,
            onAdd = { draft = ExamDraft(null, "", "", null, ExamColors.first().toArgb()) },
            onEdit = { draft = ExamDraft(it.id, it.name, it.subject, StatsCalculator.localDay(it.examDate), it.color) },
            onDelete = { exam ->
                viewModel.deleteExam(exam)
                scope.launch {
                    if (snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            },
            onPinWidget = { cls ->
                if (!requestPinWidget(context, cls)) scope.launch { snackbar.showSnackbar(pinUnsupported) }
            }
        )
        ExtendedFloatingActionButton(
            onClick = { draft = ExamDraft(null, "", "", null, ExamColors.first().toArgb()) },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.exams_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md)
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }

    draft?.let { initial ->
        ExamEditorDialog(
            initial = initial,
            onDismiss = { draft = null },
            onSave = { d ->
                val epoch = d.date!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                viewModel.saveExam(d.id, d.name, d.subject, epoch, d.color)
                draft = null
            }
        )
    }
}

@Composable
private fun ExamsContent(
    exams: List<ExamEntity>,
    onAdd: () -> Unit,
    onEdit: (ExamEntity) -> Unit,
    onDelete: (ExamEntity) -> Unit,
    onPinWidget: (Class<*>) -> Unit
) {
    val today = LocalDate.now()
    val (upcoming, past) = exams.partition { !StatsCalculator.localDay(it.examDate).isBefore(today) }
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (exams.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Event,
                    title = stringResource(R.string.exams_empty_title),
                    body = stringResource(R.string.exams_empty_body),
                    actionLabel = stringResource(R.string.exams_new),
                    onAction = onAdd
                )
            }
        }
        items(upcoming, key = { it.id }) { ExamCard(it, today, onEdit, onDelete) }
        if (past.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.exams_past)) }
            items(past.reversed(), key = { it.id }) { ExamCard(it, today, onEdit, onDelete) }
        }
        item { WidgetCard(onPinWidget) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamCard(exam: ExamEntity, today: LocalDate, onEdit: (ExamEntity) -> Unit, onDelete: (ExamEntity) -> Unit) {
    val day = StatsCalculator.localDay(exam.examDate)
    val days = ChronoUnit.DAYS.between(today, day).toInt()
    val countdown = when {
        days < 0 -> stringResource(R.string.exams_passed)
        days == 0 -> stringResource(R.string.exams_today)
        else -> pluralStringResource(R.plurals.exams_days_left, days, days)
    }
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = { onEdit(exam) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Color(exam.color)))
            Column(Modifier.weight(1f).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(exam.name, style = MaterialTheme.typography.titleMedium)
                if (exam.subject.isNotBlank()) {
                    Text(exam.subject, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                countdown,
                style = MaterialTheme.typography.titleMedium,
                color = if (days in 0..7) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.exams_more_actions, exam.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit(exam) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete(exam) })
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(onPinWidget: (Class<*>) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    stringResource(R.string.exams_widget_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
            Text(stringResource(R.string.exams_widget_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(onClick = { onPinWidget(ExamCountdownWidgetReceiver::class.java) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.exams_widget_countdown))
                }
                OutlinedButton(onClick = { onPinWidget(ExamMatrixWidgetReceiver::class.java) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.exams_widget_calendar))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamEditorDialog(initial: ExamDraft, onDismiss: () -> Unit, onSave: (ExamDraft) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var subject by rememberSaveable { mutableStateOf(initial.subject) }
    var dateEpochDay by rememberSaveable { mutableStateOf(initial.date?.toEpochDay()) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var triedSave by rememberSaveable { mutableStateOf(false) }
    val date = dateEpochDay?.let(LocalDate::ofEpochDay)
    val nameError = triedSave && name.isBlank()
    val dateError = triedSave && date == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == null) R.string.exams_new else R.string.exams_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.exams_field_name)) },
                    isError = nameError,
                    supportingText = { if (nameError) Text(stringResource(R.string.exams_error_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(stringResource(R.string.exams_field_subject)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
                    Text(
                        date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) ?: stringResource(R.string.exams_pick_date),
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
                }
                if (dateError) {
                    Text(stringResource(R.string.exams_error_date), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.exams_field_color), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ExamColors.forEachIndexed { index, c ->
                        val selected = color == c.toArgb()
                        val colorName = stringResource(R.string.exams_color_n, index + 1)
                        Surface(
                            shape = CircleShape,
                            color = c,
                            modifier = Modifier
                                .size(Spacing.minTouchTarget - Spacing.xs)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { color = c.toArgb() })
                                .semantics { contentDescription = colorName }
                        ) {
                            if (selected) Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                triedSave = true
                if (name.isNotBlank() && date != null) onSave(initial.copy(name = name, subject = subject, date = date, color = color))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (showDatePicker) {
        // The picker works in UTC midnights; convert to/from the local calendar date.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = pickerState) }
    }
}

/** Asks the launcher to add a widget. Returns false when the launcher can't (user adds it manually). */
private fun requestPinWidget(context: Context, receiver: Class<*>): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val manager = AppWidgetManager.getInstance(context)
    return manager.isRequestPinAppWidgetSupported &&
        manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
}

@ThemePreviews
@Composable
private fun ExamsPreview() {
    val zone = ZoneId.systemDefault()
    fun inDays(d: Long) = LocalDate.now().plusDays(d).atStartOfDay(zone).toInstant().toEpochMilli()
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExamsContent(
                exams = listOf(
                    ExamEntity(1, "Anatomy", inDays(3), "Upper limb", ExamColors[0].toArgb()),
                    ExamEntity(2, "Physiology", inDays(21), "Cardio", ExamColors[1].toArgb()),
                    ExamEntity(3, "Biochemistry", inDays(-5), "", ExamColors[3].toArgb())
                ),
                onAdd = {}, onEdit = {}, onDelete = {}, onPinWidget = {}
            )
        }
    }
}
