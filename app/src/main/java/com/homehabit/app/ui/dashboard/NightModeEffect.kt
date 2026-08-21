package com.homehabit.app.ui.dashboard

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.homehabit.app.model.AppSettings
import com.homehabit.app.power.NightModeSchedule
import kotlinx.coroutines.delay

/**
 * Ajuste la luminosite de la fenetre selon l'horaire configure (mode
 * nuit). Ne necessite aucune permission — screenBrightness sur les
 * attributs de la fenetre ne s'applique qu'a cette activite, pas au
 * systeme entier, donc toujours fiable contrairement a l'extinction
 * reelle (voir ScreenPowerController pour cette partie plus fragile).
 *
 * Verifie toutes les 60s plutot qu'en continu : largement suffisant
 * pour une transition qui se joue a l'heure pres, evite de reveiller
 * la composition inutilement.
 */
@Composable
fun NightModeEffect(settings: AppSettings) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    LaunchedEffect(settings.nightModeEnabled, settings.nightStartHour, settings.nightEndHour, settings.nightBrightness) {
        while (true) {
            val isNight = NightModeSchedule.isNightNow(settings)
            val targetBrightness = if (isNight) {
                settings.nightBrightness.coerceIn(0.01f, 1f)
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }

            val window = activity.window
            val params = window.attributes
            if (params.screenBrightness != targetBrightness) {
                params.screenBrightness = targetBrightness
                window.attributes = params
            }

            delay(60_000L)
        }
    }
}
