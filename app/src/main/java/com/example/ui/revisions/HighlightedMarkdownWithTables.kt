package com.example.ui.revisions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.VerticalDivider
import com.example.data.repository.NoteHighlight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.ui.components.parseMarkdownWithHighlights
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.Spacing

// --- Segment model -----------------------------------------------------------------------
sealed class ContentSegment {
    data class MarkdownText(val content: String) : ContentSegment()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ContentSegment()
}

private val SEPARATOR_ROW_REGEX = Regex("""^\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?$""")

// A real cell's own line-wraps never happen to land with a "|" immediately before AND after a
// newline — that specific pattern only occurs at a genuine row boundary. This is what lets us
// tell "flattened multi-row pipe text" apart from a normal paragraph that happens to contain a
// stray "|" character.
private val ROW_BOUNDARY_REGEX = Regex("""\|\s*\n\s*\|""")
private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val INTERNAL_NEWLINE_REGEX = Regex("""\s*\n\s*""")

fun parseContentSegments(markdown: String): List<ContentSegment> {
    val lines = markdown.lines()
    val segments = mutableListOf<ContentSegment>()
    val textBuffer = StringBuilder()

    fun flushText() {
        val text = textBuffer.toString().trim()
        if (text.isNotBlank()) {
            // Fallback net: Gemini sometimes returns an entire table as one "text" block
            // containing raw "| a | b |" rows instead of using the "table" block type. Before
            // accepting this as plain prose, check whether it's actually a flattened table.
            val fallbackTable = tryParseFlatPipeTable(text)
            segments.add(fallbackTable ?: ContentSegment.MarkdownText(text))
        }
        textBuffer.clear()
    }

    fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim().replace(BR_REGEX, "\n") }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val looksLikeRow = line.contains("|") && line.trim().isNotEmpty()
        val nextLine = lines.getOrNull(i + 1)?.trim().orEmpty()

        if (looksLikeRow && SEPARATOR_ROW_REGEX.matches(nextLine)) {
            // Clean GFM table: header line, then a "---" separator, then data rows.
            flushText()
            val headers = splitRow(line)
            var j = i + 2
            val rows = mutableListOf<List<String>>()
            while (j < lines.size && lines[j].contains("|") && lines[j].trim().isNotEmpty()) {
                rows.add(splitRow(lines[j]))
                j++
            }
            segments.add(ContentSegment.Table(headers, rows))
            i = j
        } else {
            textBuffer.appendLine(line)
            i++
        }
    }
    flushText()
    return segments
}

// --- Fallback: a table flattened into one blob of "|"-separated text with no separator row --
// Row boundaries are found via "|\n|" (a pipe immediately followed by a newline and another
// pipe); everything between two boundaries is one row, split further into cells on "|". The
// first reconstructed row becomes the header. Bails out (returns null) unless every row has the
// same number of cells — better to show slightly-ugly plain text than a misaligned grid.
private fun tryParseFlatPipeTable(text: String): ContentSegment.Table? {
    if (!text.contains("|")) return null
    val boundaryCount = ROW_BOUNDARY_REGEX.findAll(text).count()
    if (boundaryCount < 2) return null // need at least 3 rows' worth of boundaries to be confident

    val rowChunks = text.trim().split(ROW_BOUNDARY_REGEX)
    val rows = rowChunks.map { chunk ->
        chunk.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { cell -> cell.trim().replace(INTERNAL_NEWLINE_REGEX, " ").trim() }
    }

    val columnCount = rows.firstOrNull()?.size ?: return null
    if (columnCount < 2) return null
    if (rows.any { it.size != columnCount }) return null // inconsistent columns — don't guess

    return ContentSegment.Table(headers = rows.first(), rows = rows.drop(1))
}

// --- Native table composable --------------------------------------------------------------

/**
 * Column widths as fractions of the table width, from how much text each column holds
 * (square root, so a long column gets more room without crushing the others; every column
 * keeps at least 80 % of an equal share).
 */
internal fun columnWeights(headers: List<String>, rows: List<List<String>>, columns: Int): List<Float> {
    if (columns <= 0) return emptyList()
    val raw = (0 until columns).map { c ->
        val cells = listOfNotNull(headers.getOrNull(c)) + rows.mapNotNull { it.getOrNull(c) }
        val avg = if (cells.isEmpty()) 0f else cells.sumOf { it.length }.toFloat() / cells.size
        kotlin.math.sqrt(avg.coerceAtLeast(4f))
    }
    val floor = 0.8f / columns
    val shares = raw.map { it / raw.sum() }
    // Narrow columns are raised to the floor; the others share what is left, in proportion.
    val wide = shares.filter { it >= floor }.sum()
    val left = 1f - floor * shares.count { it < floor }
    return shares.map { if (it < floor) floor else it / wide * left }
}

/**
 * Cards' tables: columns share the full width by content (several lines per cell, with "_ " / "- "
 * lines shown as bullets). When there are too many columns to fit, the table scrolls sideways.
 */
@Composable
fun NativeTableRenderer(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    highlights: List<NoteHighlight> = emptyList()
) {
    val isDark = LocalIsDarkTheme.current
    val headerColor = MaterialTheme.colorScheme.onSecondaryContainer
    val columns = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val weights = remember(headers, rows, columns) { columnWeights(headers, rows, columns) }
    val divider = MaterialTheme.colorScheme.outlineVariant
    OutlinedCard(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        BoxWithConstraints {
            val fits = maxWidth >= Spacing.tableMinColumnWidth * columns
            val widthOf = { c: Int -> if (fits) maxWidth * weights[c] else Spacing.tableColumnWidth * (weights[c] * columns).coerceIn(0.8f, 2.5f) }
            Column(if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())) {
                if (headers.any { it.isNotBlank() }) {
                    Row(Modifier.height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.secondaryContainer)) {
                        (0 until columns).forEach { c ->
                            if (c > 0) VerticalDivider(color = divider)
                            val header = headers.getOrNull(c).orEmpty()
                            Text(
                                text = remember(header, headerColor, isDark) { parseMarkdownWithHighlights(header, defaultTextColor = headerColor, isDark = isDark) },
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize,
                                color = headerColor,
                                modifier = Modifier.width(widthOf(c) - if (c > 0) Spacing.hairline else Spacing.none).padding(Spacing.xs)
                            )
                        }
                    }
                }
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        Modifier
                            .height(IntrinsicSize.Min)
                            .background(if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        (0 until columns).forEach { c ->
                            if (c > 0) VerticalDivider(color = divider)
                            RichTextBlockRenderer(
                                content = row.getOrNull(c).orEmpty(),
                                highlights = highlights,
                                fontSize = fontSize,
                                modifier = Modifier.width(widthOf(c) - if (c > 0) Spacing.hairline else Spacing.none).padding(Spacing.xs)
                            )
                        }
                    }
                    if (rowIndex != rows.lastIndex) HorizontalDivider(color = divider)
                }
            }
        }
    }
}

// --- Drop-in replacement for rendering a note's full content -------------------------------
@Composable
fun HighlightedMarkdownWithTables(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp
) {
    val segments = remember(markdown) { parseContentSegments(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        segments.forEach { segment ->
            when (segment) {
                is ContentSegment.MarkdownText -> if (segment.content.isNotBlank()) {
                    RichTextBlockRenderer(content = segment.content, fontSize = fontSize, modifier = Modifier.fillMaxWidth())
                }
                is ContentSegment.Table -> NativeTableRenderer(
                    headers = segment.headers,
                    rows = segment.rows,
                    fontSize = fontSize,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
