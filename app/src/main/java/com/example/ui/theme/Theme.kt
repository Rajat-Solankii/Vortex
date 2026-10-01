package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VortexColorScheme = darkColorScheme(
    primary = VortexPurple,
    onPrimary = Color.White,
    primaryContainer = VortexPurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = VortexPurpleLight,
    onSecondary = VortexTrueBlack,
    secondaryContainer = VortexDarkSurfaceVariant,
    onSecondaryContainer = VortexPurpleLight,
    tertiary = VortexCyan,
    onTertiary = VortexTrueBlack,
    background = VortexDarkBackground,
    onBackground = VortexTextPrimary,
    surface = VortexDarkSurface,
    onSurface = VortexTextPrimary,
    surfaceVariant = VortexDarkSurfaceVariant,
    onSurfaceVariant = VortexTextSecondary,
    outline = VortexDarkCardBorder
)

@Composable
fun VortexTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VortexColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    VortexTheme(content = content)
}

