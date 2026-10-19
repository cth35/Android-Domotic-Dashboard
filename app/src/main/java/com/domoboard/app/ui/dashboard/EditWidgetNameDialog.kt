package com.domoboard.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.domoboard.app.model.WidgetType
import com.domoboard.app.ui.theme.*
import kotlin.math.roundToInt

private val PRESET_BG_COLORS = listOf(
    // Neutres & Sombres
    "#1B1C20", "#26282E", "#1E293B", "#111827", "#000000", "#374151",
    // Rouges & Roses
    "#E35B5B", "#32201F", "#EF4444", "#EC4899", "#831843", "#991B1B",
    // Verts & Émeraude
    "#A8D67A", "#22321F", "#10B981", "#059669", "#064E3B", "#15803D",
    // Bleus & Cyan
    "#4A90D9", "#1E40AF", "#06B6D4", "#0284C7", "#0369A1", "#0F172A",
    // Oranges, Jaunes & Violets
    "#E8B26A", "#D97706", "#EAB308", "#AF52DE", "#581C87", "#7C3AED"
)

// Subset shown by default (collapsed palette): neutrals + one accent per family.
private val QUICK_BG_COLORS = listOf(
    "#1B1C20", "#26282E", "#1E293B", "#111827", "#000000",
    "#E35B5B", "#A8D67A", "#4A90D9", "#E8B26A", "#AF52DE", "#10B981"
)

