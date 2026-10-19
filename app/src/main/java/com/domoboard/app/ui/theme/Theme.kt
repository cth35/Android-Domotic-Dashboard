package com.domoboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DomoBoardDarkColors = darkColorScheme(
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
fun DomoBoardTheme(
    // Dark mode is the default and currently the only supported mode.
    // isSystemInDarkTheme() remains called to prepare for a future light mode.
    useSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = if (useSystemTheme) isSystemInDarkTheme() else true

    MaterialTheme(
        colorScheme = DomoBoardDarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
