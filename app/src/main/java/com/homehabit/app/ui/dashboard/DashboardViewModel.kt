package com.homehabit.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homehabit.app.data.ConfigRepository
import com.homehabit.app.data.FakeStateProvider
import com.homehabit.app.data.SensorKind
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.data.WidgetStateEntry
import com.homehabit.app.data.domoticz.DiscoveredDomoticzDevice
import com.homehabit.app.data.domoticz.DiscoveredDomoticzScene
import com.homehabit.app.data.domoticz.DomoticzClient
import com.homehabit.app.data.domoticz.DomoticzRepository
import com.homehabit.app.data.domoticz.toDomoticzConfig
import com.homehabit.app.data.weather.OpenMeteoClient
import com.homehabit.app.data.weather.WeatherRepository
import com.homehabit.app.engine.GridEngine
import com.homehabit.app.model.AppSettings
import com.homehabit.app.model.DashboardConfig
import com.homehabit.app.model.DashboardPage
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetSource
import com.homehabit.app.model.WidgetType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: ConfigRepository
) : ViewModel() {

    // Remplacables a chaud : quand les reglages Domoticz changent
    // (updateDomoticzSettings), l'ancien client est ferme et un nouveau
    // est cree avec la nouvelle config, sans redemarrer toute l'app.
    private var domoticzClient = DomoticzClient(repository.current().settings.toDomoticzConfig())
    private var domoticzRepository = DomoticzRepository(domoticzClient)

    private val weatherClient = OpenMeteoClient()
    private val weatherRepository = WeatherRepository(weatherClient)

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

    // Etats "demo" (uniquement la camera desormais) fusionnes avec les
    // etats reels Domoticz et meteo. Les etats live sont globaux (pas
    // rattaches a une page) : peu importe la page ou vit un widget, son
    // etat continue d'etre rafraichi meme si on ne le regarde pas.
    private var demoStates: Map<String, WidgetStateEntry> = emptyMap()
    private var domoticzStates: Map<String, WidgetStateEntry> = emptyMap()
    private var weatherStates: Map<String, WidgetStateEntry> = emptyMap()

    private val _widgetStates = MutableStateFlow<Map<String, WidgetStateEntry>>(emptyMap())
    val widgetStates: StateFlow<Map<String, WidgetStateEntry>> = _widgetStates.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

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

    fun load() {
        demoStates = FakeStateProvider.defaultStates()
        publishMergedStates()

        startWeatherPolling()
        startDomoticzPolling()
        startSparklinePolling()
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
                .flatMapLatest { cfg -> weatherRepository.observeStates(cfg.allWidgets()) }
                .collect { states ->
                    weatherStates = states
                    publishMergedStates()
                }
        }
    }

    /**
     * Redemarrable : updateDomoticzSettings() annule ce job et en relance
     * un nouveau avec le domoticzRepository fraichement recree, pour que
     * le changement de serveur (host/port/identifiants) prenne effet
     * immediatement sans redemarrer toute l'app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDomoticzPolling() {
        domoticzPollingJob?.cancel()
        domoticzPollingJob = viewModelScope.launch {
            repository.configFlow
                .flatMapLatest { cfg -> domoticzRepository.observeStates(cfg.allWidgets()) }
                .collect { states ->
                    domoticzStates = states
                    publishMergedStates()
                }
        }
    }

    /**
     * Persiste les nouveaux reglages Domoticz, recree le client HTTP
     * (l'ancien est explicitement ferme pour ne pas fuiter la connexion)
     * et relance le polling avec la nouvelle configuration.
     */
    fun updateDomoticzSettings(settings: AppSettings) {
        val current = repository.current()
        repository.updateConfig(current.copy(settings = settings))

        domoticzClient.close()
        domoticzClient = DomoticzClient(settings.toDomoticzConfig())
        domoticzRepository = DomoticzRepository(domoticzClient)
        startDomoticzPolling()
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
            delay(5_000L)

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
        _isEditMode.value = !_isEditMode.value
    }

    private fun publishMergedStates() {
        _widgetStates.value = demoStates + domoticzStates + weatherStates
    }

    fun toggleLight(widgetId: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val current = _widgetStates.value[widgetId]?.state as? WidgetLiveState.Light
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
                    lastUpdate = System.currentTimeMillis()
                ))
                publishMergedStates()
            }
        }
    }

    /** Widgets DIMMER/COLOR_LIGHT : ajuste la luminosite (0-100). */
    fun setBrightness(widgetId: String, percent: Int) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val current = _widgetStates.value[widgetId]?.state as? WidgetLiveState.Light

        viewModelScope.launch {
            val ok = domoticzRepository.setBrightness(widget, percent)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Light(
                        isOn = percent > 0,
                        brightness = percent,
                        colorHex = current?.colorHex
                    ),
                    lastUpdate = System.currentTimeMillis()
                ))
                publishMergedStates()
            }
        }
    }

    /** Widgets COLOR_LIGHT uniquement : change la couleur (palette de presets). */
    fun setLightColor(widgetId: String, hex: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val current = _widgetStates.value[widgetId]?.state as? WidgetLiveState.Light

        viewModelScope.launch {
            val ok = domoticzRepository.setColor(widget, hex, current?.brightness)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Light(
                        isOn = true,
                        brightness = current?.brightness,
                        colorHex = hex
                    ),
                    lastUpdate = System.currentTimeMillis()
                ))
                publishMergedStates()
            }
        }
    }

    fun setShutterOpen(widgetId: String, open: Boolean) {
        val widget = repository.current().findWidget(widgetId) ?: return
        viewModelScope.launch {
            val ok = domoticzRepository.setShutterOpen(widget, open)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Shutter(percentOpen = if (open) 100 else 0),
                    lastUpdate = System.currentTimeMillis()
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
        val current = _widgetStates.value[widgetId]?.state as? WidgetLiveState.Lock
        val newValue = current?.isLocked != true

        viewModelScope.launch {
            val ok = domoticzRepository.toggleLock(widget, newValue)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Lock(isLocked = newValue),
                    lastUpdate = System.currentTimeMillis()
                ))
                publishMergedStates()
            }
        }
    }

    fun setThermostatSetpoint(widgetId: String, value: Float) {
        val widget = repository.current().findWidget(widgetId) ?: return
        viewModelScope.launch {
            val ok = domoticzRepository.setThermostatSetpoint(widget, value)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Thermostat(temperature = value),
                    lastUpdate = System.currentTimeMillis()
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
        val newWidget = WidgetConfig(
            id = "domoticz_${device.idx}",
            type = device.widgetType.name.lowercase(),
            x = 0,
            y = 0,
            w = w,
            h = h,
            label = device.name,
            source = WidgetSource(provider = "domoticz", deviceId = "idx:${device.idx}")
        )
        addWidget(newWidget)
        _availableDevices.value = _availableDevices.value.filterNot { it.idx == device.idx }
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
            label = scene.name,
            source = WidgetSource(provider = "domoticz", deviceId = "idx:${scene.idx}")
        )
        addWidget(newWidget)
        _availableScenes.value = _availableScenes.value.filterNot { it.idx == scene.idx }
    }

    /** Tap sur un widget scene/groupe : declenche la scene (toujours "On"), ou bascule le groupe on/off. */
    fun triggerScene(widgetId: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val current = _widgetStates.value[widgetId]?.state as? WidgetLiveState.Scene
        val isGroup = current?.isGroup == true
        val newValue = if (isGroup) current?.isOn != true else true

        viewModelScope.launch {
            val ok = domoticzRepository.triggerScene(widget, newValue)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Scene(isGroup = isGroup, isOn = newValue),
                    lastUpdate = System.currentTimeMillis()
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
