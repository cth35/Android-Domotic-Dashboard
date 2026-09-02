package com.homehabit.app.data

/**
 * Represents the "live" state of a widget.
 */
sealed class WidgetLiveState {
    enum class Trend {
        STABLE,
        UP,
        DOWN
    }

    data class Weather(
        val temperature: Int,
        val condition: String,
        val min: Int,
        val max: Int,
        val weatherCode: Int? = null,
        val humidity: Int? = null,
        val windSpeed: Double? = null,
        val sunrise: String? = null, // "HH:mm"
        val sunset: String? = null    // "HH:mm"
    ) : WidgetLiveState()

    data class Light(
        val isOn: Boolean,
        val isColor: Boolean = false,         // True if RGB is supported
        val isWhiteTunable: Boolean = false,  // True if shades of white (WW) are supported
        val brightness: Int? = null,   // 0-100, null if the device is not dimmable
        val colorHex: String? = null   // "#RRGGBB", null if no color or not determinable
    ) : WidgetLiveState()

    data class Thermostat(
        val temperature: Float,
        val trend: Trend = Trend.STABLE
    ) : WidgetLiveState()

    data class Shutter(val percentOpen: Int) : WidgetLiveState()

    data class Lock(val isLocked: Boolean) : WidgetLiveState()

    data class Camera(val isLive: Boolean, val label: String) : WidgetLiveState()

    /**
     * Generic sensor (temp, humidity, rain, wind, UV, barometer,
     * energy, etc.). displayValue directly takes the "Data" field
     * from Domoticz (already formatted with unit, e.g., "21.5 C"). gaugePercent
     * is only filled for naturally bounded values 0-100
     * (humidity, percentage) — for everything else, a gauge would invent
     * an arbitrary scale, so null rather than false precision.
     */
    data class Sensor(
        val displayValue: String,
        val kind: SensorKind = SensorKind.GENERIC,
        val gaugePercent: Float? = null,
        val trend: Trend = Trend.STABLE,
        val tempValue: Double? = null,
        val humidityValue: Double? = null
    ) : WidgetLiveState()

    data class BinarySensor(
        val isOn: Boolean,
        val isContact: Boolean = false
    ) : WidgetLiveState()

    data class Selector(
        val currentLevel: Int,
        val levels: List<String>
    ) : WidgetLiveState()

    /**
     * Domoticz scene or group triggered with one tap. isGroup distinguishes the
     * two: a Group has a real togglable on/off state, a Scene is a
     * trigger without persistent state (always "Off" at rest even
     * right after triggering) — isOn therefore only has lasting meaning for
     * a Group.
     */
    data class Scene(val isGroup: Boolean, val isOn: Boolean) : WidgetLiveState()

    /** 7-day forecast (FORECAST widget), Open-Meteo. */
    data class Forecast(val days: List<ForecastDay>) : WidgetLiveState()

    object Empty : WidgetLiveState()
}

/**
 * One day of the forecast. dayLabel is already formed ("Mon", "Tue"...),
 * no formatting logic to redo on the UI side.
 */
data class ForecastDay(
    val dateIso: String,
    val dayLabel: String,
    val condition: String,
    val weatherCode: Int?,
    val tempMin: Int,
    val tempMax: Int,
    val sunrise: String? = null,
    val sunset: String? = null,
    val precipProb: Int? = null,
    val windSpeed: Double? = null
)

object FakeStateProvider {

    /**
     * Only the camera remains in demo: the weather now has its real
     * client (Open-Meteo), Domoticz handles light/thermostat/shutter/lock.
     */
    fun defaultStates(now: Long = System.currentTimeMillis()): Map<String, WidgetStateEntry> = mapOf(
        "cam_jardin" to WidgetStateEntry(
            WidgetLiveState.Camera(isLive = true, label = "Jardin"),
            now
        )
    )
}
