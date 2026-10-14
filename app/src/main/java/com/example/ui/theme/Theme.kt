package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.service.Phase

val LocalIsDarkTheme = staticCompositionLocalOf { false }
val LocalExtendedColors = staticCompositionLocalOf { LagoonLightExtended }

/** Legacy colour bundle still read by screens not yet redesigned. Derived from the M3 scheme. */
data class AppThemeColors(
    val surface: Color,
    val onSurface: Color,
    val secondaryText: Color,
    val accent: Color,
    val inputBackground: Color,
    val divider: Color,
    val iconTint: Color
)

val LocalAppThemeColors = staticCompositionLocalOf { LagoonLightColors.toAppThemeColors() }

private fun ColorScheme.toAppThemeColors() = AppThemeColors(
    surface = surfaceContainerLow,
    onSurface = onSurface,
    secondaryText = onSurfaceVariant,
    accent = primary,
    inputBackground = surfaceContainerHighest,
    divider = outlineVariant,
    iconTint = onSurfaceVariant
)

/** User choice stored under the existing "theme_mode" preference. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

@Composable
fun FocusIslandTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    style: AppStyle = AppStyle.LAGOON,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    FocusIslandTheme(darkTheme = dark, dynamicColor = dynamicColor, style = style, content = content)
}

/**
 * The app theme in [style]. Wallpaper colours (Material You, [dynamicColor]) only apply to Lagoon:
 * the other styles are their colours. Switching style or light/dark fades the colours over
 * [STYLE_FADE_MS]; fonts and shapes switch at once.
 */
@Composable
fun FocusIslandTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    style: AppStyle = AppStyle.LAGOON,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val focusStyle = FocusStyles.of(style)
    val target = when {
        style == AppStyle.LAGOON && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> focusStyle.colors(darkTheme)
    }
    val colors = animateColorScheme(target)
    // The user's font size is always respected (WCAG 1.4.4): never override LocalDensity here.
    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalFocusStyle provides focusStyle,
        LocalExtendedColors provides focusStyle.extended(darkTheme),
        LocalAppThemeColors provides colors.toAppThemeColors()
    ) {
        MaterialTheme(colorScheme = colors, typography = focusStyle.typography, shapes = focusStyle.shapes, content = content)
    }
}

private const val STYLE_FADE_MS = 450

@Composable
private fun animate(color: Color): Color = animateColorAsState(color, tween(STYLE_FADE_MS), label = "theme").value

/** [target] with every colour fading from the previous scheme (style or light/dark switch). */
@Composable
private fun animateColorScheme(target: ColorScheme) = target.copy(
    primary = animate(target.primary),
    onPrimary = animate(target.onPrimary),
    primaryContainer = animate(target.primaryContainer),
    onPrimaryContainer = animate(target.onPrimaryContainer),
    inversePrimary = animate(target.inversePrimary),
    secondary = animate(target.secondary),
    onSecondary = animate(target.onSecondary),
    secondaryContainer = animate(target.secondaryContainer),
    onSecondaryContainer = animate(target.onSecondaryContainer),
    tertiary = animate(target.tertiary),
    onTertiary = animate(target.onTertiary),
    tertiaryContainer = animate(target.tertiaryContainer),
    onTertiaryContainer = animate(target.onTertiaryContainer),
    background = animate(target.background),
    onBackground = animate(target.onBackground),
    surface = animate(target.surface),
    onSurface = animate(target.onSurface),
    surfaceVariant = animate(target.surfaceVariant),
    onSurfaceVariant = animate(target.onSurfaceVariant),
    surfaceTint = animate(target.surfaceTint),
    inverseSurface = animate(target.inverseSurface),
    inverseOnSurface = animate(target.inverseOnSurface),
    outline = animate(target.outline),
    outlineVariant = animate(target.outlineVariant),
    surfaceBright = animate(target.surfaceBright),
    surfaceDim = animate(target.surfaceDim),
    surfaceContainerLowest = animate(target.surfaceContainerLowest),
    surfaceContainerLow = animate(target.surfaceContainerLow),
    surfaceContainer = animate(target.surfaceContainer),
    surfaceContainerHigh = animate(target.surfaceContainerHigh),
    surfaceContainerHighest = animate(target.surfaceContainerHighest)
)

val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

/** Focus = primary, short break = secondary (sand), long break = rest (blue). */
@Composable
@ReadOnlyComposable
fun phaseColor(phase: Phase): Color = when (phase) {
    Phase.FOCUS -> MaterialTheme.colorScheme.primary
    Phase.SHORT_BREAK -> MaterialTheme.colorScheme.secondary
    Phase.LONG_BREAK -> LocalExtendedColors.current.rest
}
