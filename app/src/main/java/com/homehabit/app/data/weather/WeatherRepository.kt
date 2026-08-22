package com.homehabit.app.data.weather

import com.homehabit.app.data.ForecastDay
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.data.WidgetStateEntry
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class WeatherRepository(private val client: OpenMeteoClient) {

    /**
     * Weather changes slowly: no need to poll as often as
     * Domoticz. 15 minutes by default, largely sufficient and avoids
     * soliciting the API for nothing.
     *
     * A single Open-Meteo call per widget is enough for both types
     * (WEATHER and FORECAST) — only the number of days requested differs,
     * the API returns current + daily anyway in the same response.
     */
    fun observeStates(
        widgets: List<WidgetConfig>,
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

                    if (forecast != null) {
                        val state = if (isForecast) {
                            val days = buildForecastDays(forecast.daily)
                            if (days.isEmpty()) continue
                            WidgetLiveState.Forecast(days)
                        } else {
                            val current = forecast.current
                            val daily = forecast.daily
                            WidgetLiveState.Weather(
                                temperature = current?.temperature?.roundToInt() ?: 0,
                                condition = WeatherCodeMapper.label(current?.weatherCode),
                                min = daily?.tempMin?.firstOrNull()?.roundToInt() ?: 0,
                                max = daily?.tempMax?.firstOrNull()?.roundToInt() ?: 0,
                                weatherCode = current?.weatherCode,
                                humidity = current?.humidity,
                                windSpeed = current?.windSpeed,
                                sunrise = daily?.sunrise?.firstOrNull()?.let { formatTimeLabel(it) },
                                sunset = daily?.sunset?.firstOrNull()?.let { formatTimeLabel(it) }
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

    private fun buildForecastDays(daily: OpenMeteoDaily?): List<ForecastDay> {
        if (daily == null) return emptyList()
        val size = minOf(daily.time.size, daily.weatherCode.size, daily.tempMax.size, daily.tempMin.size)
        if (size == 0) return emptyList()

        return (0 until size).map { i ->
            val code = daily.weatherCode[i]
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
                windSpeed = daily.windSpeedMax.getOrNull(i)
            )
        }
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
}
