package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val isDark: Boolean = true
)

fun resolveVortexPalette(
    themeMode: String = "Dark",
    accentColorName: String = "Cyber Purple",
    systemInDark: Boolean = true
): VortexPalette {
    val effectiveMode = when (themeMode) {
        "AMOLED" -> "AMOLED"
        "Light" -> "Light"
        "Dark" -> "Dark"
        "System" -> if (systemInDark) "Dark" else "Light"
        else -> "Dark"
    }

    val (primary, primaryDeep, primaryLight, primaryGlow) = when (accentColorName) {
        "Vortex Cyan" -> QuadColor(
            VortexCyan,
            VortexCyanDeep,
            VortexCyanLight,
            VortexCyanGlow
        )
        "Emerald Green" -> QuadColor(
            EmeraldGreen,
            EmeraldGreenDeep,
            EmeraldGreenLight,
            EmeraldGreenGlow
        )
        "Neon Orange" -> QuadColor(
            NeonOrange,
            NeonOrangeDeep,
            NeonOrangeLight,
            NeonOrangeGlow
        )
        "Rose Pink" -> QuadColor(
            RosePink,
            RosePinkDeep,
            RosePinkLight,
            RosePinkGlow
        )
        "Pixel Blue" -> QuadColor(
            PixelBlue,
            PixelBlueDeep,
            PixelBlueLight,
            PixelBlueGlow
        )
        else -> QuadColor( // Default "Cyber Purple"
            VortexPurple,
            VortexPurpleDeep,
            VortexPurpleLight,
            VortexPurpleGlow
        )
    }

    return when (effectiveMode) {
        "AMOLED" -> VortexPalette(
            primary = primary,
            primaryDeep = primaryDeep,
            primaryLight = primaryLight,
            primaryGlow = primaryGlow,
            background = VortexTrueBlack,
            surface = VortexAmoledSurface,
            surfaceVariant = VortexAmoledSurfaceVariant,
            card = VortexAmoledCard,
            cardBorder = VortexAmoledCardBorder,
            textPrimary = VortexTextPrimary,
            textSecondary = VortexTextSecondary,
            textMuted = VortexTextMuted,
            isDark = true
        )
        "Light" -> VortexPalette(
            primary = primary,
            primaryDeep = primaryDeep,
            primaryLight = primaryLight,
            primaryGlow = primaryGlow,
            background = VortexLightBackground,
            surface = VortexLightSurface,
            surfaceVariant = VortexLightSurfaceVariant,
            card = VortexLightCard,
            cardBorder = VortexLightCardBorder,
            textPrimary = VortexLightTextPrimary,
            textSecondary = VortexLightTextSecondary,
            textMuted = VortexLightTextMuted,
            isDark = false
        )
        else -> VortexPalette( // Cinematic Dark
            primary = primary,
            primaryDeep = primaryDeep,
            primaryLight = primaryLight,
            primaryGlow = primaryGlow,
            background = VortexDarkBackground,
            surface = VortexDarkSurface,
            surfaceVariant = VortexDarkSurfaceVariant,
            card = VortexDarkCard,
            cardBorder = VortexDarkCardBorder,
            textPrimary = VortexTextPrimary,
            textSecondary = VortexTextSecondary,
            textMuted = VortexTextMuted,
            isDark = true
        )
    }
}

private data class QuadColor(val c1: Color, val c2: Color, val c3: Color, val c4: Color)

val LocalVortexColors = staticCompositionLocalOf {
    resolveVortexPalette(themeMode = "Dark", accentColorName = "Cyber Purple")
}

@Composable
fun VortexTheme(
    isAmoled: Boolean = false,
    themeMode: String = if (isAmoled) "AMOLED" else "Dark",
    accentColor: String = "Cyber Purple",
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val effectiveMode = when (themeMode) {
        "AMOLED" -> "AMOLED"
        "Light" -> "Light"
        "Dark" -> "Dark"
        "System" -> if (systemInDark) "Dark" else "Light"
        else -> if (isAmoled) "AMOLED" else "Dark"
    }

    val isDark = effectiveMode != "Light"
    val palette = resolveVortexPalette(
        themeMode = effectiveMode,
        accentColorName = accentColor,
        systemInDark = systemInDark
    )

    val colorScheme: ColorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (isDark) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primaryLight.copy(alpha = 0.25f),
            onPrimaryContainer = palette.primaryDeep,
            secondary = palette.primaryDeep,
            onSecondary = Color.White,
            secondaryContainer = palette.surfaceVariant,
            onSecondaryContainer = palette.primaryDeep,
            tertiary = VortexCyanDeep,
            onTertiary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.cardBorder
        )
    }

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
    VortexTheme(
        themeMode = if (darkTheme) "Dark" else "Light",
        useDynamicColor = dynamicColor,
        content = content
    )
}
