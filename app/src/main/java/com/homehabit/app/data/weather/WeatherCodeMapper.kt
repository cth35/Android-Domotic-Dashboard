package com.homehabit.app.data.weather

/**
 * Open-Meteo returns a "weather_code" following the WMO (World
 * Meteorological Organization) table, standard but not really readable
 * as is. This function translates the most common values in
 * France; unlisted codes fall back to a generic label
 * rather than crashing or displaying a raw number.
 */
object WeatherCodeMapper {

    fun label(code: Int?): String = when (code) {
        0 -> "Ciel degage"
        1 -> "Peu nuageux"
        2 -> "Partiellement nuageux"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        51, 53, 55 -> "Bruine"
        56, 57 -> "Bruine verglacante"
        61, 63, 65 -> "Pluie"
        66, 67 -> "Pluie verglacante"
        71, 73, 75 -> "Neige"
        77 -> "Grains de neige"
        80, 81, 82 -> "Averses"
        85, 86 -> "Averses de neige"
        95 -> "Orage"
        96, 99 -> "Orage avec grele"
        else -> "Conditions inconnues"
    }
}
