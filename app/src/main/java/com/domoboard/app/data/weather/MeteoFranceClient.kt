package com.domoboard.app.data.weather

import com.domoboard.app.data.WidgetLiveState
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class MeteoFranceClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(Android) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun getVigilance(department: String, apiKey: String): MeteoFranceVigilanceResponse? {
        if (apiKey.isBlank() || department.isBlank()) return null
        return runCatching {
            val url = "https://public-api.meteofrance.fr/public/DPVigilance/v1/textesvigilance/encours"
            val response: HttpResponse = httpClient.get(url) {
                header("apikey", apiKey.trim())
            }
            val status = response.status.value
            val bodyText = response.bodyAsText()

            if (status in 200..299) {
                val textesResp = json.decodeFromString<MeteoFranceTextesResponse>(bodyText)
                val allTextes = textesResp.product?.textBlocItems.orEmpty()
                
                val depTextes = allTextes.filter { it.domainIdString.equals(department.trim(), ignoreCase = true) }
                if (depTextes.isNotEmpty()) {
                    var maxColor = 1
                    val detailsList = mutableListOf<WidgetLiveState.VigilanceDetail>()
                    for (texte in depTextes) {
                        for (bloc in texte.blocItems) {
                            for (tItem in bloc.textItems) {
                                for (term in tItem.termItems) {
                                    val rCode = term.riskCode?.toIntOrNull() ?: 1
                                    if (rCode > maxColor) {
                                        maxColor = rCode
                                    }
                                    for (sub in term.subdivisionText) {
                                        if (sub.text.isNotEmpty() || !sub.boldText.isNullOrBlank()) {
                                            detailsList.add(
                                                WidgetLiveState.VigilanceDetail(
                                                    boldText = sub.boldText,
                                                    textLines = sub.text,
                                                    startTime = term.startTime,
                                                    endTime = term.endTime
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MeteoFranceVigilanceResponse(
                        domainId = department,
                        maxColorId = maxColor,
                        phenomenonsMaxColor = emptyList(),
                        updateTime = textesResp.product?.updateTime,
                        details = detailsList
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }.getOrNull()
    }

    fun close() {
        httpClient.close()
    }
}
