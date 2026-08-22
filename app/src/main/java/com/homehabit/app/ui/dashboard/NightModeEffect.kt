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
 * Adjusts the window brightness according to the configured schedule (night
 * mode). Requires no permission — screenBrightness on window
 * attributes only applies to this activity, not the whole
 * system, so always reliable unlike actual shutdown
 * (see ScreenPowerController for this more fragile part).
 *
 * Checks every 60s rather than continuously: largely sufficient
 * for a transition that plays out to the hour, avoids waking up
 * the composition unnecessarily.
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
