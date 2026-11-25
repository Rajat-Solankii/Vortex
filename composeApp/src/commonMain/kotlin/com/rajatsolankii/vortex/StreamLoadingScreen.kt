package com.rajatsolankii.vortex

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.rajatsolankii.vortex.features.player.OpeningOverlay
import com.rajatsolankii.vortex.features.player.subtitleLoadingStatusMessage
import com.rajatsolankii.vortex.features.streams.StreamLaunch
import com.rajatsolankii.vortex.features.streams.StreamsUiState
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.debrid_resolving_stream
import com.rajatsolankii.vortex.generated.resources.player_loading_preparing
import com.rajatsolankii.vortex.generated.resources.streams_finding_source
import com.rajatsolankii.vortex.generated.resources.streams_loading_subtitles
import com.rajatsolankii.vortex.generated.resources.youtube_resolving_stream
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StreamLoadingScreen(
    launch: StreamLaunch,
    state: StreamsUiState,
    showStatus: Boolean,
    resolvingDebridStream: Boolean,
    resolvingYouTubeStream: Boolean = false,
    onBack: () -> Unit,
    preparingPlayback: Boolean = false,
) {
    val message = when {
        !showStatus -> null
        resolvingDebridStream -> stringResource(Res.string.debrid_resolving_stream)
        resolvingYouTubeStream -> stringResource(Res.string.youtube_resolving_stream)
        preparingPlayback -> stringResource(Res.string.player_loading_preparing)
        state.overlayMessage == stringResource(Res.string.streams_loading_subtitles) -> subtitleLoadingStatusMessage()
        state.overlayMessage != null -> state.overlayMessage
        state.autoPlayStream != null -> stringResource(Res.string.player_loading_preparing)
        else -> stringResource(Res.string.streams_finding_source)
    }
    OpeningOverlay(
        artwork = launch.background ?: launch.poster,
        logo = launch.logo,
        title = launch.title,
        onBack = onBack,
        horizontalSafePadding = 0.dp,
        message = message,
    )
}
