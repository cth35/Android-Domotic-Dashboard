package com.homehabit.app.data.domoticz

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Client for the Domoticz JSON API (/json.htm). Intentionally remains
 * simple: one call = one GET request with query parameters, as
 * Domoticz expects. No dependency on a reflection library like
 * Retrofit, the API is too heterogeneous from one device type to another
 * for that to be very useful.
 */
class DomoticzClient(private val config: DomoticzConfig) {

    private val httpClient = HttpClient(Android) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }

        if (!config.username.isNullOrBlank()) {
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(username = config.username, password = config.password.orEmpty())
                    }
                    sendWithoutRequest { true }
                }
            }
        }
    }

    /** Retrieves the current state of a device via its Domoticz idx. */
    suspend fun getDevice(idx: String): DomoticzDeviceDto? = runCatching {
        val response: DomoticzDevicesResponse = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "getdevices")
            parameter("rid", idx)
        }.body()
        response.result?.firstOrNull()
    }.getOrNull()

    /**
     * Lists all "used" devices (configured and visible) on the Domoticz side.
     * Used for discovery when adding a widget.
     */
    suspend fun getUsedDevices(): List<DomoticzDeviceDto> = runCatching {
        val response: DomoticzDevicesResponse = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "getdevices")
            parameter("filter", "all")
            parameter("used", "true")
            parameter("order", "Name")
        }.body()
        response.result.orEmpty()
    }.getOrDefault(emptyList())

    suspend fun switchLight(idx: String, turnOn: Boolean): Boolean =
        sendSwitchCommand(idx, if (turnOn) "On" else "Off")

    suspend fun setDimmerLevel(idx: String, levelPercent: Int): Boolean =
        sendSwitchCommand(idx, "Set Level", levelParam = levelPercent)

    suspend fun openShutter(idx: String): Boolean = sendSwitchCommand(idx, "Open")

    suspend fun closeShutter(idx: String): Boolean = sendSwitchCommand(idx, "Close")

    suspend fun stopShutter(idx: String): Boolean = sendSwitchCommand(idx, "Stop")

    suspend fun setShutterLevel(idx: String, levelPercent: Int): Boolean =
        sendSwitchCommand(idx, "Set Level", levelParam = levelPercent)

    suspend fun setThermostatSetpoint(idx: String, setpoint: Float): Boolean = runCatching {
        val response = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "setsetpoint")
            parameter("idx", idx)
            parameter("setpoint", setpoint)
        }
        response.status.isSuccess()
    }.getOrDefault(false)

    /**
     * Changes the color (and optionally the brightness) of an RGB/RGBW light
     * like Hue. Reconstructs the "Color" JSON expected by Domoticz
     * in explicit RGB mode (m=3).
     */
    suspend fun setColor(idx: String, hex: String, brightnessPercent: Int? = null): Boolean = runCatching {
        val (r, g, b) = DomoticzColorParser.hexToRgb(hex)
        val colorJson = """{"m":3,"t":0,"r":$r,"g":$g,"b":$b,"cw":0,"ww":0}"""
        val response = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "setcolbrightnessvalue")
            parameter("idx", idx)
            parameter("color", colorJson)
            parameter("brightness", brightnessPercent ?: 100)
            parameter("iswhite", false)
        }
        response.status.isSuccess()
    }.getOrDefault(false)

    suspend fun switchLock(idx: String, locked: Boolean): Boolean =
        sendSwitchCommand(idx, if (locked) "On" else "Off")

    private suspend fun sendSwitchCommand(
        idx: String,
        switchCmd: String,
        levelParam: Int? = null
    ): Boolean = runCatching {
        val response = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "switchlight")
            parameter("idx", idx)
            parameter("switchcmd", switchCmd)
            if (levelParam != null) parameter("level", levelParam)
        }
        response.status.isSuccess()
    }.getOrDefault(false)

    /**
     * Temperature history of the last 24h (points ~5min depending on
     * Domoticz config). Used for the sparkline of temperature widgets.
     * Best-effort: returns null if the call fails or if the device does not
     * have temperature (e.g., non-temp sensor), rather than crashing.
     */
    suspend fun getTempGraphDay(idx: String): List<DomoticzGraphPointDto>? = runCatching {
        val response: DomoticzGraphResponse = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "graph")
            parameter("sensor", "temp")
            parameter("idx", idx)
            parameter("range", "day")
        }.body()
        response.result?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Lists all configured scenes/groups on the Domoticz side. Distinct
     * resource from getdevices — same GET pattern, but separate Domoticz table.
     */
    suspend fun getScenes(): List<DomoticzSceneDto> = runCatching {
        val response: DomoticzScenesResponse = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "getscenes")
        }.body()
        response.result.orEmpty()
    }.getOrDefault(emptyList())

    /**
     * Triggers a scene (switchCmd must remain "On" — Domoticz does not allow
     * "Off" on a real Scene, only on a Group) or toggles
     * a group on/off.
     */
    suspend fun switchScene(idx: String, switchCmd: String): Boolean = runCatching {
        val response = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "switchscene")
            parameter("idx", idx)
            parameter("switchcmd", switchCmd)
        }
        response.status.isSuccess()
    }.getOrDefault(false)

    fun close() {
        httpClient.close()
    }
}
