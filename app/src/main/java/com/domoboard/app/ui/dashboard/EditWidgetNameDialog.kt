package com.domoboard.app.ui.dashboard

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

/**
 * Dialog allowing the user to customize a widget's parameters in edit mode:
 * - Custom label (name)
 * - Custom background color (expanded preset palette or hex input)
 * - Opacity / alpha slider (0% to 100%)
 * - Live preview box
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWidgetNameDialog(
    currentLabel: String,
    currentBackgroundColor: String? = null,
    currentBackgroundAlpha: Float? = null,
    fallbackName: String?,
    onSave: (label: String, backgroundColor: String?, backgroundAlpha: Float?) -> Unit,
    onDismiss: () -> Unit
) {
    var labelText by remember { mutableStateOf(currentLabel) }
    var colorHexText by remember { mutableStateOf(currentBackgroundColor ?: "") }
    var alphaValue by remember { mutableFloatStateOf(currentBackgroundAlpha ?: 1.0f) }

    val scrollState = rememberScrollState()

    val previewColor = remember(colorHexText, alphaValue) {
        if (colorHexText.isBlank()) parseWidgetBackgroundColor("#1B1C20", alphaValue)
        else parseWidgetBackgroundColor(colorHexText, alphaValue)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(text = "Parametres du widget", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                if (!fallbackName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Nom d'origine : $fallbackName",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // --- Apercu en direct ---
                Text(text = "Apercu de la carte", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor ?: SurfaceDark)
                        .border(1.dp, SurfaceVariantDark, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelText.ifBlank { fallbackName ?: "Nom du widget" },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))

                // --- Champ Nom ---
                Text(text = "Nom personnalise", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))

                BasicTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AccentBlue),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .padding(12.dp)
                )

                Spacer(Modifier.height(18.dp))

                // --- Couleur de fond ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Couleur de fond (${PRESET_BG_COLORS.size} nuances)", color = TextSecondary, fontSize = 11.sp)
                    if (colorHexText.isNotBlank()) {
                        Text(
                            text = "Reinitialiser la couleur",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { colorHexText = "" }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Palette de couleurs responsive réagencée pour remplir la largeur de la modale
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PRESET_BG_COLORS.forEach { hex ->
                        BgPresetButton(
                            hex = hex,
                            isSelected = colorHexText.equals(hex, ignoreCase = true),
                            onClick = { colorHexText = hex }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Saisie Hex personnalisee
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "Code Hex : ", color = TextSecondary, fontSize = 12.sp)
                    BasicTextField(
                        value = colorHexText,
                        onValueChange = { input ->
                            colorHexText = if (input.startsWith("#")) input else if (input.isNotBlank()) "#$input" else ""
                        },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(AccentBlue),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // --- Opacite / Alpha ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Opacite (Alpha)", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "${(alphaValue * 100).roundToInt()}%",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))

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

                Spacer(Modifier.height(20.dp))

                // --- Boutons d'action ---
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue)
                            .clickable {
                                onSave(
                                    labelText,
                                    colorHexText.ifBlank { null },
                                    if (alphaValue < 0.99f) alphaValue else 1.0f
                                )
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Enregistrer", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (currentLabel.isNotBlank() || !currentBackgroundColor.isNullOrBlank() || (currentBackgroundAlpha ?: 1.0f) < 0.99f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .clickable {
                                    labelText = ""
                                    colorHexText = ""
                                    alphaValue = 1.0f
                                    onSave("", null, 1.0f)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tout reinitialiser", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentRedSurface.copy(alpha = 0.5f))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Annuler", color = AccentRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
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

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AccentBlue else SurfaceVariantDark,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selectionne",
                tint = checkTint,
                modifier = Modifier.size(18.dp)
            )
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
