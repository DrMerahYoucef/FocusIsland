package com.example.ui.screen.revisions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.rounded.DocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.entity.RevisionDeckEntity
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Per-deck numbers shown on the Study tab. */
data class DeckSummary(val deck: RevisionDeckEntity?, val cards: Int, val due: Int)

/**
 * Study tab: what's due today, decks, search across all cards, and "New card"
 * (camera, gallery photo, typed card, voice card).
 */
@Composable
fun RevisionsHomeScreen(
    viewModel: RevisionsViewModel,
    onAddClick: () -> Unit,
    onImageCaptured: (String) -> Unit = {},
    onPageScanned: (String) -> Unit = {},
    onDeckClick: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onStartSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showTypedCard by rememberSaveable { mutableStateOf(false) }
    var showVoiceCard by rememberSaveable { mutableStateOf(false) }
    var showNewDeck by rememberSaveable { mutableStateOf(false) }

    val photoFailed = stringResource(R.string.study_photo_failed)
    val cardSaved = stringResource(R.string.study_card_saved)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val file = ImageTools.copyUriToCache(context, uri)
            if (file != null) onImageCaptured(file.absolutePath) else snackbar.showSnackbar(photoFailed)
        }
    }

    // Google's document scanner: finds the page edges, straightens and cleans the page. Needs
    // Google Play services; without it, the in-app camera opens instead.
    val scanUnavailable = stringResource(R.string.study_scan_unavailable)
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .build()
        )
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            val file = ImageTools.copyUriToCache(context, uri)
            if (file != null) onPageScanned(file.absolutePath) else snackbar.showSnackbar(photoFailed)
        }
    }
    fun startScan() {
        val activity = context.findActivity()
        if (activity == null) { onAddClick(); return }
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                scope.launch { snackbar.showSnackbar(scanUnavailable) }
                onAddClick()
            }
    }

    val summaries = remember(state.decks, state.allNotes, state.dueNotes) {
        val cardsByDeck = state.allNotes.groupingBy { it.deckId }.eachCount()
        val dueByDeck = state.dueNotes.groupingBy { it.deckId }.eachCount()
        state.decks.map { DeckSummary(it, cardsByDeck[it.id] ?: 0, dueByDeck[it.id] ?: 0) }
    }
    val deckNames = remember(state.decks) { state.decks.associate { it.id to it.name } }
    val results = remember(state.allNotes, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else state.allNotes.filter { it.title.contains(q, ignoreCase = true) || it.plainTextPreview.contains(q, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xxl * 2),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                TodayCard(due = state.dueCount, total = state.totalCount, onReview = { onStartSessionClick(ALL_DECKS) })
            }
            if (state.activeOcrTasks.isNotEmpty()) {
                item { BackgroundOcrTasksSection(state.activeOcrTasks, onDismissTask = viewModel::dismissOcrTask) }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.study_search_cards)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.community_clear))
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotBlank()) {
                item { SectionHeader(pluralStringResource(R.plurals.study_search_results, results.size, results.size)) }
                if (results.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.study_search_empty_title),
                            body = stringResource(R.string.study_search_empty_body)
                        )
                    }
                } else {
                    items(results, key = { it.id }) { note ->
                        CardRow(note, onClick = { onNoteClick(note.id) }, deckName = deckNames[note.deckId])
                    }
                }
            } else if (state.totalCount == 0 && state.decks.size <= 1) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.study_empty_title),
                        body = stringResource(R.string.study_empty_body),
                        actionLabel = stringResource(R.string.study_new_card),
                        onAction = { showAddSheet = true }
                    )
                }
            } else {
                item {
                    SectionHeader(stringResource(R.string.study_decks)) {
                        TextButton(onClick = { showNewDeck = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.study_new_deck))
                        }
                    }
                }
                item {
                    DeckRow(
                        summary = DeckSummary(null, state.totalCount, state.dueCount),
                        onOpen = { onDeckClick(ALL_DECKS) },
                        onReview = { onStartSessionClick(ALL_DECKS) }
                    )
                }
                items(summaries, key = { it.deck!!.id }) { summary ->
                    DeckRow(
                        summary = summary,
                        onOpen = { onDeckClick(summary.deck!!.id) },
                        onReview = { onStartSessionClick(summary.deck!!.id) }
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
            horizontalAlignment = Alignment.End
        ) {
            SnackbarHost(snackbar)
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                expanded = fabExpanded,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.study_new_card)) }
            )
        }
    }

    if (showAddSheet) {
        AddCardSheet(
            onDismiss = { showAddSheet = false },
            onScan = { showAddSheet = false; startScan() },
            onCamera = { showAddSheet = false; onAddClick() },
            onGallery = {
                showAddSheet = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onTyped = { showAddSheet = false; showTypedCard = true },
            onVoice = { showAddSheet = false; showVoiceCard = true }
        )
    }
    if (showTypedCard) {
        ManualCardCreationDialog(
            viewModel = viewModel,
            onDismiss = { showTypedCard = false },
            onCardCreated = {
                showTypedCard = false
                scope.launch { snackbar.showSnackbar(cardSaved) }
            }
        )
    }
    if (showVoiceCard) {
        AudioCardCreationDialog(
            viewModel = viewModel,
            onDismiss = { showVoiceCard = false },
            onCardCreated = {
                showVoiceCard = false
                scope.launch { snackbar.showSnackbar(cardSaved) }
            }
        )
    }
    if (showNewDeck) {
        DeckDialog(
            title = stringResource(R.string.study_new_deck),
            confirmLabel = stringResource(R.string.study_create),
            onConfirm = { name, color ->
                viewModel.addDeckAndSelect(name, color)
                showNewDeck = false
            },
            onDismiss = { showNewDeck = false }
        )
    }
}

