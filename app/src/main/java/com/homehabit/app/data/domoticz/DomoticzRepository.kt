package com.homehabit.app.data.domoticz

import com.homehabit.app.data.SensorKind
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.data.WidgetStateEntry
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale

class DomoticzRepository(private val client: DomoticzClient) {

    /**
     * Poll en continu les widgets dont la source est "domoticz" et emet
     * la map complete a chaque cycle. Les widgets non-Domoticz (meteo,
     * camera) ne sont pas concernes et restent geres ailleurs.
     *
     * Les widgets SCENE sont traites a part : Domoticz les expose via
     * une ressource distincte (getscenes) de celle des devices
     * (getdevices), donc un seul appel getScenes() par cycle suffit pour
     * TOUS les widgets scene/groupe, plutot qu'un appel par widget comme
     * pour les devices.
     */
    fun observeStates(
        widgets: List<WidgetConfig>,
        pollIntervalMs: Long = 5_000L
    ): Flow<Map<String, WidgetStateEntry>> = flow {
        val domoticzWidgets = widgets.filter { it.source?.provider == "domoticz" }
        if (domoticzWidgets.isEmpty()) {
            emit(emptyMap())
            return@flow
        }

        val sceneWidgets = domoticzWidgets.filter { it.widgetType == WidgetType.SCENE }
        val deviceWidgets = domoticzWidgets.filter { it.widgetType != WidgetType.SCENE }

        while (true) {
            val states = mutableMapOf<String, WidgetStateEntry>()

            for (widget in deviceWidgets) {
                val idx = extractIdx(widget) ?: continue
                val device = client.getDevice(idx) ?: continue
                states[widget.id] = WidgetStateEntry(
                    state = mapDeviceToState(widget.widgetType, device),
                    lastUpdate = parseDomoticzLastUpdate(device.LastUpdate)
                )
            }

            if (sceneWidgets.isNotEmpty()) {
                val scenes = client.getScenes()
                for (widget in sceneWidgets) {
                    val idx = extractIdx(widget) ?: continue
                    val scene = scenes.firstOrNull { it.idx == idx } ?: continue
                    states[widget.id] = WidgetStateEntry(
                        state = WidgetLiveState.Scene(
                            isGroup = scene.Type.equals("Group", ignoreCase = true),
                            isOn = scene.Status.equals("On", ignoreCase = true)
                        ),
                        lastUpdate = parseDomoticzLastUpdate(scene.LastUpdate)
                    )
                }
            }

            emit(states)
            delay(pollIntervalMs)
        }
    }

    /**
     * Liste les devices Domoticz decouvrables, avec leur WidgetType deduit,
     * pour l'ecran d'ajout de widget.
     */
    suspend fun discoverDevices(): List<DiscoveredDomoticzDevice> =
        client.getUsedDevices().map { device ->
            DiscoveredDomoticzDevice(
                idx = device.idx,
                name = device.Name ?: "Sans nom (idx ${device.idx})",
                widgetType = DomoticzTypeMapper.toWidgetType(device),
                raw = device
            )
        }

    /** Meme principe que discoverDevices(), mais pour les scenes/groupes (ressource Domoticz separee). */
    suspend fun discoverScenes(): List<DiscoveredDomoticzScene> =
        client.getScenes().map { scene ->
            DiscoveredDomoticzScene(
                idx = scene.idx,
                name = scene.Name ?: "Sans nom (idx ${scene.idx})",
                isGroup = scene.Type.equals("Group", ignoreCase = true)
            )
        }

