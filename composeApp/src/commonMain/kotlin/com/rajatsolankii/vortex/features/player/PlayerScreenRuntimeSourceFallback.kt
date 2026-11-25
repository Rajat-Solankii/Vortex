package com.rajatsolankii.vortex.features.player

import co.touchlab.kermit.Logger
import com.rajatsolankii.vortex.core.ui.NuvioToastController
import com.rajatsolankii.vortex.features.addons.AddonRepository
import com.rajatsolankii.vortex.features.addons.enabledAddons
import com.rajatsolankii.vortex.features.debrid.DebridSettingsRepository
import com.rajatsolankii.vortex.features.debrid.DirectDebridPlayableResult
import com.rajatsolankii.vortex.features.debrid.DirectDebridPlaybackResolver
import com.rajatsolankii.vortex.features.streams.StreamAutoPlaySelector
import com.rajatsolankii.vortex.features.streams.StreamItem
import com.rajatsolankii.vortex.features.vortex.AnilumosAnimeBackend
import com.rajatsolankii.vortex.features.vortex.VortexBuiltinAddon
import com.rajatsolankii.vortex.features.vortex.VortexExtractedMedia
import com.rajatsolankii.vortex.features.vortex.VortexMediaExtractor
import com.rajatsolankii.vortex.features.vortex.VortexServer
import com.rajatsolankii.vortex.features.vortex.VortexServers
import com.rajatsolankii.vortex.isIos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SOURCE_FALLBACK_POLL_COUNT = 30
private const val SOURCE_FALLBACK_POLL_INTERVAL_MS = 500L

private val log = Logger.withTag("PlayerSourceFallback")

/**
 * Plays [stream] at [url] in place of the current source. Before playback has started the
 * pending resume position is kept, otherwise playback continues from the current position.
 */
internal fun PlayerScreenRuntime.playReplacementSource(stream: StreamItem, url: String, videoId: String) {
    isExtractingStream = false
    extractionServerLabel = null
    if (playerSettingsUiState.streamReuseLastLinkEnabled) {
        saveDirectStreamForReuse(stream, url, videoId, activeSeasonNumber, activeEpisodeNumber)
    }
    if (activeSourceUrl == autoSelectedSourceUrl) autoSelectedSourceUrl = url
    if (initialLoadCompleted) {
        activeInitialPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
        activeInitialProgressFraction = null
    }
    flushWatchProgress()
    stopActiveP2pStream()
    externalSubtitles = stream.externalSubtitles
    activeSourceUrl = url
    activeSourceAudioUrl = null
    activeSourceHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request)
    activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response)
    activeStreamType = stream.streamType
    activeSourceIdentityKey = stream.playerSourceIdentityKey() ?: activeSourceIdentityKey
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
    showSourcesPanel = false
    controlsVisible = true
}

/** Shows [message] as the playback error unless the next matching source is tried instead. */
internal fun PlayerScreenRuntime.showErrorUnlessSourceFallback(message: String?) {
    if (tryFallbackToNextVortexServerAfterError(message)) return
    if (tryFallbackToNextSourceAfterError(message)) return
    errorMessage = message
    controlsVisible = !playerControlsLocked
}

internal sealed interface VortexBackupResolution {
    data class Extracted(
        val media: VortexExtractedMedia,
        val serverLabel: String,
        val switchedFromLabel: String? = null,
    ) : VortexBackupResolution

    data class WebEmbedFallback(
        val embedUrl: String,
        val serverLabel: String,
    ) : VortexBackupResolution

    data class Failed(val message: String) : VortexBackupResolution
}

internal fun PlayerScreenRuntime.clearWebEmbedFallback() {
    webFallbackEmbedUrl = null
    webFallbackLoading = false
    webFallbackBlockedCount = 0
}

