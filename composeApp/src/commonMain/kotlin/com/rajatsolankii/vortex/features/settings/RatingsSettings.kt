package com.rajatsolankii.vortex.features.settings

import androidx.compose.runtime.Composable
import com.rajatsolankii.vortex.core.ui.Chip
import com.rajatsolankii.vortex.features.details.EpisodeRatingsVisibility
import com.rajatsolankii.vortex.features.details.MetaScreenSettingsRepository
import com.rajatsolankii.vortex.features.details.MetaScreenSettingsUiState
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.layout_episode_ratings
import com.rajatsolankii.vortex.generated.resources.layout_episode_ratings_sub
import com.rajatsolankii.vortex.generated.resources.layout_overall_ratings
import com.rajatsolankii.vortex.generated.resources.layout_overall_ratings_sub_off
import com.rajatsolankii.vortex.generated.resources.layout_overall_ratings_sub_on
import com.rajatsolankii.vortex.generated.resources.layout_ratings_hide
import com.rajatsolankii.vortex.generated.resources.layout_ratings_hide_unwatched
import com.rajatsolankii.vortex.generated.resources.layout_ratings_show
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RatingsSettings(
    isTablet: Boolean,
    uiState: MetaScreenSettingsUiState,
) {
    SettingsSwitchRow(
        title = stringResource(Res.string.layout_overall_ratings),
        description = stringResource(
            if (uiState.showOverallRatings) Res.string.layout_overall_ratings_sub_on
            else Res.string.layout_overall_ratings_sub_off,
        ),
        checked = uiState.showOverallRatings,
        isTablet = isTablet,
        onCheckedChange = MetaScreenSettingsRepository::setShowOverallRatings,
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsChipRow(
        title = stringResource(Res.string.layout_episode_ratings),
        description = stringResource(Res.string.layout_episode_ratings_sub),
        isTablet = isTablet,
    ) {
        EpisodeRatingsVisibility.entries.forEach { visibility ->
            Chip(
                label = stringResource(
                    when (visibility) {
                        EpisodeRatingsVisibility.SHOW_ALL -> Res.string.layout_ratings_show
                        EpisodeRatingsVisibility.HIDE_EPISODES -> Res.string.layout_ratings_hide
                        EpisodeRatingsVisibility.HIDE_UNWATCHED_EPISODES -> Res.string.layout_ratings_hide_unwatched
                    },
                ),
                selected = uiState.episodeRatingsVisibility == visibility,
                onClick = { MetaScreenSettingsRepository.setEpisodeRatingsVisibility(visibility) },
            )
        }
    }
}
