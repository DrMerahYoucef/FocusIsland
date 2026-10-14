package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.IslandColors
import com.example.ui.theme.IslandKind
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Focus Island's scene: your island in a lagoon. Every completed session plants a palm (up to
 * [MAX_PALMS]) and the island grows until [FULL_GROWTH] sessions; a hut appears at [HUT_AT]
 * sessions and a lighthouse at [LIGHTHOUSE_AT], lit at night. Used by the wallpaper (static and
 * live), the Community archipelago and the friend sheet. Plain DrawScope code, so it also renders off-screen.
 */
object IslandRenderer {
    /** Sessions after which the island has its full size. */
    const val FULL_GROWTH = 40

    /** Palms drawn at most. */
    const val MAX_PALMS = 9

    const val HUT_AT = 12
    const val LIGHTHOUSE_AT = 25

    // Palm spots on the hill, as a fraction of the island's half-width, in planting order:
    // the first palm stands in the middle, the next ones alternate right and left.
    private val palmSpots = floatArrayOf(0f, .30f, -.30f, .52f, -.52f, .15f, -.15f, .41f, -.41f)

    /** Island half-width for [sessions], from 62 % of [fullRadius] (no session) to 100 %. */
    fun radius(fullRadius: Float, sessions: Int): Float =
        fullRadius * (0.62f + 0.38f * sessions.coerceIn(0, FULL_GROWTH) / FULL_GROWTH)

    /** Height above the waterline taken by the island and its palms (for labels and taps). */
    fun crownHeight(radius: Float, sessions: Int): Float = radius * if (sessions > 0) 1.15f else 0.45f