internal fun PlayerScreenRuntime.activateWebEmbedFallback(
    embedUrl: String,
    serverLabel: String,
    showToast: Boolean = true,
) {
    stopActiveP2pStream()
    errorMessage = null
    isExtractingStream = false
    extractionServerLabel = null
    playerController = null
    playerControllerSourceUrl = null
    playbackSnapshot = PlayerPlaybackSnapshot()
    playbackSnapshotKey = null
    activeVortexEmbedUrl = embedUrl
    activeSourceUrl = embedUrl
    activeSourceAudioUrl = null
    activeSourceHeaders = emptyMap()
    activeSourceResponseHeaders = emptyMap()
    activeStreamType = null
    activeStreamTitle = serverLabel
    activeStreamSubtitle = "Web Player backup"
    activeProviderName = VortexBuiltinAddon.NAME
    activeProviderAddonId = VortexBuiltinAddon.ADDON_ID
    activeStream = StreamItem(
        name = serverLabel,
        title = serverLabel,
        description = "Web Player backup",
        url = embedUrl,
        externalUrl = embedUrl,
        addonName = VortexBuiltinAddon.NAME,
        addonId = VortexBuiltinAddon.ADDON_ID,
    )
    webFallbackBlockedCount = 0
    webFallbackLoading = true
    webFallbackEmbedUrl = embedUrl
    showSourcesPanel = false
    controlsVisible = false
    if (showToast) {
        NuvioToastController.show("Direct stream unavailable — playing $serverLabel in Web Player")
    }
}

internal suspend fun PlayerScreenRuntime.resolveVortexOrAnilumosWithBackup(
    requestedUrl: String,
    requestedLabel: String,
    videoIdHint: String? = activeVideoId ?: parentMetaId,
    seasonHint: Int? = activeSeasonNumber,
    episodeHint: Int? = activeEpisodeNumber,
    preferRequestedFirst: Boolean = true,
    allowWebFallback: Boolean = true,
    onStatusUpdate: (String) -> Unit = {},
): VortexBackupResolution {
    if (requestedUrl == VortexMediaExtractor.PENDING_EXTRACT_URL) {
        val anilistTarget = AnilumosAnimeBackend.parseAnilistTarget(
            videoId = videoIdHint,
            parentMetaId = parentMetaId,
            episode = episodeHint,
        )
        if (anilistTarget != null) {
            activeVortexEmbedUrl = AnilumosAnimeBackend.autoStreamUrl(
                anilistTarget.anilistId,
                anilistTarget.episode,
            )
            val media = AnilumosAnimeBackend.extractBestForTarget(
                anilistId = anilistTarget.anilistId,
                episode = anilistTarget.episode,
                failedUrls = triedAnilumosUrls,
            )
            return if (media != null) {
                VortexBackupResolution.Extracted(
                    media = media,
                    serverLabel = media.displayNameOverride ?: "Vortex Anime Server 1",
                )
            } else {
                VortexBackupResolution.Failed(
                    "Could not extract a playable stream across anime servers. Try retrying or choosing another server below.",
                )
            }
        }
        val target = VortexServers.resolveTarget(
            videoId = videoIdHint.orEmpty(),
            parentMetaId = parentMetaId,
            type = contentType ?: parentMetaType,
            season = seasonHint,
            episode = episodeHint,
        ) ?: return VortexBackupResolution.Failed("No playable stream target found for this title.")
        val preferred = VortexServers.preferredSource(target)
        activeVortexEmbedUrl = preferred.url
        onStatusUpdate(preferred.server.displayName)
        val extracted = VortexMediaExtractor.extractBestForTarget(
            target = target,
            preferredServer = VortexServers.preferredServer,
            excludedServers = triedVortexServers,
        )
        return when {
            extracted != null -> VortexBackupResolution.Extracted(
                media = extracted,
                serverLabel = extracted.displayNameOverride
                    ?: extracted.server?.displayName
                    ?: preferred.server.displayName,
            )
            allowWebFallback -> VortexBackupResolution.WebEmbedFallback(
                embedUrl = preferred.url,
                serverLabel = preferred.server.displayName,
            )
            else -> VortexBackupResolution.Failed(
                "Could not extract a direct video stream from ${preferred.server.displayName} or backup servers.",
            )
        }
    }

    if (AnilumosAnimeBackend.isAnilumosStreamUrl(requestedUrl)) {
        val spec = AnilumosAnimeBackend.parseStreamUrl(requestedUrl)
        val direct = if (spec == null || spec.provider != "auto") {
            AnilumosAnimeBackend.extractUrl(requestedUrl)
        } else {
            null
        }
        if (direct != null) {
            return VortexBackupResolution.Extracted(
                media = direct,
                serverLabel = direct.displayNameOverride ?: requestedLabel,
            )
        }
        triedAnilumosUrls += requestedUrl
        if (spec != null) {
            onStatusUpdate("Backup Anime Server...")
            val backup = AnilumosAnimeBackend.extractBestForTarget(
                anilistId = spec.anilistId,
                episode = spec.episode,
                preferDub = spec.audio == "dub",
                failedUrls = triedAnilumosUrls,
            )
            if (backup != null) {
                return VortexBackupResolution.Extracted(
                    media = backup,
                    serverLabel = backup.displayNameOverride ?: "Vortex Anime",
                    switchedFromLabel = requestedLabel.takeIf { spec.provider != "auto" },
                )
            }
        }
        return VortexBackupResolution.Failed(
            "Could not extract a direct stream from $requestedLabel or backup anime servers.",
        )
    }

    val target = VortexServers.targetForUrl(requestedUrl)
        ?: VortexServers.resolveTarget(
            videoId = videoIdHint.orEmpty(),
            parentMetaId = parentMetaId,
            type = contentType ?: parentMetaType,
            season = seasonHint,
            episode = episodeHint,
        )
    val requestedServer = VortexServers.serverFor(requestedUrl)

    if (preferRequestedFirst) {
        val direct = try {
            VortexMediaExtractor.extract(requestedUrl)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }
        if (direct != null) {
            return VortexBackupResolution.Extracted(
                media = direct,
                serverLabel = direct.displayNameOverride ?: direct.server?.displayName ?: requestedLabel,
            )
        }
        requestedServer?.let(triedVortexServers::add)
        VortexMediaExtractor.invalidate(requestedUrl)
        if (target != null) {
            onStatusUpdate("Trying backup server...")
            val backup = VortexMediaExtractor.extractBestForTarget(
                target = target,
                preferredServer = VortexServers.preferredServer,
                excludedServers = triedVortexServers,
            )
            if (backup != null) {
                val winnerLabel = backup.displayNameOverride ?: backup.server?.displayName ?: "Vortex Server"
                return VortexBackupResolution.Extracted(
                    media = backup,
                    serverLabel = winnerLabel,
                    switchedFromLabel = requestedLabel,
                )
            }
        }
    } else if (target != null) {
        val raced = VortexMediaExtractor.extractBestForTarget(
            target = target,
            preferredServer = requestedServer ?: VortexServers.preferredServer,
            excludedServers = triedVortexServers,
        )
        if (raced != null) {
            val winnerLabel = raced.displayNameOverride ?: raced.server?.displayName ?: requestedLabel
            return VortexBackupResolution.Extracted(
                media = raced,
                serverLabel = winnerLabel,
                switchedFromLabel = requestedServer?.takeIf { raced.server != null && raced.server != it }?.displayName,
            )
        }
    } else {
        val single = VortexMediaExtractor.extract(requestedUrl)
        if (single != null) {
            return VortexBackupResolution.Extracted(
                media = single,
                serverLabel = single.displayNameOverride ?: single.server?.displayName ?: requestedLabel,
            )
        }
    }

    if (allowWebFallback && VortexServers.isEmbedUrl(requestedUrl)) {
        return VortexBackupResolution.WebEmbedFallback(
            embedUrl = requestedUrl,
            serverLabel = requestedLabel,
        )
    }

    return VortexBackupResolution.Failed(
        "Could not extract a direct video stream from $requestedLabel. Try another server or Web Player.",
    )
}

