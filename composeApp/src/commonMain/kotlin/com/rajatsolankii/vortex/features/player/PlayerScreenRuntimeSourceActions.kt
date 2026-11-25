package com.rajatsolankii.vortex.features.player

import com.rajatsolankii.vortex.core.ui.NuvioToastController
import com.rajatsolankii.vortex.features.debrid.DirectDebridPlayableResult
import com.rajatsolankii.vortex.features.debrid.DirectDebridPlaybackResolver
import com.rajatsolankii.vortex.features.debrid.toastMessage
import com.rajatsolankii.vortex.features.details.MetaDetailsRepository
import com.rajatsolankii.vortex.features.details.MetaVideo
import com.rajatsolankii.vortex.features.downloads.DownloadItem
import com.rajatsolankii.vortex.features.downloads.DownloadSubtitles
import com.rajatsolankii.vortex.features.downloads.DownloadsRepository
import com.rajatsolankii.vortex.features.p2p.P2pSettingsRepository
import com.rajatsolankii.vortex.features.p2p.P2pStreamingEngine
import com.rajatsolankii.vortex.features.servers.ServerPlayback
import com.rajatsolankii.vortex.features.servers.serverPlaybackMessage
import com.rajatsolankii.vortex.features.streams.StreamBehaviorHints
import com.rajatsolankii.vortex.features.streams.StreamItem
import com.rajatsolankii.vortex.features.streams.StreamLinkCacheRepository
import com.rajatsolankii.vortex.features.streams.YouTubeStreamResolver
import com.rajatsolankii.vortex.features.vortex.AnilumosAnimeBackend
import com.rajatsolankii.vortex.features.vortex.VortexBuiltinAddon
import com.rajatsolankii.vortex.features.vortex.VortexMediaExtractor
import com.rajatsolankii.vortex.features.vortex.VortexServers
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressEntry
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressRepository
import com.rajatsolankii.vortex.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.youtube_resolution_failed
import org.jetbrains.compose.resources.getString

internal fun PlayerScreenRuntime.resolveDebridForPlayer(
    stream: StreamItem,
    season: Int?,
    episode: Int?,
    onResolved: (StreamItem) -> Unit,
    onStale: () -> Unit,
): Boolean {
    if (!DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) return false
    scope.launch {
        val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(
            stream = stream,
            season = season,
            episode = episode,
        )
        when (resolved) {
            is DirectDebridPlayableResult.Success -> onResolved(resolved.stream)
            else -> {
                resolved.toastMessage()?.let { NuvioToastController.show(it) }
                if (resolved == DirectDebridPlayableResult.Stale) {
                    onStale()
                }
            }
        }
    }
    return true
}

internal fun PlayerScreenRuntime.prepareServerForPlayer(
    stream: StreamItem,
    onPrepared: suspend (StreamItem) -> Unit,
): Boolean {
    if (!stream.needsServerPreparation) return false
    scope.launch {
        runCatching { ServerPlayback.prepare(stream) }
            .onFailure { if (it is CancellationException) throw it }
            .onSuccess { onPrepared(it) }
            .onFailure { NuvioToastController.show(it.serverPlaybackMessage()) }
    }
    return true
}

internal fun PlayerScreenRuntime.resolveYouTubeForPlayer(
    stream: StreamItem,
    onResolved: (StreamItem) -> Unit,
): Boolean {
    if (stream.youTubeIdToResolve == null) return false
    scope.launch {
        val resolved = YouTubeStreamResolver.Default.resolve(stream)
        if (resolved == null) {
            NuvioToastController.show(getString(Res.string.youtube_resolution_failed))
            return@launch
        }
        onResolved(resolved)
    }
    return true
}

internal fun PlayerScreenRuntime.p2pSentinelUrl(infoHash: String, fileIdx: Int?): String =
    "torrent://$infoHash${fileIdx?.let { "?index=$it" }.orEmpty()}"

internal fun PlayerScreenRuntime.isP2pStream(stream: StreamItem): Boolean =
    stream.needsLocalDebridResolve && stream.p2pInfoHash != null

