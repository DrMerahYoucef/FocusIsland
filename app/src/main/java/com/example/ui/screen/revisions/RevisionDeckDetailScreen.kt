package com.example.ui.screen.revisions

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.StatTile
import com.example.ui.theme.Spacing

private enum class CardSort { DUE, NEWEST, TITLE }

/**
 * One deck, or every card when [deckId] is [ALL_DECKS]. Review, sort, edit (name + colour)
 * and delete (with confirmation) live here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionDeckDetailScreen(
    deckId: String,
    viewModel: RevisionsViewModel,
    onNoteClick: (String) -> Unit,
    onStartReview: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isAll = deckId == ALL_DECKS
    val deck = state.decks.firstOrNull { it.id == deckId }
    val deckNames = remember(state.decks) { state.decks.associate { it.id to it.name } }

    var sort by rememberSaveable { mutableStateOf(CardSort.DUE) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val notes = remember(state.allNotes, deckId, sort) {
        val list = if (isAll) state.allNotes else state.allNotes.filter { it.deckId == deckId }
        when (sort) {
            CardSort.DUE -> list.sortedBy { it.dueDate }
            CardSort.NEWEST -> list.sortedByDescending { it.createdAt }
            CardSort.TITLE -> list.sortedBy { it.title.lowercase() }
        }
    }
    val dueCount = remember(notes) { notes.count { it.dueDate <= System.currentTimeMillis() } }
    val tasks = state.activeOcrTasks.filter { isAll || it.deckId == deckId }

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isAll) stringResource(R.string.study_all_cards) else deck?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (deck != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.study_deck_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.study_edit_deck)) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = { menuOpen = false; editing = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.study_delete_deck), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (notes.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onStartReview(deckId) },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(if (dueCount > 0) R.string.start_review else R.string.review_practice_ahead)) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = Spacing.md, end = Spacing.md,
                top = padding.calculateTopPadding() + Spacing.xs,
                bottom = padding.calculateBottomPadding() + Spacing.xxl * 2
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = Spacing.contentMaxWidth)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(
                        label = pluralStringResource(R.plurals.study_cards_label, notes.size),
                        value = notes.size.toString(),
                        icon = Icons.Rounded.Style,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = stringResource(R.string.study_due_label),
                        value = dueCount.toString(),
                        icon = Icons.Rounded.Schedule,
                        modifier = Modifier.weight(1f),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (tasks.isNotEmpty()) {
                item { BackgroundOcrTasksSection(tasks, onDismissTask = viewModel::dismissOcrTask) }
            }
            if (notes.isEmpty() && tasks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.study_deck_empty_title),
                        body = stringResource(R.string.study_deck_empty_body)
                    )
                }
            } else if (notes.isNotEmpty()) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        CardSort.entries.forEach { option ->
                            FilterChip(
                                selected = sort == option,
                                onClick = { sort = option },
                                label = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                CardSort.DUE -> R.string.study_sort_due
                                                CardSort.NEWEST -> R.string.study_sort_newest
                                                CardSort.TITLE -> R.string.study_sort_title
                                            }
                                        )
                                    )
                                },
                                leadingIcon = if (sort == option) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null
                            )
                        }
                    }
                }
                items(notes, key = { it.id }) { note ->
                    CardRow(note, onClick = { onNoteClick(note.id) }, deckName = if (isAll) deckNames[note.deckId] else null)
                }
            }
        }
    }

    if (editing && deck != null) {
        DeckDialog(
            title = stringResource(R.string.study_edit_deck),
            confirmLabel = stringResource(R.string.save),
            initialName = deck.name,
            initialColor = deck.colorHex,
            onConfirm = { name, color ->
                viewModel.updateDeck(deck.id, name, color)
                editing = false
            },
            onDismiss = { editing = false }
        )
    }
    if (confirmDelete && deck != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_deck_title),
            body = pluralStringResource(R.plurals.study_delete_deck_body, notes.size, deck.name, notes.size),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteDeckAndNotes(deck.id)
                onBack()
            },
            onDismiss = { confirmDelete = false },
            destructive = true
        )
    }
}
