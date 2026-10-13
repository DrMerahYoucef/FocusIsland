package com.example.ui.screen.revisions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.service.AudioRecordingService
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.theme.Spacing

/** Full-screen form dialog: close (X), title, Save in the app bar; scrollable content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenForm(
    title: String,
    saveLabel: String,
    saveEnabled: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.study_close))
                            }
                        },
                        actions = {
                            if (saving) {
                                CircularProgressIndicator(
                                    strokeWidth = Spacing.progressStroke,
                                    modifier = Modifier.padding(horizontal = Spacing.md).size(Spacing.iconSm)
                                )
                            } else {
                                TextButton(onClick = onSave, enabled = saveEnabled) { Text(saveLabel) }
                            }
                        }
                    )
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = Spacing.contentMaxWidth)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        content = content
                    )
                }
            }
        }
    }
}

/** Shared by both forms: pick a deck or create one inline. */
@Composable
private fun DeckField(viewModel: RevisionsViewModel, selectedId: String, onSelect: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newDeck by rememberSaveable { mutableStateOf(false) }
    DeckPicker(decks = state.decks, selectedId = selectedId, onSelect = onSelect, onNewDeck = { newDeck = true })
    if (newDeck) {
        DeckDialog(
            title = stringResource(R.string.study_new_deck),
            confirmLabel = stringResource(R.string.study_create),
            onConfirm = { name, color ->
                viewModel.addDeckAndSelect(name, color) { onSelect(it) }
                newDeck = false
            },
            onDismiss = { newDeck = false }
        )
    }
}

/** "Suggest a title" with Gemini, or a short explanation when no key is set. */
@Composable
private fun TitleSuggestion(hasApiKey: Boolean, busy: Boolean, enabled: Boolean, onSuggest: () -> Unit) {
    if (hasApiKey) {
        TextButton(onClick = onSuggest, enabled = enabled && !busy) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = Spacing.progressStroke, modifier = Modifier.size(Spacing.iconSm))
            } else {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
            }
            Spacer(Modifier.size(Spacing.xs))
            Text(stringResource(R.string.study_suggest_title))
        }
    } else {
        Text(
            stringResource(R.string.study_suggest_title_no_key),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun defaultDeckId(state: RevisionsUiState): String =
    state.selectedDeckId.takeIf { id -> id != ALL_DECKS && state.decks.any { it.id == id } }
        ?: state.decks.firstOrNull()?.id.orEmpty()

// ====================================================================== Typed card

@Composable
fun ManualCardCreationDialog(
    viewModel: RevisionsViewModel,
    onDismiss: () -> Unit,
    onCardCreated: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deckId by rememberSaveable { mutableStateOf(defaultDeckId(state)) }
    var title by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val canSave = deckId.isNotBlank() && title.isNotBlank() && answer.isNotBlank() && !saving
    val close = { if (title.isNotBlank() || answer.isNotBlank()) confirmDiscard = true else onDismiss() }

    FullScreenForm(
        title = stringResource(R.string.study_new_text_card),
        saveLabel = stringResource(R.string.save),
        saveEnabled = canSave,
        saving = saving,
        onSave = {
            saving = true
            viewModel.createManualNote(title = title.trim(), answerText = answer.trim(), deckId = deckId) {
                saving = false
                onCardCreated()
            }
        },
        onClose = close
    ) {
        DeckField(viewModel, deckId) { deckId = it }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.study_field_question)) },
            supportingText = { Text(stringResource(R.string.study_field_question_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        TitleSuggestion(
            hasApiKey = state.hasApiKey,
            busy = state.isProcessingOcr,
            enabled = answer.isNotBlank() || title.isNotBlank(),
            onSuggest = { viewModel.generateTitleFromContent(answer.ifBlank { title }) { title = it } }
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text(stringResource(R.string.study_field_answer)) },
            supportingText = { Text(stringResource(R.string.study_field_answer_hint)) },
            minLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.study_discard_title),
            body = stringResource(R.string.study_discard_body),
            confirmLabel = stringResource(R.string.study_discard),
            dismissLabel = stringResource(R.string.study_keep_editing),
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
            destructive = true
        )
    }
}

