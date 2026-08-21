package com.homehabit.app.data.domoticz

import com.homehabit.app.model.AppSettings

/**
 * Config runtime du client HTTP Domoticz. Desormais derivee de
 * AppSettings (persistee, editable via l'ecran de reglages ou le
 * navigateur) plutot que d'etre en dur.
 */
data class DomoticzConfig(
    val host: String = "192.168.1.10",
    val port: Int = 8080,
    val useHttps: Boolean = false,
    val username: String? = null,
    val password: String? = null
) {
    val baseUrl: String
        get() = "${if (useHttps) "https" else "http"}://$host:$port"
}

fun AppSettings.toDomoticzConfig(): DomoticzConfig = DomoticzConfig(
    host = domoticzHost,
    port = domoticzPort,
    useHttps = domoticzUseHttps,
    username = domoticzUsername,
    password = domoticzPassword
)
