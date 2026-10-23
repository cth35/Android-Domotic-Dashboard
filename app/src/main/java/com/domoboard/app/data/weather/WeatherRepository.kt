package com.domoboard.app.data.weather

import com.domoboard.app.data.ForecastDay
import com.domoboard.app.data.WidgetLiveState
import com.domoboard.app.data.WidgetStateEntry
import com.domoboard.app.model.WidgetConfig
import com.domoboard.app.model.WidgetType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class WeatherRepository(private val client: OpenMeteoClient) {

    private val meteoFranceClient = MeteoFranceClient()

    /**
     * Weather changes slowly: no need to poll as often as
     * Domoticz. 5 minutes by default, largely sufficient and avoids
     * soliciting the API for nothing.
     *
     * A single Open-Meteo call per widget is enough for both types
     * (WEATHER and FORECAST) — only the number of days requested differs,
     * the API returns current + daily anyway in the same response.
     */
    fun observeStates(
        widgets: List<WidgetConfig>,
        meteoFranceApiKey: String? = null,
        pollIntervalMs: Long = 5 * 60_000L
    ): Flow<Map<String, WidgetStateEntry>> = flow {
        val weatherWidgets = widgets.filter { it.source?.provider == "open-meteo" }
        if (weatherWidgets.isEmpty()) {
            emit(emptyMap())
            return@flow
        }

        val lastStates = mutableMapOf<String, WidgetStateEntry>()

        while (true) {
            try {
                var anySuccess = false
                for (widget in weatherWidgets) {
                    val lat = widget.source?.latitude
                    val lon = widget.source?.longitude
                    if (lat == null || lon == null) continue

                    val isForecast = widget.widgetType == WidgetType.FORECAST
                    val forecast = client.getForecast(lat, lon, forecastDays = if (isForecast) 7 else 1)

                    val department = widget.source?.department
                    var vigilanceResp: MeteoFranceVigilanceResponse? = null
                    if (!department.isNullOrBlank() && !meteoFranceApiKey.isNullOrBlank()) {
                        vigilanceResp = meteoFranceClient.getVigilance(department, meteoFranceApiKey)
                    }

                    if (forecast != null) {
                        val state = if (isForecast) {
                            val days = buildForecastDays(forecast.daily, forecast.hourly)
                            if (days.isEmpty()) continue
                            WidgetLiveState.Forecast(days)
                        } else {
                            val current = forecast.current
                            val daily = forecast.daily

                            val maxColor = vigilanceResp?.maxColorId
                            val phenos = vigilanceResp?.phenomenonsMaxColor?.mapNotNull { p ->
                                val pid = p.phenomenonId
                                val cid = p.colorId
                                if (pid != null && cid != null && cid > 1) {
                                    VigilanceMapper.phenomenonName(pid) to cid
                                } else null
                            }.orEmpty()

                            WidgetLiveState.Weather(
                                temperature = current?.temperature?.roundToInt() ?: 0,
                                condition = WeatherCodeMapper.label(current?.weatherCode),
                                min = daily?.tempMin?.firstOrNull()?.roundToInt() ?: 0,
                                max = daily?.tempMax?.firstOrNull()?.roundToInt() ?: 0,
                                weatherCode = current?.weatherCode,
                                humidity = current?.humidity,
                                windSpeed = current?.windSpeed,
                                sunrise = daily?.sunrise?.firstOrNull()?.let { formatTimeLabel(it) },
                                sunset = daily?.sunset?.firstOrNull()?.let { formatTimeLabel(it) },
                                vigilanceLevel = maxColor,
                                vigilanceColorName = VigilanceMapper.colorName(maxColor),
                                vigilancePhenomenons = phenos,
                                vigilanceDetails = vigilanceResp?.details.orEmpty()
                            )
                        }
                        lastStates[widget.id] = WidgetStateEntry(state = state, lastUpdate = System.currentTimeMillis())
                        anySuccess = true
                    }
                }
                if (anySuccess || lastStates.isNotEmpty()) {
                    emit(lastStates.toMap())
                }
            } catch (e: Exception) {
                // Ignore and continue
            }
            delay(pollIntervalMs)
        }
    }

    private fun buildForecastDays(daily: OpenMeteoDaily?, hourly: OpenMeteoHourly?): List<ForecastDay> {
        if (daily == null) return emptyList()
        val size = minOf(daily.time.size, daily.weatherCode.size, daily.tempMax.size, daily.tempMin.size)
        if (size == 0) return emptyList()

        return (0 until size).map { i ->
            val code = daily.weatherCode[i]
            
            val periods = if (hourly != null && hourly.time.size >= (i + 1) * 24) {
                listOf(
                    buildPeriod("Matin", 9, i, hourly),
                    buildPeriod("Après-midi", 15, i, hourly),
                    buildPeriod("Soirée", 21, i, hourly)
                )
            } else emptyList()

            ForecastDay(
                dateIso = daily.time[i],
                dayLabel = formatDayLabel(daily.time[i]),
                condition = WeatherCodeMapper.label(code),
                weatherCode = code,
                tempMin = daily.tempMin[i].roundToInt(),
                tempMax = daily.tempMax[i].roundToInt(),
                sunrise = daily.sunrise.getOrNull(i)?.let { formatTimeLabel(it) },
                sunset = daily.sunset.getOrNull(i)?.let { formatTimeLabel(it) },
                precipProb = daily.precipProb.getOrNull(i),
                windSpeed = daily.windSpeedMax.getOrNull(i),
                periods = periods
            )
        }
    }

    private fun buildPeriod(label: String, hour: Int, dayIdx: Int, hourly: OpenMeteoHourly): com.domoboard.app.data.ForecastPeriod {
        val idx = dayIdx * 24 + hour
        val temp = hourly.temperature.getOrNull(idx)?.roundToInt() ?: 0
        val code = hourly.weatherCode.getOrNull(idx)
        return com.domoboard.app.data.ForecastPeriod(
            label = label,
            temp = temp,
            weatherCode = code,
            condition = WeatherCodeMapper.label(code),
            precipProb = hourly.precipProb.getOrNull(idx),
            windSpeed = hourly.windSpeed.getOrNull(idx)
        )
    }

    /** "yyyy-MM-dd" -> "Mon"/"Tue"/... in French. Falls back to "--" if the format is unexpected. */
    private fun formatDayLabel(isoDate: String): String = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(isoDate) ?: return "--"
        SimpleDateFormat("EEE", Locale.FRANCE).format(parsed).replaceFirstChar { it.uppercase() }
    }.getOrDefault("--")

    /** "yyyy-MM-dd'T'HH:mm" -> "HH:mm" */
    private fun formatTimeLabel(isoDateTime: String): String = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.FRANCE).parse(isoDateTime) ?: return "--"
        SimpleDateFormat("HH:mm", Locale.FRANCE).format(parsed)
    }.getOrDefault("--")

    fun close() {
        meteoFranceClient.close()
    }
}
