package com.rajatsolankii.vortex.features.vortex

import co.touchlab.kermit.Logger
import com.rajatsolankii.vortex.features.addons.httpRequestRaw
import com.rajatsolankii.vortex.features.streams.StreamBehaviorHints
import com.rajatsolankii.vortex.features.streams.StreamItem
import com.rajatsolankii.vortex.features.streams.StreamProxyHeaders
import com.rajatsolankii.vortex.features.streams.StreamSubtitle
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Direct media stream extracted from one of the Vortex embed pages, ready for native
 * playback in ExoPlayer / libmpv (or an external player) without showing web player controls.
 */
data class VortexExtractedMedia(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<StreamSubtitle> = emptyList(),
    val streamType: String? = null,
    val embedUrl: String,
    val server: VortexServer? = VortexServers.serverFor(embedUrl),
    val displayNameOverride: String? = null,
)

internal expect object VortexPlatformSniffer {
    suspend fun sniff(embedUrl: String, timeoutMs: Long): VortexExtractedMedia?
}

/**
 * Extracts direct playable media URLs (.m3u8 / .mpd / .mp4 / HLS .txt manifests), required
 * request headers (Referer / Origin / User-Agent / Cookie), and sidecar subtitles from Vortex
 * embed servers so playback runs inside the app's own native player controls.
 */
object VortexMediaExtractor {
    private val log = Logger.withTag("VortexExtractor")
    private const val CACHE_TTL_MS = 5 * 60_000L
    private const val MAX_CACHE_ENTRIES = 16
    private const val HTTP_TIMEOUT_MS = 2_800L
    private const val PROXY_FETCH_TIMEOUT_MS = 2_200L
    private const val PROXY_BACKUP_GRACE_MS = 140L
    private const val SNIFFER_HEAD_START_MS = 320L
    private const val RACE_SNIFFER_HEAD_START_MS = 550L
    private const val SNIFFER_TIMEOUT_MS = 5_500L
    private const val TOTAL_SNIFF_BUDGET_MS = 12_000L

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private data class CacheEntry(
        val media: VortexExtractedMedia,
        val storedAt: TimeMark,
    )

    internal data class ProxyServerCandidate(
        val key: String,
        val platform: String,
        val frameUrl: String,
        val priority: Int,
    )

    private val clock = TimeSource.Monotonic
    private val lock = SynchronizedObject()
    private val cache = LinkedHashMap<String, CacheEntry>()
    private val alternatesByEmbedUrl = LinkedHashMap<String, MutableList<VortexExtractedMedia>>()
    private val failedMediaOriginsByEmbedUrl = LinkedHashMap<String, MutableSet<String>>()

    // Exponentially weighted resolve latency + success score per VortexServer (modeled on Anilumos ProviderSpeed).
    private data class ServerSpeedStats(
        val resolveMs: Double,
        val successRate: Double,
        val samples: Int,
    )

    private val speedStatsByServer = LinkedHashMap<VortexServer, ServerSpeedStats>()

    private fun recordServerOutcome(server: VortexServer, succeeded: Boolean, durationMs: Long) {
        synchronized(lock) {
            val prev = speedStatsByServer[server] ?: ServerSpeedStats(
                resolveMs = if (server == VortexServer.SERVER2 || server == VortexServer.SERVER5) 900.0 else 1_800.0,
                successRate = if (server == VortexServer.SERVER2) 0.9 else 0.7,
                samples = 0,
            )
            val alpha = 0.35
            val nextResolve = if (succeeded && durationMs >= 25L) {
                prev.resolveMs + alpha * (durationMs.toDouble() - prev.resolveMs)
            } else {
                prev.resolveMs
            }
            val nextSuccess = prev.successRate + alpha * ((if (succeeded) 1.0 else 0.0) - prev.successRate)
            speedStatsByServer[server] = ServerSpeedStats(
                resolveMs = nextResolve,
                successRate = nextSuccess,
                samples = prev.samples + 1,
            )
        }
    }

    private fun serverScore(server: VortexServer): Double = synchronized(lock) {
        val s = speedStatsByServer[server] ?: return@synchronized when (server) {
            VortexServer.SERVER2 -> 600.0
            VortexServer.SERVER5 -> 1_100.0
            VortexServer.SERVER3 -> 1_400.0
            VortexServer.SERVER6 -> 1_500.0
            VortexServer.SERVER1 -> 1_700.0
            VortexServer.SERVER7 -> 2_100.0
            VortexServer.SERVER4 -> 4_500.0
        }
        s.resolveMs + (1.0 - s.successRate) * 3_500.0
    }

    const val PENDING_EXTRACT_URL = "vortex://pending-extract"

    fun isResolvableUrl(url: String?): Boolean =
        url == PENDING_EXTRACT_URL || VortexServers.isEmbedUrl(url) || AnilumosAnimeBackend.isAnilumosStreamUrl(url)

    fun invalidate(embedUrl: String?, failedMediaUrl: String? = null) {
        if (embedUrl.isNullOrBlank()) return
        if (AnilumosAnimeBackend.isAnilumosStreamUrl(embedUrl)) {
            AnilumosAnimeBackend.invalidateStream(embedUrl)
        }
        val failedOrigin = failedMediaUrl?.takeIf { it.isNotBlank() }?.let(::originOf)?.lowercase()
        synchronized(lock) {
            cache.remove(embedUrl)
            if (failedOrigin != null) {
                failedMediaOriginsByEmbedUrl.getOrPut(embedUrl) { LinkedHashSet() }.add(failedOrigin)
                alternatesByEmbedUrl[embedUrl]?.removeAll { alt ->
                    originOf(alt.url)?.equals(failedOrigin, ignoreCase = true) == true
                }
            }
        }
    }

