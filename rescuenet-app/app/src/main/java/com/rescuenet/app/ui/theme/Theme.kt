package com.rescuenet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = RescueNavy,
    onPrimary = Color.WhiteCompat,
    secondary = RescueTeal,
    error = EmergencyRed,
    background = SurfaceLight,
    surface = SurfaceLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = RescueTeal,
    onPrimary = Color.BlackCompat,
    secondary = RescueNavyLight,
    error = EmergencyRed,
    background = SurfaceDark,
    surface = SurfaceDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    outline = OutlineDark,
)

/**
 * App-wide theme. [darkTheme] and [largeText]/[highContrast] are driven from user Settings
 * (Part 18 — accessibility) rather than being hardcoded, so every screen respects them.
 */
@Composable
fun RescueNetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    largeText: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColors = if (darkTheme) DarkColors else LightColors
    val colors = if (highContrast) baseColors.copy(
        outline = if (darkTheme) Color.WhiteCompat else Color.BlackCompat
    ) else baseColors

    val typography = if (largeText) scaledTypography(1.25f) else RescueNetTypography

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content
    )
}

// Small local alias helpers to avoid importing androidx.compose.ui.graphics.Color under two names above.
private object Color {
    val WhiteCompat = androidx.compose.ui.graphics.Color.White
    val BlackCompat = androidx.compose.ui.graphics.Color.Black
}
