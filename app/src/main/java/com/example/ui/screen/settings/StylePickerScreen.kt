package com.example.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.example.R
import com.example.ui.components.IslandRenderer
import com.example.ui.components.IslandRenderer.drawIsland
import com.example.ui.components.IslandRenderer.drawSkyAndSea
import com.example.ui.theme.AppStyle
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.FocusStyles
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.LocalFocusStyle
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode

/** Sessions shown on the preview islands, so every style shows all its decorations. */
private const val SHOWCASE_SESSIONS = 30

/**
 * Settings → Appearance → Style: one card per style, each drawn in its own colours, fonts and
 * shapes, with its island. Tapping a card applies the style at once (the whole app fades to it).
 */
@Composable
fun StylePickerScreen(current: AppStyle, onSelect: (AppStyle) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.selectableGroup(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.style_screen_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.style_icon_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(AppStyle.entries, key = { it.key }) { style ->
            StyleCard(style = style, selected = style == current, onClick = { onSelect(style) })
        }
    }
}

/** A card drawn entirely in [style], in the current light/dark mode. */
@Composable
private fun StyleCard(style: AppStyle, selected: Boolean, onClick: () -> Unit) {
    val dark = LocalIsDarkTheme.current
    val focusStyle = FocusStyles.of(style)
    val name = stringResource(style.label)
    val hint = stringResource(style.hint)
    val selectedLabel = stringResource(R.string.style_selected)
    MaterialTheme(colorScheme = focusStyle.colors(dark), typography = focusStyle.typography, shapes = focusStyle.shapes) {
        CompositionLocalProvider(LocalFocusStyle provides focusStyle, LocalExtendedColors provides focusStyle.extended(dark)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (selected) BorderStroke(Spacing.selectedBorder, MaterialTheme.colorScheme.primary)
                else BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (selected) "$name. $hint. $selectedLabel" else "$name. $hint"
                    }
            ) {
                Column {
                    val island = focusStyle.island(dark)
                    Canvas(Modifier.fillMaxWidth().height(Spacing.stylePreviewHeight)) {
                        val horizon = size.height * 0.42f
                        drawSkyAndSea(horizon, Offset(size.width * 0.82f, size.height * 0.2f), size.height * 0.09f, island)
                        val r = minOf(size.width * 0.28f, size.height * 0.62f)
                        drawIsland(Offset(size.width / 2f, size.height * 0.84f), IslandRenderer.radius(r, SHOWCASE_SESSIONS), SHOWCASE_SESSIONS, island)
                    }
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleLarge)
                                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Spacing.icon))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text(
                                "25:00",
                                style = focusStyle.timerText.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize, lineHeight = MaterialTheme.typography.displaySmall.lineHeight),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Spacer(Modifier.weight(1f))
                            // Looks like the style's main button; the whole card is what you tap.
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(Spacing.iconSm))
                                Spacer(Modifier.size(Spacing.xxs))
                                Text(
                                    stringResource(R.string.style_sample_start),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            ).forEach { color ->
                                Box(Modifier.size(Spacing.swatch).clip(CircleShape).background(color))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 1400)
@Preview(name = "Dark", showBackground = true, heightDp = 1400, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, heightDp = 1600, fontScale = 1.6f)
@Composable
private fun StylePickerPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM, style = AppStyle.BLOSSOM) {
        Surface { StylePickerScreen(current = AppStyle.BLOSSOM, onSelect = {}) }
    }
}
