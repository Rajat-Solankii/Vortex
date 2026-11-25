package com.rajatsolankii.vortex.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatsolankii.vortex.core.ui.NuvioTokens
import com.rajatsolankii.vortex.core.ui.nuvio
import com.rajatsolankii.vortex.features.p2p.P2pLoadingStatus
import com.rajatsolankii.vortex.features.player.skip.NextEpisodeCard
import com.rajatsolankii.vortex.features.player.skip.NextEpisodeInfo
import com.rajatsolankii.vortex.features.player.skip.SkipIntroButton
import com.rajatsolankii.vortex.features.player.skip.SkipInterval
import kotlinx.coroutines.delay

private val PLAYER_EXTRACTION_QUOTES = listOf(
    "Every great frame begins just past the buffer.",
    "Finding the fastest mirror for your screen...",
    "Patience sharpens the picture—almost ready.",
    "Tuning audio tracks and crisp HD streams...",
    "Good cinema rewards a brief breath before takeoff.",
    "Racing all active mirrors for zero-stutter playback...",
    "Even the longest arc begins with a single stream.",
    "Syncing subtitles and buffering ahead for smooth play...",
    "Warming up the projector—sit back.",
    "Almost there—locking onto the cleanest CDN...",
)

@Composable
internal fun BoxScope.PlayerPlaybackOverlays(
    playerControlsLocked: Boolean,
    useLegacyLayout: Boolean,
    lockedOverlayVisible: Boolean,
    showRemainingTime: Boolean = false,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: Dp,
    onUnlock: () -> Unit,
    showOpeningOverlay: Boolean,
    backdropArtwork: String?,
    logo: String?,
    title: String,
    onBackWithProgress: () -> Unit,
    openingLoadingMessage: String?,
    p2pInitialLoadingProgress: Float?,
    showP2pRebufferStats: Boolean,
    p2pRebufferMessage: String?,
    p2pRebufferProgress: Float?,
    currentGestureFeedback: GestureFeedbackState?,
    renderedGestureFeedback: GestureFeedbackState?,
    initialLoadCompleted: Boolean,
    pausedOverlayVisible: Boolean,
    activeSkipInterval: SkipInterval?,
    skipsToPostCredits: Boolean,
    skipIntervalDismissed: Boolean,
    controlsVisible: Boolean,
    onSkipInterval: (SkipInterval) -> Unit,
    onDismissSkipInterval: () -> Unit,
    sliderEdgePadding: Dp,
    overlayBottomPadding: Dp,
    isSeries: Boolean,
    nextEpisodeInfo: NextEpisodeInfo?,
    showNextEpisodeCard: Boolean,
    nextEpisodeAutoPlaySearching: Boolean,
    nextEpisodeAutoPlaySourceName: String?,
    nextEpisodeAutoPlayCountdown: Int?,
    blurUnwatchedEpisodes: Boolean,
    onPlayNextEpisode: () -> Unit,
    onDismissNextEpisode: () -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onTryNextServer: (() -> Unit)? = null,
    onOpenWebPlayer: (() -> Unit)? = null,
    onRetryError: (() -> Unit)? = null,
    isExtractingStream: Boolean = false,
    extractionServerLabel: String? = null,
    activeServerLabel: String? = null,
    showInlineExtractionOrBuffering: Boolean = false,
) {
    AnimatedVisibility(
        visible = playerControlsLocked && lockedOverlayVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LockedPlayerOverlay(
            playbackSnapshot = playbackSnapshot,
            displayedPositionMs = displayedPositionMs,
            metrics = metrics,
            horizontalSafePadding = horizontalSafePadding,
            onUnlock = onUnlock,
            useLegacyLayout = useLegacyLayout,
            showRemainingTime = showRemainingTime,
            modifier = Modifier.fillMaxSize(),
        )
    }

    AnimatedVisibility(
        visible = showOpeningOverlay,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        OpeningOverlay(
            artwork = backdropArtwork,
            logo = logo,
            title = title,
            onBack = onBackWithProgress,
            horizontalSafePadding = horizontalSafePadding,
            modifier = Modifier.fillMaxSize(),
            message = openingLoadingMessage,
            progress = p2pInitialLoadingProgress,
        )
    }

    AnimatedVisibility(
        visible = showInlineExtractionOrBuffering && errorMessage == null,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        InPlayerExtractionBufferingOverlay(
            backdropArtwork = backdropArtwork,
            isInitialLoading = !initialLoadCompleted,
            isExtractingStream = isExtractingStream,
            serverLabel = extractionServerLabel ?: activeServerLabel,
            modifier = Modifier.fillMaxSize(),
        )
    }

    P2pLoadingStatus(
        visible = showP2pRebufferStats && errorMessage == null,
        message = p2pRebufferMessage,
        progress = p2pRebufferProgress,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(top = 58.dp),
    )

    PlayerGestureOverlay(
        currentFeedback = currentGestureFeedback,
        renderedFeedback = renderedGestureFeedback,
        useLegacyLayout = useLegacyLayout,
        horizontalSafePadding = horizontalSafePadding,
        horizontalPadding = metrics.horizontalPadding,
    )

    if (!playerControlsLocked) {
        SkipIntroButton(
            interval = if (!initialLoadCompleted || pausedOverlayVisible) null else activeSkipInterval,
            skipsToPostCredits = skipsToPostCredits,
            dismissed = skipIntervalDismissed,
            controlsVisible = controlsVisible,
            onSkip = {
                activeSkipInterval?.let(onSkipInterval)
            },
            onDismiss = onDismissSkipInterval,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sliderEdgePadding, bottom = overlayBottomPadding),
        )
    }

    if (isSeries && !playerControlsLocked) {
        NextEpisodeCard(
            nextEpisode = nextEpisodeInfo,
            visible = showNextEpisodeCard || nextEpisodeAutoPlaySearching || nextEpisodeAutoPlayCountdown != null,
            isAutoPlaySearching = nextEpisodeAutoPlaySearching,
            autoPlaySourceName = nextEpisodeAutoPlaySourceName,
            autoPlayCountdownSec = nextEpisodeAutoPlayCountdown,
            blurred = blurUnwatchedEpisodes && nextEpisodeInfo?.isWatched == false,
            onPlayNext = onPlayNextEpisode,
            onDismiss = onDismissNextEpisode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = sliderEdgePadding, bottom = overlayBottomPadding),
        )
    }

    if (errorMessage != null) {
        ErrorModal(
            message = errorMessage,
            onDismiss = onDismissError,
            onTryNextServer = onTryNextServer,
            onOpenWebPlayer = onOpenWebPlayer,
            onRetry = onRetryError,
        )
    }
}

