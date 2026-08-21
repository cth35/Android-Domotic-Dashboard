package com.homehabit.app.ui.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WbIncandescent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.homehabit.app.camera.RtspThumbnailGrabber
import com.homehabit.app.data.SensorKind
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.data.WidgetStateEntry
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetType
import com.homehabit.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "à l'instant" / "il y a Xmin" / "il y a Xh" en dessous de 24h,
 * date jour/mois au-dela.
 */
fun formatRelativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diffMs = (now - epochMillis).coerceAtLeast(0)
    val diffMin = diffMs / 60_000
    val diffHour = diffMs / 3_600_000

    return when {
        diffMin < 1 -> "à l'instant"
        diffHour < 1 -> "il y a ${diffMin}min"
        diffHour < 24 -> "il y a ${diffHour}h"
        else -> SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(epochMillis))
    }
}


/**
 * Affiche le contenu d'un widget selon son type. Les icônes utilisent
 * Material Icons ici comme placeholder ; elles seront remplacées par
 * Android-Iconics + FontAwesome dans une étape suivante (même
 * emplacement d'appel, signature identique).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetCard(
    config: WidgetConfig,
    entry: WidgetStateEntry?,
    modifier: Modifier = Modifier,
    sparkline: List<Float>? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onShutterOpen: (() -> Unit)? = null,
    onShutterStop: (() -> Unit)? = null,
    onShutterClose: (() -> Unit)? = null
) {
    val state = entry?.state
    val light = state as? WidgetLiveState.Light
    val scene = state as? WidgetLiveState.Scene
    val shutter = state as? WidgetLiveState.Shutter
    val lock = state as? WidgetLiveState.Lock
    val backgroundColor = when (config.widgetType) {
        WidgetType.LIGHT, WidgetType.DIMMER ->
            if (light?.isOn == true) AccentGreenSurface else SurfaceDark

        WidgetType.COLOR_LIGHT -> when {
            light?.isOn != true -> SurfaceDark
            light.colorHex != null -> runCatching {
                Color(android.graphics.Color.parseColor(light.colorHex)).copy(alpha = 0.28f)
            }.getOrDefault(AccentGreenSurface)
            else -> AccentGreenSurface
        }

        // Pour une vraie Scene (pas Group), isOn ne reste vrai que
        // jusqu'au prochain poll (5s) : Domoticz ne garde pas d'etat
        // persistant pour un declencheur. Le flash vert bref sert de
        // retour visuel "declenchee", pas un vrai etat durable.
        WidgetType.SCENE -> if (scene?.isOn == true) AccentGreenSurface else SurfaceDark

        // Meme logique "actif/engage" que la lumiere : volet ouvert a
        // plus de la moitie = etat engage, teinte verte coherente avec
        // le reste de la palette plutot qu'une nouvelle couleur dediee.
        WidgetType.SHUTTER -> if ((shutter?.percentOpen ?: 0) > 50) AccentGreenSurface else SurfaceDark

        // Inverse des autres : c'est l'etat DEVERROUILLE qui merite
        // l'attention (teinte rouge), pas l'etat verrouille qui reste
        // neutre — une serrure fermee n'a rien de particulier a signaler.
        WidgetType.LOCK -> if (lock?.isLocked == false) AccentRedSurface else SurfaceDark

        else -> SurfaceDark
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .let {
                if (onClick != null || onLongClick != null) {
                    it.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                } else it
            }
            .padding(10.dp)
    ) {
        when (config.widgetType) {
            WidgetType.WEATHER -> WeatherContent(state as? WidgetLiveState.Weather)
            WidgetType.FORECAST -> ForecastContent(state as? WidgetLiveState.Forecast)
            WidgetType.LIGHT, WidgetType.DIMMER, WidgetType.COLOR_LIGHT ->
                LightContent(config, light)
            WidgetType.THERMOSTAT -> ThermostatContent(state as? WidgetLiveState.Thermostat, sparkline)
            WidgetType.SHUTTER -> if (config.source?.shutterStyle == "toggle") {
                ShutterToggleContent(state as? WidgetLiveState.Shutter)
            } else {
                ShutterButtonsContent(
                    state = state as? WidgetLiveState.Shutter,
                    onOpen = { onShutterOpen?.invoke() },
                    onStop = { onShutterStop?.invoke() },
                    onClose = { onShutterClose?.invoke() }
                )
            }
            WidgetType.LOCK -> LockContent(config.label, state as? WidgetLiveState.Lock)
            WidgetType.SENSOR -> SensorContent(config, state as? WidgetLiveState.Sensor, sparkline)
            WidgetType.SCENE -> SceneContent(config, state as? WidgetLiveState.Scene)
            WidgetType.CAMERA -> CameraContent(config, state as? WidgetLiveState.Camera)
            else -> EmptyContent(config.label)
        }

        if (entry != null) {
            LastUpdateBadge(
                lastUpdate = entry.lastUpdate,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/** Petit badge discret, se rafraichit tout seul toutes les 30s pour que le texte relatif reste juste. */
