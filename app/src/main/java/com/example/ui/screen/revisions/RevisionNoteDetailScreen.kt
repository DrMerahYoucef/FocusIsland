package com.example.ui.screen.revisions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.entity.RevisionDeckEntity
import com.example.data.db.entity.RevisionNoteEntity
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.EmptyState
import com.example.ui.revisions.CardInlineEditor
import com.example.ui.revisions.FullScreenNoteReaderDialog
import com.example.ui.revisions.HighlightedMarkdownWithTables
import com.example.ui.revisions.NoteBlocksRenderer
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File

/**
 * The body of a card (photo, recording or text), shared by the card screen and the review session.
 * Photo/voice cards created without text carry a placeholder preview that is not shown.
 */
@Composable
fun CardContent(
    note: RevisionNoteEntity,
    onZoomImage: (File) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    autoPlayAudio: Boolean = false,
    mediaActions: (@Composable (File) -> Unit)? = null
) {
    val context = LocalContext.current
    val mediaFile = remember(note.mediaFilePath) { note.mediaFilePath?.let(::File)?.takeIf { it.exists() } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (mediaFile != null && note.mediaType == "IMAGE") {
            CardImage(mediaFile, onZoom = { onZoomImage(mediaFile) })
            mediaActions?.invoke(mediaFile)
        } else if (mediaFile != null && note.mediaType == "AUDIO") {
            AudioCardPlayerView(audioFile = mediaFile, context = context, autoPlay = autoPlayAudio)
            mediaActions?.invoke(mediaFile)
        }
        val hasBlocks = note.contentBlocksJson.isNotBlank() && note.contentBlocksJson != "[]"
        val plain = note.plainTextPreview.ifBlank { note.contentMarkdown }.trim()
        val isPlaceholder = plain.equals("Photo Card", ignoreCase = true) || plain.equals("Voice Note Card", ignoreCase = true)
        when {
            hasBlocks -> NoteBlocksRenderer(blocksJson = note.contentBlocksJson, fontSize = fontSize, modifier = Modifier.fillMaxWidth())
            plain.isNotBlank() && !isPlaceholder -> HighlightedMarkdownWithTables(markdown = plain, fontSize = fontSize, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One card: read, edit in place, open full screen, move to another deck, copy, share media, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionNoteDetailScreen(
    noteId: String,
    viewModel: RevisionsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val note = state.allNotes.firstOrNull { it.id == noteId }
    val deck = note?.let { n -> state.decks.firstOrNull { it.id == n.deckId } }

    var editing by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showReader by rememberSaveable { mutableStateOf(false) }
    var showMove by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var zoomFile by remember { mutableStateOf<File?>(null) }

    val msgSaved = stringResource(R.string.study_card_saved)
    val msgCopied = stringResource(R.string.study_text_copied)
    val msgMediaSaved = stringResource(R.string.study_media_saved)
    val msgFailed = stringResource(R.string.study_media_failed)
    val shareTitle = stringResource(R.string.study_share_title)
    fun notify(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    val isTextCard = note != null && note.mediaType == null

    // The editor is a full screen of its own: it handles Close/back and asks before discarding.
    if (editing && note != null) {
        CardInlineEditor(
            note = note,
            onSave = { updated ->
                viewModel.updateNote(updated)
                editing = false
                notify(msgSaved)
            },
            onCancel = { editing = false },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(deck?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (note != null) {
                        if (isTextCard) {
                            IconButton(onClick = { editing = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                            }
                        }
                        IconButton(onClick = { showReader = true }) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.full_screen))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.study_card_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.study_move_card)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                                    onClick = { menuOpen = false; showMove = true }
                                )
                                if (isTextCard) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.study_copy_text)) },
                                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            clipboard.setText(AnnotatedString(note.plainTextPreview.ifBlank { note.contentMarkdown }))
                                            notify(msgCopied)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (note == null) {
            EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.study_card_missing_title),
                body = stringResource(R.string.study_card_missing_body),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                val isDue = note.dueDate <= System.currentTimeMillis()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeckDot(deck?.colorHex)
                    Spacer(Modifier.size(Spacing.xs))
                    Text(
                        dueLabel(note.dueDate),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    note.title.ifBlank { stringResource(R.string.study_untitled) },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                HorizontalDivider()
                CardContent(
                    note = note,
                    onZoomImage = { zoomFile = it },
                    modifier = Modifier.clickable(
                        enabled = isTextCard,
                        onClickLabel = stringResource(R.string.full_screen),
                        onClick = { showReader = true }
                    ),
                    mediaActions = { file ->
                        MediaActions(
                            onSave = {
                                val ok = if (note.mediaType == "AUDIO") saveAudioToDownloads(context, file) else saveImageToGallery(context, file)
                                notify(if (ok) msgMediaSaved else msgFailed)
                            },
                            onShare = { if (!shareMediaFile(context, file, shareTitle)) notify(msgFailed) }
                        )
                    }
                )
                ScheduleSummary(note)
            }
        }
    }

    zoomFile?.let { ZoomableImageDialog(it) { zoomFile = null } }

    if (showReader && note != null) {
        FullScreenNoteReaderDialog(
            note = note,
            deckName = deck?.name.orEmpty(),
            onDismiss = { showReader = false },
            onSaveNote = { viewModel.updateNote(it) }
        )
    }
    if (showMove && note != null) {
        MoveToDeckDialog(
            currentDeckId = note.deckId,
            decks = state.decks,
            onDismiss = { showMove = false },
            onMove = { targetId ->
                viewModel.moveNoteToDeck(note.id, targetId)
                showMove = false
                val name = state.decks.firstOrNull { it.id == targetId }?.name.orEmpty()
                notify(context.getString(R.string.study_card_moved, name))
            },
            onCreateAndMove = { name, color ->
                viewModel.addDeckAndSelect(name, color) { newId -> viewModel.moveNoteToDeck(note.id, newId) }
                showMove = false
                notify(context.getString(R.string.study_card_moved, name))
            }
        )
    }
    if (confirmDelete && note != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_card_title),
            body = stringResource(R.string.delete_card_body),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteNote(note)
                onBack()
            },
            onDismiss = { confirmDelete = false },
            destructive = true
        )
    }
}

