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
import androidx.compose.ui.text.font.FontWeight
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
    onDismiss: () -> Unit
) {
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
                
                Spacer(Modifier.height(20.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.days) { day ->
                        ForecastDayRow(day)
                        if (day != state.days.last()) {
                            HorizontalDivider(
                                color = SurfaceVariantDark,
                                modifier = Modifier.padding(top = 12.dp),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

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
private fun ForecastDayRow(day: ForecastDay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.dayLabel,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(60.dp)
        )
        
        WeatherIcon(
            code = day.weatherCode,
            size = 32.dp
        )
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.condition,
                color = TextPrimary, // Brighter
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Filled.WbSunny, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Text(text = " ${day.sunrise ?: "--:--"}", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.WbTwilight, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Text(text = " ${day.sunset ?: "--:--"}", color = TextSecondary, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Filled.WaterDrop, null, tint = AccentBlueMuted, modifier = Modifier.size(14.dp))
                Text(text = " ${day.precipProb ?: 0}%", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.Air, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Text(text = " ${day.windSpeed?.roundToInt() ?: "--"} km/h", color = TextSecondary, fontSize = 12.sp)
            }
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${day.tempMax}°",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${day.tempMin}°",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
