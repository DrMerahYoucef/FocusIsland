package com.example.ui.screen.revisions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.db.entity.RevisionDeckEntity
import com.example.data.db.entity.RevisionNoteEntity
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.DeckPalette
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode

/** The "All cards" pseudo deck used by the session and deck routes. */
const val ALL_DECKS = "ALL"

// ---------------------------------------------------------------- due labels

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** "Due now", "Due in 3 days"… Localised, with plurals. */
@Composable
fun dueLabel(dueDateMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = dueDateMillis - now
    return when {
        diff <= 0 -> stringResource(R.string.study_due_now)
        diff >= 30 * DAY -> (diff / (30 * DAY)).toInt().let { pluralStringResource(R.plurals.study_due_in_months, it, it) }
        diff >= DAY -> (diff / DAY).toInt().let { pluralStringResource(R.plurals.study_due_in_days, it, it) }
        diff >= HOUR -> (diff / HOUR).toInt().let { pluralStringResource(R.plurals.study_due_in_hours, it, it) }
        else -> (diff / MINUTE).toInt().coerceAtLeast(1).let { pluralStringResource(R.plurals.study_due_in_minutes, it, it) }
    }
}

// ---------------------------------------------------------------- deck colour

/** A deck's own colour (user data). Falls back to the theme primary if the stored value is invalid. */
@Composable
fun deckColor(hex: String?): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: MaterialTheme.colorScheme.primary

/** Small colour marker for a deck (decorative: the deck name is always next to it). */
@Composable
fun DeckDot(hex: String?, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = deckColor(hex), modifier = modifier.size(Spacing.sm)) {}
}

/** Black or white, whichever reads better on [background]. */
fun contentOn(background: Color): Color = if (background.luminance() > 0.5f) Color.Black else Color.White

// ---------------------------------------------------------------- card row

/** One card in a list: type icon, title, due label (highlighted when due). */
@Composable
fun CardRow(
    note: RevisionNoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deckName: String? = null
) {
    val isDue = note.dueDate <= System.currentTimeMillis()
    val due = dueLabel(note.dueDate)
    val typeIcon = when (note.mediaType) {
        "AUDIO" -> Icons.Rounded.Mic
        "IMAGE" -> Icons.Rounded.Image
        else -> Icons.AutoMirrored.Rounded.Notes
    }
    val typeLabel = stringResource(
        when (note.mediaType) {
            "AUDIO" -> R.string.study_card_type_voice
            "IMAGE" -> R.string.study_card_type_photo
            else -> R.string.study_card_type_text
        }
    )
    ListItem(
        modifier = modifier.clickable(onClickLabel = stringResource(R.string.study_open_card), onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(Spacing.avatar).semantics { contentDescription = typeLabel }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(typeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        headlineContent = {
            Text(note.title.ifBlank { stringResource(R.string.study_untitled) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = if (deckName != null) stringResource(R.string.study_card_meta, due, deckName) else due,
                color = if (isDue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

// ---------------------------------------------------------------- photo → card tasks

/** Cards being created from photos in the background (OCR / Gemini). Errors can be dismissed. */
@Composable
fun BackgroundOcrTasksSection(
    tasks: List<BackgroundOcrTask>,
    onDismissTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        tasks.forEach { task ->
            val engine = stringResource(
                when (task.kind) {
                    OcrTaskKind.ON_DEVICE -> R.string.study_engine_on_device
                    OcrTaskKind.GEMINI -> R.string.study_engine_gemini
                    OcrTaskKind.PHOTO -> R.string.study_engine_photo
                }
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (task.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (task.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when {
                                task.isError -> Icons.Rounded.Warning
                                task.kind == OcrTaskKind.PHOTO -> Icons.Rounded.Image
                                else -> Icons.Rounded.AutoAwesome
                            },
                            contentDescription = null,
                            modifier = Modifier.size(Spacing.iconSm)
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    when {
                                        task.isError -> R.string.study_task_failed
                                        task.kind == OcrTaskKind.PHOTO -> R.string.study_task_running_photo
                                        else -> R.string.study_task_running
                                    }
                                ),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (task.deckName.isBlank()) engine else stringResource(R.string.study_task_meta, task.deckName, engine),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (task.isError) {
                            IconButton(onClick = { onDismissTask(task.id) }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.study_task_dismiss))
                            }
                        }
                    }
                    if (task.isError) {
                        Text(
                            stringResource(if (task.kind == OcrTaskKind.GEMINI) R.string.study_task_failed_gemini else R.string.study_task_failed_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        task.errorMessage?.let {
                            Text(stringResource(R.string.study_task_error_detail, it), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.study_task_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- deck picker

/** Required deck choice for new cards, with "New deck" at the end of the menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckPicker(
    decks: List<RevisionDeckEntity>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onNewDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = decks.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.study_field_deck)) },
            placeholder = { Text(stringResource(R.string.study_pick_deck)) },
            leadingIcon = selected?.let { { DeckDot(it.colorHex) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = selected == null,
            supportingText = if (selected == null) {
                { Text(stringResource(R.string.study_pick_deck_error)) }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            decks.forEach { deck ->
                DropdownMenuItem(
                    text = { Text(deck.name) },
                    leadingIcon = { DeckDot(deck.colorHex) },
                    trailingIcon = if (deck.id == selectedId) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        onSelect(deck.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_new_deck)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onNewDeck()
                }
            )
        }
    }
}

// ---------------------------------------------------------------- new / edit deck

/** Name + colour. Used for creating and for editing a deck. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialColor: String = DeckPalette.choices.first(),
    onConfirm: (name: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val submit = {
        if (name.isBlank()) showError = true else onConfirm(name.trim(), color)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = { Text(stringResource(R.string.study_field_deck_name)) },
                    placeholder = { Text(stringResource(R.string.study_deck_name_example)) },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.study_error_deck_name)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                Text(stringResource(R.string.study_field_deck_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // Keep an existing custom colour selectable.
                    (listOf(initialColor) + DeckPalette.choices).distinct().forEachIndexed { index, hex ->
                        val selected = hex.equals(color, ignoreCase = true)
                        val label = stringResource(R.string.study_deck_color_n, index + 1)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(Spacing.minTouchTarget)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { color = hex })
                                .semantics { contentDescription = label }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = deckColor(hex),
                                modifier = Modifier
                                    .size(Spacing.xl)
                                    .then(
                                        if (selected) Modifier.border(Spacing.xxs / 2, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                            ) {
                                if (selected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = contentOn(deckColor(hex)), modifier = Modifier.size(Spacing.iconSm))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = submit) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------- previews

@ThemePreviews
@Composable
private fun CardRowPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Column {
            CardRow(RevisionNoteEntity(deckId = "d", title = "Krebs cycle: the eight steps"), onClick = {}, deckName = "Biology")
            CardRow(RevisionNoteEntity(deckId = "d", title = "Lecture 3 summary", mediaType = "AUDIO", dueDate = System.currentTimeMillis() + 3 * DAY), onClick = {})
            BackgroundOcrTasksSection(
                tasks = listOf(
                    BackgroundOcrTask(deckId = "d", deckName = "Biology", kind = OcrTaskKind.GEMINI),
                    BackgroundOcrTask(deckId = "d", deckName = "Biology", kind = OcrTaskKind.ON_DEVICE, isError = true, errorMessage = "Timeout")
                ),
                onDismissTask = {},
                modifier = Modifier.padding(Spacing.md)
            )
        }
    }
}
