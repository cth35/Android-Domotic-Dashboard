package com.domoboard.app.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.domoboard.app.data.ConfigRepository

/**
 * Les alarmes AlarmManager sont perdues au redemarrage de l'appareil —
 * necessaire de les replanifier explicitement au boot, sinon le mode
 * nuit "extinction reelle" reste silencieusement casse jusqu'au
 * prochain lancement manuel de l'app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = ConfigRepository(context).current().settings
        ScreenPowerController.scheduleAlarms(context, settings)
    }
}
