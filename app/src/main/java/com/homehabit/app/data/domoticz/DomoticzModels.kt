package com.homehabit.app.data.domoticz

import kotlinx.serialization.Serializable

/**
 * Reponse de /json.htm?type=devices&rid=... ou type=devices&used=true
 * L'API Domoticz renvoie beaucoup de champs optionnels selon le type
 * d'appareil ; on ne modelise que ceux utiles au dashboard.
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
    // "On" / "Off" / "Open" / "Closed" / "Stopped" selon le type d'appareil
    val Status: String? = null,
    // Valeur brute affichee par Domoticz (ex "21.5 C", "60 %")
    val Data: String? = null,
    // Position pour volets/dimmers (0-100)
    val Level: Int? = null,
    // Temperature mesuree (capteurs, thermostats)
    val Temp: Double? = null,
    // Consigne configuree (thermostats)
    val SetPoint: Double? = null,
    val Humidity: Double? = null,
    // Format Domoticz : "yyyy-MM-dd HH:mm:ss", heure locale du serveur Domoticz
    val LastUpdate: String? = null,
    // JSON brut renvoye par Domoticz pour les lumieres couleur, ex:
    // {"m":3,"t":0,"r":255,"g":100,"b":50,"cw":0,"ww":0}
    val Color: String? = null
)

/**
 * Reponse generique des endpoints de commande
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
 * Reponse de /json.htm?type=command&param=graph&sensor=temp&idx=IDX&range=day
 * Utilisee pour la sparkline des widgets temperature. Le champ "te"
 * (temperature actuelle du point) est celui qui nous interesse ; les
 * autres champs varient selon le type de capteur et ne sont pas modelises.
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
 * Reponse de /json.htm?type=command&param=getscenes (depuis stable
 * 2023.2). Distinct de getdevices : les scenes/groupes sont une
 * ressource Domoticz a part entiere.
 */
@Serializable
data class DomoticzScenesResponse(
    val result: List<DomoticzSceneDto>? = null
)

@Serializable
data class DomoticzSceneDto(
    val idx: String,
    val Name: String? = null,
    // "On" / "Off" — n'a de sens reel que pour un Group (les Scenes sont
    // des declencheurs sans etat persistant, toujours "Off" au repos)
    val Status: String? = null,
    // "Scene" (declencheur, On uniquement) ou "Group" (togglable On/Off)
    val Type: String? = null,
    val LastUpdate: String? = null
)
