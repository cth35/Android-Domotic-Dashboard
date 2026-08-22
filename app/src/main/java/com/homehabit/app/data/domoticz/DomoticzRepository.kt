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
 * Evenements consommes par le ViewModel depuis le canal websocket, deja
 * traduits dans le vocabulaire du dashboard (WidgetStateEntry) plutot
 * que le DTO Domoticz brut — pour que updateDomoticzSettings() et load()
 * n'aient qu'un seul type d'evenement a gerer, comme pour le polling.
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
     * Poll en continu les widgets SCENE uniquement (getscenes). Les
     * devices ne sont PLUS polles en continu ici : voir
     * fetchInitialDeviceStates() (etat initial, un seul appel) et
     * observeLiveUpdates() (mises a jour temps reel via websocket) —
     * approche "full websocket" pour les devices. Les scenes/groupes
     * restent en polling REST pur car rien ne confirme qu'elles sont
     * poussees sur le canal websocket (ressource Domoticz distincte de
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
                // Meme logique que l'ancien observeStates() : on garde les
                // derniers etats connus et on retentera au prochain cycle.
            }

            val jitter = (0..500).random().toLong()
            delay(pollIntervalMs + jitter)
        }
    }

    /**
     * Etat initial des widgets devices (hors scenes), en UN SEUL appel
     * bulk. A appeler une fois au demarrage et a chaque changement de la
     * liste de widgets — le websocket (observeLiveUpdates) prend ensuite
     * le relais pour toute mise a jour ulterieure, sans nouveau poll
     * periodique. Necessaire car le websocket ne pousse que sur
     * CHANGEMENT : sans ce fetch initial, un widget resterait vide tant
     * qu'aucun changement physique n'est survenu depuis l'ouverture de
     * l'app.
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
                // Fallback individuel pour les devices "unused" absents du bulk.
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
     * Flux temps reel base sur le websocket Domoticz (/json). Reutilise
     * mapDeviceToState/parseDomoticzLastUpdate — memes regles de mapping
     * que le polling REST, seule la source du DTO change.
     *
     * Emet un evenement par device modifie (pas l'etat complet comme
     * observeStates()) : au ViewModel de fusionner ces deltas avec l'etat
     * deja connu. Les widgets SCENE ne sont PAS couverts ici : rien ne
     * confirme que Domoticz pousse les changements de scene/groupe sur ce
     * canal (ressource distincte de getdevices, cf. getScenes) — a
     * verifier sur le terrain. Ils restent geres par observeStates().
     *
     * Un seul idx peut en theorie alimenter plusieurs widgets (rare mais
     * le JSON de config le permet) : tous sont mis a jour dans ce cas.
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
        val rawPoints = client.getTempGraphDay(idx) ?: return null
        
        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - (24 * 3600 * 1000)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        // On ne garde que les points des dernieres 24h reels
        val points = rawPoints.filter {
            val time = runCatching { dateFormat.parse(it.d ?: "")?.time }.getOrNull()
            // Si le parsing echoue, on garde le point par securite, sinon on verifie les 24h
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
            WidgetType.LIGHT, WidgetType.DIMMER, WidgetType.COLOR_LIGHT -> {
                val status = device.Status.orEmpty()
                val isOn = !status.equals("Off", ignoreCase = true) && status.isNotBlank()

                val subType = device.SubType.orEmpty()
                val isActuallyColor = device.Type?.contains("Color Switch", ignoreCase = true) == true &&
                    subType.contains("RGB", ignoreCase = true)
                
                // On considere "White Tunable" les devices Color Switch qui ont "WW" dans leur sous-type
                // ou qui supportent le RGB (les RGB supportent generalement le blanc variable).
                val isWhiteTunable = device.Type?.contains("Color Switch", ignoreCase = true) == true &&
                    (subType.contains("WW", ignoreCase = true) || isActuallyColor)

                WidgetLiveState.Light(
                    isOn = isOn,
                    isColor = isActuallyColor,
                    isWhiteTunable = isWhiteTunable,
                    brightness = if (type != WidgetType.LIGHT) device.Level else null,
                    // On extrait la couleur pour tout le monde (RGB ou WW)
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

            // Le mapping "verrouille" depend du SwitchType configure cote Domoticz
            // (souvent un switch On/Off generique utilise comme serrure connectee).
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
