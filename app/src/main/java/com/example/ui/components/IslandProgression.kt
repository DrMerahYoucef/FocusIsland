package com.example.ui.components

import androidx.annotation.StringRes
import com.example.R

data class IslandEra(
    val number: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val minUnits: Int
)

data class IslandMilestone(
    val id: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val requiredPoints: Int,
    val treesAtMilestone: Int
) {
    fun isUnlocked(points: Int): Boolean = points >= requiredPoints
}

/**
 * Points-driven civilization progression engine for Focus Island.
 *
 * Every milestone, tree, and civilization era scales strictly by total [points],
 * with a challenging long-term curve spanning up to [FULL_GROWTH_UNITS] (100,000 points).
 */
object IslandProgression {
    const val POINTS_PER_UNIT = 1
    const val MAX_TREES = 28
    const val FULL_GROWTH_UNITS = 50_000

    // Landmark unlock thresholds (strictly in total points, max 50,000)
    const val SPROUT_AT = 100
    const val PIER_AT = 400
    const val HUT_AT = 1_000
    const val BOAT_AT = 2_000
    const val LIGHTHOUSE_AT = 3_500
    const val BIRDS_AT = 5_500
    const val HIGHLAND_AT = 8_500
    const val BRIDGE_WATERFALL_AT = 12_500
    const val PALACE_AT = 18_000
    const val SKY_VESSEL_AT = 26_000
    const val SANCTUARY_AT = 36_000
    const val CELESTIAL_AT = 50_000

    val eras: List<IslandEra> = listOf(
        IslandEra(1, R.string.island_era_1_title, R.string.island_era_1_desc, 0),
        IslandEra(2, R.string.island_era_2_title, R.string.island_era_2_desc, PIER_AT),
        IslandEra(3, R.string.island_era_3_title, R.string.island_era_3_desc, HUT_AT),
        IslandEra(4, R.string.island_era_4_title, R.string.island_era_4_desc, BIRDS_AT),
        IslandEra(5, R.string.island_era_5_title, R.string.island_era_5_desc, BRIDGE_WATERFALL_AT),
        IslandEra(6, R.string.island_era_6_title, R.string.island_era_6_desc, PALACE_AT),
        IslandEra(7, R.string.island_era_7_title, R.string.island_era_7_desc, SANCTUARY_AT)
    )

    val milestones: List<IslandMilestone> = listOf(
        IslandMilestone(1, R.string.island_ms_1_title, R.string.island_ms_1_desc, SPROUT_AT, 1),
        IslandMilestone(2, R.string.island_ms_2_title, R.string.island_ms_2_desc, PIER_AT, 4),
        IslandMilestone(3, R.string.island_ms_3_title, R.string.island_ms_3_desc, HUT_AT, 8),
        IslandMilestone(4, R.string.island_ms_4_title, R.string.island_ms_4_desc, BOAT_AT, 11),
        IslandMilestone(5, R.string.island_ms_5_title, R.string.island_ms_5_desc, LIGHTHOUSE_AT, 14),
        IslandMilestone(6, R.string.island_ms_6_title, R.string.island_ms_6_desc, BIRDS_AT, 16),
        IslandMilestone(7, R.string.island_ms_7_title, R.string.island_ms_7_desc, HIGHLAND_AT, 18),
        IslandMilestone(8, R.string.island_ms_8_title, R.string.island_ms_8_desc, BRIDGE_WATERFALL_AT, 20),
        IslandMilestone(9, R.string.island_ms_9_title, R.string.island_ms_9_desc, PALACE_AT, 22),
        IslandMilestone(10, R.string.island_ms_10_title, R.string.island_ms_10_desc, SKY_VESSEL_AT, 24),
        IslandMilestone(11, R.string.island_ms_11_title, R.string.island_ms_11_desc, SANCTUARY_AT, 26),
        IslandMilestone(12, R.string.island_ms_12_title, R.string.island_ms_12_desc, CELESTIAL_AT, 28)
    )

    /**
     * Progression is driven strictly by [points]. The [sessions] parameter is ignored
     * and retained only for call-site signature compatibility.
     */
    fun effectiveUnits(sessions: Int = 0, points: Int = 0): Int =
        points.coerceAtLeast(0)

    /**
     * Smoothly maps [points] (0..100,000) to 0..[MAX_TREES] (28 trees),
     * matching each milestone's target tree count and interpolating smoothly between them.
     */
    fun treeCountForUnits(units: Int): Int {
        val pts = units.coerceAtLeast(0)
        if (pts < SPROUT_AT) return 0
        if (pts >= CELESTIAL_AT) return MAX_TREES
        val curIndex = milestones.indexOfLast { pts >= it.requiredPoints }
        if (curIndex < 0) return 0
        val cur = milestones[curIndex]
        val next = milestones.getOrNull(curIndex + 1) ?: return cur.treesAtMilestone
        val span = (next.requiredPoints - cur.requiredPoints).coerceAtLeast(1)
        val frac = (pts - cur.requiredPoints).toFloat() / span.toFloat()
        val interpolated = cur.treesAtMilestone + ((next.treesAtMilestone - cur.treesAtMilestone) * frac).toInt()
        return interpolated.coerceIn(0, MAX_TREES)
    }

    fun treeCount(sessions: Int = 0, points: Int = 0): Int =
        treeCountForUnits(effectiveUnits(sessions, points))

    fun eraForUnits(units: Int): IslandEra =
        eras.lastOrNull { units >= it.minUnits } ?: eras.first()

    fun eraFor(sessions: Int = 0, points: Int = 0): IslandEra =
        eraForUnits(effectiveUnits(sessions, points))

    fun unlockedCount(units: Int): Int =
        milestones.count { it.isUnlocked(units) }

    fun islandLevel(units: Int): Int =
        1 + unlockedCount(units.coerceAtLeast(0))

    fun nextMilestone(units: Int): IslandMilestone? =
        milestones.firstOrNull { !it.isUnlocked(units) }

    fun progressToNext(sessions: Int = 0, points: Int = 0): Float {
        val pts = effectiveUnits(sessions, points)
        val next = nextMilestone(pts) ?: return 1f
        val prevPoints = milestones.lastOrNull { it.isUnlocked(pts) }?.requiredPoints ?: 0
        val spanPoints = (next.requiredPoints - prevPoints).coerceAtLeast(1)
        return ((pts - prevPoints).toFloat() / spanPoints).coerceIn(0f, 1f)
    }
}
