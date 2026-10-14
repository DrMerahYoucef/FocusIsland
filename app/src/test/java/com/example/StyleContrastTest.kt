package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.AppStyle
import com.example.ui.theme.FocusStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every style, light and dark, keeps text readable (WCAG 2.2 AA: 4.5:1 for normal text). */
class StyleContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun pairs(c: ColorScheme) = mapOf(
        "onPrimary/primary" to (c.onPrimary to c.primary),
        "onSurface/surface" to (c.onSurface to c.surface),
        "onSurfaceVariant/surface" to (c.onSurfaceVariant to c.surface),
        "onSurface/surfaceContainerHighest" to (c.onSurface to c.surfaceContainerHighest),
        "onSurfaceVariant/surfaceContainerHigh" to (c.onSurfaceVariant to c.surfaceContainerHigh),
        "primary/surface" to (c.primary to c.surface),
        "secondary/surface" to (c.secondary to c.surface),
        "tertiary/surface" to (c.tertiary to c.surface),
        "onPrimaryContainer/primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
        "onTertiaryContainer/tertiaryContainer" to (c.onTertiaryContainer to c.tertiaryContainer),
        "onSecondary/secondary" to (c.onSecondary to c.secondary),
        "onTertiary/tertiary" to (c.onTertiary to c.tertiary),
        "inverseOnSurface/inverseSurface" to (c.inverseOnSurface to c.inverseSurface)
    )

    @Test
    fun everyStyleMeetsAA() {
        val failures = AppStyle.entries.flatMap { style ->
            val s = FocusStyles.of(style)
            listOf("light" to s.light, "dark" to s.dark).flatMap { (mode, scheme) ->
                pairs(scheme).mapNotNull { (name, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    if (ratio < 4.5f) "$style $mode $name = %.2f".format(ratio) else null
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun styleKeysRoundTrip() {
        AppStyle.entries.forEach { assertEquals(it, AppStyle.from(it.key)) }
        assertEquals(AppStyle.LAGOON, AppStyle.from(null))
        assertEquals(AppStyle.LAGOON, AppStyle.from("unknown"))
    }
}
