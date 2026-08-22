package com.homehabit.app.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homehabit.app.data.ConfigRepository
import com.homehabit.app.data.SensorKind
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.data.WidgetStateEntry
import com.homehabit.app.data.camera.CameraRepository
import com.homehabit.app.data.domoticz.DiscoveredDomoticzDevice
import com.homehabit.app.data.domoticz.DiscoveredDomoticzScene
import com.homehabit.app.data.domoticz.DomoticzClient
import com.homehabit.app.data.domoticz.DomoticzLiveEvent
import com.homehabit.app.data.domoticz.DomoticzRepository
import com.homehabit.app.data.domoticz.DomoticzWebSocketClient
import com.homehabit.app.data.domoticz.toDomoticzConfig
import com.homehabit.app.data.weather.OpenMeteoClient
import com.homehabit.app.data.weather.WeatherRepository
import com.homehabit.app.engine.GridEngine
import com.homehabit.app.model.AppSettings
import com.homehabit.app.model.DashboardConfig
import com.homehabit.app.model.DashboardPage
import com.homehabit.app.model.GridConfig
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetSource
import com.homehabit.app.model.WidgetType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: ConfigRepository
) : ViewModel() {

    // Remplacables a chaud : quand les reglages Domoticz changent
    // (updateDomoticzSettings), les anciens clients sont fermes et de
    // nouveaux sont crees avec la nouvelle config, sans redemarrer toute
    // l'app. domoticzWsClient est le canal websocket temps reel,
    // complementaire du polling REST (domoticzClient) garde comme filet
    // de securite — voir startDomoticzLiveUpdates().
    private var domoticzClient = DomoticzClient(repository.current().settings.toDomoticzConfig())
    private var domoticzWsClient = DomoticzWebSocketClient(repository.current().settings.toDomoticzConfig())
    private var domoticzRepository = DomoticzRepository(domoticzClient, domoticzWsClient)

    private val weatherClient = OpenMeteoClient()
    private val weatherRepository = WeatherRepository(weatherClient)
    private val cameraRepository = CameraRepository()

    // Source unique de la config : vient directement du repository, partagee
    // avec le serveur HTTP. Toute modification (drag/resize, ajout, ou
    // edition via navigateur) transite par repository.updateConfig() et
    // se reflete automatiquement ici.
    val config: StateFlow<DashboardConfig> = repository.configFlow

    // Page actuellement affichee dans le pager. Necessaire pour savoir ou
    // ajouter un nouveau widget (toujours sur la page visible).
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    fun setCurrentPage(index: Int) {
        _currentPageIndex.value = index
    }

    // Etats reels Domoticz, meteo et camera fusionnes. Les etats live
    // sont globaux (pas rattaches a une page) : peu importe la page ou
    // vit un widget, son etat continue d'etre rafraichi meme si on ne le
    // regarde pas.
    private var domoticzStates: Map<String, WidgetStateEntry> = emptyMap()
    private var weatherStates: Map<String, WidgetStateEntry> = emptyMap()
    private var cameraStates: Map<String, WidgetStateEntry> = emptyMap()

    private val _widgetStates = MutableStateFlow<Map<String, WidgetStateEntry>>(emptyMap())
    val widgetStates: StateFlow<Map<String, WidgetStateEntry>> = _widgetStates.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private var originalConfig: DashboardConfig? = null

    private val _availableDevices = MutableStateFlow<List<DiscoveredDomoticzDevice>>(emptyList())
    val availableDevices: StateFlow<List<DiscoveredDomoticzDevice>> = _availableDevices.asStateFlow()

    // Ressource Domoticz distincte des devices (getscenes vs getdevices),
    // donc decouverte separee plutot que fusionnee dans availableDevices.
    private val _availableScenes = MutableStateFlow<List<DiscoveredDomoticzScene>>(emptyList())
    val availableScenes: StateFlow<List<DiscoveredDomoticzScene>> = _availableScenes.asStateFlow()

    // Sparkline 24h pour les widgets temperature (sensor kind=TEMPERATURE
    // ou thermostat). Poll separe et peu frequent (10min) car couteux
    // (recupere ~288 points par appel cote Domoticz) et purement decoratif.
    private val _sparklines = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val sparklines: StateFlow<Map<String, List<Float>>> = _sparklines.asStateFlow()

    private var domoticzPollingJob: Job? = null
    private var domoticzInitialFetchJob: Job? = null
    private var domoticzLiveJob: Job? = null

    // Etat du canal websocket, expose pour un futur indicateur visuel
    // (ex. petit point dans SettingsDialog) et pour le debug sur device
    // reel. N'affecte pas le polling REST, qui continue de tourner en
    // parallele quoi qu'il arrive (voir startDomoticzLiveUpdates).
    private val _isDomoticzLiveConnected = MutableStateFlow(false)
    val isDomoticzLiveConnected: StateFlow<Boolean> = _isDomoticzLiveConnected.asStateFlow()

    fun load() {
        startWeatherPolling()
        startDomoticzInitialFetch()
        startDomoticzPolling()
        startDomoticzLiveUpdates()
        startCameraPolling()
        startSparklinePolling()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startCameraPolling() {
        viewModelScope.launch {
            repository.configFlow
                .map { cfg -> cfg.allWidgets().filter { it.widgetType == WidgetType.CAMERA } }
                .distinctUntilChanged()
                .flatMapLatest { widgets -> cameraRepository.observeStates(widgets) }
                .collect { states ->
                    cameraStates = states
                    publishMergedStates()
                }
        }
    }

    /**
     * Independant des reglages Domoticz (chaque widget meteo porte ses
     * propres lat/lon) : jamais besoin d'etre redemarre manuellement,
     * flatMapLatest suffit a reagir aux changements de widgets.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startWeatherPolling() {
        viewModelScope.launch {
            repository.configFlow
                .map { cfg -> cfg.allWidgets().filter { it.source?.provider == "open-meteo" } }
                .distinctUntilChanged()
                .flatMapLatest { widgets -> weatherRepository.observeStates(widgets) }
                .collect { states ->
                    weatherStates = states
                    publishMergedStates()
                }
        }
    }

    /**
     * Fusionne un lot d'etats Domoticz fraichement recus (poll scenes,
     * fetch initial, resync de reconnexion, ou delta websocket) dans
     * domoticzStates, publie le resultat, et retourne. Regle unique
     * partagee par les quatre call sites ci-dessous : on ne remplace un
     * etat local que si l'etat entrant est plus recent, avec une marge de
     * 2 secondes pour absorber un eventuel decalage d'horloge entre la
     * tablette et le serveur Domoticz.
     *
     * Important : meme le resync de reconnexion passe par cette regle
     * desormais (pas d'ecrasement brut) — un resync REST qui termine
     * juste apres une mise a jour optimiste locale (ex. toggleLight) ne
     * doit pas ecraser une action plus recente que l'etat qu'il rapporte.
     */
    private fun mergeDomoticzStates(incoming: Map<String, WidgetStateEntry>) {
        if (incoming.isEmpty()) return
        val merged = domoticzStates.toMutableMap()
        incoming.forEach { (id, entry) ->
            val existing = merged[id]
            if (existing == null || entry.lastUpdate > (existing.lastUpdate - 2000)) {
                merged[id] = entry
            }
        }
        domoticzStates = merged
        publishMergedStates()
    }

    /**
     * Poll continu des widgets SCENE uniquement (getscenes) — les devices
     * ne sont plus polles ici, voir startDomoticzInitialFetch() (etat de
     * depart) et startDomoticzLiveUpdates() (websocket, seule source de
     * mise a jour ensuite). Redemarrable : updateDomoticzSettings()
     * annule ce job et en relance un nouveau avec le domoticzRepository
     * fraichement recree, pour que le changement de serveur prenne effet
     * immediatement sans redemarrer toute l'app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDomoticzPolling() {
        domoticzPollingJob?.cancel()
        domoticzPollingJob = viewModelScope.launch {
            repository.configFlow
                .map { cfg -> cfg.allWidgets().filter { it.source?.provider == "domoticz" } }
                .distinctUntilChanged()
                .flatMapLatest { widgets -> domoticzRepository.observeScenePolling(widgets) }
                .collect { states -> mergeDomoticzStates(states) }
        }
    }

    /**
     * Etat de depart des widgets devices (hors scenes) : un seul appel
     * bulk a chaque fois que la liste de widgets Domoticz change (ajout,
     * suppression, ou premier chargement). Ensuite, plus aucun appel
     * REST periodique sur les devices — le websocket (voir
     * startDomoticzLiveUpdates) est la SEULE source de mise a jour.
     *
     * Compromis assume ("full websocket") : si le canal websocket perd
     * silencieusement un evenement sans que la connexion se coupe
     * franchement (cas rare mais possible), le widget concerne reste
     * affiche avec sa derniere valeur connue jusqu'au prochain changement
     * de widgets ou redemarrage de l'app — il n'y a plus de resynchro
     * REST periodique pour rattraper ce genre de decalage. A surveiller
     * en usage reel ; si ca s'avere genant, la parade la plus simple est
     * un appel periodique tres espace (ex. toutes les 5-10min) plutot que
     * de revenir a un polling 5s.
     */
    private fun startDomoticzInitialFetch() {
        domoticzInitialFetchJob?.cancel()
        domoticzInitialFetchJob = viewModelScope.launch {
            repository.configFlow
                .map { cfg -> cfg.allWidgets().filter { it.source?.provider == "domoticz" } }
                .distinctUntilChanged()
                .collect { widgets ->
                    mergeDomoticzStates(domoticzRepository.fetchInitialDeviceStates(widgets))
                }
        }
    }

    // Derniere liste de widgets Domoticz connue, mise a jour par
    // startDomoticzLiveUpdates() a chaque changement de config. Sert au
    // resync declenche sur reconnexion (voir plus bas) : le websocket ne
    // fournit pas la liste de widgets, seulement l'idx qui vient de
    // changer, donc il faut la garder sous la main pour pouvoir relancer
    // un fetchInitialDeviceStates() cible.
    private var currentDomoticzWidgets: List<WidgetConfig> = emptyList()

    /**
     * Canal websocket temps reel (/json) : SEULE source de mise a jour
     * pour les devices Domoticz (voir startDomoticzInitialFetch pour
     * l'etat de depart). Reconnexion/backoff geres dans
     * DomoticzWebSocketClient.
     *
     * A la RECONNEXION (pas la toute premiere connexion, deja couverte
     * par startDomoticzInitialFetch), on relance un fetch complet des
     * devices pour rattraper tout ce qui a pu changer physiquement
     * pendant la coupure (ex. Domoticz redemarre pour maintenance,
     * quelqu'un actionne un interrupteur pendant ce temps) — sans ca,
     * ces widgets resteraient figes sur leur derniere valeur connue
     * jusqu'a leur prochain changement, potentiellement jamais.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDomoticzLiveUpdates() {
        domoticzLiveJob?.cancel()
        // Suppose "deja connecte" au demarrage pour ne pas declencher un
        // resync redondant sur la toute premiere connexion (deja geree
        // par startDomoticzInitialFetch). Ne redevient pertinent qu'apres
        // une vraie coupure suivie d'une reconnexion.
        var wasConnected = true

        domoticzLiveJob = viewModelScope.launch {
            repository.configFlow
                .map { cfg -> cfg.allWidgets().filter { it.source?.provider == "domoticz" } }
                .distinctUntilChanged()
                .onEach { widgets -> currentDomoticzWidgets = widgets }
                .flatMapLatest { widgets -> domoticzRepository.observeLiveUpdates(widgets) }
                .collect { event ->
                    when (event) {
                        is DomoticzLiveEvent.ConnectionChanged -> {
                            val justReconnected = event.connected && !wasConnected
                            wasConnected = event.connected
                            _isDomoticzLiveConnected.value = event.connected

                            if (justReconnected) {
                                Log.i(
                                    "DashboardViewModel",
                                    "Websocket reconnecte, resync des devices en cours"
                                )
                                mergeDomoticzStates(
                                    domoticzRepository.fetchInitialDeviceStates(currentDomoticzWidgets)
                                )
                            }
                        }
                        is DomoticzLiveEvent.StateUpdate -> mergeDomoticzStates(event.states)
                    }
                }
        }
    }

    /**
     * Persiste les nouveaux reglages Domoticz, recree les clients HTTP et
     * websocket (les anciens sont explicitement fermes pour ne pas fuiter
     * de connexion) et relance polling + canal temps reel avec la
     * nouvelle configuration.
     */
    fun updateDomoticzSettings(settings: AppSettings) {
        val current = repository.current()
        repository.updateConfig(current.copy(settings = settings))

        domoticzClient.close()
        domoticzWsClient.close()
        val newConfig = settings.toDomoticzConfig()
        domoticzClient = DomoticzClient(newConfig)
        domoticzWsClient = DomoticzWebSocketClient(newConfig)
        domoticzRepository = DomoticzRepository(domoticzClient, domoticzWsClient)
        _isDomoticzLiveConnected.value = false
        startDomoticzInitialFetch()
        startDomoticzPolling()
        startDomoticzLiveUpdates()
    }

    /**
     * Determine apres coup (une fois les etats connus) quels widgets sont
     * de vrais capteurs/thermostats de temperature, et va chercher leur
     * historique 24h pour la sparkline. Volontairement separe du poll
     * principal : pas besoin de rafraichir un mini-graphe toutes les 5s.
     */
    private fun startSparklinePolling() {
        viewModelScope.launch {
            // Laisse le temps au premier cycle de poll Domoticz (etats
            // sensor/thermostat) de repondre avant la premiere verification.
            delay(8_000L)

            while (true) {
                val eligibleWidgets = repository.current().allWidgets().filter { widget ->
                    when (val state = _widgetStates.value[widget.id]?.state) {
                        is WidgetLiveState.Sensor -> state.kind == SensorKind.TEMPERATURE
                        is WidgetLiveState.Thermostat -> true
                        else -> false
                    }
                }

                if (eligibleWidgets.isNotEmpty()) {
                    val updated = _sparklines.value.toMutableMap()
                    for (widget in eligibleWidgets) {
                        val points = domoticzRepository.fetchTemperatureSparkline(widget)
                        if (points != null) {
                            updated[widget.id] = points
                        }
                    }
                    _sparklines.value = updated
                }

                delay(10 * 60_000L)
            }
        }
    }

    fun toggleEditMode() {
        val nextMode = !_isEditMode.value
        if (nextMode) {
            originalConfig = repository.current()
        } else {
            originalConfig = null
        }
        _isEditMode.value = nextMode
    }

    fun cancelEditMode() {
        originalConfig?.let {
            repository.updateConfig(it)
        }
        originalConfig = null
        _isEditMode.value = false
    }

    private fun publishMergedStates() {
        _widgetStates.value = domoticzStates + weatherStates + cameraStates
    }

    fun toggleLight(widgetId: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        val current = entry?.state as? WidgetLiveState.Light
        val newValue = current?.isOn != true

        viewModelScope.launch {
            val ok = domoticzRepository.toggleLight(widget, newValue)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Light(
                        isOn = newValue,
                        brightness = current?.brightness,
                        colorHex = current?.colorHex
                    ),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    /** Widgets DIMMER/COLOR_LIGHT : ajuste la luminosite (0-100). */
    fun setBrightness(widgetId: String, percent: Int) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        val current = entry?.state as? WidgetLiveState.Light

        viewModelScope.launch {
            val ok = domoticzRepository.setBrightness(widget, percent)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Light(
                        isOn = percent > 0,
                        brightness = percent,
                        colorHex = current?.colorHex
                    ),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    /** Widgets COLOR_LIGHT uniquement : change la couleur (palette de presets). */
    fun setLightColor(widgetId: String, hex: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        val current = entry?.state as? WidgetLiveState.Light

        viewModelScope.launch {
            val ok = domoticzRepository.setColor(widget, hex, current?.brightness)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Light(
                        isOn = true,
                        brightness = current?.brightness,
                        colorHex = hex
                    ),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    fun setShutterOpen(widgetId: String, open: Boolean) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        viewModelScope.launch {
            val ok = domoticzRepository.setShutterOpen(widget, open)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Shutter(percentOpen = if (open) 100 else 0),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    /** Tap sur le widget volet : bascule ouvert/ferme selon la position actuelle. */
    /**
     * Pas de mise a jour optimiste : contrairement a open/close, on ne
     * connait pas la position exacte apres un stop (le volet peut
     * s'arreter n'importe ou entre 0 et 100%) — le prochain poll (5s)
     * ramenera la vraie valeur cote Domoticz.
     */
    fun stopShutter(widgetId: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        viewModelScope.launch {
            domoticzRepository.stopShutter(widget)
        }
    }

    /** Style "toggle" (source.shutterStyle == "toggle") : bascule ouvert/ferme selon la position actuelle. */
    fun toggleShutter(widgetId: String) {
        val current = _widgetStates.value[widgetId]?.state as? WidgetLiveState.Shutter
        val shouldOpen = (current?.percentOpen ?: 0) < 50
        setShutterOpen(widgetId, shouldOpen)
    }

    fun toggleLock(widgetId: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        val current = entry?.state as? WidgetLiveState.Lock
        val newValue = current?.isLocked != true

        viewModelScope.launch {
            val ok = domoticzRepository.toggleLock(widget, newValue)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Lock(isLocked = newValue),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    fun setThermostatSetpoint(widgetId: String, value: Float) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        viewModelScope.launch {
            val ok = domoticzRepository.setThermostatSetpoint(widget, value)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Thermostat(temperature = value),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    /**
     * Deplace/redimensionne un widget si et seulement si le nouveau
     * placement est valide (grille libre, trous autorises, aucun
     * chevauchement) sur SA page. Persiste immediatement via le
     * repository, donc visible aussi cote navigateur au prochain
     * GET /config. Utilise pour le redimensionnement (poignee), pas pour
     * le deplacement (voir applyLayout).
     */
    fun updateWidgetRect(widgetId: String, newRect: GridEngine.Rect) {
        val current = repository.current()
        val pageIndex = current.pageIndexOf(widgetId) ?: return
        val page = current.pages[pageIndex]

        val valid = GridEngine.isValidPlacement(
            candidateId = widgetId,
            rect = newRect,
            others = page.widgets,
            columns = page.grid.columns
        )
        if (!valid) return

        val updatedWidgets = page.widgets.map { widget ->
            if (widget.id == widgetId) {
                widget.copy(x = newRect.x, y = newRect.y, w = newRect.w, h = newRect.h)
            } else {
                widget
            }
        }
        repository.updateConfig(current.replacingPage(pageIndex, page.copy(widgets = updatedWidgets)))
    }

    /**
     * Committe d'un coup la disposition resultant d'un drag de
     * repositionnement (GridEngine.resolvePushLayout) : le widget
     * deplace prend sa nouvelle position, et les widgets pousses pour
     * lui faire de la place gardent leur nouvelle position (reagencement
     * reel, pas juste un apercu). Tous les widgets concernes sont
     * forcement sur la meme page (celle visible pendant le drag).
     */
    fun applyLayout(newRects: Map<String, GridEngine.Rect>) {
        val anyId = newRects.keys.firstOrNull() ?: return
        val current = repository.current()
        val pageIndex = current.pageIndexOf(anyId) ?: return
        val page = current.pages[pageIndex]

        val updatedWidgets = page.widgets.map { widget ->
            val rect = newRects[widget.id] ?: return@map widget
            widget.copy(x = rect.x, y = rect.y, w = rect.w, h = rect.h)
        }
        repository.updateConfig(current.replacingPage(pageIndex, page.copy(widgets = updatedWidgets)))
    }

    /** Ajoute toujours sur la page actuellement affichee. */
    fun addWidget(newWidget: WidgetConfig) {
        val current = repository.current()
        val pageIndex = _currentPageIndex.value.coerceIn(0, current.pages.lastIndex.coerceAtLeast(0))
        val page = current.pages.getOrNull(pageIndex) ?: return

        val slot = GridEngine.findFirstFreeSlot(
            w = newWidget.w,
            h = newWidget.h,
            existing = page.widgets,
            columns = page.grid.columns
        )
        val placed = newWidget.copy(x = slot.x, y = slot.y)
        repository.updateConfig(current.replacingPage(pageIndex, page.copy(widgets = page.widgets + placed)))
    }

    fun removeWidget(widgetId: String) {
        val current = repository.current()
        val pageIndex = current.pageIndexOf(widgetId) ?: return
        val page = current.pages[pageIndex]
        repository.updateConfig(
            current.replacingPage(pageIndex, page.copy(widgets = page.widgets.filterNot { it.id == widgetId }))
        )
    }

    /**
     * "Deja utilise" verifie sur TOUTES les pages, mais uniquement parmi
     * les widgets non-scene : devices et scenes/groupes sont deux tables
     * Domoticz distinctes qui peuvent en theorie partager un meme numero
     * idx, melanger les deux filtres aurait pu masquer a tort un device
     * ou une scene legitimes.
     */
    fun discoverDomoticzDevices() {
        viewModelScope.launch {
            val alreadyUsedIdx = repository.current().allWidgets()
                .filter { it.widgetType != WidgetType.SCENE }
                .mapNotNull { it.source?.deviceId?.removePrefix("idx:") }
                .toSet()

            _availableDevices.value = domoticzRepository.discoverDevices()
                .filterNot { it.idx in alreadyUsedIdx }
        }
    }

    fun addDiscoveredDevice(device: DiscoveredDomoticzDevice, w: Int = 1, h: Int = 1) {
        val isSystem = device.idx.startsWith("system_")
        val newWidget = WidgetConfig(
            id = if (isSystem) "${device.idx}_${System.currentTimeMillis()}" else "domoticz_${device.idx}",
            type = device.widgetType.name.lowercase(),
            x = 0,
            y = 0,
            w = w,
            h = h,
            label = device.name,
            source = if (isSystem) null else WidgetSource(provider = "domoticz", deviceId = "idx:${device.idx}")
        )
        addWidget(newWidget)
        if (!isSystem) {
            _availableDevices.value = _availableDevices.value.filterNot { it.idx == device.idx }
        }
    }

    fun discoverDomoticzScenes() {
        viewModelScope.launch {
            val alreadyUsedIdx = repository.current().allWidgets()
                .filter { it.widgetType == WidgetType.SCENE }
                .mapNotNull { it.source?.deviceId?.removePrefix("idx:") }
                .toSet()

            _availableScenes.value = domoticzRepository.discoverScenes()
                .filterNot { it.idx in alreadyUsedIdx }
        }
    }

    fun addDiscoveredScene(scene: DiscoveredDomoticzScene, w: Int = 1, h: Int = 1) {
        val newWidget = WidgetConfig(
            id = "domoticz_scene_${scene.idx}",
            type = "scene",
            x = 0,
            y = 0,
            w = w,
            h = h,
            label = scene.name, // Nom Domoticz ecrit en dur dans le JSON
            source = WidgetSource(provider = "domoticz", deviceId = "idx:${scene.idx}")
        )
        addWidget(newWidget)
        _availableScenes.value = _availableScenes.value.filterNot { it.idx == scene.idx }
    }

    /** Tap sur un widget scene/groupe : declenche la scene (toujours "On"), ou bascule le groupe on/off. */
    fun triggerScene(widgetId: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        val current = entry?.state as? WidgetLiveState.Scene
        val isGroup = current?.isGroup == true
        val newValue = if (isGroup) current?.isOn != true else true

        viewModelScope.launch {
            val ok = domoticzRepository.triggerScene(widget, newValue)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Scene(isGroup = isGroup, isOn = newValue),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    // --- Gestion des pages ---

    fun addPage(name: String? = null): Int {
        val current = repository.current()
        val pageNumber = current.pages.size + 1
        val newPage = DashboardPage(
            id = "page_${System.currentTimeMillis()}",
            name = name ?: "Page $pageNumber"
        )
        repository.updateConfig(current.copy(pages = current.pages + newPage))
        val newIndex = current.pages.size // index de la nouvelle page dans la liste mise a jour
        _currentPageIndex.value = newIndex
        return newIndex
    }

    fun renamePage(pageIndex: Int, newName: String) {
        if (newName.isBlank()) return
        val current = repository.current()
        val page = current.pages.getOrNull(pageIndex) ?: return
        repository.updateConfig(current.replacingPage(pageIndex, page.copy(name = newName.trim())))
    }

    fun updatePageConfig(pageIndex: Int, name: String, grid: GridConfig) {
        if (name.isBlank()) return
        val current = repository.current()
        val page = current.pages.getOrNull(pageIndex) ?: return
        repository.updateConfig(current.replacingPage(pageIndex, page.copy(name = name.trim(), grid = grid)))
    }

    /** Refuse de supprimer la derniere page restante : toujours au moins une. */
    fun removePage(pageIndex: Int) {
        val current = repository.current()
        if (current.pages.size <= 1) return
        if (pageIndex !in current.pages.indices) return

        val updatedPages = current.pages.filterIndexed { index, _ -> index != pageIndex }
        repository.updateConfig(current.copy(pages = updatedPages))

        if (_currentPageIndex.value >= updatedPages.size) {
            _currentPageIndex.value = updatedPages.size - 1
        }
    }

    override fun onCleared() {
        super.onCleared()
        domoticzClient.close()
        domoticzWsClient.close()
        weatherClient.close()
    }
}

// --- Helpers page-aware, prives a ce fichier ---

private fun DashboardConfig.allWidgets(): List<WidgetConfig> = pages.flatMap { it.widgets }

private fun DashboardConfig.findWidget(widgetId: String): WidgetConfig? =
    pages.firstNotNullOfOrNull { page -> page.widgets.firstOrNull { it.id == widgetId } }

private fun DashboardConfig.pageIndexOf(widgetId: String): Int? =
    pages.indexOfFirst { page -> page.widgets.any { it.id == widgetId } }.takeIf { it >= 0 }

private fun DashboardConfig.replacingPage(index: Int, newPage: DashboardPage): DashboardConfig =
    copy(pages = pages.mapIndexed { i, page -> if (i == index) newPage else page })
