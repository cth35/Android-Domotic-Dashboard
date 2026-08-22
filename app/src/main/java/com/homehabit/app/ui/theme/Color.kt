package com.homehabit.app.ui.theme

import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF0B0C0E)
val SurfaceDark = Color(0xFF1B1C20)
val SurfaceVariantDark = Color(0xFF26282E)

val TextPrimary = Color(0xFFF2F2F0)
val TextSecondary = Color(0xFF7A7D85)
val TextMuted = Color(0xFF5A5C62)

/**
 * Color convention (icons + tinted widget backgrounds), to follow
 * for any new widget instead of inventing a new shade:
 * - AccentGreen(+Surface): active/engaged state (light on, shutter
 *   open, active scene/group) — "it's doing something right now"
 * - AccentRed(+Surface): attention/alert state (unlocked
 *   lock) — never used for a simple "active", only
 *   when the state deserves a look
 * - AccentOrange: heat/energy (temperature, thermostat,
 *   UV, electricity consumption)
 * - AccentBlueMuted: "cold"/informative quantities (humidity,
 *   rain, weather)
 * - TextSecondary/TextMuted: neutral/inactive/unknown state — never any
 *   associated tinted background, just the icon in gray
 */
val AccentBlue = Color(0xFF4A90D9)
val AccentGreen = Color(0xFFA8D67A)
val AccentGreenSurface = Color(0xFF22321F)
val AccentOrange = Color(0xFFE8B26A)
val AccentRed = Color(0xFFE35B5B)
val AccentRedSurface = Color(0xFF32201F)
val AccentBlueMuted = Color(0xFF7FB2E8)
