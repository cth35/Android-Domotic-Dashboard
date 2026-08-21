package com.homehabit.app.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Client pour l'API Open-Meteo (https://open-meteo.com), gratuite et sans
 * cle API. Un seul appel suffit : temperature courante + code meteo +
 * min/max/code par jour (jusqu'a 7 jours pour le widget prevision).
 */
class OpenMeteoClient {

    private val httpClient = HttpClient(Android) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 1
    ): OpenMeteoResponse? = runCatching {
        httpClient.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current", "temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m")
            parameter("daily", "temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset,precipitation_probability_max,wind_speed_10m_max")
            parameter("forecast_days", forecastDays.coerceIn(1, 16))
            parameter("timezone", "auto")
        }.body<OpenMeteoResponse>()
    }.getOrNull()

    fun close() {
        httpClient.close()
    }
}