// ====================================================================== Voice card

/**
 * Record a voice note (keeps recording with the screen off, via AudioRecordingService),
 * then save it as a card. Closing while recording stops the recording.
 */
@Composable
fun AudioCardCreationDialog(
    viewModel: RevisionsViewModel,
    onDismiss: () -> Unit,
    onCardCreated: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recording by AudioRecordingService.recordingState.collectAsStateWithLifecycle()
    var deckId by rememberSaveable { mutableStateOf(defaultDeckId(state)) }
    var title by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }

    val recordedFile = recording.recordedFile?.takeIf { it.exists() }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) AudioRecordingService.startRecording(context)
    }
    val startRecording = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) AudioRecordingService.startRecording(context) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val discard = {
        if (recording.isRecording) AudioRecordingService.stopRecording(context)
        AudioRecordingService.clearLastRecording()
        onDismiss()
    }
    val close = { if (recording.isRecording || recordedFile != null) confirmDiscard = true else onDismiss() }

    FullScreenForm(
        title = stringResource(R.string.study_new_voice_card),
        saveLabel = stringResource(R.string.save),
        saveEnabled = deckId.isNotBlank() && recordedFile != null && !recording.isRecording && !saving,
        saving = saving,
        onSave = {
            val file = recordedFile ?: return@FullScreenForm
            saving = true
            saveFailed = false
            viewModel.createLocalAudioCard(recordingFile = file, userTitle = title.trim().ifBlank { null }, deckId = deckId) { success, _ ->
                saving = false
                saveFailed = !success // used to fail silently
                if (success) {
                    AudioRecordingService.clearLastRecording()
                    onCardCreated()
                }
            }
        },
        onClose = close
    ) {
        DeckField(viewModel, deckId) { deckId = it }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                when {
                    recording.isRecording -> {
                        val seconds = recording.elapsedSeconds
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error, modifier = Modifier.size(Spacing.sm)) {}
                            Spacer(Modifier.size(Spacing.xs))
                            Text(
                                stringResource(R.string.study_recording_time, seconds / 60, seconds % 60),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        FilledIconButton(
                            onClick = { AudioRecordingService.stopRecording(context) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.size(Spacing.recordButton)
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.study_stop_recording), modifier = Modifier.size(Spacing.iconLg))
                        }
                        Text(
                            stringResource(R.string.study_recording_background),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    recordedFile != null -> {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Spacing.iconLg))
                        Text(stringResource(R.string.study_recording_ready), style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = {
                            AudioRecordingService.clearLastRecording()
                            startRecording()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
                            Spacer(Modifier.size(Spacing.xs))
                            Text(stringResource(R.string.study_record_again))
                        }
                    }
                    else -> {
                        FilledIconButton(onClick = startRecording, modifier = Modifier.size(Spacing.recordButton)) {
                            Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.study_start_recording), modifier = Modifier.size(Spacing.iconLg))
                        }
                        Text(stringResource(R.string.study_tap_to_record), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (permissionDenied) {
                    Text(stringResource(R.string.study_mic_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (recording.error != null) {
                    Text(stringResource(R.string.recording_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (saveFailed) {
                    Text(stringResource(R.string.study_voice_save_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.study_field_title_optional)) },
            supportingText = { Text(stringResource(R.string.study_field_title_auto)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        TitleSuggestion(
            hasApiKey = state.hasApiKey,
            busy = state.isProcessingOcr,
            enabled = recordedFile != null,
            onSuggest = { viewModel.generateTitleFromAudio(recordedFile, title) { title = it } }
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.study_discard_recording_title),
            body = stringResource(R.string.study_discard_recording_body),
            confirmLabel = stringResource(R.string.study_discard),
            dismissLabel = stringResource(R.string.study_keep_editing),
            onConfirm = {
                confirmDiscard = false
                discard()
            },
            onDismiss = { confirmDiscard = false },
            destructive = true
        )
    }
}
