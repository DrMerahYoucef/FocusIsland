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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.service.Phase
import com.example.service.PomodoroTimerService
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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
 * Fractional progress (0f..1f) within the current Pomodoro minute while active, so each passing
 * minute's background element can smoothly emerge and extend.
 */
fun com.example.service.TimerState.pomodoroCurrentMinuteFraction(): Float {
    val elapsedMs = (phaseDurationMs - remainingMs).coerceAtLeast(0L)
    if (elapsedMs <= 0L && !isRunning) return 0f
    return ((elapsedMs % 60_000L).toFloat() / 60_000f).coerceIn(0.18f, 1f)
}

private const val MAX_POMODORO_MINUTES = 60

/**
 * Holds the live-measured root coordinates and radius of the active Pomodoro timer circle so
 * [PhaseTexturedBackground] can center every style's artwork with pixel precision around the dial.
 */
@Stable
class TimerCircleBounds {
    var backgroundRootOffset by mutableStateOf(Offset.Zero)
    var circleCenterInRoot by mutableStateOf<Offset?>(null)
    var circleRadiusPx by mutableFloatStateOf(0f)

    val localCenter: Offset?
        get() = circleCenterInRoot?.let { it - backgroundRootOffset }
}

val LocalTimerCircleBounds = compositionLocalOf { TimerCircleBounds() }

/**
 * Attaches to the Pomodoro timer circle container (`TimerDial` / `TimerRing`) to report its exact
 * center and outer radius to [LocalTimerCircleBounds].
 */
@Composable
fun Modifier.anchorTimerCircleBounds(): Modifier {
    val bounds = LocalTimerCircleBounds.current
    return this.onGloballyPositioned { coords ->
        if (coords.isAttached) {
            val pos = coords.positionInRoot()
            val w = coords.size.width.toFloat()
            val h = coords.size.height.toFloat()
            if (w > 0f && h > 0f) {
                bounds.circleCenterInRoot = Offset(pos.x + w * 0.5f, pos.y + h * 0.5f)
                bounds.circleRadiusPx = min(w, h) * 0.5f
            }
        }
    }
}

/**
 * Draws a clean dark or light screen ([baseColor]) with bespoke, style-specific progressive
 * artwork centered directly on the Pomodoro timer circle ([circleCenterOverride], [circleRadiusOverride])
 * that lines up / grows with every Pomodoro minute passed ([minutesPassed]), colored
 * according to the current timer phase ([cycleColor]):
 * - [AppStyle.ARCADE]: Motherboard PCB circuit traces radiating out of the timer circle with 45°/90° bends and solder vias.
 * - [AppStyle.BLOSSOM]: Delicate sakura branches and 5-petal flowers crowning the top arc of the timer circle.
 * - [AppStyle.LAGOON]: Concentric water ripples expanding from the timer circle with rising lagoon bubbles around its perimeter.
 * - [AppStyle.EXPLORER]: Winding dashed treasure-map trail with compass rings and charted miniature islands orbiting the timer circle.
 */
