package com.example.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.backup.BackupTaskState
import com.example.data.backup.BackupTaskType
import com.example.data.backup.CardTypeFilter
import com.example.data.db.entity.RevisionDeckEntity
import android.text.format.DateUtils
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.data.repository.GeminiModelTestStatus
import com.example.data.repository.GeminiModels
import com.example.data.repository.GeminiModelsInfo
import com.example.data.repository.ImportMode
import com.example.data.repository.verifyGeminiModel
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Gemini key

/**
 * The key is stored encrypted on this device only. Empty + Save removes it. "Test" asks Google
 * for the models this key can use, which also fills the model list.
 */
@Composable
fun GeminiKeyDialog(
    current: String,
    onSave: (String) -> Unit,
    onGetKey: () -> Unit,
    onTest: suspend (String) -> Int?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var key by rememberSaveable { mutableStateOf(current) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var tested by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_gemini_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.settings_gemini_key_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim(); tested = false },
                    label = { Text(stringResource(R.string.settings_gemini_key_field)) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(if (visible) R.string.settings_hide_key else R.string.settings_show_key)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedButton(
                        onClick = {
                            testing = true
                            scope.launch {
                                result = onTest(key)
                                tested = true
                                testing = false
                            }
                        },
                        enabled = key.isNotBlank() && !testing
                    ) { Text(stringResource(R.string.settings_key_test)) }
                    TextButton(onClick = onGetKey) { Text(stringResource(R.string.settings_gemini_get_key)) }
                }
                when {
                    testing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    tested -> {
                        val count = result
                        Text(
                            if (count != null) pluralStringResource(R.plurals.settings_key_works, count, count) else stringResource(R.string.settings_key_fails),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (count != null) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------- Gemini models

/** Short name of the model choice, for the Settings row. */
@Composable
fun geminiModelLabel(selected: String): String = when (selected) {
    GeminiModels.AUTO_FLASH -> stringResource(R.string.settings_model_auto_flash)
    GeminiModels.AUTO_FLASH_LITE -> stringResource(R.string.settings_model_auto_flash_lite)
    GeminiModels.AUTO_PRO -> stringResource(R.string.settings_model_auto_pro)
    else -> selected
}

/**
 * Choose "newest of a family" (recommended) or a fixed model from the list Google returns for
 * this key; refresh that list, test which models answer, add a model id by hand.
 */
@Composable
fun GeminiModelsDialog(
    apiKey: String,
    info: GeminiModelsInfo,
    selected: String,
    custom: List<String>,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<Map<String, GeminiModelTestStatus>>(emptyMap()) }
    var newModel by rememberSaveable { mutableStateOf("") }
    val testing = statuses.values.any { it == GeminiModelTestStatus.TESTING }
    val listed = (GeminiModels.rank(info.models.ifEmpty { GeminiModels.FALLBACK }) + custom).distinct()
    val autoBodies = mapOf(
        GeminiModels.AUTO_FLASH to R.string.settings_model_auto_flash_body,
        GeminiModels.AUTO_FLASH_LITE to R.string.settings_model_auto_flash_lite_body,
        GeminiModels.AUTO_PRO to R.string.settings_model_auto_pro_body
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_gemini_models)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.settings_gemini_models_body), style = MaterialTheme.typography.bodyMedium)
                ModelsStatus(apiKey.isNotBlank(), info)
                Column(Modifier.selectableGroup()) {
                    SectionLabel(stringResource(R.string.settings_models_auto_header))
                    GeminiModels.AUTO_CHOICES.forEach { choice ->
                        val now = GeminiModels.attemptOrder(choice, info.models, custom).first()
                        ModelRow(
                            title = geminiModelLabel(choice),
                            body = stringResource(autoBodies.getValue(choice)) + " · " + stringResource(R.string.settings_model_now, now),
                            selected = selected == choice,
                            status = null,
                            onSelect = { onSelect(choice) },
                            onRemove = null
                        )
                    }
                    SectionLabel(stringResource(R.string.settings_models_list_header))
                    listed.forEach { id ->
                        ModelRow(
                            title = id,
                            body = null,
                            selected = selected == id,
                            status = statuses[id],
                            onSelect = { onSelect(id) },
                            onRemove = if (id in custom && id != selected) ({ onRemove(id) }) else null
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedButton(onClick = onRefresh, enabled = apiKey.isNotBlank() && !info.refreshing, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_models_refresh), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            statuses = listed.associateWith { GeminiModelTestStatus.TESTING }
                            listed.forEach { id ->
                                scope.launch {
                                    val ok = runCatching { verifyGeminiModel(apiKey, id) }.getOrDefault(false)
                                    statuses = statuses + (id to if (ok) GeminiModelTestStatus.APPROVED else GeminiModelTestStatus.FAILED)
                                }
                            }
                        },
                        enabled = apiKey.isNotBlank() && !testing,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.settings_model_test), maxLines = 1) }
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newModel,
                        onValueChange = { newModel = it.trim() },
                        label = { Text(stringResource(R.string.settings_model_add_field)) },
                        placeholder = { Text(GeminiModels.FALLBACK.first()) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    TextButton(
                        onClick = { onAdd(newModel); newModel = "" },
                        enabled = newModel.isNotBlank() && newModel !in listed
                    ) { Text(stringResource(R.string.radio_add)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.study_close)) } }
    )
}

