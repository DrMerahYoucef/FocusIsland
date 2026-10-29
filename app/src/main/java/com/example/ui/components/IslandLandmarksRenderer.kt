package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.IslandColors
import com.example.ui.theme.IslandKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural vector renderer for Focus Island's multi-era terrain expansions,
 * wildlife flocks, sea/sky vessels, and civilization wonders across all 4 styles.
 */
internal object IslandLandmarksRenderer {

    /**
     * Draws sky-level wonders behind the island:
     * - Celebratory Grand Sky Fireworks at [IslandProgression.CELESTIAL_AT] (50,000+)
     * - Soaring bird flocks at [IslandProgression.BIRDS_AT] (5,500+)
     * - Floating sky vessel at [IslandProgression.SKY_VESSEL_AT] (26,000+)
     */
    fun DrawScope.drawSkyWonders(
        center: Offset,
        r: Float,
        units: Int,
        colors: IslandColors,
        fireworkPhase: Float = -1f
    ) {
        if (units >= IslandProgression.CELESTIAL_AT) {
            drawSkyFireworksBackdrop(center, r, colors, fireworkPhase)
        }
        if (units >= IslandProgression.BIRDS_AT) {
            drawBirdFlock(center, r, units, colors)
        }
        if (units >= IslandProgression.SKY_VESSEL_AT) {
            drawSkyVessel(Offset(center.x - r * 0.72f, center.y - r * 1.05f), r, colors)
        }
    }

    /**
     * Draws the foreground celebratory firework bursts, curved willow plumes, and twinkling
     * 4-point star sparkles above the island canopy at [IslandProgression.CELESTIAL_AT] (50,000+ pts).
     */
    fun DrawScope.drawSkyFireworksForeground(
        center: Offset,
        r: Float,
        units: Int,
        colors: IslandColors,
        fireworkPhase: Float = -1f
    ) {
        if (units >= IslandProgression.CELESTIAL_AT) {
            drawSkyFireworksBursts(center, r, colors, fireworkPhase)
        }
    }

    /**
     * Draws the upper highland mountain peak behind the main hill (unlocks at 50 units)
     * and the cascading highland waterfall (unlocks at 75 units).
     */
    fun DrawScope.drawHighlandAndWaterfall(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units < IslandProgression.HIGHLAND_AT) return
        val cx = center.x
        val cy = center.y

        // Upper mountain ridge rising behind the main hill
        val peakHeight = if (units >= IslandProgression.PALACE_AT) r * 0.64f else r * 0.58f
        val ridge = Path().apply {
            moveTo(cx - r * 0.62f, cy - r * 0.22f)
            cubicTo(
                cx - r * 0.42f, cy - peakHeight * 0.92f,
                cx - r * 0.14f, cy - peakHeight,
                cx + r * 0.04f, cy - peakHeight
            )
            cubicTo(
                cx + r * 0.24f, cy - peakHeight,
                cx + r * 0.46f, cy - peakHeight * 0.82f,
                cx + r * 0.58f, cy - r * 0.20f
            )
            close()
        }
        drawPath(ridge, colors.grass.copy(alpha = 0.92f))
        // Sunlit highland plateau highlight
        val highlight = Path().apply {
            moveTo(cx - r * 0.35f, cy - peakHeight * 0.68f)
            quadraticTo(cx + r * 0.02f, cy - peakHeight * 0.98f, cx + r * 0.32f, cy - peakHeight * 0.66f)
            quadraticTo(cx + r * 0.04f, cy - peakHeight * 0.52f, cx - r * 0.35f, cy - peakHeight * 0.68f)
            close()
        }
        drawPath(highlight, colors.grassLight.copy(alpha = 0.65f))