internal fun PlayerScreenRuntime.openExternalSourceUrl(stream: StreamItem): Boolean {
    if (!stream.shouldOpenExternally) return false
    val url = stream.externalOpenUrl ?: return false
    val openExternalUrl = args.onOpenExternalUrl ?: return false
    openExternalUrl(url)
    showSourcesPanel = false
    showEpisodesPanel = false
    showChaptersPanel = false
    controlsVisible = true
    PlayerStreamsRepository.pauseSearchForPlayback()
    return true
}

internal fun StreamItem.playerSourceIdentityKey(): String? {
    serverTarget?.let { target -> return "server:${target.item.encode()}:${target.mediaSourceId.orEmpty()}" }
    p2pInfoHash?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { hash ->
        return "torrent:$hash:${p2pFileIdx ?: -1}"
    }

    clientResolve?.let { resolve ->
        val raw = resolve.stream?.raw
        val keyParts = listOf(
            addonId,
            resolve.service,
            resolve.serviceIndex?.toString(),
            resolve.infoHash?.trim()?.lowercase(),
            resolve.fileIdx?.toString(),
            resolve.magnetUri,
            resolve.torrentName,
            resolve.filename,
            raw?.torrentName,
            raw?.filename,
            raw?.size?.toString(),
            behaviorHints.filename,
            behaviorHints.videoSize?.toString(),
            streamLabel,
            streamSubtitle,
        ).map { it.orEmpty().trim() }
        if (keyParts.any { it.isNotBlank() }) {
            return "resolve:${keyParts.joinToString("|")}"
        }
    }

    behaviorHints.videoHash?.trim()?.takeIf { it.isNotBlank() }?.let { hash ->
        return "hash:$addonId:$hash:${behaviorHints.videoSize ?: ""}:${behaviorHints.filename.orEmpty()}"
    }

    playableDirectUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
        return "url:$url"
    }

    val fallbackParts = listOf(
        addonId,
        addonName,
        streamLabel,
        streamSubtitle.orEmpty(),
        behaviorHints.filename.orEmpty(),
        behaviorHints.videoSize?.toString().orEmpty(),
        sourceName.orEmpty(),
        sources.joinToString(","),
    ).map { it.trim() }
    return fallbackParts
        .takeIf { parts -> parts.any { it.isNotBlank() } }
        ?.joinToString(separator = "|", prefix = "meta:")
}

internal fun PlayerScreenRuntime.stopActiveP2pStream() {
    if (activeTorrentInfoHash != null || p2pResolvedSourceUrl != null) {
        P2pStreamingEngine.stopStream()
    }
    activeTorrentInfoHash = null
    activeTorrentFileIdx = null
    activeTorrentFilename = null
    activeTorrentTrackers = emptyList()
    p2pResolvedSourceUrl = null
    activeStreamFilename = null
}

/**
 * Filename used by the auto engine resolver for a direct stream, mirroring NuvioTV:
 * the stream's `behaviorHints.filename` when present, otherwise the URL path basename
 * (query string stripped, only when it looks like a file with an extension).
 */
internal fun streamPlaybackFilename(stream: StreamItem, url: String): String? =
    stream.behaviorHints.filename?.takeIf { it.isNotBlank() }
        ?: sourceFilenameFromUrl(url)

internal fun sourceFilenameFromUrl(url: String?): String? =
    url?.substringBefore('?')?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() && it.contains('.') }

internal fun PlayerScreenRuntime.saveP2pStreamForReuse(
    stream: StreamItem,
    videoId: String?,
    season: Int?,
    episode: Int?,
) {
    if (!playerSettingsUiState.streamReuseLastLinkEnabled || videoId == null) return
    val infoHash = stream.p2pInfoHash ?: return
    val cacheKey = StreamLinkCacheRepository.contentKey(
        type = contentType ?: parentMetaType,
        videoId = videoId,
        parentMetaId = parentMetaId,
        season = season,
        episode = episode,
    )
    StreamLinkCacheRepository.save(
        contentKey = cacheKey,
        url = "",
        streamName = stream.streamLabel,
        addonName = stream.addonName,
        addonId = stream.addonId,
        requestHeaders = emptyMap(),
        responseHeaders = emptyMap(),
        filename = stream.behaviorHints.filename,
        videoHash = stream.behaviorHints.videoHash,
        videoSize = stream.behaviorHints.videoSize,
        infoHash = infoHash,
        fileIdx = stream.p2pFileIdx,
        sources = stream.sources,
        bingeGroup = stream.behaviorHints.bingeGroup,
        addonLogo = stream.addonLogo,
        streamDescription = stream.streamSubtitle,
    )
}