    /** Wallpaper: sky, sea, two distant islets and your island low in the middle of the screen. */
    fun DrawScope.drawLagoonScene(sessions: Int, colors: IslandColors) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.50f
        drawSkyAndSea(horizon, Offset(w * 0.74f, h * 0.30f), w * 0.07f, colors)
        drawIslet(Offset(w * 0.12f, horizon), w * 0.11f, colors)
        drawIslet(Offset(w * 0.90f, horizon), w * 0.07f, colors)
        drawIsland(Offset(w * 0.5f, h * 0.70f), radius(w * 0.36f, sessions), sessions, colors)
    }

    /** Sky with sun or moon (and stars, clouds), sea from [horizon] down with a reflection under the light. */
    fun DrawScope.drawSkyAndSea(horizon: Float, light: Offset, lightRadius: Float, colors: IslandColors) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(colors.skyTop, colors.skyHorizon), 0f, horizon), size = Size(w, horizon))
        if (colors.star.alpha > 0f) {
            repeat(36) { i ->
                val x = (i * 97.3f) % w
                val y = horizon * (0.04f + ((i * 0.137f) % 0.80f))
                drawCircle(colors.star.copy(alpha = if (i % 4 == 0) 0.85f else 0.35f), if (i % 5 == 0) 2.2f else 1.2f, Offset(x, y))
            }
        }
        drawCircle(Brush.radialGradient(listOf(colors.glow, colors.glow.copy(alpha = 0f)), light, lightRadius * 4f), lightRadius * 4f, light)
        drawCircle(colors.sunOrMoon, lightRadius, light)
        if (colors.kind == IslandKind.NEON) {
            // Synthwave sun: gaps across its lower half.
            repeat(4) { k ->
                val y = light.y + lightRadius * (0.12f + 0.22f * k)
                drawLine(colors.skyHorizon, Offset(light.x - lightRadius, y), Offset(light.x + lightRadius, y), strokeWidth = lightRadius * (0.05f + 0.035f * k))
            }
        }
        if (colors.cloud.alpha > 0f) {
            drawCloud(Offset(w * 0.22f, horizon * 0.32f), w * 0.10f, colors)
            drawCloud(Offset(w * 0.58f, horizon * 0.62f), w * 0.07f, colors)
        }

        drawRect(Brush.verticalGradient(listOf(colors.seaFar, colors.seaNear), horizon, h), Offset(0f, horizon), Size(w, h - horizon))
        // Reflection: short strokes under the light, shorter and fainter as they come closer.
        repeat(14) { i ->
            val t = i / 14f
            val y = horizon + (h - horizon) * (0.02f + t * t * 0.9f)
            val half = lightRadius * (1.4f - t) * (if (i % 2 == 0) 1f else 0.6f)
            drawLine(colors.shimmer.copy(alpha = colors.shimmer.alpha * (1f - t)), Offset(light.x - half, y), Offset(light.x + half, y), strokeWidth = 2f + 3f * t, cap = StrokeCap.Round)
        }
        when (colors.kind) {
            IslandKind.NEON -> drawNeonGrid(horizon, colors)
            IslandKind.SAKURA -> drawPetals(horizon, lightRadius, colors)
            else -> drawWaveMarks(horizon, colors)
        }
    }

    /** Small wave marks spread over the sea. */
    private fun DrawScope.drawWaveMarks(horizon: Float, colors: IslandColors) {
        val w = size.width
        val h = size.height
        repeat(18) { i ->
            val x = (i * 173.7f) % w
            val y = horizon + (h - horizon) * (0.08f + ((i * 0.291f) % 0.85f))
            val len = w * (0.015f + 0.02f * ((i * 0.37f) % 1f)) * (y - horizon) / (h - horizon) * 2f
            drawLine(colors.shimmer.copy(alpha = 0.25f), Offset(x - len, y), Offset(x + len, y), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }

    /** Night Arcade: a perspective grid on the sea, lines closer together towards the horizon. */
    private fun DrawScope.drawNeonGrid(horizon: Float, colors: IslandColors) {
        val w = size.width
        val h = size.height
        for (i in 1..10) {
            val t = (i / 10f) * (i / 10f)
            val y = horizon + (h - horizon) * t
            drawLine(colors.accent.copy(alpha = 0.12f + 0.30f * t), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f + 2f * t)
        }
        for (k in -10..10) {
            drawLine(colors.accent.copy(alpha = 0.18f), Offset(w / 2f + k * w * 0.03f, horizon), Offset(w / 2f + k * w * 0.20f, h), strokeWidth = 1.5f)
        }
    }

    /** Blossom: petals floating on the water. */
    private fun DrawScope.drawPetals(horizon: Float, lightRadius: Float, colors: IslandColors) {
        val w = size.width
        val h = size.height
        repeat(22) { i ->
            val x = (i * 131.3f) % w
            val t = 0.06f + ((i * 0.377f) % 0.9f)
            val y = horizon + (h - horizon) * t
            val s = lightRadius * (0.10f + 0.12f * t)
            drawOval(if (i % 3 == 0) colors.accent2 else colors.accent, Offset(x - s, y - s * 0.45f), Size(s * 2f, s * 0.9f))
        }
    }

    /**
     * Small wave marks on a staggered grid, offset by [shift] (px) so they slide as the view moves:
     * the sea has no edge however far you drag.
     */
    fun DrawScope.drawSeaMarks(top: Float, shift: Offset, spacing: Float, colors: IslandColors) {
        if (spacing < 8f) return
        val rowStep = spacing * 0.5f
        val ox = ((shift.x % spacing) + spacing) % spacing
        val oy = ((shift.y % rowStep) + rowStep) % rowStep
        val rowOffset = Math.floorMod(kotlin.math.floor(shift.y / rowStep).toInt(), 2)
        val len = spacing * 0.12f
        val color = when (colors.kind) {
            IslandKind.NEON -> colors.accent.copy(alpha = 0.28f)
            IslandKind.SAKURA -> colors.accent.copy(alpha = 0.45f)
            else -> colors.shimmer.copy(alpha = 0.22f)
        }
        var row = 0
        var y = top - oy
        while (y < size.height + rowStep) {
            if (y > top + 2f) {
                val stagger = if ((row + rowOffset) % 2 == 0) 0f else spacing / 2f
                var x = -ox + stagger - spacing
                while (x < size.width + spacing) {
                    drawLine(color, Offset(x - len, y), Offset(x + len, y), strokeWidth = 2f, cap = StrokeCap.Round)
                    x += spacing
                }
            }
            y += rowStep
            row++
        }
    }

    private fun DrawScope.drawCloud(center: Offset, r: Float, colors: IslandColors) {
        drawOval(colors.cloud, Offset(center.x - r * 1.6f, center.y - r * 0.25f), Size(r * 3.2f, r * 0.6f))
        drawOval(colors.cloud, Offset(center.x - r * 0.9f, center.y - r * 0.55f), Size(r * 1.6f, r * 0.7f))
    }

    /** A distant islet: a soft hump sitting on the horizon. */
    private fun DrawScope.drawIslet(center: Offset, r: Float, colors: IslandColors) {
        val path = Path().apply {
            moveTo(center.x - r, center.y)
            cubicTo(center.x - r * 0.5f, center.y - r * 0.35f, center.x + r * 0.4f, center.y - r * 0.40f, center.x + r, center.y)
            close()
        }
        drawPath(path, colors.farIsland)
    }

    /**
     * One island whose waterline is centred on [center], [radius] wide on each side, with a palm
     * for each of the first [MAX_PALMS] sessions.
     */
    fun DrawScope.drawIsland(center: Offset, radius: Float, sessions: Int, colors: IslandColors) {
        val cx = center.x
        val cy = center.y
        val r = radius
        // Turquoise shallows and the foam line around the beach.
        drawOval(colors.shallow, Offset(cx - r * 1.35f, cy - r * 0.16f), Size(r * 2.7f, r * 0.42f))
        drawOval(colors.foam, Offset(cx - r * 1.06f, cy - r * 0.08f), Size(r * 2.12f, r * 0.22f), style = Stroke(r * 0.02f))
        // Beach.
        val sand = Path().apply {
            moveTo(cx - r, cy)
            cubicTo(cx - r * 0.7f, cy - r * 0.22f, cx - r * 0.3f, cy - r * 0.30f, cx, cy - r * 0.30f)
            cubicTo(cx + r * 0.3f, cy - r * 0.30f, cx + r * 0.7f, cy - r * 0.22f, cx + r, cy)
            quadraticTo(cx, cy + r * 0.16f, cx - r, cy)
            close()
        }
        drawPath(sand, colors.sand)
        if (colors.ink.alpha > 0f) drawPath(sand, colors.ink, style = Stroke(r * 0.022f))
        drawOval(colors.sandShade, Offset(cx - r * 0.92f, cy - r * 0.02f), Size(r * 1.84f, r * 0.10f))
        // Green hill on top of the beach.
        val hill = Path().apply {
            moveTo(cx - r * 0.80f, cy - r * 0.10f)
            cubicTo(cx - r * 0.55f, cy - r * 0.38f, cx - r * 0.2f, cy - r * 0.44f, cx + r * 0.05f, cy - r * 0.44f)
            cubicTo(cx + r * 0.35f, cy - r * 0.44f, cx + r * 0.62f, cy - r * 0.34f, cx + r * 0.80f, cy - r * 0.10f)
            quadraticTo(cx, cy - r * 0.02f, cx - r * 0.80f, cy - r * 0.10f)
            close()
        }
        drawPath(hill, colors.grass)
        when {
            colors.ink.alpha > 0f -> drawPath(hill, colors.ink, style = Stroke(r * 0.022f))
            colors.kind == IslandKind.NEON -> {
                drawPath(hill, colors.accent.copy(alpha = 0.25f), style = Stroke(r * 0.04f))
                drawPath(hill, colors.accent, style = Stroke(r * 0.012f))
            }
        }
        drawOval(colors.grassLight, Offset(cx - r * 0.50f, cy - r * 0.38f), Size(r * 0.42f, r * 0.12f))

        // Palms, back (higher on the hill) to front.
        val count = sessions.coerceIn(0, MAX_PALMS)
        (0 until count)
            .map { i ->
                val rel = palmSpots[i]
                val x = cx + rel * r
                val base = cy - r * (0.12f + 0.30f * (1f - (rel / 0.8f) * (rel / 0.8f)).coerceAtLeast(0f))
                Triple(i, x, base)
            }
            .sortedBy { it.third }
            .forEach { (i, x, base) ->
                val height = r * 0.50f * (0.82f + 0.18f * ((i * 0.61f) % 1f))
                val lean = height * (if (palmSpots[i] < 0f) -0.20f else 0.20f) * (if (i == 0) 0.4f else 1f)
                when (colors.kind) {
                    IslandKind.NEON -> drawNeonPalm(Offset(x, base), height, lean, colors)
                    IslandKind.SAKURA -> drawCherryTree(Offset(x, base), height * 0.92f, lean * 0.4f, colors)
                    else -> drawPalm(Offset(x, base), height, lean, colors)
                }
            }
        // Milestones stand on the beach, in front of the trees.
        val left = Offset(cx - r * 0.70f, cy - r * 0.06f)
        val right = Offset(cx + r * 0.74f, cy - r * 0.05f)
        when (colors.kind) {
            IslandKind.PALM -> {
                if (sessions >= HUT_AT) drawHut(left, r, colors)
                if (sessions >= LIGHTHOUSE_AT) drawLighthouse(right, r, colors)
            }
            IslandKind.NEON -> {
                if (sessions >= HUT_AT) drawServer(left, r, colors)
                if (sessions >= LIGHTHOUSE_AT) drawRadioTower(right, r, colors)
            }
            IslandKind.SAKURA -> {
                if (sessions >= HUT_AT) drawTeaHouse(left, r, colors)
                if (sessions >= LIGHTHOUSE_AT) drawLantern(right, r, colors)
            }
            IslandKind.TREASURE -> {
                // The chest appears first and opens when the flag goes up.
                if (sessions >= HUT_AT) drawChest(left, r, open = sessions >= LIGHTHOUSE_AT, colors)
                if (sessions >= LIGHTHOUSE_AT) drawPirateFlag(right, r, colors)
                drawTurtle(Offset(cx - r * 1.08f, cy + r * 0.12f), r, colors)
            }
        }
    }

    // ------------------------------------------------------------------ Night Arcade

    /** A palm drawn in neon strokes, each line over a soft glow. */
    private fun DrawScope.drawNeonPalm(base: Offset, height: Float, lean: Float, colors: IslandColors) {
        val top = Offset(base.x + lean, base.y - height)
        val trunk = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(base.x + lean * 0.1f, base.y - height * 0.55f, top.x, top.y)
        }
        drawPath(trunk, colors.trunk.copy(alpha = 0.22f), style = Stroke(height * 0.14f, cap = StrokeCap.Round))
        drawPath(trunk, colors.trunk, style = Stroke(height * 0.035f, cap = StrokeCap.Round))
        val len = height * 0.6f
        floatArrayOf(-170f, -135f, -100f, -72f, -38f, -6f).forEachIndexed { i, degrees ->
            val a = Math.toRadians(degrees.toDouble())
            val tip = Offset(top.x + (cos(a) * len).toFloat(), top.y + (sin(a) * len).toFloat() + len * 0.38f)
            val mid = Offset((top.x + tip.x) / 2f, (top.y + tip.y) / 2f - len * 0.32f)
            val frond = Path().apply { moveTo(top.x, top.y); quadraticTo(mid.x, mid.y, tip.x, tip.y) }
            val color = if (i % 2 == 0) colors.frond else colors.frondDark
            drawPath(frond, color.copy(alpha = 0.2f), style = Stroke(height * 0.10f, cap = StrokeCap.Round))
            drawPath(frond, color, style = Stroke(height * 0.03f, cap = StrokeCap.Round))
        }
        drawCircle(colors.coconut, height * 0.035f, Offset(top.x, top.y + height * 0.04f))
    }

    /** A little server cabinet with blinking lights. */
    private fun DrawScope.drawServer(base: Offset, r: Float, colors: IslandColors) {
        val w = r * 0.18f
        val h = r * 0.22f
        val topLeft = Offset(base.x - w / 2f, base.y - h)
        drawRect(colors.thatch.copy(alpha = 0.25f), topLeft - Offset(r * 0.012f, r * 0.012f), Size(w + r * 0.024f, h + r * 0.024f))
        drawRect(colors.wood, topLeft, Size(w, h))
        drawRect(colors.thatch, topLeft, Size(w, h), style = Stroke(r * 0.008f))
        repeat(4) { k ->
            val y = topLeft.y + h * (0.2f + 0.2f * k)
            drawLine(colors.window, Offset(topLeft.x + w * 0.18f, y), Offset(topLeft.x + w * 0.62f, y), strokeWidth = r * 0.012f, cap = StrokeCap.Round)
            drawCircle(if (k % 2 == 0) colors.tower else colors.window, r * 0.008f, Offset(topLeft.x + w * 0.8f, y))
        }
    }

    /** A lattice antenna sending signal waves. */
    private fun DrawScope.drawRadioTower(base: Offset, r: Float, colors: IslandColors) {
        val h = r * 0.66f
        val half = r * 0.06f
        val top = Offset(base.x, base.y - h)
        val stroke = r * 0.012f
        drawLine(colors.tower, Offset(base.x - half, base.y), top, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(colors.tower, Offset(base.x + half, base.y), top, strokeWidth = stroke, cap = StrokeCap.Round)
        repeat(5) { k ->
            val y0 = base.y - h * k / 6f
            val y1 = base.y - h * (k + 1) / 6f
            val w0 = half * (1f - k / 6f)
            val w1 = half * (1f - (k + 1) / 6f)
            drawLine(colors.tower, Offset(base.x - w0, y0), Offset(base.x + w1, y1), strokeWidth = stroke * 0.7f)
        }
        drawCircle(colors.lamp, r * 0.018f, top)
        for (k in 1..3) {
            val rr = r * 0.06f * k
            val arc = colors.lampGlow.copy(alpha = colors.lampGlow.alpha * (1f - 0.25f * k))
            drawArc(arc, -50f, 100f, false, top - Offset(rr, rr), Size(rr * 2f, rr * 2f), style = Stroke(stroke))
            drawArc(arc, 130f, 100f, false, top - Offset(rr, rr), Size(rr * 2f, rr * 2f), style = Stroke(stroke))
        }
    }

    // ------------------------------------------------------------------ Blossom

    /** A cherry tree in bloom: a dark trunk and a round crown of blossom clusters. */
    private fun DrawScope.drawCherryTree(base: Offset, height: Float, lean: Float, colors: IslandColors) {
        val top = Offset(base.x + lean, base.y - height * 0.78f)
        val trunk = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(base.x - lean * 0.3f, base.y - height * 0.45f, top.x, top.y)
        }
        drawPath(trunk, colors.trunk, style = Stroke(height * 0.08f, cap = StrokeCap.Round))
        drawLine(colors.trunk, Offset(base.x + lean * 0.3f, base.y - height * 0.5f), Offset(top.x + height * 0.2f, top.y + height * 0.02f), strokeWidth = height * 0.04f, cap = StrokeCap.Round)
        val clusters = floatArrayOf(
            0f, 0f, .30f, -.30f, .10f, .24f, .30f, .08f, .25f,
            -.12f, -.20f, .24f, .16f, -.18f, .23f, 0f, .14f, .22f
        )
        for (i in clusters.indices step 3) {
            val c = Offset(top.x + clusters[i] * height, top.y + clusters[i + 1] * height)
            drawCircle(if ((i / 3) % 2 == 0) colors.frond else colors.frondDark, clusters[i + 2] * height, c)
        }
        for (k in 0 until 6) {
            val a = k * 1.05f
            drawCircle(colors.coconut, height * 0.025f, Offset(top.x + cos(a) * height * 0.2f, top.y + sin(a) * height * 0.14f))
        }
    }

    /** A small tea house with an upturned roof. */
    private fun DrawScope.drawTeaHouse(base: Offset, r: Float, colors: IslandColors) {
        val w = r * 0.24f
        val h = r * 0.12f
        drawRect(colors.wood, Offset(base.x - w / 2f, base.y - h), Size(w, h))
        drawRect(colors.door, Offset(base.x - w * 0.12f, base.y - h * 0.75f), Size(w * 0.24f, h * 0.75f))
        drawRect(colors.window, Offset(base.x + w * 0.2f, base.y - h * 0.7f), Size(w * 0.18f, h * 0.3f))
        val y0 = base.y - h
        val roof = Path().apply {
            moveTo(base.x - w * 0.85f, y0 - h * 0.35f)
            quadraticTo(base.x - w * 0.62f, y0 + h * 0.05f, base.x - w * 0.5f, y0)
            lineTo(base.x + w * 0.5f, y0)
            quadraticTo(base.x + w * 0.62f, y0 + h * 0.05f, base.x + w * 0.85f, y0 - h * 0.35f)
            lineTo(base.x + w * 0.3f, y0 - h * 1.1f)
            lineTo(base.x - w * 0.3f, y0 - h * 1.1f)
            close()
        }
        drawPath(roof, colors.thatch)
        drawLine(colors.thatch, Offset(base.x - w * 0.36f, y0 - h * 1.2f), Offset(base.x + w * 0.36f, y0 - h * 1.2f), strokeWidth = h * 0.12f, cap = StrokeCap.Round)
    }

    /** A paper lantern hanging from a curved pole; it glows at night. */
    private fun DrawScope.drawLantern(base: Offset, r: Float, colors: IslandColors) {
        val h = r * 0.5f
        val top = Offset(base.x, base.y - h)
        val pole = Path().apply {
            moveTo(base.x, base.y)
            lineTo(top.x, top.y)
            quadraticTo(top.x + r * 0.02f, top.y - r * 0.05f, top.x + r * 0.10f, top.y - r * 0.02f)
        }
        drawPath(pole, colors.tower, style = Stroke(r * 0.014f, cap = StrokeCap.Round))
        val center = Offset(top.x + r * 0.10f, top.y + r * 0.07f)
        if (colors.lampGlow.alpha > 0f) {
            drawCircle(Brush.radialGradient(listOf(colors.lampGlow, colors.lampGlow.copy(alpha = 0f)), center, r * 0.22f), r * 0.22f, center)
        }
        val size = Size(r * 0.09f, r * 0.11f)
        drawOval(colors.lamp, center - Offset(size.width / 2f, size.height / 2f), size)
        repeat(3) { k ->
            val y = center.y - size.height * 0.25f + size.height * 0.25f * k
            drawLine(colors.towerStripe.copy(alpha = 0.6f), Offset(center.x - size.width * 0.42f, y), Offset(center.x + size.width * 0.42f, y), strokeWidth = r * 0.004f)
        }
        drawRect(colors.towerStripe, Offset(center.x - size.width * 0.25f, center.y - size.height * 0.58f), Size(size.width * 0.5f, size.height * 0.12f))
    }

    // ------------------------------------------------------------------ Little Explorer

    /** A treasure chest; [open] shows the gold. */
    private fun DrawScope.drawChest(base: Offset, r: Float, open: Boolean, colors: IslandColors) {
        val w = r * 0.20f
        val h = r * 0.10f
        val line = r * 0.014f
        val body = Offset(base.x - w / 2f, base.y - h)
        if (open) {
            drawCircle(colors.accent2, h * 0.32f, Offset(base.x - w * 0.18f, body.y - h * 0.05f))
            drawCircle(colors.accent2, h * 0.32f, Offset(base.x + w * 0.16f, body.y - h * 0.08f))
            drawCircle(colors.accent2, h * 0.36f, Offset(base.x, body.y - h * 0.2f))
            // The lid stands open behind the gold.
            drawRect(colors.wood, Offset(body.x - w * 0.06f, body.y - h * 1.15f), Size(w * 1.0f, h * 0.35f))
            drawRect(colors.ink, Offset(body.x - w * 0.06f, body.y - h * 1.15f), Size(w * 1.0f, h * 0.35f), style = Stroke(line))
            drawLine(colors.accent2, Offset(base.x - w * 0.3f, body.y - h * 1.6f), Offset(base.x - w * 0.2f, body.y - h * 1.35f), strokeWidth = line, cap = StrokeCap.Round)
            drawLine(colors.accent2, Offset(base.x + w * 0.3f, body.y - h * 1.6f), Offset(base.x + w * 0.2f, body.y - h * 1.35f), strokeWidth = line, cap = StrokeCap.Round)
        } else {
            drawRoundRect(colors.wood, Offset(body.x, body.y - h * 0.55f), Size(w, h * 0.6f), CornerRadius(h * 0.4f))
            drawRoundRect(colors.ink, Offset(body.x, body.y - h * 0.55f), Size(w, h * 0.6f), CornerRadius(h * 0.4f), style = Stroke(line))
        }
        drawRect(colors.wood, body, Size(w, h))
        drawRect(colors.accent2, Offset(body.x + w * 0.15f, body.y), Size(w * 0.1f, h))
        drawRect(colors.accent2, Offset(body.x + w * 0.75f, body.y), Size(w * 0.1f, h))
        drawRect(colors.ink, body, Size(w, h), style = Stroke(line))
        drawRect(colors.accent2, Offset(base.x - w * 0.07f, body.y + h * 0.15f), Size(w * 0.14f, h * 0.3f))
    }

    /** A pirate flag on a pole, waving. */
    private fun DrawScope.drawPirateFlag(base: Offset, r: Float, colors: IslandColors) {
        val h = r * 0.62f
        val top = Offset(base.x, base.y - h)
        drawLine(colors.tower, base, top, strokeWidth = r * 0.016f, cap = StrokeCap.Round)
        val fw = r * 0.26f
        val fh = r * 0.15f
        val flag = Path().apply {
            moveTo(top.x, top.y)
            quadraticTo(top.x + fw * 0.5f, top.y - fh * 0.2f, top.x + fw, top.y + fh * 0.05f)
            lineTo(top.x + fw, top.y + fh * 1.05f)
            quadraticTo(top.x + fw * 0.5f, top.y + fh * 0.8f, top.x, top.y + fh)
            close()
        }
        drawPath(flag, colors.towerStripe)
        val skull = Offset(top.x + fw * 0.5f, top.y + fh * 0.48f)
        drawCircle(colors.lamp, fh * 0.24f, skull)
        drawCircle(colors.towerStripe, fh * 0.06f, skull + Offset(-fh * 0.09f, -fh * 0.02f))
        drawCircle(colors.towerStripe, fh * 0.06f, skull + Offset(fh * 0.09f, -fh * 0.02f))
        drawLine(colors.lamp, skull + Offset(-fh * 0.3f, fh * 0.32f), skull + Offset(fh * 0.3f, fh * 0.12f), strokeWidth = fh * 0.08f, cap = StrokeCap.Round)
        drawLine(colors.lamp, skull + Offset(-fh * 0.3f, fh * 0.12f), skull + Offset(fh * 0.3f, fh * 0.32f), strokeWidth = fh * 0.08f, cap = StrokeCap.Round)
    }

    /** The turtle mascot, swimming beside the island. */
    private fun DrawScope.drawTurtle(center: Offset, r: Float, colors: IslandColors) {
        val line = r * 0.012f
        val shell = Size(r * 0.24f, r * 0.12f)
        drawOval(colors.foam, Offset(center.x - shell.width * 0.7f, center.y + shell.height * 0.15f), Size(shell.width * 1.4f, shell.height * 0.5f), style = Stroke(line))
        listOf(Offset(-0.38f, 0.35f), Offset(0.32f, 0.38f)).forEach { (dx, dy) ->
            val c = Offset(center.x + dx * shell.width, center.y + dy * shell.height)
            drawOval(colors.grassLight, c - Offset(r * 0.03f, r * 0.018f), Size(r * 0.06f, r * 0.036f))
        }
        val head = Offset(center.x + shell.width * 0.58f, center.y - shell.height * 0.05f)
        drawCircle(colors.grassLight, r * 0.045f, head)
        drawCircle(colors.ink, r * 0.045f, head, style = Stroke(line))
        drawCircle(colors.ink, r * 0.01f, head + Offset(r * 0.014f, -r * 0.01f))
        val shellTopLeft = Offset(center.x - shell.width / 2f, center.y - shell.height / 2f)
        drawArc(colors.accent, 180f, 180f, true, shellTopLeft, Size(shell.width, shell.height * 2f))
        drawArc(colors.ink, 180f, 180f, true, shellTopLeft, Size(shell.width, shell.height * 2f), style = Stroke(line))
        drawCircle(colors.grassLight, r * 0.022f, Offset(center.x, center.y + shell.height * 0.15f))
        drawCircle(colors.grassLight, r * 0.016f, Offset(center.x - shell.width * 0.24f, center.y + shell.height * 0.3f))
        drawCircle(colors.grassLight, r * 0.016f, Offset(center.x + shell.width * 0.24f, center.y + shell.height * 0.3f))
    }

    private fun DrawScope.drawHut(base: Offset, r: Float, colors: IslandColors) {
        val w = r * 0.24f
        val h = r * 0.13f
        drawRect(colors.wood, Offset(base.x - w / 2f, base.y - h), Size(w, h))
        drawRect(colors.door, Offset(base.x - w * 0.12f, base.y - h * 0.7f), Size(w * 0.24f, h * 0.7f))
        drawRect(colors.window, Offset(base.x + w * 0.22f, base.y - h * 0.75f), Size(w * 0.16f, h * 0.3f))
        val roof = Path().apply {
            moveTo(base.x - w * 0.68f, base.y - h * 0.92f)
            lineTo(base.x, base.y - h * 2.1f)
            lineTo(base.x + w * 0.68f, base.y - h * 0.92f)
            close()
        }
        drawPath(roof, colors.thatch)
    }

    private fun DrawScope.drawLighthouse(base: Offset, r: Float, colors: IslandColors) {
        val h = r * 0.62f
        val bottom = r * 0.06f
        val top = r * 0.04f
        // Tapered tower with three stripes.
        repeat(4) { band ->
            val y0 = base.y - h * band / 4f
            val y1 = base.y - h * (band + 1) / 4f
            val w0 = bottom + (top - bottom) * band / 4f
            val w1 = bottom + (top - bottom) * (band + 1) / 4f
            val part = Path().apply {
                moveTo(base.x - w0, y0); lineTo(base.x - w1, y1); lineTo(base.x + w1, y1); lineTo(base.x + w0, y0); close()
            }
            drawPath(part, if (band % 2 == 0) colors.tower else colors.towerStripe)
        }
        val lampY = base.y - h - r * 0.045f
        if (colors.lampGlow.alpha > 0f) {
            // Night: the lamp glows and sweeps a beam over the lagoon.
            val beam = Path().apply {
                moveTo(base.x, lampY); lineTo(base.x - r * 1.6f, lampY - r * 0.16f); lineTo(base.x - r * 1.6f, lampY + r * 0.16f); close()
            }
            drawPath(beam, Brush.horizontalGradient(listOf(colors.lampGlow.copy(alpha = 0f), colors.lampGlow), base.x - r * 1.6f, base.x))
            drawCircle(Brush.radialGradient(listOf(colors.lampGlow, colors.lampGlow.copy(alpha = 0f)), Offset(base.x, lampY), r * 0.18f), r * 0.18f, Offset(base.x, lampY))
        }
        drawRect(colors.lamp, Offset(base.x - top * 0.9f, lampY - r * 0.035f), Size(top * 1.8f, r * 0.07f))
        val roof = Path().apply {
            moveTo(base.x - top * 1.3f, lampY - r * 0.035f); lineTo(base.x, lampY - r * 0.11f); lineTo(base.x + top * 1.3f, lampY - r * 0.035f); close()
        }
        drawPath(roof, colors.towerStripe)
    }

    private fun DrawScope.drawPalm(base: Offset, height: Float, lean: Float, colors: IslandColors) {
        val top = Offset(base.x + lean, base.y - height)
        val trunk = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(base.x + lean * 0.1f, base.y - height * 0.55f, top.x, top.y)
        }
        drawPath(trunk, colors.trunk, style = Stroke(height * 0.08f, cap = StrokeCap.Round))
        // Fronds: six leaves arching out and drooping, alternating two greens.
        val angles = floatArrayOf(-172f, -138f, -104f, -76f, -42f, -8f)
        val len = height * 0.62f
        angles.forEachIndexed { i, degrees ->
            val a = Math.toRadians(degrees.toDouble())
            val tip = Offset(top.x + (cos(a) * len).toFloat(), top.y + (sin(a) * len).toFloat() + len * 0.35f)
            val dx = tip.x - top.x
            val dy = tip.y - top.y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val normal = Offset(-dy / d, dx / d)
            val mid = Offset((top.x + tip.x) / 2f, (top.y + tip.y) / 2f - len * 0.28f)
            val leaf = Path().apply {
                moveTo(top.x, top.y)
                quadraticTo(mid.x + normal.x * len * 0.26f, mid.y + normal.y * len * 0.26f, tip.x, tip.y)
                quadraticTo(mid.x - normal.x * len * 0.06f, mid.y - normal.y * len * 0.06f, top.x, top.y)
                close()
            }
            drawPath(leaf, if (i % 2 == 0) colors.frond else colors.frondDark)
        }
        val nut = height * 0.035f
        drawCircle(colors.coconut, nut, Offset(top.x - nut, top.y + nut * 1.2f))
        drawCircle(colors.coconut, nut, Offset(top.x + nut, top.y + nut * 1.4f))
    }
}