        when {
            colors.ink.alpha > 0f -> drawPath(ridge, colors.ink, style = Stroke(r * 0.02f))
            colors.kind == IslandKind.NEON -> {
                drawPath(ridge, colors.accent.copy(alpha = 0.28f), style = Stroke(r * 0.035f))
                drawPath(ridge, colors.accent2, style = Stroke(r * 0.012f))
            }
        }
    }

    /**
     * Draws the cascading waterfall down the eastern highland slope into the lagoon (75+ units).
     */
    fun DrawScope.drawWaterfall(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units < IslandProgression.BRIDGE_WATERFALL_AT) return
        val cx = center.x + r * 0.24f
        val topY = center.y - r * 0.48f
        val botY = center.y - r * 0.04f

        val waterColor = when (colors.kind) {
            IslandKind.NEON -> colors.accent
            IslandKind.SAKURA -> colors.shallow.copy(alpha = 0.9f)
            else -> Color(0xFF5CE1E6)
        }
        val fall = Path().apply {
            moveTo(cx - r * 0.035f, topY)
            quadraticTo(cx - r * 0.01f, (topY + botY) * 0.5f, cx - r * 0.055f, botY)
            lineTo(cx + r * 0.065f, botY)
            quadraticTo(cx + r * 0.035f, (topY + botY) * 0.5f, cx + r * 0.035f, topY)
            close()
        }
        drawPath(
            fall,
            Brush.verticalGradient(
                listOf(colors.foam, waterColor, colors.shallow),
                startY = topY,
                endY = botY
            )
        )
        // Sparkling vertical foam streaks inside the cascade
        repeat(3) { i ->
            val sx = cx - r * 0.02f + i * r * 0.022f
            drawLine(
                colors.foam.copy(alpha = 0.85f),
                Offset(sx, topY + r * 0.04f * (i + 1)),
                Offset(sx + r * 0.008f, botY - r * 0.03f),
                strokeWidth = r * 0.01f,
                cap = StrokeCap.Round
            )
        }
        // Mist & splash pool at the bottom of the waterfall
        drawOval(colors.foam.copy(alpha = 0.85f), Offset(cx - r * 0.09f, botY - r * 0.025f), Size(r * 0.19f, r * 0.055f))
        drawCircle(colors.foam, r * 0.022f, Offset(cx - r * 0.04f, botY - r * 0.01f))
        drawCircle(colors.foam, r * 0.026f, Offset(cx + r * 0.03f, botY - r * 0.012f))
    }

    /**
     * Draws the connected eastern side-islet terrace and arched bridge (75+ units).
     */
    fun DrawScope.drawSideTerraceAndBridge(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units < IslandProgression.BRIDGE_WATERFALL_AT) return
        val isletCenter = Offset(center.x + r * 1.18f, center.y + r * 0.03f)
        val ir = r * 0.34f

        // Shallows & beach of the side terrace islet
        drawOval(colors.shallow, Offset(isletCenter.x - ir * 1.3f, isletCenter.y - ir * 0.18f), Size(ir * 2.6f, ir * 0.44f))
        val sand = Path().apply {
            moveTo(isletCenter.x - ir, isletCenter.y)
            cubicTo(
                isletCenter.x - ir * 0.6f, isletCenter.y - ir * 0.28f,
                isletCenter.x + ir * 0.6f, isletCenter.y - ir * 0.28f,
                isletCenter.x + ir, isletCenter.y
            )
            quadraticTo(isletCenter.x, isletCenter.y + ir * 0.16f, isletCenter.x - ir, isletCenter.y)
            close()
        }
        drawPath(sand, colors.sand)
        val hill = Path().apply {
            moveTo(isletCenter.x - ir * 0.82f, isletCenter.y - ir * 0.08f)
            cubicTo(
                isletCenter.x - ir * 0.45f, isletCenter.y - ir * 0.48f,
                isletCenter.x + ir * 0.45f, isletCenter.y - ir * 0.48f,
                isletCenter.x + ir * 0.82f, isletCenter.y - ir * 0.08f
            )
            close()
        }
        drawPath(hill, colors.grass)
        if (colors.ink.alpha > 0f) {
            drawPath(sand, colors.ink, style = Stroke(r * 0.016f))
            drawPath(hill, colors.ink, style = Stroke(r * 0.016f))
        } else if (colors.kind == IslandKind.NEON) {
            drawPath(hill, colors.accent, style = Stroke(r * 0.012f))
        }

        // Arched bridge connecting main island east slope to the terrace islet
        val start = Offset(center.x + r * 0.78f, center.y - r * 0.06f)
        val end = Offset(isletCenter.x - ir * 0.72f, isletCenter.y - ir * 0.08f)
        val mid = Offset((start.x + end.x) / 2f, minOf(start.y, end.y) - r * 0.11f)

        val bridgeColor = when (colors.kind) {
            IslandKind.SAKURA -> colors.towerStripe
            IslandKind.NEON -> colors.accent2
            else -> colors.wood
        }
        val railColor = when (colors.kind) {
            IslandKind.NEON -> colors.accent
            IslandKind.SAKURA -> colors.lamp
            else -> colors.thatch
        }

        val deck = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(mid.x, mid.y, end.x, end.y)
        }
        val rail = Path().apply {
            moveTo(start.x, start.y - r * 0.035f)
            quadraticTo(mid.x, mid.y - r * 0.042f, end.x, end.y - r * 0.035f)
        }
        // Vertical bridge posts
        for (i in 0..4) {
            val t = i / 4f
            val u = 1f - t
            val px = u * u * start.x + 2 * u * t * mid.x + t * t * end.x
            val py = u * u * start.y + 2 * u * t * mid.y + t * t * end.y
            drawLine(bridgeColor, Offset(px, py), Offset(px, py - r * 0.038f), strokeWidth = r * 0.01f, cap = StrokeCap.Round)
        }
        drawPath(deck, bridgeColor, style = Stroke(r * 0.022f, cap = StrokeCap.Round))
        drawPath(rail, railColor, style = Stroke(r * 0.011f, cap = StrokeCap.Round))
    }

    /**
     * Draws the western wooden pier and glowing beach campfire (unlocks at 5 units).
     */
    fun DrawScope.drawDockAndCampfire(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units < IslandProgression.PIER_AT) return
        val cx = center.x
        val cy = center.y

        // Western pier jutting into the lagoon
        val pierStart = Offset(cx - r * 0.88f, cy - r * 0.01f)
        val pierEnd = Offset(cx - r * 1.22f, cy + r * 0.03f)
        val plankColor = if (colors.kind == IslandKind.NEON) colors.accent else colors.wood
        val postColor = if (colors.kind == IslandKind.NEON) colors.accent2 else colors.trunk

        // Pier pilings in the water
        for (i in 0..3) {
            val t = i / 3f
            val px = pierStart.x + (pierEnd.x - pierStart.x) * t
            val py = pierStart.y + (pierEnd.y - pierStart.y) * t
            drawLine(postColor, Offset(px, py - r * 0.025f), Offset(px, py + r * 0.055f), strokeWidth = r * 0.015f, cap = StrokeCap.Round)
        }
        // Pier deck
        drawLine(plankColor, pierStart, pierEnd, strokeWidth = r * 0.024f, cap = StrokeCap.Round)

        // Cozy beach campfire near the pier entrance
        val fireBase = Offset(cx - r * 0.52f, cy - r * 0.03f)
        if (colors.lampGlow.alpha > 0f || colors.kind == IslandKind.NEON) {
            drawCircle(
                Brush.radialGradient(
                    listOf(colors.lamp.copy(alpha = 0.55f), Color.Transparent),
                    fireBase - Offset(0f, r * 0.03f),
                    r * 0.14f
                ),
                r * 0.14f,
                fireBase - Offset(0f, r * 0.03f)
            )
        }
        // Crossed logs
        drawLine(colors.trunk, fireBase + Offset(-r * 0.035f, r * 0.008f), fireBase + Offset(r * 0.035f, -r * 0.008f), strokeWidth = r * 0.014f, cap = StrokeCap.Round)
        drawLine(colors.trunk, fireBase + Offset(-r * 0.035f, -r * 0.008f), fireBase + Offset(r * 0.035f, r * 0.008f), strokeWidth = r * 0.014f, cap = StrokeCap.Round)
        // Outer and inner flame
        val flameOuter = Path().apply {
            moveTo(fireBase.x - r * 0.026f, fireBase.y - r * 0.005f)
            quadraticTo(fireBase.x - r * 0.03f, fireBase.y - r * 0.06f, fireBase.x, fireBase.y - r * 0.085f)
            quadraticTo(fireBase.x + r * 0.03f, fireBase.y - r * 0.06f, fireBase.x + r * 0.026f, fireBase.y - r * 0.005f)
            close()
        }
        drawPath(flameOuter, if (colors.kind == IslandKind.NEON) colors.accent2 else Color(0xFFFF7043))
        drawCircle(colors.lamp, r * 0.016f, fireBase - Offset(0f, r * 0.026f))
    }

    /**
     * Draws anchored boats in the lagoon (primary vessel at 18+ units, harbor fleet ship at 150+ units).
     */
    fun DrawScope.drawSeaVessels(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units >= IslandProgression.BOAT_AT) {
            drawBoat(Offset(center.x - r * 1.28f, center.y + r * 0.14f), r * 0.95f, flip = false, colors)
        }
        if (units >= IslandProgression.SKY_VESSEL_AT) {
            drawBoat(Offset(center.x + r * 1.42f, center.y + r * 0.16f), r * 0.82f, flip = true, colors)
        }
    }

    private fun DrawScope.drawBoat(pos: Offset, r: Float, flip: Boolean, colors: IslandColors) {
        val dir = if (flip) -1f else 1f
        val hw = r * 0.15f
        val hh = r * 0.055f

        // Water wake ripple under hull
        drawLine(
            colors.foam.copy(alpha = 0.7f),
            Offset(pos.x - hw * 1.25f, pos.y + hh * 0.2f),
            Offset(pos.x + hw * 1.25f, pos.y + hh * 0.2f),
            strokeWidth = r * 0.012f,
            cap = StrokeCap.Round
        )

        // Boat hull
        val hull = Path().apply {
            moveTo(pos.x - hw, pos.y - hh * 0.6f)
            lineTo(pos.x + hw, pos.y - hh * 0.6f)
            lineTo(pos.x + hw * 0.68f, pos.y + hh * 0.35f)
            lineTo(pos.x - hw * 0.68f, pos.y + hh * 0.35f)
            close()
        }
        val hullColor = when (colors.kind) {
            IslandKind.NEON -> colors.wood
            IslandKind.SAKURA -> colors.trunk
            else -> colors.wood
        }
        drawPath(hull, hullColor)
        if (colors.kind == IslandKind.NEON) {
            drawPath(hull, colors.accent, style = Stroke(r * 0.01f))
        }

        // Mast & Sail
        val mastTop = Offset(pos.x, pos.y - r * 0.24f)
        drawLine(colors.tower, Offset(pos.x, pos.y - hh * 0.6f), mastTop, strokeWidth = r * 0.011f, cap = StrokeCap.Round)

        val sail = Path().apply {
            moveTo(pos.x, mastTop.y + r * 0.01f)
            lineTo(pos.x + dir * hw * 0.95f, pos.y - hh * 1.1f)
            lineTo(pos.x, pos.y - hh * 0.95f)
            close()
        }
        val sailColor = when (colors.kind) {
            IslandKind.NEON -> colors.accent2.copy(alpha = 0.85f)
            IslandKind.SAKURA -> colors.lamp
            IslandKind.TREASURE -> colors.towerStripe
            IslandKind.PALM -> colors.foam
        }
        drawPath(sail, sailColor)
    }

    /**
     * Draws the second village building on the mid-right hillside (unlocks at 50 units):
     * Windmill (PALM), Geodesic Holo-Dome (NEON), Pagoda Pavilion (SAKURA), or Watchtower (TREASURE).
     */
    fun DrawScope.drawHamletBuilding(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units < IslandProgression.HIGHLAND_AT) return
        val base = Offset(center.x + r * 0.42f, center.y - r * 0.16f)

        when (colors.kind) {
            IslandKind.PALM -> {
                // Charming island windmill
                val w = r * 0.13f
                val h = r * 0.20f
                val body = Path().apply {
                    moveTo(base.x - w * 0.55f, base.y)
                    lineTo(base.x - w * 0.38f, base.y - h)
                    lineTo(base.x + w * 0.38f, base.y - h)
                    lineTo(base.x + w * 0.55f, base.y)
                    close()
                }
                drawPath(body, colors.wood)
                drawArc(colors.thatch, 180f, 180f, true, Offset(base.x - w * 0.42f, base.y - h - w * 0.35f), Size(w * 0.84f, w * 0.7f))
                val hub = Offset(base.x, base.y - h * 0.78f)
                val bladeLen = r * 0.13f
                for (k in 0 until 4) {
                    val a = Math.toRadians((k * 90 + 35).toDouble())
                    val tip = Offset(hub.x + (cos(a) * bladeLen).toFloat(), hub.y + (sin(a) * bladeLen).toFloat())
                    drawLine(colors.foam, hub, tip, strokeWidth = r * 0.012f, cap = StrokeCap.Round)
                }
                drawCircle(colors.lamp, r * 0.016f, hub)
            }
            IslandKind.NEON -> {
                // Synthwave geodesic holo-dome
                val rad = r * 0.12f
                val topLeft = Offset(base.x - rad, base.y - rad)
                drawArc(colors.wood, 180f, 180f, true, topLeft, Size(rad * 2f, rad * 2f))
                drawArc(colors.accent, 180f, 180f, true, topLeft, Size(rad * 2f, rad * 2f), style = Stroke(r * 0.012f))
                drawLine(colors.accent2, Offset(base.x, base.y - rad), Offset(base.x, base.y), strokeWidth = r * 0.009f)
                drawLine(colors.accent, Offset(base.x - rad * 0.85f, base.y - rad * 0.45f), Offset(base.x + rad * 0.85f, base.y - rad * 0.45f), strokeWidth = r * 0.009f)
            }
            IslandKind.SAKURA -> {
                // Tiered wooden pavilion
                val w = r * 0.18f
                val h = r * 0.11f
                drawRect(colors.wood, Offset(base.x - w / 2f, base.y - h), Size(w, h))
                drawRect(colors.window, Offset(base.x - w * 0.22f, base.y - h * 0.75f), Size(w * 0.44f, h * 0.45f))
                for (tier in 0..1) {
                    val ty = base.y - h * (1f + tier * 0.55f)
                    val tw = w * (0.72f - tier * 0.18f)
                    val roof = Path().apply {
                        moveTo(base.x - tw, ty)
                        quadraticTo(base.x, ty - h * 0.55f, base.x + tw, ty)
                        close()
                    }
                    drawPath(roof, colors.thatch)
                }
            }
            IslandKind.TREASURE -> {
                // Stone watchtower with battlements
                val w = r * 0.12f
                val h = r * 0.21f
                drawRect(colors.wood, Offset(base.x - w / 2f, base.y - h), Size(w, h))
                if (colors.ink.alpha > 0f) {
                    drawRect(colors.ink, Offset(base.x - w / 2f, base.y - h), Size(w, h), style = Stroke(r * 0.012f))
                }
                drawRect(colors.window, Offset(base.x - w * 0.18f, base.y - h * 0.72f), Size(w * 0.36f, h * 0.25f))
                repeat(3) { i ->
                    val bx = base.x - w * 0.55f + i * (w * 0.42f)
                    drawRect(colors.thatch, Offset(bx, base.y - h - r * 0.028f), Size(w * 0.26f, r * 0.028f))
                }
            }
        }
    }

    /**
     * Draws the majestic centerpiece Grand Palace / Citadel right in front of the trees
     * on the central terrace of the island (100+ units).
     */
    fun DrawScope.drawGrandPalace(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units < IslandProgression.PALACE_AT) return
        val base = Offset(center.x, center.y - r * 0.10f)
        val hasBanners = units >= IslandProgression.CELESTIAL_AT

        // Soft radiant aura behind the palace so its silhouette pops against the trees
        drawCircle(
            Brush.radialGradient(
                listOf(
                    colors.lampGlow.copy(alpha = if (colors.lampGlow.alpha > 0f) 0.55f else 0.28f),
                    Color.Transparent
                ),
                Offset(base.x, base.y - r * 0.24f),
                r * 0.44f
            ),
            r * 0.44f,
            Offset(base.x, base.y - r * 0.24f)
        )

        // Grand ceremonial steps leading from the beach up to the palace terrace
        val stepColor = if (colors.kind == IslandKind.NEON) colors.accent else colors.thatch
        for (s in 0..2) {
            val sw = r * (0.28f - s * 0.05f)
            val sy = base.y + r * (0.04f - s * 0.016f)
            drawRoundRect(
                stepColor,
                Offset(base.x - sw / 2f, sy),
                Size(sw, r * 0.018f),
                CornerRadius(r * 0.006f)
            )
        }

        when (colors.kind) {
            IslandKind.PALM -> drawSunPalace(base, r, hasBanners, colors)
            IslandKind.NEON -> drawCyberCitadel(base, r, hasBanners, colors)
            IslandKind.SAKURA -> drawImperialPagodaPalace(base, r, hasBanners, colors)
            IslandKind.TREASURE -> drawGovernorFortress(base, r, hasBanners, colors)
        }
    }

    private fun DrawScope.drawSunPalace(base: Offset, r: Float, banners: Boolean, colors: IslandColors) {
        val w = r * 0.48f
        val h = r * 0.24f
        // Terraced foundation plinth
        drawRoundRect(colors.thatch, Offset(base.x - w * 0.58f, base.y - h * 0.22f), Size(w * 1.16f, h * 0.24f), CornerRadius(r * 0.02f))
        // Main palace facade
        drawRect(colors.wood, Offset(base.x - w * 0.46f, base.y - h), Size(w * 0.92f, h * 0.80f))
        drawRect(colors.thatch, Offset(base.x - w * 0.48f, base.y - h * 1.05f), Size(w * 0.96f, h * 0.08f))
        // Twin flanking towers (minarets)
        val towerW = w * 0.17f
        val towerH = h * 1.52f
        listOf(-w * 0.50f, w * 0.33f).forEach { dx ->
            drawRect(colors.wood, Offset(base.x + dx, base.y - towerH), Size(towerW, towerH))
            val domeCenter = Offset(base.x + dx + towerW / 2f, base.y - towerH)
            drawCircle(colors.lamp, towerW * 0.58f, domeCenter)
            drawLine(colors.lamp, domeCenter, domeCenter - Offset(0f, r * 0.06f), strokeWidth = r * 0.011f, cap = StrokeCap.Round)
        }
        // Central grand golden onion dome
        val mainDome = Path().apply {
            moveTo(base.x - w * 0.26f, base.y - h)
            cubicTo(
                base.x - w * 0.30f, base.y - h * 1.70f,
                base.x - w * 0.05f, base.y - h * 1.92f,
                base.x, base.y - h * 2.10f
            )
            cubicTo(
                base.x + w * 0.05f, base.y - h * 1.92f,
                base.x + w * 0.30f, base.y - h * 1.70f,
                base.x + w * 0.26f, base.y - h
            )
            close()
        }
        drawPath(mainDome, colors.lamp)
        // Glowing arched palace windows & grand royal portal
        drawArc(colors.door, 180f, 180f, true, Offset(base.x - w * 0.12f, base.y - h * 0.70f), Size(w * 0.24f, h * 0.70f))
        for (i in listOf(-1, 1)) {
            drawCircle(colors.window, r * 0.022f, Offset(base.x + i * w * 0.24f, base.y - h * 0.56f))
        }
        if (banners) {
            drawRoyalBanner(Offset(base.x, base.y - h * 2.10f), r, colors.towerStripe)
        }
    }

    private fun DrawScope.drawCyberCitadel(base: Offset, r: Float, banners: Boolean, colors: IslandColors) {
        val w = r * 0.44f
        val h = r * 0.42f
        // Stepped synthwave megatower
        val tiers = listOf(1.0f to 0.36f, 0.74f to 0.70f, 0.44f to 1.0f)
        tiers.forEach { (wf, hf) ->
            val tw = w * wf
            val th = h * hf
            val topLeft = Offset(base.x - tw / 2f, base.y - th)
            drawRect(colors.wood, topLeft, Size(tw, th))
            drawRect(colors.accent, topLeft, Size(tw, th), style = Stroke(r * 0.014f))
        }
        // Central spire & levitating neon energy ring
        val spireTop = Offset(base.x, base.y - h * 1.34f)
        drawLine(colors.accent2, Offset(base.x, base.y - h), spireTop, strokeWidth = r * 0.018f, cap = StrokeCap.Round)
        drawOval(colors.accent, Offset(base.x - w * 0.34f, base.y - h * 1.12f), Size(w * 0.68f, r * 0.052f), style = Stroke(r * 0.014f))
        drawCircle(colors.lamp, r * 0.028f, spireTop)
        // Glowing vertical neon strips
        for (i in -1..1) {
            val lx = base.x + i * w * 0.14f
            drawLine(colors.window, Offset(lx, base.y - h * 0.12f), Offset(lx, base.y - h * 0.62f), strokeWidth = r * 0.015f, cap = StrokeCap.Round)
        }
        if (banners) {
            drawRoyalBanner(spireTop, r, colors.accent2)
        }
    }

    private fun DrawScope.drawImperialPagodaPalace(base: Offset, r: Float, banners: Boolean, colors: IslandColors) {
        val w = r * 0.48f
        val h = r * 0.36f
        // Sloped stone castle wall base
        val foundation = Path().apply {
            moveTo(base.x - w * 0.58f, base.y)
            lineTo(base.x - w * 0.45f, base.y - h * 0.30f)
            lineTo(base.x + w * 0.45f, base.y - h * 0.30f)
            lineTo(base.x + w * 0.58f, base.y)
            close()
        }
        drawPath(foundation, colors.tower)
        // Three stacked pagoda stories with curved crimson/thatch roofs
        for (tier in 0..2) {
            val yBot = base.y - h * (0.30f + tier * 0.26f)
            val yTop = yBot - h * 0.22f
            val tw = w * (0.78f - tier * 0.18f)
            drawRect(colors.wood, Offset(base.x - tw / 2f, yTop), Size(tw, yBot - yTop))
            drawRect(colors.window, Offset(base.x - tw * 0.24f, yTop + (yBot - yTop) * 0.25f), Size(tw * 0.48f, (yBot - yTop) * 0.45f))
            val rw = tw * 0.74f
            val roof = Path().apply {
                moveTo(base.x - rw, yTop + r * 0.01f)
                quadraticTo(base.x, yTop - h * 0.24f, base.x + rw, yTop + r * 0.01f)
                close()
            }
            drawPath(roof, colors.thatch)
        }
        val finialTop = Offset(base.x, base.y - h * 1.26f)
        drawLine(colors.lamp, Offset(base.x, base.y - h * 1.05f), finialTop, strokeWidth = r * 0.016f, cap = StrokeCap.Round)
        drawCircle(colors.lamp, r * 0.020f, finialTop)
        if (banners) {
            drawRoyalBanner(finialTop, r, colors.accent)
        }
    }

    private fun DrawScope.drawGovernorFortress(base: Offset, r: Float, banners: Boolean, colors: IslandColors) {
        val w = r * 0.48f
        val h = r * 0.31f
        val stroke = r * 0.015f
        // Central stone keep
        val keepRect = Offset(base.x - w * 0.36f, base.y - h)
        drawRect(colors.wood, keepRect, Size(w * 0.72f, h))
        if (colors.ink.alpha > 0f) drawRect(colors.ink, keepRect, Size(w * 0.72f, h), style = Stroke(stroke))
        // Twin outer bastion towers
        val tw = w * 0.24f
        val th = h * 1.24f
        listOf(-w * 0.50f, w * 0.26f).forEach { dx ->
            val tTop = Offset(base.x + dx, base.y - th)
            drawRect(colors.wood, tTop, Size(tw, th))
            if (colors.ink.alpha > 0f) drawRect(colors.ink, tTop, Size(tw, th), style = Stroke(stroke))
            val cone = Path().apply {
                moveTo(tTop.x - tw * 0.15f, tTop.y)
                lineTo(tTop.x + tw * 0.5f, tTop.y - h * 0.44f)
                lineTo(tTop.x + tw * 1.15f, tTop.y)
                close()
            }
            drawPath(cone, colors.towerStripe)
        }
        // Golden crown roof & arched gate
        drawArc(colors.door, 180f, 180f, true, Offset(base.x - w * 0.13f, base.y - h * 0.64f), Size(w * 0.26f, h * 0.64f))
        drawCircle(colors.accent2, r * 0.026f, Offset(base.x, base.y - h * 0.78f))
        if (banners) {
            drawRoyalBanner(Offset(base.x, base.y - h * 1.08f), r, colors.accent2)
        }
    }

    private fun DrawScope.drawRoyalBanner(top: Offset, r: Float, color: Color) {
        val poleTop = top - Offset(0f, r * 0.09f)
        drawLine(color, top, poleTop, strokeWidth = r * 0.01f, cap = StrokeCap.Round)
        val pennant = Path().apply {
            moveTo(poleTop.x, poleTop.y)
            lineTo(poleTop.x + r * 0.11f, poleTop.y + r * 0.028f)
            lineTo(poleTop.x, poleTop.y + r * 0.056f)
            close()
        }
        drawPath(pennant, color)
    }

    /**
     * Draws sacred sanctuary gates, guardian statues, and flowering gardens (unlocks at 220 units).
     */
    fun DrawScope.drawAncientSanctuary(center: Offset, r: Float, units: Int, colors: IslandColors) {
        if (units < IslandProgression.SANCTUARY_AT) return
        val cx = center.x
        val cy = center.y

        // Sanctuary monument on theconnected eastern terrace islet (or east cape)
        val shrineBase = Offset(cx + r * 1.18f, cy - r * 0.05f)
        val sh = r * 0.18f
        val sw = r * 0.16f
        val pillarColor = when (colors.kind) {
            IslandKind.SAKURA -> colors.towerStripe
            IslandKind.NEON -> colors.accent2
            IslandKind.TREASURE -> colors.accent2
            IslandKind.PALM -> colors.lamp
        }
        // Twin sanctuary pillars + lintel beam (Torii / Sun Gate / Cyber Portal)
        drawLine(pillarColor, Offset(shrineBase.x - sw * 0.42f, shrineBase.y), Offset(shrineBase.x - sw * 0.38f, shrineBase.y - sh), strokeWidth = r * 0.018f, cap = StrokeCap.Round)
        drawLine(pillarColor, Offset(shrineBase.x + sw * 0.42f, shrineBase.y), Offset(shrineBase.x + sw * 0.38f, shrineBase.y - sh), strokeWidth = r * 0.018f, cap = StrokeCap.Round)
        drawLine(pillarColor, Offset(shrineBase.x - sw * 0.65f, shrineBase.y - sh * 0.95f), Offset(shrineBase.x + sw * 0.65f, shrineBase.y - sh * 0.95f), strokeWidth = r * 0.022f, cap = StrokeCap.Round)
        drawLine(pillarColor, Offset(shrineBase.x - sw * 0.50f, shrineBase.y - sh * 0.72f), Offset(shrineBase.x + sw * 0.50f, shrineBase.y - sh * 0.72f), strokeWidth = r * 0.014f, cap = StrokeCap.Round)

        // Blooming flower / crystal garden clusters along the foreground hill
        val gardenSpots = floatArrayOf(-0.44f, -0.18f, 0.14f, 0.36f, 0.58f)
        gardenSpots.forEachIndexed { idx, relX ->
            val gx = cx + relX * r
            val gy = cy - r * (0.09f + (idx % 2) * 0.04f)
            drawCircle(colors.frondDark, r * 0.026f, Offset(gx, gy))
            drawCircle(if (idx % 2 == 0) colors.accent else colors.lamp, r * 0.013f, Offset(gx - r * 0.01f, gy - r * 0.008f))
            drawCircle(colors.accent2, r * 0.011f, Offset(gx + r * 0.012f, gy - r * 0.005f))
        }
    }

    /**
     * Draws soaring bird flocks wheeling across the sky above the island (35+ units).
     */
    private fun DrawScope.drawBirdFlock(center: Offset, r: Float, units: Int, colors: IslandColors) {
        val count = when {
            units >= IslandProgression.SANCTUARY_AT -> 11
            units >= IslandProgression.PALACE_AT -> 8
            else -> 5
        }
        val birdColor = when (colors.kind) {
            IslandKind.NEON -> colors.accent2
            IslandKind.SAKURA -> colors.lamp
            IslandKind.TREASURE -> colors.accent2
            IslandKind.PALM -> colors.tower
        }
        val offsets = listOf(
            Offset(-0.65f, -0.92f), Offset(-0.48f, -1.02f), Offset(-0.32f, -0.95f),
            Offset(0.38f, -0.98f), Offset(0.55f, -1.08f),
            Offset(0.72f, -0.96f), Offset(-0.12f, -1.14f), Offset(0.08f, -1.20f),
            Offset(0.24f, -1.12f), Offset(-0.82f, -0.82f), Offset(0.88f, -0.86f)
        )
        for (i in 0 until count.coerceAtMost(offsets.size)) {
            val rel = offsets[i]
            val pos = Offset(center.x + rel.x * r, center.y + rel.y * r)
            val span = r * (0.045f + (i % 3) * 0.008f)
            val wing = Path().apply {
                moveTo(pos.x - span, pos.y - span * 0.25f)
                quadraticTo(pos.x - span * 0.45f, pos.y - span * 0.65f, pos.x, pos.y)
                quadraticTo(pos.x + span * 0.45f, pos.y - span * 0.65f, pos.x + span, pos.y - span * 0.25f)
            }
            drawPath(wing, birdColor, style = Stroke(r * 0.011f, cap = StrokeCap.Round))
        }
    }

    /**
     * Draws a floating sky vessel (Hot Air Balloon, Synth Cruiser, Sky Lanterns, or Airship) at 150+ units.
     */
    private fun DrawScope.drawSkyVessel(pos: Offset, r: Float, colors: IslandColors) {
        when (colors.kind) {
            IslandKind.NEON -> {
                // Synthwave hover-cruiser with glowing cyan trail
                val w = r * 0.22f
                val h = r * 0.06f
                drawLine(colors.accent.copy(alpha = 0.55f), Offset(pos.x - w * 1.1f, pos.y), Offset(pos.x - w * 0.3f, pos.y), strokeWidth = r * 0.016f, cap = StrokeCap.Round)
                val ship = Path().apply {
                    moveTo(pos.x - w * 0.5f, pos.y + h * 0.3f)
                    lineTo(pos.x - w * 0.1f, pos.y - h * 0.6f)
                    lineTo(pos.x + w * 0.6f, pos.y + h * 0.1f)
                    close()
                }
                drawPath(ship, colors.wood)
                drawPath(ship, colors.accent2, style = Stroke(r * 0.01f))
            }
            IslandKind.SAKURA -> {
                // Trio of glowing floating sky lanterns
                val spots = listOf(Offset(0f, 0f) to 1f, Offset(r * 0.14f, -r * 0.08f) to 0.78f, Offset(-r * 0.12f, r * 0.07f) to 0.72f)
                spots.forEach { (off, scale) ->
                    val c = pos + off
                    val lr = r * 0.055f * scale
                    drawCircle(Brush.radialGradient(listOf(colors.lamp.copy(alpha = 0.55f), Color.Transparent), c, lr * 2.4f), lr * 2.4f, c)
                    drawRoundRect(colors.lamp, Offset(c.x - lr * 0.7f, c.y - lr), Size(lr * 1.4f, lr * 2f), CornerRadius(lr * 0.4f))
                    drawCircle(colors.towerStripe, lr * 0.25f, Offset(c.x, c.y + lr * 0.7f))
                }
            }
            else -> {
                // Striped Hot Air Balloon / Expedition Blimp
                val br = r * 0.11f
                // Gondola basket & ropes
                val basketTop = pos.y + br * 1.25f
                drawLine(colors.trunk, Offset(pos.x - br * 0.3f, pos.y + br * 0.7f), Offset(pos.x - br * 0.2f, basketTop), strokeWidth = r * 0.007f)
                drawLine(colors.trunk, Offset(pos.x + br * 0.3f, pos.y + br * 0.7f), Offset(pos.x + br * 0.2f, basketTop), strokeWidth = r * 0.007f)
                drawRect(colors.wood, Offset(pos.x - br * 0.25f, basketTop), Size(br * 0.5f, br * 0.35f))
                // Balloon envelope
                drawCircle(colors.towerStripe, br, pos)
                drawOval(colors.lamp, Offset(pos.x - br * 0.55f, pos.y - br), Size(br * 1.1f, br * 2f))
                drawLine(colors.accent, Offset(pos.x - br * 0.96f, pos.y), Offset(pos.x + br * 0.96f, pos.y), strokeWidth = r * 0.016f, cap = StrokeCap.Round)
            }
        }
    }

    private data class FireworkSpec(
        val relX: Float,
        val relY: Float,
        val radiusFactor: Float,
        val outerColor: Color,
        val midColor: Color,
        val coreColor: Color,
        val launchOffsetX: Float,
        val phaseOffset: Float,
        val defaultBloom: Float
    )

    private fun fireworkSpecsFor(colors: IslandColors): List<FireworkSpec> {
        val (c1Outer, c1Mid, c1Core) = when (colors.kind) {
            IslandKind.NEON -> Triple(Color(0xFFFF2A85), Color(0xFFFF9E00), Color(0xFF00F0FF))
            IslandKind.SAKURA -> Triple(Color(0xFFFF2E7E), Color(0xFFFFAB40), Color(0xFFFFF8E1))
            IslandKind.TREASURE -> Triple(Color(0xFFFFAB00), Color(0xFFFF3D00), Color(0xFFFFF59D))
            IslandKind.PALM -> Triple(Color(0xFFFFAB00), Color(0xFFFFEA00), Color(0xFF18FFFF))
        }
        val (c2Outer, c2Mid, c2Core) = when (colors.kind) {
            IslandKind.NEON -> Triple(Color(0xFF00F0FF), Color(0xFF76FF03), Color(0xFFFFFFFF))
            IslandKind.SAKURA -> Triple(Color(0xFFAA00FF), Color(0xFFFF4081), Color(0xFFFFE57F))
            IslandKind.TREASURE -> Triple(Color(0xFFFF1744), Color(0xFFFFD600), Color(0xFFFFFFFF))
            IslandKind.PALM -> Triple(Color(0xFFFF3D7F), Color(0xFFFFAB40), Color(0xFFFFF9C4))
        }
        val (c3Outer, c3Mid, c3Core) = when (colors.kind) {
            IslandKind.NEON -> Triple(Color(0xFFC6FF00), Color(0xFF00E5FF), Color(0xFFFF4081))
            IslandKind.SAKURA -> Triple(Color(0xFFFF6D00), Color(0xFFFFD740), Color(0xFFFFFFFF))
            IslandKind.TREASURE -> Triple(Color(0xFF00C853), Color(0xFF64DD17), Color(0xFFFFF176))
            IslandKind.PALM -> Triple(Color(0xFF00E5FF), Color(0xFF64FFDA), Color(0xFFFFD740))
        }
        val (c4Outer, c4Mid, c4Core) = when (colors.kind) {
            IslandKind.NEON -> Triple(Color(0xFFD500F9), Color(0xFF00E5FF), Color(0xFFFFEA00))
            IslandKind.SAKURA -> Triple(Color(0xFFFF1744), Color(0xFFFF80AB), Color(0xFFFFF59D))
            IslandKind.TREASURE -> Triple(Color(0xFF00B0FF), Color(0xFFFFD600), Color(0xFFFFFFFF))
            IslandKind.PALM -> Triple(Color(0xFFE040FB), Color(0xFFFF80AB), Color(0xFFFFE57F))
        }

        // Positioned in a high royal arc well above the tallest highland palm fronds and canopy
        // (relY -1.08..-1.32) so bursts never overlap the trees while staying inside the sky viewport.
        return listOf(
            // 0. Centerpiece Grand Brocade Chrysanthemum high above the Grand Palace spire & central canopy
            FireworkSpec(0.02f, -1.32f, 0.19f, c1Outer, c1Mid, c1Core, 0.00f, 0.00f, 0.78f),
            // 1. Western Peony Shell floating above the left highland palms
            FireworkSpec(-0.44f, -1.24f, 0.17f, c2Outer, c2Mid, c2Core, -0.26f, 0.34f, 0.68f),
            // 2. Eastern Willow Shell floating above the right highland canopy
            FireworkSpec(0.46f, -1.26f, 0.17f, c3Outer, c3Mid, c3Core, 0.28f, 0.67f, 0.84f),
            // 3. Far-West Coastal Shell high above the western shore trees
            FireworkSpec(-0.84f, -1.08f, 0.15f, c4Outer, c4Mid, c4Core, -0.64f, 0.18f, 0.74f),
            // 4. Far-East Sanctuary Terrace Shell high above the eastern terrace & beacon
            FireworkSpec(0.92f, -1.10f, 0.15f, c1Outer, c2Mid, c3Core, 0.76f, 0.52f, 0.64f)
        )
    }

    /**
     * Computes the lifecycle stage (0f..1f) for a single firework shell:
     * - 0.00..0.16: Rising rocket trail shooting up from behind the island
     * - 0.16..1.00: Parabolic chrysanthemum burst expanding, drooping with gravity, and twinkling
     */
    private fun shellCycle(spec: FireworkSpec, fireworkPhase: Float): Float {
        return if (fireworkPhase < 0f) {
            spec.defaultBloom
        } else {
            ((fireworkPhase + spec.phaseOffset) % 1f + 1f) % 1f
        }
    }

    /**
     * Pass 1 (Behind island & trees): Draws the rising whistler rocket trails and
     * atmospheric sky glow so rockets genuinely launch from behind the mountain ridge.
     */
    private fun DrawScope.drawSkyFireworksBackdrop(
        center: Offset,
        r: Float,
        colors: IslandColors,
        fireworkPhase: Float
    ) {
        val isDaySky = colors.star.alpha == 0f
        val specs = fireworkSpecsFor(colors)

        specs.forEachIndexed { idx, spec ->
            val cycle = shellCycle(spec, fireworkPhase)
            val burstCenter = Offset(center.x + spec.relX * r, center.y + spec.relY * r)
            val burstRadius = r * spec.radiusFactor
            val launchStart = Offset(center.x + spec.launchOffsetX * r, center.y - r * 0.58f)
            val ctrlX = (launchStart.x + burstCenter.x) * 0.5f + (if (idx % 2 == 0) -r * 0.045f else r * 0.045f)
            val ctrlY = (launchStart.y + burstCenter.y) * 0.5f

            // Rising rocket phase (0.00..0.16) or fading launch smoke during early burst (0.16..0.55)
            val trailAlpha = when {
                cycle < 0.16f -> 0.85f
                cycle < 0.58f -> ((0.58f - cycle) / 0.42f) * 0.48f
                else -> 0f
            }
            if (trailAlpha > 0.02f) {
                val headT = (cycle / 0.16f).coerceIn(0f, 1f)
                val curX = (1f - headT) * (1f - headT) * launchStart.x +
                    2f * (1f - headT) * headT * ctrlX +
                    headT * headT * burstCenter.x
                val curY = (1f - headT) * (1f - headT) * launchStart.y +
                    2f * (1f - headT) * headT * ctrlY +
                    headT * headT * burstCenter.y

                val trailPath = Path().apply {
                    moveTo(launchStart.x, launchStart.y)
                    quadraticTo(
                        launchStart.x + (ctrlX - launchStart.x) * headT,
                        launchStart.y + (ctrlY - launchStart.y) * headT,
                        curX,
                        curY
                    )
                }
                if (isDaySky) {
                    drawPath(
                        trailPath,
                        Color(0xFF1A103D).copy(alpha = trailAlpha * 0.28f),
                        style = Stroke(width = r * 0.014f, cap = StrokeCap.Round)
                    )
                }
                drawPath(
                    trailPath,
                    Brush.verticalGradient(
                        listOf(
                            spec.midColor.copy(alpha = trailAlpha),
                            spec.outerColor.copy(alpha = trailAlpha * 0.45f),
                            Color.Transparent
                        ),
                        startY = curY,
                        endY = launchStart.y
                    ),
                    style = Stroke(width = r * 0.0075f, cap = StrokeCap.Round)
                )
                // Bright ascending rocket head spark while climbing
                if (cycle < 0.16f) {
                    val headPos = Offset(curX, curY)
                    drawCircle(spec.midColor.copy(alpha = 0.55f), r * 0.022f, headPos)
                    drawCircle(Color.White, r * 0.009f, headPos)
                }
            }

            // Soft atmospheric bloom behind the burst once exploded (cycle >= 0.16)
            if (cycle >= 0.16f) {
                val rawBloom = ((cycle - 0.16f) / 0.84f).coerceIn(0f, 1f)
                val fade = if (rawBloom < 0.72f) 1f else ((1f - rawBloom) / 0.28f).coerceIn(0f, 1f)
                val glowRadius = burstRadius * (0.65f + 0.75f * kotlin.math.sqrt(rawBloom))

                // In bright Day skies, paint a subtle twilight-indigo contrast halo so vivid sparks pop
                if (isDaySky) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF160E33).copy(alpha = 0.28f * fade),
                                Color(0xFF160E33).copy(alpha = 0.12f * fade),
                                Color.Transparent
                            ),
                            center = burstCenter,
                            radius = glowRadius * 1.18f
                        ),
                        radius = glowRadius * 1.18f,
                        center = burstCenter
                    )
                }

                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            spec.midColor.copy(alpha = (if (isDaySky) 0.42f else 0.36f) * fade),
                            spec.outerColor.copy(alpha = 0.16f * fade),
                            Color.Transparent
                        ),
                        center = burstCenter,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = burstCenter
                )
            }
        }
    }

    /**
     * Pass 2 (Above canopy): Draws curved parabolic chrysanthemum & willow streamers,
     * inner pistil rings, cascading golden embers, and crisp 4-point diamond stars.
     */
    private fun DrawScope.drawSkyFireworksBursts(
        center: Offset,
        r: Float,
        colors: IslandColors,
        fireworkPhase: Float
    ) {
        val isDaySky = colors.star.alpha == 0f
        val specs = fireworkSpecsFor(colors)

        specs.forEachIndexed { idx, spec ->
            val cycle = shellCycle(spec, fireworkPhase)
            if (cycle < 0.16f) return@forEachIndexed

            val rawBloom = ((cycle - 0.16f) / 0.84f).coerceIn(0f, 1f)
            // Fast explosive ease-out expansion, then graceful slow willow drift
            val easeBloom = 1f - (1f - rawBloom) * (1f - rawBloom) * (1f - rawBloom)
            val alpha = when {
                rawBloom < 0.08f -> (rawBloom / 0.08f).coerceIn(0.2f, 1f)
                rawBloom < 0.72f -> 1f
                else -> ((1f - rawBloom) / 0.28f).coerceIn(0f, 1f)
            }
            if (alpha <= 0.02f) return@forEachIndexed

            val burstCenter = Offset(center.x + spec.relX * r, center.y + spec.relY * r)
            val maxRadius = r * spec.radiusFactor
            val currentRadius = maxRadius * (0.22f + 0.82f * easeBloom)
            // Gravity pulls the outer willow tips gently downward as the shell matures
            val gravityDroop = maxRadius * 0.20f * rawBloom * rawBloom

            // 1. Outer & Mid 3D Spherical Chrysanthemum Plumes (20 curved parabolic arcs)
            val plumeCount = 20
            for (i in 0 until plumeCount) {
                val baseAngleDeg = i * (360.0 / plumeCount) + (idx * 9.0)
                val angleRad = Math.toRadians(baseAngleDeg)
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()

                // Natural 3D spherical foreshortening: alternate long outer plumes & medium mid-sphere plumes
                val layerScale = when (i % 4) {
                    0 -> 1.00f
                    2 -> 0.84f
                    1 -> 0.68f
                    else -> 0.54f
                }
                val plumeLen = currentRadius * layerScale
                val innerGap = plumeLen * (0.10f + 0.14f * rawBloom)

                val startPt = Offset(
                    burstCenter.x + cosA * innerGap,
                    burstCenter.y + sinA * innerGap + gravityDroop * 0.08f
                )
                val ctrlPt = Offset(
                    burstCenter.x + cosA * (plumeLen * 0.58f),
                    burstCenter.y + sinA * (plumeLen * 0.58f) + gravityDroop * 0.35f
                )
                val endPt = Offset(
                    burstCenter.x + cosA * plumeLen,
                    burstCenter.y + sinA * plumeLen + gravityDroop * (0.75f + 0.25f * kotlin.math.abs(cosA))
                )

                val plumePath = Path().apply {
                    moveTo(startPt.x, startPt.y)
                    quadraticTo(ctrlPt.x, ctrlPt.y, endPt.x, endPt.y)
                }

                // Soft outer luminance halo along each curved streamer
                val glowStrokeColor = if (isDaySky) {
                    spec.outerColor.copy(alpha = 0.42f * alpha)
                } else {
                    spec.outerColor.copy(alpha = 0.30f * alpha)
                }
                drawPath(
                    path = plumePath,
                    color = glowStrokeColor,
                    style = Stroke(width = r * 0.013f, cap = StrokeCap.Round)
                )

                // Crisp gradient core streamer (White-hot center -> Mid color -> Vivid outer tip)
                val tipColor = if (i % 2 == 0) spec.outerColor else spec.midColor
                drawPath(
                    path = plumePath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f * alpha),
                            spec.midColor.copy(alpha = 0.92f * alpha),
                            tipColor.copy(alpha = alpha)
                        ),
                        start = startPt,
                        end = endPt
                    ),
                    style = Stroke(
                        width = r * (if (i % 2 == 0) 0.0062f else 0.0048f),
                        cap = StrokeCap.Round
                    )
                )

                // Delicate incandescent ember bead at the drooping tip (no oversized circles!)
                val tipGlowRadius = r * (if (i % 2 == 0) 0.0105f else 0.0080f)
                drawCircle(
                    color = tipColor.copy(alpha = 0.52f * alpha),
                    radius = tipGlowRadius,
                    center = endPt
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f * alpha),
                    radius = r * (if (i % 2 == 0) 0.0046f else 0.0034f),
                    center = endPt
                )

                // Trailing falling willow micro-spark below every primary plume
                if (i % 2 == 0 && rawBloom > 0.22f) {
                    val emberPos = Offset(
                        endPt.x + cosA * (r * 0.012f),
                        endPt.y + gravityDroop * 0.36f + r * 0.010f
                    )
                    drawCircle(
                        color = spec.midColor.copy(alpha = 0.78f * alpha),
                        radius = r * 0.0036f,
                        center = emberPos
                    )
                }
            }

            // 2. Inner Contrasting Pistil Ring (10 crisp short filaments + crackle sparks)
            val pistilRays = 10
            val pistilRadius = currentRadius * 0.38f
            for (k in 0 until pistilRays) {
                val angleRad = Math.toRadians(k * (360.0 / pistilRays) + 18.0 + (idx * 11.0))
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()
                val pStart = Offset(
                    burstCenter.x + cosA * (pistilRadius * 0.18f),
                    burstCenter.y + sinA * (pistilRadius * 0.18f)
                )
                val pEnd = Offset(
                    burstCenter.x + cosA * pistilRadius,
                    burstCenter.y + sinA * pistilRadius + gravityDroop * 0.18f
                )
                drawLine(
                    color = spec.coreColor.copy(alpha = 0.88f * alpha),
                    start = pStart,
                    end = pEnd,
                    strokeWidth = r * 0.0044f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.92f * alpha),
                    radius = r * 0.0035f,
                    center = pEnd
                )
            }

            // 3. Twinkling 4-Pointed Diamond Star Sparkles (✦) around the shell
            val starCount = 5
            val twinklePulse = 0.65f + 0.35f * sin((rawBloom * 14f + idx * 1.7f).toDouble()).toFloat()
            for (s in 0 until starCount) {
                val starAngle = Math.toRadians(s * (360.0 / starCount) + 36.0 + idx * 23.0)
                val starDist = currentRadius * (if (s % 2 == 0) 0.76f else 1.06f)
                val starCenter = Offset(
                    burstCenter.x + cos(starAngle).toFloat() * starDist,
                    burstCenter.y + sin(starAngle).toFloat() * starDist + gravityDroop * 0.45f
                )
                val starSize = r * (if (s % 2 == 0) 0.022f else 0.017f) * twinklePulse
                drawSparkleStar(
                    center = starCenter,
                    radius = starSize,
                    color = if (s % 2 == 0) Color.White.copy(alpha = alpha) else spec.midColor.copy(alpha = alpha),
                    haloColor = spec.outerColor.copy(alpha = 0.45f * alpha)
                )
            }

            // 4. Incandescent Central Flash Starburst at the core of the explosion
            val coreFlashScale = (1f - rawBloom * 0.55f).coerceIn(0.35f, 1f)
            drawSparkleStar(
                center = burstCenter,
                radius = r * 0.032f * coreFlashScale,
                color = Color.White.copy(alpha = alpha),
                haloColor = spec.coreColor.copy(alpha = 0.65f * alpha)
            )
        }
    }

    /**
     * Draws a crisp 4-pointed diamond sparkle star (`✦`) with concave bezier edges and a soft glow.
     */
    private fun DrawScope.drawSparkleStar(
        center: Offset,
        radius: Float,
        color: Color,
        haloColor: Color
    ) {
        if (radius <= 0.5f) return
        drawCircle(
            color = haloColor,
            radius = radius * 0.85f,
            center = center
        )
        val waist = radius * 0.16f
        val starPath = Path().apply {
            moveTo(center.x, center.y - radius)
            quadraticTo(center.x + waist, center.y - waist, center.x + radius, center.y)
            quadraticTo(center.x + waist, center.y + waist, center.x, center.y + radius)
            quadraticTo(center.x - waist, center.y + waist, center.x - radius, center.y)
            quadraticTo(center.x - waist, center.y - waist, center.x, center.y - radius)
            close()
        }
        drawPath(starPath, color)
    }
}
