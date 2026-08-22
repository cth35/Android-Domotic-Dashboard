package com.homehabit.app.data.domoticz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Response from /json.htm?type=devices&rid=... or type=devices&used=true
 * The Domoticz API returns many optional fields depending on the type
 * of device; we only model those useful for the dashboard.
 */
@Serializable
data class DomoticzDevicesResponse(
    val status: String? = null,
    val result: List<DomoticzDeviceDto>? = null
)

@Serializable
data class DomoticzDeviceDto(
    val idx: String,
    val Name: String? = null,
    val Type: String? = null,
    val SubType: String? = null,
    val SwitchType: String? = null,
    // "On" / "Off" / "Open" / "Closed" / "Stopped" depending on device type
    val Status: String? = null,
    // Raw value displayed by Domoticz (e.g., "21.5 C", "60 %")
    val Data: String? = null,
    // Position for shutters/dimmers (0-100)
    val Level: Int? = null,
    // Measured temperature (sensors, thermostats)
    val Temp: Double? = null,
    // Measured trend (0=stable, 1=up, 2=down)
    val Trend: JsonElement? = null,
    @SerialName("trend")
    val trendValue: JsonElement? = null,
    val TempTrend: JsonElement? = null,
    // Configured setpoint (thermostats)
    val SetPoint: Double? = null,
    val Humidity: Double? = null,
    // Domoticz format: "yyyy-MM-dd HH:mm:ss", local time of the Domoticz server
    val LastUpdate: String? = null,
    // Raw JSON returned by Domoticz for color lights, e.g.:
    // {"m":3,"t":0,"r":255,"g":100,"b":50,"cw":0,"ww":0}
    val Color: String? = null
)

/**
 * Generic response from command endpoints
 * (/json.htm?type=command&param=...).
 */
@Serializable
data class DomoticzCommandResponse(
    val status: String? = null,
    val title: String? = null
) {
    val isOk: Boolean get() = status.equals("OK", ignoreCase = true)
}

/**
 * Response from /json.htm?type=command&param=graph&sensor=temp&idx=IDX&range=day
 * Used for the sparkline of temperature widgets. The "te" field
 * (current temperature of the point) is the one that interests us; the
 * other fields vary according to the type of sensor and are not modeled.
 */
@Serializable
data class DomoticzGraphResponse(
    val result: List<DomoticzGraphPointDto>? = null
)

@Serializable
data class DomoticzGraphPointDto(
    val d: String? = null,
    val te: String? = null
)

/**
 * Response from /json.htm?type=command&param=getscenes (since stable
 * 2023.2). Distinct from getdevices: scenes/groups are a
 * Domoticz resource in their own right.
 */
@Serializable
data class DomoticzScenesResponse(
    val result: List<DomoticzSceneDto>? = null
)

@Serializable
data class DomoticzSceneDto(
    val idx: String,
    val Name: String? = null,
    // "On" / "Off" — only has real meaning for a Group (Scenes are
    // triggers without persistent state, always "Off" at rest)
    val Status: String? = null,
    // "Scene" (trigger, On only) or "Group" (togglable On/Off)
    val Type: String? = null,
    val LastUpdate: String? = null
)
