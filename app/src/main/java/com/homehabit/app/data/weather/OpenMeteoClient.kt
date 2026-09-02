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
 * Client for the Open-Meteo API (https://open-meteo.com), free and without
 * API key. One call is enough: current temperature + weather code +
 * min/max/code per day (up to 7 days for the forecast widget).
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
            parameter("hourly", "temperature_2m,weather_code,precipitation_probability,wind_speed_10m")
            parameter("forecast_days", forecastDays.coerceIn(1, 16))
            parameter("timezone", "auto")
        }.body<OpenMeteoResponse>()
    }.getOrNull()

    fun close() {
        httpClient.close()
    }
}
