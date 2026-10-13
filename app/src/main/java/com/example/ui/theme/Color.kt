package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// "Lagoon at dusk" — calm lagoon teal (focus), warm sand (rewards), soft coral (stop moments).

val LagoonLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F6A64),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F2E9),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF7A5900),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDEA3),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = Color(0xFF9C4234),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF3E0602),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF6FAF8),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F0),
    inversePrimary = Color(0xFF8BD5CD),
    surfaceTint = Color(0xFF1F6A64),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FAF8),
    surfaceDim = Color(0xFFD6DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F2),
    surfaceContainer = Color(0xFFEAEFED),
    surfaceContainerHigh = Color(0xFFE4E9E7),
    surfaceContainerHighest = Color(0xFFDFE4E2)
)

val LagoonDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8BD5CD),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFFA7F2E9),
    secondary = Color(0xFFF6BE48),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFDEA3),
    tertiary = Color(0xFFFFB4A7),
    onTertiary = Color(0xFF5F150B),
    tertiaryContainer = Color(0xFF7E2B1F),
    onTertiaryContainer = Color(0xFFFFDAD4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1514),
    onBackground = Color(0xFFDEE4E2),
    surface = Color(0xFF0F1514),
    onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFDEE4E2),
    inverseOnSurface = Color(0xFF2B3231),
    inversePrimary = Color(0xFF1F6A64),
    surfaceTint = Color(0xFF8BD5CD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353B3A),
    surfaceDim = Color(0xFF0F1514),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635)
)

/** Semantic colours Material 3 doesn't define: breaks ("rest") and positive outcomes ("success"). */
@Immutable
data class ExtendedColors(
    val rest: Color,
    val onRest: Color,
    val restContainer: Color,
    val onRestContainer: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color
)

val LagoonLightExtended = ExtendedColors(
    rest = Color(0xFF3F6FA0),
    onRest = Color(0xFFFFFFFF),
    restContainer = Color(0xFFD3E4FF),
    onRestContainer = Color(0xFF001C38),
    success = Color(0xFF2E6B30),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB0F2A8),
    onSuccessContainer = Color(0xFF002204)
)

val LagoonDarkExtended = ExtendedColors(
    rest = Color(0xFFA9C7FF),
    onRest = Color(0xFF003060),
    restContainer = Color(0xFF214A7A),
    onRestContainer = Color(0xFFD3E4FF),
    success = Color(0xFF95D78F),
    onSuccess = Color(0xFF00390A),
    successContainer = Color(0xFF13511A),
    onSuccessContainer = Color(0xFFB0F2A8)
)

/** Colours of the island scene (wallpaper, Community archipelago, friend sheet). */
@Immutable
data class IslandColors(
    val skyTop: Color,
    val skyHorizon: Color,
    val glow: Color,
    val sunOrMoon: Color,
    val star: Color,
    val cloud: Color,
    val seaFar: Color,
    val seaNear: Color,
    val shimmer: Color,
    val farIsland: Color,
    val shallow: Color,
    val foam: Color,
    val sand: Color,
    val sandShade: Color,
    val grass: Color,
    val grassLight: Color,
    val trunk: Color,
    val frond: Color,
    val frondDark: Color,
    val coconut: Color,
    val wood: Color,
    val thatch: Color,
    val door: Color,
    val window: Color,
    val tower: Color,
    val towerStripe: Color,
    val lamp: Color,
    val lampGlow: Color,
    val label: Color,
    val labelShadow: Color
)

/**
 * Focus Island scene. Day: a sunny lagoon in the brand teal and sand. Night: the same lagoon
 * under the moon ("Lagoon at dusk"), also used by the Community archipelago in both themes.
 */