    fun consumeAlternate(embedUrl: String?, failedMediaUrl: String? = null): VortexExtractedMedia? {
        if (embedUrl.isNullOrBlank()) return null
        val failedOrigin = failedMediaUrl?.takeIf { it.isNotBlank() }?.let(::originOf)?.lowercase()
        return synchronized(lock) {
            cache.remove(embedUrl)
            if (failedOrigin != null) {
                failedMediaOriginsByEmbedUrl.getOrPut(embedUrl) { LinkedHashSet() }.add(failedOrigin)
            }
            val failedOrigins = failedMediaOriginsByEmbedUrl[embedUrl].orEmpty()
            val queue = alternatesByEmbedUrl[embedUrl] ?: return@synchronized null
            while (queue.isNotEmpty()) {
                val next = queue.removeAt(0)
                val nextOrigin = originOf(next.url)?.lowercase()
                if (nextOrigin == null || nextOrigin !in failedOrigins) {
                    cache[embedUrl] = CacheEntry(next, clock.markNow())
                    return@synchronized next
                }
            }
            alternatesByEmbedUrl.remove(embedUrl)
            null
        }
    }

    fun clearCache() {
        synchronized(lock) {
            cache.clear()
            alternatesByEmbedUrl.clear()
            failedMediaOriginsByEmbedUrl.clear()
        }
    }

    private fun failedOriginsFor(embedUrl: String): Set<String> = synchronized(lock) {
        failedMediaOriginsByEmbedUrl[embedUrl]?.toSet().orEmpty()
    }

    private fun rememberAlternates(embedUrl: String, alternates: List<VortexExtractedMedia>) {
        synchronized(lock) {
            if (alternates.isEmpty()) {
                alternatesByEmbedUrl.remove(embedUrl)
            } else {
                alternatesByEmbedUrl[embedUrl] = alternates.toMutableList()
            }
        }
    }

    private fun cached(embedUrl: String): VortexExtractedMedia? = synchronized(lock) {
        val entry = cache[embedUrl] ?: return null
        if (entry.storedAt.elapsedNow().inWholeMilliseconds > CACHE_TTL_MS) {
            cache.remove(embedUrl)
            return null
        }
        entry.media
    }

