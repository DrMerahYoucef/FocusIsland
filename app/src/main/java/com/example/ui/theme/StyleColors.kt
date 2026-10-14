package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Colours of the three extra styles (Lagoon lives in Color.kt). Every text/background pair used by
// Material components meets WCAG AA (checked in StyleContrastTest).

// ---------------------------------------------------------------- Night Arcade: neon on midnight

// Daytime arcade: electric violet leads (cyan and magenta as accents), so it never reads as Lagoon's teal.
val ArcadeLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF5B2EE0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF1A0063),
    inversePrimary = Color(0xFFCBBEFF),
    secondary = Color(0xFF006874), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF97F0FF), onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFFA4007E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EE), onTertiaryContainer = Color(0xFF3B002C),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F6FF), onBackground = Color(0xFF161B2E),
    surface = Color(0xFFF4F6FF), onSurface = Color(0xFF161B2E),
    surfaceVariant = Color(0xFFDDE1F2), onSurfaceVariant = Color(0xFF444A60),
    surfaceTint = Color(0xFF5B2EE0),
    inverseSurface = Color(0xFF2B3044), inverseOnSurface = Color(0xFFEEF0FF),
    outline = Color(0xFF747A92), outlineVariant = Color(0xFFC4C8DD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4F6FF), surfaceDim = Color(0xFFD4D7E6),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEDF0FC),
    surfaceContainer = Color(0xFFE7EAF7), surfaceContainerHigh = Color(0xFFE1E4F2),
    surfaceContainerHighest = Color(0xFFDBDEEC)
)

val ArcadeDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF4DF0FF), onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58), onPrimaryContainer = Color(0xFF9EF4FF),
    inversePrimary = Color(0xFF006874),
    secondary = Color(0xFFB8FF3C), onSecondary = Color(0xFF213600),
    secondaryContainer = Color(0xFF314F00), onSecondaryContainer = Color(0xFFD2FF8A),
    tertiary = Color(0xFFFF5CC8), onTertiary = Color(0xFF5B0045),
    tertiaryContainer = Color(0xFF7E1A63), onTertiaryContainer = Color(0xFFFFD8EE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1020), onBackground = Color(0xFFE4E8FF),
    surface = Color(0xFF0B1020), onSurface = Color(0xFFE4E8FF),
    surfaceVariant = Color(0xFF353D5E), onSurfaceVariant = Color(0xFFB4BBDA),
    surfaceTint = Color(0xFF4DF0FF),
    inverseSurface = Color(0xFFE4E8FF), inverseOnSurface = Color(0xFF23283A),
    outline = Color(0xFF7E86A8), outlineVariant = Color(0xFF353D5E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A3152), surfaceDim = Color(0xFF0B1020),
    surfaceContainerLowest = Color(0xFF070B18), surfaceContainerLow = Color(0xFF121831),
    surfaceContainer = Color(0xFF171E3A), surfaceContainerHigh = Color(0xFF1D2545),
    surfaceContainerHighest = Color(0xFF242D52)
)

val ArcadeLightExtended = ExtendedColors(
    rest = Color(0xFF0058CA), onRest = Color(0xFFFFFFFF),
    restContainer = Color(0xFFD8E2FF), onRestContainer = Color(0xFF001A43),
    success = Color(0xFF2E6B30), onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB0F2A8), onSuccessContainer = Color(0xFF002204)
)

val ArcadeDarkExtended = ExtendedColors(
    rest = Color(0xFFB79CFF), onRest = Color(0xFF2E1878),
    restContainer = Color(0xFF45309A), onRestContainer = Color(0xFFE6DEFF),
    success = Color(0xFF9CE562), onSuccess = Color(0xFF173800),
    successContainer = Color(0xFF245100), onSuccessContainer = Color(0xFFB8F47A)
)

// ---------------------------------------------------------------- Blossom: rose, lilac and peach

val BlossomLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF9C3D63), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E0020),
    inversePrimary = Color(0xFFFFB0CB),
    secondary = Color(0xFF6E5596), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBDDFF), onSecondaryContainer = Color(0xFF27104F),
    tertiary = Color(0xFF9A4A1F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCB), onTertiaryContainer = Color(0xFF351000),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE1), onSurfaceVariant = Color(0xFF524347),
    surfaceTint = Color(0xFF9C3D63),
    inverseSurface = Color(0xFF382E30), inverseOnSurface = Color(0xFFFDEDEF),
    outline = Color(0xFF847377), outlineVariant = Color(0xFFD6C2C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F7), surfaceDim = Color(0xFFE7D6D8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFBEAEC), surfaceContainerHigh = Color(0xFFF5E4E6),
    surfaceContainerHighest = Color(0xFFEFDEE1)
)

val BlossomDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB0CB), onPrimary = Color(0xFF5E1135),
    primaryContainer = Color(0xFF7D294B), onPrimaryContainer = Color(0xFFFFD9E3),
    inversePrimary = Color(0xFF9C3D63),
    secondary = Color(0xFFD3BBFF), onSecondary = Color(0xFF3D2766),
    secondaryContainer = Color(0xFF553E7D), onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFFFB691), onTertiary = Color(0xFF552000),
    tertiaryContainer = Color(0xFF7A3309), onTertiaryContainer = Color(0xFFFFDBCB),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1114), onBackground = Color(0xFFF0DEE1),
    surface = Color(0xFF1A1114), onSurface = Color(0xFFF0DEE1),
    surfaceVariant = Color(0xFF524347), onSurfaceVariant = Color(0xFFD6C2C6),
    surfaceTint = Color(0xFFFFB0CB),
    inverseSurface = Color(0xFFF0DEE1), inverseOnSurface = Color(0xFF382E30),
    outline = Color(0xFF9F8C90), outlineVariant = Color(0xFF524347),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF41373A), surfaceDim = Color(0xFF1A1114),
    surfaceContainerLowest = Color(0xFF140C0F), surfaceContainerLow = Color(0xFF22191C),
    surfaceContainer = Color(0xFF271D20), surfaceContainerHigh = Color(0xFF32282A),
    surfaceContainerHighest = Color(0xFF3D3235)
)

val BlossomLightExtended = ExtendedColors(
    rest = Color(0xFF2F5F94), onRest = Color(0xFFFFFFFF),
    restContainer = Color(0xFFD3E4FF), onRestContainer = Color(0xFF001C38),
    success = Color(0xFF3B6939), onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFBCF0B4), onSuccessContainer = Color(0xFF002105)
)

val BlossomDarkExtended = ExtendedColors(
    rest = Color(0xFF9CCBFF), onRest = Color(0xFF003258),
    restContainer = Color(0xFF1F4876), onRestContainer = Color(0xFFD3E4FF),
    success = Color(0xFFA1D39A), onSuccess = Color(0xFF0A390F),
    successContainer = Color(0xFF235024), onSuccessContainer = Color(0xFFBCF0B4)
)

// ---------------------------------------------------------------- Little Explorer: sky, sun and grass

val ExplorerLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0061A4), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
    inversePrimary = Color(0xFF9ECAFF),
    secondary = Color(0xFF6C5E00), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE24C), onSecondaryContainer = Color(0xFF211B00),
    tertiary = Color(0xFF2E6C00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA8F57A), onTertiaryContainer = Color(0xFF082100),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBEF), onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFFBEF), onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFEAE2CF), onSurfaceVariant = Color(0xFF4B4639),
    surfaceTint = Color(0xFF0061A4),
    inverseSurface = Color(0xFF333027), inverseOnSurface = Color(0xFFF7F0E2),
    outline = Color(0xFF7C7767), outlineVariant = Color(0xFFCDC6B4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFBEF), surfaceDim = Color(0xFFE0D9CA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFBF3E3),
    surfaceContainer = Color(0xFFF5EDDD), surfaceContainerHigh = Color(0xFFEFE7D7),
    surfaceContainerHighest = Color(0xFFE9E2D2)
)

val ExplorerDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
    inversePrimary = Color(0xFF0061A4),
    secondary = Color(0xFFF2D33A), onSecondary = Color(0xFF383000),
    secondaryContainer = Color(0xFF524600), onSecondaryContainer = Color(0xFFFFE24C),
    tertiary = Color(0xFF8DD860), onTertiary = Color(0xFF133800),
    tertiaryContainer = Color(0xFF1F5100), onTertiaryContainer = Color(0xFFA8F57A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121A2B), onBackground = Color(0xFFE6E9F5),
    surface = Color(0xFF121A2B), onSurface = Color(0xFFE6E9F5),
    surfaceVariant = Color(0xFF404858), onSurfaceVariant = Color(0xFFC0C7D6),
    surfaceTint = Color(0xFF9ECAFF),
    inverseSurface = Color(0xFFE6E9F5), inverseOnSurface = Color(0xFF2A3245),
    outline = Color(0xFF8A92A3), outlineVariant = Color(0xFF404858),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343E57), surfaceDim = Color(0xFF121A2B),
    surfaceContainerLowest = Color(0xFF0D1422), surfaceContainerLow = Color(0xFF172036),
    surfaceContainer = Color(0xFF1B253D), surfaceContainerHigh = Color(0xFF243049),
    surfaceContainerHighest = Color(0xFF2E3A55)
)

val ExplorerLightExtended = ExtendedColors(
    rest = Color(0xFF006A60), onRest = Color(0xFFFFFFFF),
    restContainer = Color(0xFF74F8E5), onRestContainer = Color(0xFF00201C),
    success = Color(0xFF1E6D2F), onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA6F5AE), onSuccessContainer = Color(0xFF002109)
)

val ExplorerDarkExtended = ExtendedColors(
    rest = Color(0xFF4FD8C4), onRest = Color(0xFF003731),
    restContainer = Color(0xFF005048), onRestContainer = Color(0xFF74F8E5),
    success = Color(0xFF8BD895), onSuccess = Color(0xFF003913),
    successContainer = Color(0xFF13521F), onSuccessContainer = Color(0xFFA6F5AE)
)

// ---------------------------------------------------------------- Island scenes of each style