@Composable
private fun TodayCard(due: Int, total: Int, onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = if (due > 0) pluralStringResource(R.plurals.study_due_today, due, due) else stringResource(R.string.study_caught_up),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (due > 0) pluralStringResource(R.plurals.study_total_cards, total, total)
                else stringResource(R.string.study_caught_up_body),
                style = MaterialTheme.typography.bodyMedium
            )
            if (total > 0) {
                Spacer(Modifier.size(Spacing.xxs))
                Button(onClick = onReview) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(if (due > 0) R.string.start_review else R.string.review_practice_ahead))
                }
            }
        }
    }
}

/** A deck (or "All cards" when [DeckSummary.deck] is null): opens the deck; Review when cards are due. */
@Composable
private fun DeckRow(summary: DeckSummary, onOpen: () -> Unit, onReview: () -> Unit) {
    val name = summary.deck?.name ?: stringResource(R.string.study_all_cards)
    val color = summary.deck?.let { deckColor(it.colorHex) } ?: MaterialTheme.colorScheme.secondaryContainer
    ListItem(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.study_open_deck), onClick = onOpen),
        leadingContent = {
            Surface(shape = MaterialTheme.shapes.small, color = color, contentColor = contentOn(color), modifier = Modifier.size(Spacing.avatar)) {
                Box(contentAlignment = Alignment.Center) {
                    if (summary.deck == null) Icon(Icons.Rounded.Style, contentDescription = null)
                    else Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (summary.due > 0) stringResource(
                    R.string.study_deck_meta,
                    pluralStringResource(R.plurals.count_cards, summary.cards, summary.cards),
                    pluralStringResource(R.plurals.study_due_count, summary.due, summary.due)
                ) else pluralStringResource(R.plurals.count_cards, summary.cards, summary.cards)
            )
        },
        trailingContent = {
            if (summary.due > 0) {
                FilledTonalButton(onClick = onReview) {
                    Text(stringResource(R.string.study_review_short))
                }
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardSheet(
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onTyped: () -> Unit,
    onVoice: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.md)) {
            Text(
                stringResource(R.string.study_new_card),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs).semantics { heading() }
            )
            AddOption(Icons.Rounded.DocumentScanner, R.string.study_add_scan, R.string.study_add_scan_body, onScan)
            AddOption(Icons.Rounded.PhotoCamera, R.string.study_add_camera, R.string.study_add_camera_body, onCamera)
            AddOption(Icons.Rounded.PhotoLibrary, R.string.study_add_gallery, R.string.study_add_gallery_body, onGallery)
            AddOption(Icons.Rounded.EditNote, R.string.study_add_typed, R.string.study_add_typed_body, onTyped)
            AddOption(Icons.Rounded.Mic, R.string.study_add_voice, R.string.study_add_voice_body, onVoice)
        }
    }
}

@Composable
private fun AddOption(icon: ImageVector, title: Int, body: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(Spacing.avatar)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) }
    )
}

// ---------------------------------------------------------------- previews

@ThemePreviews
@Composable
private fun StudyHomePiecesPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TodayCard(due = 12, total = 86, onReview = {})
                DeckRow(DeckSummary(null, 86, 12), {}, {})
                DeckRow(DeckSummary(RevisionDeckEntity(name = "Biology", colorHex = "#1F6A64"), 24, 5), {}, {})
                DeckRow(DeckSummary(RevisionDeckEntity(name = "History", colorHex = "#9C4234"), 18, 0), {}, {})
            }
        }
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
