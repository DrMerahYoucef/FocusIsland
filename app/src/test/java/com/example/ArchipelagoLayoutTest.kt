package com.example

import com.example.ui.screen.community.ARCHIPELAGO_SPACING
import com.example.ui.screen.community.archipelagoLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ArchipelagoLayoutTest {
    @Test
    fun everyoneGetsAPlaceAndNoIslandsOverlap() {
        val spots = archipelagoLayout(40)
        assertEquals(41, spots.size)
        assertEquals(0f, spots[0].x, 0f)
        // Undo the vertical flattening: neighbours are then at least about one spacing apart.
        for (i in spots.indices) for (j in i + 1 until spots.size) {
            val d = hypot(spots[i].x - spots[j].x, (spots[i].y - spots[j].y) / 0.62f)
            assertTrue("$i-$j too close: $d", d >= ARCHIPELAGO_SPACING * 0.95f)
        }
    }
}
