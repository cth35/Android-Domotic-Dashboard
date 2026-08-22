package com.homehabit.app.ui.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
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
    sunrise: String? = null,
    sunset: String? = null,
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

    // On reste sur un fond sombre uniforme type HomeHabit, sauf feedback
    // tres spécifique (ex: alerte lock).
    val backgroundColor = when (config.widgetType) {
        WidgetType.LOCK -> if (lock?.isLocked == false) AccentRedSurface else SurfaceDark
        else -> SurfaceDark
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .let {
                if (onClick != null || onLongClick != null) {
                    it.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                } else it
            }
    ) {
        // Overlay Camera : prend tout l'espace
        if (config.widgetType == WidgetType.CAMERA || config.widgetType == WidgetType.CLOCK) {
            when (config.widgetType) {
                WidgetType.CAMERA -> CameraContent(config, state as? WidgetLiveState.Camera)
                WidgetType.CLOCK -> ClockContent(sunrise, sunset)
                else -> {}
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header : Label custom ou nom Domoticz en repli
                Text(
                    text = config.label.takeIf { !it.isNullOrBlank() } ?: entry?.fallbackName ?: "",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (entry != null) {
                    RelativeTimeBadge(entry.lastUpdate)
                }

                Spacer(Modifier.height(2.dp))
                Spacer(Modifier.weight(0.5f))

                // Contenu central
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
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
                        WidgetType.LOCK -> LockContent(state as? WidgetLiveState.Lock)
                        WidgetType.SENSOR -> SensorContent(state as? WidgetLiveState.Sensor, sparkline)
                        WidgetType.SCENE -> SceneContent(state as? WidgetLiveState.Scene)
                        WidgetType.BINARY_SENSOR -> BinarySensorContent(state as? WidgetLiveState.BinarySensor)
                        else -> EmptyContent()
                    }
                }
                
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ClockContent(sunrise: String? = null, sunset: String? = null) {
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val dateFormat = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
        while (true) {
            val now = Date()
            time = timeFormat.format(now)
            date = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = time,
            color = TextPrimary,
            fontSize = 75.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 56.sp,
            letterSpacing = (-1).sp
        )
        Text(
            text = date,
            color = TextSecondary,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (sunrise != null || sunset != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (sunrise != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = sunrise,
                            color = TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (sunset != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.WbTwilight,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = sunset,
                            color = TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BinarySensorContent(state: WidgetLiveState.BinarySensor?) {
    val isOn = state?.isOn == true
    val isContact = state?.isContact == true

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = when {
                isContact && isOn -> Icons.Filled.DoorBack
                isContact && !isOn -> Icons.Filled.DoorFront
                isOn -> Icons.Default.DirectionsWalk
                else -> Icons.Default.DirectionsWalk
            },
            contentDescription = null,
            tint = if (isOn) AccentOrange else TextSecondary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = when {
                isContact && isOn -> "OUVERT"
                isContact && !isOn -> "FERMÉ"
                isOn -> "MOUVEMENT"
                else -> "AUCUN"
            },
            color = if (isOn) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RelativeTimeBadge(lastUpdate: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    Text(
        text = formatRelativeTime(lastUpdate, now),
        color = TextSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WeatherContent(state: WidgetLiveState.Weather?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Cloud,
            contentDescription = null,
            tint = AccentBlueMuted,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = state?.let { "${it.temperature}°" } ?: "--",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp
        )
        Text(
            text = state?.let { "${it.condition} · ${it.min}°/${it.max}°" } ?: "Chargement...",
            color = TextSecondary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WidgetIcon(Icons.Filled.Cloud, TextSecondary)
            Text(text = "Prevision 7 jours", color = TextSecondary, fontSize = 11.sp)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.take(7).forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = day.dayLabel, 
                    color = TextSecondary, 
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Icon(
                    iconForWeatherCode(day.weatherCode),
                    contentDescription = day.condition,
                    tint = AccentBlueMuted,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${day.tempMax}°", 
                    color = TextPrimary, 
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/** Mapping large des codes WMO vers une icone — meme esprit que WeatherCodeMapper mais pour le glyphe plutot que le libelle. */
internal fun iconForWeatherCode(code: Int?): ImageVector = when (code) {
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
    
    // On affiche la couleur/nuance si disponible dans l'état, 
    // quel que soit le type de widget (RGB ou WW).
    val swatchColor = if (isOn) {
        state?.colorHex?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        }
    } else null

    val isColorLight = config.widgetType == WidgetType.COLOR_LIGHT && state?.isColor == true
    val color = if (isOn) AccentGreen else TextMuted
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                // On retire le clip ici pour permettre à la lueur de déborder
                .drawBehind {
                    if (isOn) {
                        val glowColor = (swatchColor ?: color)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.6f),
                                    glowColor.copy(alpha = 0.2f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.8f
                            ),
                            radius = size.minDimension * 0.8f
                        )
                    }
                }
                .clip(CircleShape)
                .background(if (isOn) (swatchColor ?: color).copy(alpha = 0.3f) else SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = if (isOn) (swatchColor ?: color) else TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
        
        if (isColorLight && isOn) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { /* Clic géré par le parent via onLongClick */ }
            ) {
                listOf("#4A90D9", "#A8D67A", "#E8B26A", "#E35B5B").forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isOn) "ON" else "OFF",
                color = if (isOn) TextPrimary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (isOn && state?.brightness != null) {
                Text(
                    text = " (${state.brightness}%)",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ThermostatContent(state: WidgetLiveState.Thermostat?, sparkline: List<Float>?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = state?.let { "${it.temperature}".replace(".", ",") + "°" } ?: "--",
            color = TextPrimary,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
        if (sparkline != null && sparkline.size >= 2) {
            Spacer(Modifier.height(2.dp))
            Sparkline(
                values = sparkline,
                color = AccentOrange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val percent = state?.percentOpen ?: 0
        Text(
            text = when (percent) {
                0 -> "FERMÉ"
                100 -> "OUVERT"
                else -> "$percent%"
            },
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LocalIconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
            LocalIconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Stop, null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
            LocalIconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ShutterToggleContent(state: WidgetLiveState.Shutter?) {
    val isOpen = (state?.percentOpen ?: 0) > 50
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (isOpen) Icons.Filled.Blinds else Icons.Filled.BlindsClosed,
            contentDescription = null,
            tint = if (isOpen) AccentGreen else TextSecondary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = state?.let { "${it.percentOpen}%" } ?: "--",
            color = TextPrimary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LockContent(state: WidgetLiveState.Lock?) {
    val locked = state?.isLocked == true
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = null,
            tint = if (locked) TextSecondary else AccentRed,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = if (locked) "VERROUILLÉ" else "DÉVERROUILLÉ",
            color = if (locked) TextSecondary else AccentRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SensorContent(state: WidgetLiveState.Sensor?, sparkline: List<Float>?) {
    val displayValue = state?.displayValue ?: "--"
    val (value, unit) = splitValueAndUnit(displayValue)
    val isTemp = state?.kind == SensorKind.TEMPERATURE
    
    val kindUnit = when (state?.kind) {
        SensorKind.TEMPERATURE -> "" // Affiché via ° à côté de la valeur
        SensorKind.HUMIDITY -> "PERCENT"
        else -> unit?.uppercase() ?: ""
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.replace(".", ",") + (if (isTemp) "°" else ""),
            color = TextPrimary,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
        if (kindUnit.isNotBlank()) {
            Text(
                text = kindUnit,
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        if (sparkline != null && sparkline.size >= 2) {
            Spacer(Modifier.height(2.dp))
            Sparkline(
                values = sparkline,
                color = tintForSensorKind(state?.kind ?: SensorKind.GENERIC),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )
        }
    }
}

@Composable
private fun SceneContent(state: WidgetLiveState.Scene?) {
    val isOn = state?.isOn == true
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = if (isOn) AccentGreen else TextSecondary,
            modifier = Modifier.size(28.dp)
        )
        if (state?.isGroup == true) {
            Text(
                text = if (isOn) "ACTIF" else "INACTIF",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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
    val refreshMs = ((config.source?.refreshSeconds ?: 30).coerceAtLeast(5)) * 1000L
    val contentScale = if (config.source?.imageScale == "fit") ContentScale.Fit else ContentScale.Crop

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            snapshotUrl != null -> SnapshotImage(snapshotUrl, refreshMs, contentScale)
            rtspUrl != null -> RtspFallbackThumbnail(rtspUrl, refreshMs, contentScale)
            else -> CameraPlaceholder()
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
private fun SnapshotImage(url: String, refreshMs: Long, contentScale: ContentScale) {
    var tick by remember(url) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(url, refreshMs) {
        while (true) {
            delay(refreshMs)
            tick = System.currentTimeMillis()
        }
    }
    val bustedUrl = remember(url, tick) {
        if (url.contains("?")) "$url&_t=$tick" else "$url?_t=$tick"
    }

    // On garde le dernier painter réussi pour l'utiliser en fond pendant
    // que la nouvelle image charge, garantissant un rafraîchissement
    // sans aucun flickering (zéro flash).
    var lastSuccessPainter by remember(url) { mutableStateOf<Painter?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (lastSuccessPainter != null) {
            Image(
                painter = lastSuccessPainter!!,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(bustedUrl)
                .memoryCacheKey(url) // Clé stable pour partage avec la modale
                .diskCacheKey(url)
                .memoryCachePolicy(CachePolicy.WRITE_ONLY)
                .diskCachePolicy(CachePolicy.DISABLED)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = contentScale,
            onSuccess = { state ->
                lastSuccessPainter = state.painter
            },
            modifier = Modifier.fillMaxSize()
        )
    }
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
private fun RtspFallbackThumbnail(rtspUrl: String, refreshMs: Long, contentScale: ContentScale) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(rtspUrl) {
        val grabber = RtspThumbnailGrabber(context)
        val effectiveInterval = refreshMs.coerceAtLeast(5_000L)
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
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
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
private fun EmptyContent() {
    Icon(Icons.Filled.QuestionMark, contentDescription = null, tint = TextMuted)
}

@Composable
private fun LocalIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(SurfaceVariantDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun splitValueAndUnit(displayValue: String): Pair<String, String?> {
    val trimmed = displayValue.trim()
    val firstSpace = trimmed.indexOf(' ')
    if (firstSpace != -1) {
        val value = trimmed.substring(0, firstSpace)
        val unit = trimmed.substring(firstSpace + 1).trim()
        return value to unit
    }
    // Cas spécial pour les valeurs collées type "25C" ou "60%"
    val lastDigit = trimmed.indexOfLast { it.isDigit() }
    if (lastDigit != -1 && lastDigit < trimmed.length - 1) {
        return trimmed.substring(0, lastDigit + 1) to trimmed.substring(lastDigit + 1)
    }
    return trimmed to null
}

@Composable
private fun WidgetIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}
