package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.ui.theme.*

@Composable
fun WeatherDetailsModal(
    label: String,
    state: WidgetLiveState.Weather,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(32.dp))
                
                // Température et Condition
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = iconForWeatherCode(state.weatherCode),
                        contentDescription = null,
                        tint = colorForWeatherCode(state.weatherCode),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(
                            text = "${state.temperature}°",
                            color = TextPrimary,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = state.condition,
                            color = TextPrimary, // Plus contrasté
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Text(
                    text = "Min: ${state.min}° · Max: ${state.max}°",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Spacer(Modifier.height(32.dp))
                HorizontalDivider(color = SurfaceVariantDark, thickness = 1.dp)
                Spacer(Modifier.height(32.dp))

                // Grille de détails
                Row(modifier = Modifier.fillMaxWidth()) {
                    WeatherDetailItem(
                        icon = Icons.Filled.WaterDrop,
                        label = "Humidité",
                        value = "${state.humidity ?: "--"}%",
                        modifier = Modifier.weight(1f)
                    )
                    WeatherDetailItem(
                        icon = Icons.Filled.Air,
                        label = "Vent",
                        value = "${state.windSpeed ?: "--"} km/h",
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    WeatherDetailItem(
                        icon = Icons.Filled.WbSunny,
                        label = "Lever",
                        value = state.sunrise ?: "--:--",
                        modifier = Modifier.weight(1f)
                    )
                    WeatherDetailItem(
                        icon = Icons.Filled.WbTwilight,
                        label = "Coucher",
                        value = state.sunset ?: "--:--",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(40.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 40.dp, vertical = 14.dp)
                ) {
                    Text("Fermer", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WeatherDetailItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(text = label, color = TextSecondary, fontSize = 12.sp) // TextSecondary au lieu de Muted
        Text(text = value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