@Composable
private fun ModelsStatus(hasKey: Boolean, info: GeminiModelsInfo) {
    val text = when {
        !hasKey -> stringResource(R.string.settings_models_no_key)
        info.refreshing -> stringResource(R.string.settings_models_checking)
        info.checkedAt > 0L -> stringResource(
            R.string.settings_models_checked,
            DateUtils.getRelativeTimeSpanString(info.checkedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        ) + if (info.failed) " · " + stringResource(R.string.settings_models_failed_short) else ""
        info.failed -> stringResource(R.string.settings_models_failed)
        else -> stringResource(R.string.settings_models_checking)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (info.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xxs).semantics { heading() }
    )
}

@Composable
private fun ModelRow(
    title: String,
    body: String?,
    selected: Boolean,
    status: GeminiModelTestStatus?,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)?
) {
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = when {
            status == GeminiModelTestStatus.APPROVED -> { { Text(stringResource(R.string.settings_model_works), color = MaterialTheme.extendedColors.success) } }
            status == GeminiModelTestStatus.FAILED -> { { Text(stringResource(R.string.settings_model_fails), color = MaterialTheme.colorScheme.error) } }
            body != null -> { { Text(body) } }
            else -> null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    GeminiModelTestStatus.TESTING -> CircularProgressIndicator(strokeWidth = Spacing.progressStroke, modifier = Modifier.size(Spacing.iconSm))
                    GeminiModelTestStatus.APPROVED -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.extendedColors.success)
                    GeminiModelTestStatus.FAILED -> Icon(Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    else -> {}
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.settings_model_remove, title))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    )
}

// ---------------------------------------------------------------- prompt