/** Where the card stands in spaced repetition: next review, interval, times reviewed. */
@Composable
private fun ScheduleSummary(note: RevisionNoteEntity) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SummaryItem(stringResource(R.string.study_next_review), dueLabel(note.dueDate), Modifier.weight(1f))
            SummaryItem(
                stringResource(R.string.study_interval),
                if (note.intervalDays <= 0) stringResource(R.string.study_interval_new)
                else pluralStringResource(R.plurals.days_count, note.intervalDays, note.intervalDays),
                Modifier.weight(1f)
            )
            SummaryItem(
                stringResource(R.string.study_reviews),
                note.repetitions.toString(),
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Pick another deck (radio list) or create one and move the card there. */
@Composable
fun MoveToDeckDialog(
    currentDeckId: String,
    decks: List<RevisionDeckEntity>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
    onCreateAndMove: (name: String, colorHex: String) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(currentDeckId) }
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        DeckDialog(
            title = stringResource(R.string.study_new_deck),
            confirmLabel = stringResource(R.string.study_create_and_move),
            onConfirm = onCreateAndMove,
            onDismiss = { creating = false }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_move_card)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                decks.forEach { deck ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.minTouchTarget)
                            .selectable(selected = selected == deck.id, role = Role.RadioButton, onClick = { selected = deck.id })
                    ) {
                        RadioButton(selected = selected == deck.id, onClick = null)
                        Spacer(Modifier.size(Spacing.sm))
                        DeckDot(deck.colorHex)
                        Spacer(Modifier.size(Spacing.xs))
                        Text(deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
                    Spacer(Modifier.size(Spacing.xs))
                    Text(stringResource(R.string.study_new_deck))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(selected) }, enabled = selected != currentDeckId) {
                Text(stringResource(R.string.study_move))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