internal fun PlayerScreenRuntime.switchToP2pSourceStream(stream: StreamItem) {
    val infoHash = stream.p2pInfoHash ?: return
    if (!P2pSettingsRepository.isVisible) return
    if (!P2pSettingsRepository.uiState.value.p2pEnabled) {
        pendingP2pSwitch = PendingPlayerP2pSwitch(stream = stream, episode = null, isAutoPlay = false)
        return
    }
    clearWebEmbedFallback()
    val currentPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
    flushWatchProgress()
    stopActiveP2pStream()
    saveP2pStreamForReuse(
        stream = stream,
        videoId = activeVideoId,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = p2pSentinelUrl(infoHash, stream.p2pFileIdx)
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeTorrentInfoHash = infoHash
    activeTorrentFileIdx = stream.p2pFileIdx
    activeTorrentFilename = stream.behaviorHints.filename
    activeSourceFilename = stream.behaviorHints.filename
    activeTorrentTrackers = stream.p2pTrackers
    activeSourceIdentityKey = stream.playerSourceIdentityKey()
    activeStreamTitle = stream.streamLabel
    activeStreamSubtitle = stream.streamSubtitle
    activeProviderName = stream.addonName
    activeProviderAddonId = stream.addonId
    activeStream = stream
    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
    activeSubtitleExtras = SubtitleRequestExtras.from(stream.behaviorHints)
    currentStreamFilename = stream.behaviorHints.filename
    currentStreamInfoHash = stream.infoHash
    activeInitialPositionMs = currentPositionMs
    activeInitialProgressFraction = null
    showSourcesPanel = false
    controlsVisible = true
    PlayerStreamsRepository.pauseSearchForPlayback()
}

internal fun PlayerScreenRuntime.switchToP2pEpisodeStream(
    stream: StreamItem,
    episode: MetaVideo,
    isAutoPlay: Boolean = false,
) {
    val infoHash = stream.p2pInfoHash ?: return
    if (!P2pSettingsRepository.isVisible) return
    if (!P2pSettingsRepository.uiState.value.p2pEnabled) {
        pendingP2pSwitch = PendingPlayerP2pSwitch(stream = stream, episode = episode, isAutoPlay = isAutoPlay)
        return
    }
    clearWebEmbedFallback()
    resetEpisodePanelAndNextEpisodeState()
    flushWatchProgress()
    stopActiveP2pStream()
    val epVideoId = episode.id
    val resume = resolveEpisodeResume(epVideoId, episode)
    saveP2pStreamForReuse(
        stream = stream,
        videoId = epVideoId,
        season = episode.season,
        episode = episode.episode,
    )
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = p2pSentinelUrl(infoHash, stream.p2pFileIdx)
    // A torrent is never an auto-selected source here, so the episode has nothing to fall back from.
    restartSourceFallback(activeSourceUrl, autoSelection = null)
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeTorrentInfoHash = infoHash
    activeTorrentFileIdx = stream.p2pFileIdx
    activeTorrentFilename = stream.behaviorHints.filename
    activeTorrentTrackers = stream.p2pTrackers
    activeSubtitleExtras = SubtitleRequestExtras.from(stream.behaviorHints)
    applyEpisodeStreamMetadata(stream, episode, resume)
}

internal fun PlayerScreenRuntime.switchToSource(
    stream: StreamItem,
    saveForReuse: Boolean = true,
) {
    if (prepareServerForPlayer(stream) { switchToSource(it) }) return
    // The resolved URL expires after a few hours, so it isn't kept for reuse.
    if (resolveYouTubeForPlayer(stream) { switchToSource(it, saveForReuse = false) }) return
    if (
        resolveDebridForPlayer(
            stream = stream,
            season = activeSeasonNumber,
            episode = activeEpisodeNumber,
            onResolved = { switchToSource(it) },
            onStale = {
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
        )
    ) return
    clearWebEmbedFallback()
    if (isP2pStream(stream)) {
        switchToP2pSourceStream(stream)
        return
    }
    val pendingEmbedUrl = stream.playableDirectUrl?.takeIf { VortexMediaExtractor.isResolvableUrl(it) }
    if (pendingEmbedUrl != null) {
        vortexExtractionJob?.cancel()
        showSourcesPanel = false
        controlsVisible = true
        errorMessage = null
        isExtractingStream = true
        extractionServerLabel = stream.streamLabel
        activeVortexEmbedUrl = pendingEmbedUrl
        triedVortexServers.clear()
        triedAnilumosUrls.clear()
        vortexExtractionJob = scope.launch {
            val resolution = try {
                resolveVortexOrAnilumosWithBackup(
                    requestedUrl = pendingEmbedUrl,
                    requestedLabel = stream.streamLabel,
                    preferRequestedFirst = true,
                    allowWebFallback = true,
                    onStatusUpdate = { label -> extractionServerLabel = label },
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                VortexBackupResolution.Failed("Could not extract a direct video stream from ${stream.streamLabel}.")
            }
            when (resolution) {
                is VortexBackupResolution.Extracted -> {
                    isExtractingStream = false
                    extractionServerLabel = null
                    activeVortexEmbedUrl = resolution.media.embedUrl
                    resolution.switchedFromLabel?.let { fromLabel ->
                        NuvioToastController.show("$fromLabel unavailable — playing ${resolution.serverLabel}")
                    }
                    val resolvedStream = VortexMediaExtractor.applyExtractedMedia(stream, resolution.media)
                    switchToSource(resolvedStream, saveForReuse = false)
                }
                is VortexBackupResolution.WebEmbedFallback -> {
                    activateWebEmbedFallback(
                        embedUrl = resolution.embedUrl,
                        serverLabel = resolution.serverLabel,
                    )
                }
                is VortexBackupResolution.Failed -> {
                    isExtractingStream = false
                    extractionServerLabel = null
                    errorMessage = resolution.message
                    controlsVisible = true
                }
            }
        }
        return
    }
    if (openExternalSourceUrl(stream)) return
    val url = stream.playableDirectUrl ?: return
    val sourceIdentityKey = stream.playerSourceIdentityKey()
    if (url == activeSourceUrl) {
        activeSourceIdentityKey = sourceIdentityKey ?: activeSourceIdentityKey
        return
    }
    val currentPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
    flushWatchProgress()
    stopActiveP2pStream()
    val currentVideoId = activeVideoId
    if (saveForReuse && playerSettingsUiState.streamReuseLastLinkEnabled && currentVideoId != null) {
        saveDirectStreamForReuse(stream, url, currentVideoId, activeSeasonNumber, activeEpisodeNumber)
    }
    activeVortexEmbedUrl = stream.externalUrl?.takeIf { VortexMediaExtractor.isResolvableUrl(it) } ?: activeVortexEmbedUrl
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = url
    activeSourceAudioUrl = null
    activeSourceHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request)
    activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response)
    activeStreamType = stream.streamType
    activeSourceIdentityKey = sourceIdentityKey
    activeSourceFilename = stream.behaviorHints.filename
    activeStreamTitle = stream.streamLabel
    activeStreamSubtitle = stream.streamSubtitle
    activeStreamFilename = streamPlaybackFilename(stream, url)
    activeProviderName = stream.addonName
    activeProviderAddonId = stream.addonId
    activeStream = stream
    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
    activeSubtitleExtras = SubtitleRequestExtras.from(stream.behaviorHints, url)
    currentStreamFilename = stream.behaviorHints.filename
    currentStreamInfoHash = stream.infoHash
    activeInitialPositionMs = currentPositionMs
    activeInitialProgressFraction = null
    showSourcesPanel = false
    controlsVisible = true
    PlayerStreamsRepository.pauseSearchForPlayback()
}

/**
 * @param autoSelection non-null when auto source selection picked [stream]: the sources it tried
 * before it, so the fallback to the next matching source goes on from them.
 */
internal fun PlayerScreenRuntime.switchToEpisodeStream(
    stream: StreamItem,
    episode: MetaVideo,
    serverResumeMs: Long? = null,
    saveForReuse: Boolean = true,
    autoSelection: PlayerSourceFallbackTried? = null,
) {
    if (
        prepareServerForPlayer(stream) {
            switchToEpisodeStream(
                it,
                episode,
                serverResumeMs = newerServerResumeMs(it, episode),
                autoSelection = autoSelection,
            )
        }
    ) return
    if (
        resolveYouTubeForPlayer(stream) { resolved ->
            switchToEpisodeStream(resolved, episode, saveForReuse = false, autoSelection = autoSelection)
        }
    ) return
    if (
        resolveDebridForPlayer(
            stream = stream,
            season = episode.season,
            episode = episode.episode,
            onResolved = { resolvedStream -> switchToEpisodeStream(resolvedStream, episode, autoSelection = autoSelection) },
            onStale = {
                PlayerStreamsRepository.loadEpisodeStreams(
                    type = contentType ?: parentMetaType,
                    videoId = episode.id,
                    season = episode.season,
                    episode = episode.episode,
                    forceRefresh = true,
                )
            },
        )
    ) return
    clearWebEmbedFallback()
    if (isP2pStream(stream)) {
        switchToP2pEpisodeStream(stream, episode)
        return
    }
    val pendingEmbedUrl = stream.playableDirectUrl?.takeIf { VortexMediaExtractor.isResolvableUrl(it) }
    if (pendingEmbedUrl != null) {
        vortexExtractionJob?.cancel()
        resetEpisodePanelAndNextEpisodeState()
        errorMessage = null
        isExtractingStream = true
        extractionServerLabel = buildString {
            append(stream.streamLabel)
            episode.episode?.let { append(" · Ep $it") }
        }
        activeVortexEmbedUrl = pendingEmbedUrl
        triedVortexServers.clear()
        triedAnilumosUrls.clear()
        vortexExtractionJob = scope.launch {
            val epSuffix = episode.episode?.let { " · Ep $it" }.orEmpty()
            val resolution = try {
                resolveVortexOrAnilumosWithBackup(
                    requestedUrl = pendingEmbedUrl,
                    requestedLabel = stream.streamLabel,
                    videoIdHint = episode.id,
                    seasonHint = episode.season,
                    episodeHint = episode.episode,
                    preferRequestedFirst = true,
                    allowWebFallback = true,
                    onStatusUpdate = { label -> extractionServerLabel = "$label$epSuffix" },
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                VortexBackupResolution.Failed("Could not extract a direct video stream from ${stream.streamLabel}.")
            }
            when (resolution) {
                is VortexBackupResolution.Extracted -> {
                    isExtractingStream = false
                    extractionServerLabel = null
                    activeVortexEmbedUrl = resolution.media.embedUrl
                    resolution.switchedFromLabel?.let { fromLabel ->
                        NuvioToastController.show("$fromLabel unavailable — playing ${resolution.serverLabel}")
                    }
                    val resolvedStream = VortexMediaExtractor.applyExtractedMedia(stream, resolution.media)
                    switchToEpisodeStream(resolvedStream, episode, saveForReuse = false, autoSelection = autoSelection)
                }
                is VortexBackupResolution.WebEmbedFallback -> {
                    val resume = resolveEpisodeResume(episode.id, episode)
                    applyEpisodeStreamMetadata(stream, episode, resume)
                    activateWebEmbedFallback(
                        embedUrl = resolution.embedUrl,
                        serverLabel = resolution.serverLabel,
                    )
                }
                is VortexBackupResolution.Failed -> {
                    isExtractingStream = false
                    extractionServerLabel = null
                    val resume = resolveEpisodeResume(episode.id, episode)
                    applyEpisodeStreamMetadata(stream, episode, resume)
                    errorMessage = resolution.message
                    controlsVisible = true
                }
            }
        }
        return
    }
    if (openExternalSourceUrl(stream)) return
    val url = stream.playableDirectUrl ?: return
    resetEpisodePanelAndNextEpisodeState()
    flushWatchProgress()
    stopActiveP2pStream()
    activeVortexEmbedUrl = stream.externalUrl?.takeIf { VortexMediaExtractor.isResolvableUrl(it) }
    triedVortexServers.clear()
    triedAnilumosUrls.clear()
    val epVideoId = episode.id
    val resume = serverResumeMs?.let { EpisodeResume(positionMs = it, fraction = null) } ?: resolveEpisodeResume(epVideoId, episode)
    if (saveForReuse && playerSettingsUiState.streamReuseLastLinkEnabled) {
        saveDirectStreamForReuse(stream, url, epVideoId, episode.season, episode.episode)
    }
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = url
    restartSourceFallback(url, autoSelection)
    activeSourceAudioUrl = null
    activeSourceHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request)
    activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response)
    activeStreamType = stream.streamType
    activeSubtitleExtras = SubtitleRequestExtras.from(stream.behaviorHints, url)
    applyEpisodeStreamMetadata(stream, episode, resume)
}

