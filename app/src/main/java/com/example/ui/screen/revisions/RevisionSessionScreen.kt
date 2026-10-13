package com.example.ui.screen.revisions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.entity.RevisionNoteEntity
import com.example.data.srs.ReviewGrade
import com.example.data.srs.Sm2Scheduler
import com.example.ui.designsystem.BannerTone
import com.example.ui.designsystem.InfoBanner
import com.example.ui.designsystem.LoadingState
import com.example.ui.revisions.FullScreenNoteReaderDialog
import com.example.ui.theme.Spacing
import com.example.ui.theme.extendedColors
import java.io.File

/**
 * Review session: tap the card (or "Show answer") to flip, then grade it.
 * Each grade button shows when the card would come back. Undo returns to the previous card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionSessionScreen(
    deckId: String,
    viewModel: RevisionsViewModel,
    onFinishSession: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.reviewSession.collectAsStateWithLifecycle()
    LaunchedEffect(deckId) { viewModel.startReviewSession(deckId) }

    // Show the latest saved version (edits made in the reader appear immediately).
    val current: RevisionNoteEntity? = session.current?.let { queued -> state.allNotes.firstOrNull { it.id == queued.id } ?: queued }
    var flipped by rememberSaveable(current?.id) { mutableStateOf(false) }
    var showReader by rememberSaveable { mutableStateOf(false) }
    var zoomFile by remember { mutableStateOf<File?>(null) }
    val haptics = LocalHapticFeedback.current

    val total = session.queue.size
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (current != null && total > 0) {
                            Text(stringResource(R.string.review_progress, session.index + 1, total))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.study_end_review))
                        }
                    },
                    actions = {
                        if (session.canUndo) {
                            IconButton(onClick = { viewModel.undoLastGrade() }) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.study_undo_answer))
                            }
                        }
                        if (current != null) {
                            IconButton(onClick = { showReader = true }) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.full_screen))
                            }
                        }
                    }
                )
                if (current != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { session.index.toFloat() / total },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                session.isLoading -> LoadingState()
                current == null -> SessionDone(
                    nothingDue = session.isEmpty,
                    practiceAhead = session.practiceAhead,
                    reviewed = total,
                    onPractice = { viewModel.startReviewSession(deckId, practiceAhead = true) },
                    onDone = onFinishSession
                )
                else -> Column(
                    Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    if (session.practiceAhead) {
                        InfoBanner(text = stringResource(R.string.review_practice_ahead_banner), tone = BannerTone.Info)
                    }
                    FlipCard(
                        note = current,
                        flipped = flipped,
                        onFlip = { flipped = !flipped },
                        onZoomImage = { zoomFile = it },
                        modifier = Modifier.weight(1f)
                    )
                    if (!flipped) {
                        Button(
                            onClick = { flipped = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.buttonHeight)
                        ) { Text(stringResource(R.string.study_show_answer)) }
                    } else {
                        GradeButtons(
                            note = current,
                            showIntervals = !session.practiceAhead,
                            onGrade = { grade ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                flipped = false
                                viewModel.gradeCurrentCard(grade)
                            }
                        )
                    }
                }
            }
        }
    }

    zoomFile?.let { ZoomableImageDialog(it) { zoomFile = null } }
    if (showReader && current != null) {
        FullScreenNoteReaderDialog(
            note = current,
            deckName = state.decks.firstOrNull { it.id == current.deckId }?.name.orEmpty(),
            onDismiss = { showReader = false },
            onSaveNote = { viewModel.updateNote(it) }
        )
    }
}

@Composable
private fun FlipCard(
    note: RevisionNoteEntity,
    flipped: Boolean,
    onFlip: () -> Unit,
    onZoomImage: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "cardFlip"
    )
    var textScale by remember(note.id) { mutableFloatStateOf(1f) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
            .clickable(
                onClickLabel = stringResource(if (flipped) R.string.study_show_question else R.string.study_show_answer),
                onClick = onFlip
            )
    ) {
        if (rotation <= 90f) {
            Column(
                Modifier.fillMaxSize().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.study_question), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    note.title.ifBlank { stringResource(R.string.study_untitled) },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Spacing.iconSm))
                    Spacer(Modifier.size(Spacing.xs))
                    Text(stringResource(R.string.tap_card_to_reveal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Back side: drawn mirrored so it reads correctly after the flip. Pinch to resize the text.
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .pointerInput(note.id) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) textScale = (textScale * zoom).coerceIn(0.7f, 3.5f)
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(stringResource(R.string.study_answer), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(note.title, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                CardContent(note = note, onZoomImage = onZoomImage, fontSize = (15 * textScale).sp, autoPlayAudio = true)
            }
        }
    }
}

@Composable
private fun GradeButtons(note: RevisionNoteEntity, showIntervals: Boolean, onGrade: (ReviewGrade) -> Unit) {
    val now = remember(note.id) { System.currentTimeMillis() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ReviewGrade.entries.forEach { grade ->
            val (container, content) = gradeColors(grade)
            val label = stringResource(
                when (grade) {
                    ReviewGrade.AGAIN -> R.string.grade_again
                    ReviewGrade.HARD -> R.string.grade_hard
                    ReviewGrade.GOOD -> R.string.grade_good
                    ReviewGrade.EASY -> R.string.grade_easy
                }
            )
            val nextDue = remember(note, grade) { Sm2Scheduler.schedule(note, grade).dueDate }
            val spoken = if (showIntervals) stringResource(R.string.study_grade_a11y, label, dueLabel(nextDue, now)) else label
            Button(
                onClick = { onGrade(grade) },
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.xxs, vertical = Spacing.xs),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Spacing.buttonHeight + Spacing.xs)
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    if (showIntervals) {
                        Text(shortInterval(nextDue - now), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun gradeColors(grade: ReviewGrade): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (grade) {
        ReviewGrade.AGAIN -> c.errorContainer to c.onErrorContainer
        ReviewGrade.HARD -> c.tertiaryContainer to c.onTertiaryContainer
        ReviewGrade.GOOD -> c.primaryContainer to c.onPrimaryContainer
        ReviewGrade.EASY -> MaterialTheme.extendedColors.successContainer to MaterialTheme.extendedColors.onSuccessContainer
    }
}

/** Compact "10 min", "3 d", "2 mo" for the grade buttons. */
@Composable
private fun shortInterval(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(1)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 30 -> stringResource(R.string.study_short_months, (days / 30).toInt())
        days >= 1 -> stringResource(R.string.study_short_days, days.toInt())
        hours >= 1 -> stringResource(R.string.study_short_hours, hours.toInt())
        else -> stringResource(R.string.study_short_minutes, minutes.toInt())
    }
}

@Composable
private fun SessionDone(
    nothingDue: Boolean,
    practiceAhead: Boolean,
    reviewed: Int,
    onPractice: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.extendedColors.successContainer, modifier = Modifier.size(Spacing.illustration)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.extendedColors.onSuccessContainer, modifier = Modifier.size(Spacing.xxl))
            }
        }
        Text(
            stringResource(if (nothingDue) R.string.review_nothing_due_title else R.string.review_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            when {
                nothingDue && practiceAhead -> stringResource(R.string.review_deck_empty_body)
                nothingDue -> stringResource(R.string.review_nothing_due_body)
                else -> pluralStringResource(R.plurals.review_complete_body, reviewed, reviewed)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(Spacing.sm))
        if (nothingDue && !practiceAhead) {
            OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_practice_ahead)) }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.buttonHeight)) {
            Text(stringResource(R.string.review_back_to_decks))
        }
    }
}
