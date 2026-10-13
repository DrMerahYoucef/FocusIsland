package com.example.ui.revisions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.data.repository.NoteBlock
import com.example.data.repository.NoteBlocksSerializer
import com.example.data.repository.NoteHighlight
import com.example.ui.components.parseMarkdownWithHighlights
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.noteHeadingColor
import com.example.ui.theme.noteHeadingContainer

// --- Structured Note Line Classification ---------------------------------------------------
sealed class FormattedNoteLine {
    data class Heading(val level: Int, val text: String) : FormattedNoteLine()
    data class BulletList(val text: String, val indentLevel: Int = 0) : FormattedNoteLine()
    data class NumberedList(val numberStr: String, val text: String) : FormattedNoteLine()
    data class Blockquote(val text: String) : FormattedNoteLine()
    object Divider : FormattedNoteLine()
    data class Paragraph(val text: String) : FormattedNoteLine()
}

/**
 * Parses a markdown text block into structured lines and paragraphs.
 * Extracts headers (#, ##, ###, ####, #####, ######), bullet lists (- , * , • ),
 * numbered lists (1. , 2. ), blockquotes (> ), horizontal dividers, and paragraphs.
 */
fun parseNoteLines(content: String): List<FormattedNoteLine> {
    if (content.isBlank()) return emptyList()

    // A lone "_" between words is a dash in course notes ("Fréquence: _ Affection grave").
    val lines = content.replace("\r\n", "\n").replace("\r", "\n").replace(LONE_UNDERSCORE, "–").lines()
    val result = mutableListOf<FormattedNoteLine>()
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        val p = paragraphBuffer.toString().trim()
        if (p.isNotEmpty()) {
            result.add(FormattedNoteLine.Paragraph(p))
        }
        paragraphBuffer.clear()
    }

    // Matches "# Title", "## Subtitle", "### Topic", "#Title", etc.
    val headingRegex = Regex("""^(\s*#{1,6})\s*(.*)$""")
    val bulletRegex = Regex("""^(\s*)(?:[-*•+–]|\(?[•*-]\)?)\s+(.*)$""")
    val numberedRegex = Regex("""^(\s*)(\d+[\.\)]|\(\d+\)|\d+-\s)\s*(.*)$""")
    val blockquoteRegex = Regex("""^>\s*(.*)$""")
    val hrRegex = Regex("""^(\s*[-*_]\s*){3,}$""")

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            flushParagraph()
            continue
        }

        // 1. Check for headings (#, ##, ###, ...)
        val headingMatch = headingRegex.matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            val hashes = headingMatch.groupValues[1].trim()
            val level = hashes.length.coerceIn(1, 6)
            var headingText = headingMatch.groupValues[2].trim()
            // Clean any trailing hashes or markdown wrap
            headingText = headingText.removeSuffix("#").trim()
            if (headingText.isNotEmpty()) {
                result.add(FormattedNoteLine.Heading(level, headingText))
                continue
            }
        }

        // 2. Check for Horizontal Rule
        if (hrRegex.matches(trimmed)) {
            flushParagraph()
            result.add(FormattedNoteLine.Divider)
            continue
        }

        // 3. Check for Blockquote
        val blockquoteMatch = blockquoteRegex.matchEntire(trimmed)
        if (blockquoteMatch != null) {
            flushParagraph()
            result.add(FormattedNoteLine.Blockquote(blockquoteMatch.groupValues[1].trim()))
            continue
        }

        // 4a. "_ text" (or "_text" with no closing underscore) is how course notes write a dash.
        if (trimmed.startsWith("_") && !trimmed.startsWith("__") && trimmed.length > 1 &&
            (trimmed[1].isWhitespace() || trimmed.indexOf('_', 1) < 0)
        ) {
            flushParagraph()
            val indent = (line.length - line.trimStart().length) / 2
            result.add(FormattedNoteLine.BulletList(trimmed.drop(1).trim(), indent))
            continue
        }

        // 4. Check for Bullet list
        val bulletMatch = bulletRegex.matchEntire(line)
        if (bulletMatch != null) {
            flushParagraph()
            val indent = bulletMatch.groupValues[1].length / 2
            val itemText = bulletMatch.groupValues[2].trim()
            result.add(FormattedNoteLine.BulletList(itemText, indent))
            continue
        }

        // 5. Check for Numbered list
        val numberedMatch = numberedRegex.matchEntire(line)
        if (numberedMatch != null) {
            flushParagraph()
            val numStr = numberedMatch.groupValues[2].trim()
            val itemText = numberedMatch.groupValues[3].trim()
            result.add(FormattedNoteLine.NumberedList(numStr, itemText))
            continue
        }

        // 6. Regular prose line: accumulate into current paragraph buffer
        if (paragraphBuffer.isNotEmpty()) {
            paragraphBuffer.append("\n")
        }
        paragraphBuffer.append(line)
    }
    flushParagraph()

    return result
}