internal fun PlayerScreenRuntime.switchToDownloadedEpisode(downloadItem: DownloadItem, episode: MetaVideo) {
    val localFileUri = DownloadsRepository.playableLocalFileUri(downloadItem) ?: return
    clearWebEmbedFallback()
    resetEpisodePanelAndNextEpisodeState()
    flushWatchProgress()
    stopActiveP2pStream()

    val fallbackVideoId = buildPlaybackVideoId(
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
        fallbackVideoId = episode.id,
    )
    val resolvedVideoId = episode.id.takeIf { it.isNotBlank() } ?: fallbackVideoId
    val epEntry = WatchProgressRepository.progressForVideo(
        videoId = resolvedVideoId,
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
    )
        ?.takeIf { !it.isCompleted }
    val epResumeFraction = epEntry?.progressPercent
        ?.takeIf { it > 0f }
        ?.let { (it / 100f).coerceIn(0f, 1f) }
    val epResumePositionMs = epEntry?.lastPositionMs?.takeIf { it > 0L } ?: 0L

    externalSubtitles = DownloadSubtitles.localSubtitles(localFileUri)
    activeSourceUrl = localFileUri
    restartSourceFallback(activeSourceUrl, autoSelection = null)
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeSourceIdentityKey = null
    activeSourceFilename = null
    activeStreamTitle = downloadItem.streamTitle.ifBlank {
        episode.title.ifBlank { title }
    }
    activeStreamSubtitle = downloadItem.streamSubtitle
    activeStreamFilename = sourceFilenameFromUrl(localFileUri)
    activeProviderName = downloadItem.providerName.ifBlank { downloadedLabel }
    activeProviderAddonId = downloadItem.providerAddonId
    activeStream = null
    currentStreamBingeGroup = null
    activeSubtitleExtras = null
    currentStreamFilename = null
    currentStreamInfoHash = null
    activeSeasonNumber = episode.season
    activeEpisodeNumber = episode.episode
    activeEpisodeTitle = episode.title
    activeEpisodeThumbnail = episode.thumbnail
    activePauseDescription = episode.overview
    activeVideoId = resolvedVideoId
    activeInitialPositionMs = epResumePositionMs
    activeInitialProgressFraction = epResumeFraction
    controlsVisible = true
}

