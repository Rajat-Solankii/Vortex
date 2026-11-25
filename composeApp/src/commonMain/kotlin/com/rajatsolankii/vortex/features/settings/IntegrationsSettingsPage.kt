package com.rajatsolankii.vortex.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.compose_settings_page_mdblist_ratings
import com.rajatsolankii.vortex.generated.resources.settings_integrations_mdblist_description
import com.rajatsolankii.vortex.generated.resources.settings_integrations_section_title
import org.jetbrains.compose.resources.stringResource

// TMDB data comes from the Vortex API and debrid sources are not used, so only MDBList is configurable.
@Suppress("UNUSED_PARAMETER")
internal fun LazyListScope.integrationsContent(
    isTablet: Boolean,
    onTmdbClick: () -> Unit,
    onMdbListClick: () -> Unit,
    onDebridClick: () -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_mdblist_ratings),
                    description = stringResource(Res.string.settings_integrations_mdblist_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.MdbList),
                    isTablet = isTablet,
                    onClick = onMdbListClick,
                )
            }
        }
    }
}
