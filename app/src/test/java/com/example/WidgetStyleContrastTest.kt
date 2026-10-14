package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * Widget text stays readable in every style, light and dark, whatever the wallpaper: the card
 * (88 % / 86 % opaque) is composited over pure white and pure black and every foreground must
 * pass on both (4.5:1 for text, 3:1 for large accents and graphics).
 */
class WidgetStyleContrastTest {

    private val res = File("src/main/res")

    private fun palette(file: File): Map<String, Int> =
        Regex("""<color name="wgt_(?:(?:arcade|blossom|explorer)_)?([a-z_]+)">#([0-9A-Fa-f]{6})</color>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }

    private fun channel(c: Int, shift: Int) = ((c shr shift) and 0xFF) / 255.0
    private fun lin(v: Double) = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Int) = 0.2126 * lin(channel(c, 16)) + 0.7152 * lin(channel(c, 8)) + 0.0722 * lin(channel(c, 0))
    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
    private fun over(fg: Int, alpha: Double, bg: Int): Int {
        fun mix(shift: Int) = (channel(fg, shift) * alpha + channel(bg, shift) * (1 - alpha)).times(255).toInt()
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    @Test
    fun everyWidgetPaletteIsReadable() {
        val files = listOf("values", "values-night").flatMap { folder ->
            File(res, folder).listFiles { f -> f.name.startsWith("widget_palette") }!!.map { folder to it }
        }
        assertTrue("no widget palettes found", files.size >= 8)
        val failures = files.flatMap { (folder, file) ->
            val p = palette(file)
            val alpha = if (folder == "values-night") 0.86 else 0.88
            val cards = listOf(over(p.getValue("surface_base"), alpha, 0xFFFFFF), over(p.getValue("surface_base"), alpha, 0x000000))
            mapOf("on_surface" to 4.5, "on_surface_variant" to 4.5, "accent" to 4.5, "accent_soft" to 3.0, "secondary" to 3.0, "critical" to 3.0)
                .flatMap { (role, min) ->
                    cards.mapNotNull { card ->
                        val ratio = contrast(p.getValue(role), card)
                        if (ratio < min) "$folder/${file.name} $role = %.2f".format(ratio) else null
                    }
                }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