object IslandPalette {
    val Day = IslandColors(
        skyTop = Color(0xFF8ECFE8),
        skyHorizon = Color(0xFFFCE6CF),
        glow = Color(0x66FFE3A3),
        sunOrMoon = Color(0xFFFFC970),
        star = Color(0x00FFFFFF),
        cloud = Color(0xB3FFFFFF),
        seaFar = Color(0xFF5CC3C0),
        seaNear = Color(0xFF1F7E78),
        shimmer = Color(0x99FFFFFF),
        farIsland = Color(0xFF6FA9A1),
        shallow = Color(0xFF8FE0D6),
        foam = Color(0xCCFFFFFF),
        sand = Color(0xFFF3D9A4),
        sandShade = Color(0xFFDDB878),
        grass = Color(0xFF4FA36A),
        grassLight = Color(0xFF7CC48A),
        trunk = Color(0xFF8B6544),
        frond = Color(0xFF2F8A5A),
        frondDark = Color(0xFF22704A),
        coconut = Color(0xFF6B4A2E),
        wood = Color(0xFFB7884F),
        thatch = Color(0xFFD8B26A),
        door = Color(0xFF5A3E26),
        window = Color(0xFF7FC7D6),
        tower = Color(0xFFF7F3EA),
        towerStripe = Color(0xFFD9604C),
        lamp = Color(0xFFFFD27A),
        lampGlow = Color(0x00FFE3A3),
        label = Color(0xFFFFFFFF),
        labelShadow = Color(0xFF000000)
    )
    val Night = IslandColors(
        skyTop = Color(0xFF0A1A2A),
        skyHorizon = Color(0xFF1D4656),
        glow = Color(0x40FFDEA3),
        sunOrMoon = Color(0xFFFFDEA3),
        star = Color(0xFFFFFFFF),
        cloud = Color(0x00FFFFFF),
        seaFar = Color(0xFF174652),
        seaNear = Color(0xFF06161D),
        shimmer = Color(0x80FFDEA3),
        farIsland = Color(0xFF0E2A33),
        shallow = Color(0xFF1F5E63),
        foam = Color(0x66CFEFEA),
        sand = Color(0xFFB09C78),
        sandShade = Color(0xFF7F7055),
        grass = Color(0xFF2A6355),
        grassLight = Color(0xFF3B7D6A),
        trunk = Color(0xFF5E4C3C),
        frond = Color(0xFF2A7560),
        frondDark = Color(0xFF1E5A4A),
        coconut = Color(0xFF3A2C20),
        wood = Color(0xFF5E4630),
        thatch = Color(0xFF7C6740),
        door = Color(0xFF2A1E14),
        window = Color(0xFFFFC970),
        tower = Color(0xFFB9B4A8),
        towerStripe = Color(0xFF8E3F33),
        lamp = Color(0xFFFFE3A3),
        lampGlow = Color(0x80FFE3A3),
        label = Color(0xFFFFFFFF),
        labelShadow = Color(0xFF000000)
    )
}

/**
 * Deck colours the user can pick. Stored in the database as "#RRGGBB" strings (user data),
 * so they are listed as hex here. Mid-tone hues that read on light and dark surfaces.
 */
object DeckPalette {
    const val DEFAULT = "#4CAF50" // historical default, kept for existing decks
    val choices = listOf(
        "#1F6A64", // lagoon
        "#4CAF50", // leaf
        "#3F6FA0", // ocean
        "#7A5900", // sand
        "#9C4234", // coral
        "#7E57C2", // lavender
        "#C2185B", // berry
        "#5D6B6A"  // stone
    )
}

/**
 * Camera and crop screens: always dark, whatever the app theme, so the photo is what stands
 * out (the convention of every camera app). Accent comes from the Lagoon teal.
 */
object CameraPalette {
    val background = Color(0xFF000000)
    val chrome = Color(0xB3000000)        // 70 % black behind bars and buttons
    val scrim = Color(0x99000000)         // 60 % black outside the crop area
    val onChrome = Color(0xFFFFFFFF)
    val onChromeVariant = Color(0xFFBEC9C6)
    val accent = Color(0xFF8BD5CD)
    val onAccent = Color(0xFF003733)
    val shutter = Color(0xFFFFFFFF)
    val shutterRing = Color(0x80FFFFFF)
    val handle = Color(0xFFFFFFFF)
}

/** Focus mode: true black for OLED battery saving, whatever the app theme. */
object FocusModePalette {
    val background = Color(0xFF000000)
    val track = Color(0x33FFFFFF)       // brightness track, 20 % white
    val trackFill = Color(0x99FFFFFF)   // brightness level, 60 % white
    val flash = Color(0xFFFFFFFF)       // milestone flash (drawn at low alpha)
}