fun Modifier.phaseTextureBackground(
    cycleColor: Color,
    baseColor: Color,
    isDark: Boolean,
    style: AppStyle = AppStyle.LAGOON,
    minutesPassed: Int = 0,
    currentMinuteFraction: Float = 0f,
    circleCenterOverride: Offset? = null,
    circleRadiusOverride: Float = 0f
): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val clampedMinutes = minutesPassed.coerceIn(0, MAX_POMODORO_MINUTES)
    val baseAlpha = if (isDark) 0.36f else 0.38f

    // Fallback center/radius only before first layout pass; once measured, uses exact pixel bounds
    val isLandscape = w > h
    val defaultCenter = if (isLandscape) {
        Offset(w * 0.25f, h * 0.50f)
    } else {
        Offset(w * 0.50f, h * 0.42f)
    }
    val defaultRadius = min(w, h) * (if (isLandscape) 0.30f else 0.31f)

    val circleCenter = circleCenterOverride?.takeIf {
        it.x in 0f..w && it.y in 0f..h
    } ?: defaultCenter
    val circleRadius = circleRadiusOverride.takeIf { it > 10f } ?: defaultRadius

    onDrawBehind {
        // 1. Clean dark or light screen
        drawRect(color = baseColor)
        if (w <= 0f || h <= 0f) return@onDrawBehind

        // Include 2 subtle starter slots so the style motif is immediately recognizable,
        // then grow/line up one new element per Pomodoro minute passed.
        val starterCount = 2
        val effectiveCompleted = starterCount + clampedMinutes
        val activeFraction = if (currentMinuteFraction > 0f) currentMinuteFraction else 0.45f

        when (style) {
            AppStyle.ARCADE -> drawArcadeMotherboard(
                center = circleCenter,
                innerRadius = circleRadius,
                color = cycleColor,
                baseAlpha = baseAlpha,
                completedCount = effectiveCompleted,
                activeFraction = activeFraction
            )
            AppStyle.BLOSSOM -> drawBlossomCircleCrown(
                center = circleCenter,
                innerRadius = circleRadius,
                color = cycleColor,
                baseAlpha = baseAlpha,
                completedCount = effectiveCompleted,
                activeFraction = activeFraction
            )
            AppStyle.LAGOON -> drawLagoonRipplesAndBubbles(
                center = circleCenter,
                innerRadius = circleRadius,
                color = cycleColor,
                baseAlpha = baseAlpha,
                completedCount = effectiveCompleted,
                activeFraction = activeFraction
            )
            AppStyle.EXPLORER -> drawExplorerMapTrail(
                center = circleCenter,
                innerRadius = circleRadius,
                color = cycleColor,
                baseAlpha = baseAlpha,
                completedCount = effectiveCompleted,
                activeFraction = activeFraction
            )
        }
    }
}

/**
 * 1. NIGHT ARCADE: Motherboard-like PCB lines going out of the exact Pomodoro timer circle
 * perimeter that line up gradually with each Pomodoro minute passed.
 */
