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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: ConfigRepository
) : ViewModel() {

    // Hot-swappable: when Domoticz settings change
    // (updateDomoticzSettings), old clients are closed and new ones
    // are created with the new config, without restarting the entire
    // app. domoticzWsClient is the real-time websocket channel,
    // complementary to REST polling (domoticzClient) kept as a safety
    // net — see startDomoticzLiveUpdates().
    private var domoticzClient = DomoticzClient(repository.current().settings.toDomoticzConfig())
    private var domoticzWsClient = DomoticzWebSocketClient(repository.current().settings.toDomoticzConfig())
    private var domoticzRepository = DomoticzRepository(domoticzClient, domoticzWsClient)

    private val weatherClient = OpenMeteoClient()
    private val weatherRepository = WeatherRepository(weatherClient)
    private val cameraRepository = CameraRepository()

    // Unique source of the config: comes directly from the repository, shared
    // with the HTTP server. Any modification (drag/resize, addition, or
    // edition via browser) passes through repository.updateConfig() and
    // is automatically reflected here.
    val config: StateFlow<DashboardConfig> = repository.configFlow

    // Page currently displayed in the pager. Necessary to know where
    // to add a new widget (always on the visible page).
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    fun setCurrentPage(index: Int) {
        _currentPageIndex.value = index
    }

    // Actual Domoticz, weather and camera states merged. Live states
    // are global (not attached to a page): no matter which page
    // a widget lives on, its state continues to be refreshed even if we are not
    // looking at it.
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

    // Domoticz resource distinct from devices (getscenes vs getdevices),
    // so separate discovery rather than merged in availableDevices.
    private val _availableScenes = MutableStateFlow<List<DiscoveredDomoticzScene>>(emptyList())
    val availableScenes: StateFlow<List<DiscoveredDomoticzScene>> = _availableScenes.asStateFlow()

    // 24h sparkline for temperature widgets (sensor kind=TEMPERATURE
    // or thermostat). Separate and infrequent poll (10min) because costly
    // (retrieves ~288 points per call on the Domoticz side) and purely decorative.
    private val _sparklines = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val sparklines: StateFlow<Map<String, List<Float>>> = _sparklines.asStateFlow()

    private var domoticzPollingJob: Job? = null
    private var domoticzInitialFetchJob: Job? = null
    private var domoticzLiveJob: Job? = null

    // Websocket channel state, exposed for a future visual indicator
    // (e.g., small dot in SettingsDialog) and for debug on real
    // device. Does not affect REST polling, which continues to run in
    // parallel no matter what (see startDomoticzLiveUpdates).
    private val _isDomoticzLiveConnected = MutableStateFlow(false)
    val isDomoticzLiveConnected: StateFlow<Boolean> = _isDomoticzLiveConnected.asStateFlow()

    // Event emitted when a trigger asks to open a camera modal automatically
    private val _autoOpenEvent = MutableSharedFlow<WidgetConfig>(extraBufferCapacity = 1)
    val autoOpenEvent: SharedFlow<WidgetConfig> = _autoOpenEvent.asSharedFlow()

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
     * Independent of Domoticz settings (each weather widget carries its
     * own lat/lon): never needs to be manually restarted,
     * flatMapLatest is enough to react to widget changes.
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
     * Merges a batch of freshly received Domoticz states (scene poll,
     * initial fetch, reconnection resync, or websocket delta) into
     * domoticzStates, publishes the result, and returns. Unique rule
     * shared by the four call sites below: we only replace a
     * local state if the incoming state is more recent, with a margin of
     * 2 seconds to absorb a possible clock shift between the
     * tablet and the Domoticz server.
     *
     * Important: even the reconnection resync goes through this rule
     * now (no brute overwrite) — a REST resync that ends
     * just after a local optimistic update (e.g., toggleLight) must
     * not overwrite an action more recent than the state it reports.
     */
    private fun mergeDomoticzStates(incoming: Map<String, WidgetStateEntry>) {
        if (incoming.isEmpty()) return
        val merged = domoticzStates.toMutableMap()
        incoming.forEach { (id, entry) ->
            val existing = merged[id]
            if (existing == null || entry.lastUpdate > (existing.lastUpdate - 2000)) {
                // Check if this update should trigger an auto-open for a camera
                checkForTrigger(id, existing, entry)
                
                merged[id] = entry
            }
        }
        domoticzStates = merged
        publishMergedStates()
    }

    /**
     * Checks if a device state change should automatically open a camera modal.
     * Triggered when a device configured as 'triggerId' in a camera widget
     * transitions from 'Off' to 'On'.
     */
    private fun checkForTrigger(widgetId: String, oldEntry: WidgetStateEntry?, newEntry: WidgetStateEntry) {
        val currentConfig = repository.current()
        
        // Extract idx from ID, supporting both real and virtual widgets
        val domoticzIdx = if (widgetId.startsWith("virtual_trigger_")) {
            widgetId.removePrefix("virtual_trigger_")
        } else {
            currentConfig.findWidget(widgetId)?.source?.deviceId ?: return
        }
        
        // Find all camera widgets that use this device as a trigger
        val cameraWidgets = currentConfig.allWidgets().filter { 
            it.widgetType == WidgetType.CAMERA && it.source?.triggerId == domoticzIdx 
        }
        
        if (cameraWidgets.isEmpty()) return

        val wasOn = oldEntry?.state?.let { isStateOn(it) } ?: false
        val isOn = isStateOn(newEntry.state)

        if (isOn && !wasOn) {
            cameraWidgets.forEach { camera ->
                viewModelScope.launch {
                    _autoOpenEvent.emit(camera)
                }
            }
        }
    }

    private fun isStateOn(state: WidgetLiveState): Boolean = when (state) {
        is WidgetLiveState.Light -> state.isOn
        is WidgetLiveState.BinarySensor -> state.isOn
        is WidgetLiveState.Scene -> state.isOn
        is WidgetLiveState.Lock -> !state.isLocked
        is WidgetLiveState.Sensor -> {
            // For virtual sensors or generic ones, we check common "active" keywords in the data
            val data = state.displayValue.lowercase()
            data.contains("on") || data.contains("motion") || data.contains("open") || data.contains("alerte")
        }
        else -> false
    }

    /**
     * Continuous poll of SCENE widgets only (getscenes) — devices
     * are no longer polled here, see startDomoticzInitialFetch() (start
     * state) and startDomoticzLiveUpdates() (websocket, only source of
     * subsequent update). Restartable: updateDomoticzSettings()
     * cancels this job and launches a new one with the freshly recreated
     * domoticzRepository, so that the server change takes effect
     * immediately without restarting the entire app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDomoticzPolling() {
        domoticzPollingJob?.cancel()
        domoticzPollingJob = viewModelScope.launch {
            repository.configFlow
                .map { cfg -> getObservedWidgets(cfg).filter { it.source?.provider == "domoticz" } }
                .distinctUntilChanged()
                .flatMapLatest { widgets -> domoticzRepository.observeScenePolling(widgets) }
                .collect { states -> mergeDomoticzStates(states) }
        }
    }

    /**
     * Starting state of device widgets (including triggers): a single
     * bulk call each time the observed widget list changes.
     */
    private fun startDomoticzInitialFetch() {
        domoticzInitialFetchJob?.cancel()
        domoticzInitialFetchJob = viewModelScope.launch {
            repository.configFlow
                .map { cfg -> getObservedWidgets(cfg).filter { it.source?.provider == "domoticz" } }
                .distinctUntilChanged()
                .collect { widgets ->
                    mergeDomoticzStates(domoticzRepository.fetchInitialDeviceStates(widgets))
                }
        }
    }

    // Last known Domoticz widget list, updated by
    // startDomoticzLiveUpdates() at each config change. Used for the
    // resync triggered on reconnection (see below): the websocket does not
    // provide the widget list, only the idx that just
    // changed, so it must be kept on hand to be able to relaunch
    // a targeted fetchInitialDeviceStates().
    private var currentDomoticzWidgets: List<WidgetConfig> = emptyList()

    /**
     * Real-time websocket channel (/json): ONLY update source
     * for Domoticz devices (see startDomoticzInitialFetch for
     * the starting state). Reconnection/backoff managed in
     * DomoticzWebSocketClient.
     *
     * On RECONNECTION (not the very first connection, already covered
     * by startDomoticzInitialFetch), we relaunch a full fetch of
     * devices to catch everything that may have changed physically
     * during the cut (e.g., Domoticz restarts for maintenance,
     * someone operates a switch during this time) — without this,
     * these widgets would remain frozen on their last known value
     * until their next change, potentially never.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDomoticzLiveUpdates() {
        domoticzLiveJob?.cancel()
        // Assumes "already connected" at startup so as not to trigger a
        // redundant resync on the very first connection (already managed
        // by startDomoticzInitialFetch). Only becomes relevant after
        // a real cut followed by a reconnection.
        var wasConnected = true

        domoticzLiveJob = viewModelScope.launch {
            repository.configFlow
                .map { cfg -> getObservedWidgets(cfg).filter { it.source?.provider == "domoticz" } }
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
     * Persists new Domoticz settings, recreates HTTP and
     * websocket clients (old ones are explicitly closed so as not to leak
     * connections) and relaunches polling + real-time channel with the
     * new configuration.
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
     * Determines after the fact (once the states are known) which widgets are
     * true temperature sensors/thermostats, and fetches their
     * 24h history for the sparkline. Voluntarily separate from the main
     * poll: no need to refresh a mini-graph every 5s.
     */
    private fun startSparklinePolling() {
        viewModelScope.launch {
            // Gives time for the first Domoticz poll cycle (sensor/thermostat
            // states) to respond before the first verification.
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
                        isColor = current?.isColor ?: false,
                        isWhiteTunable = current?.isWhiteTunable ?: false,
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

    /** DIMMER/COLOR_LIGHT widgets: adjusts brightness (0-100). */
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
                        isColor = current?.isColor ?: false,
                        isWhiteTunable = current?.isWhiteTunable ?: false,
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

    /** COLOR_LIGHT widgets only: changes color (preset palette). */
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
                        isColor = current?.isColor ?: false,
                        isWhiteTunable = current?.isWhiteTunable ?: false,
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

    /** Tap on the shutter widget: toggles open/closed depending on current position. */
    /**
     * No optimistic update: unlike open/close, we do not
     * know the exact position after a stop (the shutter can
     * stop anywhere between 0 and 100%) — the next poll (5s)
     * will bring the true value on the Domoticz side.
     */
    fun stopShutter(widgetId: String) {
        val widget = repository.current().findWidget(widgetId) ?: return
        viewModelScope.launch {
            domoticzRepository.stopShutter(widget)
        }
    }

    /** "toggle" style (source.shutterStyle == "toggle"): toggles open/closed depending on current position. */
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
        val current = entry?.state as? WidgetLiveState.Thermostat
        viewModelScope.launch {
            val ok = domoticzRepository.setThermostatSetpoint(widget, value)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = WidgetLiveState.Thermostat(
                        temperature = value,
                        trend = current?.trend ?: WidgetLiveState.Trend.STABLE
                    ),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry?.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    /**
     * Moves/resizes a widget if and only if the new
     * placement is valid (free grid, holes allowed, no
     * overlap) on ITS page. Persists immediately via the
     * repository, so also visible on the browser side at the next
     * GET /config. Used for resizing (handle), not for
     * moving (see applyLayout).
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
     * Commits the layout resulting from a positioning drag
     * (GridEngine.resolvePushLayout) all at once: the moved widget
     * takes its new position, and the pushed widgets to make
     * room for it keep their new position (actual rearrangement,
     * not just a preview). All concerned widgets are
     * necessarily on the same page (the one visible during the drag).
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

    /** Always adds to the currently displayed page. */
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
     * "Already used" checked on ALL pages, but only among
     * non-scene widgets: devices and scenes/groups are two distinct Domoticz
     * tables that can in theory share the same idx number; mixing
     * the two filters could have wrongly hidden a legitimate device
     * or scene.
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
            label = scene.name, // Domoticz name hardcoded in the JSON
            source = WidgetSource(provider = "domoticz", deviceId = "idx:${scene.idx}")
        )
        addWidget(newWidget)
        _availableScenes.value = _availableScenes.value.filterNot { it.idx == scene.idx }
    }

    /** Tap on a scene/group widget: triggers the scene (always "On"), or toggles the group on/off. */
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

    fun setSelectorLevel(widgetId: String, level: Int) {
        val widget = repository.current().findWidget(widgetId) ?: return
        val entry = _widgetStates.value[widgetId]
        val current = entry?.state as? WidgetLiveState.Selector ?: return

        viewModelScope.launch {
            val ok = domoticzRepository.setSelectorLevel(widget, level)
            if (ok) {
                domoticzStates = domoticzStates + (widgetId to WidgetStateEntry(
                    state = current.copy(currentLevel = level),
                    lastUpdate = System.currentTimeMillis(),
                    fallbackName = entry.fallbackName
                ))
                publishMergedStates()
            }
        }
    }

    // --- Page management ---

    fun addPage(name: String? = null): Int {
        val current = repository.current()
        val pageNumber = current.pages.size + 1
        val newPage = DashboardPage(
            id = "page_${System.currentTimeMillis()}",
            name = name ?: "Page $pageNumber"
        )
        repository.updateConfig(current.copy(pages = current.pages + newPage))
        val newIndex = current.pages.size // index of the new page in the updated list
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

    /** Refuses to delete the last remaining page: always at least one. */
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

// --- Page-aware helpers, private to this file ---

private fun DashboardConfig.allWidgets(): List<WidgetConfig> = pages.flatMap { it.widgets }

/**
 * Returns all widgets to observe, including "virtual" ones for camera triggers
 * that are not explicitly present on the dashboard.
 */
private fun getObservedWidgets(config: DashboardConfig): List<WidgetConfig> {
    val realWidgets = config.pages.flatMap { it.widgets }
    val existingIdx = realWidgets.mapNotNull { it.source?.deviceId }.toSet()

    val virtualWidgets = realWidgets.filter { it.widgetType == WidgetType.CAMERA }
        .mapNotNull { it.source?.triggerId }
        .filter { it !in existingIdx }
        .distinct()
        .map { idx ->
            WidgetConfig(
                id = "virtual_trigger_$idx",
                type = "sensor", // Default type for mapping, isStateOn will handle detection
                x = 0, y = 0, w = 1, h = 1,
                source = WidgetSource(provider = "domoticz", deviceId = idx)
            )
        }

    return realWidgets + virtualWidgets
}

private fun DashboardConfig.findWidget(widgetId: String): WidgetConfig? =
    pages.firstNotNullOfOrNull { page -> page.widgets.firstOrNull { it.id == widgetId } }

private fun DashboardConfig.pageIndexOf(widgetId: String): Int? =
    pages.indexOfFirst { page -> page.widgets.any { it.id == widgetId } }.takeIf { it >= 0 }

private fun DashboardConfig.replacingPage(index: Int, newPage: DashboardPage): DashboardConfig =
    copy(pages = pages.mapIndexed { i, page -> if (i == index) newPage else page })
