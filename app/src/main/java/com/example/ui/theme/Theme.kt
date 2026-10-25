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
 * Returns the number of Pomodoro minutes passed in the current phase.
 */
fun com.example.service.TimerState.pomodoroMinutesPassed(): Int {
    val elapsedMs = (phaseDurationMs - remainingMs).coerceAtLeast(0L)
    return (elapsedMs / 60_000L).toInt().coerceAtLeast(0)
}

/**
 * Fractional progress (0f..1f) within the current Pomodoro minute while active, so stars for each
 * passing minute can softly emerge.
 */
fun com.example.service.TimerState.pomodoroCurrentMinuteFraction(): Float {
    val elapsedMs = (phaseDurationMs - remainingMs).coerceAtLeast(0L)
    if (elapsedMs <= 0L && !isRunning) return 0f
    return ((elapsedMs % 60_000L).toFloat() / 60_000f).coerceIn(0.15f, 1f)
}

private data class DispersedStar(
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val alphaWeight: Float,
    val isSparkle: Boolean,
    val minuteIndex: Int
)

/** Low-discrepancy Van der Corput / Halton coordinate in (0f, 1f) for organic star dispersion. */
private fun haltonCoordinate(index: Int, base: Int): Float {
    var result = 0f
    var f = 1f / base
    var i = index
    while (i > 0) {
        result += f * (i % base)
        i /= base
        f /= base
    }
    return result
}

private const val STARS_PER_MINUTE = 5
private const val MAX_STAR_MINUTES = 120

/**
 * Draws a clean dark or light background ([baseColor]) where faint, organically dispersed stars
 * appear with every Pomodoro minute passed ([minutesPassed]), colored according to the current
 * timer phase ([cycleColor]: Focus, Short Break / Courte pause, or Long Break / Longue pause).
 */
fun Modifier.phaseTextureBackground(
    cycleColor: Color,
    baseColor: Color,
    isDark: Boolean,
    minutesPassed: Int = 0,
    currentMinuteFraction: Float = 0f
): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val clampedMinutes = minutesPassed.coerceIn(0, MAX_STAR_MINUTES)
    val totalMinutesToGenerate = (clampedMinutes + if (currentMinuteFraction > 0f) 1 else 0)
        .coerceIn(0, MAX_STAR_MINUTES)

    val stars = ArrayList<DispersedStar>(totalMinutesToGenerate * STARS_PER_MINUTE)
    if (w > 0f && h > 0f && totalMinutesToGenerate > 0) {
        val marginX = w * 0.04f
        val marginY = h * 0.04f
        val usableW = (w - marginX * 2f).coerceAtLeast(1f)
        val usableH = (h - marginY * 2f).coerceAtLeast(1f)

        val sparkleRadius = 2.4.dp.toPx()
        val mediumRadius = 1.7.dp.toPx()
        val smallRadius = 1.2.dp.toPx()

        for (minute in 0 until totalMinutesToGenerate) {
            for (s in 0 until STARS_PER_MINUTE) {
                // Offset seed by 7 so initial points are well inside the canvas
                val seqIndex = minute * STARS_PER_MINUTE + s + 7
                val nx = haltonCoordinate(seqIndex, 2)
                val ny = haltonCoordinate(seqIndex, 3)
                val x = marginX + nx * usableW
                val y = marginY + ny * usableH
                val isSparkle = (s == 0)
                val radius = when {
                    isSparkle -> sparkleRadius
                    s % 2 == 1 -> mediumRadius
                    else -> smallRadius
                }
                val alphaWeight = when {
                    isSparkle -> 1.0f
                    s % 2 == 1 -> 0.78f
                    else -> 0.58f
                }
                stars.add(
                    DispersedStar(
                        x = x,
                        y = y,
                        radiusPx = radius,
                        alphaWeight = alphaWeight,
                        isSparkle = isSparkle,
                        minuteIndex = minute
                    )
                )
            }
        }
    }

    val baseStarAlpha = if (isDark) 0.30f else 0.34f
    val rayStrokePx = 1.1.dp.toPx()

    onDrawBehind {
        // 1. Clean dark or light screen
        drawRect(color = baseColor)
        if (w <= 0f || h <= 0f || stars.isEmpty()) return@onDrawBehind

        // 2. Faint dispersed stars for each Pomodoro minute passed
        for (i in 0 until stars.size) {
            val star = stars[i]
            val minuteVisibility = when {
                star.minuteIndex < clampedMinutes -> 1f
                star.minuteIndex == clampedMinutes -> currentMinuteFraction
                else -> 0f
            }
            if (minuteVisibility <= 0.01f) continue

            val coreAlpha = (baseStarAlpha * star.alphaWeight * minuteVisibility).coerceIn(0f, 1f)
            val haloAlpha = (coreAlpha * 0.28f).coerceIn(0f, 1f)
            val center = Offset(star.x, star.y)

            // Soft faint outer star glow
            drawCircle(
                color = cycleColor.copy(alpha = haloAlpha),
                radius = star.radiusPx * 2.8f,
                center = center
            )

            // Faint 4-point star rays for the primary star of each minute
            if (star.isSparkle) {
                val rayLen = star.radiusPx * 3.4f
                val rayColor = cycleColor.copy(alpha = (coreAlpha * 0.65f).coerceIn(0f, 1f))
                drawLine(
                    color = rayColor,
                    start = Offset(star.x - rayLen, star.y),
                    end = Offset(star.x + rayLen, star.y),
                    strokeWidth = rayStrokePx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = rayColor,
                    start = Offset(star.x, star.y - rayLen),
                    end = Offset(star.x, star.y + rayLen),
                    strokeWidth = rayStrokePx,
                    cap = StrokeCap.Round
                )
            }

            // Star core
            drawCircle(
                color = cycleColor.copy(alpha = coreAlpha),
                radius = star.radiusPx,
                center = center
            )
        }
    }
}

/**
 * Full-screen container that renders a clean dark or light screen and reveals faint dispersed
 * stars with every Pomodoro minute passed, colored according to the current phase
 * (Focus, Short Break / Courte pause, Long Break / Longue pause).
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
    val minutesPassed = timerState.pomodoroMinutesPassed()
    val currentMinuteFraction = timerState.pomodoroCurrentMinuteFraction()

    Box(
        modifier = modifier
            .fillMaxSize()
            .phaseTextureBackground(
                cycleColor = animatedCycleColor,
                baseColor = baseColor,
                isDark = isDark,
                minutesPassed = minutesPassed,
                currentMinuteFraction = currentMinuteFraction
            ),
        content = content
    )
}



