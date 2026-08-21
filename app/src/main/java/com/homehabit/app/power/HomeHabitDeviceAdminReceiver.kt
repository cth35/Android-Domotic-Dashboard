package com.homehabit.app.power

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Necessaire pour DevicePolicyManager.lockNow() (extinction reelle de
 * l'ecran, pas juste assombrissement). L'utilisateur doit accorder ces
 * droits explicitement via un dialogue systeme (Parametres > Securite >
 * Applications d'administration) — jamais accorde automatiquement,
 * jamais demande sans action explicite dans l'ecran de reglages.
 *
 * Contrairement au "Device Owner" (qui necessite une commande ADB avant
 * meme la premiere configuration de l'appareil), le "Device Admin" est
 * beaucoup plus leger : un simple accord via une popup systeme standard,
 * revocable a tout moment par l'utilisateur dans les parametres.
 */
class HomeHabitDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Si l'utilisateur revoque les droits depuis les parametres
        // systeme, on ne peut plus rien faire cote code — le prochain
        // GET de la config affichera toujours nightScreenOffEnabled=true
        // si c'etait configure ainsi, mais lockNow() echouera
        // silencieusement (SecurityException interceptee dans
        // ScreenPowerController). Pas de reset automatique du reglage
        // ici : mieux vaut que l'utilisateur voie que ca ne marche plus
        // et aille re-accorder les droits, que de perdre silencieusement
        // son reglage.
    }
}
