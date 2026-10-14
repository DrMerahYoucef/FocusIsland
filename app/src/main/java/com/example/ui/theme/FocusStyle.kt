package com.example.ui.theme

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/** How a style celebrates a goal (Challenges) or a new palm (Focus tab). */
enum class CelebrationKind {
    /** Paper confetti. */
    CONFETTI,
    /** Square pixels falling in steps, with a "LEVEL UP" banner. */
    PIXELS,
    /** Petals and little hearts drifting down. */
    PETALS,
    /** Stars falling and balloons rising, the turtle jumps. */
    STARS
}

/** Everything that makes a style look and sound like itself. Read with [LocalFocusStyle]. */
@Immutable
data class FocusStyle(
    val id: AppStyle,
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightExtended: ExtendedColors,
    val darkExtended: ExtendedColors,
    val islandDay: IslandColors,
    val islandNight: IslandColors,
    val typography: Typography,
    val timerText: TextStyle,
    val shapes: Shapes,
    val celebration: CelebrationKind,
    @RawRes val successSound: Int,
    /** Shown on the Focus tab when a session plants something on the island. */
    @StringRes val plantedMessage: Int,
    /** Simpler, encouraging wording for the main messages (Little Explorer). */
    val kidCopy: Boolean = false
) {
    fun colors(dark: Boolean) = if (dark) this.dark else light
    fun extended(dark: Boolean) = if (dark) darkExtended else lightExtended
    fun island(dark: Boolean) = if (dark) islandNight else islandDay
}

object FocusStyles {
    val Lagoon = FocusStyle(
        id = AppStyle.LAGOON,
        light = LagoonLightColors, dark = LagoonDarkColors,
        lightExtended = LagoonLightExtended, darkExtended = LagoonDarkExtended,
        islandDay = IslandPalette.Day, islandNight = IslandPalette.Night,
        typography = Typography,
        timerText = timerStyle(BrandFontFamily),
        shapes = Shapes,
        celebration = CelebrationKind.CONFETTI,
        successSound = R.raw.success_lagoon,
        plantedMessage = R.string.planted_lagoon
    )

    val Arcade = FocusStyle(
        id = AppStyle.ARCADE,
        light = ArcadeLightColors, dark = ArcadeDarkColors,
        lightExtended = ArcadeLightExtended, darkExtended = ArcadeDarkExtended,
        islandDay = StyleIslandPalettes.ArcadeDay, islandNight = StyleIslandPalettes.ArcadeNight,
        typography = styleTypography(ChakraPetchFamily, label = ChakraPetchFamily, tracking = 0.3f),
        timerText = timerStyle(ChakraPetchFamily, FontWeight.Bold, tracking = 0.sp),
        shapes = ArcadeShapes,
        celebration = CelebrationKind.PIXELS,
        successSound = R.raw.success_arcade,
        plantedMessage = R.string.planted_arcade
    )

    val Blossom = FocusStyle(
        id = AppStyle.BLOSSOM,
        light = BlossomLightColors, dark = BlossomDarkColors,
        lightExtended = BlossomLightExtended, darkExtended = BlossomDarkExtended,
        islandDay = StyleIslandPalettes.BlossomDay, islandNight = StyleIslandPalettes.BlossomNight,
        typography = styleTypography(QuicksandFamily),
        timerText = timerStyle(QuicksandFamily, FontWeight.Bold, tracking = (-0.5).sp),
        shapes = BlossomShapes,
        celebration = CelebrationKind.PETALS,
        successSound = R.raw.success_blossom,
        plantedMessage = R.string.planted_blossom
    )

    val Explorer = FocusStyle(
        id = AppStyle.EXPLORER,
        light = ExplorerLightColors, dark = ExplorerDarkColors,
        lightExtended = ExplorerLightExtended, darkExtended = ExplorerDarkExtended,
        islandDay = StyleIslandPalettes.ExplorerDay, islandNight = StyleIslandPalettes.ExplorerNight,
        typography = styleTypography(FredokaFamily, label = FredokaFamily, bodyBoost = 1f),
        timerText = timerStyle(FredokaFamily, FontWeight.Bold, tracking = (-0.5).sp),
        shapes = ExplorerShapes,
        celebration = CelebrationKind.STARS,
        successSound = R.raw.success_explorer,
        plantedMessage = R.string.planted_explorer,
        kidCopy = true
    )

    fun of(style: AppStyle) = when (style) {
        AppStyle.LAGOON -> Lagoon
        AppStyle.ARCADE -> Arcade
        AppStyle.BLOSSOM -> Blossom
        AppStyle.EXPLORER -> Explorer
    }
}

val LocalFocusStyle = staticCompositionLocalOf { FocusStyles.Lagoon }

/** Kid-friendly wording used instead of the usual text when the style asks for it. */
private val kidStrings = mapOf(
    R.string.challenge_success_title to R.string.kid_challenge_success_title,
    R.string.challenge_success_body to R.string.kid_challenge_success_body,
    R.string.challenge_success_body_tight to R.string.kid_challenge_success_body_tight,
    R.string.challenge_time_up_title to R.string.kid_challenge_time_up_title,
    R.string.challenge_time_up_body to R.string.kid_challenge_time_up_body
)

/** [stringResource], replaced by the kid-friendly version in Little Explorer. */
@Composable
@ReadOnlyComposable
fun styledString(@StringRes id: Int, vararg args: Any): String {
    val res = if (LocalFocusStyle.current.kidCopy) kidStrings[id] ?: id else id
    return stringResource(res, *args)
}
