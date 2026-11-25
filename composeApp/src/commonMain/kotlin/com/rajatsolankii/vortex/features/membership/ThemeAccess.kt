package com.rajatsolankii.vortex.features.membership

import com.rajatsolankii.vortex.core.ui.AppTheme
import com.rajatsolankii.vortex.core.ui.CustomThemeColors

private val supporterThemes = linkedMapOf(
    AppTheme.GOLD to CosmeticEntitlement.GOLD_THEME,
    AppTheme.JADE to CosmeticEntitlement.JADE_THEME,
    AppTheme.ROSE_GOLD to CosmeticEntitlement.ROSE_GOLD_THEME,
    AppTheme.ARCTIC_BLUE to CosmeticEntitlement.ARCTIC_BLUE_THEME,
    AppTheme.GRAPHITE to CosmeticEntitlement.GRAPHITE_THEME,
)

/** Default app theme, used until the user picks another one. */
val DefaultAppTheme: AppTheme = AppTheme.WHITE

private val standardThemes = listOf(DefaultAppTheme) + AppTheme.entries.filterNot {
    it == DefaultAppTheme || it == AppTheme.CUSTOM || it in supporterThemes
}

fun availableAppThemes(entitlements: CosmeticEntitlements): List<AppTheme> {
    val supporter = supporterThemes
        .filterValues(entitlements::includes)
        .keys
        .toList()
    return supporter + AppTheme.CUSTOM + standardThemes
}

fun resolveAppTheme(
    selectedTheme: AppTheme?,
    entitlements: CosmeticEntitlements,
): AppTheme {
    if (selectedTheme == null) {
        return supporterThemes
            .filterValues(entitlements::includes)
            .keys
            .firstOrNull()
            ?: DefaultAppTheme
    }
    return selectedTheme.takeIf { it in availableAppThemes(entitlements) } ?: DefaultAppTheme
}

fun resolveCustomThemeColors(colors: CustomThemeColors, memberTier: MemberTier?): CustomThemeColors =
    if (memberTier == null) CustomThemeColors.solid(colors.second) else colors
