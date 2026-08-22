package com.homehabit.app.data.domoticz

import com.homehabit.app.model.WidgetType

/**
 * Domoticz does not expose a "widget category" directly: it is necessary to
 * deduce the type from Type / SubType / SwitchType, which vary
 * a lot depending on the protocol (RFXCOM, Zwave, MQTT...) and the hardware.
 * This heuristic covers the most common cases; to be refined when
 * in contact with real devices if some devices fall into UNKNOWN.
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

            // Domoticz exposes RGB/RGBW lights (Hue and similar) under
            // Type = "Color Switch". We refine according to the SubType to avoid
            // treating "WW" (Warm White) or "Switch" (simple White) lamps
            // as RGB.
            type.contains("Color Switch", ignoreCase = true) -> {
                val subType = device.SubType.orEmpty()
                if (subType.contains("RGB", ignoreCase = true)) {
                    WidgetType.COLOR_LIGHT
                } else {
                    // Fallback to DIMMER for WW or simple Switch lamps
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

            // Generic sensors: covers most types of Domoticz sensors
            // that do not fit into any other category
            // (temperature alone, humidity, rain, wind, UV, barometer,
            // percentage/battery, energy counters...).
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

/** A discovered Domoticz device, with its deduced widget type. */
data class DiscoveredDomoticzDevice(
    val idx: String,
    val name: String,
    val widgetType: WidgetType,
    val raw: DomoticzDeviceDto
)

/** A discovered Domoticz scene/group, separate resource from devices. */
data class DiscoveredDomoticzScene(
    val idx: String,
    val name: String,
    val isGroup: Boolean
)
