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

    @Test
    fun islandProgressionScalesStrictlyByPointsWithHarderCurve() {
        // High sessions with 0 points do not unlock anything (points-only scaling)
        val zeroPointUnits = com.example.ui.components.IslandProgression.effectiveUnits(sessions = 500, points = 0)
        assertEquals(0, zeroPointUnits)
        assertEquals(0, com.example.ui.components.IslandProgression.treeCount(sessions = 500, points = 0))
        assertEquals(0, com.example.ui.components.IslandProgression.unlockedCount(zeroPointUnits))

        // 4,500 points is mid-game (unlocks 5 landmarks and 15 trees, not max civilization)
        val midUnits = com.example.ui.components.IslandProgression.effectiveUnits(sessions = 5, points = 4_500)
        assertEquals(4_500, midUnits)
        assertEquals(5, com.example.ui.components.IslandProgression.unlockedCount(midUnits))
        assertEquals(15, com.example.ui.components.IslandProgression.treeCount(sessions = 5, points = 4_500))

        // Grand Palace unlocks at 18,000 points with 22 trees
        val palaceUnits = com.example.ui.components.IslandProgression.effectiveUnits(sessions = 9, points = 18_000)
        assertEquals(18_000, palaceUnits)
        assertEquals(22, com.example.ui.components.IslandProgression.treeCount(sessions = 9, points = 18_000))
        assertEquals(9, com.example.ui.components.IslandProgression.unlockedCount(palaceUnits))
        assertEquals(6, com.example.ui.components.IslandProgression.eraFor(sessions = 9, points = 18_000).number)

        // Full civilization with Grand Sky Fireworks requires 50,000 points to reach 28 trees and all 12 landmarks
        val maxUnits = com.example.ui.components.IslandProgression.effectiveUnits(sessions = 10, points = 50_000)
        assertEquals(50_000, maxUnits)
        assertEquals(28, com.example.ui.components.IslandProgression.treeCountForUnits(maxUnits))
        assertEquals(12, com.example.ui.components.IslandProgression.unlockedCount(maxUnits))
    }
}
