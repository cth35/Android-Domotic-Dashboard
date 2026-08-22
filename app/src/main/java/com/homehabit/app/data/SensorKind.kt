package com.homehabit.app.data

/**
 * Category of a generic sensor, used to choose the icon and
 * determine if a visual gauge makes sense (only for
 * naturally bounded values 0-100).
 */
enum class SensorKind {
    TEMPERATURE,
    HUMIDITY,
    RAIN,
    WIND,
    UV,
    BAROMETER,
    PERCENTAGE,
    ENERGY,
    GENERIC
}
