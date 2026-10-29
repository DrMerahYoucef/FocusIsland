package com.example.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.R
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode

/** A titled group of rows on one tonal card. Rows are separated by thin dividers. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title, modifier = Modifier.padding(horizontal = Spacing.xxs))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) { Column { content() } }
    }
}

@Composable
fun SettingsDivider() = HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant,
    modifier = Modifier.padding(start = Spacing.md + Spacing.icon + Spacing.md)
)

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Tappable row: icon, title, optional summary and trailing content. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        colors = rowColors,
        modifier = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    )
}

/** Whole row toggles the switch (one TalkBack stop, announced as a switch). */
@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = rowColors,
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    )
}

/** Number with − / + buttons (48 dp targets). The value is announced when it changes. */
@Composable
fun StepperRow(
    icon: ImageVector,
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
    summary: String? = null
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, enabled = canDecrease) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.settings_decrease, title))
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = Spacing.xxl + Spacing.sm)
                        .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "$title $value" }
                )
                IconButton(onClick = onIncrease, enabled = canIncrease) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.settings_increase, title))
                }
            }
        },
        colors = rowColors
    )
}

@ThemePreviews
@Composable
private fun SettingsComponentsPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SettingsGroup("Focus timer") {
                StepperRow(Icons.Rounded.Timer, "Focus", "25 min", {}, {}, canDecrease = true, canIncrease = true)
                SettingsDivider()
                SwitchRow(Icons.Rounded.Vibration, "Vibrate", "When a focus or break ends", checked = true, onCheckedChange = {})
            }
        }
    }
}