private val LONE_UNDERSCORE = Regex("""(?<=\S[ \t])_(?=[ \t])""")

// --- Authoritative structured note renderer ----------------------------------------------

/** Renders a card's structured blocks (text with headings/lists/highlights, and tables). */
@Composable
fun NoteBlocksRenderer(
    blocksJson: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp
) {
    val blocks = remember(blocksJson) { NoteBlocksSerializer.fromJson(blocksJson) }
    // Highlights are stored with the text blocks; tables use the same terms.
    val allHighlights = remember(blocks) { blocks.filterIsInstance<NoteBlock.TextBlock>().flatMap { it.highlights }.distinct() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.TextBlock -> if (block.content.isNotBlank()) {
                    RichTextBlockRenderer(
                        content = block.content,
                        highlights = block.highlights,
                        fontSize = fontSize,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is NoteBlock.TableBlock -> NativeTableRenderer(
                    headers = block.headers,
                    rows = block.rows,
                    fontSize = fontSize,
                    highlights = allHighlights,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * A text block: headings (no raw '#'), bullet and numbered lists, quotes, dividers and
 * paragraphs, with highlights. All sizes scale with [fontSize] so the reader's zoom works.
 */
@Composable
fun RichTextBlockRenderer(
    content: String,
    highlights: List<NoteHighlight> = emptyList(),
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp
) {
    val isDark = LocalIsDarkTheme.current
    val parsedLines = remember(content) { parseNoteLines(content) }
    val textColor = MaterialTheme.colorScheme.onSurface

    @Composable
    fun annotate(text: String, color: Color): AnnotatedString = remember(text, highlights, isDark, color) {
        parseMarkdownWithHighlights(input = text, defaultTextColor = color, highlights = highlights, isDark = isDark)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        parsedLines.forEachIndexed { index, line ->
            when (line) {
                is FormattedNoteLine.Heading -> {
                    val text = annotate(line.text, Color.Unspecified)
                    when (line.level) {
                        1 -> NoteHeading1(text, fontSize = (fontSize.value * 1.30f).sp, modifier = Modifier.fillMaxWidth())
                        2 -> NoteHeading2(text, fontSize = (fontSize.value * 1.16f).sp, modifier = Modifier.fillMaxWidth())
                        3 -> NoteHeading3(text, fontSize = (fontSize.value * 1.06f).sp, modifier = Modifier.fillMaxWidth())
                        4 -> NoteHeading4(text, fontSize = fontSize, modifier = Modifier.fillMaxWidth())
                        else -> NoteHeadingSmall(text, level = line.level, fontSize = (fontSize.value * 0.95f).sp, modifier = Modifier.fillMaxWidth())
                    }
                }
                is FormattedNoteLine.BulletList -> NoteBulletItem(
                    text = annotate(line.text, textColor),
                    indentLevel = line.indentLevel,
                    fontSize = fontSize,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
                is FormattedNoteLine.NumberedList -> NoteNumberedItem(
                    numberStr = line.numberStr,
                    text = annotate(line.text, textColor),
                    fontSize = fontSize,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
                is FormattedNoteLine.Blockquote -> NoteBlockquote(
                    text = annotate(line.text, textColor),
                    fontSize = fontSize,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
                is FormattedNoteLine.Divider -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = Spacing.xs)
                )
                is FormattedNoteLine.Paragraph -> Text(
                    text = annotate(line.text, textColor),
                    fontSize = fontSize,
                    color = textColor,
                    lineHeight = 1.5.em,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xxs)
                        .testTag("note_paragraph_$index")
                )
            }
        }
    }
}

// --- Headings -----------------------------------------------------------------------------

/** Level 1: teal band with an accent bar. Announced as a heading by TalkBack. */
@Composable
fun NoteHeading1(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 21.sp,
    color: Color = noteHeadingColor(1),
    bgColor: Color = noteHeadingContainer(1)
) = BandHeading(text, fontSize, FontWeight.ExtraBold, color, bgColor, Spacing.md, "heading_level_1", modifier)

/** Level 2: coral band with an accent bar. */
@Composable
fun NoteHeading2(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.5.sp,
    color: Color = noteHeadingColor(2),
    bgColor: Color = noteHeadingContainer(2)
) = BandHeading(text, fontSize, FontWeight.Bold, color, bgColor, Spacing.sm, "heading_level_2", modifier)

/** Level 3: sand text with a dot. */
@Composable
fun NoteHeading3(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    color: Color = noteHeadingColor(3),
    bgColor: Color = noteHeadingContainer(3)
) = DotHeading(text, fontSize, FontWeight.Bold, color, bgColor, Spacing.noteBullet + Spacing.xxs / 2, "heading_level_3", modifier)

/** Level 4: slate text with a small dot, no band. */
@Composable
fun NoteHeading4(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = noteHeadingColor(4)
) = DotHeading(text, fontSize, FontWeight.SemiBold, color, Color.Transparent, Spacing.noteBullet, "heading_level_4", modifier)

/** Levels 5–6: plain semibold line. */
@Composable
fun NoteHeadingSmall(
    text: AnnotatedString,
    level: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    color: Color = noteHeadingColor(level)
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = color,
        lineHeight = 1.35.em,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, bottom = Spacing.xxs / 2)
            .semantics { heading() }
            .testTag("heading_level_$level")
    )
}

@Composable
private fun BandHeading(
    text: AnnotatedString,
    fontSize: TextUnit,
    weight: FontWeight,
    color: Color,
    bgColor: Color,
    topSpace: androidx.compose.ui.unit.Dp,
    tag: String,
    modifier: Modifier
) {
    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topSpace, bottom = Spacing.xxs)
            .semantics { heading() }
            .testTag(tag)
    ) {
        // IntrinsicSize lets the accent bar follow headings that wrap onto several lines.
        Row(Modifier.height(IntrinsicSize.Min).padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            Box(
                Modifier
                    .width(Spacing.noteAccentBar)
                    .fillMaxHeight()
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(text, fontSize = fontSize, fontWeight = weight, color = color, lineHeight = 1.3.em, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DotHeading(
    text: AnnotatedString,
    fontSize: TextUnit,
    weight: FontWeight,
    color: Color,
    bgColor: Color,
    dot: androidx.compose.ui.unit.Dp,
    tag: String,
    modifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xxs / 2)
            .background(bgColor, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            .semantics { heading() }
            .testTag(tag)
    ) {
        Box(Modifier.size(dot).background(color, CircleShape))
        Spacer(Modifier.width(Spacing.xs))
        Text(text, fontSize = fontSize, fontWeight = weight, color = color, lineHeight = 1.35.em, modifier = Modifier.weight(1f))
    }
}

// --- Lists and quotes ---------------------------------------------------------------------

@Composable
fun NoteBulletItem(
    text: AnnotatedString,
    indentLevel: Int = 0,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    bulletColor: Color = noteHeadingColor(1)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm * indentLevel + Spacing.xxs / 2, top = Spacing.xxs / 2, bottom = Spacing.xxs / 2)
            .testTag("note_bullet_item"),
        verticalAlignment = Alignment.Top
    ) {
        // Centre the dot on the first line, whatever the zoom level.
        val lineHeightDp = with(LocalDensity.current) { (fontSize.value * 1.45f).sp.toDp() }
        Box(Modifier.height(lineHeightDp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(Spacing.noteBullet).background(bulletColor, CircleShape))
        }
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, fontSize = fontSize, color = textColor, lineHeight = 1.45.em, modifier = Modifier.weight(1f))
    }
}

@Composable
fun NoteNumberedItem(
    numberStr: String,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    numberColor: Color = noteHeadingColor(1)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs / 2, horizontal = Spacing.xxs / 2)
            .testTag("note_numbered_item"),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = numberStr,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = numberColor,
            lineHeight = 1.45.em,
            modifier = Modifier.padding(end = Spacing.xs).widthIn(min = Spacing.md + Spacing.xxs)
        )
        Text(text = text, fontSize = fontSize, color = textColor, lineHeight = 1.45.em, modifier = Modifier.weight(1f))
    }
}

@Composable
fun NoteBlockquote(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = noteHeadingColor(3)
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxs).testTag("note_blockquote")
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            Box(Modifier.width(Spacing.noteAccentBar).fillMaxHeight().background(accentColor, CircleShape))
            Spacer(Modifier.width(Spacing.sm))
            Text(text = text, fontSize = fontSize, fontStyle = FontStyle.Italic, color = textColor, lineHeight = 1.45.em, modifier = Modifier.weight(1f))
        }
    }
}

// --- Previews -----------------------------------------------------------------------------

@ThemePreviews
@Composable
private fun RichTextBlockPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface {
            RichTextBlockRenderer(
                content = """
                    # Photosynthesis
                    ## Light reactions
                    Light energy splits ==water== and makes **ATP** and NADPH.
                    ### Where
                    - In the ==color:green==thylakoid membranes==
                    - Needs chlorophyll a and b
                    1. Photon absorbed
                    2. Electron transport chain
                    > Oxygen is a by-product.
                """.trimIndent(),
                modifier = Modifier.padding(Spacing.md)
            )
        }
    }
}
