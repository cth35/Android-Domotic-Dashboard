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
 * Planifie les alarmes d'extinction/rallumage via AlarmManager (survit
 * a une app en arriere-plan, contrairement a un simple delay() dans une
 * coroutine liee a l'Activity), et execute les actions elles-memes.
 *
 * Best-effort assume, pas garanti :
 * - Necessite les droits Device Admin pour l'extinction reelle (voir
 *   HomeHabitDeviceAdminReceiver) — sans eux, l'alarme "off" ne fait
 *   rien (l'assombrissement seul reste actif, gere separement par
 *   NightModeEffect, toujours fiable lui).
 * - Les alarmes exactes (setExactAndAllowWhileIdle) necessitent la
 *   permission SCHEDULE_EXACT_ALARM sur Android 12+, potentiellement a
 *   accorder manuellement dans Parametres > Applications > Alarmes et
 *   rappels selon le fabricant.
 * - Les gestionnaires de batterie agressifs de certains fabricants
 *   (MIUI, Samsung...) peuvent tuer l'app en arriere-plan et empecher
 *   le declenchement, malgre l'alarme systeme.
 * - Le rallumage automatique peut atterrir sur l'ecran de verrouillage
 *   si l'appareil a un code/schema configure — recommande de ne pas en
 *   avoir sur un appareil dedie a l'affichage mural.
 * - Non teste sur device reel, comme le reste des integrations
 *   plateforme de ce projet.
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
            // SecurityException possible si SCHEDULE_EXACT_ALARM est
            // refusee : on retombe sur une alarme inexacte plutot que de
            // ne rien planifier du tout.
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

    /** Tente d'eteindre reellement l'ecran. Ne fait rien (silencieusement) si les droits admin ne sont pas accordes. */
    fun performScreenOff(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (!dpm.isAdminActive(deviceAdminComponent(context))) return

        runCatching { dpm.lockNow() }
            .onFailure { Log.w("ScreenPowerController", "lockNow() a echoue: ${it.message}") }
    }

    /**
     * Relance MainActivity avec les flags necessaires pour rallumer
     * l'ecran et passer par-dessus l'ecran de verrouillage si possible
     * (voir MainActivity.onCreate pour setShowWhenLocked/setTurnScreenOn).
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
