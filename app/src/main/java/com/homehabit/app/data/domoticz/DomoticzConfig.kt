package com.homehabit.app.data.domoticz

import com.homehabit.app.model.AppSettings

/**
 * Runtime config of the Domoticz HTTP client. Now derived from
 * AppSettings (persisted, editable via the settings screen or the
 * browser) instead of being hardcoded.
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