internal fun PlayerScreenRuntime.nextBackupServerCandidate(allServers: List<StreamItem>): StreamItem? {
    if (allServers.isEmpty()) return null
    val currentUrl = activeVortexEmbedUrl ?: activeSourceUrl
    val currentIdx = resolveSelectedServerIndex(
        allServers = allServers,
        activeSourceUrl = activeSourceUrl,
        activeVortexEmbedUrl = activeVortexEmbedUrl,
        activeStreamTitle = activeStreamTitle,
    )
    val orderedIndices = if (currentIdx in allServers.indices) {
        (currentIdx + 1 until allServers.size) + (0 until currentIdx)
    } else {
        allServers.indices.toList()
    }
    return orderedIndices
        .asSequence()
        .map { allServers[it] }
        .firstOrNull { candidate ->
            val candidateUrl = candidate.playableDirectUrl ?: candidate.externalUrl ?: return@firstOrNull false
            if (candidateUrl.equals(currentUrl, ignoreCase = true)) return@firstOrNull false
            val server = VortexServers.serverFor(candidateUrl)
            (server == null || server !in triedVortexServers) && candidateUrl !in triedAnilumosUrls
        }
        ?: orderedIndices.asSequence().map { allServers[it] }.firstOrNull { candidate ->
            val candidateUrl = candidate.playableDirectUrl ?: candidate.externalUrl
            candidateUrl != null && !candidateUrl.equals(currentUrl, ignoreCase = true)
        }
}

