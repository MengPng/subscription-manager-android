package com.netkaize.subscription.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Apple Human Interface Guidelines design tokens (light appearance).
// https://developer.apple.com/design/resources/

// System colors
val AppBlue = Color(0xFF007AFF)      // systemBlue
val AppGreen = Color(0xFF34C759)     // systemGreen
val AppRed = Color(0xFFFF3B30)       // systemRed
val AppOrange = Color(0xFFFF9500)    // systemOrange
val AppPurple = Color(0xFFAF52DE)    // systemPurple
val AppIndigo = Color(0xFF5856D6)    // systemIndigo
val AppGray = Color(0xFF8E8E93)      // systemGray

// Backgrounds
val AppCanvas = Color(0xFFF2F2F7)    // systemGroupedBackground
val AppCard = Color(0xFFFFFFFF)      // secondarySystemGroupedBackground

// Labels
val AppInk = Color(0xFF000000)       // label
val AppSecondary = Color(0x993C3C43) // secondaryLabel (60,60,67 @ 60%)
val AppTertiary = Color(0x4D3C3C43)  // tertiaryLabel (60,60,67 @ 30%)

// Separators & fills
val AppDivider = Color(0xFFD1D1D6)   // hairline separator
val AppFill = Color(0x29787880)      // secondarySystemFill (120,120,128 @ 16%)
val AppSearchFill = Color(0x1F767680)// iOS search field fill (118,118,128 @ 12%)
val AppBlueTint = Color(0x1A007AFF)  // systemBlue @ 10%
val AppGreenTint = Color(0x1A34C759)
val AppRedTint = Color(0x1AFF3B30)
val AppOrangeTint = Color(0x1AFF9500)
val AppPurpleTint = Color(0x1AAF52DE)
val AppIndigoTint = Color(0x1A5856D6)
val AppGrayTint = Color(0x1A8E8E93)

private val colors = lightColorScheme(
    primary = AppBlue,
    onPrimary = Color.White,
    primaryContainer = AppBlueTint,
    onPrimaryContainer = AppBlue,
    secondary = AppGray,
    onSecondary = Color.White,
    secondaryContainer = AppFill,
    onSecondaryContainer = AppInk,
    tertiary = AppOrange,
    onTertiary = Color.White,
    background = AppCanvas,
    onBackground = AppInk,
    surface = AppCard,
    onSurface = AppInk,
    surfaceVariant = AppFill,
    onSurfaceVariant = AppSecondary,
    surfaceContainerLowest = AppCard,
    surfaceContainerLow = AppCard,
    surfaceContainer = AppCard,
    surfaceContainerHigh = AppCard,
    surfaceContainerHighest = AppCard,
    error = AppRed,
    onError = Color.White,
    errorContainer = AppRedTint,
    onErrorContainer = AppRed,
    outline = AppDivider,
    outlineVariant = AppDivider,
)

// SF Pro type scale mapped to Material typography slots.
private val typography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp),      // Large Title
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),    // Title 1
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),   // Title 2
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),// Title 3
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),   // Headline
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),  // Callout semibold
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),      // Body
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),     // Subheadline
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),      // Footnote
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),    // Caption
)

@Composable
fun DingYueTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
