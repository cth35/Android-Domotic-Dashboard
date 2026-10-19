package com.domoboard.app

import android.app.Application
import com.domoboard.app.data.ConfigRepository
import com.domoboard.app.power.ScreenPowerController
import com.domoboard.app.server.ConfigHttpServer

class DomoBoardApp : Application() {

    // Unique instance shared between the UI (DashboardViewModel) and the
    // embedded HTTP server: any modification via one is visible
    // by the other via configFlow.
    val configRepository by lazy { ConfigRepository(this) }

    private val configServer by lazy { ConfigHttpServer(configRepository) }

    override fun onCreate() {
        super.onCreate()
        configServer.start()
        // Covers the very first launch and the case where the app is
        // relaunched without a full restart of the device (BootReceiver
        // only triggers on a real reboot).
        ScreenPowerController.scheduleAlarms(this, configRepository.current().settings)
    }
}