private fun DrawScope.drawArcadeMotherboard(
    center: Offset,
    innerRadius: Float,
    color: Color,
    baseAlpha: Float,
    completedCount: Int,
    activeFraction: Float
) {
    val socketRadius = innerRadius + 4.dp.toPx()
    val maxReach = hypot(size.width, size.height) * 0.44f
    val traceStroke = 1.6.dp.toPx()
    val busStroke = 1.0.dp.toPx()
    val padRadius = 2.8.dp.toPx()
    val viaOuterRadius = 4.2.dp.toPx()
    val viaInnerRadius = 1.8.dp.toPx()

    // Subtle IC socket perimeter ring and microprocessor pin ticks around the exact timer circle
    val ringAlpha = (baseAlpha * 0.42f).coerceIn(0f, 1f)
    drawCircle(
        color = color.copy(alpha = ringAlpha),
        radius = socketRadius,
        center = center,
        style = Stroke(width = 1.1.dp.toPx())
    )

    val totalSlots = 32
    val pinLen = 5.dp.toPx()
    for (slot in 0 until totalSlots) {
        val angleRad = (slot * (360f / totalSlots) - 90f) * (PI.toFloat() / 180f)
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val pStart = Offset(center.x + cosA * socketRadius, center.y + sinA * socketRadius)
        val pEnd = Offset(center.x + cosA * (socketRadius + pinLen), center.y + sinA * (socketRadius + pinLen))
        drawLine(
            color = color.copy(alpha = ringAlpha * 0.85f),
            start = pStart,
            end = pEnd,
            strokeWidth = busStroke,
            cap = StrokeCap.Square
        )
    }

    // Order slots so traces line up sequentially around the circle in balanced PCB bus banks
    val totalTracesToDraw = (completedCount + 1).coerceAtMost(totalSlots)
    for (minuteIdx in 0 until totalTracesToDraw) {
        val progress = when {
            minuteIdx < completedCount -> 1f
            minuteIdx == completedCount -> activeFraction
            else -> 0f
        }
        if (progress <= 0.02f) continue

        // Line up traces around the circle in clock order with grouped bus alignment
        val bank = minuteIdx / 4
        val inBank = minuteIdx % 4
        val baseAngleDeg = -90f + (bank * 45f) + (inBank - 1.5f) * 7.5f
        val angleRad = baseAngleDeg * (PI.toFloat() / 180f)
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        // Segment 1: Radial launch out of the circle IC pad
        val seg1Len = socketRadius * (0.14f + (inBank % 2) * 0.05f)
        val p0 = Offset(center.x + cosA * (socketRadius + pinLen), center.y + sinA * (socketRadius + pinLen))
        val p1 = Offset(p0.x + cosA * seg1Len, p0.y + sinA * seg1Len)

        // Segment 2: 45-degree PCB bend
        val bendSign = if (bank % 2 == 0) 1f else -1f
        val bendAngleRad = (baseAngleDeg + bendSign * 45f) * (PI.toFloat() / 180f)
        val seg2Len = socketRadius * (0.18f + (inBank * 0.04f))
        val p2 = Offset(p1.x + cos(bendAngleRad) * seg2Len, p1.y + sin(bendAngleRad) * seg2Len)

        // Segment 3: Cardinal / orthogonal motherboard bus line outward
        val cardinalAngleDeg = kotlin.math.round(baseAngleDeg / 90f) * 90f
        val cardRad = cardinalAngleDeg * (PI.toFloat() / 180f)
        val seg3Len = (maxReach * (0.20f + ((minuteIdx % 5) * 0.07f))).coerceAtLeast(22.dp.toPx())
        val p3 = Offset(p2.x + cos(cardRad) * seg3Len, p2.y + sin(cardRad) * seg3Len)

        val traceAlpha = (baseAlpha * (0.68f + 0.32f * progress)).coerceIn(0f, 1f)
        val traceColor = color.copy(alpha = traceAlpha)
        val glowColor = color.copy(alpha = (traceAlpha * 0.26f).coerceIn(0f, 1f))

        // IC solder pad at the circle edge
        drawCircle(
            color = traceColor,
            radius = padRadius,
            center = p0
        )

        // Progressive 3-segment line drawing based on `progress`
        val s1Prog = (progress / 0.35f).coerceIn(0f, 1f)
        val s2Prog = ((progress - 0.35f) / 0.30f).coerceIn(0f, 1f)
        val s3Prog = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)

        val curP1 = Offset(p0.x + (p1.x - p0.x) * s1Prog, p0.y + (p1.y - p0.y) * s1Prog)
        drawLine(color = glowColor, start = p0, end = curP1, strokeWidth = traceStroke * 2.6f, cap = StrokeCap.Round)
        drawLine(color = traceColor, start = p0, end = curP1, strokeWidth = traceStroke, cap = StrokeCap.Round)

        if (s2Prog > 0f) {
            val curP2 = Offset(p1.x + (p2.x - p1.x) * s2Prog, p1.y + (p2.y - p1.y) * s2Prog)
            drawLine(color = glowColor, start = p1, end = curP2, strokeWidth = traceStroke * 2.6f, cap = StrokeCap.Round)
            drawLine(color = traceColor, start = p1, end = curP2, strokeWidth = traceStroke, cap = StrokeCap.Round)
            drawCircle(color = traceColor, radius = padRadius * 0.7f, center = p1)
        }

        if (s3Prog > 0f) {
            val curP3 = Offset(p2.x + (p3.x - p2.x) * s3Prog, p2.y + (p3.y - p2.y) * s3Prog)
            drawLine(color = glowColor, start = p2, end = curP3, strokeWidth = traceStroke * 2.6f, cap = StrokeCap.Round)
            drawLine(color = traceColor, start = p2, end = curP3, strokeWidth = traceStroke, cap = StrokeCap.Round)
            drawCircle(color = traceColor, radius = padRadius * 0.7f, center = p2)

            // Secondary parallel companion micro-trace on every second trace
            if (inBank % 2 == 0) {
                val perpX = -sin(cardRad) * 4.5.dp.toPx()
                val perpY = cos(cardRad) * 4.5.dp.toPx()
                val subStart = Offset(p2.x + perpX, p2.y + perpY)
                val subEnd = Offset(curP3.x + perpX, curP3.y + perpY)
                drawLine(
                    color = color.copy(alpha = traceAlpha * 0.55f),
                    start = subStart,
                    end = subEnd,
                    strokeWidth = busStroke,
                    cap = StrokeCap.Round
                )
            }

            // Motherboard solder via (outer ring + inner node) at the trace tip
            if (s3Prog > 0.85f) {
                drawCircle(
                    color = glowColor,
                    radius = viaOuterRadius * 1.6f,
                    center = curP3
                )
                drawCircle(
                    color = traceColor,
                    radius = viaOuterRadius,
                    center = curP3,
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawCircle(
                    color = traceColor,
                    radius = viaInnerRadius,
                    center = curP3
                )
            }
        }
    }
}

