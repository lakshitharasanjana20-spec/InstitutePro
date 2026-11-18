package com.example.institutepro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.institutepro.R

// Core Blue Color Palette (Light-Mode Only)
val CobaltPrimary = Color(0xFF1D4ED8)      // Royal Blue
val CobaltDark = Color(0xFF1E3A8A)         // Deep Navy Blue
val CobaltMid = Color(0xFF2563EB)          // Crisp Azure Blue
val CobaltContainer = Color(0xFFDBEAFE)    // Ice Blue Container
val OceanBlue = Color(0xFF0284C7)          // Ocean / Cerulean Blue
val OceanContainer = Color(0xFFE0F2FE)     // Mist Sky Container
val SkyBlue = Color(0xFF0EA5E9)            // Sky Blue
val SkyLight = Color(0xFF38BDF8)           // Bright Sky Blue
val PeriwinkleBlue = Color(0xFF60A5FA)     // Soft Cornflower Blue
val PowderBlue = Color(0xFF93C5FD)         // Pale Powder Blue
val IndigoBlue = Color(0xFF4338CA)         // Deep Indigo Blue
val IndigoLight = Color(0xFF6366F1)        // Periwinkle Indigo

// Blue-harmonized semantic accents
val TealSecondary = OceanBlue
val TealContainer = OceanContainer
val AmberTertiary = Color(0xFF0369A1)      // Deep Cerulean Accent
val AmberContainer = Color(0xFFE0F2FE)
val EmeraldSuccess = Color(0xFF0F766E)     // Deep Teal-Blue Positive
val EmeraldSurface = Color(0xFFCCFBF1)
val CrimsonAlert = Color(0xFFDC2626)       // High-clarity Alert Red
val CrimsonSurface = Color(0xFFFEE2E2)

val ChartBluePalette = listOf(
    CobaltPrimary,
    SkyBlue,
    CobaltDark,
    IndigoLight,
    OceanBlue,
    PeriwinkleBlue,
    SkyLight,
    IndigoBlue
)

private val BlueLightColors = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = CobaltDark,
    secondary = OceanBlue,
    onSecondary = Color.White,
    secondaryContainer = OceanContainer,
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = IndigoBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    background = Color(0xFFF3F7FF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEBF2FE),
    onSurfaceVariant = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFF),
    surfaceContainer = Color(0xFFEFF5FF),
    surfaceContainerHigh = Color(0xFFE2ECFE),
    surfaceContainerHighest = Color(0xFFD6E4FE),
    outline = Color(0xFF93C5FD),
    outlineVariant = Color(0xFFD6E4FE),
    error = CrimsonAlert,
    errorContainer = CrimsonSurface,
    onErrorContainer = Color(0xFF7F1D1D)
)

val DisplayFontFamily = FontFamily(Font(R.font.outfit, FontWeight.Normal))
val BodyFontFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))

val InstituteTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val InstituteShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun InstituteProTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlueLightColors,
        typography = InstituteTypography,
        shapes = InstituteShapes,
        content = content
    )
}
