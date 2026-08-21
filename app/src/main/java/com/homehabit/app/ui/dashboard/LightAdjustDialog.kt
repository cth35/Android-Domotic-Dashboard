package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.homehabit.app.ui.theme.AccentBlue
import com.homehabit.app.ui.theme.SurfaceDark
import com.homehabit.app.ui.theme.SurfaceVariantDark
import com.homehabit.app.ui.theme.TextPrimary
import com.homehabit.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Palette de presets plutot qu'un vrai color picker HSV : suffisant pour
 * une Hue, plus simple a utiliser au doigt sur un ecran mural.
 */
private val COLOR_PRESETS = listOf(
    "#FFE9C6", "#F5F8FF", "#FF3B30", "#FF9500", "#FFD60A",
    "#34C759", "#0A84FF", "#AF52DE", "#FF2D9B"
)

/** Delai d'inactivite avant fermeture automatique, apres la derniere action (brightness ou couleur). */
private const val AUTO_CLOSE_DELAY_MS = 1_500L

@Composable
fun LightAdjustDialog(
    label: String,
    isColorLight: Boolean,
    currentBrightness: Int,
    currentColorHex: String?,
    onBrightnessChange: (Int) -> Unit,
    onColorChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var brightness by remember { mutableStateOf(currentBrightness.coerceIn(0, 100)) }

    // Se relance a chaque action (brightness +/- ou couleur) grace a la
    // cle sur lastActionAt : un tap rapproche n'annule pas la fermeture,
    // il la reporte simplement. Permet d'enchainer plusieurs taps +/-
    // sans que la modale ne se ferme entre deux, tout en fermant seule
    // des que l'utilisateur arrete d'interagir.
    var lastActionAt by remember { mutableStateOf(0L) }
    LaunchedEffect(lastActionAt) {
        if (lastActionAt > 0L) {
            delay(AUTO_CLOSE_DELAY_MS)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = label, color = TextPrimary, fontSize = 14.sp)

                Spacer(Modifier.height(16.dp))
                Text(text = "Luminosite", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                ) {
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { 
                            brightness = it.roundToInt()
                            onBrightnessChange(brightness)
                            lastActionAt = System.currentTimeMillis()
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentBlue,
                            activeTrackColor = AccentBlue,
                            inactiveTrackColor = SurfaceVariantDark
                        )
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(text = "$brightness%", color = TextPrimary, fontSize = 18.sp, modifier = Modifier.width(45.dp))
                }

                if (isColorLight) {
                    Spacer(Modifier.height(20.dp))
                    Text(text = "Couleur", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        COLOR_PRESETS.forEach { hex ->
                            val isSelected = hex.equals(currentColorHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable {
                                        onColorChange(hex)
                                        lastActionAt = System.currentTimeMillis()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selectionne",
                                        tint = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Fermer", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}