internal fun PlayerScreenRuntime.playNextEpisode(automatic: Boolean = false) {
    if (nextEpisodeAutoPlaySearching || nextEpisodeAutoPlayCountdown != null) return
    nextEpisodePreloadTriggered = false
    val playbackKey = activePlaybackKey
    val nextVideoId = nextEpisodeInfo?.takeIf { it.hasAired }?.videoId ?: return
    fun isCurrentRequest(): Boolean = playbackKey == activePlaybackKey &&
        nextEpisodeInfo?.videoId == nextVideoId &&
        (!automatic || (
            playerSettingsUiState.streamAutoPlayNextEpisodeEnabled &&
                !nextEpisodeCardDismissed && isAtNextEpisodeThreshold()
            ))
    if (!isCurrentRequest()) return
    nextEpisodeAutoPlayAutomatic = automatic

    scope.launchPlayerNextEpisodeAutoPlay(
        previousJob = nextEpisodeAutoPlayJob,
        nextEpisodeInfo = nextEpisodeInfo,
        allEpisodes = playerMetaVideos,
        parentMetaId = parentMetaId,
        parentMetaType = parentMetaType,
        contentType = contentType,
        settings = playerSettingsUiState,
        currentStreamBingeGroup = currentStreamBingeGroup,
        onDownloadedEpisodeSelected = { item, episode ->
            if (isCurrentRequest()) switchToDownloadedEpisode(item, episode)
        },
        onEpisodeStreamSelected = { stream, episode, tried ->
            // Auto source selection picked it, whether or not the user asked for the episode.
            if (isCurrentRequest()) switchToEpisodeStream(stream, episode, autoSelection = tried)
        },
        onManualSelectionRequired = { nextVideo ->
            if (isCurrentRequest()) {
                nextEpisodeCardDismissed = true
                episodeStreamsPanelState = EpisodeStreamsPanelState(
                    showStreams = true,
                    selectedEpisode = nextVideo,
                )
                showEpisodesPanel = true
            }
        },
        onSearchingChanged = {
            if (playbackKey == activePlaybackKey) nextEpisodeAutoPlaySearching = it
        },
        onSourceNameChanged = {
            if (playbackKey == activePlaybackKey) nextEpisodeAutoPlaySourceName = it
        },
        onCountdownChanged = {
            if (playbackKey == activePlaybackKey) nextEpisodeAutoPlayCountdown = it
        },
        onNextEpisodeCardVisibleChanged = {
            if (playbackKey == activePlaybackKey) showNextEpisodeCard = it
        },
    )?.let { job ->
        nextEpisodeAutoPlayJob = job
    }
}

