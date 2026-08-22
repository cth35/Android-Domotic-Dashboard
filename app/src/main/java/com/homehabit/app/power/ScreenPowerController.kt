package com.homehabit.app.power

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.homehabit.app.MainActivity
import com.homehabit.app.model.AppSettings
import java.util.Calendar

/**
 * Plans the off/on alarms via AlarmManager (survives
 * a background app, unlike a simple delay() in a
 * coroutine linked to the Activity), and executes the actions themselves.
 *
 * Best-effort assumed, not guaranteed:
 * - Requires Device Admin rights for actual shutdown (see
 *   HomeHabitDeviceAdminReceiver) — without them, the "off" alarm does
 *   nothing (dimming alone remains active, managed separately by
 *   NightModeEffect, which is always reliable).
 * - Exact alarms (setExactAndAllowWhileIdle) require the
 *   SCHEDULE_EXACT_ALARM permission on Android 12+, potentially to
 *   be granted manually in Settings > Apps > Alarms &
 *   reminders depending on the manufacturer.
 * - Aggressive battery managers from some manufacturers
 *   (MIUI, Samsung...) can kill the app in the background and prevent
 *   triggering, despite the system alarm.
 * - Automatic turning on may end up on the lock screen
 *   if the device has a configured code/pattern — recommended not to
 *   have one on a device dedicated to wall display.
 * - Not tested on real device, like the rest of the platform
 *   integrations of this project.
 */
object ScreenPowerController {

    private const val REQUEST_CODE_OFF = 1001
    private const val REQUEST_CODE_ON = 1002

    fun scheduleAlarms(context: Context, settings: AppSettings) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        cancelAlarms(context, alarmManager)

        if (!settings.nightModeEnabled || !settings.nightScreenOffEnabled) return

        scheduleNextOccurrence(context, alarmManager, settings.nightStartHour, ScreenAlarmReceiver.ACTION_SCREEN_OFF, REQUEST_CODE_OFF)
        scheduleNextOccurrence(context, alarmManager, settings.nightEndHour, ScreenAlarmReceiver.ACTION_SCREEN_ON, REQUEST_CODE_ON)
    }

    fun cancelAlarms(context: Context, alarmManager: AlarmManager? = null) {
        val manager = alarmManager
            ?: context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        manager.cancel(pendingIntentFor(context, ScreenAlarmReceiver.ACTION_SCREEN_OFF, REQUEST_CODE_OFF))
        manager.cancel(pendingIntentFor(context, ScreenAlarmReceiver.ACTION_SCREEN_ON, REQUEST_CODE_ON))
    }

    private fun scheduleNextOccurrence(
        context: Context,
        alarmManager: AlarmManager,
        hour: Int,
        action: String,
        requestCode: Int
    ) {
        val triggerAt = nextOccurrenceMillis(hour.coerceIn(0, 23))
        val pendingIntent = pendingIntentFor(context, action, requestCode)
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }.onFailure {
            // SecurityException possible if SCHEDULE_EXACT_ALARM is
            // refused: we fall back to an inexact alarm rather than
            // planning nothing at all.
            runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent) }
        }
    }

    private fun nextOccurrenceMillis(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun pendingIntentFor(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ScreenAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Attempts to actually turn off the screen. Does nothing (silently) if admin rights are not granted. */
    fun performScreenOff(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (!dpm.isAdminActive(deviceAdminComponent(context))) return

        runCatching { dpm.lockNow() }
            .onFailure { Log.w("ScreenPowerController", "lockNow() a echoue: ${it.message}") }
    }

    /**
     * Relaunches MainActivity with the necessary flags to turn back on
     * the screen and pass over the lock screen if possible
     * (see MainActivity.onCreate for setShowWhenLocked/setTurnScreenOn).
     */
    fun performScreenOn(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        context.startActivity(intent)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return dpm.isAdminActive(deviceAdminComponent(context))
    }

    fun deviceAdminComponent(context: Context): ComponentName =
        ComponentName(context, HomeHabitDeviceAdminReceiver::class.java)
}
