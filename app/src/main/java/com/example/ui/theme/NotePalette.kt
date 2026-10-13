package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Highlight colours a user can apply to card text. The stored name (e.g. "green") is part of
 * the card data (`==color:green==term==` in markdown, `NoteHighlight.color` in blocks), so the
 * names and aliases below must never change; only the colours they render with may.
 */
enum class HighlightTone(val storedName: String) {
    Amber("amber"), Green("green"), Blue("blue"), Red("red"), Purple("purple"), Pink("pink"), Cyan("cyan");

    companion object {
        /** Tones offered in the editor, in toolbar order. Pink and cyan are only rendered (Gemini can produce them). */
        val pickable = listOf(Amber, Green, Blue, Red, Purple)

        /** Accepts every name the app or Gemini has ever stored, in English and French. Unknown → Amber. */
        fun fromName(name: String?): HighlightTone = when (name?.trim()?.lowercase()) {
            "green", "emerald", "vert" -> Green
            "blue", "bleu", "sky" -> Blue
            "cyan" -> Cyan
            "red", "rouge" -> Red
            "pink", "rose" -> Pink
            "purple", "violet", "lavender" -> Purple
            else -> Amber
        }
    }
}

@Immutable
data class HighlightColors(val container: Color, val content: Color, val swatch: Color)

/**
 * Colours used to render card content (headings, highlights) in the reader, the review session
 * and the editor. Headings follow the Lagoon roles (teal, coral, sand, slate) so cards feel part
 * of the app; highlights stay distinct hues because the user picks them by colour.
 * Every content/container pair meets WCAG AA (≥ 4.5:1) in both themes.
 * Plain functions (not @Composable) because the editor's VisualTransformation needs them.
 */
object NotePalette {

    /** Heading text colour for levels 1–6 (level ≥ 5 uses the body variant colour). */
    fun heading(level: Int, isDark: Boolean): Color = when (level) {
        1 -> if (isDark) Color(0xFF8BD5CD) else Color(0xFF1F6A64) // primary
        2 -> if (isDark) Color(0xFFFFB4A7) else Color(0xFF9C4234) // tertiary
        3 -> if (isDark) Color(0xFFF6BE48) else Color(0xFF7A5900) // secondary
        4 -> if (isDark) Color(0xFFA9C8F2) else Color(0xFF3B5F8A) // slate blue
        else -> if (isDark) Color(0xFFBEC9C6) else Color(0xFF3F4947) // onSurfaceVariant
    }

    /** Soft band behind level 1–3 headings. */
    fun headingContainer(level: Int, isDark: Boolean): Color =
        heading(level, isDark).copy(alpha = if (isDark) 0.14f else 0.08f)

    fun highlight(tone: HighlightTone, isDark: Boolean): HighlightColors = if (isDark) {
        when (tone) {
            HighlightTone.Amber -> HighlightColors(Color(0xFF5C4300), Color(0xFFFFE7B0), Color(0xFFF6BE48))
            HighlightTone.Green -> HighlightColors(Color(0xFF1E5236), Color(0xFFCFF5DD), Color(0xFF6FD39A))
            HighlightTone.Blue -> HighlightColors(Color(0xFF23446E), Color(0xFFD8E7FF), Color(0xFF8DB8F5))
            HighlightTone.Red -> HighlightColors(Color(0xFF6B2A21), Color(0xFFFFDAD4), Color(0xFFFF8A7A))
            HighlightTone.Purple -> HighlightColors(Color(0xFF45306E), Color(0xFFEADDFF), Color(0xFFC3A6FF))
            HighlightTone.Pink -> HighlightColors(Color(0xFF6A2550), Color(0xFFFFD8EC), Color(0xFFFF8DC7))
            HighlightTone.Cyan -> HighlightColors(Color(0xFF19505B), Color(0xFFCDF3FA), Color(0xFF6FD6E8))
        }
    } else {
        when (tone) {
            HighlightTone.Amber -> HighlightColors(Color(0xFFFFE08A), Color(0xFF3D2E00), Color(0xFFE0A800))
            HighlightTone.Green -> HighlightColors(Color(0xFFC2F0D3), Color(0xFF0B3D22), Color(0xFF2E9E5B))
            HighlightTone.Blue -> HighlightColors(Color(0xFFCFE2FF), Color(0xFF0E2F5C), Color(0xFF3B7BD9))
            HighlightTone.Red -> HighlightColors(Color(0xFFFFD6D1), Color(0xFF5C1209), Color(0xFFD9483B))
            HighlightTone.Purple -> HighlightColors(Color(0xFFE6DAFF), Color(0xFF2E1462), Color(0xFF7C5CD6))
            HighlightTone.Pink -> HighlightColors(Color(0xFFFFD8EC), Color(0xFF5A0F38), Color(0xFFD6458F))
            HighlightTone.Cyan -> HighlightColors(Color(0xFFC6EEF6), Color(0xFF053A45), Color(0xFF1C95AB))
        }
    }

    fun highlight(name: String?, isDark: Boolean): HighlightColors = highlight(HighlightTone.fromName(name), isDark)
}

/** Convenience for composables: the heading colour in the current theme. */
@Composable
fun noteHeadingColor(level: Int): Color = NotePalette.heading(level, LocalIsDarkTheme.current)

/** Convenience for composables: the heading band colour in the current theme. */
@Composable
fun noteHeadingContainer(level: Int): Color = NotePalette.headingContainer(level, LocalIsDarkTheme.current)