internal fun PlayerScreenRuntime.openSourcesPanel() {
    val vid = activeVideoId ?: return
    PlayerStreamsRepository.loadSources(
        type = contentType ?: parentMetaType,
        videoId = vid,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )
    showSourcesPanel = true
    showEpisodesPanel = false
    showChaptersPanel = false
    controlsVisible = false
}

internal fun PlayerScreenRuntime.openEpisodesPanel() {
    if (playerMetaVideos.isEmpty()) {
        scope.launch {
            playerMetaVideos = MetaDetailsRepository.fetch(parentMetaType, parentMetaId)?.videos ?: emptyList()
        }
    }
    showEpisodesPanel = true
    showSourcesPanel = false
    showChaptersPanel = false
    controlsVisible = false
}

private data class EpisodeResume(val positionMs: Long, val fraction: Float?)

private fun PlayerScreenRuntime.resetEpisodePanelAndNextEpisodeState() {
    showNextEpisodeCard = false
    showSourcesPanel = false
    showEpisodesPanel = false
    showChaptersPanel = false
    episodeStreamsPanelState = EpisodeStreamsPanelState()
    cancelNextEpisodeAutoPlay()
    PlayerStreamsRepository.clearEpisodeStreams()
}

private fun PlayerScreenRuntime.episodeProgress(epVideoId: String, episode: MetaVideo): WatchProgressEntry? {
    val epResumeVideoId = buildPlaybackVideoId(
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
        fallbackVideoId = epVideoId,
    )
    return WatchProgressRepository.progressForVideo(
        videoId = epVideoId.takeIf { it.isNotBlank() } ?: epResumeVideoId,
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
    )
}

