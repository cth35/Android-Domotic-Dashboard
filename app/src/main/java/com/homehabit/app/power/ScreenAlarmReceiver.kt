package com.homehabit.app.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homehabit.app.data.ConfigRepository

/**
 * Recoit les alarmes planifiees par ScreenPowerController. Une seule
 * alarme AlarmManager ne se repete pas toute seule ici (on utilise
 * setExactAndAllowWhileIdle, pas setRepeating, plus fiable sur Android
 * recent) : chaque declenchement replanifie explicitement le lendemain,
 * que l'action ait reussi ou non.
 */
class ScreenAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCREEN_OFF = "com.homehabit.app.action.SCREEN_OFF"
        const val ACTION_SCREEN_ON = "com.homehabit.app.action.SCREEN_ON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val settings = ConfigRepository(context).current().settings

        when (intent.action) {
            ACTION_SCREEN_OFF -> ScreenPowerController.performScreenOff(context)
            ACTION_SCREEN_ON -> ScreenPowerController.performScreenOn(context)
        }

        ScreenPowerController.scheduleAlarms(context, settings)
    }
}