/**
 * 2. BLOSSOM: Delicate sakura branches and 5-petal flowers crowning the top arc of the
 * Pomodoro timer circle and blooming symmetrically around its upper perimeter as minutes pass.
 */
private fun DrawScope.drawBlossomCircleCrown(
    center: Offset,
    innerRadius: Float,
    color: Color,
    baseAlpha: Float,
    completedCount: Int,
    activeFraction: Float
) {
    val crownRadius = innerRadius + 10.dp.toPx()
    val outerCrownRadius = innerRadius + 30.dp.toPx()
    val branchStroke = 1.8.dp.toPx()
    val twigStroke = 1.1.dp.toPx()
    val branchAlpha = (baseAlpha * 0.58f).coerceIn(0f, 1f)

    // Graceful sakura vine arcs hugging the top crown of the timer circle (-165° to -15°)
    val crownRect = Rect(
        center.x - crownRadius,
        center.y - crownRadius,
        center.x + crownRadius,
        center.y + crownRadius
    )
    drawArc(
        color = color.copy(alpha = branchAlpha),
        startAngle = -162f,
        sweepAngle = 144f,
        useCenter = false,
        topLeft = crownRect.topLeft,
        size = crownRect.size,
        style = Stroke(width = branchStroke, cap = StrokeCap.Round)
    )

    val outerCrownRect = Rect(
        center.x - outerCrownRadius,
        center.y - outerCrownRadius,
        center.x + outerCrownRadius,
        center.y + outerCrownRadius
    )
    drawArc(
        color = color.copy(alpha = branchAlpha * 0.65f),
        startAngle = -140f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = outerCrownRect.topLeft,
        size = outerCrownRect.size,
        style = Stroke(width = twigStroke, cap = StrokeCap.Round)
    )

    // Symmetric polar coordinates (angleDeg around the top of the circle, radialOffsetDp, scale)
    // Starts at the top center (-90°) and blooms symmetrically left & right along the top of the circle!
    val flowerPolarSpots = listOf(
        Triple(-90f, 10f, 1.08f),
        Triple(-112f, 11f, 0.98f),
        Triple(-68f, 11f, 0.98f),
        Triple(-134f, 12f, 0.92f),
        Triple(-46f, 12f, 0.92f),
        Triple(-101f, 29f, 0.90f),
        Triple(-79f, 29f, 0.90f),
        Triple(-154f, 13f, 0.86f),
        Triple(-26f, 13f, 0.86f),
        Triple(-123f, 30f, 0.85f),
        Triple(-57f, 30f, 0.85f),
        Triple(-90f, 34f, 0.94f),
        Triple(-172f, 14f, 0.80f),
        Triple(-8f, 14f, 0.80f),
        Triple(-144f, 31f, 0.80f),
        Triple(-36f, 31f, 0.80f),
        Triple(-112f, 46f, 0.78f),
        Triple(-68f, 46f, 0.78f),
        Triple(-164f, 32f, 0.75f),
        Triple(-16f, 32f, 0.75f),
        Triple(-134f, 48f, 0.74f),
        Triple(-46f, 48f, 0.74f),
        Triple(-188f, 15f, 0.72f),
        Triple(8f, 15f, 0.72f),
        Triple(-90f, 52f, 0.82f)
    )

    val baseFlowerRadius = 12.5.dp.toPx()
    val totalToDraw = (completedCount + 1).coerceAtMost(flowerPolarSpots.size)

    for (idx in 0 until totalToDraw) {
        val bloomProgress = when {
            idx < completedCount -> 1f
            idx == completedCount -> activeFraction
            else -> 0f
        }
        if (bloomProgress <= 0.02f) continue

        val (angleDeg, radialDp, scale) = flowerPolarSpots[idx]
        val rad = angleDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val dist = innerRadius + radialDp.dp.toPx()
        val flowerCenter = Offset(center.x + cosA * dist, center.y + sinA * dist)
        val anchorOnCircle = Offset(center.x + cosA * crownRadius, center.y + sinA * crownRadius)

        val radius = baseFlowerRadius * scale * (0.45f + 0.55f * bloomProgress)
        val flowerAlpha = (baseAlpha * (0.58f + 0.42f * bloomProgress)).coerceIn(0f, 1f)
        val rotOffsetDeg = angleDeg + 90f + (idx * 18f)

        // Delicate twig connecting outer crown blossoms to the circle's top vine
        if (radialDp > 16f) {
            drawLine(
                color = color.copy(alpha = flowerAlpha * 0.52f),
                start = anchorOnCircle,
                end = flowerCenter,
                strokeWidth = twigStroke,
                cap = StrokeCap.Round
            )
        }

        drawSakuraFlower(
            center = flowerCenter,
            radius = radius,
            rotationDeg = rotOffsetDeg,
            color = color,
            alpha = flowerAlpha
        )

        // Companion bud / petal radiating outward from the blossom
        if (idx % 2 == 1 && bloomProgress > 0.6f) {
            val petalDist = dist + radius * 1.65f
            val petalAngle = (angleDeg + (if (idx % 4 == 1) 5f else -5f)) * (PI.toFloat() / 180f)
            val petalCenter = Offset(
                center.x + cos(petalAngle) * petalDist,
                center.y + sin(petalAngle) * petalDist
            )
            drawCircle(
                color = color.copy(alpha = flowerAlpha * 0.48f),
                radius = radius * 0.24f,
                center = petalCenter
            )
        }
    }
}

