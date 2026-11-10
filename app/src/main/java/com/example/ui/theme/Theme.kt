package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VortexPalette(
    val primary: Color,
    val primaryDeep: Color,
    val primaryLight: Color,
    val primaryGlow: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color = VortexTextPrimary,
    val textSecondary: Color = VortexTextSecondary,
    val textMuted: Color = VortexTextMuted
)

fun resolveVortexPalette(
    isAmoled: Boolean,
    accentColorName: String
): VortexPalette {
    val (primary, primaryDeep, primaryLight, primaryGlow) = when (accentColorName) {
        "Vortex Cyan" -> QuadColor(
            Color(0xFF00E5FF),
            Color(0xFF00B0FF),
            Color(0xFF80D8FF),
            Color(0x6600E5FF)
        )
        "Emerald Green" -> QuadColor(
            Color(0xFF00E676),
            Color(0xFF00C853),
            Color(0xFF69F0AE),
            Color(0x6600E676)
        )
        "Neon Orange" -> QuadColor(
            Color(0xFFFF9100),
            Color(0xFFFF6D00),
            Color(0xFFFFD180),
            Color(0x66FF9100)
        )
        "Rose Pink" -> QuadColor(
            Color(0xFFFF4081),
            Color(0xFFF50057),
            Color(0xFFFF80AB),
            Color(0x66FF4081)
        )
        else -> QuadColor( // Default "Cyber Purple"
            VortexPurple,
            VortexPurpleDeep,
            VortexPurpleLight,
            VortexPurpleGlow
        )
    }

    val (bg, surface, surfaceVar, card, cardBorder) = if (isAmoled) {
        QuintColor(
            Color(0xFF000000), // Pure Pitch Black for AMOLED
            Color(0xFF08080C),
            Color(0xFF101018),
            Color(0xFF0C0C14),
            Color(0xFF1F1E2E)
        )
    } else {
        QuintColor(
            VortexDarkBackground, // Atmospheric Dark #0A0A0F
            VortexDarkSurface,
            VortexDarkSurfaceVariant,
            VortexDarkCard,
            VortexDarkCardBorder
        )
    }

    return VortexPalette(
        primary = primary,
        primaryDeep = primaryDeep,
        primaryLight = primaryLight,
        primaryGlow = primaryGlow,
        background = bg,
        surface = surface,
        surfaceVariant = surfaceVar,
        card = card,
        cardBorder = cardBorder
    )
}

private data class QuadColor(val c1: Color, val c2: Color, val c3: Color, val c4: Color)
private data class QuintColor(val c1: Color, val c2: Color, val c3: Color, val c4: Color, val c5: Color)

val LocalVortexColors = staticCompositionLocalOf {
    resolveVortexPalette(isAmoled = false, accentColorName = "Cyber Purple")
}

@Composable
fun VortexTheme(
    isAmoled: Boolean = false,
    accentColor: String = "Cyber Purple",
    content: @Composable () -> Unit
) {
    val palette = resolveVortexPalette(isAmoled = isAmoled, accentColorName = accentColor)

    val colorScheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = Color.White,
        primaryContainer = palette.primaryDeep,
        onPrimaryContainer = Color.White,
        secondary = palette.primaryLight,
        onSecondary = VortexTrueBlack,
        secondaryContainer = palette.surfaceVariant,
        onSecondaryContainer = palette.primaryLight,
        tertiary = VortexCyan,
        onTertiary = VortexTrueBlack,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.cardBorder
    )

    CompositionLocalProvider(LocalVortexColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
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
