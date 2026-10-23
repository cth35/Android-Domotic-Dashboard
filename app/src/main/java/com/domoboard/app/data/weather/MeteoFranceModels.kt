package com.domoboard.app.data.weather

import com.domoboard.app.data.WidgetLiveState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class MeteoFranceTextesResponse(
    @SerialName("product") val product: MeteoFranceTextesProduct? = null
)

@Serializable
data class MeteoFranceTextesProduct(
    @SerialName("update_time") val updateTime: String? = null,
    @SerialName("text_bloc_items") val textBlocItems: List<MeteoFranceTexteDomain> = emptyList()
)

@Serializable
data class MeteoFranceTexteDomain(
    @SerialName("domain_id") val domainId: JsonElement? = null,
    @SerialName("domain_name") val domainName: String? = null,
    @SerialName("bloc_id") val blocId: String? = null,
    @SerialName("bloc_items") val blocItems: List<MeteoFranceBlocItem> = emptyList()
) {
    val domainIdString: String?
        get() = runCatching { domainId?.jsonPrimitive?.content }.getOrNull()
}

@Serializable
data class MeteoFranceBlocItem(
    @SerialName("id") val id: String? = null,
    @SerialName("text_items") val textItems: List<MeteoFranceTextItem> = emptyList()
)

@Serializable
data class MeteoFranceTextItem(
    @SerialName("hazard_name") val hazardName: String? = null,
    @SerialName("term_items") val termItems: List<MeteoFranceTermItem> = emptyList()
)

@Serializable
data class MeteoFranceTermItem(
    @SerialName("risk_name") val riskName: String? = null,
    @SerialName("risk_code") val riskCode: String? = null,
    @SerialName("risk_level") val riskLevel: Int? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("subdivision_text") val subdivisionText: List<MeteoFranceSubdivisionText> = emptyList()
)

@Serializable
data class MeteoFranceSubdivisionText(
    @SerialName("bold_text") val boldText: String? = null,
    @SerialName("text") val text: List<String> = emptyList()
)

data class MeteoFranceVigilanceResponse(
    val domainId: String? = null,
    val maxColorId: Int? = null,
    val phenomenonsMaxColor: List<VigilancePhenomenon> = emptyList(),
    val updateTime: String? = null,
    val details: List<WidgetLiveState.VigilanceDetail> = emptyList()
)

@Serializable
data class VigilancePhenomenon(
    @SerialName("phenomenon_id") val phenomenonId: Int? = null,
    @SerialName("color_id") val colorId: Int? = null
)

fun formatVigilanceTime(isoDateTime: String?): String? {
    if (isoDateTime.isNullOrBlank()) return null
    return runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = format.parse(isoDateTime.substringBefore("Z")) ?: return isoDateTime
        SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE).format(parsed)
    }.getOrDefault(isoDateTime)
}

object VigilanceMapper {
    fun phenomenonName(id: Int?): String = when (id) {
        1 -> "Vent violent"
        2 -> "Pluie-Inondation"
        3 -> "Orages"
        4 -> "Inondation"
        5 -> "Neige-Verglas"
        6 -> "Canicule"
        7 -> "Grand Froid"
        8 -> "Avalanches"
        9 -> "Vagues-Submersion"
        else -> "Risque météo"
    }

    fun colorName(colorId: Int?): String = when (colorId) {
        1 -> "Vert"
        2 -> "Jaune"
        3 -> "Orange"
        4 -> "Rouge"
        else -> "Inconnu"
    }
}