private fun DrawScope.drawSakuraFlower(
    center: Offset,
    radius: Float,
    rotationDeg: Float,
    color: Color,
    alpha: Float
) {
    val petalCount = 5
    val petalFill = color.copy(alpha = (alpha * 0.42f).coerceIn(0f, 1f))
    val petalStroke = color.copy(alpha = (alpha * 0.88f).coerceIn(0f, 1f))
    val strokeW = 1.1.dp.toPx()

    for (i in 0 until petalCount) {
        val angleDeg = rotationDeg + i * (360f / petalCount)
        val rad = angleDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val perpX = -sinA
        val perpY = cosA

        val tipDist = radius
        val widthDist = radius * 0.48f
        val notchDist = radius * 0.82f

        val tipLeft = Offset(
            center.x + cosA * tipDist + perpX * (widthDist * 0.32f),
            center.y + sinA * tipDist + perpY * (widthDist * 0.32f)
        )
        val notch = Offset(
            center.x + cosA * notchDist,
            center.y + sinA * notchDist
        )
        val tipRight = Offset(
            center.x + cosA * tipDist - perpX * (widthDist * 0.32f),
            center.y + sinA * tipDist - perpY * (widthDist * 0.32f)
        )
        val ctrlLeft = Offset(
            center.x + cosA * (tipDist * 0.55f) + perpX * widthDist,
            center.y + sinA * (tipDist * 0.55f) + perpY * widthDist
        )
        val ctrlRight = Offset(
            center.x + cosA * (tipDist * 0.55f) - perpX * widthDist,
            center.y + sinA * (tipDist * 0.55f) - perpY * widthDist
        )

        val petalPath = Path().apply {
            moveTo(center.x, center.y)
            quadraticTo(ctrlLeft.x, ctrlLeft.y, tipLeft.x, tipLeft.y)
            lineTo(notch.x, notch.y)
            lineTo(tipRight.x, tipRight.y)
            quadraticTo(ctrlRight.x, ctrlRight.y, center.x, center.y)
            close()
        }
        drawPath(petalPath, color = petalFill)
        drawPath(petalPath, color = petalStroke, style = Stroke(width = strokeW, join = StrokeJoin.Round))
    }

    // Pistil core
    drawCircle(
        color = color.copy(alpha = (alpha * 0.95f).coerceIn(0f, 1f)),
        radius = radius * 0.18f,
        center = center
    )
}

