package com.example

import com.example.ui.components.parseMarkdownWithHighlights
import com.example.ui.revisions.FormattedNoteLine
import com.example.ui.revisions.columnWeights
import com.example.ui.revisions.parseNoteLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFormattingTest {
    @Test
    fun underscoreDashesAreBulletsNotItalic() {
        val lines = parseNoteLines("_ L'ONTF est la mortification\n_ Peut être primitive\n_A) Les étiologies\nTexte _important_ ici")
        assertEquals(FormattedNoteLine.BulletList("L'ONTF est la mortification"), lines[0])
        assertEquals(FormattedNoteLine.BulletList("Peut être primitive"), lines[1])
        assertEquals(FormattedNoteLine.BulletList("A) Les étiologies"), lines[2])
        assertTrue(lines[3] is FormattedNoteLine.Paragraph)
        assertEquals(FormattedNoteLine.BulletList("01) Fréquence: – Affection Grave"), parseNoteLines("_ 01) Fréquence: _ Affection Grave")[0])
    }

    @Test
    fun italicNeverSpansLines() {
        val text = parseMarkdownWithHighlights("_ une ligne\n_ une autre", defaultTextColor = androidx.compose.ui.graphics.Color.Unspecified, isDark = false)
        assertTrue(text.spanStyles.none { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
    }

    @Test
    fun longColumnGetsMoreRoomButLabelsStayReadable() {
        val w = columnWeights(listOf("Etiologies", "Détails"), listOf(listOf("Causes", "x".repeat(600)), listOf("Pathogénie", "y".repeat(400))), 2)
        assertEquals(1f, w.sum(), 0.001f)
        assertTrue(w[1] > w[0])
        assertTrue(w[0] >= 0.4f - 0.001f)
    }
}
