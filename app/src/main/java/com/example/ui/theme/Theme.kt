package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.service.Phase
import com.example.service.PomodoroTimerService
import java.util.Locale

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

/** App-level language override stored under "app_language" in focusflow_prefs. */
enum class AppLanguage(val key: String, val localeTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    FRENCH("fr", "fr");

    companion object {
        const val PREF_KEY = "app_language"
        private const val PREFS_NAME = "focusflow_prefs"

        private val initialSystemLocale: Locale = runCatching {
            val sysConfig = Resources.getSystem().configuration
            sysConfig.locales.takeIf { !it.isEmpty }?.get(0)
        }.getOrNull() ?: Locale.getDefault()

        fun from(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: SYSTEM

        fun read(context: Context): AppLanguage = runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            from(prefs.getString(PREF_KEY, null))
        }.getOrDefault(SYSTEM)

        fun resolveLocale(language: AppLanguage): Locale {
            val tag = language.localeTag
            if (!tag.isNullOrBlank()) {
                return Locale.forLanguageTag(tag)
            }
            val sysLocale = runCatching {
                val sysConfig = Resources.getSystem().configuration
                sysConfig.locales.takeIf { !it.isEmpty }?.get(0)
            }.getOrNull()
            return sysLocale ?: initialSystemLocale
        }

        /**
         * Updates [context]'s Resources configuration in-place and returns a new [Configuration]
         * without replacing the Activity [Context] (so ActivityResultRegistryOwner, Dialog window
         * tokens, and findActivity() remain intact).
         */
        @Suppress("DEPRECATION")
        fun applyLocale(
            context: Context,
            baseConfig: Configuration? = null,
            language: AppLanguage = read(context)
        ): Configuration {
            val locale = resolveLocale(language)
            Locale.setDefault(locale)
            val sourceConfig = baseConfig ?: context.resources.configuration
            val localizedConfig = Configuration(sourceConfig).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            runCatching {
                context.resources.updateConfiguration(
                    Configuration(localizedConfig),
                    context.resources.displayMetrics
                )
                context.applicationContext?.resources?.let { appRes ->
                    val appConfig = Configuration(appRes.configuration).apply {
                        setLocale(locale)
                        setLayoutDirection(locale)
                    }
                    appRes.updateConfiguration(appConfig, appRes.displayMetrics)
                }
            }
            return localizedConfig
        }
    }
}

@Composable
fun FocusIslandTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    style: AppStyle = AppStyle.LAGOON,
    language: AppLanguage = AppLanguage.read(LocalContext.current),
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    FocusIslandTheme(
        darkTheme = dark,
        dynamicColor = dynamicColor,
        style = style,
        language = language,
        content = content
    )
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
    language: AppLanguage = AppLanguage.read(LocalContext.current),
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val localizedConfig = remember(baseContext, baseConfig, language) {
        AppLanguage.applyLocale(baseContext, baseConfig, language)
    }

    val focusStyle = FocusStyles.of(style)
    val target = when {
        style == AppStyle.LAGOON && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(baseContext) else dynamicLightColorScheme(baseContext)
        else -> focusStyle.colors(darkTheme)
    }
    val colors = animateColorScheme(target)
    // Never override LocalContext or LocalDensity here: LocalContext must remain the Activity
    // so ActivityResult launchers, Dialogs, and ModalBottomSheets keep their Activity owner/window.
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
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

/**
 * Draws a subtle fine staggered dot-grain texture paired with a soft radial glow that follows
 * the color of the current Pomodoro cycle ([Phase.FOCUS], [Phase.SHORT_BREAK], [Phase.LONG_BREAK]).
 * Uses [drawWithCache] and batched [drawPoints] so point coordinates are computed only when
 * layout bounds change.
 */
fun Modifier.phaseTextureBackground(
    cycleColor: Color,
    baseColor: Color,
    isDark: Boolean
): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val stepPx = 16.dp.toPx().coerceAtLeast(10f)
    val mainDotDiameter = 2.3.dp.toPx()
    val subDotDiameter = 1.7.dp.toPx()

    val mainPoints = ArrayList<Offset>()
    val subPoints = ArrayList<Offset>()
    if (w > 0f && h > 0f) {
        var row = 0
        var y = stepPx * 0.5f
        val rowStep = stepPx * 0.866f
        while (y < h) {
            val isOddRow = (row and 1) == 1
            val xOffset = if (isOddRow) stepPx * 0.5f else 0f
            val targetList = if (isOddRow) subPoints else mainPoints
            var x = stepPx * 0.5f + xOffset
            while (x < w) {
                targetList.add(Offset(x, y))
                x += stepPx
            }
            y += rowStep
            row++
        }
    }

    val primaryGlowRadius = (maxOf(w, h) * 0.72f).coerceAtLeast(1f)
    val primaryGlowCenter = Offset(w * 0.5f, h * 0.28f)
    val primaryGlowBrush = Brush.radialGradient(
        colors = listOf(
            cycleColor.copy(alpha = if (isDark) 0.19f else 0.14f),
            cycleColor.copy(alpha = if (isDark) 0.07f else 0.05f),
            Color.Transparent
        ),
        center = primaryGlowCenter,
        radius = primaryGlowRadius
    )

    val secondaryGlowRadius = (maxOf(w, h) * 0.55f).coerceAtLeast(1f)
    val secondaryGlowCenter = Offset(w * 0.85f, h * 0.82f)
    val secondaryGlowBrush = Brush.radialGradient(
        colors = listOf(
            cycleColor.copy(alpha = if (isDark) 0.11f else 0.08f),
            Color.Transparent
        ),
        center = secondaryGlowCenter,
        radius = secondaryGlowRadius
    )

    val washColor = cycleColor.copy(alpha = if (isDark) 0.065f else 0.05f)
    val mainDotColor = cycleColor.copy(alpha = if (isDark) 0.17f else 0.145f)
    val subDotColor = cycleColor.copy(alpha = if (isDark) 0.11f else 0.095f)

    onDrawBehind {
        drawRect(color = baseColor)
        if (w <= 0f || h <= 0f) return@onDrawBehind

        drawRect(color = washColor)
        drawCircle(
            brush = primaryGlowBrush,
            radius = primaryGlowRadius,
            center = primaryGlowCenter
        )
        drawCircle(
            brush = secondaryGlowBrush,
            radius = secondaryGlowRadius,
            center = secondaryGlowCenter
        )
        if (mainPoints.isNotEmpty()) {
            drawPoints(
                points = mainPoints,
                pointMode = PointMode.Points,
                color = mainDotColor,
                strokeWidth = mainDotDiameter,
                cap = StrokeCap.Round
            )
        }
        if (subPoints.isNotEmpty()) {
            drawPoints(
                points = subPoints,
                pointMode = PointMode.Points,
                color = subDotColor,
                strokeWidth = subDotDiameter,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Full-screen container that automatically observes the current Pomodoro phase
 * (Focus, Short Break, Long Break) and renders the cycle-colored fine dot-grain texture behind [content].
 */
@Composable
fun PhaseTexturedBackground(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.background,
    isDark: Boolean = LocalIsDarkTheme.current,
    content: @Composable BoxScope.() -> Unit
) {
    val timerState by PomodoroTimerService.state.collectAsState()
    val targetCycleColor = phaseColor(timerState.phase)
    val animatedCycleColor by animateColorAsState(
        targetValue = targetCycleColor,
        animationSpec = tween(600),
        label = "cycleTextureColor"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .phaseTextureBackground(
                cycleColor = animatedCycleColor,
                baseColor = baseColor,
                isDark = isDark
            ),
        content = content
    )
}