private suspend fun PlayerScreenRuntime.newerServerResumeMs(stream: StreamItem, episode: MetaVideo): Long? =
    ServerPlayback.newerResumePositionMs(stream.playableDirectUrl, episodeProgress(episode.id, episode)?.lastUpdatedEpochMs)

private fun PlayerScreenRuntime.resolveEpisodeResume(epVideoId: String, episode: MetaVideo): EpisodeResume {
    val epEntry = episodeProgress(epVideoId, episode)?.takeIf { !it.isCompleted }
    val epResumeFraction = epEntry?.progressPercent
        ?.takeIf { it > 0f }
        ?.let { (it / 100f).coerceIn(0f, 1f) }
    val epResumePositionMs = epEntry?.lastPositionMs?.takeIf { it > 0L } ?: 0L
    return EpisodeResume(positionMs = epResumePositionMs, fraction = epResumeFraction)
}

private fun PlayerScreenRuntime.applyEpisodeStreamMetadata(
    stream: StreamItem,
    episode: MetaVideo,
    resume: EpisodeResume,
) {
    activeSourceIdentityKey = stream.playerSourceIdentityKey()
    activeSourceFilename = stream.behaviorHints.filename
    activeStreamTitle = stream.streamLabel
    activeStreamSubtitle = stream.streamSubtitle
    activeStreamFilename = streamPlaybackFilename(stream, stream.playableDirectUrl.orEmpty())
    activeProviderName = stream.addonName
    activeProviderAddonId = stream.addonId
    activeStream = stream
    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
    currentStreamFilename = stream.behaviorHints.filename
    currentStreamInfoHash = stream.infoHash
    activeSeasonNumber = episode.season
    activeEpisodeNumber = episode.episode
    activeEpisodeTitle = episode.title
    activeEpisodeThumbnail = episode.thumbnail
    activePauseDescription = episode.overview
    activeVideoId = episode.id
    activeInitialPositionMs = resume.positionMs
    activeInitialProgressFraction = resume.fraction
    controlsVisible = true
}