    private fun remember(embedUrl: String, media: VortexExtractedMedia) = synchronized(lock) {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            val oldest = cache.keys.firstOrNull()
            if (oldest != null) cache.remove(oldest)
        }
        cache[embedUrl] = CacheEntry(media, clock.markNow())
    }

    suspend fun extract(embedUrl: String, forceRefresh: Boolean = false): VortexExtractedMedia? {
        if (AnilumosAnimeBackend.isAnilumosStreamUrl(embedUrl)) {
            if (forceRefresh) AnilumosAnimeBackend.invalidateStream(embedUrl)
            return AnilumosAnimeBackend.extractUrl(embedUrl)
        }
        if (!VortexServers.isEmbedUrl(embedUrl)) return null
        if (!forceRefresh) {
            cached(embedUrl)?.let { return it }
        } else {
            invalidate(embedUrl)
        }

        // Race direct HTTP extraction against WebView sniffing (HTTP gets a tiny head start)
        // so the very first .m3u8/.mp4 wins immediately instead of stalling sequentially on timeouts.
        val winner = coroutineScope {
            val channel = Channel<Pair<String, VortexExtractedMedia>>(Channel.UNLIMITED)
            val httpJob = launch {
                val res = runCatching {
                    withTimeoutOrNull(HTTP_TIMEOUT_MS) { extractViaHttp(embedUrl) }
                }.onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                if (res != null) channel.trySend("HTTP" to res)
            }
            val sniffJob = launch {
                delay(SNIFFER_HEAD_START_MS)
                val res = runCatching {
                    VortexPlatformSniffer.sniff(embedUrl, SNIFFER_TIMEOUT_MS)
                }.onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                if (res != null) channel.trySend("WebView sniffer" to res)
            }
            launch {
                httpJob.join()
                sniffJob.join()
                channel.close()
            }
            val first = channel.receiveCatching().getOrNull()
            if (httpJob.isActive) httpJob.cancel()
            if (sniffJob.isActive) sniffJob.cancel()
            first
        }
        if (winner != null) {
            val (sourceKind, media) = winner
            log.i { "Extracted via $sourceKind for ${VortexAdShield.hostOf(embedUrl)}: type=${media.streamType}" }
            remember(embedUrl, media)
            return media
        }
        log.w { "Direct extraction failed for ${VortexAdShield.hostOf(embedUrl)}" }
        return null
    }

    fun cachedBestForTarget(
        target: VortexEmbedTarget,
        preferredServer: VortexServer = VortexServers.preferredServer,
        excludedServers: Set<VortexServer> = emptySet(),
    ): VortexExtractedMedia? {
        val allSources = VortexServers.sourcesFor(target).filter { it.server !in excludedServers }
        if (allSources.isEmpty()) return null
        val ordered = buildList {
            allSources.firstOrNull { it.server == preferredServer }?.let(::add)
            allSources.filter { it.server != preferredServer }.forEach(::add)
        }
        for (source in ordered) {
            cached(source.url)?.let { return it }
        }
        return null
    }

    /**
     * Silent HTTP-only warmup for likely next playback targets (for example Continue Watching top card).
     * Never spawns a WebView sniffer and returns the cached/extracted media if ready within [HTTP_TIMEOUT_MS].
     */
    suspend fun prewarmHttpForTarget(
        target: VortexEmbedTarget,
        preferredServer: VortexServer = VortexServers.preferredServer,
    ): VortexExtractedMedia? {
        cachedBestForTarget(target, preferredServer)?.let { return it }
        val allSources = VortexServers.sourcesFor(target)
        if (allSources.isEmpty()) return null
        val ordered = buildList {
            allSources.firstOrNull { it.server == VortexServer.SERVER2 }?.let(::add)
            allSources.firstOrNull { it.server == preferredServer && it.server != VortexServer.SERVER2 }?.let(::add)
            allSources.filter { it.server != preferredServer && it.server != VortexServer.SERVER2 }.forEach(::add)
        }
        val winner = coroutineScope {
            val channel = Channel<Pair<VortexEmbedSource, VortexExtractedMedia>>(Channel.UNLIMITED)
            val jobs = ordered.map { source ->
                source to launch {
                    val res = runCatching {
                        withTimeoutOrNull(HTTP_TIMEOUT_MS) { extractViaHttp(source.url) }
                    }.onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                    if (res != null) channel.trySend(source to res)
                }
            }
            launch {
                jobs.forEach { (_, job) -> job.join() }
                channel.close()
            }
            val found = channel.receiveCatching().getOrNull()
            jobs.forEach { (_, job) -> if (job.isActive) job.cancel() }
            found
        } ?: return null
        remember(winner.first.url, winner.second)
        return winner.second
    }

    /**
     * Tries [preferredServer] and all alternate servers using fast parallel HTTP extraction first,
     * then falls back to a bounded silent WebView sniffer budget across remaining servers.
     */
    suspend fun extractBestForTarget(
        target: VortexEmbedTarget,
        preferredServer: VortexServer = VortexServers.preferredServer,
        excludedServers: Set<VortexServer> = emptySet(),
    ): VortexExtractedMedia? {
        val allSources = VortexServers.sourcesFor(target).filter { it.server !in excludedServers }
        if (allSources.isEmpty()) return null

        val ordered = buildList {
            allSources.firstOrNull { it.server == preferredServer }?.let(::add)
            allSources.filter { it.server != preferredServer }.forEach(::add)
        }

        // Cached result first so already-resolved servers start instantaneously.
        for (source in ordered) {
            cached(source.url)?.let { hit ->
                VortexServers.preferredServer = source.server
                return hit
            }
        }

        // Pipeline HTTP race and WebView sniffer concurrently inside one winner channel:
        // HTTP starts immediately across fast servers, and WebView sniffing joins at 450ms
        // so the fastest .m3u8/.mp4 from ANY server launches ExoPlayer immediately.
        val httpPriorityOrder = ordered
            .filter { it.server != VortexServer.SERVER4 }
            .sortedBy { serverScore(it.server) }
            .ifEmpty { ordered }
        val sniffOrder = buildList {
            val ranked = ordered.sortedBy {
                if (it.server == preferredServer) -1.0 else serverScore(it.server)
            }
            for (srv in ranked) {
                if (srv.server != VortexServer.SERVER4) add(srv)
            }
            ranked.firstOrNull { it.server == VortexServer.SERVER4 }?.let(::add)
        }
        val winner = coroutineScope {
            val channel = Channel<Triple<VortexEmbedSource, VortexExtractedMedia, String>>(Channel.UNLIMITED)
            val httpJobs = httpPriorityOrder.map { source ->
                source to launch {
                    val mark = clock.markNow()
                    val res = runCatching {
                        withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                            extractViaHttp(source.url)
                        }
                    }.onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                    val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                    if (res != null) {
                        recordServerOutcome(source.server, succeeded = true, durationMs = elapsedMs)
                        channel.trySend(Triple(source, res, "HTTP (${elapsedMs}ms)"))
                    } else {
                        recordServerOutcome(source.server, succeeded = false, durationMs = elapsedMs)
                    }
                }
            }
            val sniffJob = launch {
                delay(RACE_SNIFFER_HEAD_START_MS)
                withTimeoutOrNull(TOTAL_SNIFF_BUDGET_MS) {
                    for (source in sniffOrder) {
                        currentCoroutineContext().ensureActive()
                        val mark = clock.markNow()
                        val sniffed = runCatching {
                            VortexPlatformSniffer.sniff(source.url, SNIFFER_TIMEOUT_MS)
                        }.onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                        if (sniffed != null) {
                            recordServerOutcome(source.server, succeeded = true, durationMs = elapsedMs)
                            channel.trySend(Triple(source, sniffed, "WebView sniffer (${elapsedMs}ms)"))
                            break
                        } else {
                            recordServerOutcome(source.server, succeeded = false, durationMs = elapsedMs)
                        }
                    }
                }
            }
            launch {
                httpJobs.forEach { (_, job) -> job.join() }
                sniffJob.join()
                channel.close()
            }
            val found = channel.receiveCatching().getOrNull()
            httpJobs.forEach { (_, job) -> if (job.isActive) job.cancel() }
            if (sniffJob.isActive) sniffJob.cancel()
            found
        } ?: return null

        val (source, extracted, how) = winner
        log.i { "Fastest winner ${source.server.displayName} via $how: type=${extracted.streamType}" }
        remember(source.url, extracted)
        VortexServers.preferredServer = source.server
        return extracted
    }

    /**
     * Resolves a Vortex embed [StreamItem] into a direct-playable [StreamItem] with extracted
     * headers and subtitles, or returns `null` when extraction fails.
     */
    suspend fun resolveStreamItem(stream: StreamItem, forceRefresh: Boolean = false): StreamItem? {
        val embedUrl = stream.playableDirectUrl?.takeIf(::isResolvableUrl) ?: return null
        val extracted = extract(embedUrl, forceRefresh = forceRefresh) ?: return null
        extracted.server?.let { VortexServers.preferredServer = it }
        return applyExtractedMedia(stream, extracted)
    }

    fun applyExtractedMedia(stream: StreamItem, extracted: VortexExtractedMedia): StreamItem {
        val serverName = extracted.displayNameOverride
            ?: extracted.server?.displayName
            ?: stream.streamLabel
        return stream.copy(
            name = serverName,
            title = serverName,
            description = "$serverName · Direct stream",
            url = extracted.url,
            externalUrl = extracted.embedUrl,
            streamType = extracted.streamType ?: stream.streamType,
            externalSubtitles = (extracted.subtitles + stream.externalSubtitles).distinctBy { it.url },
            behaviorHints = stream.behaviorHints.copy(
                notWebReady = true,
                proxyHeaders = extracted.headers.takeIf { it.isNotEmpty() }?.let {
                    StreamProxyHeaders(request = it)
                },
            ),
        )
    }

    fun toDirectStreamItem(extracted: VortexExtractedMedia): StreamItem {
        val server = extracted.server
        val isAnilumos = AnilumosAnimeBackend.isAnilumosStreamUrl(extracted.embedUrl)
        val serverLabel = extracted.displayNameOverride
            ?: server?.displayName
            ?: if (isAnilumos) "Anilumos" else VortexServer.SERVER1.displayName
        val bingeGroup = server?.let { "vortex-${it.name.lowercase()}" }
            ?: if (isAnilumos) "anilumos-direct" else "vortex-direct"
        val addonName = if (isAnilumos) serverLabel else VortexBuiltinAddon.NAME
        return StreamItem(
            name = addonName,
            title = serverLabel,
            description = "$serverLabel · Direct stream",
            url = extracted.url,
            externalUrl = extracted.embedUrl,
            addonName = addonName,
            addonId = VortexBuiltinAddon.ADDON_ID,
            streamType = extracted.streamType,
            externalSubtitles = extracted.subtitles,
            behaviorHints = StreamBehaviorHints(
                bingeGroup = bingeGroup,
                notWebReady = true,
                proxyHeaders = extracted.headers.takeIf { it.isNotEmpty() }?.let {
                    StreamProxyHeaders(request = it)
                },
            ),
        )
    }

    internal suspend fun extractViaHttp(embedUrl: String): VortexExtractedMedia? = withContext(Dispatchers.Default) {
        val origin = originOf(embedUrl)
        val headers = buildMap {
            put("User-Agent", BROWSER_USER_AGENT)
            put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            put("Accept-Language", "en-US,en;q=0.9")
            origin?.let {
                put("Referer", "$it/")
            }
        }
        val response = runCatching {
            httpRequestRaw(
                method = "GET",
                url = embedUrl,
                headers = headers,
                body = "",
                followRedirects = true,
            )
        }.onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return@withContext null

        val html = response.body
        if (response.status !in 200..299 || html.isBlank() || looksLikeBotChallenge(html)) {
            return@withContext null
        }

        parseDirectMediaFromHtml(embedUrl, html)?.let { return@withContext it }

        // Rozgarlelo / CineVerse proxy-iframe server: #playerFrame -> /proxy.php?p=... -> master.txt + SEG_REF.
        if (embedUrl.contains("modiplay.xyz", ignoreCase = true) ||
            html.contains("/proxy.php", ignoreCase = true)
        ) {
            extractProxyFrameServer(embedUrl, html, headers)?.let { return@withContext it }
        }

        null
    }

    internal fun parseProxyServerCandidates(
        embedUrl: String,
        outerHtml: String,
    ): List<ProxyServerCandidate> {
        val embedOrigin = originOf(embedUrl) ?: return emptyList()
        val seenKeys = LinkedHashSet<String>()
        val candidates = mutableListOf<ProxyServerCandidate>()

        fun addCandidate(platformRaw: String, codeRaw: String?, explicitUrl: String?) {
            val platform = platformRaw.trim().lowercase()
            val code = codeRaw?.trim().orEmpty()
            val frameUrl = explicitUrl ?: if (platform.isNotBlank() && code.isNotBlank()) {
                "$embedOrigin/proxy.php?p=${platform.encodeQueryValue()}&c=${code.encodeQueryValue()}&noredirect=1"
            } else {
                return
            }
            val dedupeKey = if (platform.isNotBlank() && code.isNotBlank()) "$platform:$code" else frameUrl
            if (!seenKeys.add(dedupeKey)) return
            val priority = when (platform) {
                "filesunny" -> 0
                "vidara" -> 1
                "vidrift" -> 2
                "earnvids" -> 3
                "vsembed" -> 4
                else -> 5
            }
            candidates += ProxyServerCandidate(
                key = dedupeKey,
                platform = platform.ifBlank { "default" },
                frameUrl = frameUrl,
                priority = priority,
            )
        }

        // Parse server chip elements first (data-platform + data-code in any attribute order).
        DATA_TAG_CANDIDATE_REGEX.findAll(outerHtml).forEach { match ->
            val tag = match.value
            val platform = DATA_ATTR_PLATFORM_REGEX.find(tag)?.groupValues?.getOrNull(1) ?: return@forEach
            val code = DATA_ATTR_CODE_REGEX.find(tag)?.groupValues?.getOrNull(1) ?: return@forEach
            addCandidate(platform, code, explicitUrl = null)
        }

        // Also include the default #playerFrame src if not already present by platform:code.
        PLAYER_FRAME_SRC_REGEX.find(outerHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { raw -> normalizeExtractedUrl(embedUrl, raw) }
            ?.let { frameUrl ->
                val p = QUERY_PARAM_P_REGEX.find(frameUrl)?.groupValues?.getOrNull(1).orEmpty()
                val c = QUERY_PARAM_C_REGEX.find(frameUrl)?.groupValues?.getOrNull(1)
                addCandidate(p, c, explicitUrl = frameUrl)
            }

        return candidates.sortedBy { it.priority }.take(4)
    }

    private data class ProbedProxyCandidate(
        val candidate: ProxyServerCandidate,
        val media: VortexExtractedMedia,
        val failedOrigin: Boolean,
    )

    private suspend fun extractProxyFrameServer(
        embedUrl: String,
        outerHtml: String,
        baseHttpHeaders: Map<String, String>,
    ): VortexExtractedMedia? {
        val candidates = parseProxyServerCandidates(embedUrl, outerHtml)
        if (candidates.isEmpty()) return null
        val failedOrigins = failedOriginsFor(embedUrl)

        val probed: List<ProbedProxyCandidate> = coroutineScope {
            val results = mutableListOf<ProbedProxyCandidate>()
            val fastWinnerSignal = Channel<ProbedProxyCandidate>(Channel.UNLIMITED)

            val jobs = candidates.map { candidate ->
                launch {
                    val item = withTimeoutOrNull(PROXY_FETCH_TIMEOUT_MS) {
                        val frameHeaders = baseHttpHeaders + mapOf("Referer" to embedUrl)
                        val frameResponse = runCatching {
                            httpRequestRaw(
                                method = "GET",
                                url = candidate.frameUrl,
                                headers = frameHeaders,
                                body = "",
                                followRedirects = true,
                            )
                        }.onFailure { if (it is CancellationException) throw it }
                            .getOrNull() ?: return@withTimeoutOrNull null

                        val frameHtml = frameResponse.body
                        if (frameResponse.status !in 200..299 || frameHtml.isBlank() || looksLikeBotChallenge(frameHtml)) {
                            return@withTimeoutOrNull null
                        }

                        val parsed = parseProxyPlayerHtml(embedUrl, candidate.frameUrl, frameHtml, verifyOnline = true)
                            ?: return@withTimeoutOrNull null
                        val origin = originOf(parsed.url)?.lowercase()
                        val isFailed = origin != null && origin in failedOrigins
                        ProbedProxyCandidate(
                            candidate = candidate,
                            media = parsed,
                            failedOrigin = isFailed,
                        )
                    }
                    if (item != null) {
                        fastWinnerSignal.trySend(item)
                    }
                }
            }
            launch {
                jobs.forEach { it.join() }
                fastWinnerSignal.close()
            }

            // Return immediately once a primary CDN (filesunny/vidara, priority <= 1) responds;
            // only give slower backup frames a short grace window if a low-priority CDN responded first.
            var sawPrimaryWinner = false
            var sawBackupWinner = false
            for (item in fastWinnerSignal) {
                results += item
                if (!item.failedOrigin) {
                    if (item.candidate.priority <= 1) {
                        sawPrimaryWinner = true
                        break
                    } else {
                        sawBackupWinner = true
                        break
                    }
                }
            }
            if (sawBackupWinner && !sawPrimaryWinner) {
                withTimeoutOrNull(PROXY_BACKUP_GRACE_MS) {
                    for (extra in fastWinnerSignal) {
                        results += extra
                        if (!extra.failedOrigin && extra.candidate.priority <= 1) {
                            break
                        }
                    }
                }
            }
            while (true) {
                val extra = fastWinnerSignal.tryReceive().getOrNull() ?: break
                results += extra
            }
            jobs.forEach { if (it.isActive) it.cancel() }
            results
        }

        val ranked = probed
            .filterNot { it.failedOrigin }
            .ifEmpty { if (failedOrigins.isEmpty()) probed else emptyList() }
            .sortedBy { it.candidate.priority }

        val winner = ranked.firstOrNull() ?: return null
        rememberAlternates(embedUrl, ranked.drop(1).map { it.media })
        log.i {
            "Rozgarlelo CDN winner platform=${winner.candidate.platform} " +
                "priority=${winner.candidate.priority} alternates=${ranked.size - 1}"
        }
        return winner.media
    }

    internal suspend fun parseProxyPlayerHtml(
        embedUrl: String,
        proxyUrl: String,
        proxyHtml: String,
        verifyOnline: Boolean = true,
    ): VortexExtractedMedia? {
        val proxyOrigin = originOf(proxyUrl) ?: originOf(embedUrl)
        val rawDirect = PROXY_VAR_SRC_REGEX.findAll(proxyHtml)
            .mapNotNull { normalizeExtractedUrl(proxyUrl, it.groupValues[1]) }
            .firstOrNull { !VortexAdShield.isAdUrl(it) }
        val innerEmbed = PROXY_EMBED_URL_REGEX.find(proxyHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { normalizeExtractedUrl(proxyUrl, it) }
        val segRef = PROXY_SEG_REF_REGEX.find(proxyHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { normalizeExtractedUrl(proxyUrl, it) }

        val subtitleHeaders = buildMap {
            put("User-Agent", BROWSER_USER_AGENT)
            proxyOrigin?.let {
                put("Referer", "$it/")
                put("Origin", it)
            }
        }
        val subtitles = extractSubtitlesFromHtml(proxyUrl, proxyHtml, subtitleHeaders)

        if (rawDirect != null) {
            val headerCandidates = buildList {
                if (segRef != null) {
                    val segOrigin = originOf(segRef)
                    add(
                        buildMap {
                            put("User-Agent", BROWSER_USER_AGENT)
                            put("Referer", segRef)
                            segOrigin?.let { put("Origin", it) }
                        },
                    )
                }
                if (innerEmbed != null && innerEmbed != segRef) {
                    val innerOrigin = originOf(innerEmbed)
                    add(
                        buildMap {
                            put("User-Agent", BROWSER_USER_AGENT)
                            put("Referer", innerEmbed)
                            innerOrigin?.let { put("Origin", it) }
                        },
                    )
                }
                add(
                    buildMap {
                        put("User-Agent", BROWSER_USER_AGENT)
                        proxyOrigin?.let {
                            put("Referer", "$it/")
                            put("Origin", it)
                        }
                    },
                )
            }

            if (!verifyOnline) {
                val chosenHeaders = headerCandidates.first()
                return VortexExtractedMedia(
                    url = rawDirect,
                    headers = chosenHeaders,
                    subtitles = subtitles,
                    streamType = inferStreamType(rawDirect) ?: if (rawDirect.contains("master", true)) "hls" else null,
                    embedUrl = embedUrl,
                )
            }

            // Check at most the primary header (SEG_REF / EMBED_URL) to avoid extra probe latency.
            for (candidateHeaders in headerCandidates.distinct().take(1)) {
                val probe = runCatching {
                    httpRequestRaw(
                        method = "GET",
                        url = rawDirect,
                        headers = candidateHeaders + mapOf("Accept" to "*/*"),
                        body = "",
                        followRedirects = true,
                        maxResponseBodyBytes = 4 * 1024,
                    )
                }.onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: continue

                if (probe.status !in 200..299) continue
                val contentType = probe.headers.entries
                    .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value
                    ?.lowercase()
                    .orEmpty()
                val bodyStart = probe.body.trimStart()
                val verifiedType = when {
                    "mpegurl" in contentType || "m3u8" in contentType || bodyStart.startsWith("#EXTM3U") -> "hls"
                    "dash+xml" in contentType || bodyStart.startsWith("<MPD") -> "dash"
                    contentType.startsWith("video/") -> "mp4"
                    isDirectMediaUrl(rawDirect) -> inferStreamType(rawDirect)
                    else -> null
                } ?: continue

                return VortexExtractedMedia(
                    url = rawDirect,
                    headers = candidateHeaders,
                    subtitles = subtitles,
                    streamType = verifiedType,
                    embedUrl = embedUrl,
                )
            }
        }

        // Fall back to plain HTML extraction inside proxyHtml (e.g. if proxyHtml has .m3u8/.mp4 directly).
        return parseDirectMediaFromHtml(proxyUrl, proxyHtml)?.let { parsed ->
            val h = if (segRef != null) {
                buildMap {
                    put("User-Agent", BROWSER_USER_AGENT)
                    put("Referer", segRef)
                    originOf(segRef)?.let { put("Origin", it) }
                }
            } else {
                parsed.headers
            }
            parsed.copy(
                headers = h,
                subtitles = (subtitles + parsed.subtitles).distinctBy { it.url },
                embedUrl = embedUrl,
                server = VortexServers.serverFor(embedUrl),
            )
        }
    }

    internal fun parseDirectMediaFromHtml(embedUrl: String, html: String): VortexExtractedMedia? {
        val origin = originOf(embedUrl)
        val playbackHeaders = buildMap {
            put("User-Agent", BROWSER_USER_AGENT)
            put("Referer", origin?.let { "$it/" } ?: embedUrl)
            origin?.let { put("Origin", it) }
        }

        val subtitles = extractSubtitlesFromHtml(embedUrl, html, playbackHeaders)

        // 1. vixsrc / inertia-style masterPlaylist block:
        // window.masterPlaylist = { params: { 'token': '...', 'expires': '...' }, url: 'https://.../playlist/123' }
        parseMasterPlaylistBlock(embedUrl, html)?.let { playlistUrl ->
            return VortexExtractedMedia(
                url = playlistUrl,
                headers = playbackHeaders,
                subtitles = subtitles,
                streamType = "hls",
                embedUrl = embedUrl,
            )
        }

        // 2. JSON-embedded sources / file / url fields pointing to .m3u8, .mpd, .mp4, or master.txt
        val candidateUrls = buildList {
            for (regex in MEDIA_URL_REGEXES) {
                regex.findAll(html).forEach { match ->
                    val raw = match.groupValues.getOrNull(1).orEmpty()
                    normalizeExtractedUrl(embedUrl, raw)
                        ?.takeIf { isDirectMediaUrl(it) && !VortexAdShield.isAdUrl(it) }
                        ?.let(::add)
                }
            }
        }.distinct()

        val bestUrl = candidateUrls.firstOrNull { it.contains(".m3u8", ignoreCase = true) }
            ?: candidateUrls.firstOrNull { it.contains("master.txt", ignoreCase = true) || it.contains("playlist", ignoreCase = true) }
            ?: candidateUrls.firstOrNull { it.contains(".mpd", ignoreCase = true) }
            ?: candidateUrls.firstOrNull()
            ?: return null

        return VortexExtractedMedia(
            url = bestUrl,
            headers = playbackHeaders,
            subtitles = subtitles,
            streamType = inferStreamType(bestUrl),
            embedUrl = embedUrl,
        )
    }

    private fun parseMasterPlaylistBlock(embedUrl: String, html: String): String? {
        val startMatch = MASTER_PLAYLIST_START_REGEX.find(html) ?: return null
        val openIndex = startMatch.range.last
        var depth = 0
        var endIndex = -1
        val maxEnd = minOf(html.length, openIndex + 2_000)
        for (i in openIndex until maxEnd) {
            when (html[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        endIndex = i + 1
                        break
                    }
                }
            }
        }
        if (endIndex <= openIndex) return null
        val block = html.substring(openIndex, endIndex)
        val rawUrl = MASTER_URL_REGEX.find(block)?.groupValues?.getOrNull(1) ?: return null
        val baseUrl = normalizeExtractedUrl(embedUrl, rawUrl) ?: return null
        val token = MASTER_TOKEN_REGEX.find(block)?.groupValues?.getOrNull(1)
        val expires = MASTER_EXPIRES_REGEX.find(block)?.groupValues?.getOrNull(1)
        if (token.isNullOrBlank() || expires.isNullOrBlank()) return null
        val separator = if ('?' in baseUrl) '&' else '?'
        return buildString {
            append(baseUrl)
            append(separator)
            append("token=").append(token.encodeQueryValue())
            append("&expires=").append(expires.encodeQueryValue())
            append("&h=1")
        }
    }

    private fun extractSubtitlesFromHtml(
        baseUrl: String,
        html: String,
        headers: Map<String, String>,
    ): List<StreamSubtitle> {
        val tracks = mutableListOf<StreamSubtitle>()
        TRACK_JSON_OBJECT_REGEX.findAll(html).forEach { match ->
            val obj = match.value
            val rawUrl = TRACK_URL_FIELD_REGEX.find(obj)?.groupValues?.getOrNull(1) ?: return@forEach
            val label = TRACK_LABEL_FIELD_REGEX.find(obj)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            val url = normalizeExtractedUrl(baseUrl, rawUrl) ?: return@forEach
            if (!isSubtitleUrl(url)) return@forEach
            val lang = inferLanguageCode(label ?: url)
            tracks += StreamSubtitle(
                url = url,
                language = lang,
                name = label ?: lang.uppercase(),
                headers = headers,
            )
        }
        HTML_TRACK_TAG_REGEX.findAll(html).forEach { match ->
            val tag = match.value
            val rawUrl = HTML_TRACK_SRC_REGEX.find(tag)?.groupValues?.getOrNull(1) ?: return@forEach
            val label = HTML_TRACK_LABEL_REGEX.find(tag)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            val url = normalizeExtractedUrl(baseUrl, rawUrl) ?: return@forEach
            if (!isSubtitleUrl(url) || tracks.any { it.url == url }) return@forEach
            val lang = inferLanguageCode(label ?: url)
            tracks += StreamSubtitle(
                url = url,
                language = lang,
                name = label ?: lang.uppercase(),
                headers = headers,
            )
        }
        SUBTITLE_URL_REGEX.findAll(html).forEach { match ->
            val url = normalizeExtractedUrl(baseUrl, match.groupValues[1]) ?: return@forEach
            if (!isSubtitleUrl(url) || tracks.any { it.url == url }) return@forEach
            val lang = inferLanguageCode(url)
            tracks += StreamSubtitle(
                url = url,
                language = lang,
                name = lang.uppercase(),
                headers = headers,
            )
        }
        return tracks.distinctBy { it.url }
    }

    internal fun inferStreamType(url: String): String? {
        val lower = url.lowercase()
        val clean = lower.substringBefore('?').substringBefore('#')
        return when {
            clean.endsWith(".m3u8") || ".m3u8" in lower -> "hls"
            clean.endsWith("/master.txt") || clean.endsWith("/playlist.txt") ||
                ("/playlist/" in clean && "token=" in lower) -> "hls"
            clean.endsWith(".mpd") || ".mpd" in lower -> "dash"
            clean.endsWith(".mp4") || clean.endsWith(".m4v") || clean.endsWith(".mkv") || clean.endsWith(".webm") -> "mp4"
            else -> null
        }
    }

    internal fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val path = lower.substringBefore('?').substringBefore('#')
        if (path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".html") ||
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
            path.endsWith(".webp") || path.endsWith(".svg") || path.endsWith(".woff2") ||
            path.endsWith(".woff") || path.endsWith(".ts") || path.endsWith(".m4s")
        ) {
            return false
        }
        if ("/ads" in lower || "doubleclick" in lower || "vast.xml" in lower) return false
        if ("/_next/" in path || "/__next" in path || path.endsWith("/robots.txt")) return false
        return ".m3u8" in lower || ".mpd" in lower ||
            path.endsWith("/master.txt") || path.endsWith("/playlist.txt") ||
            ("/playlist/" in path && "token=" in lower) ||
            path.endsWith(".mp4") || path.endsWith(".m4v") ||
            path.endsWith(".mkv") || path.endsWith(".webm")
    }

    internal fun isSubtitleUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val path = lower.substringBefore('?').substringBefore('#')
        if (path.endsWith(".vtt") || path.endsWith(".srt") || path.endsWith(".ass") || path.endsWith(".ssa") || path.endsWith(".ttml")) {
            return true
        }
        // Proxied subtitles such as /api/sub_proxy.php?url=https%3A%2F%2F...%2Fen.vtt&tok=...
        if (("sub_proxy" in path || "subtitle" in path) &&
            (".vtt" in lower || "%2evtt" in lower || ".srt" in lower || "%2esrt" in lower || ".ass" in lower || "%2eass" in lower)
        ) {
            return true
        }
        return false
    }

    private fun looksLikeBotChallenge(html: String): Boolean {
        val lower = html.lowercase()
        return "cf-turnstile" in lower ||
            "cf-challenge" in lower ||
            "just a moment..." in lower ||
            "attention required! | cloudflare" in lower
    }

    internal fun normalizeExtractedUrl(baseUrl: String, raw: String): String? {
        val trimmed = raw.trim()
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("&amp;", "&")
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> originOf(baseUrl)?.let { "$it$trimmed" }
            else -> null
        }
    }

    internal fun resolvePlaylistEntryUrl(baseUrl: String, entry: String): String? {
        normalizeExtractedUrl(baseUrl, entry)?.let { return it }
        val clean = entry.trim().removePrefix("./")
        if (clean.isBlank() || clean.startsWith("#")) return null
        val baseWithoutQuery = baseUrl.substringBefore('#').substringBefore('?')
        val lastSlash = baseWithoutQuery.lastIndexOf('/')
        if (lastSlash <= baseWithoutQuery.indexOf("://") + 2) {
            return originOf(baseUrl)?.let { "$it/$clean" }
        }
        return "${baseWithoutQuery.substring(0, lastSlash + 1)}$clean"
    }

    internal fun firstPlaylistEntryUrl(playlistUrl: String, playlistBody: String): String? {
        val lines = playlistBody.lineSequence().map { it.trim() }.toList()
        var afterStreamInf = false
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                afterStreamInf = true
                continue
            }
            if (afterStreamInf && !line.startsWith("#")) {
                resolvePlaylistEntryUrl(playlistUrl, line)?.let { return it }
            }
        }
        return lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.let { resolvePlaylistEntryUrl(playlistUrl, it) }
    }

    internal fun firstSegmentUrl(playlistUrl: String, playlistBody: String): String? {
        val lines = playlistBody.lineSequence().map { it.trim() }.toList()
        var afterExtInf = false
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                afterExtInf = true
                continue
            }
            if (afterExtInf && !line.startsWith("#")) {
                resolvePlaylistEntryUrl(playlistUrl, line)?.let { return it }
            }
        }
        for (line in lines) {
            if (line.startsWith("#EXT-X-MAP", ignoreCase = true)) {
                val uri = EXT_X_MAP_URI_REGEX.find(line)?.groupValues?.getOrNull(1)
                if (!uri.isNullOrBlank()) {
                    resolvePlaylistEntryUrl(playlistUrl, uri)?.let { return it }
                }
            }
        }
        return lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.let { resolvePlaylistEntryUrl(playlistUrl, it) }
    }

    internal fun originOf(url: String): String? {
        val schemeEnd = url.indexOf("://").takeIf { it > 0 } ?: return null
        val hostStart = schemeEnd + 3
        val hostEnd = url.indexOf('/', startIndex = hostStart).takeIf { it >= 0 } ?: url.length
        val authority = url.substring(hostStart, hostEnd).takeIf { it.isNotBlank() } ?: return null
        return "${url.substring(0, schemeEnd)}://$authority"
    }

    private fun inferLanguageCode(hint: String): String {
        val lower = hint.lowercase()
        return when {
            lower.startsWith("en ") || lower.startsWith("en-") || "english" in lower || ".eng." in lower || "/en" in lower -> "en"
            lower.startsWith("es ") || lower.startsWith("es-") || "spanish" in lower || "español" in lower || ".spa." in lower || "/es" in lower -> "es"
            lower.startsWith("hi ") || lower.startsWith("hi-") || "hindi" in lower || ".hin." in lower || "/hi" in lower -> "hi"
            lower.startsWith("ja ") || lower.startsWith("ja-") || "japanese" in lower || ".jpn." in lower || "/ja" in lower -> "ja"
            lower.startsWith("ar ") || lower.startsWith("ar-") || "arabic" in lower || ".ara." in lower || "/ar" in lower -> "ar"
            lower.startsWith("fr ") || lower.startsWith("fr-") || "french" in lower || "français" in lower || "/fr" in lower -> "fr"
            lower.startsWith("de ") || lower.startsWith("de-") || "german" in lower || "deutsch" in lower || "/de" in lower -> "de"
            lower.startsWith("it ") || lower.startsWith("it-") || "italian" in lower || "italiano" in lower || "/it" in lower -> "it"
            lower.startsWith("pt ") || lower.startsWith("pt-") || "portuguese" in lower || "português" in lower || "/pt" in lower -> "pt"
            else -> "und"
        }
    }

    private val MASTER_PLAYLIST_START_REGEX = Regex(
        """window\.masterPlaylist\s*=\s*\{""",
        RegexOption.IGNORE_CASE,
    )
    private val MASTER_URL_REGEX = Regex("""url\s*:\s*["']([^"']+)["']""")
    private val MASTER_TOKEN_REGEX = Regex("""['"]?token['"]?\s*:\s*['"]([^'"]+)['"]""")
    private val MASTER_EXPIRES_REGEX = Regex("""['"]?expires['"]?\s*:\s*['"]([^'"]+)['"]""")

    private val PLAYER_FRAME_SRC_REGEX = Regex(
        """<iframe\b[^>]*\bsrc=["']([^"']*proxy\.php[^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val DATA_TAG_CANDIDATE_REGEX = Regex(
        """<[^>]*data-(?:platform|code)=[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val DATA_ATTR_PLATFORM_REGEX = Regex(
        """data-platform=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val DATA_ATTR_CODE_REGEX = Regex(
        """data-code=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val QUERY_PARAM_P_REGEX = Regex("""[?&]p=([^&]+)""", RegexOption.IGNORE_CASE)
    private val QUERY_PARAM_C_REGEX = Regex("""[?&]c=([^&]+)""", RegexOption.IGNORE_CASE)
    private val EXT_X_MAP_URI_REGEX = Regex("""URI=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val PROXY_VAR_SRC_REGEX = Regex(
        """var\s+(?:directSrc|src)\s*=\s*["']((?:https?:)?(?:\\/|/)[^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val PROXY_SEG_REF_REGEX = Regex(
        """var\s+SEG_REF\s*=\s*["']((?:https?:)?(?:\\/|/)[^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val PROXY_EMBED_URL_REGEX = Regex(
        """var\s+EMBED_URL\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val MEDIA_URL_REGEXES = listOf(
        Regex("""["'](?:file|url|src|source|hls|playlist|streamUrl)["']\s*:\s*["']((?:https?:)?//[^"'\\]+|(?:https?:)?\\/\\/[^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<(?:video|source)\b[^>]*\bsrc=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""(https?://[^\s"'<>\\]+\.(?:m3u8|mpd|mp4)(?:\?[^\s"'<>\\]*)?)""", RegexOption.IGNORE_CASE),
    )

    private val TRACK_JSON_OBJECT_REGEX = Regex(
        """\{[^{}]{0,600}?(?:\.(?:vtt|srt|ass|ssa)|%2[Ee](?:vtt|srt|ass|ssa))[^{}]{0,600}?\}""",
        RegexOption.IGNORE_CASE,
    )
    private val TRACK_URL_FIELD_REGEX = Regex(
        """["'](?:file|src|url)["']\s*:\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val TRACK_LABEL_FIELD_REGEX = Regex(
        """["'](?:label|lang|srclang)["']\s*:\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val HTML_TRACK_TAG_REGEX = Regex(
        """<track\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val HTML_TRACK_SRC_REGEX = Regex(
        """\bsrc=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val HTML_TRACK_LABEL_REGEX = Regex(
        """\blabel=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val SUBTITLE_URL_REGEX = Regex(
        """(https?:(?://|\\\/\\\/)[^\s"'<>]+\.(?:vtt|srt|ass|ssa)(?:\?[^\s"'<>]*)?)""",
        RegexOption.IGNORE_CASE,
    )
}
