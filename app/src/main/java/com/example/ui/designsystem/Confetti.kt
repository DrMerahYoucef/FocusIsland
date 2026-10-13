package com.example.ui.designsystem

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.ui.theme.extendedColors
import kotlin.math.sin
import kotlin.random.Random

private class Piece(val x: Float, val delay: Float, val speed: Float, val sway: Float, val spin: Float, val color: Color, val wide: Boolean)

/**
 * Celebration: confetti falls across the screen once each time [trigger] changes to a new
 * non-null value. Decorative only (the success message carries the meaning) and skipped when
 * the user turned animations off.
 */
@Composable
fun Confetti(trigger: Any?, modifier: Modifier = Modifier, durationMs: Int = 3_000) {
    if (trigger == null) return
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (animationsOff) return

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.extendedColors.success,
        MaterialTheme.colorScheme.primaryContainer
    )
    val pieces = remember(trigger) {
        val random = Random(trigger.hashCode())
        List(140) {
            Piece(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.7f + random.nextFloat() * 0.6f,
                sway = random.nextFloat() * 6.28f,
                spin = 180f + random.nextFloat() * 540f,
                color = palette[random.nextInt(palette.size)],
                wide = random.nextBoolean()
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    val pieceSize = with(LocalDensity.current) { Size(10.dp.toPx(), 5.dp.toPx()) }

    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -pieceSize.width + local * p.speed * size.height * 1.15f
            val x = p.x * size.width + sin(p.sway + local * 9f) * pieceSize.width * 2.5f
            val alpha = if (local > 0.85f) (1f - local) / 0.15f else 1f
            val s = if (p.wide) pieceSize else Size(pieceSize.height * 1.4f, pieceSize.height * 1.4f)
            rotate(p.spin * local, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - s.width / 2f, y - s.height / 2f), s)
            }
        }
    }
}