/**
 * Dialog allowing the user to customize a widget's parameters in edit mode.
 *
 * Layout: pinned header + live preview card at the top, scrollable settings in the
 * middle, pinned action bar at the bottom (Save is always reachable).
 * - Custom label (name)
 * - Sensor mode selection (temp, humidity, both) for SENSOR widgets
 * - Background color (quick palette, full palette + hex on demand)
 * - Opacity / alpha slider (0% to 100%)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWidgetNameDialog(
    widgetType: WidgetType = WidgetType.UNKNOWN,
    currentSensorMode: String? = null,
    currentLabel: String,
    currentBackgroundColor: String? = null,
    currentBackgroundAlpha: Float? = null,
    fallbackName: String?,
    onSave: (label: String, backgroundColor: String?, backgroundAlpha: Float?, sensorMode: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var labelText by remember { mutableStateOf(currentLabel) }
    var colorHexText by remember { mutableStateOf(currentBackgroundColor ?: "") }
    var alphaValue by remember { mutableFloatStateOf(currentBackgroundAlpha ?: 1.0f) }
    var sensorModeText by remember { mutableStateOf(currentSensorMode ?: "temp") }
    // Auto-expand the palette when the current color is a custom/non-quick one
    var showAllColors by remember {
        mutableStateOf(
            colorHexText.isNotBlank() && QUICK_BG_COLORS.none { it.equals(colorHexText, ignoreCase = true) }
        )
    }

    val scrollState = rememberScrollState()
    val isSensor = widgetType == WidgetType.SENSOR

    val previewColor = remember(colorHexText, alphaValue) {
        if (colorHexText.isBlank()) parseWidgetBackgroundColor("#1B1C20", alphaValue)
        else parseWidgetBackgroundColor(colorHexText, alphaValue)
    }

    val hexIsValid = colorHexText.isBlank() || parseWidgetBackgroundColor(colorHexText, 1f) != null
    val showHexError = !hexIsValid && colorHexText.length >= 7
    val isDirty = labelText.isNotBlank() ||
            colorHexText.isNotBlank() ||
            alphaValue < 0.99f ||
            sensorModeText != "temp"

    fun resetForm() {
        labelText = ""
        colorHexText = ""
        alphaValue = 1.0f
        sensorModeText = "temp"
        showAllColors = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .widthIn(max = 420.dp)
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
        ) {
            // ---------- Header (pinned) ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Paramètres du widget",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!fallbackName.isNullOrBlank()) {
                        Text(
                            text = "Nom d'origine : $fallbackName",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isDirty) {
                    Text(
                        text = "Réinitialiser",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { resetForm() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // ---------- Live preview (pinned) ----------
            // Dark gradient "wallpaper" so that transparency is visible.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(132.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF2E3340), Color(0xFF0B0C0F)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                WidgetPreviewCard(
                    label = labelText.ifBlank { fallbackName ?: "Nom du widget" },
                    isSensor = isSensor,
                    sensorMode = sensorModeText,
                    background = previewColor ?: SurfaceDark,
                    modifier = Modifier.size(width = 170.dp, height = 108.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Settings (scrollable) ----------
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                // --- Name ---
                SectionLabel("Nom")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        if (labelText.isEmpty()) {
                            Text(
                                text = fallbackName ?: "Nom du widget",
                                color = TextMuted,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(AccentBlue),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (labelText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { labelText = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Effacer",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // --- Sensor mode ---
                if (isSensor) {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Affichage du capteur")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariantDark)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        SensorModeSegment(
                            title = "Température",
                            icons = listOf(Icons.Filled.Thermostat),
                            isSelected = sensorModeText == "temp",
                            onClick = { sensorModeText = "temp" },
                            modifier = Modifier.weight(1f)
                        )
                        SensorModeSegment(
                            title = "Humidité",
                            icons = listOf(Icons.Filled.WaterDrop),
                            isSelected = sensorModeText == "humidity",
                            onClick = { sensorModeText = "humidity" },
                            modifier = Modifier.weight(1f)
                        )
                        SensorModeSegment(
                            title = "Les deux",
                            icons = listOf(Icons.Filled.Thermostat, Icons.Filled.WaterDrop),
                            isSelected = sensorModeText == "both",
                            onClick = { sensorModeText = "both" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // --- Background color ---
                Spacer(Modifier.height(20.dp))
                SectionLabel("Couleur de fond")
                Spacer(Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefaultColorButton(
                        isSelected = colorHexText.isBlank(),
                        onClick = { colorHexText = "" }
                    )
                    val visibleColors = if (showAllColors) PRESET_BG_COLORS else QUICK_BG_COLORS
                    visibleColors.forEach { hex ->
                        BgPresetButton(
                            hex = hex,
                            isSelected = colorHexText.equals(hex, ignoreCase = true),
                            onClick = { colorHexText = hex }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAllColors = !showAllColors }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAllColors) "Moins de couleurs" else "Plus de couleurs",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (showAllColors) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showAllColors) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariantDark)
                            .border(
                                width = 1.dp,
                                color = if (showHexError) AccentRed else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(parseWidgetBackgroundColor(colorHexText, 1f) ?: Color.Transparent)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (colorHexText.isEmpty()) {
                                Text(text = "Code hex  (#RRGGBB)", color = TextMuted, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = colorHexText,
                                onValueChange = { input ->
                                    val withHash = when {
                                        input.startsWith("#") -> input
                                        input.isNotBlank() -> "#$input"
                                        else -> ""
                                    }
                                    colorHexText = withHash.take(9)
                                },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(AccentBlue),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // --- Opacity ---
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Opacité")
                    Text(
                        text = "${(alphaValue * 100).roundToInt()}%",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceVariantDark)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Slider(
                    value = alphaValue,
                    onValueChange = { alphaValue = it },
                    valueRange = 0.0f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = SurfaceVariantDark
                    )
                )

                Spacer(Modifier.height(12.dp))
            }

            // ---------- Action bar (pinned) ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SurfaceVariantDark)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Annuler", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hexIsValid) AccentBlue else AccentBlue.copy(alpha = 0.35f))
                        .clickable(enabled = hexIsValid) {
                            onSave(
                                labelText,
                                colorHexText.ifBlank { null },
                                if (alphaValue < 0.99f) alphaValue else 1.0f,
                                sensorModeText.ifBlank { "temp" }
                            )
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Enregistrer", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
}

/** Miniature of the widget card, reflecting label / color / alpha / sensor mode live. */
@Composable
private fun WidgetPreviewCard(
    label: String,
    isSensor: Boolean,
    sensorMode: String,
    background: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "à l'instant",
            color = TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (isSensor) {
            Spacer(Modifier.weight(1f))
            PreviewSensorValue(sensorMode)
            Spacer(Modifier.height(4.dp))
            PreviewSparkline(
                color = if (sensorMode == "humidity") AccentBlueMuted else AccentOrange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun PreviewSensorValue(mode: String) {
    if (mode == "humidity") {
        Text(text = "58%", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "22,2", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Box {
                Text(text = "°", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (mode == "both") {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            tint = AccentBlueMuted,
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(Modifier.width(1.dp))
                        Text(
                            text = "58%",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSparkline(color: Color, modifier: Modifier = Modifier) {
    val points = remember { listOf(0.45f, 0.6f, 0.4f, 0.7f, 0.55f, 0.5f, 0.65f, 0.8f, 0.6f, 0.72f) }
    Canvas(modifier = modifier) {
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, v ->
            val x = index * stepX
            val y = size.height - v * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun SensorModeSegment(
    title: String,
    icons: List<ImageVector>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) AccentBlue else Color.Transparent,
        label = "segmentBackground"
    )
    val foreground = if (isSelected) TextPrimary else TextSecondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEach { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Outer 36dp touch target with a selection ring; the swatch itself is smaller (26dp). */
@Composable
private fun SwatchFrame(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.border(2.dp, AccentBlue, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun DefaultColorButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    SwatchFrame(isSelected = isSelected, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SurfaceVariantDark)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FormatColorReset,
                contentDescription = "Couleur par défaut",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun BgPresetButton(
    hex: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = parseWidgetBackgroundColor(hex, 1.0f) ?: SurfaceDark
    val checkTint = remember(hex) { checkmarkTint(hex) }

    SwatchFrame(isSelected = isSelected, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Sélectionné",
                    tint = checkTint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun checkmarkTint(hex: String): Color {
    val clean = hex.removePrefix("#")
    if (clean.length == 6) {
        val r = clean.substring(0, 2).toIntOrNull(16) ?: 0
        val g = clean.substring(2, 4).toIntOrNull(16) ?: 0
        val b = clean.substring(4, 6).toIntOrNull(16) ?: 0
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return if (luminance < 140) TextPrimary else Color.Black
    }
    return TextPrimary
}