/**
 * 3. LAGOON: Concentric water ripples expanding directly from the Pomodoro timer circle
 * paired with rising translucent lagoon bubbles orbiting around the circle's perimeter.
 */
private fun DrawScope.drawLagoonRipplesAndBubbles(
    center: Offset,
    innerRadius: Float,
    color: Color,
    baseAlpha: Float,
    completedCount: Int,
    activeFraction: Float
) {
    val rippleSpacing = 14.dp.toPx()
    val strokeW = 1.4.dp.toPx()
    val totalToDraw = (completedCount + 1).coerceAtMost(28)

    for (idx in 0 until totalToDraw) {
        val progress = when {
            idx < completedCount -> 1f
            idx == completedCount -> activeFraction
            else -> 0f
        }
        if (progress <= 0.02f) continue

        // 1. Concentric broken water ripple ring expanding out from the exact timer circle
        val ringIdx = idx / 2
        if (idx % 2 == 0 && ringIdx < 12) {
            val radius = innerRadius + 8.dp.toPx() + ringIdx * rippleSpacing
            val distanceFade = (1f - ringIdx * 0.06f).coerceIn(0.32f, 1f)
            val rippleAlpha = (baseAlpha * 0.76f * distanceFade * progress).coerceIn(0f, 1f)
            val arcRect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
            val startOffset = (ringIdx * 37f) % 360f
            val sweep = 64f * progress

            for (arc in 0 until 4) {
                drawArc(
                    color = color.copy(alpha = rippleAlpha),
                    startAngle = startOffset + arc * 90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }

        // 2. Translucent lagoon bubbles rising symmetrically around the timer circle's perimeter
        val isLeftSide = idx % 2 == 0
        val step = idx / 2
        // Ascend from bottom-left / bottom-right of the circle (+65° / +115°) up to the top (-75° / -105°)
        val angleProgress = (step / 13f).coerceIn(0f, 1f)
        val angleDeg = if (isLeftSide) {
            145f + angleProgress * 115f // 145° (bottom-left) -> 260° (top-left)
        } else {
            35f - angleProgress * 115f  // 35° (bottom-right) -> -80° (top-right)
        }
        val rad = angleDeg * (PI.toFloat() / 180f)
        val radialOffset = innerRadius + 18.dp.toPx() + ((idx % 4) * 12.dp.toPx())
        val bubbleCenter = Offset(
            center.x + cos(rad) * radialOffset,
            center.y + sin(rad) * radialOffset
        )
        val bubbleRadius = (4.8.dp.toPx() + (idx % 4) * 2.2.dp.toPx()) * (0.5f + 0.5f * progress)
        val bubbleAlpha = (baseAlpha * 0.80f * progress).coerceIn(0f, 1f)

        // Translucent bubble fill & rim
        drawCircle(
            color = color.copy(alpha = bubbleAlpha * 0.22f),
            radius = bubbleRadius,
            center = bubbleCenter
        )
        drawCircle(
            color = color.copy(alpha = bubbleAlpha * 0.84f),
            radius = bubbleRadius,
            center = bubbleCenter,
            style = Stroke(width = 1.2.dp.toPx())
        )
        // Specular highlight arc on top-left of bubble
        val hlRadius = bubbleRadius * 0.65f
        drawArc(
            color = color.copy(alpha = (bubbleAlpha * 0.95f).coerceIn(0f, 1f)),
            startAngle = 200f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(bubbleCenter.x - hlRadius, bubbleCenter.y - hlRadius),
            size = Size(hlRadius * 2f, hlRadius * 2f),
            style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * 4. LITTLE EXPLORER: Compass rings around the exact Pomodoro timer circle + a winding dashed
 * treasure-map trail and miniature charted islands orbiting the circle with each minute passed.
 */
private fun DrawScope.drawExplorerMapTrail(
    center: Offset,
    innerRadius: Float,
    color: Color,
    baseAlpha: Float,
    completedCount: Int,
    activeFraction: Float
) {
    val compassRadius = innerRadius + 8.dp.toPx()
    val compassAlpha = (baseAlpha * 0.52f).coerceIn(0f, 1f)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()), 0f)

    // 1. Cartographic compass rose ring & degree ticks hugging the exact timer circle
    drawCircle(
        color = color.copy(alpha = compassAlpha),
        radius = compassRadius,
        center = center,
        style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashEffect)
    )
    for (deg in 0 until 360 step 15) {
        val rad = deg * (PI.toFloat() / 180f)
        val isCardinal = deg % 90 == 0
        val tickLen = if (isCardinal) 8.dp.toPx() else 4.dp.toPx()
        val p0 = Offset(center.x + cos(rad) * compassRadius, center.y + sin(rad) * compassRadius)
        val p1 = Offset(center.x + cos(rad) * (compassRadius + tickLen), center.y + sin(rad) * (compassRadius + tickLen))
        drawLine(
            color = color.copy(alpha = if (isCardinal) compassAlpha * 1.3f else compassAlpha * 0.8f),
            start = p0,
            end = p1,
            strokeWidth = if (isCardinal) 1.8.dp.toPx() else 1.0.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // 2. Winding polar treasure-map trail orbiting around the Pomodoro timer circle
    val totalWaypoints = 25
    val waypoints = ArrayList<Offset>(totalWaypoints)
    val baseTrailRadius = compassRadius + 22.dp.toPx()
    val waveAmplitude = 10.dp.toPx()
    for (i in 0 until totalWaypoints) {
        // Orbit clockwise starting from top (-90°), gently expanding into a second outer orbit
        val angleDeg = -90f + i * 24f
        val rad = angleDeg * (PI.toFloat() / 180f)
        val spiralBoost = (i / 15) * 22.dp.toPx()
        val wave = sin(i * 1.35f) * waveAmplitude
        val r = baseTrailRadius + spiralBoost + wave
        waypoints.add(
            Offset(
                center.x + cos(rad) * r,
                center.y + sin(rad) * r
            )
        )
    }

    val totalSteps = (completedCount + 1).coerceAtMost(waypoints.size)
    val trailStroke = 1.8.dp.toPx()

    for (idx in 0 until totalSteps) {
        val progress = when {
            idx < completedCount -> 1f
            idx == completedCount -> activeFraction
            else -> 0f
        }
        if (progress <= 0.02f) continue

        val pt = waypoints[idx]
        val stepAlpha = (baseAlpha * (0.65f + 0.35f * progress)).coerceIn(0f, 1f)

        // Dashed trail segment from previous waypoint to current waypoint around the circle
        if (idx > 0) {
            val prev = waypoints[idx - 1]
            val curEnd = Offset(prev.x + (pt.x - prev.x) * progress, prev.y + (pt.y - prev.y) * progress)
            drawLine(
                color = color.copy(alpha = stepAlpha * 0.82f),
                start = prev,
                end = curEnd,
                strokeWidth = trailStroke,
                cap = StrokeCap.Round,
                pathEffect = dashEffect
            )
        }

        // Every 3rd minute reveals a charted miniature island with double contour rings & peak;
        // intermediate minutes place a waypoint dot or treasure 'X' marker.
        if (idx % 3 == 0) {
            val islandRadius = 10.dp.toPx() * (0.5f + 0.5f * progress)
            drawOval(
                color = color.copy(alpha = stepAlpha * 0.38f),
                topLeft = Offset(pt.x - islandRadius * 1.35f, pt.y - islandRadius * 0.95f),
                size = Size(islandRadius * 2.7f, islandRadius * 1.9f),
                style = Stroke(width = 1.1.dp.toPx(), pathEffect = dashEffect)
            )
            drawOval(
                color = color.copy(alpha = stepAlpha * 0.24f),
                topLeft = Offset(pt.x - islandRadius, pt.y - islandRadius * 0.7f),
                size = Size(islandRadius * 2f, islandRadius * 1.4f)
            )
            drawOval(
                color = color.copy(alpha = stepAlpha * 0.85f),
                topLeft = Offset(pt.x - islandRadius, pt.y - islandRadius * 0.7f),
                size = Size(islandRadius * 2f, islandRadius * 1.4f),
                style = Stroke(width = 1.4.dp.toPx())
            )
            val peakPath = Path().apply {
                moveTo(pt.x - islandRadius * 0.45f, pt.y + islandRadius * 0.15f)
                lineTo(pt.x, pt.y - islandRadius * 0.45f)
                lineTo(pt.x + islandRadius * 0.45f, pt.y + islandRadius * 0.15f)
            }
            drawPath(
                peakPath,
                color = color.copy(alpha = stepAlpha * 0.9f),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else if (idx % 3 == 2) {
            val xSize = 4.2.dp.toPx() * progress
            val xColor = color.copy(alpha = stepAlpha * 0.9f)
            drawLine(xColor, Offset(pt.x - xSize, pt.y - xSize), Offset(pt.x + xSize, pt.y + xSize), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(xColor, Offset(pt.x + xSize, pt.y - xSize), Offset(pt.x - xSize, pt.y + xSize), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        } else {
            drawCircle(
                color = color.copy(alpha = stepAlpha * 0.85f),
                radius = 2.8.dp.toPx() * progress,
                center = pt
            )
        }
    }
}

/**
 * Full-screen container that renders a clean dark or light screen and reveals style-specific
 * progressive artwork centered on the Pomodoro timer circle with every Pomodoro minute passed,
 * colored according to the current phase (Focus, Short Break / Courte pause, Long Break / Longue pause).
 */
@Composable
fun PhaseTexturedBackground(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.background,
    isDark: Boolean = LocalIsDarkTheme.current,
    style: AppStyle = LocalFocusStyle.current.id,
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
    val circleBounds = remember { TimerCircleBounds() }

    CompositionLocalProvider(LocalTimerCircleBounds provides circleBounds) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        circleBounds.backgroundRootOffset = coords.positionInRoot()
                    }
                }
                .phaseTextureBackground(
                    cycleColor = animatedCycleColor,
                    baseColor = baseColor,
                    isDark = isDark,
                    style = style,
                    minutesPassed = minutesPassed,
                    currentMinuteFraction = currentMinuteFraction,
                    circleCenterOverride = circleBounds.localCenter,
                    circleRadiusOverride = circleBounds.circleRadiusPx
                ),
            content = content
        )
    }
}




