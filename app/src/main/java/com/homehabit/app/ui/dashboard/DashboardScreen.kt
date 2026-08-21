package com.homehabit.app.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val WIDGET_GAP = 8.dp
private val HANDLE_SIZE = 22.dp

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val config by viewModel.config.collectAsState()
    val pages = config.pages
    val widgetStates by viewModel.widgetStates.collectAsState()
    val sparklines by viewModel.sparklines.collectAsState()
    val availableDevices by viewModel.availableDevices.collectAsState()
    val availableScenes by viewModel.availableScenes.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var cameraModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var thermostatModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var lightModalWidget by remember { mutableStateOf<WidgetConfig?>(null) }
    var managePageIndex by remember { mutableStateOf<Int?>(null) }

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    // Garde le ViewModel informe de la page visible : addWidget() en a
    // besoin pour savoir ou placer un nouveau widget.
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentPage(pagerState.currentPage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageTabsBar(
                pages = pages,
                currentPage = pagerState.currentPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0)),
                isEditMode = isEditMode,
                onPageSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                onPageLongPress = { index -> managePageIndex = index },
                onAddPage = { viewModel.addPage() }
            )

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
                        onDeleteWidget = viewModel::removeWidget
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
                            onClick = when {
                                isEditMode -> null
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
                                widgetConfig.widgetType == WidgetType.THERMOSTAT ->
                                    { { thermostatModalWidget = widgetConfig } }
                                widgetConfig.widgetType == WidgetType.CAMERA && widgetConfig.source?.rtspUrl != null ->
                                    { { cameraModalWidget = widgetConfig } }
                                else -> null
                            },
                            onLongClick = if (!isEditMode && isDimmableLight) {
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

        FloatingActionButton(
            onClick = { viewModel.toggleEditMode() },
            containerColor = if (isEditMode) AccentBlue else SurfaceDark,
            contentColor = TextPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .size(44.dp)
        ) {
            Icon(
                imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                contentDescription = if (isEditMode) "Terminer l'edition" else "Mode edition"
            )
        }

        FloatingActionButton(
            onClick = { showSettingsDialog = true },
            containerColor = SurfaceDark,
            contentColor = TextSecondary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 76.dp, end = 20.dp)
                .size(44.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Reglages")
        }

        if (isEditMode) {
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current
            val fullUrl = remember(config.settings.httpAuthToken) {
                "http://${NetworkUtils.getLocalIpAddress() ?: "?"}:8090/?token=${config.settings.httpAuthToken}"
            }

            Text(
                text = fullUrl,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(fullUrl))
                        Toast.makeText(context, "URL copiee", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

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

    lightModalWidget?.let { widget ->
        val light = widgetStates[widget.id]?.state as? WidgetLiveState.Light
        LightAdjustDialog(
            label = widget.label ?: "Lumiere",
            isColorLight = widget.widgetType == WidgetType.COLOR_LIGHT,
            currentBrightness = light?.brightness ?: 100,
            currentColorHex = light?.colorHex,
            onBrightnessChange = { percent -> viewModel.setBrightness(widget.id, percent) },
            onColorChange = { hex -> viewModel.setLightColor(widget.id, hex) },
            onDismiss = { lightModalWidget = null }
        )
    }

    manageDialogHost(pageIndex = managePageIndex, pages = pages, viewModel = viewModel) {
        managePageIndex = null
    }
}

/**
 * Petit wrapper pour garder le corps de DashboardScreen lisible : affiche
 * PageManageDialog si un index de page est en cours d'edition (long-press
 * sur un onglet).
 */
@Composable
private fun manageDialogHost(
    pageIndex: Int?,
    pages: List<DashboardPage>,
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val index = pageIndex ?: return
    val page = pages.getOrNull(index) ?: return

    PageManageDialog(
        currentName = page.name,
        canDelete = pages.size > 1,
        onRename = { newName ->
            viewModel.renamePage(index, newName)
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
 * Grille libre : chaque widget est positionné en absolu à partir de ses
 * coordonnées (x, y) et dimensionné selon (w, h), en unités de cellule.
 * Les cases non occupées restent simplement vides en dehors d'un drag.
 * Opère sur UNE page a la fois (grid + widgets), page-agnostic autrement
 * — c'est l'appelant (DashboardScreen, dans le pager) qui choisit quelle
 * page afficher.
 *
 * Pendant un drag de repositionnement, un aperçu (GridEngine.resolvePushLayout)
 * est calculé en continu et animé pour tous les widgets sauf celui qu'on
 * déplace (qui suit le doigt brut, sans lag). Rien n'est persisté avant
 * le relâchement : si le doigt s'éloigne d'une zone avant de lâcher, les
 * widgets qu'on avait poussés reviennent naturellement à leur position
 * d'origine (le calcul est refait à chaque évènement de drag).
 */
@Composable
private fun DashboardGrid(
    gridConfig: GridConfig,
    widgets: List<WidgetConfig>,
    isEditMode: Boolean,
    onResizeUpdate: (String, GridEngine.Rect) -> Unit,
    onMoveCommit: (Map<String, GridEngine.Rect>) -> Unit,
    onDeleteWidget: (String) -> Unit,
    content: @Composable (widget: WidgetConfig) -> Unit
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(0) }
    var dragState by remember { mutableStateOf<DragUiState?>(null) }

    val columns = gridConfig.columns.coerceAtLeast(1)
    val gapPx = with(density) { WIDGET_GAP.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .onSizeChanged { containerWidthPx = it.width }
            .padding(12.dp)
    ) {
        if (containerWidthPx == 0) return@Box

        val cellPx = (containerWidthPx - gapPx * (columns - 1)) / columns
        val cellStepPx = cellPx + gapPx

        val maxRow = (widgets.maxOfOrNull { it.y + it.h } ?: 0) +
            (dragState?.let { 4 } ?: 0) // marge visuelle pendant qu'on pousse vers le bas
        val totalHeightDp = with(density) { (cellStepPx * maxRow).toDp() }

        Box(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
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
                val heightDp = with(density) { (cellPx * widget.h + gapPx * (widget.h - 1)).toDp() }

                if (isBeingDragged) {
                    // Suit le doigt sans lag : position d'origine + delta brut, pas d'animation.
                    val committedOffsetX = with(density) { (cellStepPx * widget.x).toDp() }
                    val committedOffsetY = with(density) { (cellStepPx * widget.y).toDp() }
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
                        EditOverlay(
                            widget = widget,
                            allWidgets = widgets,
                            columns = columns,
                            cellStepPx = cellStepPx,
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
                } else {
                    val targetRect = dragState?.preview?.get(widget.id)
                        ?: GridEngine.Rect(widget.x, widget.y, widget.w, widget.h)
                    val targetOffsetX = with(density) { (cellStepPx * targetRect.x).toDp() }
                    val targetOffsetY = with(density) { (cellStepPx * targetRect.y).toDp() }
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

/** Etat partagé du drag en cours, pour que TOUS les widgets puissent réagir (poussée) pendant qu'un seul est déplacé. */
private data class DragUiState(
    val draggedId: String,
    val preview: Map<String, GridEngine.Rect>,
    val rawOffsetPx: Offset
)

/**
 * Superpose au widget une zone de déplacement (tout le corps) et une
 * poignée de redimensionnement (coin bas-droit).
 *
 * Déplacement : à chaque évènement de drag, calcule la cellule visée
 * (snap sur la grille) et le layout complet résultant via
 * GridEngine.resolvePushLayout (les widgets dans le chemin sont poussés
 * plus bas). Ce résultat n'est qu'un aperçu (onMovePreview) tant que le
 * doigt n'est pas relâché ; c'est seulement à onMoveCommit que le
 * ViewModel persiste réellement la disposition.
 *
 * Redimensionnement : inchangé par rapport à avant, commit immédiat à
 * chaque étape valide (pas de préview poussée pour le resize).
 */
@Composable
private fun EditOverlay(
    widget: WidgetConfig,
    allWidgets: List<WidgetConfig>,
    columns: Int,
    cellStepPx: Float,
    onMovePreview: (Map<String, GridEngine.Rect>, Offset) -> Unit,
    onMoveCommit: (Map<String, GridEngine.Rect>) -> Unit,
    onMoveCancel: () -> Unit,
    onResizeUpdate: (String, GridEngine.Rect) -> Unit,
    onDelete: () -> Unit
) {
    val latestWidget = rememberUpdatedState(widget)
    val latestAllWidgets = rememberUpdatedState(allWidgets)

    var startX by remember { mutableStateOf(0) }
    var startY by remember { mutableStateOf(0) }
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
                        val deltaCellY = (moveAccum.y / cellStepPx).roundToInt()
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

    var startW by remember { mutableStateOf(0) }
    var startH by remember { mutableStateOf(0) }
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
                        val deltaCellH = (resizeAccum.y / cellStepPx).roundToInt()
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

    // Suppression : pas de confirmation (coherent avec le reste de
    // l'app — removePage n'en a pas non plus), un tap suffit. A
    // reconsiderer si ca s'avere source d'accidents en usage reel.
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