    suspend fun toggleLight(widget: WidgetConfig, turnOn: Boolean): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.switchLight(idx, turnOn)
    }

    suspend fun setBrightness(widget: WidgetConfig, percent: Int): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.setDimmerLevel(idx, percent.coerceIn(0, 100))
    }

    suspend fun setColor(widget: WidgetConfig, hex: String, brightnessPercent: Int?): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.setColor(idx, hex, brightnessPercent)
    }

    suspend fun setShutterOpen(widget: WidgetConfig, open: Boolean): Boolean {
        val idx = extractIdx(widget) ?: return false
        return if (open) client.openShutter(idx) else client.closeShutter(idx)
    }

    suspend fun stopShutter(widget: WidgetConfig): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.stopShutter(idx)
    }

    suspend fun setThermostatSetpoint(widget: WidgetConfig, value: Float): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.setThermostatSetpoint(idx, value)
    }

    suspend fun toggleLock(widget: WidgetConfig, locked: Boolean): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.switchLock(idx, locked)
    }

    /**
     * Declenche une Scene (turnOn doit rester true — Domoticz refuse
     * "Off" sur une vraie scene) ou bascule un Group on/off.
     */
    suspend fun triggerScene(widget: WidgetConfig, turnOn: Boolean): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.switchScene(idx, if (turnOn) "On" else "Off")
    }

    /**
     * Historique 24h pour la sparkline, sous-echantillonne a maxPoints
     * pour ne pas envoyer ~288 points (5min sur 24h) a l'UI pour un
     * simple mini-graphe. Best-effort : null si indisponible.
     */
    suspend fun fetchTemperatureSparkline(widget: WidgetConfig, maxPoints: Int = 48): List<Float>? {
        val idx = extractIdx(widget) ?: return null
        val points = client.getTempGraphDay(idx) ?: return null
        if (points.size <= maxPoints) return points

        val step = (points.size / maxPoints).coerceAtLeast(1)
        return points.filterIndexed { index, _ -> index % step == 0 }
    }

    private fun extractIdx(widget: WidgetConfig): String? =
        widget.source?.deviceId?.removePrefix("idx:")

    /**
     * Domoticz renvoie LastUpdate en heure locale du serveur, sans fuseau
     * horaire explicite. On suppose donc que le serveur Domoticz et
     * l'appareil Android sont dans le meme fuseau (cas courant : reseau
     * domestique local). En cas d'echec de parsing, on retombe sur "now"
     * plutot que de planter ou d'afficher une date invalide.
     */
    private fun parseDomoticzLastUpdate(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private fun mapDeviceToState(type: WidgetType, device: DomoticzDeviceDto): WidgetLiveState =
        when (type) {
            WidgetType.LIGHT, WidgetType.DIMMER, WidgetType.COLOR_LIGHT -> WidgetLiveState.Light(
                isOn = device.Status?.equals("On", ignoreCase = true) == true,
                brightness = if (type != WidgetType.LIGHT) device.Level else null,
                colorHex = if (type == WidgetType.COLOR_LIGHT) DomoticzColorParser.parseToHex(device.Color) else null
            )

            WidgetType.THERMOSTAT -> WidgetLiveState.Thermostat(
                temperature = (device.Temp ?: device.SetPoint ?: 0.0).toFloat()
            )

            WidgetType.SHUTTER -> WidgetLiveState.Shutter(
                percentOpen = device.Level
                    ?: if (device.Status?.equals("Open", ignoreCase = true) == true) 100 else 0
            )

            // Le mapping "verrouille" depend du SwitchType configure cote Domoticz
            // (souvent un switch On/Off generique utilise comme serrure connectee).
            WidgetType.LOCK -> WidgetLiveState.Lock(
                isLocked = device.Status?.equals("On", ignoreCase = true) == true
            )

            WidgetType.SENSOR -> mapSensorState(device)

            else -> WidgetLiveState.Empty
        }

    /**
     * Determine la categorie du capteur et calcule une jauge visuelle
     * uniquement pour les grandeurs naturellement bornees 0-100
     * (humidite, pourcentage). Pour le reste (pluie, vent, energie...),
     * pas de jauge : une echelle arbitraire serait trompeuse.
     */
    private fun mapSensorState(device: DomoticzDeviceDto): WidgetLiveState.Sensor {
        val type = device.Type.orEmpty()

        val kind = when {
            type.contains("Temp", ignoreCase = true) -> SensorKind.TEMPERATURE
            type.contains("Humidity", ignoreCase = true) -> SensorKind.HUMIDITY
            type.contains("Rain", ignoreCase = true) -> SensorKind.RAIN
            type.contains("Wind", ignoreCase = true) -> SensorKind.WIND
            type.contains("UV", ignoreCase = true) -> SensorKind.UV
            type.contains("Barometer", ignoreCase = true) ||
                type.contains("Pressure", ignoreCase = true) -> SensorKind.BAROMETER
            type.contains("Percentage", ignoreCase = true) -> SensorKind.PERCENTAGE
            type.contains("Usage", ignoreCase = true) ||
                type.contains("kWh", ignoreCase = true) ||
                type.contains("Counter", ignoreCase = true) -> SensorKind.ENERGY
            else -> SensorKind.GENERIC
        }

        val gaugePercent = when (kind) {
            SensorKind.HUMIDITY, SensorKind.PERCENTAGE ->
                (device.Humidity ?: device.Level)?.let { (it / 100f).coerceIn(0f, 1f) }
            else -> null
        }

        return WidgetLiveState.Sensor(
            displayValue = device.Data ?: "--",
            kind = kind,
            gaugePercent = gaugePercent
        )
    }
}
