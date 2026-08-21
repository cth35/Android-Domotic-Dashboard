package com.homehabit.app.ui.theme

import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF0B0C0E)
val SurfaceDark = Color(0xFF1B1C20)
val SurfaceVariantDark = Color(0xFF26282E)

val TextPrimary = Color(0xFFF2F2F0)
val TextSecondary = Color(0xFF7A7D85)
val TextMuted = Color(0xFF5A5C62)

/**
 * Convention de couleur (icones + fonds teintes des widgets), a suivre
 * pour tout nouveau widget plutot que d'inventer une nouvelle teinte :
 * - AccentGreen(+Surface)  : etat actif/engage (lumiere allumee, volet
 *   ouvert, scene/groupe actif) — "ca fait quelque chose en ce moment"
 * - AccentRed(+Surface)    : etat d'attention/alerte (serrure
 *   deverrouillee) — jamais utilise pour un simple "actif", uniquement
 *   quand l'etat merite un coup d'oeil
 * - AccentOrange           : chaleur/energie (temperature, thermostat,
 *   UV, conso electrique)
 * - AccentBlueMuted        : grandeurs "froides"/informatives (humidite,
 *   pluie, meteo)
 * - TextSecondary/TextMuted: etat neutre/inactif/inconnu — jamais de
 *   fond teinte associe, juste l'icone en gris
 */
val AccentBlue = Color(0xFF4A90D9)
val AccentGreen = Color(0xFFA8D67A)
val AccentGreenSurface = Color(0xFF22321F)
val AccentOrange = Color(0xFFE8B26A)
val AccentRed = Color(0xFFE35B5B)
val AccentRedSurface = Color(0xFF32201F)
val AccentBlueMuted = Color(0xFF7FB2E8)
