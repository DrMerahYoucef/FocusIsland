package com.example.ui.revisions

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.db.entity.RevisionNoteEntity
import com.example.ui.screen.revisions.CardContent
import com.example.ui.screen.revisions.ZoomableImageDialog
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private const val MIN_SCALE = 0.7f
private const val MAX_SCALE = 3.0f
private const val SCALE_STEP = 0.15f
private const val BASE_FONT_SP = 18f

/**
 * Distraction-free reading of one card. Text size: buttons in the app bar or pinch with two
 * fingers (tap the percentage to reset). Tap a photo to zoom it. Text cards can be edited here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenNoteReaderDialog(
    note: RevisionNoteEntity,
    deckName: String = "",
    onDismiss: () -> Unit,
    onSaveNote: ((RevisionNoteEntity) -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var current by remember(note) { mutableStateOf(note) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var textScale by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomFile by remember { mutableStateOf<File?>(null) }
    val msgCopied = stringResource(R.string.study_text_copied)
    val msgSaved = stringResource(R.string.study_card_saved)
    val canEdit = onSaveNote != null && current.mediaType == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        if (editing) {
            CardInlineEditor(
                note = current,
                onSave = { updated ->
                    current = updated
                    onSaveNote?.invoke(updated)
                    editing = false
                    scope.launch { snackbar.showSnackbar(msgSaved) }
                },
                onCancel = { editing = false },
                modifier = Modifier.fillMaxSize()
            )
            return@Dialog
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                current.title.ifBlank { stringResource(R.string.study_untitled) },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (deckName.isNotBlank()) {
                                Text(
                                    deckName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("fullscreen_exit_button")) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.exit_full_screen))
                        }
                    },
                    actions = {
                        TextSizeControl(
                            scale = textScale,
                            onScale = { textScale = it.coerceIn(MIN_SCALE, MAX_SCALE) }
                        )
                        if (canEdit) {
                            IconButton(onClick = { editing = true }, modifier = Modifier.testTag("fullscreen_edit_toggle_button")) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                            }
                        }
                        if (current.mediaType == null) {
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(current.plainTextPreview.ifBlank { current.contentMarkdown }))
                                scope.launch { snackbar.showSnackbar(msgCopied) }
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.study_copy_text))
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                CardContent(
                    note = current,
                    onZoomImage = { zoomFile = it },
                    fontSize = (BASE_FONT_SP * textScale).sp,
                    modifier = Modifier
                        .widthIn(max = Spacing.contentMaxWidth)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            // Two-finger pinch changes the text size; one finger still scrolls.
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) textScale = (textScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            }
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        .testTag("fullscreen_note_content_container")
                )
            }
        }
        zoomFile?.let { ZoomableImageDialog(it) { zoomFile = null } }
    }
}

/** − 100 % + ; tapping the percentage resets to 100 %. */
@Composable
private fun TextSizeControl(scale: Float, onScale: (Float) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { onScale(scale - SCALE_STEP) }, enabled = scale > MIN_SCALE, modifier = Modifier.testTag("zoom_out_button")) {
                Icon(Icons.Rounded.TextDecrease, contentDescription = stringResource(R.string.zoom_out))
            }
            TextButton(onClick = { onScale(1f) }, modifier = Modifier.testTag("zoom_percentage_badge")) {
                Text(
                    stringResource(R.string.reader_text_size, (scale * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            IconButton(onClick = { onScale(scale + SCALE_STEP) }, enabled = scale < MAX_SCALE, modifier = Modifier.testTag("zoom_in_button")) {
                Icon(Icons.Rounded.TextIncrease, contentDescription = stringResource(R.string.zoom_in))
            }
        }
    }
}
