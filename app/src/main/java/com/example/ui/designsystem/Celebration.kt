package com.example.ui.designsystem

import android.content.Context
import android.media.MediaPlayer
import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CelebrationKind
import com.example.ui.theme.LocalFocusStyle
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.extendedColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private class Piece(val x: Float, val delay: Float, val speed: Float, val sway: Float, val spin: Float, val color: Color, val variant: Boolean)

private const val PIECES = 140
private const val BALLOONS = 9

/**
 * Celebration in the current style's way (confetti, pixels with "LEVEL UP", petals and hearts,
 * stars and balloons), played once each time [trigger] changes to a new non-null value.
 * Decorative only (the message next to it carries the meaning, so it's hidden from TalkBack) and
 * skipped when the user turned animations off.
 */
@Composable
fun Celebration(
    trigger: Any?,
    modifier: Modifier = Modifier,
    kind: CelebrationKind = LocalFocusStyle.current.celebration
) {
    if (trigger == null) return
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (animationsOff) return

    val durationMs = if (kind == CelebrationKind.PETALS) 4_500 else 3_000
    val scheme = MaterialTheme.colorScheme
    val island = LocalFocusStyle.current.island(LocalIsDarkTheme.current)
    val palette = when (kind) {
        CelebrationKind.CONFETTI -> listOf(scheme.primary, scheme.secondary, scheme.tertiary, MaterialTheme.extendedColors.success, scheme.primaryContainer)
        CelebrationKind.PIXELS -> listOf(scheme.primary, scheme.secondary, scheme.tertiary)
        // Blossom: the island's own blossom pinks, with lilac.
        CelebrationKind.PETALS -> listOf(island.accent, island.frond, island.frondDark, scheme.primary, scheme.secondary)
        // Little Explorer: bright toy colours (gold, sun, sky, grass, roof red).
        CelebrationKind.STARS -> listOf(island.accent2, island.sunOrMoon, scheme.primary, island.grass, island.thatch)
    }
    val pieces = remember(trigger, kind) {
        val random = Random(trigger.hashCode())
        List(PIECES) {
            Piece(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.7f + random.nextFloat() * 0.6f,
                sway = random.nextFloat() * 6.28f,
                spin = 180f + random.nextFloat() * 540f,
                color = palette[random.nextInt(palette.size)],
                variant = random.nextInt(5) == 0
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    val unit = with(LocalDensity.current) { 1.dp.toPx() }
    val star = remember { starPath() }
    val heart = remember { heartPath() }
    val balloonString = MaterialTheme.colorScheme.outline

    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            if (t >= 1f) return@Canvas
            when (kind) {
                CelebrationKind.CONFETTI -> drawConfetti(pieces, t, unit)
                CelebrationKind.PIXELS -> drawPixels(pieces, t, unit)
                CelebrationKind.PETALS -> drawPetals(pieces, t, unit, heart)
                CelebrationKind.STARS -> {
                    drawStars(pieces, t, unit, star)
                    drawBalloons(pieces, t, unit, balloonString)
                }
            }
        }
        if (kind == CelebrationKind.PIXELS && progress.value < 0.8f) {
            // Pops in, holds, then fades.
            val t = progress.value
            val scale = if (t < 0.1f) 0.6f + 4f * t else 1f
            val alpha = if (t > 0.65f) ((0.8f - t) / 0.15f).coerceIn(0f, 1f) else 1f
            Text(
                stringResource(R.string.celebration_level_up),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            )
        }
    }
}

private fun local(p: Piece, t: Float) = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
private fun fade(local: Float) = if (local > 0.85f) (1f - local) / 0.15f else 1f

private fun DrawScope.drawConfetti(pieces: List<Piece>, t: Float, unit: Float) {
    val wide = Size(10f * unit, 5f * unit)
    val square = Size(7f * unit, 7f * unit)
    pieces.forEach { p ->
        val l = local(p, t)
        if (l <= 0f) return@forEach
        val y = -wide.width + l * p.speed * size.height * 1.15f
        val x = p.x * size.width + sin(p.sway + l * 9f) * wide.width * 2.5f
        val s = if (p.variant) square else wide
        rotate(p.spin * l, Offset(x, y)) {
            drawRect(p.color.copy(alpha = fade(l)), Offset(x - s.width / 2f, y - s.height / 2f), s)
        }
    }
}

/** Night Arcade: square pixels falling on a grid, in steps. */
private fun DrawScope.drawPixels(pieces: List<Piece>, t: Float, unit: Float) {
    val px = 8f * unit
    pieces.forEach { p ->
        val l = local(p, t)
        if (l <= 0f) return@forEach
        val y = floor((-px + l * p.speed * size.height * 1.15f) / px) * px
        val x = floor((p.x * size.width) / px) * px
        val s = if (p.variant) px * 2f else px
        drawRect(p.color.copy(alpha = fade(l)), Offset(x, y), Size(s, s))
    }
}

/** Blossom: petals drifting and turning slowly, with a few hearts. */
private fun DrawScope.drawPetals(pieces: List<Piece>, t: Float, unit: Float, heart: Path) {
    val petal = Size(12f * unit, 7f * unit)
    pieces.forEach { p ->
        val l = local(p, t)
        if (l <= 0f) return@forEach
        val y = -petal.width + l * p.speed * size.height * 1.1f
        val x = p.x * size.width + sin(p.sway + l * 5f) * petal.width * 4f
        val color = p.color.copy(alpha = fade(l) * 0.9f)
        if (p.variant) {
            val s = 14f * unit
            withTransform({ translate(x - s / 2f, y - s / 2f); scale(s, s, Offset.Zero) }) { drawPath(heart, color) }
        } else {
            rotate(p.spin * 0.4f * l, Offset(x, y)) {
                drawOval(color, Offset(x - petal.width / 2f, y - petal.height / 2f), petal)
            }
        }
    }
}

/** Little Explorer: stars tumbling down. */
private fun DrawScope.drawStars(pieces: List<Piece>, t: Float, unit: Float, star: Path) {
    pieces.forEachIndexed { i, p ->
        if (i % 2 == 1) return@forEachIndexed // half as many: stars are bigger
        val l = local(p, t)
        if (l <= 0f) return@forEachIndexed
        val s = (if (p.variant) 22f else 14f) * unit
        val y = -s + l * p.speed * size.height * 1.15f
        val x = p.x * size.width + sin(p.sway + l * 7f) * s
        withTransform({
            rotate(p.spin * 0.5f * l, Offset(x, y))
            translate(x - s / 2f, y - s / 2f)
            scale(s, s, Offset.Zero)
        }) { drawPath(star, p.color.copy(alpha = fade(l))) }
    }
}

/** Little Explorer: balloons floating up from the bottom. */
private fun DrawScope.drawBalloons(pieces: List<Piece>, t: Float, unit: Float, string: Color) {
    val r = 18f * unit
    for (i in 0 until BALLOONS) {
        val p = pieces[i * 7]
        val l = local(p, t)
        if (l <= 0f) continue
        val x = (0.08f + 0.84f * i / (BALLOONS - 1)) * size.width + sin(p.sway + l * 4f) * r
        val y = size.height + r * 2f - l * p.speed * (size.height + r * 4f)
        val alpha = fade(l)
        drawLine(string.copy(alpha = alpha), Offset(x, y + r * 1.2f), Offset(x + sin(l * 6f) * r * 0.3f, y + r * 3f), strokeWidth = unit, cap = StrokeCap.Round)
        drawOval(p.color.copy(alpha = alpha), Offset(x - r, y - r * 1.2f), Size(r * 2f, r * 2.4f))
        drawCircle(Color.White.copy(alpha = alpha * 0.35f), r * 0.25f, Offset(x - r * 0.4f, y - r * 0.55f))
    }
}

/** Five-pointed star in a 1 × 1 box. */
private fun starPath() = Path().apply {
    for (k in 0 until 10) {
        val radius = if (k % 2 == 0) 0.5f else 0.21f
        val a = -PI / 2 + k * PI / 5
        val x = 0.5f + (cos(a) * radius).toFloat()
        val y = 0.52f + (sin(a) * radius).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Heart in a 1 × 1 box. */
private fun heartPath() = Path().apply {
    moveTo(0.5f, 0.92f)
    cubicTo(0.1f, 0.65f, 0f, 0.38f, 0.12f, 0.2f)
    cubicTo(0.26f, 0.02f, 0.46f, 0.08f, 0.5f, 0.28f)
    cubicTo(0.54f, 0.08f, 0.74f, 0.02f, 0.88f, 0.2f)
    cubicTo(1f, 0.38f, 0.9f, 0.65f, 0.5f, 0.92f)
    close()
}

/** Plays the style's short success sound once (media volume); silent if the device can't. */
fun playSuccessSound(context: Context, @RawRes sound: Int) {
    runCatching {
        MediaPlayer.create(context, sound)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
}
