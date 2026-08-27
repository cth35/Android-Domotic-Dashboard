package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.homehabit.app.ui.theme.HomeHabitTheme
import com.homehabit.app.ui.theme.TextSecondary

@Composable
fun WeatherIcon(
    code: Int?,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assetPath = weatherAssetForCode(code)
    
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .logger(coil.util.DebugLogger())
            .build()
    }

    if (assetPath != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/$assetPath")
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            placeholder = rememberVectorPainter(Icons.Filled.Cloud),
            error = rememberVectorPainter(Icons.Filled.Warning),
            modifier = modifier.size(size)
        )
    } else {
        // Fallback to Material Icon if no asset found
        Icon(
            imageVector = Icons.Filled.Cloud,
            contentDescription = null,
            tint = TextSecondary,
            modifier = modifier.size(size)
        )
    }
}

fun weatherAssetForCode(code: Int?): String? = when (code) {
    0 -> "weather/clear_day.svg"
    1 -> "weather/mostly_clear_day.svg"
    2 -> "weather/partly_cloudy_day.svg"
    3 -> "weather/cloudy.svg"
    45, 48 -> "weather/haze_fog_dust_smoke.svg"
    51, 53, 55, 56, 57 -> "weather/drizzle.svg"
    61, 63, 65, 66, 67 -> "weather/heavy_rain.svg"
    80, 81, 82 -> "weather/showers_rain.svg"
    71, 73, 75, 77, 85, 86 -> "weather/heavy_snow.svg"
    95, 96, 99 -> "weather/strong_thunderstorms.svg"
    else -> null
}

@Preview
@Composable
fun WeatherIconPreview() {
    HomeHabitTheme {
        WeatherIcon(code = 3, size = 64.dp)
    }
}
