package com.homehabit.app.data.domoticz

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * G�re le parsing des couleurs Domoticz (RGB et Temp�rature de blanc).
 */
object DomoticzColorParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseToHex(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val dto = runCatching { json.decodeFromString<ColorDto>(raw) }.getOrNull() ?: return null
        
        return when (dto.m) {
            3 -> { // RGB Mode
                val r = dto.r ?: 0
                val g = dto.g ?: 0
                val b = dto.b ?: 0
                if (r == 0 && g == 0 && b == 0) return null
                "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }
            2 -> { // Mode Temp�rature de blanc (WW)
                // t va de 0 (froid) � 255 (chaud) ou inversement selon le hardware.
                // On fait une approximation visuelle pour le dashboard.
                val temp = dto.t ?: 128
                approximateWhiteTempToHex(temp)
            }
            else -> null
        }
    }

    /**
     * Convertit une valeur de temp�rature Domoticz (0-255) en couleur hexad�cimale
     * pour l'affichage visuel (ambiance chaude � froide).
     */
    private fun approximateWhiteTempToHex(t: Int): String {
        val factor = t.coerceIn(0, 255) / 255f
        // D�grad� de #D1EAFF (froid, t=0) vers #FFAE00 (chaud, t=255)
        val r = (209 + (255 - 209) * factor).toInt()
        val g = (234 + (174 - 234) * factor).toInt()
        val b = (255 + (0 - 255) * factor).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    fun hexToRgb(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        return Triple(r, g, b)
    }

    @Serializable
    private data class ColorDto(
        val m: Int? = null, // Mode
        val r: Int? = null,
        val g: Int? = null,
        val b: Int? = null,
        val t: Int? = null, // Temperature
        val ww: Int? = null, // Warm white level
        val cw: Int? = null  // Cold white level
    )
}
