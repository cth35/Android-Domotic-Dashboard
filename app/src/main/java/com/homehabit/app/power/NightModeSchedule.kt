package com.homehabit.app.power

import com.homehabit.app.model.AppSettings
import java.util.Calendar

/**
 * Calcul pur (aucune dependance Android) de la plage "nuit" configuree.
 * Gere le cas ou la plage traverse minuit (ex 22h -> 7h).
 */
object NightModeSchedule {

    fun isNightNow(settings: AppSettings, hour: Int = currentHour()): Boolean {
        if (!settings.nightModeEnabled) return false
        return isHourInNightRange(hour, settings.nightStartHour, settings.nightEndHour)
    }

    fun isHourInNightRange(hour: Int, startHour: Int, endHour: Int): Boolean {
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        return if (start == end) {
            false // plage nulle : jamais nuit, evite une ambiguite (24h "nuit" vs jamais)
        } else if (start < end) {
            hour in start until end
        } else {
            // La plage traverse minuit (ex 22 -> 7) : nuit si on est
            // apres le debut OU avant la fin.
            hour >= start || hour < end
        }
    }

    private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
