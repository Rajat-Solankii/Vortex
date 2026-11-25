package com.rajatsolankii.vortex.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import com.rajatsolankii.vortex.core.ui.NuvioStatusModal
import com.rajatsolankii.vortex.core.ui.nuvio
import com.rajatsolankii.vortex.features.details.MetaDetailsRepository
import com.rajatsolankii.vortex.core.ui.LocalFold
import com.rajatsolankii.vortex.features.p2p.P2pStreamingState
import com.rajatsolankii.vortex.features.party.PartyRepository
import com.rajatsolankii.vortex.features.p2p.formatP2pMegabytes
import com.rajatsolankii.vortex.features.p2p.formatP2pSpeed
import com.rajatsolankii.vortex.features.player.skip.internalSkipAction
import com.rajatsolankii.vortex.features.servers.ServerStreams
import com.rajatsolankii.vortex.features.streams.AddonStreamGroup
import com.rajatsolankii.vortex.features.streams.StreamItem
import com.rajatsolankii.vortex.features.streams.StreamsUiState
import com.rajatsolankii.vortex.features.streams.streamAddonInstanceId
import com.rajatsolankii.vortex.features.vortex.AnilumosAnimeBackend
import com.rajatsolankii.vortex.features.vortex.VortexBuiltinAddon
import com.rajatsolankii.vortex.features.vortex.VortexEmbedWebView
import com.rajatsolankii.vortex.features.vortex.VortexMediaExtractor
import com.rajatsolankii.vortex.features.vortex.VortexServers
import com.rajatsolankii.vortex.isIos
import kotlinx.coroutines.launch
import com.rajatsolankii.vortex.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerScreenRuntime.RenderPlayerRuntimeUi(
    partyPanelWidth: Dp,
    partyPanelInset: Dp,
    dockPartyPanel: Boolean,
    isPortraitViewport: Boolean = false,
) {
    val runtime = this
    val isInPip = rememberIsInPictureInPicture()
    val displayedPositionMs = scrubbingPositionMs ?: playbackSnapshot.positionMs
    val isEpisode = activeSeasonNumber != null && activeEpisodeNumber != null
    val currentGestureFeedback = liveGestureFeedback ?: gestureFeedback
    val isP2pPlaybackActive = activeTorrentInfoHash != null
    val allServers = rememberMergedAllServers()
    val mergedSourceStreamsState = remember(sourceStreamsState, allServers) {
        mergeInstantStreamsIntoUiState(sourceStreamsState, allServers)
    }
    val p2pConnecting = p2pStreamingState as? P2pStreamingState.Connecting
    val p2pStats = p2pStreamingState as? P2pStreamingState.Streaming
    val p2pPeerInfo = p2pStats?.let { stats ->
        org.jetbrains.compose.resources.stringResource(
            com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_peer_info,
            stats.seeds,
            stats.peers,
        )
    }
    val p2pDownloadSpeed = p2pStats?.let { formatP2pSpeed(it.downloadSpeed) }
    val p2pLoadingBytes = p2pStats?.let { maxOf(it.downloadedBytes, it.deliveredBytes) } ?: 0L
    val connectingPeerInfo = p2pConnecting?.let { state ->
        org.jetbrains.compose.resources.stringResource(
            com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_peer_info,
            state.seeds,
            state.peers,
        )
    }
    val p2pInitialLoadingMessage = when {
        !isP2pPlaybackActive || initialLoadCompleted -> null
        p2pConnecting != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                p2pConnectingPhaseLabel(p2pConnecting.phase)
            } else {
                org.jetbrains.compose.resources.stringResource(
                    com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_connecting_status,
                    p2pConnectingPhaseLabel(p2pConnecting.phase),
                    connectingPeerInfo.orEmpty(),
                    formatP2pSpeed(p2pConnecting.downloadSpeed),
                )
            }
        }
        p2pStats != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                null
            } else {
                org.jetbrains.compose.resources.stringResource(
                    com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_loading_status,
                    formatP2pMegabytes(p2pLoadingBytes),
                    p2pPeerInfo.orEmpty(),
                    p2pDownloadSpeed.orEmpty(),
                )
            }
        }
        else -> org.jetbrains.compose.resources.stringResource(
            com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_starting_engine,
        )
    }
    val bufferedAheadMs = (playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs)
        .coerceAtLeast(0L)
    val p2pInitialLoadingProgress = when {
        !isP2pPlaybackActive || initialLoadCompleted || p2pStats == null -> null
        else -> p2pInitialLoadingProgress(
            bufferedAheadMs = bufferedAheadMs,
            downloadedBytes = p2pStats.downloadedBytes,
            deliveredBytes = p2pStats.deliveredBytes,
        )
    }
    val showP2pRebufferStats = isP2pPlaybackActive &&
        initialLoadCompleted &&
        playbackSnapshot.isLoading &&
        p2pStats != null &&
        !p2pSettingsUiState.hideTorrentStats
    val p2pRebufferMessage = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000L)
                .coerceAtLeast(0L)
            "${bufferedSeconds}s buffered · ${p2pPeerInfo.orEmpty()} · ${p2pDownloadSpeed.orEmpty()}"
        }
    }
    val p2pRebufferProgress = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000f)
                .coerceAtLeast(0f)
            (bufferedSeconds / 10f).coerceIn(0f, 1f)
        }
    }
    val gestureCallbacks = rememberSurfaceGestureCallbacks()
    val fold = LocalFold.current?.takeIf { !isInPip }
    val baseVideoModifier = if (fold != null) Modifier.fillMaxWidth().height(fold.top) else Modifier.fillMaxSize()
    val videoModifier = if (partyPanelInset == 0.dp && (videoZoomScale > 1f || videoPanOffset != Offset.Zero)) {
        baseVideoModifier.graphicsLayer {
            scaleX = videoZoomScale
            scaleY = videoZoomScale
            translationX = videoPanOffset.x
            translationY = videoPanOffset.y
        }
    } else {
        baseVideoModifier
    }
    val zoomScaleState = rememberUpdatedState(videoZoomScale)
    val panOffsetState = rememberUpdatedState(videoPanOffset)
    val onPinchTransformState = rememberUpdatedState<(Float, Offset) -> Unit> { nextScale, nextPan ->
        videoZoomScale = nextScale
        videoPanOffset = nextPan
        showGestureMessage("${(nextScale * 100f).roundToInt()}%")
    }
    val onPinchEndedState = rememberUpdatedState<(Float) -> Unit> { finalScale ->
        if (finalScale <= 1f) {
            showGestureMessage("100%")
        }
    }
    val panelModifier = if (fold != null) Modifier.fillMaxSize().padding(top = fold.bottom) else Modifier.fillMaxSize()
    val playbackGesturesEnabled = initialLoadCompleted && errorMessage == null && webFallbackEmbedUrl == null
    val keyFocusRequester = remember { FocusRequester() }
    LaunchedEffect(keyFocusRequester) {
        keyFocusRequester.requestFocus()
    }

    BindAutoSyncRuntimeEffects() // AutoSync hook

    val viewportContainerModifier = if (isPortraitViewport) {
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .statusBarsPadding()
            .aspectRatio(16f / 9f)
    } else {
        Modifier
            .fillMaxSize()
            .padding(end = partyPanelInset)
    }

    val renderVideoBox: @Composable (Modifier) -> Unit = { containerModifier ->
        Box(
            modifier = containerModifier
                .clipToBounds()
                .onSizeChanged { layoutSize = it }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || playerControlsLocked) return@onKeyEvent false
                    when {
                        event.key == Key.Spacebar -> togglePlayback()
                        event.key == Key.DirectionLeft && playbackGesturesEnabled -> seekBy(-10_000L)
                        event.key == Key.DirectionRight && playbackGesturesEnabled -> seekBy(10_000L)
                        event.key == Key.DirectionUp -> changeVolumeBy(PlayerKeyVolumeStep)
                        event.key == Key.DirectionDown -> changeVolumeBy(-PlayerKeyVolumeStep)
                        else -> return@onKeyEvent false
                    }
                    true
                }
                .focusRequester(keyFocusRequester)
                .focusable()
                .playerSurfaceTapGestures(
                    layoutSize = layoutSize,
                    playbackGesturesEnabled = playbackGesturesEnabled,
                    playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                    onSurfaceTap = gestureCallbacks.onSurfaceTap,
                    onSurfaceDoubleTap = gestureCallbacks.onSurfaceDoubleTap,
                    activateHoldToSpeedState = gestureCallbacks.activateHoldToSpeed,
                    deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                    revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
                )
                .playerSurfacePinchToZoomGesture(
                    layoutSize = layoutSize,
                    playbackGesturesEnabled = playbackGesturesEnabled && partyPanelInset == 0.dp,
                    playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                    zoomScaleState = zoomScaleState,
                    panOffsetState = panOffsetState,
                    onTransformChangedState = onPinchTransformState,
                    onPinchGestureEndedState = onPinchEndedState,
                )
                .playerSurfaceDragGestures(
                    gestureController = gestureController,
                    layoutSize = layoutSize,
                    playbackGesturesEnabled = playbackGesturesEnabled,
                    sideGestureSystemEdgeExclusionPx = sideGestureSystemEdgeExclusionPx,
                    horizontalSeekSystemEdgeExclusionPx = horizontalSeekSystemEdgeExclusionPx,
                    playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                    touchGesturesEnabledState = gestureCallbacks.touchGesturesEnabled,
                    isHoldToSpeedGestureActiveState = gestureCallbacks.isHoldToSpeedGestureActive,
                    currentPositionMsState = gestureCallbacks.currentPositionMs,
                    currentDurationMsState = gestureCallbacks.currentDurationMs,
                    deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                    showHorizontalSeekPreviewState = gestureCallbacks.showHorizontalSeekPreview,
                    showBrightnessFeedbackState = gestureCallbacks.showBrightnessFeedback,
                    showVolumeFeedbackState = gestureCallbacks.showVolumeFeedback,
                    clearLiveGestureFeedbackState = gestureCallbacks.clearLiveGestureFeedback,
                    revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
                    commitHorizontalSeekState = gestureCallbacks.commitHorizontalSeek,
                ),
        ) {
            val resolvedSurfaceUrl = activeSourceUrl.takeUnless {
                VortexMediaExtractor.isResolvableUrl(it)
            }
            val playerSurfaceSourceUrl = if (isP2pPlaybackActive) p2pResolvedSourceUrl else resolvedSurfaceUrl
            val playbackKey = activePlaybackKey
            val initialPositionRequestKey = currentInitialPositionRequestKey()

            val resolvedAutoEngineState = remember(sourcePlaybackKey) {
                mutableStateOf<AndroidPlaybackEngine?>(null)
            }
            if (!initialLoadCompleted || resolvedAutoEngineState.value == null) {
                resolvedAutoEngineState.value = resolveAutoPlaybackEngine(
                    playbackEngineSetting = playerSettingsUiState.androidPlaybackEngine,
                    videoId = activeVideoId,
                    streamFilename = activeTorrentFilename ?: activeStreamFilename,
                    streamName = activeStreamTitle,
                    streamDescription = activeStreamSubtitle,
                    contentTitle = title,
                    genres = playerMeta?.genres.orEmpty(),
                    country = playerMeta?.country,
                )
            }
            val resolvedAutoEngine = resolvedAutoEngineState.value

            val activeWebEmbed = webFallbackEmbedUrl
            if (activeWebEmbed != null) {
                key(activeWebEmbed) {
                    VortexEmbedWebView(
                        url = activeWebEmbed,
                        modifier = baseVideoModifier,
                        onLoadingChange = { loading ->
                            webFallbackLoading = loading
                            if (!loading) {
                                initialLoadCompleted = true
                            }
                        },
                        onBlocked = { webFallbackBlockedCount += 1 },
                        onError = { desc ->
                            webFallbackLoading = false
                            errorMessage = "Web Player error: $desc. Try another server."
                        },
                    )
                }
                if (webFallbackLoading && errorMessage == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Text(
                                text = "Loading Web Player...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (!isInPip && errorMessage == null) {
                    val webTopPadding = if (isPortraitViewport) 8.dp else 14.dp
                    val webSidePadding = (if (isPortraitViewport) 10.dp else 16.dp) +
                        if (isPortraitViewport) 0.dp else horizontalSafePadding
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = webSidePadding, top = webTopPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.58f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable {
                                    if (isPlayerFullscreen) {
                                        isPlayerFullscreen = false
                                    } else {
                                        flushWatchProgress()
                                        args.onBack()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = buildString {
                                    append("Web Player")
                                    if (webFallbackBlockedCount > 0) {
                                        append(" · $webFallbackBlockedCount blocked")
                                    }
                                },
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = webSidePadding, top = webTopPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                .clickable { retryCurrentServerExtraction() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry direct player",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Direct",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (fold == null) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.58f))
                                    .border(0.75.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .clickable { isPlayerFullscreen = !isPlayerFullscreen },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isPlayerFullscreen) {
                                        Icons.Rounded.FullscreenExit
                                    } else {
                                        Icons.Rounded.Fullscreen
                                    },
                                    contentDescription = if (isPlayerFullscreen) "Exit fullscreen" else "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            } else if (playerSurfaceSourceUrl != null) {
                key(playbackKey) {
                    val active = remember { mutableStateOf(true) }
                    DisposableEffect(Unit) {
                        onDispose { active.value = false }
                    }
                    PlatformPlayerSurface(
                        sourceUrl = playerSurfaceSourceUrl,
                        sourceAudioUrl = activeSourceAudioUrl,
                        sourceHeaders = activeSourceHeaders,
                        sourceResponseHeaders = activeSourceResponseHeaders,
                        externalSubtitles = externalSubtitles,
                        streamType = activeStreamType,
                        modifier = videoModifier,
                        playWhenReady = shouldPlay,
                        initialPositionMs = activeInitialPositionMs.takeIf { it > 0L },
                        initialPositionRequestKey = initialPositionRequestKey,
                        resizeMode = if (partyPanelInset > 0.dp) PlayerResizeMode.Fit else resizeMode,
                        playbackEngine = playbackEngineOverride ?: resolvedAutoEngine,
                        onInitialPositionHandled = { key, handled ->
                            if (active.value && playbackKey == activePlaybackKey && key == currentInitialPositionRequestKey()) {
                                initialSeekApplied = handled
                            }
                        },
                        onControllerReady = { controller ->
                            if (active.value && playbackKey == activePlaybackKey) {
                                playerController = controller
                                playerControllerSourceUrl = activeSourceUrl
                            }
                        },
                        onSnapshot = { snapshot ->
                            if (!active.value || !updatePlaybackSnapshot(snapshot, playbackKey)) return@PlatformPlayerSurface
                            refreshAudioTracksIfChanged()
                            if (!snapshot.isLoading) initialLoadCompleted = true
                            if (snapshot.isEnded) {
                                shouldPlay = false
                                controlsVisible = !playerControlsLocked
                            }
                        },
                        onError = { message ->
                            if (!active.value || playbackKey != activePlaybackKey) return@PlatformPlayerSurface
                            if (message != null && tryRefreshExpiredLinkAfterError(message)) {
                                return@PlatformPlayerSurface
                            }
                            if (message != null && tryFallbackToNextVortexServerAfterError(message)) {
                                return@PlatformPlayerSurface
                            }
                            if (message != null && tryFallbackToNextSourceAfterError(message)) {
                                return@PlatformPlayerSurface
                            }
                            // Engine failover is decided here, driven by its own player setting;
                            // the engine's own decoder/MIME retries have already been tried.
                            if (message != null && maybeFailoverPlaybackEngine(resolvedAutoEngine)) {
                                return@PlatformPlayerSurface
                            }
                            errorMessage = message
                            if (message != null) {
                                scrubbingPositionMs = null
                                controlsVisible = !playerControlsLocked
                                removeFailedStreamFromCache()
                            }
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = !isP2pPlaybackActive &&
                    webFallbackEmbedUrl == null &&
                    errorMessage == null &&
                    !controlsVisible &&
                    (isExtractingStream || !initialLoadCompleted || playbackSnapshot.isLoading),
                enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 180)),
            ) {
                InPlayerExtractionBufferingOverlay(
                    backdropArtwork = background ?: poster,
                    isInitialLoading = !initialLoadCompleted,
                    isExtractingStream = isExtractingStream,
                    serverLabel = extractionServerLabel
                        ?: activeStreamTitle.ifBlank { activeProviderName },
                    modifier = videoModifier,
                )
            }

            AnimatedVisibility(
                visible = !isPortraitViewport &&
                    playerSettingsUiState.pauseOverlayEnabled &&
                    pausedOverlayVisible &&
                    !controlsVisible &&
                    !playerControlsLocked &&
                    fold == null,
                enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 180)),
            ) {
                PauseMetadataOverlay(
                    title = title,
                    logo = logo,
                    isEpisode = isEpisode,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    episodeTitle = activeEpisodeTitle,
                    pauseDescription = activePauseDescription ?: activeStreamSubtitle,
                    providerName = activeProviderName,
                    metrics = metrics,
                    horizontalSafePadding = horizontalSafePadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (webFallbackEmbedUrl == null) {
                RenderPlayerControls(
                    displayedPositionMs = displayedPositionMs,
                    isEpisode = isEpisode,
                    folded = fold != null,
                    compactPortraitLayout = isPortraitViewport,
                    isFullscreen = !isPortraitViewport && !isInPip && fold == null,
                    onToggleFullscreen = if (!isInPip && fold == null) {
                        { isPlayerFullscreen = !isPlayerFullscreen }
                    } else {
                        null
                    },
                    modifier = if (isPortraitViewport) Modifier.fillMaxSize() else panelModifier,
                )
            }
            Box(modifier = videoModifier) {
                val backupNextServer = remember(
                    allServers,
                    activeSourceUrl,
                    activeVortexEmbedUrl,
                    activeStreamTitle,
                    errorMessage,
                ) {
                    if (errorMessage != null) nextBackupServerCandidate(allServers) else null
                }
                val canOpenWebPlayer = remember(
                    activeVortexEmbedUrl,
                    activeSourceUrl,
                    activeVideoId,
                    activeSeasonNumber,
                    activeEpisodeNumber,
                    errorMessage,
                    webFallbackEmbedUrl,
                ) {
                    errorMessage != null && webFallbackEmbedUrl == null && resolveWebFallbackEmbedUrl() != null
                }
                RenderPlaybackOverlays(
                    runtime = runtime,
                    displayedPositionMs = displayedPositionMs,
                    currentGestureFeedback = currentGestureFeedback,
                    p2pInitialLoadingMessage = p2pInitialLoadingMessage,
                    p2pInitialLoadingProgress = p2pInitialLoadingProgress,
                    showP2pRebufferStats = showP2pRebufferStats,
                    p2pRebufferMessage = p2pRebufferMessage,
                    p2pRebufferProgress = p2pRebufferProgress,
                    folded = fold != null,
                    onTryNextServer = backupNextServer?.let { next -> { switchToSource(next) } },
                    onOpenWebPlayer = if (canOpenWebPlayer) {
                        { openWebPlayerFallback() }
                    } else {
                        null
                    },
                    onRetryError = { retryCurrentServerExtraction() },
                )
            }
        }
    }

    // Keep renderVideoBox permanently in slot 0 of this single Column so toggling portrait <-> fullscreen
    // resizes the existing ExoPlayer surface in place without recreating playback from position 0.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.nuvio.colors.background),
    ) {
        renderVideoBox(viewportContainerModifier)
        if (isPortraitViewport) {
            val resolvedMeta = playerMeta ?: metaUiState.meta?.takeIf { it.id == parentMetaId }
            val hasSeriesContent = isSeries || playerMetaVideos.isNotEmpty()
            PlayerPortraitCompanion(
                title = title,
                overview = resolvedMeta?.description ?: activePauseDescription,
                genres = resolvedMeta?.genres.orEmpty(),
                backdropUrl = background ?: poster ?: resolvedMeta?.background ?: resolvedMeta?.poster,
                isSeries = hasSeriesContent,
                activeSeasonNumber = activeSeasonNumber,
                activeEpisodeNumber = activeEpisodeNumber,
                activeEpisodeTitle = activeEpisodeTitle,
                activeServerLabel = extractionServerLabel
                    ?: AnilumosAnimeBackend.displayNameForStreamUrl(activeVortexEmbedUrl ?: activeSourceUrl)
                    ?: VortexServers.serverFor(activeVortexEmbedUrl ?: activeSourceUrl)?.displayName
                    ?: activeStreamTitle.ifBlank { activeProviderName },
                isExtractingStream = isExtractingStream,
                allServers = allServers,
                isSourcesLoading = sourceStreamsState.isAnyLoading,
                activeSourceUrl = activeSourceUrl,
                activeVortexEmbedUrl = activeVortexEmbedUrl,
                activeStreamTitle = activeStreamTitle,
                episodes = playerMetaVideos,
                parentMetaType = parentMetaType,
                parentMetaId = parentMetaId,
                progressByVideoId = watchProgressUiState.byVideoIdForContent(parentMetaId),
                watchedKeys = watchedUiState.watchedKeys,
                blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
                recommendations = resolvedMeta?.moreLikeThis.orEmpty(),
                isMetaLoading = metaUiState.isLoading && resolvedMeta == null,
                onServerSelected = { stream -> switchToSource(stream) },
                onReloadServers = {
                    val vid = activeVideoId ?: parentMetaId.takeIf { it.isNotBlank() }
                    if (vid != null) {
                        PlayerStreamsRepository.loadSources(
                            type = contentType ?: parentMetaType,
                            videoId = vid,
                            season = activeSeasonNumber,
                            episode = activeEpisodeNumber,
                            forceRefresh = true,
                        )
                    }
                },
                onEpisodeSelected = { episode -> switchToPortraitEpisode(episode) },
                onOpenAllEpisodes = { openEpisodesPanel() },
                onRecommendationClick = { item ->
                    flushWatchProgress()
                    args.onOpenMeta?.invoke(item)
                },
                onOpenFullscreen = { isPlayerFullscreen = true },
                onOpenSourcesPanel = {
                    showSourcesPanel = true
                    val vid = activeVideoId ?: parentMetaId.takeIf { it.isNotBlank() }
                    if (vid != null) {
                        PlayerStreamsRepository.loadSources(
                            type = contentType ?: parentMetaType,
                            videoId = vid,
                            season = activeSeasonNumber,
                            episode = activeEpisodeNumber,
                        )
                    }
                },
                onOpenSubtitles = {
                    refreshTracks()
                    showSubtitleModal = true
                },
                onOpenAudio = {
                    refreshTracks()
                    showAudioModal = true
                },
                onOpenResize = {
                    showResizeModal = true
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

    Box(modifier = panelModifier) {
        RenderParty(
            runtime = runtime,
            panelWidth = partyPanelWidth,
            modal = !dockPartyPanel,
            panelVisible = showPartyPanel && !isInPip,
        )
        RenderPlayerModals(
            displayedPositionMs = displayedPositionMs,
            sourceStreamsUiState = mergedSourceStreamsState,
        )
    }
}

@Composable
private fun PlayerScreenRuntime.rememberMergedAllServers(): List<StreamItem> {
    val instantStreams = remember(
        contentType,
        parentMetaType,
        activeVideoId,
        parentMetaId,
        activeSeasonNumber,
        activeEpisodeNumber,
        activeVortexEmbedUrl,
    ) {
        VortexBuiltinAddon.instantStreamsFor(
            type = contentType ?: parentMetaType,
            videoId = activeVideoId,
            parentMetaId = parentMetaId,
            season = activeSeasonNumber,
            episode = activeEpisodeNumber,
            embedUrlHint = activeVortexEmbedUrl,
        )
    }
    val addonStreams = remember(sourceStreamsState.groups) {
        sourceStreamsState.groups.flatMap { it.streams }
    }
    return remember(instantStreams, addonStreams) {
        buildList {
            val seenUrls = HashSet<String>()
            val seenLabels = HashSet<String>()
            instantStreams.forEach { stream ->
                add(stream)
                stream.playableDirectUrl?.let(seenUrls::add)
                seenLabels += stream.displayServerLabel().lowercase()
            }
            addonStreams.forEach { stream ->
                val urlKey = stream.playableDirectUrl ?: stream.externalUrl
                val labelKey = stream.displayServerLabel().lowercase()
                val isDuplicateBuiltin = stream.addonId == VortexBuiltinAddon.ADDON_ID &&
                    (urlKey in seenUrls || labelKey in seenLabels)
                if (!isDuplicateBuiltin && (urlKey == null || urlKey !in seenUrls)) {
                    add(stream)
                    urlKey?.let(seenUrls::add)
                }
            }
        }
    }
}

private fun mergeInstantStreamsIntoUiState(
    base: StreamsUiState,
    mergedServers: List<StreamItem>,
): StreamsUiState {
    if (mergedServers.isEmpty()) return base
    val existingUrls = base.groups.flatMap { g ->
        g.streams.mapNotNull { it.playableDirectUrl ?: it.externalUrl }
    }.toHashSet()
    val missingInstant = mergedServers.filter { stream ->
        val u = stream.playableDirectUrl ?: stream.externalUrl
        stream.addonId == VortexBuiltinAddon.ADDON_ID && (u == null || u !in existingUrls)
    }
    if (missingInstant.isEmpty()) return base
    val existingVortexIndex = base.groups.indexOfFirst { it.addonId == VortexBuiltinAddon.ADDON_ID }
    val updatedGroups = if (existingVortexIndex >= 0) {
        base.groups.mapIndexed { idx, group ->
            if (idx == existingVortexIndex) {
                group.copy(streams = mergedServers.filter { it.addonId == VortexBuiltinAddon.ADDON_ID })
            } else {
                group
            }
        }
    } else {
        listOf(
            AddonStreamGroup(
                addonName = if (missingInstant.any { AnilumosAnimeBackend.isAnilumosStreamUrl(it.playableDirectUrl) }) {
                    "Vortex Anime"
                } else {
                    VortexBuiltinAddon.NAME
                },
                addonId = VortexBuiltinAddon.ADDON_ID,
                streams = missingInstant,
                isLoading = false,
            ),
        ) + base.groups
    }
    return base.copy(
        groups = updatedGroups,
        emptyStateReason = if (updatedGroups.any { it.streams.isNotEmpty() }) null else base.emptyStateReason,
    )
}

@Composable
private fun p2pConnectingPhaseLabel(phase: String): String = when (phase) {
    "add_magnet" -> org.jetbrains.compose.resources.stringResource(
        com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_fetching_metadata,
    )
    "prepare_stream", "attach_route" -> org.jetbrains.compose.resources.stringResource(
        com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_preparing_stream,
    )
    else -> org.jetbrains.compose.resources.stringResource(
        com.rajatsolankii.vortex.generated.resources.Res.string.player_torrent_starting_engine,
    )
}

private val PlayerScreenRuntime.activeAddonLogo: String?
    get() = addonsUiState.addons.firstNotNullOfOrNull { addon ->
        addon.manifest?.takeIf { addon.streamAddonInstanceId(it.id) == activeProviderAddonId }?.logoUrl
    }

private fun PlayerScreenRuntime.currentInitialPositionRequestKey(): String? {
    val positionMs = activeInitialPositionMs.takeIf { it > 0L } ?: return null
    return "$activePlaybackIdentity:${activeVideoId.orEmpty()}:$positionMs"
}

@Composable
private fun PlayerScreenRuntime.RenderPlayerControls(
    displayedPositionMs: Long,
    isEpisode: Boolean,
    folded: Boolean,
    compactPortraitLayout: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier,
) {
    val isInPip = rememberIsInPictureInPicture()
    val partyState by PartyRepository.state.collectAsStateWithLifecycle()
    val partyActive = partyState.party != null
    val playerBadges = rememberPlayerBadges()
    LaunchedEffect(folded) {
        if (folded) {
            controlsVisible = true
            showParentalGuide = false
        }
    }
    AnimatedVisibility(
        visible = (controlsVisible || folded || showParentalGuide) && !playerControlsLocked && !isInPip &&
            !(folded && isPanelOpen),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        PlayerControlsShell(
            title = title,
            streamTitle = activeStreamTitle,
            providerName = activeProviderName,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            episodeTitle = activeEpisodeTitle,
            playbackSnapshot = playbackSnapshot,
            displayedPositionMs = displayedPositionMs,
            metrics = metrics,
            resizeMode = resizeMode,
            isLocked = playerControlsLocked,
            useLegacyLayout = if (compactPortraitLayout) false else playerSettingsUiState.useLegacyPlayerLayout,
            showRemainingTime = showRemainingTime,
            onRuntimeClick = { showRemainingTime = !showRemainingTime },
            releaseInfo = metaUiState.meta?.takeIf { it.id == parentMetaId }?.releaseInfo,
            badges = playerBadges,
            hideDetails = activeSkipInterval != null && !skipIntervalDismissed,
            onNextEpisodeClick = if (nextEpisodeInfo?.hasAired == true && !nextEpisodeAutoPlaySearching && nextEpisodeAutoPlayCountdown == null) {
                {
                    if (!partyBlocksControl()) playNextEpisode()
                }
            } else null,
            onInteraction = { controlsActivityTick += 1 },
            showPlaybackControls = controlsVisible,
            onLockToggle = {
                if (playerControlsLocked) unlockPlayerControls() else lockPlayerControls()
            },
            onBack = {
                flushWatchProgress()
                args.onBack()
            },
            onPartyClick = if (PartyRepository.available.collectAsStateWithLifecycle().value) {
                { openPartyPanel() }
            } else {
                null
            },
            partyActive = partyActive,
            onTogglePlayback = { togglePlayback() },
            onSeekBack = { seekBy(-10_000L) },
            onSeekForward = { seekBy(10_000L) },
            onResizeModeClick = {
                showResizeModal = true
                controlsVisible = true
            },
            onSpeedClick = { cyclePlaybackSpeed() },
            onSubtitleClick = {
                refreshTracks()
                showSubtitleModal = true
            },
            onAudioClick = {
                refreshTracks()
                showAudioModal = true
            },
            onVideoSettingsClick = if (isIos) {
                {
                    showVideoSettingsModal = true
                    controlsVisible = true
                }
            } else {
                null
            },
            onSourcesClick = if (activeVideoId != null) { { openSourcesPanel() } } else null,
            onEpisodesClick = if (isSeries) { { openEpisodesPanel() } } else null,
            chapters = chapters,
            onChaptersClick = if (chapters.isNotEmpty()) { { openChaptersPanel() } } else null,
            onOpenInExternalPlayer = args.onOpenInExternalPlayer
                ?.takeUnless { ServerStreams.isServerSourceId(activeProviderAddonId) }
                ?.let { openExternal ->
                {
                    val loadedSubtitles = addonSubtitles
                        .takeIf { it.isNotEmpty() }
                        ?.map { sub ->
                            SubtitleInput(
                                url = sub.url,
                                name = buildString {
                                    if (!sub.addonName.isNullOrBlank()) append("[${sub.addonName}] ")
                                    append(sub.display)
                                },
                                lang = sub.language,
                            )
                        }
                    PlayerStreamsRepository.pauseSearchForPlayback()
                    openExternal(
                        ExternalPlayerPlaybackRequest(
                            sourceUrl = activeSourceUrl,
                            title = title,
                            streamTitle = activeStreamTitle,
                            sourceHeaders = activeSourceHeaders,
                            resumePositionMs = playbackSnapshot.positionMs,
                            durationMs = playbackSnapshot.durationMs.takeIf { it > 0L },
                            playbackSession = playbackSession,
                            subtitles = loadedSubtitles,
                            season = activeSeasonNumber,
                            episode = activeEpisodeNumber,
                            episodeTitle = activeEpisodeTitle,
                        ),
                    )
                }
            },
            onSubmitIntroClick = if (
                isSeries &&
                playerSettingsUiState.introSubmitEnabled &&
                playerSettingsUiState.introDbApiKey.isNotBlank()
            ) {
                { showSubmitIntroModal = true }
            } else {
                null
            },
            onSwitchEngineClick = if (playerController?.playbackEngine != null) {
                { showSwitchEngineConfirm = true }
            } else {
                null
            },
            onStreamInfoClick = { openStreamInfo() },
            parentalWarnings = parentalWarnings,
            showParentalGuide = showParentalGuide,
            onParentalGuideAnimationComplete = { showParentalGuide = false },
            onScrubChange = { positionMs ->
                isScrubbingTimeline = true
                scrubbingPositionMs = positionMs
            },
            onScrubFinished = { positionMs ->
                val targetMs = if (isPartyControlLocked) ownSeekTarget(positionMs) else positionMs
                if (targetMs == null) {
                    isScrubbingTimeline = false
                    scrubbingPositionMs = null
                } else {
                    finishTimelineScrub(targetMs)
                    playerController?.seekTo(targetMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = targetMs)
                }
            },
            horizontalSafePadding = horizontalSafePadding,
            modifier = modifier,
            foldLayout = folded,
            gestureController = gestureController,
            onWake = {
                controlsVisible = true
                controlsActivityTick += 1
            },
            compactPortraitLayout = compactPortraitLayout,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
        )
    }
    AnimatedVisibility(
        visible = folded && playerControlsLocked && !isInPip,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        FoldLockedControls(
            onUnlock = { unlockPlayerControls() },
            modifier = modifier,
        )
    }
}

@Composable
private fun BoxScope.RenderPlaybackOverlays(
    runtime: PlayerScreenRuntime,
    displayedPositionMs: Long,
    currentGestureFeedback: GestureFeedbackState?,
    p2pInitialLoadingMessage: String?,
    p2pInitialLoadingProgress: Float?,
    showP2pRebufferStats: Boolean,
    p2pRebufferMessage: String?,
    p2pRebufferProgress: Float?,
    folded: Boolean,
    onTryNextServer: (() -> Unit)? = null,
    onOpenWebPlayer: (() -> Unit)? = null,
    onRetryError: (() -> Unit)? = null,
) {
    runtime.run {
        PlayerPlaybackOverlays(
            playerControlsLocked = playerControlsLocked,
            useLegacyLayout = playerSettingsUiState.useLegacyPlayerLayout,
            lockedOverlayVisible = lockedOverlayVisible && !folded,
            showRemainingTime = showRemainingTime,
            playbackSnapshot = playbackSnapshot,
        displayedPositionMs = displayedPositionMs,
        metrics = metrics,
        horizontalSafePadding = horizontalSafePadding,
        onUnlock = { unlockPlayerControls() },
        showOpeningOverlay = activeTorrentInfoHash != null &&
            playerSettingsUiState.showLoadingOverlay &&
            !initialLoadCompleted &&
            errorMessage == null,
        backdropArtwork = background ?: poster,
        logo = logo,
        title = title,
        onBackWithProgress = {
            flushWatchProgress()
            args.onBack()
        },
        openingLoadingMessage = if (playerSettingsUiState.showPlayerLoadingStatus) {
            p2pInitialLoadingMessage ?: playerLoadingStatusMessage(
                showStatus = true,
                controllerReady = playerController != null,
                snapshot = playbackSnapshot,
            )
        } else null,
        p2pInitialLoadingProgress = p2pInitialLoadingProgress,
        showP2pRebufferStats = showP2pRebufferStats,
        p2pRebufferMessage = p2pRebufferMessage,
        p2pRebufferProgress = p2pRebufferProgress,
        currentGestureFeedback = currentGestureFeedback,
        renderedGestureFeedback = renderedGestureFeedback,
        initialLoadCompleted = initialLoadCompleted,
        pausedOverlayVisible = pausedOverlayVisible,
        activeSkipInterval = activeSkipInterval,
        skipsToPostCredits = activeSkipInterval?.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.skipsToPostCredits == true,
        skipIntervalDismissed = skipIntervalDismissed,
        controlsVisible = controlsVisible,
        onSkipInterval = { interval ->
            interval.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.let { action ->
                val durationMs = playbackSnapshot.durationMs
                val requestedMs = if (durationMs > 0L) action.targetMs.coerceAtMost(durationMs - 1) else action.targetMs
                val seekMs = if (isPartyControlLocked) ownSeekTarget(requestedMs) else requestedMs
                if (seekMs != null) {
                    playerController?.seekTo(seekMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = seekMs)
                    skipIntervalDismissed = true
                }
            }
        },
        onDismissSkipInterval = { skipIntervalDismissed = true },
        sliderEdgePadding = sliderEdgePadding,
        overlayBottomPadding = overlayBottomPadding,
        isSeries = isSeries,
        nextEpisodeInfo = nextEpisodeInfo,
        showNextEpisodeCard = showNextEpisodeCard,
        nextEpisodeAutoPlaySearching = nextEpisodeAutoPlaySearching,
        nextEpisodeAutoPlaySourceName = nextEpisodeAutoPlaySourceName,
        nextEpisodeAutoPlayCountdown = nextEpisodeAutoPlayCountdown,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        onPlayNextEpisode = {
            if (!partyBlocksControl()) playNextEpisode()
        },
        onDismissNextEpisode = {
            cancelNextEpisodeAutoPlay()
            nextEpisodeCardDismissed = true
            showNextEpisodeCard = false
        },
        errorMessage = errorMessage,
            onDismissError = {
                flushWatchProgress()
                args.onBack()
            },
            onTryNextServer = onTryNextServer,
            onOpenWebPlayer = onOpenWebPlayer,
            onRetryError = onRetryError,
        )
    }
}

@Composable
private fun PlayerScreenRuntime.RenderPlayerModals(
    displayedPositionMs: Long,
    sourceStreamsUiState: StreamsUiState = sourceStreamsState,
) {
    PlayerChaptersPanel(
        visible = showChaptersPanel && chapters.isNotEmpty(),
        chapters = chapters,
        positionMs = displayedPositionMs,
        durationMs = playbackSnapshot.durationMs,
        onChapterSelected = { selectChapter(it) },
        onDismiss = {
            showChaptersPanel = false
            controlsVisible = true
        },
    )
    PlayerScreenModalHosts(
        pendingP2pSwitch = pendingP2pSwitch,
        onPendingP2pSwitchChanged = { pendingP2pSwitch = it },
        onP2pEpisodeStreamSelected = { stream, episode, isAutoPlay ->
            switchToP2pEpisodeStream(stream, episode, isAutoPlay)
        },
        onP2pSourceStreamSelected = { stream -> switchToP2pSourceStream(stream) },
        onNextEpisodeAutoPlaySearchingChanged = { nextEpisodeAutoPlaySearching = it },
        onNextEpisodeAutoPlayCountdownChanged = { nextEpisodeAutoPlayCountdown = it },
        onNextEpisodeAutoPlaySourceNameChanged = { nextEpisodeAutoPlaySourceName = it },
        showAudioModal = showAudioModal,
        audioTracks = serverAudioTracks.ifEmpty { audioTracks },
        selectedAudioIndex = serverAudioTracks.firstOrNull { it.isSelected }?.index ?: selectedAudioIndex,
        onAudioTrackSelected = { index ->
            if (serverAudioTracks.isNotEmpty()) {
                selectServerAudioTrack(index)
            } else {
                selectedAudioIndex = index
                persistAudioPreference(audioTracks.firstOrNull { it.index == index })
                playerController?.selectAudioTrack(index)
            }
            scope.launch {
                kotlinx.coroutines.delay(200)
                showAudioModal = false
            }
        },
        onAudioModalDismissed = { showAudioModal = false },
        showAudioDelay = playerController?.supportsAudioDelay == true,
        audioDelayMs = audioDelayMs,
        onAudioDelayChanged = { delayMs -> setAudioDelay(delayMs) },
        showAudioAmplification = playerController?.supportsAudioAmplification == true,
        audioAmplificationDb = audioAmplificationDb,
        keepAudioAmplification = playerSettingsUiState.persistAudioAmplification,
        onAudioAmplificationChanged = { db -> setAudioAmplification(db) },
        onKeepAudioAmplificationChanged = { keep -> setKeepAudioAmplification(keep) },
        showCenterMix = centerMixAvailable,
        centerMixLevelDb = centerMixLevelDb,
        onCenterMixLevelChanged = { db -> setCenterMixLevel(db) },
        showSubtitleModal = showSubtitleModal,
        subtitleTracks = serverSubtitleTracks.ifEmpty { subtitleTracks },
        selectedSubtitleIndex = if (serverSubtitleTracks.isEmpty()) {
            selectedSubtitleIndex
        } else {
            serverSubtitleTracks.firstOrNull { it.isSelected }?.index ?: -1
        },
        addonSubtitles = visibleAddonSubtitles,
        selectedAddonSubtitleId = selectedAddonSubtitleId,
        isLoadingAddonSubtitles = isLoadingAddonSubtitles,
        subtitleStyle = subtitleStyle,
        subtitleDelayMs = subtitleDelayMs,
        selectedAddonSubtitle = selectedAddonSubtitle,
        subtitleAutoSyncState = subtitleAutoSyncState,
        onBuiltInSubtitleTrackSelected = { index ->
            if (serverSubtitleTracks.isNotEmpty() && index >= 0) {
                selectServerSubtitleTrack(index)
            } else {
                val wasCustom = useCustomSubtitles
                isUserExplicitSubtitleSelection = true
                preferredSubtitleSelectionApplied = true
                selectedSubtitleIndex = index
                selectedAddonSubtitleId = null
                useCustomSubtitles = false
                persistInternalSubtitlePreference(subtitleTracks.firstOrNull { it.index == index })
                if (wasCustom) {
                    playerController?.clearExternalSubtitleAndSelect(index)
                } else {
                    playerController?.selectSubtitleTrack(index)
                }
                if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
            }
        },
        onAddonSubtitleSelected = { addon ->
            isUserExplicitSubtitleSelection = true
            selectedAddonSubtitleId = addon.selectionKey
            selectedSubtitleIndex = -1
            useCustomSubtitles = true
            preferredSubtitleSelectionApplied = true
            persistAddonSubtitlePreference(addon)
            attachSelectedAddonSubtitleWithAutoSync(addon.url) // AutoSync hook
            if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
        },
        onFetchAddonSubtitles = { fetchAddonSubtitlesForActiveItem() },
        onSubtitleStyleChanged = PlayerSettingsRepository::setSubtitleStyle,
        onSubtitleDelayChanged = { delayMs -> setSubtitleDelay(delayMs) },
        onSubtitleDelayReset = { setSubtitleDelay(0) },
        onAutoSyncCapture = { captureSubtitleAutoSyncTime() },
        onAutoSyncCueSelected = { cue -> applySubtitleAutoSyncCue(cue) },
        onAutoSyncReload = { loadSubtitleAutoSyncCues(force = true) },
        onSubtitleModalDismissed = { showSubtitleModal = false },
        showVideoSettingsModal = showVideoSettingsModal,
        playerSettings = playerSettingsUiState,
        onVideoSettingsChanged = {
            playerController?.configureIosVideoOutput(PlayerSettingsRepository.uiState.value)
        },
        onVideoSettingsModalDismissed = { showVideoSettingsModal = false },
        showResizeModal = showResizeModal,
        resizeMode = resizeMode,
        videoZoomScale = videoZoomScale,
        onResizeModeSelected = { mode ->
            applyResizeMode(mode)
            showResizeModal = false
        },
        onVideoZoomScaleChanged = { scale ->
            val clamped = scale.coerceIn(1f, 3f)
            videoZoomScale = clamped
            videoPanOffset = if (clamped <= 1f) Offset.Zero else clampVideoPanOffset(videoPanOffset, clamped, layoutSize)
            showGestureMessage("${(clamped * 100f).roundToInt()}%")
        },
        onResetVideoZoom = {
            resetPinchZoom()
        },
        onResizeModalDismissed = { showResizeModal = false },
        showSourcesPanel = showSourcesPanel,
        sourceStreamsState = sourceStreamsUiState,
        contentTitle = title,
        activeEpisodeTitle = activeEpisodeTitle,
        currentStream = PlayerCurrentStream(
            url = activeVortexEmbedUrl ?: activeSourceUrl,
            name = extractionServerLabel
                ?: AnilumosAnimeBackend.displayNameForStreamUrl(activeVortexEmbedUrl ?: activeSourceUrl)
                ?: VortexServers.serverFor(activeVortexEmbedUrl ?: activeSourceUrl)?.displayName
                ?: activeStreamTitle,
            description = activeStreamSubtitle,
            addonId = activeProviderAddonId,
            infoHash = activeStream?.p2pInfoHash ?: activeTorrentInfoHash,
            fileIdx = activeStream?.p2pFileIdx ?: activeTorrentFileIdx,
            filename = activeSourceFilename,
        ),
        onSourceFilterSelected = PlayerStreamsRepository::selectSourceFilter,
        onSourceStreamSelected = { stream -> switchToSource(stream) },
        onReloadSources = {
            val vid = activeVideoId
            if (vid != null) {
                PlayerStreamsRepository.loadSources(
                    type = contentType ?: parentMetaType,
                    videoId = vid,
                    season = activeSeasonNumber,
                    episode = activeEpisodeNumber,
                    forceRefresh = true,
                )
            }
        },
        onSourcesPanelDismissed = {
            showSourcesPanel = false
            PlayerStreamsRepository.stopSourcesLoading()
            controlsVisible = true
        },
        isSeries = isSeries,
        showEpisodesPanel = showEpisodesPanel,
        allEpisodes = playerMetaVideos,
        parentMetaType = parentMetaType,
        parentMetaId = parentMetaId,
        activeSeasonNumber = activeSeasonNumber,
        activeEpisodeNumber = activeEpisodeNumber,
        watchProgressByVideoId = watchProgressUiState.byVideoIdForContent(parentMetaId),
        watchedKeys = watchedUiState.watchedKeys,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        episodeStreamsPanelState = episodeStreamsPanelState,
        episodeStreamsRepoState = episodeStreamsRepoState,
        onEpisodeSelectedForDownload = { episode ->
            selectDownloadedEpisodeForPlayback(
                parentMetaId = parentMetaId,
                episode = episode,
                onDownloadedEpisodeSelected = { item, video -> switchToDownloadedEpisode(item, video) },
            )
        },
        onEpisodeStreamsRequested = { episode ->
            PlayerStreamsRepository.loadEpisodeStreams(
                type = contentType ?: parentMetaType,
                videoId = episode.id,
                season = episode.season,
                episode = episode.episode,
            )
            episodeStreamsPanelState = EpisodeStreamsPanelState(showStreams = true, selectedEpisode = episode)
        },
        onEpisodeStreamFilterSelected = PlayerStreamsRepository::selectEpisodeStreamsFilter,
        onEpisodeStreamSelected = { stream, episode -> switchToEpisodeStream(stream, episode) },
        onBackToEpisodes = {
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
        },
        onReloadEpisodeStreams = {
            val episode = episodeStreamsPanelState.selectedEpisode
            if (episode != null) {
                PlayerStreamsRepository.loadEpisodeStreams(
                    type = contentType ?: parentMetaType,
                    videoId = episode.id,
                    season = episode.season,
                    episode = episode.episode,
                    forceRefresh = true,
                )
            }
        },
        onEpisodesPanelDismissed = {
            showEpisodesPanel = false
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
            controlsVisible = true
        },
        showSubmitIntroModal = showSubmitIntroModal,
        activeVideoId = activeVideoId,
        metaUiState = metaUiState,
        displayedPositionMs = displayedPositionMs,
        submitIntroSegmentType = submitIntroSegmentType,
        onSubmitIntroSegmentTypeChanged = { submitIntroSegmentType = it },
        submitIntroStartTimeStr = submitIntroStartTimeStr,
        onSubmitIntroStartTimeChanged = { submitIntroStartTimeStr = it },
        submitIntroEndTimeStr = submitIntroEndTimeStr,
        onSubmitIntroEndTimeChanged = { submitIntroEndTimeStr = it },
        onSubmitIntroDismissed = { showSubmitIntroModal = false },
        onSubmitIntroSuccess = {
            submitIntroStartTimeStr = "00:00"
            submitIntroEndTimeStr = "00:00"
            submitIntroSegmentType = "intro"
            showSubmitIntroModal = false
        },
    )
    StreamInfoOverlay(
        visible = showStreamInfo,
        addonName = activeProviderName,
        addonLogo = activeAddonLogo,
        streamName = activeStreamTitle,
        streamDescription = activeStreamSubtitle,
        playbackEngine = playerController?.playbackEngine,
        serverPlayback = serverPlaybackSummary(),
        mediaInfo = streamMediaInfo,
        audioTrack = audioTracks.firstOrNull { it.index == selectedAudioIndex },
        subtitleTrack = subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }.takeIf { !useCustomSubtitles },
        addonSubtitle = selectedAddonSubtitle.takeIf { useCustomSubtitles },
        onDismiss = { showStreamInfo = false },
    )
    val switchEngineTarget = playerController?.playbackEngine?.switchTarget
    if (switchEngineTarget != null) {
        NuvioStatusModal(
            isVisible = showSwitchEngineConfirm,
            title = stringResource(Res.string.player_engine_switch_title, switchEngineTarget.label),
            message = stringResource(Res.string.player_engine_switch_message),
            confirmText = stringResource(Res.string.action_switch),
            dismissText = stringResource(Res.string.action_cancel),
            onConfirm = {
                showSwitchEngineConfirm = false
                switchPlaybackEngine()
            },
            onDismiss = { showSwitchEngineConfirm = false },
        )
    }
}