internal fun PlayerScreenRuntime.resolveWebFallbackEmbedUrl(): String? {
    activeVortexEmbedUrl?.takeIf { VortexServers.isEmbedUrl(it) }?.let { return it }
    activeStream?.externalUrl?.takeIf { VortexServers.isEmbedUrl(it) }?.let { return it }
    activeSourceUrl.takeIf { VortexServers.isEmbedUrl(it) }?.let { return it }
    val vid = activeVideoId ?: parentMetaId
    val isSeriesType = (contentType ?: parentMetaType).let {
        it.equals("series", ignoreCase = true) || it.equals("tv", ignoreCase = true)
    }
    val target = VortexServers.targetFromVideoId(vid, contentType ?: parentMetaType)
        ?: VortexServers.targetFromVideoId(parentMetaId, contentType ?: parentMetaType)?.let { base ->
            val s = activeSeasonNumber
            val e = activeEpisodeNumber
            if (isSeriesType && s != null && e != null) base.copy(season = s, episode = e) else base
        }
        ?: return null
    return VortexServers.preferredSource(target).url
}

internal fun PlayerScreenRuntime.openWebPlayerFallback(): Boolean {
    val embedUrl = resolveWebFallbackEmbedUrl() ?: return false
    val label = VortexServers.serverFor(embedUrl)?.displayName
        ?: activeStreamTitle.ifBlank { VortexServers.preferredServer.displayName }
    activateWebEmbedFallback(embedUrl = embedUrl, serverLabel = label, showToast = false)
    return true
}

internal fun PlayerScreenRuntime.retryCurrentServerExtraction() {
    clearWebEmbedFallback()
    errorMessage = null
    val retryTargetUrl = activeVortexEmbedUrl ?: activeStream?.externalUrl ?: activeSourceUrl
    if (VortexMediaExtractor.isResolvableUrl(retryTargetUrl)) {
        VortexMediaExtractor.invalidate(retryTargetUrl)
        val serverObj = VortexServers.serverFor(retryTargetUrl)
        if (serverObj != null) triedVortexServers.remove(serverObj)
        triedAnilumosUrls.remove(retryTargetUrl)
        val stream = activeStream?.copy(url = retryTargetUrl) ?: StreamItem(
            name = activeStreamTitle.ifBlank { serverObj?.displayName ?: "Vortex Server" },
            title = activeStreamTitle.ifBlank { serverObj?.displayName ?: "Vortex Server" },
            url = retryTargetUrl,
            addonName = activeProviderName,
            addonId = VortexBuiltinAddon.ADDON_ID,
        )
        switchToSource(stream, saveForReuse = false)
        return
    }
    val currentStream = activeStream
    if (currentStream != null) {
        switchToSource(currentStream, saveForReuse = false)
    } else if (activeSourceUrl.isNotBlank()) {
        val current = activeSourceUrl
        activeSourceUrl = ""
        activeSourceUrl = current
    }
}

/**
 * When a Vortex-extracted direct stream fails in ExoPlayer (e.g., 403/dead CDN), invalidates
 * its cache and automatically extracts and switches to the next working Vortex server inside
 * [PlayerScreen], or falls back to the shielded in-player WebView embed player when direct
 * extraction cannot recover.
 */
