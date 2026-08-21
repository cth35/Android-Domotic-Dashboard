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
     * La meteo change lentement : pas besoin de poller aussi souvent que
     * Domoticz. 15 minutes par defaut, largement suffisant et evite de
     * solliciter l'API pour rien.
     *
     * Un seul appel Open-Meteo par widget suffit pour les deux types
     * (WEATHER et FORECAST) — seul le nombre de jours demande differe,
     * l'API renvoie de toute facon current + daily dans la meme reponse.
     */
    fun observeStates(
        widgets: List<WidgetConfig>,
        pollIntervalMs: Long = 15 * 60_000L
    ): Flow<Map<String, WidgetStateEntry>> = flow {
        val weatherWidgets = widgets.filter { it.source?.provider == "open-meteo" }
        if (weatherWidgets.isEmpty()) {
            emit(emptyMap())
            return@flow
        }

        while (true) {
            val states = mutableMapOf<String, WidgetStateEntry>()
            for (widget in weatherWidgets) {
                val lat = widget.source?.latitude
                val lon = widget.source?.longitude
                if (lat == null || lon == null) continue

                val isForecast = widget.widgetType == WidgetType.FORECAST
                val forecast = client.getForecast(lat, lon, forecastDays = if (isForecast) 7 else 1)
                    ?: continue

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
                        max = daily?.tempMax?.firstOrNull()?.roundToInt() ?: 0
                    )
                }

                states[widget.id] = WidgetStateEntry(state = state, lastUpdate = System.currentTimeMillis())
            }
            emit(states)
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
                tempMax = daily.tempMax[i].roundToInt()
            )
        }
    }

    /** "yyyy-MM-dd" -> "Lun"/"Mar"/... en francais. Retombe sur "--" si le format est inattendu. */
    private fun formatDayLabel(isoDate: String): String = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(isoDate) ?: return "--"
        SimpleDateFormat("EEE", Locale.FRANCE).format(parsed).replaceFirstChar { it.uppercase() }
    }.getOrDefault("--")
}
