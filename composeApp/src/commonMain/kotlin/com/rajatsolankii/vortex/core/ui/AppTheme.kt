package com.rajatsolankii.vortex.core.ui

import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.theme_amber
import com.rajatsolankii.vortex.generated.resources.theme_crimson
import com.rajatsolankii.vortex.generated.resources.theme_emerald
import com.rajatsolankii.vortex.generated.resources.theme_ocean
import com.rajatsolankii.vortex.generated.resources.theme_rose
import com.rajatsolankii.vortex.generated.resources.theme_violet
import com.rajatsolankii.vortex.generated.resources.theme_white
import com.rajatsolankii.vortex.generated.resources.theme_gold
import com.rajatsolankii.vortex.generated.resources.theme_jade
import com.rajatsolankii.vortex.generated.resources.theme_rose_gold
import com.rajatsolankii.vortex.generated.resources.theme_arctic_blue
import com.rajatsolankii.vortex.generated.resources.theme_graphite
import com.rajatsolankii.vortex.generated.resources.theme_custom
import org.jetbrains.compose.resources.StringResource

enum class AppTheme {
    GOLD,
    JADE,
    ROSE_GOLD,
    ARCTIC_BLUE,
    GRAPHITE,
    CUSTOM,
    CRIMSON,
    OCEAN,
    VIOLET,
    EMERALD,
    AMBER,
    ROSE,
    WHITE,
}

val AppTheme.labelRes: StringResource
    get() = when (this) {
        AppTheme.GOLD -> Res.string.theme_gold
        AppTheme.JADE -> Res.string.theme_jade
        AppTheme.ROSE_GOLD -> Res.string.theme_rose_gold
        AppTheme.ARCTIC_BLUE -> Res.string.theme_arctic_blue
        AppTheme.GRAPHITE -> Res.string.theme_graphite
        AppTheme.CUSTOM -> Res.string.theme_custom
        AppTheme.CRIMSON -> Res.string.theme_crimson
        AppTheme.OCEAN -> Res.string.theme_ocean
        AppTheme.VIOLET -> Res.string.theme_violet
        AppTheme.EMERALD -> Res.string.theme_emerald
        AppTheme.AMBER -> Res.string.theme_amber
        AppTheme.ROSE -> Res.string.theme_rose
        AppTheme.WHITE -> Res.string.theme_white
    }
