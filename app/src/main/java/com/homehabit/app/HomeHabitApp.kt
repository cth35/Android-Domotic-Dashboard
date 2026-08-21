package com.homehabit.app

import android.app.Application
import com.homehabit.app.data.ConfigRepository
import com.homehabit.app.power.ScreenPowerController
import com.homehabit.app.server.ConfigHttpServer

class HomeHabitApp : Application() {

    // Instance unique partagée entre l'UI (DashboardViewModel) et le
    // serveur HTTP embarqué : toute modification via l'un est visible
    // par l'autre via configFlow.
    val configRepository by lazy { ConfigRepository(this) }

    private val configServer by lazy { ConfigHttpServer(configRepository) }

    override fun onCreate() {
        super.onCreate()
        configServer.start()
        // Couvre le tout premier lancement et le cas ou l'app est
        // relancee sans redemarrage complet de l'appareil (BootReceiver
        // ne se declenche que sur un vrai reboot).
        ScreenPowerController.scheduleAlarms(this, configRepository.current().settings)
    }
}
