package com.homehabit.app.data.domoticz

import com.homehabit.app.data.SensorKind
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.data.WidgetStateEntry
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Events consumed by the ViewModel from the websocket channel, already
 * translated into the dashboard vocabulary (WidgetStateEntry) rather
 * than the raw Domoticz DTO — so that updateDomoticzSettings() and load()
 * have only one type of event to handle, as with polling.
 */
sealed class DomoticzLiveEvent {
    data class ConnectionChanged(val connected: Boolean) : DomoticzLiveEvent()
    data class StateUpdate(val states: Map<String, WidgetStateEntry>) : DomoticzLiveEvent()
}

class DomoticzRepository(
    private val client: DomoticzClient,
    private val wsClient: DomoticzWebSocketClient
) {

    /**
     * Continuously polls SCENE widgets only (getscenes).
     * devices are NO LONGER continuously polled here: see
     * fetchInitialDeviceStates() (initial state, single call) and
     * observeLiveUpdates() (real-time updates via websocket) —
     * "full websocket" approach for devices. Scenes/groups
     * remain in pure REST polling because nothing confirms that they are
     * pushed on the websocket channel (Domoticz resource distinct from
     * getdevices).
     */
    fun observeScenePolling(
        widgets: List<WidgetConfig>,
        pollIntervalMs: Long = 5_000L
    ): Flow<Map<String, WidgetStateEntry>> = flow {
        val sceneWidgets = widgets.filter {
            it.source?.provider == "domoticz" && it.widgetType == WidgetType.SCENE
        }
        if (sceneWidgets.isEmpty()) {
            emit(emptyMap())
            return@flow
        }

        while (true) {
            try {
                val scenes = client.getScenes()
                val states = sceneWidgets.mapNotNull { widget ->
                    val idx = extractIdx(widget) ?: return@mapNotNull null
                    val scene = scenes.firstOrNull { it.idx == idx } ?: return@mapNotNull null
                    widget.id to WidgetStateEntry(
                        state = WidgetLiveState.Scene(
                            isGroup = scene.Type.equals("Group", ignoreCase = true),
                            isOn = scene.Status.equals("On", ignoreCase = true)
                        ),
                        lastUpdate = parseDomoticzLastUpdate(scene.LastUpdate),
                        fallbackName = scene.Name
                    )
                }.toMap()
                if (states.isNotEmpty()) emit(states)
            } catch (e: Exception) {
                // Same logic as the old observeStates(): we keep the
                // last known states and will retry in the next cycle.
            }

            val jitter = (0..500).random().toLong()
            delay(pollIntervalMs + jitter)
        }
    }

    /**
     * Initial state of device widgets (excluding scenes), in a SINGLE bulk
     * call. To be called once at startup and at each change of the
     * widget list — the websocket (observeLiveUpdates) then takes
     * over for any subsequent updates, without a new periodic
     * poll. Necessary because the websocket only pushes on
     * CHANGE: without this initial fetch, a widget would remain empty as
     * long as no physical change has occurred since the app was opened.
     */
    suspend fun fetchInitialDeviceStates(widgets: List<WidgetConfig>): Map<String, WidgetStateEntry> {
        val deviceWidgets = widgets.filter {
            it.source?.provider == "domoticz" && it.widgetType != WidgetType.SCENE
        }
        if (deviceWidgets.isEmpty()) return emptyMap()

        return runCatching {
            val allDevices = client.getUsedDevices()
            deviceWidgets.mapNotNull { widget ->
                val idx = extractIdx(widget) ?: return@mapNotNull null
                // Individual fallback for "unused" devices absent from bulk.
                val device = allDevices.firstOrNull { it.idx == idx } ?: client.getDevice(idx)
                device ?: return@mapNotNull null
                widget.id to WidgetStateEntry(
                    state = mapDeviceToState(widget.widgetType, device),
                    lastUpdate = parseDomoticzLastUpdate(device.LastUpdate),
                    fallbackName = device.Name
                )
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * Real-time flow based on the Domoticz websocket (/json). Reuses
     * mapDeviceToState/parseDomoticzLastUpdate — same mapping rules
     * as REST polling, only the DTO source changes.
     *
     * Emits an event per modified device (not the full state like
     * observeStates()): for the ViewModel to merge these deltas with the already
     * known state. SCENE widgets are NOT covered here: nothing
     * confirms that Domoticz pushes scene/group changes on this
     * channel (resource distinct from getdevices, cf. getScenes) — to
     * be verified in the field. They remain managed by observeStates().
     *
     * A single idx can in theory feed multiple widgets (rare but
     * the config JSON allows it): all are updated in this case.
     */
    fun observeLiveUpdates(widgets: List<WidgetConfig>): Flow<DomoticzLiveEvent> {
        val deviceWidgets = widgets.filter {
            it.source?.provider == "domoticz" && it.widgetType != WidgetType.SCENE
        }
        if (deviceWidgets.isEmpty()) return emptyFlow()

        return wsClient.observeEvents().mapNotNull { event ->
            when (event) {
                is DomoticzWsEvent.Connected -> DomoticzLiveEvent.ConnectionChanged(true)
                is DomoticzWsEvent.Disconnected -> DomoticzLiveEvent.ConnectionChanged(false)
                is DomoticzWsEvent.Failed -> DomoticzLiveEvent.ConnectionChanged(false)
                is DomoticzWsEvent.DeviceUpdate -> {
                    val matching = deviceWidgets.filter { extractIdx(it) == event.device.idx }
                    if (matching.isEmpty()) return@mapNotNull null

                    val states = matching.associate { widget ->
                        widget.id to WidgetStateEntry(
                            state = mapDeviceToState(widget.widgetType, event.device),
                            lastUpdate = parseDomoticzLastUpdate(event.device.LastUpdate),
                            fallbackName = event.device.Name
                        )
                    }
                    DomoticzLiveEvent.StateUpdate(states)
                }
            }
        }
    }

    /**
     * Lists discoverable Domoticz devices, with their deduced WidgetType,
     * for the widget addition screen.
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

    /** Same principle as discoverDevices(), but for scenes/groups (separate Domoticz resource). */
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
     * Triggers a Scene (turnOn must remain true — Domoticz refuses
     * "Off" on a real scene) or toggles a Group on/off.
     */
    suspend fun triggerScene(widget: WidgetConfig, turnOn: Boolean): Boolean {
        val idx = extractIdx(widget) ?: return false
        return client.switchScene(idx, if (turnOn) "On" else "Off")
    }

    /**
     * 24h history for the sparkline, down-sampled to maxPoints
     * not to send ~288 points (5min over 24h) to the UI for a
     * simple mini-graph. Best-effort: null if unavailable.
     */
    suspend fun fetchTemperatureSparkline(widget: WidgetConfig, maxPoints: Int = 48): List<Float>? {
        val idx = extractIdx(widget) ?: return null
        val rawPoints = client.getTempGraphDay(idx) ?: return null
        
        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - (24 * 3600 * 1000)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        // We only keep the points from the real last 24h
        val points = rawPoints.filter {
            val time = runCatching { dateFormat.parse(it.d ?: "")?.time }.getOrNull()
            // If parsing fails, we keep the point for safety, otherwise we check the 24h
            if (time == null) true else time >= twentyFourHoursAgo
        }.mapNotNull { it.te?.toFloatOrNull() }

        if (points.isEmpty()) return null
        if (points.size <= maxPoints) return points

        val step = (points.size / maxPoints).coerceAtLeast(1)
        return points.filterIndexed { index, _ -> index % step == 0 }
    }

    private fun extractIdx(widget: WidgetConfig): String? =
        widget.source?.deviceId?.removePrefix("idx:")

    /**
     * Domoticz returns LastUpdate in the server's local time, without an explicit
     * time zone. We therefore assume that the Domoticz server and
     * the Android device are in the same zone (common case: local
     * home network). In case of parsing failure, we fall back to "now"
     * rather than crashing or displaying an invalid date.
     */
    private fun parseDomoticzLastUpdate(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private fun mapDeviceToState(type: WidgetType, device: DomoticzDeviceDto): WidgetLiveState =
        when (type) {
            WidgetType.LIGHT, WidgetType.DIMMER, WidgetType.COLOR_LIGHT -> {
                val status = device.Status.orEmpty()
                val isOn = !status.equals("Off", ignoreCase = true) && status.isNotBlank()

                val subType = device.SubType.orEmpty()
                val isActuallyColor = device.Type?.contains("Color Switch", ignoreCase = true) == true &&
                    subType.contains("RGB", ignoreCase = true)
                
                // We consider "White Tunable" the Color Switch devices that have "WW" in their sub-type
                // or that support RGB (RGB usually support variable white).
                val isWhiteTunable = device.Type?.contains("Color Switch", ignoreCase = true) == true &&
                    (subType.contains("WW", ignoreCase = true) || isActuallyColor)

                WidgetLiveState.Light(
                    isOn = isOn,
                    isColor = isActuallyColor,
                    isWhiteTunable = isWhiteTunable,
                    brightness = if (type != WidgetType.LIGHT) device.Level else null,
                    // We extract the color for everyone (RGB or WW)
                    colorHex = DomoticzColorParser.parseToHex(device.Color)
                )
            }

            WidgetType.THERMOSTAT -> WidgetLiveState.Thermostat(
                temperature = (device.Temp ?: device.SetPoint ?: 0.0).toFloat()
            )

            WidgetType.SHUTTER -> WidgetLiveState.Shutter(
                percentOpen = device.Level
                    ?: if (device.Status?.equals("Open", ignoreCase = true) == true) 100 else 0
            )

            // The "locked" mapping depends on the SwitchType configured on the Domoticz side
            // (often a generic On/Off switch used as a connected lock).
            WidgetType.LOCK -> WidgetLiveState.Lock(
                isLocked = device.Status?.equals("On", ignoreCase = true) == true
            )

            WidgetType.SENSOR -> mapSensorState(device)

            WidgetType.BINARY_SENSOR -> {
                val status = device.Status.orEmpty()
                WidgetLiveState.BinarySensor(
                    isOn = status.startsWith("On", ignoreCase = true) ||
                        status.startsWith("Open", ignoreCase = true) ||
                        status.startsWith("Motion", ignoreCase = true),
                    isContact = device.SwitchType?.contains("Contact", ignoreCase = true) == true ||
                        device.Name?.contains("Porte", ignoreCase = true) == true ||
                        device.Name?.contains("Fenetre", ignoreCase = true) == true
                )
            }

            else -> WidgetLiveState.Empty
        }

    /**
     * Determines the sensor category and calculates a visual gauge
     * only for naturally bounded quantities 0-100
     * (humidity, percentage). For the rest (rain, wind, energy...),
     * no gauge: an arbitrary scale would be misleading.
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
                (device.Humidity ?: device.Level?.toDouble())?.let { (it.toFloat() / 100f).coerceIn(0f, 1f) }
            else -> null
        }

        return WidgetLiveState.Sensor(
            displayValue = device.Data ?: "--",
            kind = kind,
            gaugePercent = gaugePercent
        )
    }
}
