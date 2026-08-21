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
 * Client pour l'API JSON de Domoticz (/json.htm). Reste volontairement
 * simple : un appel = une requete GET avec parametres de query, comme
 * l'attend Domoticz. Pas de dependance a une lib de reflexion type
 * Retrofit, l'API est trop heterogene d'un type d'appareil a l'autre
 * pour que ca apporte grand chose.
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

    /** Recupere l'etat courant d'un device via son idx Domoticz. */
    suspend fun getDevice(idx: String): DomoticzDeviceDto? = runCatching {
        val response: DomoticzDevicesResponse = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "getdevices")
            parameter("rid", idx)
        }.body()
        response.result?.firstOrNull()
    }.getOrNull()

    /**
     * Liste tous les devices "used" (configures et visibles) cote Domoticz.
     * Utilise pour la decouverte lors de l'ajout d'un widget.
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
     * Change la couleur (et optionnellement la luminosite) d'une lumiere
     * RGB/RGBW type Hue. Reconstruit le JSON "Color" attendu par Domoticz
     * en mode RGB explicite (m=3).
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
     * Historique de temperature des dernieres 24h (points ~5min selon la
     * config Domoticz). Utilise pour la sparkline des widgets temperature.
     * Best-effort : retourne null si l'appel echoue ou si le device n'a
     * pas de temperature (ex. capteur non-temp), plutot que de planter.
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
     * Liste toutes les scenes/groupes configures cote Domoticz. Ressource
     * distincte de getdevices — meme motif GET, mais table Domoticz a part.
     */
    suspend fun getScenes(): List<DomoticzSceneDto> = runCatching {
        val response: DomoticzScenesResponse = httpClient.get("${config.baseUrl}/json.htm") {
            parameter("type", "command")
            parameter("param", "getscenes")
        }.body()
        response.result.orEmpty()
    }.getOrDefault(emptyList())

    /**
     * Declenche une scene (switchCmd doit rester "On" — Domoticz n'autorise
     * pas "Off" sur une vraie Scene, seulement sur un Group) ou bascule
     * un groupe on/off.
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
