package com.homehabit.app.data.domoticz

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Domoticz encode la couleur d'un device dans un champ "Color" qui est
 * lui-meme une chaine JSON (ex: {"m":3,"t":0,"r":255,"g":100,"b":50}),
 * avec un mode "m" (1=blanc, 2=temperature, 3=RGB, 4=custom...). On ne
 * gere ici que le cas RGB explicite (le plus courant pour une Hue en
 * mode couleur) : les modes blanc/temperature retombent sur `null`
 * (pas de swatch couleur affiche) plutot que d'inventer une teinte
 * approximative. Best-effort assume, comme pour le snapshot RTSP.
 */
object DomoticzColorParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseToHex(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val dto = runCatching { json.decodeFromString(ColorDto.serializer(), raw) }.getOrNull() ?: return null
        val r = dto.r
        val g = dto.g
        val b = dto.b
        if (r == null || g == null || b == null) return null
        if (r == 0 && g == 0 && b == 0) return null
        return "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
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
        val m: Int? = null,
        val r: Int? = null,
        val g: Int? = null,
        val b: Int? = null
    )
}
