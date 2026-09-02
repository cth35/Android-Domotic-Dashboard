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
                
                Spacer(Modifier.height(12.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.days) { day ->
                        ForecastDayRow(day)
                        if (day != state.days.last()) {
                            HorizontalDivider(
                                color = SurfaceVariantDark,
                                modifier = Modifier.padding(top = 8.dp),
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
        // Line 1: Principal Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = day.dayLabel,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(60.dp)
            )
            
            WeatherIcon(
                code = day.weatherCode,
                size = 32.dp
            )
            
            Spacer(Modifier.width(10.dp))
            
            Text(
                text = day.condition,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${day.tempMax}°",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = " / ",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${day.tempMin}°",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Line 2: Details (Sunrise, Sunset, Rain, Wind)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DetailItem(Icons.Filled.WbSunny, day.sunrise ?: "--:--", AccentOrange)
            DetailItem(Icons.Filled.WbTwilight, day.sunset ?: "--:--", AccentOrange)
            DetailItem(Icons.Filled.WaterDrop, "${day.precipProb ?: 0}%", AccentBlueMuted)
            DetailItem(Icons.Filled.Air, "${day.windSpeed?.roundToInt() ?: "--"} km/h", TextSecondary)
        }

        // Line 3: Periods (Morning, Afternoon, Evening)
        if (day.periods.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                day.periods.forEach { period ->
                    PeriodItem(period)
                }
            }
        }
    }
}

@Composable
private fun PeriodItem(period: com.homehabit.app.data.ForecastPeriod) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = period.label.uppercase(),
            color = TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 1.dp)
        ) {
            WeatherIcon(code = period.weatherCode, size = 18.dp)
            Spacer(Modifier.width(3.dp))
            Text(
                text = "${period.temp}°",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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