internal fun PlayerScreenRuntime.tryFallbackToNextVortexServerAfterError(message: String?): Boolean {
    val embedUrl = activeVortexEmbedUrl
        ?: activeStream?.externalUrl?.takeIf { VortexMediaExtractor.isResolvableUrl(it) }
        ?: return false
    if (activeProviderAddonId != null && activeProviderAddonId != VortexBuiltinAddon.ADDON_ID &&
        !activeProviderAddonId!!.contains(VortexBuiltinAddon.ADDON_ID)
    ) {
        return false
    }
    if (vortexExtractionJob?.isActive == true) return true
    if (AnilumosAnimeBackend.isAnilumosStreamUrl(embedUrl)) {
        triedAnilumosUrls += embedUrl
        AnilumosAnimeBackend.invalidateStream(embedUrl)
        val remaining = AnilumosAnimeBackend.fallbackCandidatesFor(embedUrl)
            .filterNot { it in triedAnilumosUrls }
        if (remaining.isEmpty()) return false
        errorMessage = null
        isExtractingStream = true
        extractionServerLabel = remaining.firstOrNull()?.let(AnilumosAnimeBackend::displayNameForStreamUrl)
            ?: "Vortex Anime Server"
        vortexExtractionJob = scope.launch {
            var replacementMedia: VortexExtractedMedia? = null
            for (candidateUrl in remaining) {
                triedAnilumosUrls += candidateUrl
                extractionServerLabel = AnilumosAnimeBackend.displayNameForStreamUrl(candidateUrl)
                    ?: "Vortex Anime Server"
                val resolved = AnilumosAnimeBackend.extractUrl(candidateUrl)
                if (resolved != null) {
                    replacementMedia = resolved
                    break
                }
            }
            if (replacementMedia != null) {
                val replacement = VortexMediaExtractor.toDirectStreamItem(replacementMedia)
                activeVortexEmbedUrl = replacementMedia.embedUrl
                val vid = activeVideoId ?: parentMetaId
                playReplacementSource(replacement, replacementMedia.url, vid)
            } else {
                isExtractingStream = false
                extractionServerLabel = null
                errorMessage = message ?: "Playback failed across Anilumos anime servers"
                controlsVisible = !playerControlsLocked
            }
        }
        return true
    }
    val target = VortexServers.targetForUrl(embedUrl)
        ?: VortexServers.targetFromVideoId(activeVideoId ?: parentMetaId, contentType ?: parentMetaType)
    val alternate = VortexMediaExtractor.consumeAlternate(embedUrl, failedMediaUrl = activeSourceUrl)
    if (alternate != null) {
        errorMessage = null
        val replacement = VortexMediaExtractor.toDirectStreamItem(alternate)
        activeVortexEmbedUrl = alternate.embedUrl
        val vid = activeVideoId ?: parentMetaId
        playReplacementSource(replacement, alternate.url, vid)
        return true
    }
    VortexServers.serverFor(embedUrl)?.let(triedVortexServers::add)
    VortexMediaExtractor.invalidate(embedUrl, failedMediaUrl = activeSourceUrl)

    errorMessage = null
    isExtractingStream = true
    extractionServerLabel = "Switching Vortex Server..."
    vortexExtractionJob = scope.launch {
        val nextExtracted = if (target != null) {
            VortexMediaExtractor.extractBestForTarget(
                target = target,
                preferredServer = VortexServers.preferredServer,
                excludedServers = triedVortexServers,
            )
        } else {
            null
        }
        if (nextExtracted != null) {
            val replacement = VortexMediaExtractor.toDirectStreamItem(nextExtracted)
            activeVortexEmbedUrl = nextExtracted.embedUrl
            val vid = activeVideoId ?: parentMetaId
            playReplacementSource(replacement, nextExtracted.url, vid)
        } else if (VortexServers.isEmbedUrl(embedUrl)) {
            val fallbackLabel = VortexServers.serverFor(embedUrl)?.displayName
                ?: activeStreamTitle.ifBlank { "Vortex Server" }
            activateWebEmbedFallback(embedUrl = embedUrl, serverLabel = fallbackLabel)
        } else {
            isExtractingStream = false
            extractionServerLabel = null
            errorMessage = message ?: "Playback failed across available servers"
            controlsVisible = !playerControlsLocked
        }
    }
    return true
}

/**
 * When the auto-selected source fails before playback starts, quietly plays the next source that
 * auto source selection would pick, up to the configured number of further sources. It never
 * applies to a source the user picked, and it excludes the engine switch on error.
 *
 * Returns `true` when a fallback was started and the error must not be shown yet.
 */
