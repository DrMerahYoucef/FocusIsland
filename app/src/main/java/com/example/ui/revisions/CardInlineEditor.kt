package com.example.ui.revisions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.db.entity.RevisionNoteEntity
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.HighlightTone
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.NotePalette
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.noteHeadingColor

/**
 * Full-screen card editor. What you see is what you get: headings, highlights, bold and italic
 * are drawn on clean text (no "#", "==" or "**"); on save the text is written back to the
 * card's markdown and blocks exactly as before.
 *
 * Owns its back behaviour: with unsaved changes, Close and system back ask before discarding.
 * Everything typed survives rotation and process death.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardInlineEditor(
    note: RevisionNoteEntity,
    onSave: (RevisionNoteEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val initialDoc = remember(note.id) { RichTextEditorEngine.parseFromNote(note) }
    val initialMarkdown = remember(note.id) {
        RichTextEditorEngine.serializeToMarkdown(
            cleanText = initialDoc.cleanText,
            lineHeadings = initialDoc.lineHeadings,
            highlights = initialDoc.highlights,
            bolds = initialDoc.bolds,
            italics = initialDoc.italics
        )
    }

    var title by rememberSaveable(note.id) { mutableStateOf(note.title) }
    var body by rememberSaveable(note.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialDoc.cleanText, TextRange(initialDoc.cleanText.length)))
    }
    var lineHeadings by rememberSaveable(note.id, stateSaver = IntListSaver) { mutableStateOf(initialDoc.lineHeadings) }
    var highlights by rememberSaveable(note.id, stateSaver = HighlightListSaver) { mutableStateOf(initialDoc.highlights) }
    var bolds by rememberSaveable(note.id, stateSaver = SpanListSaver) { mutableStateOf(initialDoc.bolds) }
    var italics by rememberSaveable(note.id, stateSaver = SpanListSaver) { mutableStateOf(initialDoc.italics) }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = edit, 1 = preview
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val markdown = remember(body.text, lineHeadings, highlights, bolds, italics) {
        RichTextEditorEngine.serializeToMarkdown(
            cleanText = body.text,
            lineHeadings = lineHeadings,
            highlights = highlights,
            bolds = bolds,
            italics = italics
        )
    }
    val dirty = title != note.title || markdown != initialMarkdown
    val requestClose = { if (dirty) confirmDiscard = true else onCancel() }
    val save = {
        onSave(CardMarkdownEditorUtils.serializeEditedContentToNote(originalNote = note, newTitle = title, editedMarkdown = markdown))
    }

    BackHandler(onBack = requestClose)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = requestClose, modifier = Modifier.testTag("card_editor_cancel_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.editor_close))
                    }
                },
                actions = {
                    Button(
                        onClick = save,
                        modifier = Modifier.padding(end = Spacing.xs).testTag("card_editor_save_button")
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
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                listOf(
                    Triple(R.string.editor_tab_edit, Icons.Rounded.Edit, 0),
                    Triple(R.string.editor_tab_preview, Icons.Rounded.Visibility, 1)
                ).forEach { (label, icon, index) ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        icon = { SegmentedButtonDefaults.Icon(active = tab == index) { Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) } }
                    ) { Text(stringResource(label)) }
                }
            }

            if (tab == 0) {
                FormattingToolbar(
                    text = body.text,
                    selection = body.selection,
                    lineHeadings = lineHeadings,
                    bolds = bolds,
                    italics = italics,
                    onHeading = { level ->
                        lineHeadings = RichTextEditorEngine.toggleHeadingOnLine(body.text, body.selection, level, lineHeadings)
                    },
                    onHighlight = { colorName ->
                        val (updated, newSelection) = RichTextEditorEngine.applyHighlightToRange(body.text, body.selection, colorName, highlights)
                        highlights = updated
                        body = body.copy(selection = newSelection)
                    },
                    onBold = { bolds = RichTextEditorEngine.toggleBoldOnRange(body.text, body.selection, bolds) },
                    onItalic = { italics = RichTextEditorEngine.toggleItalicOnRange(body.text, body.selection, italics) },
                    onBullet = { body = insertAtLineStart(body, "• ") },
                    onNumbered = { body = insertAtLineStart(body, "1. ") }
                )
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = Spacing.contentMaxWidth)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.editor_field_title)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().testTag("card_editor_title_input")
                        )
                        BodyField(
                            value = body,
                            isDark = isDark,
                            lineHeadings = lineHeadings,
                            highlights = highlights,
                            bolds = bolds,
                            italics = italics,
                            onValueChange = { newValue ->
                                if (newValue.text != body.text) {
                                    // Keep every span attached to the same characters while typing.
                                    val (spans, headings) = RichTextEditorEngine.adjustSpansOnTextChange(
                                        oldText = body.text,
                                        newText = newValue.text,
                                        currentHighlights = highlights,
                                        currentBolds = bolds,
                                        currentItalics = italics,
                                        currentLineHeadings = lineHeadings
                                    )
                                    highlights = spans.first
                                    bolds = spans.second
                                    italics = spans.third
                                    lineHeadings = headings
                                }
                                body = newValue
                            }
                        )
                        Text(
                            text = stringResource(
                                R.string.editor_stats,
                                pluralStringResource(R.plurals.editor_stats_chars, body.text.length, body.text.length),
                                pluralStringResource(R.plurals.editor_stats_lines, body.text.split('\n').size, body.text.split('\n').size),
                                pluralStringResource(R.plurals.editor_stats_highlights, highlights.size, highlights.size)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                PreviewPane(note = note, title = title, markdown = markdown, modifier = Modifier.weight(1f))
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            body = stringResource(R.string.editor_discard_body),
            confirmLabel = stringResource(R.string.study_discard),
            dismissLabel = stringResource(R.string.study_keep_editing),
            onConfirm = { confirmDiscard = false; onCancel() },
            onDismiss = { confirmDiscard = false },
            destructive = true
        )
    }
}

@Composable
private fun BodyField(
    value: TextFieldValue,
    isDark: Boolean,
    lineHeadings: List<Int>,
    highlights: List<RichHighlight>,
    bolds: List<RichStyleSpan>,
    italics: List<RichStyleSpan>,
    onValueChange: (TextFieldValue) -> Unit
) {
    val transformation = remember(isDark, lineHeadings, highlights, bolds, italics) {
        WordRichTextVisualTransformation(isDark, lineHeadings, highlights, bolds, italics)
    }
    val label = stringResource(R.string.editor_body_label)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Spacing.xxs / 4, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.editorMinHeight)
    ) {
        Box(Modifier.padding(Spacing.md)) {
            if (value.text.isEmpty()) {
                Text(
                    stringResource(R.string.editor_body_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                visualTransformation = transformation,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.editorMinHeight - Spacing.xl)
                    .semantics { contentDescription = label }
                    .testTag("card_editor_content_input")
            )
        }
    }
}

/** Live preview of the card exactly as the reader and the review session will show it. */
@Composable
private fun PreviewPane(note: RevisionNoteEntity, title: String, markdown: String, modifier: Modifier = Modifier) {
    val preview = remember(title, markdown) {
        CardMarkdownEditorUtils.serializeEditedContentToNote(originalNote = note, newTitle = title, editedMarkdown = markdown)
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        preview.title.ifBlank { stringResource(R.string.study_untitled) },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    HorizontalDivider()
                    val hasBlocks = preview.contentBlocksJson.isNotBlank() && preview.contentBlocksJson != "[]"
                    when {
                        hasBlocks -> NoteBlocksRenderer(blocksJson = preview.contentBlocksJson, modifier = Modifier.fillMaxWidth())
                        preview.contentMarkdown.isNotBlank() -> HighlightedMarkdownWithTables(markdown = preview.contentMarkdown, modifier = Modifier.fillMaxWidth())
                        else -> Text(
                            stringResource(R.string.editor_preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two rows: text styles (Title … Body) with bold/italic/lists, then highlight colours.
 * Every control is ≥ 48 dp and labelled for TalkBack; the active style is shown as selected.
 */
@Composable
fun FormattingToolbar(
    text: String,
    selection: TextRange,
    lineHeadings: List<Int>,
    bolds: List<RichStyleSpan>,
    italics: List<RichStyleSpan>,
    onHeading: (Int) -> Unit,
    onHighlight: (String?) -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val selStart = selection.min.coerceIn(0, text.length)
    val selEnd = selection.max.coerceIn(0, text.length)
    fun List<RichStyleSpan>.isActive() =
        if (selStart == selEnd) any { selStart in it.start..it.end } else any { it.start < selEnd && it.end > selStart }

    val lineIndex = text.substring(0, selStart).count { it == '\n' }
    val currentLine = text.split('\n').getOrNull(lineIndex)?.trimStart().orEmpty()
    val activeHeading = lineHeadings.getOrNull(lineIndex) ?: 0
    val bulletActive = currentLine.startsWith("•") || currentLine.startsWith("- ") || currentLine.startsWith("* ")
    val numberedActive = currentLine.matches(Regex("""^\d+[.)]\s+.*"""))

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                listOf(
                    1 to R.string.editor_style_title,
                    2 to R.string.editor_style_heading,
                    3 to R.string.editor_style_subheading,
                    4 to R.string.editor_style_section,
                    0 to R.string.editor_style_body
                ).forEach { (level, label) ->
                    FilterChip(
                        selected = activeHeading == level,
                        onClick = { onHeading(level) },
                        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (level > 0) {
                            { Box(Modifier.size(Spacing.noteAccentBar, Spacing.md).background(noteHeadingColor(level), CircleShape)) }
                        } else null
                    )
                }
                VerticalDivider(Modifier.height(Spacing.lg).padding(horizontal = Spacing.xxs))
                ToggleTool(Icons.Rounded.FormatBold, R.string.editor_bold, bolds.isActive(), onBold)
                ToggleTool(Icons.Rounded.FormatItalic, R.string.editor_italic, italics.isActive(), onItalic)
                ToggleTool(Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.editor_bullets, bulletActive, onBullet)
                ToggleTool(Icons.Rounded.FormatListNumbered, R.string.editor_numbered, numberedActive, onNumbered)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.editor_highlight),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.xs)
                )
                HighlightTone.pickable.forEach { tone ->
                    val colors = NotePalette.highlight(tone, isDark)
                    val label = stringResource(tone.labelRes())
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(Spacing.minTouchTarget)
                            .clickable(role = Role.Button, onClick = { onHighlight(tone.storedName) })
                            .semantics { contentDescription = label }
                    ) {
                        Box(
                            Modifier
                                .size(Spacing.xl - Spacing.xxs)
                                .background(colors.container, CircleShape)
                                .border(Spacing.xxs / 2, colors.swatch, CircleShape)
                        )
                    }
                }
                IconButton(onClick = { onHighlight(null) }) {
                    Icon(Icons.Rounded.FormatColorReset, contentDescription = stringResource(R.string.editor_highlight_clear))
                }
                Text(
                    stringResource(R.string.editor_highlight_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun ToggleTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, checked: Boolean, onClick: () -> Unit) {
    FilledTonalIconToggleButton(checked = checked, onCheckedChange = { onClick() }) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

private fun HighlightTone.labelRes(): Int = when (this) {
    HighlightTone.Amber -> R.string.editor_highlight_amber
    HighlightTone.Green -> R.string.editor_highlight_green
    HighlightTone.Blue -> R.string.editor_highlight_blue
    HighlightTone.Red -> R.string.editor_highlight_red
    HighlightTone.Purple -> R.string.editor_highlight_purple
    HighlightTone.Pink -> R.string.editor_highlight_pink
    HighlightTone.Cyan -> R.string.editor_highlight_cyan
}

/** Inserts [prefix] at the start of the line holding the cursor and moves the cursor with it. */
private fun insertAtLineStart(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1 || cursor == 0) 0 else it + 1 }
    return TextFieldValue(text.substring(0, lineStart) + prefix + text.substring(lineStart), TextRange(cursor + prefix.length))
}

// --- State savers (rotation / process death) ---------------------------------------------

private val IntListSaver = listSaver<List<Int>, Int>(save = { it }, restore = { it.toList() })

private val SpanListSaver = listSaver<List<RichStyleSpan>, Int>(
    save = { spans -> spans.flatMap { listOf(it.start, it.end) } },
    restore = { flat -> flat.chunked(2).map { RichStyleSpan(it[0], it[1]) } }
)

private val HighlightListSaver = listSaver<List<RichHighlight>, String>(
    save = { list -> list.flatMap { listOf(it.id, it.start.toString(), it.end.toString(), it.color) } },
    restore = { flat -> flat.chunked(4).map { RichHighlight(id = it[0], start = it[1].toInt(), end = it[2].toInt(), color = it[3]) } }
)

// --- Previews ------------------------------------------------------------------------------

@ThemePreviews
@Composable
private fun FormattingToolbarPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        FormattingToolbar(
            text = "Mitochondria\nThe powerhouse of the cell",
            selection = TextRange(3),
            lineHeadings = listOf(1, 0),
            bolds = emptyList(),
            italics = emptyList(),
            onHeading = {}, onHighlight = {}, onBold = {}, onItalic = {}, onBullet = {}, onNumbered = {}
        )
    }
}

@ThemePreviews
@Composable
private fun CardInlineEditorPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        CardInlineEditor(
            note = RevisionNoteEntity(
                deckId = "d",
                title = "Cell biology",
                contentBlocksJson = "",
                plainTextPreview = "Mitochondria make ATP.",
                contentMarkdown = "# Mitochondria\nThey make ==ATP== through **respiration**."
            ),
            onSave = {},
            onCancel = {}
        )
    }
}
