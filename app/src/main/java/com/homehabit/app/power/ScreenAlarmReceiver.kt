package com.homehabit.app.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homehabit.app.data.ConfigRepository

/**
 * Receives alarms planned by ScreenPowerController. A single
 * AlarmManager alarm does not repeat itself here (we use
 * setExactAndAllowWhileIdle, not setRepeating, more reliable on recent
 * Android): each trigger explicitly reschedules for the next day,
 * whether the action succeeded or not.
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
