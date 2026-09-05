package com.rescuenet.app.ui.theme

import androidx.compose.ui.graphics.Color

// RescueNet design tokens.
// Palette is deliberately restrained: one strong "alert" red reserved ONLY for the
// emergency action and critical severity, so it never competes for attention elsewhere.

// Brand
val RescueNavy = Color(0xFF0B1F3A)      // primary — trust, calm, "command center" feel
val RescueNavyLight = Color(0xFF1C3A63)
val RescueTeal = Color(0xFF0FA3A3)      // secondary — "network alive" accent

// Semantic severity (color + always paired with an icon/label per accessibility rules)
val SeverityCritical = Color(0xFFE1341E)
val SeverityHigh = Color(0xFFF08A24)
val SeverityModerate = Color(0xFFF2C230)
val SeveritySafeGreen = Color(0xFF2FA84F)
val SeverityUnknownGray = Color(0xFF8A93A6)

// Emergency action
val EmergencyRed = Color(0xFFD8261C)
val EmergencyRedPressed = Color(0xFFB01F17)

// Neutrals
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceDark = Color(0xFF0E1420)
val OnSurfaceLight = Color(0xFF10151F)
val OnSurfaceDark = Color(0xFFEDEFF3)
val OutlineLight = Color(0xFFD7DBE2)
val OutlineDark = Color(0xFF2B3547)
