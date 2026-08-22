package com.homehabit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HomeHabitDarkColors = darkColorScheme(
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    primary = AccentBlue,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    secondary = AccentGreen,
    error = AccentRed
)

@Composable
fun HomeHabitTheme(
    // Dark mode is the default and currently the only supported mode.
    // isSystemInDarkTheme() remains called to prepare for a future light mode.
    useSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = if (useSystemTheme) isSystemInDarkTheme() else true

    MaterialTheme(
        colorScheme = HomeHabitDarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
