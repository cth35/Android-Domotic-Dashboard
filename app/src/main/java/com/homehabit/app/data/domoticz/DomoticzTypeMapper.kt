package com.homehabit.app.data.domoticz

import com.homehabit.app.model.WidgetType

/**
 * Domoticz n'expose pas de "categorie widget" directement : il faut
 * deduire le type a partir de Type / SubType / SwitchType, qui varient
 * beaucoup selon le protocole (RFXCOM, Zwave, MQTT...) et le materiel.
 * Cette heuristique couvre les cas les plus courants ; a affiner au
 * contact de vrais appareils si des devices tombent en UNKNOWN.
 */
object DomoticzTypeMapper {

    fun toWidgetType(device: DomoticzDeviceDto): WidgetType {
        val type = device.Type.orEmpty()
        val switchType = device.SwitchType.orEmpty()

        return when {
            switchType.contains("Door Lock", ignoreCase = true) ||
                switchType.contains("Door Contact", ignoreCase = true) -> WidgetType.LOCK

            type.contains("Blinds", ignoreCase = true) ||
                type.contains("RFY", ignoreCase = true) ||
                switchType.contains("Blinds", ignoreCase = true) ||
                switchType.contains("Venetian", ignoreCase = true) -> WidgetType.SHUTTER

            type.contains("Thermostat", ignoreCase = true) ||
                switchType.contains("Thermostat", ignoreCase = true) -> WidgetType.THERMOSTAT

            // Domoticz expose les lumieres RGB/RGBW (Hue et similaires) sous
            // Type = "Color Switch". On affine selon le SubType pour eviter
            // de traiter les lampes "WW" (Warm White) ou "Switch" (White simple)
            // comme du RGB.
            type.contains("Color Switch", ignoreCase = true) -> {
                val subType = device.SubType.orEmpty()
                if (subType.contains("RGB", ignoreCase = true)) {
                    WidgetType.COLOR_LIGHT
                } else {
                    // Fallback sur DIMMER pour les lampes WW ou Switch simple
                    WidgetType.DIMMER
                }
            }

            switchType.contains("Dimmer", ignoreCase = true) -> WidgetType.DIMMER

            switchType.contains("Motion", ignoreCase = true) ||
                switchType.contains("Presence", ignoreCase = true) ||
                switchType.contains("Occupancy", ignoreCase = true) ||
                switchType.contains("Smoke", ignoreCase = true) ||
                switchType.contains("Water", ignoreCase = true) ||
                switchType.contains("Leak", ignoreCase = true) ||
                switchType.contains("Contact", ignoreCase = true) ||
                type.contains("Sensor", ignoreCase = true) -> WidgetType.BINARY_SENSOR

            // Capteurs génériques : couvre la plupart des types de capteurs
            // Domoticz qui ne rentrent dans aucune autre catégorie
            // (température seule, humidité, pluie, vent, UV, baromètre,
            // pourcentage/batterie, compteurs d'énergie...).
            type.contains("Temp", ignoreCase = true) ||
                type.contains("Humidity", ignoreCase = true) ||
                type.contains("Rain", ignoreCase = true) ||
                type.contains("Wind", ignoreCase = true) ||
                type.contains("UV", ignoreCase = true) ||
                type.contains("Barometer", ignoreCase = true) ||
                type.contains("Pressure", ignoreCase = true) ||
                type.contains("Percentage", ignoreCase = true) ||
                type.contains("Usage", ignoreCase = true) ||
                type.contains("kWh", ignoreCase = true) ||
                type.contains("Counter", ignoreCase = true) ||
                type.contains("Custom Sensor", ignoreCase = true) ||
                type.contains("Air Quality", ignoreCase = true) ||
                type.contains("Visibility", ignoreCase = true) ||
                type.contains("Solar Radiation", ignoreCase = true) ||
                type.contains("Soil Moisture", ignoreCase = true) ||
                type.contains("Leaf Wetness", ignoreCase = true) ||
                type.contains("General", ignoreCase = true) -> WidgetType.SENSOR

            type.contains("Light", ignoreCase = true) ||
                type.contains("Switch", ignoreCase = true) ||
                switchType.contains("On/Off", ignoreCase = true) -> WidgetType.LIGHT

            else -> WidgetType.UNKNOWN
        }
    }
}

/** Un device Domoticz decouvert, avec son type de widget deduit. */
data class DiscoveredDomoticzDevice(
    val idx: String,
    val name: String,
    val widgetType: WidgetType,
    val raw: DomoticzDeviceDto
)

/** Une scene/groupe Domoticz decouverte, ressource separee des devices. */
data class DiscoveredDomoticzScene(
    val idx: String,
    val name: String,
    val isGroup: Boolean
)