internal fun PlayerScreenRuntime.saveDirectStreamForReuse(
    stream: StreamItem,
    url: String,
    videoId: String,
    season: Int?,
    episode: Int?,
) {
    if (stream.serverTarget != null) return
    val cacheKey = StreamLinkCacheRepository.contentKey(
        type = contentType ?: parentMetaType,
        videoId = videoId,
        parentMetaId = parentMetaId,
        season = season,
        episode = episode,
    )
    StreamLinkCacheRepository.save(
        contentKey = cacheKey,
        url = url,
        streamName = stream.streamLabel,
        addonName = stream.addonName,
        addonId = stream.addonId,
        requestHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request),
        responseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
        filename = stream.behaviorHints.filename,
        videoHash = stream.behaviorHints.videoHash,
        videoSize = stream.behaviorHints.videoSize,
        infoHash = stream.p2pInfoHash,
        fileIdx = stream.p2pFileIdx,
        bingeGroup = stream.behaviorHints.bingeGroup,
        streamType = stream.streamType,
        contentLanguage = contentLanguage,
        addonLogo = stream.addonLogo,
        streamDescription = stream.streamSubtitle,
    )
}

internal fun PlayerScreenRuntime.switchToPortraitEpisode(episode: MetaVideo) {
    val mediaType = contentType ?: parentMetaType
    val instantStreams = VortexBuiltinAddon.instantStreamsFor(
        type = mediaType,
        videoId = episode.id,
        parentMetaId = parentMetaId,
        season = episode.season,
        episode = episode.episode,
        embedUrlHint = activeVortexEmbedUrl,
    )
    if (instantStreams.isNotEmpty()) {
        val currentBinge = currentStreamBingeGroup.orEmpty().lowercase()
        val currentName = activeStreamTitle.lowercase()
        val preferDub = "dub" in currentBinge || "dub" in currentName || "server 2" in currentName || "server 4" in currentName
        val preferred = instantStreams.firstOrNull { candidate ->
            val bg = candidate.behaviorHints.bingeGroup.orEmpty().lowercase()
            if (currentBinge.isNotBlank() && bg == currentBinge) return@firstOrNull true
            val srv = candidate.playableDirectUrl?.let(VortexServers::serverFor)
            srv != null && srv == VortexServers.preferredServer
        } ?: instantStreams.firstOrNull { candidate ->
            val url = candidate.playableDirectUrl.orEmpty()
            AnilumosAnimeBackend.isAnilumosStreamUrl(url) &&
                (if (preferDub) url.endsWith("/dub") else url.endsWith("/sub"))
        } ?: instantStreams.first()
        switchToEpisodeStream(preferred, episode)
        return
    }
    PlayerStreamsRepository.loadEpisodeStreams(
        type = mediaType,
        videoId = episode.id,
        season = episode.season,
        episode = episode.episode,
    )
    episodeStreamsPanelState = EpisodeStreamsPanelState(showStreams = true, selectedEpisode = episode)
    showEpisodesPanel = true
    showSourcesPanel = false
    controlsVisible = false
}
