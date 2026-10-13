package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.HighlightTone
import com.example.ui.theme.LagoonDarkColors
import com.example.ui.theme.LagoonLightColors
import com.example.ui.theme.NotePalette
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.2 AA: text needs a contrast ratio of at least 4.5:1 against its background. */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, bg: Color, fg: Color) {
        val r = ratio(bg, fg)
        assertTrue("$name contrast is %.2f, needs 4.5".format(r), r >= 4.5)
    }

    private fun checkScheme(label: String, c: ColorScheme) {
        check("$label primary", c.primary, c.onPrimary)
        check("$label primaryContainer", c.primaryContainer, c.onPrimaryContainer)
        check("$label secondary", c.secondary, c.onSecondary)
        check("$label secondaryContainer", c.secondaryContainer, c.onSecondaryContainer)
        check("$label tertiary", c.tertiary, c.onTertiary)
        check("$label error", c.error, c.onError)
        check("$label errorContainer", c.errorContainer, c.onErrorContainer)
        check("$label surface", c.surface, c.onSurface)
        check("$label surfaceVariant text", c.surface, c.onSurfaceVariant)
        check("$label surfaceContainerHigh text", c.surfaceContainerHigh, c.onSurfaceVariant)
    }

    @Test fun lightSchemeMeetsAA() = checkScheme("light", LagoonLightColors)
    @Test fun darkSchemeMeetsAA() = checkScheme("dark", LagoonDarkColors)

    @Test
    fun cardHighlightsMeetAA() {
        for (dark in listOf(false, true)) for (tone in HighlightTone.entries) {
            val c = NotePalette.highlight(tone, dark)
            check("highlight $tone dark=$dark", c.container, c.content)
        }
    }
}