@Composable
private fun LastUpdateBadge(lastUpdate: Long, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceDark.copy(alpha = 0.7f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = formatRelativeTime(lastUpdate, now),
            color = TextMuted,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun WeatherContent(state: WidgetLiveState.Weather?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WidgetIcon(Icons.Filled.Cloud, AccentBlueMuted)
        Column {
            Text(
                text = state?.let { "${it.temperature}°C" } ?: "--",
                color = TextPrimary,
                fontSize = 18.sp
            )
            Text(
                text = state?.let { "${it.condition} · min ${it.min}° max ${it.max}°" } ?: "Chargement...",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Prevision 7 jours, en ligne defilable horizontalement (fonctionne
 * quelle que soit la taille du widget, meme etroit — recommande en
 * largeur w=4 dans l'exemple JSON pour voir plusieurs jours sans avoir
 * a swiper). Pas teste sur device reel : le scroll horizontal a
 * l'interieur d'un widget pourrait entrer en conflit avec le swipe de
 * page du HorizontalPager en dehors du mode edition — a valider.
 */
@Composable
private fun ForecastContent(state: WidgetLiveState.Forecast?) {
    val days = state?.days.orEmpty()

    if (days.isEmpty()) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            WidgetIcon(Icons.Filled.Cloud, TextSecondary)
            Text(text = "Prevision 7 jours", color = TextSecondary, fontSize = 11.sp)
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(36.dp)
            ) {
                Text(text = day.dayLabel, color = TextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Icon(
                    iconForWeatherCode(day.weatherCode),
                    contentDescription = day.condition,
                    tint = AccentBlueMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(text = "${day.tempMax}°", color = TextPrimary, fontSize = 11.sp)
                Text(text = "${day.tempMin}°", color = TextSecondary, fontSize = 9.sp)
            }
        }
    }
}

/** Mapping large des codes WMO vers une icone — meme esprit que WeatherCodeMapper mais pour le glyphe plutot que le libelle. */
private fun iconForWeatherCode(code: Int?): ImageVector = when (code) {
    0 -> Icons.Filled.WbSunny
    1, 2, 3 -> Icons.Filled.Cloud
    45, 48 -> Icons.Filled.Cloud
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Filled.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Bolt
    else -> Icons.Filled.Cloud
}

@Composable
private fun LightContent(config: WidgetConfig, state: WidgetLiveState.Light?) {
    val isOn = state?.isOn == true
    val isColorLight = config.widgetType == WidgetType.COLOR_LIGHT
    val isDimmable = config.widgetType != WidgetType.LIGHT

    val swatchColor = if (isColorLight && isOn) {
        state?.colorHex?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        }
    } else null

    val iconTint = when {
        !isOn -> TextSecondary
        swatchColor != null -> swatchColor
        else -> AccentGreen
    }

    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WidgetIcon(if (isOn) Icons.Filled.Lightbulb else Icons.Outlined.WbIncandescent, iconTint)
            if (swatchColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                )
            }
        }
        Column {
            Text(
                text = config.label ?: "Lumiere",
                color = if (isOn) TextPrimary else TextSecondary,
                fontSize = 11.sp
            )
            if (isDimmable && isOn && state?.brightness != null) {
                Text(text = "${state.brightness}%", color = TextSecondary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ThermostatContent(state: WidgetLiveState.Thermostat?, sparkline: List<Float>?) {
    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
        WidgetIcon(Icons.Filled.Thermostat, AccentOrange)
        Column {
            Text(
                text = state?.let { "${it.temperature}°C" } ?: "--",
                color = TextPrimary,
                fontSize = 11.sp
            )
            if (sparkline != null && sparkline.size >= 2) {
                Spacer(Modifier.height(4.dp))
                Sparkline(
                    values = sparkline,
                    color = AccentOrange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ShutterButtonsContent(
    state: WidgetLiveState.Shutter?,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val isOpen = (state?.percentOpen ?: 0) > 50
    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
        WidgetIcon(Icons.Filled.Blinds, if (isOpen) AccentGreen else TextSecondary)
        Text(
            text = state?.let { "Volet ${it.percentOpen}%" } ?: "Volet",
            color = TextPrimary,
            fontSize = 11.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ShutterButton(Icons.Filled.KeyboardArrowUp, "Ouvrir", onOpen)
            ShutterButton(Icons.Filled.Stop, "Stop", onStop)
            ShutterButton(Icons.Filled.KeyboardArrowDown, "Fermer", onClose)
        }
    }
}

/**
 * Style "toggle" (source.shutterStyle == "toggle") : tap sur tout le
 * widget bascule ouvert/ferme selon la position actuelle (seuil 50%),
 * pas de bouton stop accessible dans ce mode — plus compact, mais moins
 * de controle que le style 3 boutons (par defaut).
 */
@Composable
private fun ShutterToggleContent(state: WidgetLiveState.Shutter?) {
    val isOpen = (state?.percentOpen ?: 0) > 50
    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
        WidgetIcon(Icons.Filled.Blinds, if (isOpen) AccentGreen else TextSecondary)
        Text(
            text = state?.let { "Volet ${it.percentOpen}%" } ?: "Volet",
            color = TextPrimary,
            fontSize = 11.sp
        )
    }
}

/**
 * Zone tactile volontairement petite (20dp) pour tenir 3 boutons sur un
 * widget 1x1 — a valider au doigt sur un vrai device, comme la poignee
 * de resize en mode edition. Fonctionne quelle que soit la taille du
 * widget, mais plus confortable sur un widget 2x1.
 */
@Composable
private fun ShutterButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceVariantDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = TextPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LockContent(label: String?, state: WidgetLiveState.Lock?) {
    val locked = state?.isLocked == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetIcon(
            icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            tint = if (locked) TextSecondary else AccentRed
        )
        Text(text = label ?: "Porte", color = TextPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun SensorContent(config: WidgetConfig, state: WidgetLiveState.Sensor?, sparkline: List<Float>?) {
    val kind = state?.kind ?: SensorKind.GENERIC

    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
        WidgetIcon(iconForSensorKind(kind), tintForSensorKind(kind))
        Column {
            Text(text = state?.displayValue ?: "--", color = TextPrimary, fontSize = 15.sp)
            Text(text = config.label ?: "Capteur", color = TextSecondary, fontSize = 9.sp)

            state?.gaugePercent?.let { percent ->
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SurfaceVariantDark)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(percent.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(tintForSensorKind(kind))
                    )
                }
            }

            if (kind == SensorKind.TEMPERATURE && sparkline != null && sparkline.size >= 2) {
                Spacer(Modifier.height(4.dp))
                Sparkline(
                    values = sparkline,
                    color = tintForSensorKind(kind),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
            }
        }
    }
}

/**
 * Scene : icone + label, tap = declenchement (voir DashboardScreen).
 * "Actif"/"Inactif" affiche uniquement pour un Group (etat reel),
 * jamais pour une Scene (pas d'etat durable cote Domoticz).
 */
@Composable
private fun SceneContent(config: WidgetConfig, state: WidgetLiveState.Scene?) {
    val isOn = state?.isOn == true
    val tint = if (isOn) AccentGreen else TextSecondary

    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
        WidgetIcon(Icons.Filled.AutoAwesome, tint)
        Column {
            Text(
                text = config.label ?: "Scene",
                color = if (isOn) TextPrimary else TextSecondary,
                fontSize = 11.sp
            )
            if (state?.isGroup == true) {
                Text(
                    text = if (isOn) "Actif" else "Inactif",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Mini-graphe en ligne (sparkline), purement decoratif : pas d'axes,
 * pas de labels, juste la tendance. Normalise entre le min et le max
 * de la serie fournie.
 */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return

    val minValue = values.min()
    val maxValue = values.max()
    val range = (maxValue - minValue).takeIf { it > 0.01f } ?: 1f

    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value - minValue) / range
            val y = size.height - (normalized * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun iconForSensorKind(kind: SensorKind): ImageVector = when (kind) {
    SensorKind.TEMPERATURE -> Icons.Filled.Thermostat
    SensorKind.HUMIDITY -> Icons.Filled.WaterDrop
    SensorKind.RAIN -> Icons.Filled.WaterDrop
    SensorKind.WIND -> Icons.Filled.Air
    SensorKind.UV -> Icons.Filled.WbSunny
    SensorKind.BAROMETER -> Icons.Filled.Speed
    SensorKind.PERCENTAGE -> Icons.Filled.PieChart
    SensorKind.ENERGY -> Icons.Filled.Bolt
    SensorKind.GENERIC -> Icons.Filled.Info
}

private fun tintForSensorKind(kind: SensorKind): Color = when (kind) {
    SensorKind.TEMPERATURE -> AccentOrange
    SensorKind.HUMIDITY -> AccentBlueMuted
    SensorKind.RAIN -> AccentBlueMuted
    SensorKind.WIND -> TextPrimary
    SensorKind.UV -> AccentOrange
    SensorKind.BAROMETER -> TextPrimary
    SensorKind.PERCENTAGE -> AccentGreen
    SensorKind.ENERGY -> AccentOrange
    SensorKind.GENERIC -> TextSecondary
}

@Composable
private fun CameraContent(config: WidgetConfig, state: WidgetLiveState.Camera?) {
    val snapshotUrl = config.source?.url?.takeIf { it.isNotBlank() }
    val rtspUrl = config.source?.rtspUrl?.takeIf { it.isNotBlank() }
    val refreshMs = ((config.source?.refreshSeconds ?: 15).coerceAtLeast(5)) * 1000L

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            snapshotUrl != null -> SnapshotImage(snapshotUrl, refreshMs)
            rtspUrl != null -> RtspFallbackThumbnail(rtspUrl, refreshMs)
            else -> CameraPlaceholder()
        }

        if (state?.isLive == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = "LIVE", color = AccentRed, fontSize = 10.sp)
            }
        }
        Text(
            text = state?.label ?: config.label ?: "Camera",
            color = TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )
    }
}

/** Snapshot HTTP, rafraichi periodiquement (cache-busting simple sur l'url). */
@Composable
private fun SnapshotImage(url: String, refreshMs: Long) {
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(url, refreshMs) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(refreshMs)
        }
    }
    val bustedUrl = remember(url, tick) {
        if (url.contains("?")) "$url&_t=$tick" else "$url?_t=$tick"
    }
    AsyncImage(
        model = bustedUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
    )
}

/**
 * Fallback best-effort quand aucune url_snapshot n'est configuree : tente
 * de capturer une frame du flux RTSP. Plus couteux qu'un simple GET HTTP
 * (ouvre une vraie connexion RTSP), donc l'intervalle reel est au moins
 * de 30s quel que soit refreshSeconds. En cas d'echec (device trop
 * ancien, flux indisponible...) on retombe simplement sur le placeholder,
 * ce n'est pas bloquant.
 */
@Composable
private fun RtspFallbackThumbnail(rtspUrl: String, refreshMs: Long) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(rtspUrl) {
        val grabber = RtspThumbnailGrabber(context)
        val effectiveInterval = refreshMs.coerceAtLeast(30_000L)
        while (true) {
            val bitmap = runCatching { grabber.capture(rtspUrl) }.getOrNull()
            if (bitmap != null) thumbnail = bitmap
            delay(effectiveInterval)
        }
    }

    val bitmap = thumbnail
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        CameraPlaceholder()
    }
}

@Composable
private fun CameraPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = TextPrimary)
    }
}

@Composable
private fun EmptyContent(label: String?) {
    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
        WidgetIcon(Icons.Filled.QuestionMark, TextMuted)
        Text(text = label ?: "?", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun WidgetIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}
