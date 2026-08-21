package com.homehabit.app.data.weather

/**
 * Open-Meteo renvoie un code "weather_code" suivant la table WMO (World
 * Meteorological Organization), standard mais pas franchement lisible
 * tel quel. Cette fonction traduit les valeurs les plus courantes en
 * France ; les codes non listes retombent sur un libelle generique
 * plutot que de planter ou d'afficher un nombre brut.
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
