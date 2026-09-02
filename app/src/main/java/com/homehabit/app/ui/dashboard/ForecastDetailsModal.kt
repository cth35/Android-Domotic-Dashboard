package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.homehabit.app.data.ForecastDay
import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ForecastDetailsModal(
    label: String,
    state: WidgetLiveState.Forecast,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .fillMaxWidth(0.95f)
                .heightIn(max = 850.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Column Headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 110.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Matin", "Après-midi", "Soirée").forEach { 
                        Text(
                            text = it,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(90.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.days) { day ->
                        ForecastDayRow(day)
                        if (day != state.days.last()) {
                            HorizontalDivider(
                                color = SurfaceVariantDark,
                                modifier = Modifier.padding(top = 12.dp),
                                thickness = 1.dp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariantDark)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 40.dp, vertical = 10.dp)
                ) {
                    Text("FERMER", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun ForecastDayRow(day: ForecastDay) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // Line 1: Main Info (mock-style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Day, Condition, Min/Max
            Column(modifier = Modifier.width(90.dp)) {
                Text(
                    text = day.dayLabel,
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = day.condition,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${day.tempMax}° / ${day.tempMin}°",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Vertical Divider
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(1.dp)
                    .height(70.dp)
                    .background(SurfaceVariantDark)
            )

            // Matin / Après-midi / Soirée Columns
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                day.periods.forEach { period ->
                    Row(
                        modifier = Modifier.width(90.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WeatherIcon(
                                code = period.weatherCode,
                                size = 34.dp
                            )
                            Text(
                                text = "${period.temp}°",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            // Secondary info: Rain and Wind
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.WaterDrop, null, tint = AccentBlueMuted, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "${period.precipProb ?: 0}%",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Air, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                                Text(
                                    text = period.windSpeed?.roundToInt()?.toString() ?: "--",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(10.dp))
        
        // Line 2: Details (Sunrise, Sunset, Rain, Wind)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailItem(Icons.Filled.WbSunny, day.sunrise ?: "--:--", AccentOrange)
            DetailItem(Icons.Filled.WbTwilight, day.sunset ?: "--:--", AccentOrange)
            DetailItem(Icons.Filled.WaterDrop, "${day.precipProb ?: 0}%", AccentBlueMuted)
            DetailItem(Icons.Filled.Air, "${day.windSpeed?.roundToInt() ?: "--"} km/h", TextSecondary)
        }
    }
}

@Composable
private fun DetailItem(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