/** Full-screen editor for the instructions Gemini follows when it reads a photo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorDialog(current: String, defaultPrompt: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_prompt)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel)) } },
                    actions = {
                        Button(
                            // Saving the default text clears the override, so future app updates to it apply.
                            onClick = { onSave(text.trim().takeIf { it.isNotEmpty() && it != defaultPrompt.trim() }) },
                            modifier = Modifier.padding(end = Spacing.xs)
                        ) { Text(stringResource(R.string.save)) }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(stringResource(R.string.settings_prompt_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.settings_prompt)) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                TextButton(onClick = { text = defaultPrompt }, enabled = text != defaultPrompt) {
                    Text(stringResource(R.string.settings_prompt_restore))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- backup

@Composable
fun cardTypeLabel(filter: CardTypeFilter): String = stringResource(
    when (filter) {
        CardTypeFilter.ALL -> R.string.backup_type_all
        CardTypeFilter.TEXT -> R.string.backup_type_text
        CardTypeFilter.IMAGE -> R.string.backup_type_photo
        CardTypeFilter.AUDIO -> R.string.backup_type_voice
    }
)

/** Choose what goes into the .focuscards file: card types and one deck or all decks. */
@Composable
fun ExportDialog(
    decks: List<RevisionDeckEntity>,
    totalCards: Int,
    onStart: (CardTypeFilter, String) -> Unit,
    onDismiss: () -> Unit
) {
    var type by rememberSaveable { mutableStateOf(CardTypeFilter.ALL) }
    var deckId by rememberSaveable { mutableStateOf(ALL_DECKS_FILTER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_create)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.backup_which_cards), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    CardTypeFilter.entries.forEach { f -> RadioLine(cardTypeLabel(f), type == f) { type = f } }
                }
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(R.string.backup_which_decks), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    RadioLine(stringResource(R.string.backup_all_decks, totalCards), deckId == ALL_DECKS_FILTER) { deckId = ALL_DECKS_FILTER }
                    decks.forEach { d -> RadioLine(d.name, deckId == d.id) { deckId = d.id } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(type, deckId) }) { Text(stringResource(R.string.backup_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Merge keeps your cards and adds the backup's; Replace deletes your cards first. */
@Composable
fun ImportDialog(onStart: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(ImportMode.MERGE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore)) },
        text = {
            Column(Modifier.selectableGroup()) {
                RadioLine(stringResource(R.string.backup_merge), mode == ImportMode.MERGE, stringResource(R.string.backup_merge_body)) { mode = ImportMode.MERGE }
                RadioLine(
                    stringResource(R.string.backup_replace), mode == ImportMode.REPLACE_ALL, stringResource(R.string.backup_replace_body),
                    titleColor = MaterialTheme.colorScheme.error
                ) { mode = ImportMode.REPLACE_ALL }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(mode) }) {
                Text(
                    stringResource(if (mode == ImportMode.REPLACE_ALL) R.string.backup_replace_confirm else R.string.backup_restore_confirm),
                    color = if (mode == ImportMode.REPLACE_ALL) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RadioLine(title: String, selected: Boolean, body: String? = null, titleColor: Color = Color.Unspecified, onClick: () -> Unit) {
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = body?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    )
}

/** Progress, result or error of the last backup / restore. Runs in the background service. */
@Composable
fun BackupStatusCard(state: BackupTaskState, onSave: () -> Unit, onSend: () -> Unit, onDismiss: () -> Unit) {
    val (container, content) = when (state) {
        is BackupTaskState.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    if (state is BackupTaskState.Idle) return
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when (state) {
                is BackupTaskState.Running -> {
                    Text(
                        stringResource(if (state.type == BackupTaskType.EXPORT) R.string.backup_running_export else R.string.backup_running_import, (state.progress * 100).toInt()),
                        style = MaterialTheme.typography.titleSmall
                    )
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.backup_running_hint), style = MaterialTheme.typography.bodySmall)
                }
                is BackupTaskState.ExportCompleted -> {
                    Text(stringResource(R.string.backup_export_done), style = MaterialTheme.typography.titleSmall)
                    Text(state.result.summaryText, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Button(onClick = onSave) { Text(stringResource(R.string.backup_save_file)) }
                        OutlinedButton(onClick = onSend) { Text(stringResource(R.string.study_send)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.study_task_dismiss)) }
                    }
                }
                is BackupTaskState.ImportCompleted -> {
                    Text(stringResource(R.string.backup_import_done), style = MaterialTheme.typography.titleSmall)
                    Text(state.result.summaryText, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.study_task_dismiss)) }
                }
                is BackupTaskState.Error -> {
                    Text(
                        stringResource(if (state.type == BackupTaskType.EXPORT) R.string.backup_export_failed else R.string.backup_import_failed),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(stringResource(R.string.study_task_error_detail, state.errorMessage), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.study_task_dismiss)) }
                }
                BackupTaskState.Idle -> {}
            }
        }
    }
}

/** Deck filter value meaning "every deck" (what CardBackupService expects). */
const val ALL_DECKS_FILTER = "ALL"

@ThemePreviews
@Composable
private fun BackupStatusPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Column {
            BackupStatusCard(BackupTaskState.Running(BackupTaskType.EXPORT, 0.4f, ""), {}, {}, {})
            BackupStatusCard(BackupTaskState.Error(BackupTaskType.IMPORT, "Not a .focuscards file"), {}, {}, {})
        }
    }
}
