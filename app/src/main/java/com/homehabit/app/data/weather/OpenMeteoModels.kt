package com.homehabit.app.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reponse de https://api.open-meteo.com/v1/forecast
 * (parametres current=temperature_2m,weather_code et
 * daily=temperature_2m_max,temperature_2m_min,weather_code utilises).
 */
@Serializable
data class OpenMeteoResponse(
    val current: OpenMeteoCurrent? = null,
    val daily: OpenMeteoDaily? = null
)

@Serializable
data class OpenMeteoCurrent(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null
)

@Serializable
data class OpenMeteoDaily(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val tempMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double> = emptyList()
)
