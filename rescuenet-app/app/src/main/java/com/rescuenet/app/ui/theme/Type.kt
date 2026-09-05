package com.rescuenet.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Default system font family, kept device-native for maximum legibility and
// automatic support for Urdu/Arabic-script fallback fonts on-device.
val RescueNetTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

// Scales up for the accessibility "Large Text" setting (Part 18).
fun scaledTypography(scale: Float): Typography = Typography(
    displayLarge = RescueNetTypography.displayLarge.copy(fontSize = RescueNetTypography.displayLarge.fontSize * scale),
    headlineLarge = RescueNetTypography.headlineLarge.copy(fontSize = RescueNetTypography.headlineLarge.fontSize * scale),
    headlineMedium = RescueNetTypography.headlineMedium.copy(fontSize = RescueNetTypography.headlineMedium.fontSize * scale),
    titleLarge = RescueNetTypography.titleLarge.copy(fontSize = RescueNetTypography.titleLarge.fontSize * scale),
    titleMedium = RescueNetTypography.titleMedium.copy(fontSize = RescueNetTypography.titleMedium.fontSize * scale),
    bodyLarge = RescueNetTypography.bodyLarge.copy(fontSize = RescueNetTypography.bodyLarge.fontSize * scale),
    bodyMedium = RescueNetTypography.bodyMedium.copy(fontSize = RescueNetTypography.bodyMedium.fontSize * scale),
    labelLarge = RescueNetTypography.labelLarge.copy(fontSize = RescueNetTypography.labelLarge.fontSize * scale),
    labelMedium = RescueNetTypography.labelMedium.copy(fontSize = RescueNetTypography.labelMedium.fontSize * scale),
)
