package com.example.ui.designsystem

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.Spacing
import com.example.ui.theme.TimerDisplayStyle

/** Light, dark and 150 % font previews for every design-system component and screen. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Large font", showBackground = true, fontScale = 1.5f)
annotation class ThemePreviews

/** Section title with an optional trailing action (e.g. "See all"). Announced as a heading. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        action?.invoke()
    }
}

/** Centered illustration + message + optional call to action, for empty lists and first runs. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(Spacing.illustration)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(Spacing.xxl))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xs))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Compact metric card: icon, big value, label. Read by TalkBack as "value label". */
@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Spacing.iconSm))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

enum class BannerTone { Info, Warning, Error }

/** Inline, non-blocking message with an optional action (permissions, offline, errors). */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val (container, content, icon) = when (tone) {
        BannerTone.Info -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Rounded.Info)
        BannerTone.Warning -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Icons.Rounded.Warning)
        BannerTone.Error -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Rounded.Warning)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Spacing.iconSm))
            Spacer(Modifier.width(Spacing.sm))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = Spacing.xs))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = content) }
            }
        }
    }
}

/** Standard confirmation dialog. [destructive] colours the confirm button with the error role. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (destructive) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(confirmLabel) }
            } else {
                FilledTonalButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

/**
 * The timer's digits, sized from the ring they sit in instead of the user's text size, so they
 * always fit inside it: at 200 % text, in landscape and on small screens.
 */
@Composable
fun RingClock(time: String, color: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Tabular digits are about 0.6 em wide; the text takes about 60 % of the ring's width.
        val fontSize = with(LocalDensity.current) { (maxWidth * 0.6f / (time.length * 0.6f)).toSp() }
        Text(
            time,
            style = TimerDisplayStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.1f),
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/**
 * Circular progress ring used by the focus timer. [progress] is the fraction remaining (1 → 0).
 * Content (time, phase) is drawn in the centre.
 */
@Composable
fun PhaseRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "phaseRing")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color, startAngle = -90f, sweepAngle = 360f * animated, useCenter = false, style = stroke)
        }
        content()
    }
}

// ---------------------------------------------------------------- previews

@ThemePreviews
@Composable
private fun ComponentsPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionHeader("Today") { TextButton(onClick = {}) { Text("See all") } }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile("Focus minutes", "125", Icons.Rounded.Style, Modifier.weight(1f))
                    StatTile("Day streak", "6", Icons.Rounded.LocalFireDepartment, Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
                }
                InfoBanner("Allow Do Not Disturb access to silence notifications while you focus.", tone = BannerTone.Warning, actionLabel = "Allow", onAction = {})
                PhaseRing(progress = 0.62f, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(220.dp)) {
                    RingClock("15:32", MaterialTheme.colorScheme.onSurface)
                }
                EmptyState(Icons.Rounded.Style, "No cards yet", "Add your first card to start reviewing.", actionLabel = "New card", onAction = {})
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ConfirmDialogPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        ConfirmDialog(
            title = "Delete deck?",
            body = "“Biology” and its 24 cards will be permanently deleted.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {},
            onDismiss = {},
            destructive = true
        )
    }
}
