package com.example.ui.screen.timer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import com.example.R
import com.example.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

private const val DAY_MINUTES = 1440f

/**
 * Today on a 24-hour dial around the timer: midnight at the top, 6h right, 12h bottom, 18h left.
 * Each focus session is an arc at the time it happened (dimmer if it ended early), the running
 * session grows live, and a dot marks the current time.
 */
@Composable
fun DayDial(
    blocks: List<DayBlock>,
    liveStartMinute: Float?,
    sessionColor: Color,
    liveColor: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = Spacing.dayDialStroke
) {
    val nowMinute by produceState(minuteOfDay()) {
        while (true) {
            delay(30_000L)
            value = minuteOfDay()
        }
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = tick)
    val labels = listOf(0, 6, 12, 18).map { stringResource(R.string.focus_day_hour, it) }
    val description = pluralStringResource(R.plurals.focus_day_dial_a11y, blocks.count { it.completed }, blocks.count { it.completed })
    val measured = remember(labels, labelStyle) { labels.map { measurer.measure(it, labelStyle) } }

    Canvas(modifier.semantics { contentDescription = description }) {
        val w = stroke.toPx()
        val radius = size.minDimension / 2f - w / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        fun angle(minute: Float) = minute / DAY_MINUTES * 360f - 90f
        fun point(minute: Float, r: Float): Offset {
            val a = Math.toRadians(angle(minute).toDouble())
            return Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat())
        }

        drawCircle(track, radius, center, style = Stroke(w))
        // Hour ticks inside the track, longer every 6 hours, with their labels.
        for (h in 0 until 24) {
            val major = h % 6 == 0
            val inner = radius - w / 2f - if (major) w * 1.6f else w * 0.9f
            drawLine(tick.copy(alpha = if (major) 0.8f else 0.4f), point(h * 60f, radius - w / 2f - w * 0.3f), point(h * 60f, inner), strokeWidth = w * 0.25f)
        }
        measured.forEachIndexed { i, layout ->
            val p = point(i * 360f, radius - w * 3.6f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        blocks.forEach { b ->
            val sweep = ((b.endMinute - b.startMinute) / DAY_MINUTES * 360f).coerceAtLeast(MIN_SWEEP)
            drawArc(sessionColor.copy(alpha = if (b.completed) 1f else 0.45f), angle(b.startMinute), sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Butt))
        }
        liveStartMinute?.let { start ->
            val sweep = ((nowMinute - start) / DAY_MINUTES * 360f).coerceAtLeast(MIN_SWEEP)
            drawArc(liveColor, angle(start), sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Butt))
        }
        val now = point(nowMinute, radius)
        drawCircle(track, w * 0.85f, now)
        drawCircle(marker, w * 0.55f, now)
    }
}

private const val MIN_SWEEP = 1.5f

private fun minuteOfDay(): Float = LocalTime.now().let { it.hour * 60f + it.minute + it.second / 60f }
