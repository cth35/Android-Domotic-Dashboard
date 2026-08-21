package com.homehabit.app.data

/**
 * Catégorie d'un capteur générique, utilisée pour choisir l'icône et
 * déterminer si une jauge visuelle a un sens (uniquement pour les
 * grandeurs naturellement bornées 0-100).
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