/** Island scenes for the three extra styles (Lagoon: [IslandPalette]). */
object StyleIslandPalettes {
    /** Synthwave dusk: purple sky, orange sun, neon palms on a dark platform. */
    val ArcadeDay = IslandColors(
        skyTop = Color(0xFF2A1260), skyHorizon = Color(0xFFFF7AB6), glow = Color(0x66FF5CC8),
        sunOrMoon = Color(0xFFFFB347), star = Color(0xCCFFFFFF), cloud = Color(0x00FFFFFF),
        seaFar = Color(0xFF3B1D7A), seaNear = Color(0xFF0B1030), shimmer = Color(0x99FF9AD5),
        farIsland = Color(0xFF2A1660), shallow = Color(0xFF263A8A), foam = Color(0x994DF0FF),
        sand = Color(0xFF3A3F8F), sandShade = Color(0xFF23286B), grass = Color(0xFF1B2F5E), grassLight = Color(0xFF25407A),
        trunk = Color(0xFF4DF0FF), frond = Color(0xFFB8FF3C), frondDark = Color(0xFF7CDB2A), coconut = Color(0xFFFF5CC8),
        wood = Color(0xFF1D2545), thatch = Color(0xFF4DF0FF), door = Color(0xFF0B1020), window = Color(0xFFB8FF3C),
        tower = Color(0xFFFF5CC8), towerStripe = Color(0xFF4DF0FF), lamp = Color(0xFFFFFFFF), lampGlow = Color(0x80FF5CC8),
        label = Color(0xFFFFFFFF), labelShadow = Color(0xFF000000),
        kind = IslandKind.NEON, accent = Color(0xFF4DF0FF), accent2 = Color(0xFFFF5CC8)
    )
    /** Midnight grid: magenta moon over a neon sea. */
    val ArcadeNight = IslandColors(
        skyTop = Color(0xFF05081A), skyHorizon = Color(0xFF2A1450), glow = Color(0x55FF5CC8),
        sunOrMoon = Color(0xFFFF5CC8), star = Color(0xFFFFFFFF), cloud = Color(0x00FFFFFF),
        seaFar = Color(0xFF0E1A3A), seaNear = Color(0xFF05081A), shimmer = Color(0x80FF5CC8),
        farIsland = Color(0xFF121B45), shallow = Color(0xFF16306B), foam = Color(0x994DF0FF),
        sand = Color(0xFF2B3A6B), sandShade = Color(0xFF1B2650), grass = Color(0xFF1B2F5E), grassLight = Color(0xFF24407E),
        trunk = Color(0xFF4DF0FF), frond = Color(0xFFB8FF3C), frondDark = Color(0xFF8EE02E), coconut = Color(0xFFFF5CC8),
        wood = Color(0xFF1D2545), thatch = Color(0xFF4DF0FF), door = Color(0xFF05081A), window = Color(0xFFB8FF3C),
        tower = Color(0xFFFF5CC8), towerStripe = Color(0xFF4DF0FF), lamp = Color(0xFFFFFFFF), lampGlow = Color(0x99FF5CC8),
        label = Color(0xFFFFFFFF), labelShadow = Color(0xFF000000),
        kind = IslandKind.NEON, accent = Color(0xFF4DF0FF), accent2 = Color(0xFFFF5CC8)
    )
    /** Spring morning: pink sky, lilac sea, cherry trees in bloom. */
    val BlossomDay = IslandColors(
        skyTop = Color(0xFFF9CFE2), skyHorizon = Color(0xFFFFE9DC), glow = Color(0x66FFF3B8),
        sunOrMoon = Color(0xFFFFF0A8), star = Color(0x00FFFFFF), cloud = Color(0xCCFFFFFF),
        seaFar = Color(0xFFC7B8F0), seaNear = Color(0xFF8E7CD0), shimmer = Color(0x99FFFFFF),
        farIsland = Color(0xFFB9A6E0), shallow = Color(0xFFDCCFFF), foam = Color(0xCCFFFFFF),
        sand = Color(0xFFF7DCC8), sandShade = Color(0xFFE7BFA4), grass = Color(0xFFA9D8A0), grassLight = Color(0xFFC6E8BC),
        trunk = Color(0xFF7A4E3A), frond = Color(0xFFF7A8C4), frondDark = Color(0xFFF28DB2), coconut = Color(0xFFFFFFFF),
        wood = Color(0xFFFFF1E6), thatch = Color(0xFFB5476E), door = Color(0xFF7A4E3A), window = Color(0xFFFFD9A8),
        tower = Color(0xFF7A4E3A), towerStripe = Color(0xFFB5476E), lamp = Color(0xFFFF9E7A), lampGlow = Color(0x00FFC9A8),
        label = Color(0xFFFFFFFF), labelShadow = Color(0xFF000000),
        kind = IslandKind.SAKURA, accent = Color(0xFFFFC4D8), accent2 = Color(0xFFFFFFFF)
    )
    /** Spring night: plum sky, paper lantern glowing. */
    val BlossomNight = IslandColors(
        skyTop = Color(0xFF1E1433), skyHorizon = Color(0xFF5A3A6E), glow = Color(0x40FFE0F0),
        sunOrMoon = Color(0xFFFFE6F0), star = Color(0xFFFFFFFF), cloud = Color(0x00FFFFFF),
        seaFar = Color(0xFF3A2A5E), seaNear = Color(0xFF140C22), shimmer = Color(0x80FFD9E3),
        farIsland = Color(0xFF2A1E45), shallow = Color(0xFF4A3A78), foam = Color(0x66FFD9E3),
        sand = Color(0xFFB89C92), sandShade = Color(0xFF8A7068), grass = Color(0xFF4E7A5A), grassLight = Color(0xFF5E8E6A),
        trunk = Color(0xFF4A3028), frond = Color(0xFFD9789E), frondDark = Color(0xFFB85E82), coconut = Color(0xFFFFE0EC),
        wood = Color(0xFFD8C4B8), thatch = Color(0xFF7E2F50), door = Color(0xFF2A1A14), window = Color(0xFFFFC97A),
        tower = Color(0xFF4A3028), towerStripe = Color(0xFF7E2F50), lamp = Color(0xFFFFB08A), lampGlow = Color(0x99FFB08A),
        label = Color(0xFFFFFFFF), labelShadow = Color(0xFF000000),
        kind = IslandKind.SAKURA, accent = Color(0xFFE89AB8), accent2 = Color(0xFFFFE0EC)
    )
    /** Sunny adventure: bright sky and sea, cartoon outlines. */
    val ExplorerDay = IslandColors(
        skyTop = Color(0xFF6EC8FF), skyHorizon = Color(0xFFC9EEFF), glow = Color(0x66FFE27A),
        sunOrMoon = Color(0xFFFFD23F), star = Color(0x00FFFFFF), cloud = Color(0xE6FFFFFF),
        seaFar = Color(0xFF2FA8E0), seaNear = Color(0xFF1677B8), shimmer = Color(0x99FFFFFF),
        farIsland = Color(0xFF4FB06A), shallow = Color(0xFF7FE0F5), foam = Color(0xE6FFFFFF),
        sand = Color(0xFFFFD978), sandShade = Color(0xFFF0B94A), grass = Color(0xFF5CC94A), grassLight = Color(0xFF8EE07A),
        trunk = Color(0xFF9C6B3F), frond = Color(0xFF2FA84F), frondDark = Color(0xFF1E8A3C), coconut = Color(0xFF6B4A2E),
        wood = Color(0xFF9C6B3F), thatch = Color(0xFFC94A3A), door = Color(0xFF3A2A18), window = Color(0xFFFFE27A),
        tower = Color(0xFF3A2A18), towerStripe = Color(0xFF1E1B13), lamp = Color(0xFFFFFFFF), lampGlow = Color(0x00FFFFFF),
        label = Color(0xFFFFFFFF), labelShadow = Color(0xFF000000),
        kind = IslandKind.TREASURE, accent = Color(0xFF3FA34D), accent2 = Color(0xFFFFC83D), ink = Color(0xFF3A2A18)
    )
    /** Camping night: deep blue sky, warm moon. */
    val ExplorerNight = IslandColors(
        skyTop = Color(0xFF0E1A3D), skyHorizon = Color(0xFF2E4A86), glow = Color(0x40FFF1B0),
        sunOrMoon = Color(0xFFFFF1B0), star = Color(0xFFFFFFFF), cloud = Color(0x00FFFFFF),
        seaFar = Color(0xFF1E4C8A), seaNear = Color(0xFF0A1A38), shimmer = Color(0x80FFF1B0),
        farIsland = Color(0xFF1E4A3A), shallow = Color(0xFF2E6FA8), foam = Color(0x80E6F6FF),
        sand = Color(0xFFC9A85A), sandShade = Color(0xFF9E7E3A), grass = Color(0xFF3C8A3A), grassLight = Color(0xFF4E9E4A),
        trunk = Color(0xFF6E4A2C), frond = Color(0xFF237A3C), frondDark = Color(0xFF1A5E2E), coconut = Color(0xFF3A2A18),
        wood = Color(0xFF6E4A2C), thatch = Color(0xFF8E3328), door = Color(0xFF1E140C), window = Color(0xFFFFD86A),
        tower = Color(0xFF1E140C), towerStripe = Color(0xFF0A0A0A), lamp = Color(0xFFFFFFFF), lampGlow = Color(0x00FFFFFF),
        label = Color(0xFFFFFFFF), labelShadow = Color(0xFF000000),
        kind = IslandKind.TREASURE, accent = Color(0xFF2F8A3E), accent2 = Color(0xFFFFC83D), ink = Color(0xFF1E140C)
    )
}
