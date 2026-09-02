package com.homehabit.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.engine.GridEngine
import com.homehabit.app.model.DashboardPage
import com.homehabit.app.model.GridConfig
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetType
import com.homehabit.app.server.NetworkUtils
import com.homehabit.app.ui.theme.AccentBlue
import com.homehabit.app.ui.theme.AccentRed
import com.homehabit.app.ui.theme.BackgroundDark
import com.homehabit.app.ui.theme.SurfaceDark
import com.homehabit.app.ui.theme.TextPrimary
import com.homehabit.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val WIDGET_GAP = 8.dp
private val HANDLE_SIZE = 22.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val config by viewModel.config.collectAsState()
    val pages = config.pages
    val widgetStates by viewModel.widgetStates.collectAsState()
    val sparklines by viewModel.sparklines.collectAsState()
    val availableDevices by viewModel.availableDevices.collectAsState()
    val availableScenes by viewModel.availableScenes.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isDomoticzLiveConnected by viewModel.isDomoticzLiveConnected.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var cameraModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var thermostatModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var weatherModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var lightModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var selectorModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var managePageIndex by remember { mutableStateOf<Int?>(null) }

    // Management of the automatic display of controls (Auto-hide)
    var controlsVisible by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    fun pokeControls() {
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(controlsVisible, lastInteractionTime, isEditMode) {
        if (controlsVisible && !isEditMode) {
            delay(5000)
            controlsVisible = false
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    
    // Extraction of sunrise/sunset from the first available weather widget
    val weatherData = widgetStates.values.mapNotNull { it.state }.firstOrNull { 
        it is WidgetLiveState.Weather || it is WidgetLiveState.Forecast 
    }
    val (sunrise, sunset) = when (weatherData) {
        is WidgetLiveState.Weather -> weatherData.sunrise to weatherData.sunset
        is WidgetLiveState.Forecast -> weatherData.days.firstOrNull()?.let { it.sunrise to it.sunset } ?: (null to null)
        else -> null to null
    }
    val scope = rememberCoroutineScope()

    // Automatic opening of camera modal on trigger
    LaunchedEffect(Unit) {
        viewModel.autoOpenEvent.collect { camera ->
            cameraModalWidget = camera
        }
    }

    // Automatic closing of camera modal if opened via trigger
    LaunchedEffect(cameraModalWidget) {
        // We only trigger auto-close if the widget has a triggerId (was opened automatically)
        val triggerId = cameraModalWidget?.source?.triggerId
        val seconds = cameraModalWidget?.source?.autoCloseSeconds ?: 60
        if (triggerId != null && cameraModalWidget != null && seconds > 0) {
            delay(seconds * 1000L)
            cameraModalWidget = null
        }
    }

    // Keeps the ViewModel informed of the visible page: addWidget() needs
    // it to know where to place a new widget.
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentPage(pagerState.currentPage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isEditMode) {
                PageTabsBar(
                    pages = pages,
                    currentPage = pagerState.currentPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0)),
                    isEditMode = isEditMode,
                    onPageSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    onPageLongPress = { index -> managePageIndex = index },
                    onAddPage = { viewModel.addPage() }
                )
            }

            if (pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().background(BackgroundDark))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager

                    DashboardGrid(
                        gridConfig = page.grid,
                        widgets = page.widgets,
                        isEditMode = isEditMode,
                        onResizeUpdate = viewModel::updateWidgetRect,
                        onMoveCommit = viewModel::applyLayout,
                        onDeleteWidget = viewModel::removeWidget,
                        onBackgroundClick = ::pokeControls
                    ) { widgetConfig ->
                        val isDimmableLight = widgetConfig.widgetType == WidgetType.DIMMER ||
                            widgetConfig.widgetType == WidgetType.COLOR_LIGHT
                        val isToggleShutter = widgetConfig.widgetType == WidgetType.SHUTTER &&
                            widgetConfig.source?.shutterStyle == "toggle"
                        val isButtonsShutter = widgetConfig.widgetType == WidgetType.SHUTTER && !isToggleShutter

                        WidgetCard(
                            config = widgetConfig,
                            entry = widgetStates[widgetConfig.id],
                            sparkline = sparklines[widgetConfig.id],
                            sunrise = sunrise,
                            sunset = sunset,
                            onClick = when {
                                isEditMode -> null
                                widgetConfig.widgetType == WidgetType.WEATHER || widgetConfig.widgetType == WidgetType.FORECAST ->
                                    { { weatherModalWidget = widgetConfig } }
                                widgetConfig.widgetType == WidgetType.LIGHT ->
                                    { { viewModel.toggleLight(widgetConfig.id) } }
                                isDimmableLight ->
                                    { { viewModel.toggleLight(widgetConfig.id) } }
                                isToggleShutter ->
                                    { { viewModel.toggleShutter(widgetConfig.id) } }
                                widgetConfig.widgetType == WidgetType.LOCK ->
                                    { { viewModel.toggleLock(widgetConfig.id) } }
                                widgetConfig.widgetType == WidgetType.SCENE ->
                                    { { viewModel.triggerScene(widgetConfig.id) } }
                                widgetConfig.widgetType == WidgetType.SELECTOR ->
                                    { { selectorModalWidget = widgetConfig } }
                                widgetConfig.widgetType == WidgetType.BINARY_SENSOR -> null
                                widgetConfig.widgetType == WidgetType.THERMOSTAT ->
                                    { { thermostatModalWidget = widgetConfig } }
                                widgetConfig.widgetType == WidgetType.CAMERA && widgetConfig.source?.rtspUrl != null ->
                                    { { cameraModalWidget = widgetConfig } }
                                else -> null
                            },
                            onLongClick = if (!isEditMode && (isDimmableLight || widgetConfig.widgetType == WidgetType.LIGHT)) {
                                { lightModalWidget = widgetConfig }
                            } else null,
                            onShutterOpen = if (!isEditMode && isButtonsShutter) {
                                { viewModel.setShutterOpen(widgetConfig.id, true) }
                            } else null,
                            onShutterStop = if (!isEditMode && isButtonsShutter) {
                                { viewModel.stopShutter(widgetConfig.id) }
                            } else null,
                            onShutterClose = if (!isEditMode && isButtonsShutter) {
                                { viewModel.setShutterOpen(widgetConfig.id, false) }
                            } else null
                        )
                    }
                }
            }
        }

        // Badge de connexion Domoticz : contrairement aux FAB ci-dessous
        // (masques par defaut, revelees seulement au toucher), celui-ci
        // reste visible en permanence tant que le websocket est coupe —
        // c'est un dashboard mural, personne ne va "toucher l'ecran" pour
        // decouvrir que le serveur est en panne. N'affiche rien tant que
        // tout va bien : aucun encombrement visuel en usage normal.
        ConnectionStatusBadge(
            isConnected = isDomoticzLiveConnected,
            modifier = Modifier.align(Alignment.TopStart)
        )

        AnimatedVisibility(
            visible = controlsVisible || isEditMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { 
                        viewModel.toggleEditMode()
                        if (!isEditMode) pokeControls() // S'assure qu'ils restent visibles au debut
                    },
                    containerColor = if (isEditMode) AccentBlue else SurfaceDark,
                    contentColor = TextPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                        contentDescription = if (isEditMode) "Terminer l'edition" else "Mode edition"
                    )
                }

                if (!isEditMode) {
                    FloatingActionButton(
                        onClick = { showSettingsDialog = true },
                        containerColor = SurfaceDark,
                        contentColor = TextSecondary,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Reglages")
                    }
                } else {
                    FloatingActionButton(
                        onClick = { viewModel.cancelEditMode() },
                        containerColor = AccentRed,
                        contentColor = TextPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Annuler")
                    }
                }
            }
        }

        if (isEditMode) {
            FloatingActionButton(
                onClick = {
                    viewModel.discoverDomoticzDevices()
                    viewModel.discoverDomoticzScenes()
                    showAddDialog = true
                },
                containerColor = AccentBlue,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter un widget")
            }
        }
    }

    if (showAddDialog) {
        AddWidgetDialog(
            devices = availableDevices,
            scenes = availableScenes,
            onSelect = { device ->
                viewModel.addDiscoveredDevice(device)
                showAddDialog = false
            },
            onSelectScene = { scene ->
                viewModel.addDiscoveredScene(scene)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            initial = config.settings,
            pages = pages,
            onManagePage = { index -> managePageIndex = index },
            onAddPage = { viewModel.addPage() },
            onSave = { settings ->
                viewModel.updateDomoticzSettings(settings)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    cameraModalWidget?.let { widget ->
        val rtspUrl = widget.source?.rtspUrl
        if (rtspUrl != null) {
            CameraStreamModal(
                label = widget.label ?: "Camera",
                rtspUrl = rtspUrl,
                posterUrl = widget.source.url,
                useRtspClientNative = widget.source.useRtspClientNative ?: config.settings.useRtspClientNative,
                onDismiss = { cameraModalWidget = null }
            )
        }
    }

    thermostatModalWidget?.let { widget ->
        val currentSetpoint = (widgetStates[widget.id]?.state as? WidgetLiveState.Thermostat)?.temperature ?: 19f
        ThermostatAdjustDialog(
            label = widget.label ?: "Thermostat",
            currentSetpoint = currentSetpoint,
            onConfirm = { value ->
                viewModel.setThermostatSetpoint(widget.id, value)
                thermostatModalWidget = null
            },
            onDismiss = { thermostatModalWidget = null }
        )
    }

    weatherModalWidget?.let { widget ->
        val state = widgetStates[widget.id]?.state
        if (state is WidgetLiveState.Weather) {
            WeatherDetailsModal(
                label = widget.label ?: "Météo",
                state = state,
                onDismiss = { weatherModalWidget = null }
            )
        } else if (state is WidgetLiveState.Forecast) {
            ForecastDetailsModal(
                label = widget.label ?: "Prévision 7 jours",
                state = state,
                onDismiss = { weatherModalWidget = null }
            )
        }
    }

    lightModalWidget?.let { widget ->
        val light = widgetStates[widget.id]?.state as? WidgetLiveState.Light
        LightAdjustDialog(
            label = widget.label ?: "Lumiere",
            // We rely on the live state to know what to display
            isColorLight = light?.isColor == true,
            isWhiteTunable = light?.isWhiteTunable == true,
            currentBrightness = light?.brightness ?: 100,
            currentColorHex = light?.colorHex,
            onBrightnessChange = { percent -> viewModel.setBrightness(widget.id, percent) },
            onColorChange = { hex -> viewModel.setLightColor(widget.id, hex) },
            onDismiss = { lightModalWidget = null }
        )
    }

    selectorModalWidget?.let { widget ->
        val state = widgetStates[widget.id]?.state as? WidgetLiveState.Selector
        if (state != null) {
            SelectorAdjustDialog(
                label = widget.label ?: "Selecteur",
                currentLevel = state.currentLevel,
                levels = state.levels,
                onLevelChange = { level: Int ->
                    viewModel.setSelectorLevel(widget.id, level)
                    selectorModalWidget = null
                },
                onDismiss = { selectorModalWidget = null }
            )
        }
    }

    ManageDialogHost(pageIndex = managePageIndex, pages = pages, viewModel = viewModel) {
        managePageIndex = null
    }
}

/**
 * Global connection status badge to the Domoticz server, based on the
 * real-time websocket channel (DashboardViewModel.isDomoticzLiveConnected).
 * Invisible as long as everything is fine; appears as soon as the connection is
 * lost, with a grace period not to flash at app startup
 * while the very first handshake is established.
 *
 * Intentionally independent of the REST polling of scenes: this badge only
 * reflects the state of the websocket, which covers most widgets
 * (lights, shutters, sensors...). If the server is completely unreachable
 * (e.g., down for maintenance), the websocket also falls, so the badge
 * displays correctly in this case — this is the targeted scenario.
 */
@Composable
private fun ConnectionStatusBadge(isConnected: Boolean, modifier: Modifier = Modifier) {
    var showBadge by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            showBadge = false
        } else {
            // Grace period: the very first handshake at app launch
            // generally takes 1-2s, no need to alarm
            // the user for that. Beyond that, the cut is real.
            delay(8_000)
            showBadge = true
        }
    }

    AnimatedVisibility(
        visible = showBadge,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AccentRed.copy(alpha = 0.9f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Domoticz hors ligne",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Small wrapper to keep the body of DashboardScreen readable: displays
 * PageManageDialog if a page index is being edited (long-press
 * on a tab).
 */
@Composable
private fun ManageDialogHost(
    pageIndex: Int?,
    pages: List<DashboardPage>,
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val index = pageIndex ?: return
    val page = pages.getOrNull(index) ?: return

    PageManageDialog(
        currentName = page.name,
        currentGrid = page.grid,
        canDelete = pages.size > 1,
        onSave = { newName, newGrid ->
            viewModel.updatePageConfig(index, newName, newGrid)
            onDismiss()
        },
        onDelete = {
            viewModel.removePage(index)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

/**
 * Free grid: each widget is positioned absolutely from its
 * coordinates (x, y) and sized according to (w, h), in cell units.
 * Unoccupied cells simply remain empty outside of a drag.
 * Operates on ONE page at a time (grid + widgets), page-agnostic otherwise
 * — it is the caller (DashboardScreen, in the pager) who chooses which
 * page to display.
 *
 * During a repositioning drag, a preview (GridEngine.resolvePushLayout)
 * is calculated continuously and animated for all widgets except the one being
 * moved (which follows the raw finger, without lag). Nothing is persisted before
 * the release: if the finger moves away from an area before releasing, the
 * widgets that had been pushed naturally return to their original position
 * (the calculation is redone at each drag event).
 */
@Composable
private fun DashboardGrid(
    gridConfig: GridConfig,
    widgets: List<WidgetConfig>,
    isEditMode: Boolean,
    onResizeUpdate: (String, GridEngine.Rect) -> Unit,
    onMoveCommit: (Map<String, GridEngine.Rect>) -> Unit,
    onDeleteWidget: (String) -> Unit,
    onBackgroundClick: () -> Unit,
    content: @Composable (widget: WidgetConfig) -> Unit
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    var dragState by remember { mutableStateOf<DragUiState?>(null) }

    // If we leave the edit mode, we force the reset of the local drag state
    // to avoid widgets remaining grayed out (alpha 0.6).
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            dragState = null
        }
    }

    val columns = gridConfig.columns.coerceAtLeast(1)
    val gapPx = with(density) { WIDGET_GAP.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(12.dp)
            .onSizeChanged { 
                containerWidthPx = it.width
                containerHeightPx = it.height
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onBackgroundClick() })
            }
    ) {
        if (containerWidthPx == 0 || containerHeightPx == 0) return@Box

        val cellPx = (containerWidthPx - gapPx * (columns - 1)) / columns
        val cellStepPx = cellPx + gapPx

        val isFitMode = gridConfig.rows > 0
        val rows = if (isFitMode) gridConfig.rows else 0
        
        // In Fit mode, we calculate the cell height so that everything fits
        val cellHeightPx = if (isFitMode) {
            (containerHeightPx - gapPx * (rows - 1)) / rows
        } else {
            cellPx // Default square in scroll mode
        }
        val cellHeightStepPx = cellHeightPx + gapPx

        val maxRow = if (isFitMode) rows else {
            (widgets.maxOfOrNull { it.y + it.h } ?: 0) + (dragState?.let { 4 } ?: 0)
        }
        val totalHeightDp = with(density) { (cellHeightStepPx * maxRow).toDp() }

        Box(
            modifier = Modifier
                .let { if (isFitMode) it else it.verticalScroll(rememberScrollState()) }
                .fillMaxWidth()
                .height(totalHeightDp)
        ) {
            if (widgets.isEmpty()) {
                Text(
                    text = "Aucun widget sur cette page",
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            widgets.forEach { widget ->
                val isBeingDragged = dragState?.draggedId == widget.id
                val widthDp = with(density) { (cellPx * widget.w + gapPx * (widget.w - 1)).toDp() }
                val heightDp = with(density) { (cellHeightPx * widget.h + gapPx * (widget.h - 1)).toDp() }

                if (isBeingDragged) {
                    // Follows the finger without lag
                    val committedOffsetX = with(density) { (cellStepPx * widget.x).toDp() }
                    val committedOffsetY = with(density) { (cellHeightStepPx * widget.y).toDp() }
                    val rawOffsetX = with(density) { dragState!!.rawOffsetPx.x.toDp() }
                    val rawOffsetY = with(density) { dragState!!.rawOffsetPx.y.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = committedOffsetX + rawOffsetX, y = committedOffsetY + rawOffsetY)
                            .size(width = widthDp, height = heightDp)
                            .zIndex(10f)
                            .alpha(0.6f)
                    ) {
                        content(widget)
                        if (isEditMode) {
                            EditOverlay(
                                widget = widget,
                                allWidgets = widgets,
                                columns = columns,
                                cellStepPx = cellStepPx,
                                cellHeightStepPx = cellHeightStepPx,
                                onMovePreview = { preview, rawPx ->
                                    dragState = DragUiState(widget.id, preview, rawPx)
                                },
                                onMoveCommit = { preview ->
                                    onMoveCommit(preview)
                                    dragState = null
                                },
                                onMoveCancel = { dragState = null },
                                onResizeUpdate = onResizeUpdate,
                                onDelete = { onDeleteWidget(widget.id) }
                            )
                        }
                    }
                } else {
                    val targetRect = dragState?.preview?.get(widget.id)
                        ?: GridEngine.Rect(widget.x, widget.y, widget.w, widget.h)
                    val targetOffsetX = with(density) { (cellStepPx * targetRect.x).toDp() }
                    val targetOffsetY = with(density) { (cellHeightStepPx * targetRect.y).toDp() }
                    val animatedOffsetX by animateDpAsState(targetOffsetX, label = "widgetOffsetX")
                    val animatedOffsetY by animateDpAsState(targetOffsetY, label = "widgetOffsetY")

                    Box(
                        modifier = Modifier
                            .offset(x = animatedOffsetX, y = animatedOffsetY)
                            .size(width = widthDp, height = heightDp)
                    ) {
                        content(widget)

                        if (isEditMode) {
                            EditOverlay(
                                widget = widget,
                                allWidgets = widgets,
                                columns = columns,
                                cellStepPx = cellStepPx,
                                cellHeightStepPx = cellHeightStepPx,
                                onMovePreview = { preview, rawPx ->
                                    dragState = DragUiState(widget.id, preview, rawPx)
                                },
                                onMoveCommit = { preview ->
                                    onMoveCommit(preview)
                                    dragState = null
                                },
                                onMoveCancel = { dragState = null },
                                onResizeUpdate = onResizeUpdate,
                                onDelete = { onDeleteWidget(widget.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shared state of the current drag, so that ALL widgets can react (push) while only one is moved. */
private data class DragUiState(
    val draggedId: String,
    val preview: Map<String, GridEngine.Rect>,
    val rawOffsetPx: Offset
)

/**
 * Overlays the widget with a movement area (the whole body) and a
 * resizing handle (bottom-right corner).
 *
 * Movement: at each drag event, calculates the target cell
 * (snap on the grid) and the resulting full layout via
 * GridEngine.resolvePushLayout (widgets in the way are pushed
 * lower). This result is only a preview (onMovePreview) until the
 * finger is released; it is only at onMoveCommit that the
 * ViewModel actually persists the layout.
 *
 * Resizing: unchanged from before, immediate commit at
 * each valid step (no pushed preview for resizing).
 */
@Composable
private fun EditOverlay(
    widget: WidgetConfig,
    allWidgets: List<WidgetConfig>,
    columns: Int,
    cellStepPx: Float,
    cellHeightStepPx: Float,
    onMovePreview: (Map<String, GridEngine.Rect>, Offset) -> Unit,
    onMoveCommit: (Map<String, GridEngine.Rect>) -> Unit,
    onMoveCancel: () -> Unit,
    onResizeUpdate: (String, GridEngine.Rect) -> Unit,
    onDelete: () -> Unit
) {
    val latestWidget = rememberUpdatedState(widget)
    val latestAllWidgets = rememberUpdatedState(allWidgets)

    Box(modifier = Modifier.fillMaxSize()) {
        var startX by remember { mutableIntStateOf(0) }
        var startY by remember { mutableIntStateOf(0) }
        var moveAccum by remember { mutableStateOf(Offset.Zero) }
        var lastPreview by remember { mutableStateOf<Map<String, GridEngine.Rect>?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, AccentBlue.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .pointerInput(widget.id) {
                    detectDragGestures(
                        onDragStart = {
                            startX = latestWidget.value.x
                            startY = latestWidget.value.y
                            moveAccum = Offset.Zero
                            lastPreview = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            moveAccum += dragAmount

                            val deltaCellX = (moveAccum.x / cellStepPx).roundToInt()
                            val deltaCellY = (moveAccum.y / cellHeightStepPx).roundToInt()
                            val current = latestWidget.value
                            val candidate = GridEngine.Rect(
                                startX + deltaCellX,
                                startY + deltaCellY,
                                current.w,
                                current.h
                            )

                            val preview = GridEngine.resolvePushLayout(
                                draggedId = widget.id,
                                candidate = candidate,
                                allWidgets = latestAllWidgets.value,
                                columns = columns
                            )
                            lastPreview = preview
                            onMovePreview(preview, moveAccum)
                        },
                        onDragEnd = {
                            lastPreview?.let { onMoveCommit(it) } ?: onMoveCancel()
                        },
                        onDragCancel = {
                            onMoveCancel()
                        }
                    )
                }
        )

        var startW by remember { mutableIntStateOf(0) }
        var startH by remember { mutableIntStateOf(0) }
        var resizeAccum by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(HANDLE_SIZE)
                .clip(CircleShape)
                .background(AccentBlue)
                .pointerInput(widget.id) {
                    detectDragGestures(
                        onDragStart = {
                            startW = latestWidget.value.w
                            startH = latestWidget.value.h
                            resizeAccum = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            resizeAccum += dragAmount
                            val deltaCellW = (resizeAccum.x / cellStepPx).roundToInt()
                            val deltaCellH = (resizeAccum.y / cellHeightStepPx).roundToInt()
                            val current = latestWidget.value
                            onResizeUpdate(
                                widget.id,
                                GridEngine.Rect(
                                    current.x,
                                    current.y,
                                    (startW + deltaCellW).coerceAtLeast(1),
                                    (startH + deltaCellH).coerceAtLeast(1)
                                )
                            )
                        }
                    )
                }
        )

        // Deletion: no confirmation (consistent with the rest of the
        // app — removePage does not have one either), one tap is enough. To
        // be reconsidered if it proves to be a source of accidents in real use.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(HANDLE_SIZE)
                .clip(CircleShape)
                .background(AccentRed)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Supprimer le widget",
                tint = TextPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
