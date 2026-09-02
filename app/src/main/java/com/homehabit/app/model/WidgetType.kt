package com.homehabit.app.model

enum class WidgetType {
    WEATHER,
    FORECAST,
    CAMERA,
    LIGHT,
    DIMMER,
    COLOR_LIGHT,
    THERMOSTAT,
    SHUTTER,
    LOCK,
    SENSOR,
    SCENE,
    SWITCH,
    SELECTOR,
    BINARY_SENSOR,
    CLOCK,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String): WidgetType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}
