package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.R

/** Plus Jakarta Sans (SIL OFL 1.1), one variable font file for every heading weight. */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Lagoon headings. */
val BrandFontFamily = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold)
)

// Style fonts (SIL OFL 1.1, Latin subset). Their digits were made equal-width so timers don't jitter.

/** Night Arcade: angular, tech. */
val ChakraPetchFamily = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold)
)

/** Blossom: soft and rounded. */
val QuicksandFamily = FontFamily(
    Font(R.font.quicksand_medium, FontWeight.Normal),
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_semibold, FontWeight.SemiBold),
    Font(R.font.quicksand_bold, FontWeight.Bold)
)

/** Little Explorer: chunky and friendly. */
val FredokaFamily = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Normal),
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold)
)

private val Body = FontFamily.Default // system font for long reading text

/**
 * The type scale with [display] for headings and titles, [label] for buttons and chips, and body
 * text in the system font, [bodyBoost] larger (Little Explorer reads a little bigger).
 */
fun styleTypography(
    display: FontFamily,
    label: FontFamily = Body,
    bodyBoost: Float = 0f,
    tracking: Float = 0f
) = Typography(
    displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = tracking.sp),
    titleMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (0.15f + tracking).sp),
    titleSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (0.1f + tracking).sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = (16f + bodyBoost).sp, lineHeight = (24f + bodyBoost).sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = (14f + bodyBoost).sp, lineHeight = (20f + bodyBoost).sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = (12f + bodyBoost).sp, lineHeight = (16f + bodyBoost).sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = label, fontWeight = FontWeight.Medium, fontSize = (14f + bodyBoost).sp, lineHeight = (20f + bodyBoost).sp, letterSpacing = (0.1f + tracking).sp),
    labelMedium = TextStyle(fontFamily = label, fontWeight = FontWeight.Medium, fontSize = (12f + bodyBoost).sp, lineHeight = (16f + bodyBoost).sp, letterSpacing = (0.5f + tracking).sp),
    labelSmall = TextStyle(fontFamily = label, fontWeight = FontWeight.Medium, fontSize = (11f + bodyBoost).sp, lineHeight = (16f + bodyBoost).sp, letterSpacing = (0.5f + tracking).sp)
)

/** Lagoon's type scale (unchanged since 2.0). */
val Typography = styleTypography(BrandFontFamily)

/** Countdown digits in [family]: large and equal-width (no jitter as seconds change). */
fun timerStyle(family: FontFamily, weight: FontWeight = FontWeight.SemiBold, tracking: TextUnit = (-1).sp) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = 88.sp,
    lineHeight = 96.sp,
    letterSpacing = tracking,
    fontFeatureSettings = "tnum"
)

/** The current style's countdown digits. */
val TimerDisplayStyle: TextStyle
    @Composable @ReadOnlyComposable get() = LocalFocusStyle.current.timerText
