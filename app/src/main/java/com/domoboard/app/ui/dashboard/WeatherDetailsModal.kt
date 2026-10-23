package com.domoboard.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.domoboard.app.data.WidgetLiveState
import com.domoboard.app.ui.theme.*

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
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
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
                
                // Temperature and Condition
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(
                        code = state.weatherCode,
                        size = 64.dp
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
                            color = TextPrimary,
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

                // Detail grid
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

                if (state.vigilanceLevel != null) {
                    Spacer(Modifier.height(32.dp))
                    HorizontalDivider(color = SurfaceVariantDark, thickness = 1.dp)
                    Spacer(Modifier.height(24.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = when (state.vigilanceLevel) {
                                1 -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
                                2 -> androidx.compose.ui.graphics.Color(0xFFFFEB3B) // Yellow
                                3 -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
                                4 -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
                                else -> TextSecondary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Vigilance Météo-France : ${state.vigilanceColorName}",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (state.vigilancePhenomenons.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.vigilancePhenomenons.joinToString(" · ") { 
                                "${it.first} (${com.domoboard.app.data.weather.VigilanceMapper.colorName(it.second)})" 
                            },
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (state.vigilanceDetails.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark.copy(alpha = 0.5f))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            state.vigilanceDetails.forEach { detail ->
                                val startFormatted = com.domoboard.app.data.weather.formatVigilanceTime(detail.startTime)
                                val endFormatted = com.domoboard.app.data.weather.formatVigilanceTime(detail.endTime)

                                if (!detail.boldText.isNullOrBlank()) {
                                    Text(
                                        text = detail.boldText,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                    )
                                }
                                if (startFormatted != null && endFormatted != null) {
                                    Text(
                                        text = "Valide du $startFormatted au $endFormatted",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                detail.textLines.forEach { line ->
                                    if (line.isNotBlank()) {
                                        Text(
                                            text = line,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 40.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
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
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
