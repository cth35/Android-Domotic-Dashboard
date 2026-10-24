package com.domoboard.app.data.weather

import android.util.Log
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

    companion object {
        private const val TAG = "MeteoFranceClient"
    }

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

        val cleanKey = apiKey.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
        if (cleanKey.isBlank()) return null

        return runCatching {
            val targetDep = department.trim()

            // 1. Fetch carte vigilance (for exact vigilance color & phenomenons)
            var carteMaxColor: Int? = null
            var carteUpdateTime: String? = null
            val phenomenonsList = mutableListOf<VigilancePhenomenon>()
            var carteSuccess = false

            try {
                val carteUrl = "https://public-api.meteofrance.fr/public/DPVigilance/v1/cartevigilance/encours"
                val carteResp: HttpResponse = httpClient.get(carteUrl) {
                    header("Authorization", "Bearer $cleanKey")
                    header("apikey", cleanKey)
                }
                val carteStatus = carteResp.status.value
                val carteBody = carteResp.bodyAsText()

                if (carteStatus in 200..299) {
                    carteSuccess = true
                    val parsed = json.decodeFromString<MeteoFranceCarteResponse>(carteBody)
                    val prod = parsed.product
                    carteUpdateTime = prod?.updateTime

                    val allDomains = prod?.extractProductDomains(json).orEmpty()

                    val matchedDomains = allDomains.filter { isDepartmentMatch(it.domainIdString, targetDep) }
                    for (d in matchedDomains) {
                        d.maxColorId?.let { color ->
                            carteMaxColor = maxOf(carteMaxColor ?: 1, color)
                        }
                        for (p in d.phenomenonItems) {
                            val pid = p.phenomenonIdInt
                            val cid = p.maxColor
                            if (pid != null && cid != null) {
                                phenomenonsList.add(VigilancePhenomenon(pid, cid))
                            }
                        }
                    }
                    if (matchedDomains.isNotEmpty() && carteMaxColor == null) {
                        carteMaxColor = 1
                    }
                } else {
                    Log.w(TAG, "cartevigilance/encours HTTP error: $carteStatus - ${carteBody.take(150)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "cartevigilance/encours exception: ${e.message}")
            }

            // 2. Fetch textes vigilance (for text commentary details)
            var textesMaxColor: Int? = null
            var textesUpdateTime: String? = null
            val detailsList = mutableListOf<WidgetLiveState.VigilanceDetail>()
            var textesSuccess = false

            try {
                val textesUrl = "https://public-api.meteofrance.fr/public/DPVigilance/v1/textesvigilance/encours"
                val textesResp: HttpResponse = httpClient.get(textesUrl) {
                    header("Authorization", "Bearer $cleanKey")
                    header("apikey", cleanKey)
                }
                val textesStatus = textesResp.status.value
                val textesBody = textesResp.bodyAsText()

                if (textesStatus in 200..299) {
                    textesSuccess = true
                    val parsed = json.decodeFromString<MeteoFranceTextesResponse>(textesBody)
                    textesUpdateTime = parsed.product?.updateTime
                    val allTextes = parsed.product?.textBlocItems.orEmpty()
                    val depTextes = allTextes.filter { isDepartmentMatch(it.domainIdString, targetDep) }

                    for (texte in depTextes) {
                        for (bloc in texte.blocItems) {
                            for (tItem in bloc.textItems) {
                                for (term in tItem.termItems) {
                                    val rCode = term.riskCode?.toIntOrNull() ?: term.riskLevel ?: 1
                                    textesMaxColor = maxOf(textesMaxColor ?: 1, rCode)

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
                } else {
                    Log.w(TAG, "textesvigilance/encours HTTP error: $textesStatus - ${textesBody.take(150)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "textesvigilance/encours exception: ${e.message}")
            }

            // 3. Combine results
            if (carteSuccess || textesSuccess) {
                val finalMaxColor = maxOf(carteMaxColor ?: 1, textesMaxColor ?: 1)
                MeteoFranceVigilanceResponse(
                    domainId = targetDep,
                    maxColorId = finalMaxColor,
                    phenomenonsMaxColor = phenomenonsList.distinctBy { it.phenomenonId },
                    updateTime = carteUpdateTime ?: textesUpdateTime,
                    details = detailsList
                )
            } else {
                Log.e(TAG, "Echec de la recuperation de la vigilance Meteo-France pour le departement $targetDep")
                null
            }
        }.getOrNull()
    }

    private fun isDepartmentMatch(domainId: String?, targetDept: String): Boolean {
        if (domainId.isNullOrBlank()) return false
        val rawDomain = domainId.trim()
        val rawTarget = targetDept.trim()
        if (rawDomain.equals(rawTarget, ignoreCase = true)) return true

        val padDomain = if (rawDomain.length == 1 && rawDomain[0].isDigit()) "0$rawDomain" else rawDomain
        val padTarget = if (rawTarget.length == 1 && rawTarget[0].isDigit()) "0$rawTarget" else rawTarget
        if (padDomain.equals(padTarget, ignoreCase = true)) return true

        return rawDomain.removePrefix("FR").equals(rawTarget.removePrefix("FR"), ignoreCase = true)
    }

    fun close() {
        httpClient.close()
    }
}