@Composable
internal fun InPlayerExtractionBufferingOverlay(
    backdropArtwork: String?,
    isInitialLoading: Boolean,
    isExtractingStream: Boolean,
    serverLabel: String?,
    modifier: Modifier = Modifier,
) {
    var quoteIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_200L)
            quoteIndex = (quoteIndex + 1) % PLAYER_EXTRACTION_QUOTES.size
        }
    }

    val statusText = when {
        isExtractingStream && !serverLabel.isNullOrBlank() -> "Extracting $serverLabel..."
        isExtractingStream -> "Extracting fast stream..."
        isInitialLoading && !serverLabel.isNullOrBlank() -> "Connecting to $serverLabel..."
        isInitialLoading -> "Starting playback..."
        !serverLabel.isNullOrBlank() -> "Buffering $serverLabel..."
        else -> "Buffering stream..."
    }
    val subHintText = when {
        isExtractingStream -> "Racing fastest mirrors automatically"
        isInitialLoading -> "Preparing direct media pipeline"
        else -> "Filling playback buffer"
    }

    val tokens = MaterialTheme.nuvio
    BoxWithConstraints(
        modifier = modifier.background(
            if (isInitialLoading) tokens.colors.background else Color.Black.copy(alpha = 0.45f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < 460.dp || maxHeight < 290.dp
        if (isInitialLoading && !backdropArtwork.isNullOrBlank()) {
            AsyncImage(
                model = backdropArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.28f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                tokens.colors.background.copy(alpha = 0.80f),
                                tokens.colors.background.copy(alpha = 0.58f),
                                tokens.colors.background.copy(alpha = 0.90f),
                            ),
                        ),
                    ),
            )
        }

        val cardShape = RoundedCornerShape(if (compact) NuvioTokens.Radius.xl else NuvioTokens.Radius.xxl)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = if (compact) 18.dp else 32.dp)
                .widthIn(max = if (compact) 340.dp else 460.dp)
                .clip(cardShape)
                .background(Color(0xFF1C1C1E).copy(alpha = 0.68f))
                .border(
                    width = tokens.borders.thin,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = cardShape,
                )
                .padding(
                    horizontal = if (compact) 16.dp else 24.dp,
                    vertical = if (compact) 12.dp else 18.dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 28.dp else 34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(tokens.borders.hairline, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 17.dp else 21.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        trackColor = Color.White.copy(alpha = 0.16f),
                        strokeWidth = 2.2.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = statusText,
                        color = tokens.colors.textPrimary,
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subHintText,
                        color = tokens.colors.textSecondary,
                        fontSize = if (compact) 10.5.sp else 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

            Crossfade(
                targetState = quoteIndex,
                animationSpec = tween(
                    durationMillis = NuvioTokens.Motion.slowMillis,
                    easing = NuvioTokens.Motion.standard,
                ),
                label = "extractionQuote",
            ) { idx ->
                Text(
                    text = "\u201C${PLAYER_EXTRACTION_QUOTES[idx]}\u201D",
                    color = tokens.colors.textPrimary.copy(alpha = 0.84f),
                    fontSize = if (compact) 11.sp else 12.5.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = if (compact) 15.sp else 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