internal fun PlayerScreenRuntime.tryFallbackToNextSourceAfterError(message: String?): Boolean {
    if (sourceFallbackJob?.isActive == true) return true
    val settings = playerSettingsUiState
    if (!settings.streamAutoPlayFallbackActive) return false
    if (!isIos && settings.autoSwitchPlaybackEngineOnError) return false
    if (initialLoadCompleted) return false
    val failedUrl = activeSourceUrl
    if (failedUrl.isBlank() || failedUrl != autoSelectedSourceUrl) return false
    if (sourceFallbackAttempts >= settings.streamAutoPlayFallbackMaxSources) return false
    val videoId = activeVideoId ?: return false

    activeStream?.playerSourceIdentityKey()?.let(sourceFallbackTriedKeys::add)
    val failedInfoHash = activeStream?.p2pInfoHash ?: currentStreamInfoHash
    val failedAddonId = activeProviderAddonId
    val season = activeSeasonNumber
    val episode = activeEpisodeNumber
    errorMessage = null

    sourceFallbackJob = scope.launch {
        try {
            // Joins the shared source search, so results the stream screen already found are reused.
            PlayerStreamsRepository.loadSources(
                type = contentType ?: parentMetaType,
                videoId = videoId,
                season = season,
                episode = episode,
            )
            var polls = 0
            while (sourceFallbackAttempts < playerSettingsUiState.streamAutoPlayFallbackMaxSources) {
                val state = PlayerStreamsRepository.sourceState.value
                val next = nextSourceFallbackCandidate(
                    streams = state.groups.flatMap { it.streams },
                    settings = playerSettingsUiState,
                    triedKeys = sourceFallbackTriedKeys,
                    failedAddonId = failedAddonId,
                    failedInfoHash = failedInfoHash,
                )
                if (next == null) {
                    val searchDone = state.emptyStateReason != null || (!state.isAnyLoading && state.groups.isNotEmpty())
                    if (searchDone || polls >= SOURCE_FALLBACK_POLL_COUNT) break
                    delay(SOURCE_FALLBACK_POLL_INTERVAL_MS)
                    polls++
                    continue
                }
                next.playerSourceIdentityKey()?.let(sourceFallbackTriedKeys::add)
                sourceFallbackAttempts++

                val playable = when (
                    val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(next, season, episode)
                ) {
                    is DirectDebridPlayableResult.Success -> resolved.stream
                    else -> null
                }
                val url = playable?.playableDirectUrl
                if (playable == null || url.isNullOrBlank() || url == failedUrl) {
                    log.d { "Fallback source from ${next.addonName} has no playable link, trying the next one" }
                    continue
                }
                log.i { "Source failed to start, falling back to ${playable.streamLabel} from ${playable.addonName}" }
                playReplacementSource(playable, url, videoId)
                return@launch
            }
        } finally {
            PlayerStreamsRepository.stopSourcesLoading()
        }
        // The last source that failed may have been saved for reuse.
        removeFailedStreamFromCache()
        errorMessage = message
        controlsVisible = !playerControlsLocked
    }
    return true
}

/**
 * The best stream auto source selection would pick from [streams], leaving out the sources
 * already tried and other copies of the torrent that failed.
 */
internal fun nextSourceFallbackCandidate(
    streams: List<StreamItem>,
    settings: PlayerSettingsUiState,
    triedKeys: Set<String>,
    failedAddonId: String?,
    failedInfoHash: String?,
    installedAddonNames: Set<String> = AddonRepository.uiState.value.addons.enabledAddons().map { it.displayTitle }.toSet(),
    debridEnabled: Boolean = DebridSettingsRepository.snapshot().canResolvePlayableLinks,
    activeResolverProviderId: String? = DebridSettingsRepository.snapshot().activeResolverProviderId,
): StreamItem? {
    val untried = streams.filterNot { stream ->
        val key = stream.playerSourceIdentityKey()
        (key != null && key in triedKeys) ||
            (failedInfoHash != null && stream.addonId == failedAddonId &&
                stream.p2pInfoHash.equals(failedInfoHash, ignoreCase = true))
    }
    return StreamAutoPlaySelector.evaluateAutoPlayStream(
        streams = untried,
        mode = settings.streamAutoPlayMode,
        regexPattern = settings.streamAutoPlayRegex,
        source = settings.streamAutoPlaySource,
        installedAddonNames = installedAddonNames,
        selectedAddons = settings.streamAutoPlaySelectedAddons,
        selectedPlugins = settings.streamAutoPlaySelectedPlugins,
        debridEnabled = debridEnabled,
        activeResolverProviderId = activeResolverProviderId,
    ).readyStreams.firstOrNull()
}
