package com.rajatsolankii.vortex.features.vortex

import co.touchlab.kermit.Logger
import com.rajatsolankii.vortex.features.addons.httpRequestRaw
import com.rajatsolankii.vortex.features.streams.StreamSubtitle
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Anilumos-backed Anime catalog, metadata, episode, skip-window, and direct stream resolver.
 *
 * Replaces TMDB in the Anime section with Anilumos's native sources:
 * - Catalog & search & core metadata: AniList GraphQL (`https://graphql.anilist.co`)
 * - Clear logos, backdrops & rich episodes: Konoha CDN + AniZip (`api.ani.zip`)
 * - Playback streams & subtitles & OP/ED skip windows: AniPm/Settlar, AniChan, and AnimeHeaven
 */
object AnilumosAnimeBackend {
    private val log = Logger.withTag("AnilumosBackend")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val clock = TimeSource.Monotonic
    private val lock = SynchronizedObject()

    private const val ANILIST_ENDPOINT = "https://graphql.anilist.co"
    private const val KONOHA_DATA_BASE = "https://cdn.jsdelivr.net/gh/anilifecommunity/media-index@main/data"
    private const val ANIZIP_BASE = "https://api.ani.zip/mappings"
    private const val ANIPM_BASE = "https://ani.pm"
    private const val SETTLAR_EMBED_BASE = "https://embed.settlar.io"
    private const val ANICHAN_BASE = "https://anichan.to"
    private const val ANIMEHEAVEN_BASE = "https://animeheaven.me"

    private const val MAX_LARGE_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val STREAM_CACHE_TTL_MS = 10 * 60_000L
    private const val PACKAGES_CACHE_TTL_MS = 15 * 60_000L
    private const val META_CACHE_TTL_MS = 30 * 60_000L
    private const val NETWORK_TIMEOUT_MS = 6_000L
    private const val ANIPM_TIMEOUT_MS = 4_500L
    private const val ANICHAN_SESSION_TTL_MS = 90 * 60_000L

    private const val CHROME_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

    data class AnilumosTarget(
        val anilistId: Int,
        val episode: Int,
    )

    data class AnilumosStreamSpec(
        val provider: String,
        val anilistId: Int,
        val episode: Int,
        val audio: String,
    )

    data class AnilumosSkipWindow(
        val introStart: Double? = null,
        val introEnd: Double? = null,
        val outroStart: Double? = null,
        val outroEnd: Double? = null,
    )

    private data class EpisodeAvailability(
        val sub: Set<Int> = emptySet(),
        val dub: Set<Int> = emptySet(),
    )

    private data class CachedMetaSummary(
        val anilistId: Int,
        val malId: Int?,
        val titles: List<String>,
        val storedAt: TimeMark,
    )

    private data class CachedAvailability(
        val availability: EpisodeAvailability,
        val storedAt: TimeMark,
    )

    private data class CachedStream(
        val media: VortexExtractedMedia,
        val storedAt: TimeMark,
    )

    private data class AniChanSession(
        val cookie: String,
        val storedAt: TimeMark,
    )

    private val metaSummaryCache = LinkedHashMap<Int, CachedMetaSummary>()
    private val metaJsonCache = LinkedHashMap<Int, Pair<JsonObject, TimeMark>>()
    private val availabilityCache = LinkedHashMap<Int, CachedAvailability>()
    private val streamCache = LinkedHashMap<String, CachedStream>()
    private val skipWindowsByKey = LinkedHashMap<String, AnilumosSkipWindow>()
    private var aniChanSession: AniChanSession? = null

    private val SERIES_FORMATS = setOf("TV", "TV_SHORT", "ONA")

    val GENRES: List<String> = listOf(
        "Action",
        "Adventure",
        "Comedy",
        "Drama",
        "Ecchi",
        "Fantasy",
        "Horror",
        "Mahou Shoujo",
        "Mecha",
        "Music",
        "Mystery",
        "Psychological",
        "Romance",
        "Sci-Fi",
        "Slice of Life",
        "Sports",
        "Supernatural",
        "Thriller",
    )

    fun isAnilistId(value: String?): Boolean {
        val raw = value?.trim().orEmpty()
        return raw.startsWith("anilist:", ignoreCase = true)
    }

    fun isAnilumosStreamUrl(url: String?): Boolean =
        url?.trim()?.startsWith("anilumos://", ignoreCase = true) == true

    fun parseAnilistId(id: String?): Int? {
        val raw = id?.trim()?.takeIf { it.startsWith("anilist:", ignoreCase = true) } ?: return null
        return raw.substringAfter(':').substringBefore(':').toIntOrNull()?.takeIf { it > 0 }
    }

    fun parseAnilistTarget(
        videoId: String?,
        parentMetaId: String? = null,
        episode: Int? = null,
    ): AnilumosTarget? {
        val idCandidate = listOfNotNull(videoId, parentMetaId)
            .firstOrNull { isAnilistId(it) }
            ?: return null
        val anilistId = parseAnilistId(idCandidate) ?: return null
        val parts = videoId.orEmpty().split(':')
        val parsedEpisode = if (isAnilistId(videoId) && parts.size >= 4) {
            parts.lastOrNull()?.toIntOrNull()
        } else if (isAnilistId(videoId) && parts.size == 3) {
            parts.lastOrNull()?.toIntOrNull()
        } else {
            null
        }
        val ep = (episode ?: parsedEpisode ?: 1).coerceAtLeast(1)
        return AnilumosTarget(anilistId = anilistId, episode = ep)
    }

    fun autoStreamUrl(anilistId: Int, episode: Int = 1): String =
        "anilumos://auto/$anilistId/${episode.coerceAtLeast(1)}/sub"

    fun parseStreamUrl(url: String?): AnilumosStreamSpec? {
        val raw = url?.trim()?.takeIf { it.startsWith("anilumos://", ignoreCase = true) } ?: return null
        val path = raw.removePrefix("anilumos://").removePrefix("ANILUMOS://").trim('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        val provider = parts.getOrNull(0)?.lowercase() ?: return null
        val anilistId = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val episode = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val audio = parts.getOrNull(3)?.lowercase()?.takeIf { it == "dub" || it == "sub" } ?: "sub"
        return AnilumosStreamSpec(provider = provider, anilistId = anilistId, episode = episode, audio = audio)
    }

    fun displayNameForStreamUrl(url: String?): String? {
        val spec = parseStreamUrl(url) ?: return null
        return when (spec.provider) {
            "anipm" -> if (spec.audio == "dub") "Vortex Anime Server 2" else "Vortex Anime Server 1"
            "anichan" -> if (spec.audio == "dub") "Vortex Anime Server 4" else "Vortex Anime Server 3"
            "animeheaven" -> "Vortex Anime Server 5"
            else -> "Vortex Anime Server 1"
        }
    }

    fun cachedMalId(anilistId: Int): Int? = synchronized(lock) { metaSummaryCache[anilistId]?.malId }

    fun cachedSkipWindow(anilistId: Int, episode: Int): AnilumosSkipWindow? = synchronized(lock) {
        skipWindowsByKey["$anilistId:$episode"]
    }

    fun cachedBestStream(anilistId: Int, episode: Int = 1): VortexExtractedMedia? {
        val candidates = listOf(
            autoStreamUrl(anilistId, episode),
            "anilumos://anipm/$anilistId/$episode/sub",
            "anilumos://anichan/$anilistId/$episode/sub",
            "anilumos://animeheaven/$anilistId/$episode/sub",
            "anilumos://anipm/$anilistId/$episode/dub",
            "anilumos://anichan/$anilistId/$episode/dub",
        )
        return candidates.firstNotNullOfOrNull(::cachedStream)
    }

    fun instantStreamsFor(anilistId: Int, episode: Int = 1): List<com.rajatsolankii.vortex.features.streams.StreamItem> {
        val ep = episode.coerceAtLeast(1)
        return listOf(
            com.rajatsolankii.vortex.features.streams.StreamItem(
                name = "Vortex Anime Server 1",
                title = "Vortex Anime Server 1",
                description = "AniPm Settlar · Subtitled HLS + WebVTT subtitles & OP/ED skip",
                url = "anilumos://anipm/$anilistId/$ep/sub",
                addonName = "Vortex Anime Server 1",
                addonId = VortexBuiltinAddon.ADDON_ID,
                streamType = "hls",
                behaviorHints = com.rajatsolankii.vortex.features.streams.StreamBehaviorHints(
                    bingeGroup = "anilumos-anipm-sub",
                ),
            ),
            com.rajatsolankii.vortex.features.streams.StreamItem(
                name = "Vortex Anime Server 2",
                title = "Vortex Anime Server 2",
                description = "AniPm Settlar · English Dubbed HLS",
                url = "anilumos://anipm/$anilistId/$ep/dub",
                addonName = "Vortex Anime Server 2",
                addonId = VortexBuiltinAddon.ADDON_ID,
                streamType = "hls",
                behaviorHints = com.rajatsolankii.vortex.features.streams.StreamBehaviorHints(
                    bingeGroup = "anilumos-anipm-dub",
                ),
            ),
            com.rajatsolankii.vortex.features.streams.StreamItem(
                name = "Vortex Anime Server 3",
                title = "Vortex Anime Server 3",
                description = "AniChan CDN · Subtitled 1080p HLS + WebVTT",
                url = "anilumos://anichan/$anilistId/$ep/sub",
                addonName = "Vortex Anime Server 3",
                addonId = VortexBuiltinAddon.ADDON_ID,
                streamType = "hls",
                behaviorHints = com.rajatsolankii.vortex.features.streams.StreamBehaviorHints(
                    bingeGroup = "anilumos-anichan-sub",
                ),
            ),
            com.rajatsolankii.vortex.features.streams.StreamItem(
                name = "Vortex Anime Server 4",
                title = "Vortex Anime Server 4",
                description = "AniChan CDN · English Dubbed HLS + WebVTT",
                url = "anilumos://anichan/$anilistId/$ep/dub",
                addonName = "Vortex Anime Server 4",
                addonId = VortexBuiltinAddon.ADDON_ID,
                streamType = "hls",
                behaviorHints = com.rajatsolankii.vortex.features.streams.StreamBehaviorHints(
                    bingeGroup = "anilumos-anichan-dub",
                ),
            ),
            com.rajatsolankii.vortex.features.streams.StreamItem(
                name = "Vortex Anime Server 5",
                title = "Vortex Anime Server 5",
                description = "AnimeHeaven · Subtitled HD Direct MP4",
                url = "anilumos://animeheaven/$anilistId/$ep/sub",
                addonName = "Vortex Anime Server 5",
                addonId = VortexBuiltinAddon.ADDON_ID,
                streamType = "mp4",
                behaviorHints = com.rajatsolankii.vortex.features.streams.StreamBehaviorHints(
                    bingeGroup = "anilumos-animeheaven-sub",
                ),
            ),
        )
    }

    fun cachedStream(url: String): VortexExtractedMedia? = synchronized(lock) {
        val entry = streamCache[url] ?: return@synchronized null
        if (entry.storedAt.elapsedNow().inWholeMilliseconds > STREAM_CACHE_TTL_MS) {
            streamCache.remove(url)
            return@synchronized null
        }
        entry.media
    }

    fun invalidateStream(url: String?) {
        if (url.isNullOrBlank()) return
        synchronized(lock) { streamCache.remove(url) }
    }

    private fun rememberStream(url: String, media: VortexExtractedMedia) {
        synchronized(lock) {
            if (streamCache.size >= 48) {
                streamCache.entries.firstOrNull()?.key?.let(streamCache::remove)
            }
            streamCache[url] = CachedStream(media = media, storedAt = clock.markNow())
        }
    }

    // ---- Catalog & Search --------------------------------------------------------------

    suspend fun catalog(
        catalogId: String,
        skip: Int = 0,
        genre: String? = null,
        search: String? = null,
    ): JsonObject = withContext(Dispatchers.Default) {
        val trimmedSearch = search?.trim().orEmpty()
        if (catalogId == "vortex.search" && trimmedSearch.isBlank()) {
            return@withContext buildJsonObject { putJsonArray("metas") {} }
        }
        val perPage = 25
        val page = (skip.coerceAtLeast(0) / perPage) + 1
        val normalizedGenre = genre?.trim()?.takeIf { it.isNotEmpty() && !it.equals("All", ignoreCase = true) }

        val sortList = when {
            trimmedSearch.isNotBlank() -> listOf("SEARCH_MATCH")
            catalogId == "vortex.anime_top_rated" -> listOf("SCORE_DESC", "POPULARITY_DESC")
            catalogId == "vortex.anime_popular" || normalizedGenre != null -> listOf("POPULARITY_DESC")
            else -> listOf("TRENDING_DESC", "POPULARITY_DESC")
        }

        val gql = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}search: String, ${'$'}genres: [String], ${'$'}sort: [MediaSort]) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage currentPage }
                media(type: ANIME, search: ${'$'}search, genre_in: ${'$'}genres, sort: ${'$'}sort, isAdult: false) {
                  id
                  idMal
                  title { romaji english native userPreferred }
                  description(asHtml: false)
                  coverImage { large extraLarge color }
                  bannerImage
                  format
                  episodes
                  seasonYear
                  startDate { year month day }
                  averageScore
                  popularity
                  genres
                  relations {
                    edges {
                      relationType
                      node {
                        id
                        type
                        format
                        seasonYear
                        startDate { year month day }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            if (trimmedSearch.isNotBlank()) put("search", trimmedSearch)
            if (normalizedGenre != null) {
                putJsonArray("genres") { add(normalizedGenre) }
            }
            putJsonArray("sort") { sortList.forEach(::add) }
        }

        val root = postAniList(gql, variables) ?: return@withContext buildJsonObject { putJsonArray("metas") {} }
        val rawItems = root.obj("data")?.obj("Page")?.array("media").orEmpty()
            .mapNotNull { it as? JsonObject }
        val items = deduplicateAnimeCatalogEntries(rawItems)

        buildJsonObject {
            putJsonArray("metas") {
                items.forEach { item ->
                    val id = item.int("id") ?: return@forEach
                    rememberAniListSummary(item)
                    add(aniListToPreviewJson(item, id))
                }
            }
        }
    }

    internal fun deduplicateAnimeCatalogEntries(items: List<JsonObject>): List<JsonObject> {
        if (items.size <= 1) return items
        val parentById = HashMap<Int, Int>()
        val childrenById = HashMap<Int, MutableList<Int>>()
        for (item in items) {
            val id = item.int("id") ?: continue
            val edges = item.obj("relations")?.array("edges").orEmpty().mapNotNull { it as? JsonObject }
            for (edge in edges) {
                val rel = edge.str("relationType")?.uppercase()
                val node = edge.obj("node") ?: continue
                val relId = node.int("id") ?: continue
                val relType = node.str("type")?.uppercase()
                val relFormat = node.str("format")?.uppercase()
                if (relType != null && relType != "ANIME") continue
                if (relFormat != null && relFormat !in SERIES_FORMATS) continue
                when (rel) {
                    "PREQUEL" -> parentById.putIfAbsent(id, relId)
                    "SEQUEL" -> childrenById.getOrPut(id) { mutableListOf() }.add(relId)
                }
            }
        }
        // Build connected TV franchise components via union-find / BFS across PREQUEL & SEQUEL links.
        val neighbors = HashMap<Int, MutableSet<Int>>()
        for ((child, parent) in parentById) {
            neighbors.getOrPut(child) { LinkedHashSet() }.add(parent)
            neighbors.getOrPut(parent) { LinkedHashSet() }.add(child)
        }
        for ((parent, children) in childrenById) {
            for (child in children) {
                neighbors.getOrPut(parent) { LinkedHashSet() }.add(child)
                neighbors.getOrPut(child) { LinkedHashSet() }.add(parent)
            }
        }
        val componentKeyById = HashMap<Int, Int>()
        val allIds = items.mapNotNull { it.int("id") } + neighbors.keys
        for (start in allIds) {
            if (start in componentKeyById) continue
            val queue = ArrayDeque<Int>()
            val visited = LinkedHashSet<Int>()
            queue.add(start)
            visited.add(start)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                neighbors[cur]?.forEach { nxt ->
                    if (visited.add(nxt)) queue.add(nxt)
                }
            }
            val canonical = visited.minOrNull() ?: start
            visited.forEach { componentKeyById[it] = canonical }
        }
        // Within each connected franchise present on this page, pick ONE representative entry:
        // prefer the earliest chronological TV/OVA entry (or Season 1 with no PREQUEL).
        val itemByGroup = LinkedHashMap<Int, JsonObject>()
        val orderedGroups = ArrayList<Int>()
        for (item in items) {
            val id = item.int("id") ?: continue
            val format = item.str("format")?.uppercase()
            val groupKey = if (format in SERIES_FORMATS) (componentKeyById[id] ?: id) else id
            val existing = itemByGroup[groupKey]
            if (existing == null) {
                itemByGroup[groupKey] = item
                orderedGroups += groupKey
            } else {
                val existingHasPrequel = existing.int("id")?.let { it in parentById } == true
                val currentHasPrequel = id in parentById
                val existingYear = existing.int("seasonYear") ?: existing.obj("startDate")?.int("year") ?: Int.MAX_VALUE
                val currentYear = item.int("seasonYear") ?: item.obj("startDate")?.int("year") ?: Int.MAX_VALUE
                val preferCurrent = (!currentHasPrequel && existingHasPrequel) ||
                    (currentHasPrequel == existingHasPrequel && currentYear < existingYear)
                if (preferCurrent) {
                    itemByGroup[groupKey] = item
                }
            }
        }
        return orderedGroups.mapNotNull { itemByGroup[it] }
    }

    private fun aniListToPreviewJson(item: JsonObject, anilistId: Int): JsonObject = buildJsonObject {
        val format = item.str("format")?.uppercase()
        val episodes = item.int("episodes")
        val stremioType = if (format == "MOVIE" && (episodes == null || episodes <= 1)) "movie" else "series"
        val title = preferredAniListTitle(item.obj("title")) ?: "Anime #$anilistId"
        val cover = item.obj("coverImage")
        val poster = cover?.str("extraLarge") ?: cover?.str("large")
        val background = item.str("bannerImage") ?: poster
        val desc = cleanSynopsis(item.str("description"))
        val startDate = item.obj("startDate")
        val year = item.int("seasonYear") ?: startDate?.int("year")
        val isoDate = formatStartDate(startDate) ?: year?.let { "$it-01-01" }
        val score = item.int("averageScore")?.takeIf { it > 0 }?.let { formatTenPointRating(it / 10.0) }
        val genres = item.array("genres").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

        put("id", "anilist:$anilistId")
        put("type", stremioType)
        put("name", title)
        poster?.let { put("poster", it) }
        background?.let { put("background", it) }
        desc?.let { put("description", it) }
        year?.let { put("releaseInfo", it.toString()) }
        isoDate?.let { put("released", "${it}T00:00:00.000Z") }
        score?.let { put("imdbRating", it) }
        if (genres.isNotEmpty()) putJsonArray("genres") { genres.forEach(::add) }
    }

    // ---- Metadata (AniList + Konoha CDN + AniZip, Zero TMDB) ---------------------------

    suspend fun meta(id: String): JsonObject = withContext(Dispatchers.Default) {
        val anilistId = parseAnilistId(id) ?: error("Invalid AniList id: $id")
        synchronized(lock) {
            metaJsonCache[anilistId]?.takeIf { it.second.elapsedNow().inWholeMilliseconds < META_CACHE_TTL_MS }?.first
        }?.let { return@withContext it }

        val (aniListMedia, konohaIndex, konohaEpisodes, aniZipRoot) = coroutineScope {
            val aniListDeferred = async { fetchAniListDetail(anilistId) }
            val konohaIndexDeferred = async { fetchKonohaIndex(anilistId) }
            val konohaEpsDeferred = async { fetchKonohaEpisodes(anilistId) }
            val aniZipDeferred = async { fetchAniZip(anilistId) }
            MetaBundle(
                aniList = aniListDeferred.await(),
                konohaIndex = konohaIndexDeferred.await(),
                konohaEpisodes = konohaEpsDeferred.await(),
                aniZip = aniZipDeferred.await(),
            )
        }

        val media = aniListMedia ?: konohaIndex?.let(::konohaIndexAsFallbackAniList)
            ?: error("Unable to fetch Anilumos metadata for anilist:$anilistId")

        rememberAniListSummary(media, konohaIndex)

        val format = media.str("format")?.uppercase() ?: konohaIndex?.str("format")?.uppercase()
        val totalEpisodesHint = media.int("episodes") ?: konohaIndex?.int("episodes")
        val nextAiringEp = media.obj("nextAiringEpisode")?.int("episode")
        val maxKnownEp = maxOf(
            konohaEpisodes.maxOfOrNull { it.number } ?: 0,
            parseAniZipMaxEpisode(aniZipRoot),
            nextAiringEp?.minus(1) ?: 0,
            totalEpisodesHint ?: 0,
            media.array("streamingEpisodes").size,
        )
        val isSingleMovie = format == "MOVIE" && maxKnownEp <= 1
        val stremioType = if (isSingleMovie) "movie" else "series"

        val titlesObj = media.obj("title") ?: konohaIndex?.obj("titles")
        val title = preferredAniListTitle(titlesObj) ?: "Anime #$anilistId"
        val konohaImages = konohaIndex?.obj("images")
        val cover = media.obj("coverImage")
        val poster = cover?.str("extraLarge") ?: cover?.str("large") ?: konohaImages?.str("poster")
        val trailer = media.obj("trailer")
        val background = konohaImages?.str("banner")
            ?: media.str("bannerImage")
            ?: trailer?.str("thumbnail")
            ?: poster
        val logo = konohaImages?.str("logo")?.takeIf { it.isNotBlank() } ?: extractAniZipLogo(aniZipRoot)
        val description = cleanSynopsis(media.str("description") ?: konohaIndex?.str("description"))
        val startDate = media.obj("startDate")
        val endDate = media.obj("endDate")
        val startYear = media.int("seasonYear") ?: konohaIndex?.int("season_year") ?: startDate?.int("year")
        val endYear = endDate?.int("year")
        val statusRaw = media.str("status") ?: konohaIndex?.str("status")
        val statusText = when (statusRaw?.uppercase()) {
            "FINISHED" -> "Ended"
            "RELEASING" -> "Continuing"
            "NOT_YET_RELEASED" -> "Upcoming"
            "CANCELLED" -> "Canceled"
            else -> statusRaw
        }
        val scoreInt = media.int("averageScore") ?: konohaIndex?.int("score_anilist")
        val scoreText = scoreInt?.takeIf { it > 0 }?.let { formatTenPointRating(it / 10.0) }
        val durationMin = media.int("duration") ?: konohaIndex?.int("duration")
        val genres = media.array("genres").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .ifEmpty { konohaIndex?.array("genres").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }

        val studios = media.obj("studios")?.array("nodes").orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { it.str("name")?.takeIf(String::isNotBlank) }
            .distinct()

        val castEdges = media.obj("characters")?.array("edges").orEmpty()
            .mapNotNull { it as? JsonObject }
            .take(16)

        val seasonEntries = if (isSingleMovie) {
            emptyList()
        } else {
            resolveMultiSeasonEntries(
                anchorId = anilistId,
                anchorMedia = media,
                anchorKonohaIndex = konohaIndex,
                anchorKonohaEpisodes = konohaEpisodes,
                anchorAniZip = aniZipRoot,
            )
        }
        val recommendationNodes = media.obj("recommendations")?.array("nodes").orEmpty()
            .mapNotNull { (it as? JsonObject)?.obj("mediaRecommendation") }
            .filter { (it.int("id") ?: 0) > 0 && it.bool("isAdult") != true }
            .distinctBy { it.int("id") }
            .take(12)

        val metaJson = buildJsonObject {
            put("id", "anilist:$anilistId")
            put("type", stremioType)
            put("name", title)
            poster?.let { put("poster", it) }
            background?.let { put("background", it) }
            logo?.let { put("logo", it) }
            description?.let { put("description", it) }
            startYear?.let { year ->
                val releaseInfo = when {
                    !isSingleMovie && statusText == "Ended" && endYear != null && endYear > year -> "$year-$endYear"
                    !isSingleMovie && statusText != "Ended" -> "$year-"
                    else -> year.toString()
                }
                put("releaseInfo", releaseInfo)
            }
            formatStartDate(startDate)?.let { put("released", "${it}T00:00:00.000Z") }
            statusText?.let { put("status", it) }
            scoreText?.let { put("imdbRating", it) }
            durationMin?.takeIf { it > 0 }?.let { put("runtime", "$it min") }
            if (genres.isNotEmpty()) putJsonArray("genres") { genres.forEach(::add) }
            if (studios.isNotEmpty()) {
                putJsonArray("director") { studios.take(3).forEach(::add) }
            }
            put("country", "Japan")

            val seasonPosters = seasonEntries.mapNotNull { entry ->
                entry.poster?.let { entry.seasonNumber to it }
            }
            if (castEdges.isNotEmpty() || seasonPosters.isNotEmpty()) {
                val names = castEdges.mapNotNull { edge ->
                    edge.obj("node")?.obj("name")?.str("full")
                }
                if (names.isNotEmpty()) putJsonArray("cast") { names.forEach(::add) }
                putJsonObject("app_extras") {
                    if (castEdges.isNotEmpty()) {
                        putJsonArray("cast") {
                            castEdges.forEach { edge ->
                                val charNode = edge.obj("node") ?: return@forEach
                                val charName = charNode.obj("name")?.str("full") ?: return@forEach
                                val charImage = charNode.obj("image")?.str("large")
                                val va = edge.array("voiceActors").firstOrNull() as? JsonObject
                                val vaName = va?.obj("name")?.str("full")
                                val vaPhoto = va?.obj("image")?.str("large")
                                addJsonObject {
                                    put("name", vaName ?: charName)
                                    put("character", charName)
                                    (vaPhoto ?: charImage)?.let { put("photo", it) }
                                }
                            }
                        }
                    }
                    if (seasonPosters.isNotEmpty()) {
                        putJsonObject("seasonPosters") {
                            seasonPosters.forEach { (num, img) -> put(num.toString(), img) }
                        }
                    }
                }
            }

            val trailerId = trailer?.str("id")?.takeIf { it.isNotBlank() }
            val trailerSite = trailer?.str("site")?.lowercase()
            if (trailerId != null && trailerSite == "youtube") {
                putJsonArray("trailers") {
                    addJsonObject {
                        put("source", trailerId)
                        put("key", trailerId)
                        put("name", "$title Trailer")
                        put("type", "Trailer")
                        put("site", "YouTube")
                        put("official", true)
                    }
                }
            }

            if (!isSingleMovie) {
                putJsonArray("videos") {
                    seasonEntries.forEach { seasonEntry ->
                        val sId = seasonEntry.anilistId
                        val sNum = seasonEntry.seasonNumber
                        val konohaByEp = seasonEntry.konohaEpisodes.associateBy { it.number }
                        val aniZipEpisodes = parseAniZipEpisodes(seasonEntry.aniZipRoot)
                        val streamingEps = seasonEntry.media.array("streamingEpisodes").mapNotNull { it as? JsonObject }
                        val sBackdrop = seasonEntry.media.str("bannerImage") ?: seasonEntry.poster ?: background
                        val sDuration = seasonEntry.media.int("duration") ?: durationMin
                        val releasedCount = seasonEntry.releasedEpisodeCount
                        for (ep in 1..releasedCount) {
                            val kEp = konohaByEp[ep]
                            val zEp = aniZipEpisodes[ep]
                            val sEp = streamingEps.getOrNull(ep - 1)
                            val epTitle = kEp?.title?.takeIf { it.isNotBlank() }
                                ?: zEp?.title?.takeIf { it.isNotBlank() }
                                ?: sEp?.str("title")?.substringAfter(" - ")?.takeIf { it.isNotBlank() }
                                ?: "Episode $ep"
                            val epThumb = kEp?.still?.takeIf { it.isNotBlank() }
                                ?: zEp?.still?.takeIf { it.isNotBlank() }
                                ?: sEp?.str("thumbnail")?.takeIf { it.isNotBlank() }
                                ?: sBackdrop
                            val epOverview = kEp?.overview?.takeIf { it.isNotBlank() } ?: zEp?.overview
                            val epAir = kEp?.airDate?.takeIf { it.isNotBlank() } ?: zEp?.airDate
                            val epRuntime = kEp?.runtime ?: zEp?.runtime ?: sDuration
                            val epRating = zEp?.rating

                            addJsonObject {
                                put("id", "anilist:$sId:$sNum:$ep")
                                put("title", epTitle)
                                put("season", sNum)
                                put("episode", ep)
                                epAir?.let { put("released", if (it.contains('T')) it else "${it}T00:00:00.000Z") }
                                epThumb?.let { put("thumbnail", it) }
                                epOverview?.let { put("overview", it) }
                                epRuntime?.takeIf { it > 0 }?.let { put("runtime", "$it min") }
                                epRating?.let { put("rating", it) }
                            }
                        }
                    }
                }
            } else {
                putJsonObject("behaviorHints") { put("defaultVideoId", "anilist:$anilistId:1:1") }
            }

            if (recommendationNodes.isNotEmpty()) {
                putJsonArray("moreLikeThis") {
                    recommendationNodes.forEach { rec ->
                        val recId = rec.int("id") ?: return@forEach
                        add(aniListToPreviewJson(rec, recId))
                    }
                }
            }
        }

        val wrapped = buildJsonObject { put("meta", metaJson) }
        synchronized(lock) {
            if (metaJsonCache.size >= 32) {
                metaJsonCache.entries.firstOrNull()?.key?.let(metaJsonCache::remove)
            }
            metaJsonCache[anilistId] = wrapped to clock.markNow()
        }
        wrapped
    }

    private data class MetaBundle(
        val aniList: JsonObject?,
        val konohaIndex: JsonObject?,
        val konohaEpisodes: List<KonohaEpisodeEntry>,
        val aniZip: JsonObject?,
    )

    // ---- Streams & Direct Extraction ---------------------------------------------------

    suspend fun streams(id: String): JsonObject = withContext(Dispatchers.Default) {
        val target = parseAnilistTarget(videoId = id)
            ?: return@withContext buildJsonObject { putJsonArray("streams") {} }
        val anilistId = target.anilistId
        val episode = target.episode

        val availability = runCatching {
            withTimeoutOrNull(1_500L) { fetchAniPmAvailability(anilistId) }
        }.getOrNull()

        val dubConfirmed = availability != null && episode in availability.dub
        val dubNote = if (dubConfirmed) "English Dub HLS" else "English Dub HLS (when available)"

        buildJsonObject {
            putJsonArray("streams") {
                addJsonObject {
                    put("name", "Vortex Anime Server 1")
                    put("title", "Vortex Anime Server 1")
                    put("description", "AniPm Settlar · Subtitled HLS + WebVTT subtitles & OP/ED skip")
                    put("url", "anilumos://anipm/$anilistId/$episode/sub")
                    putJsonObject("behaviorHints") { put("bingeGroup", "anilumos-anipm-sub") }
                }
                addJsonObject {
                    put("name", "Vortex Anime Server 2")
                    put("title", "Vortex Anime Server 2")
                    put("description", "AniPm Settlar · $dubNote")
                    put("url", "anilumos://anipm/$anilistId/$episode/dub")
                    putJsonObject("behaviorHints") { put("bingeGroup", "anilumos-anipm-dub") }
                }
                addJsonObject {
                    put("name", "Vortex Anime Server 3")
                    put("title", "Vortex Anime Server 3")
                    put("description", "AniChan CDN · Subtitled 1080p HLS + WebVTT")
                    put("url", "anilumos://anichan/$anilistId/$episode/sub")
                    putJsonObject("behaviorHints") { put("bingeGroup", "anilumos-anichan-sub") }
                }
                addJsonObject {
                    put("name", "Vortex Anime Server 4")
                    put("title", "Vortex Anime Server 4")
                    put("description", "AniChan CDN · $dubNote + WebVTT")
                    put("url", "anilumos://anichan/$anilistId/$episode/dub")
                    putJsonObject("behaviorHints") { put("bingeGroup", "anilumos-anichan-dub") }
                }
                addJsonObject {
                    put("name", "Vortex Anime Server 5")
                    put("title", "Vortex Anime Server 5")
                    put("description", "AnimeHeaven · Direct progressive HD MP4")
                    put("url", "anilumos://animeheaven/$anilistId/$episode/sub")
                    putJsonObject("behaviorHints") { put("bingeGroup", "anilumos-animeheaven-sub") }
                }
            }
        }
    }

    suspend fun extractBestForTarget(
        anilistId: Int,
        episode: Int,
        failedUrls: Set<String> = emptySet(),
        preferDub: Boolean = false,
    ): VortexExtractedMedia? {
        val candidates = buildList {
            if (preferDub) {
                add("anilumos://anipm/$anilistId/$episode/dub")
                add("anilumos://anichan/$anilistId/$episode/dub")
            }
            add("anilumos://anipm/$anilistId/$episode/sub")
            add("anilumos://anichan/$anilistId/$episode/sub")
            add("anilumos://animeheaven/$anilistId/$episode/sub")
            if (!preferDub) {
                add("anilumos://anipm/$anilistId/$episode/dub")
                add("anilumos://anichan/$anilistId/$episode/dub")
            }
        }.filterNot { it in failedUrls }

        var winner: VortexExtractedMedia? = null
        for (candidateUrl in candidates) {
            val resolved = extractUrl(candidateUrl)
            if (resolved != null) {
                winner = resolved
                break
            }
        }
        return winner
    }

    fun fallbackCandidatesFor(url: String?): List<String> {
        val spec = parseStreamUrl(url) ?: return emptyList()
        val id = spec.anilistId
        val ep = spec.episode
        val all = listOf(
            "anilumos://anipm/$id/$ep/sub",
            "anilumos://anichan/$id/$ep/sub",
            "anilumos://animeheaven/$id/$ep/sub",
            "anilumos://anipm/$id/$ep/dub",
            "anilumos://anichan/$id/$ep/dub",
        )
        return all.filterNot { it.equals(url, ignoreCase = true) }
    }

    suspend fun extractUrl(url: String): VortexExtractedMedia? = withContext(Dispatchers.Default) {
        val spec = parseStreamUrl(url) ?: return@withContext null
        if (spec.provider == "auto") {
            return@withContext extractBestForTarget(
                anilistId = spec.anilistId,
                episode = spec.episode,
                preferDub = spec.audio == "dub",
            )
        }
        cachedStream(url)?.let { return@withContext it }

        val extracted = when (spec.provider) {
            "anipm" -> resolveAniPm(spec.anilistId, spec.episode, spec.audio, url)
            "anichan" -> resolveAniChan(spec.anilistId, spec.episode, spec.audio, url)
            "animeheaven" -> resolveAnimeHeaven(spec.anilistId, spec.episode, url)
            else -> null
        }
        if (extracted != null) {
            rememberStream(url, extracted)
        }
        extracted
    }

    suspend fun prewarm(anilistId: Int, episode: Int = 1): VortexExtractedMedia? =
        withTimeoutOrNull(ANIPM_TIMEOUT_MS) {
            extractUrl("anilumos://anipm/$anilistId/$episode/sub")
        }

    // ---- Provider 1: AniPm / Settlar ---------------------------------------------------

    private suspend fun fetchAniPmAvailability(anilistId: Int): EpisodeAvailability? {
        synchronized(lock) {
            availabilityCache[anilistId]
                ?.takeIf { it.storedAt.elapsedNow().inWholeMilliseconds < PACKAGES_CACHE_TTL_MS }
                ?.availability
        }?.let { return it }

        val root = getJson(
            url = "$ANIPM_BASE/api/anime/anipm-server/_packages?anilistId=$anilistId",
            headers = aniPmHeaders(),
        ) ?: return null

        val episodes = root.obj("episodes") ?: return null
        val sub = LinkedHashSet<Int>()
        val dub = LinkedHashSet<Int>()
        for ((key, value) in episodes) {
            val epNum = key.toIntOrNull()?.takeIf { it > 0 } ?: continue
            val entry = value as? JsonObject ?: continue
            if (entry.bool("sub") == true) sub += epNum
            if (entry.bool("dub") == true) dub += epNum
        }
        val result = EpisodeAvailability(sub = sub, dub = dub)
        synchronized(lock) {
            if (availabilityCache.size >= 48) {
                availabilityCache.entries.firstOrNull()?.key?.let(availabilityCache::remove)
            }
            availabilityCache[anilistId] = CachedAvailability(result, clock.markNow())
        }
        return result
    }

    private suspend fun resolveAniPm(
        anilistId: Int,
        episode: Int,
        audio: String,
        descriptorUrl: String,
    ): VortexExtractedMedia? = withTimeoutOrNull(ANIPM_TIMEOUT_MS) {
        val channel = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        val headers = aniPmHeaders()
        val bootstrap = getJson(
            url = "$ANIPM_BASE/api/anime/playback-bootstrap/anilist/$anilistId?ep=$episode&lang=$channel&core=0",
            headers = headers,
        ) ?: return@withTimeoutOrNull null

        val effective = bootstrap.str("effectiveLanguage")
        if (!effective.isNullOrBlank() && !effective.equals(channel, ignoreCase = true)) {
            log.w { "AniPm has no $channel for anilist:$anilistId ep=$episode (offered=$effective)" }
            return@withTimeoutOrNull null
        }

        val skipObj = bootstrap.obj("skip")
        if (skipObj != null) {
            val op = skipObj.obj("op")
            val ed = skipObj.obj("ed")
            val window = AnilumosSkipWindow(
                introStart = op?.dbl("start"),
                introEnd = op?.dbl("end"),
                outroStart = ed?.dbl("start"),
                outroEnd = ed?.dbl("end"),
            )
            if ((window.introEnd ?: 0.0) > (window.introStart ?: 0.0) ||
                (window.outroEnd ?: 0.0) > (window.outroStart ?: 0.0)
            ) {
                synchronized(lock) {
                    if (skipWindowsByKey.size >= 64) {
                        skipWindowsByKey.entries.firstOrNull()?.key?.let(skipWindowsByKey::remove)
                    }
                    skipWindowsByKey["$anilistId:$episode"] = window
                }
            }
        }

        val selection = bootstrap.str("settlarSelection") ?: return@withTimeoutOrNull null
        val sessionResp = getJson(
            url = "$ANIPM_BASE/api/anime/settlar/session?selection=${selection.encodeQueryValue()}&provider=anipm&ep=$episode&channel=$channel&telemetry=0",
            headers = headers,
        ) ?: return@withTimeoutOrNull null
        val embedUrl = sessionResp.str("embedUrl") ?: return@withTimeoutOrNull null
        val token = extractQueryParam(embedUrl, "t") ?: return@withTimeoutOrNull null

        val embedSession = getJson(
            url = "$SETTLAR_EMBED_BASE/api/embed/session?t=${token.encodeQueryValue()}",
            headers = headers,
        ) ?: return@withTimeoutOrNull null

        val sourceUrl = embedSession.str("source")?.takeIf { it.startsWith("https://") }
            ?: return@withTimeoutOrNull null
        val kind = embedSession.str("kind")?.lowercase() ?: "hls"
        val subtitles = embedSession.array("subtitles")
            .mapNotNull { it as? JsonObject }
            .filter { it.str("url")?.startsWith("https://") == true }
            .sortedByDescending { it.bool("default") == true }
            .map { track ->
                val rawUrl = track.str("url").orEmpty()
                val rawCode = track.str("srclang").orEmpty().lowercase()
                val lang = SETTLAR_LANG_MAP[rawCode] ?: rawCode.ifBlank { "en" }
                val label = track.str("label")?.trim()?.takeIf { it.isNotEmpty() } ?: "Subtitles"
                StreamSubtitle(
                    url = rawUrl,
                    language = lang,
                    name = label,
                    headers = mapOf("User-Agent" to CHROME_MOBILE_UA),
                )
            }
            .distinctBy(StreamSubtitle::url)

        log.i { "AniPm resolved anilist:$anilistId ep=$episode ($channel): subs=${subtitles.size}" }
        VortexExtractedMedia(
            url = sourceUrl,
            headers = mapOf("User-Agent" to CHROME_MOBILE_UA),
            subtitles = subtitles,
            streamType = kind,
            embedUrl = descriptorUrl,
            server = null,
            displayNameOverride = displayNameForStreamUrl(descriptorUrl),
        )
    }

    // ---- Provider 2: AniChan -----------------------------------------------------------

    private suspend fun resolveAniChan(
        anilistId: Int,
        episode: Int,
        audio: String,
        descriptorUrl: String,
    ): VortexExtractedMedia? = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
        val category = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        var cookie = ensureAniChanSessionCookie() ?: return@withTimeoutOrNull null
        val url = "$ANICHAN_BASE/api/watch/servers?anilistId=$anilistId&ep=$episode&category=$category"
        var resp = runCatching {
            httpRequestRaw(
                method = "GET",
                url = url,
                headers = mapOf(
                    "User-Agent" to CHROME_MOBILE_UA,
                    "Accept" to "application/json",
                    "Referer" to "$ANICHAN_BASE/",
                    "Cookie" to cookie,
                ),
                body = "",
                followRedirects = true,
            )
        }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return@withTimeoutOrNull null

        if (resp.status == 401) {
            synchronized(lock) { aniChanSession = null }
            cookie = ensureAniChanSessionCookie() ?: return@withTimeoutOrNull null
            resp = runCatching {
                httpRequestRaw(
                    method = "GET",
                    url = url,
                    headers = mapOf(
                        "User-Agent" to CHROME_MOBILE_UA,
                        "Accept" to "application/json",
                        "Referer" to "$ANICHAN_BASE/",
                        "Cookie" to cookie,
                    ),
                    body = "",
                    followRedirects = true,
                )
            }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return@withTimeoutOrNull null
        }

        if (resp.status !in 200..299) return@withTimeoutOrNull null
        val root = runCatching { json.parseToJsonElement(resp.body) as? JsonObject }.getOrNull()
            ?: return@withTimeoutOrNull null
        val servers = root.array("servers").mapNotNull { it as? JsonObject }
            .filterNot {
                val name = it.str("name").orEmpty()
                name.startsWith("megaplay", ignoreCase = true) || name.equals("vidhawk", ignoreCase = true)
            }
            .filter { !it.str("stream").isNullOrBlank() }
            .sortedBy { it.int("rank") ?: Int.MAX_VALUE }
        val chosen = servers.firstOrNull() ?: return@withTimeoutOrNull null
        val rawStream = chosen.str("stream") ?: return@withTimeoutOrNull null
        val streamUrl = absoluteAniChanUrl(rawStream)
        val subtitles = chosen.array("subtitles").mapNotNull { it as? JsonObject }.mapNotNull { track ->
            val subUrl = track.str("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = track.str("lang")?.trim()?.takeIf { it.isNotBlank() } ?: "English"
            StreamSubtitle(
                url = absoluteAniChanUrl(subUrl),
                language = normalizeSubtitleLangLabel(label),
                name = label,
                headers = mapOf("User-Agent" to CHROME_MOBILE_UA),
            )
        }.distinctBy(StreamSubtitle::url)

        log.i { "AniChan resolved anilist:$anilistId ep=$episode ($category): subs=${subtitles.size}" }
        VortexExtractedMedia(
            url = streamUrl,
            headers = mapOf("User-Agent" to CHROME_MOBILE_UA),
            subtitles = subtitles,
            streamType = "hls",
            embedUrl = descriptorUrl,
            server = null,
            displayNameOverride = displayNameForStreamUrl(descriptorUrl),
        )
    }

    private suspend fun ensureAniChanSessionCookie(): String? {
        synchronized(lock) {
            aniChanSession?.takeIf { it.storedAt.elapsedNow().inWholeMilliseconds < ANICHAN_SESSION_TTL_MS }?.cookie
        }?.let { return it }

        val resp = runCatching {
            httpRequestRaw(
                method = "POST",
                url = "$ANICHAN_BASE/api/watch/session",
                headers = mapOf(
                    "User-Agent" to CHROME_MOBILE_UA,
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "Referer" to "$ANICHAN_BASE/",
                ),
                body = """{"token":""}""",
                followRedirects = true,
            )
        }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return null
        if (resp.status !in 200..299) return null

        val setCookieHeader = resp.headers.entries
            .firstOrNull { (k, _) -> k.equals("Set-Cookie", ignoreCase = true) }
            ?.value
            ?: return null
        val cookiePair = ANICHAN_COOKIE_REGEX.find(setCookieHeader)?.let { "anichan_ws=${it.groupValues[1]}" }
            ?: return null

        synchronized(lock) {
            aniChanSession = AniChanSession(cookie = cookiePair, storedAt = clock.markNow())
        }
        return cookiePair
    }

    // ---- Provider 3: AnimeHeaven (Direct MP4) ------------------------------------------

    private suspend fun resolveAnimeHeaven(
        anilistId: Int,
        episode: Int,
        descriptorUrl: String,
    ): VortexExtractedMedia? = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
        val titles = resolveTitleCandidates(anilistId)
        val primaryQuery = titles.firstOrNull() ?: return@withTimeoutOrNull null
        val searchHtml = getText(
            url = "$ANIMEHEAVEN_BASE/search.php?s=${primaryQuery.encodeQueryValue()}",
            headers = mapOf("User-Agent" to CHROME_MOBILE_UA),
        ) ?: return@withTimeoutOrNull null

        val searchResults = parseAnimeHeavenSearch(searchHtml)
        val bestMatch = pickBestAnimeHeavenMatch(searchResults, titles) ?: return@withTimeoutOrNull null
        val animeUrl = "$ANIMEHEAVEN_BASE/${bestMatch.path.trimStart('/')}"
        val animeHtml = getText(
            url = animeUrl,
            headers = mapOf("User-Agent" to CHROME_MOBILE_UA, "Referer" to "$ANIMEHEAVEN_BASE/"),
        ) ?: return@withTimeoutOrNull null

        val epEntry = parseAnimeHeavenEpisodes(animeHtml).firstOrNull { it.number == episode }
            ?: return@withTimeoutOrNull null
        val gateHtml = getText(
            url = "$ANIMEHEAVEN_BASE/gate.php",
            headers = mapOf(
                "User-Agent" to CHROME_MOBILE_UA,
                "Referer" to animeUrl,
                "Cookie" to "key=${epEntry.gateKey}",
            ),
        ) ?: return@withTimeoutOrNull null

        val videoUrl = parseAnimeHeavenVideoUrls(gateHtml, epEntry.gateKey).firstOrNull()
            ?: return@withTimeoutOrNull null

        log.i { "AnimeHeaven resolved anilist:$anilistId ep=$episode: direct mp4" }
        VortexExtractedMedia(
            url = videoUrl,
            headers = mapOf("User-Agent" to CHROME_MOBILE_UA),
            subtitles = emptyList(),
            streamType = "mp4",
            embedUrl = descriptorUrl,
            server = null,
            displayNameOverride = displayNameForStreamUrl(descriptorUrl),
        )
    }

    // ---- Metadata Sources (AniList + Konoha CDN + AniZip) -------------------------------

    private suspend fun resolveTitleCandidates(anilistId: Int): List<String> {
        synchronized(lock) { metaSummaryCache[anilistId]?.titles }?.takeIf { it.isNotEmpty() }?.let { return it }
        val konoha = fetchKonohaIndex(anilistId)
        val ani = if (konoha == null) fetchAniListDetail(anilistId) else null
        val titlesObj = ani?.obj("title") ?: konoha?.obj("titles")
        val list = listOfNotNull(
            titlesObj?.str("english"),
            titlesObj?.str("romaji"),
            titlesObj?.str("userPreferred"),
        ).map(String::trim).filter(String::isNotEmpty).distinct()
        if (list.isNotEmpty()) {
            synchronized(lock) {
                metaSummaryCache[anilistId] = CachedMetaSummary(
                    anilistId = anilistId,
                    malId = ani?.int("idMal") ?: konoha?.obj("ids")?.int("mal"),
                    titles = list,
                    storedAt = clock.markNow(),
                )
            }
        }
        return list
    }

    private suspend fun postAniList(query: String, variables: JsonObject): JsonObject? {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()
        val resp = runCatching {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                httpRequestRaw(
                    method = "POST",
                    url = ANILIST_ENDPOINT,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Accept" to "application/json",
                        "User-Agent" to CHROME_MOBILE_UA,
                    ),
                    body = body,
                    followRedirects = true,
                    maxResponseBodyBytes = MAX_LARGE_RESPONSE_BYTES,
                )
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return null
        if (resp.status !in 200..299 || resp.body.isBlank()) return null
        return runCatching { json.parseToJsonElement(resp.body) as? JsonObject }.getOrNull()
    }

    private data class SeasonEntryData(
        val anilistId: Int,
        val seasonNumber: Int,
        val media: JsonObject,
        val poster: String?,
        val konohaEpisodes: List<KonohaEpisodeEntry>,
        val aniZipRoot: JsonObject?,
        val releasedEpisodeCount: Int,
    )

    private suspend fun resolveMultiSeasonEntries(
        anchorId: Int,
        anchorMedia: JsonObject,
        anchorKonohaIndex: JsonObject?,
        anchorKonohaEpisodes: List<KonohaEpisodeEntry>,
        anchorAniZip: JsonObject?,
    ): List<SeasonEntryData> {
        val relationNodes = LinkedHashMap<Int, JsonObject>()
        anchorMedia.obj("relations")?.array("edges").orEmpty()
            .mapNotNull { it as? JsonObject }
            .forEach { edge ->
                val relType = edge.str("relationType")?.uppercase()
                val node = edge.obj("node") ?: return@forEach
                val nodeId = node.int("id") ?: return@forEach
                val nodeFormat = node.str("format")?.uppercase()
                val nodeType = node.str("type")?.uppercase()
                if ((relType == "PREQUEL" || relType == "SEQUEL") &&
                    (nodeType == null || nodeType == "ANIME") &&
                    nodeFormat in SERIES_FORMATS
                ) {
                    relationNodes[nodeId] = node
                }
            }

        val konohaSeriesIds = anchorKonohaIndex?.array("series").orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { id -> id > 0 } }
            .filter { id -> id == anchorId || id !in relationNodes || relationNodes[id]?.str("format")?.uppercase() in SERIES_FORMATS }

        val candidateIds = (konohaSeriesIds + listOf(anchorId) + relationNodes.keys.toList())
            .distinct()
            .take(12)

        val extraMissingIds = candidateIds.filter { it != anchorId }
        val batchMediaById = if (extraMissingIds.isNotEmpty()) {
            fetchAniListBatch(extraMissingIds)
                .mapNotNull { m -> m.int("id")?.let { it to m } }
                .toMap()
        } else {
            emptyMap()
        }

        // Also include 2nd-hop prequels/sequels discovered on fetched batch nodes (e.g. when anchor is S3).
        val secondHopIds = batchMediaById.values
            .flatMap { m ->
                m.obj("relations")?.array("edges").orEmpty()
                    .mapNotNull { edge ->
                        val obj = edge as? JsonObject ?: return@mapNotNull null
                        val relType = obj.str("relationType")?.uppercase()
                        val node = obj.obj("node") ?: return@mapNotNull null
                        val nId = node.int("id") ?: return@mapNotNull null
                        val nFmt = node.str("format")?.uppercase()
                        val nType = node.str("type")?.uppercase()
                        if ((relType == "PREQUEL" || relType == "SEQUEL") &&
                            (nType == null || nType == "ANIME") &&
                            nFmt in SERIES_FORMATS &&
                            nId != anchorId &&
                            nId !in batchMediaById
                        ) nId else null
                    }
            }
            .distinct()
            .take(8)
        val secondHopById = if (secondHopIds.isNotEmpty()) {
            fetchAniListBatch(secondHopIds)
                .mapNotNull { m -> m.int("id")?.let { it to m } }
                .toMap()
        } else {
            emptyMap()
        }

        val allMediaMap = LinkedHashMap<Int, JsonObject>().apply {
            relationNodes.forEach { (id, node) -> put(id, node) }
            secondHopById.forEach { (id, node) -> put(id, node) }
            batchMediaById.forEach { (id, node) -> put(id, node) }
            put(anchorId, anchorMedia)
        }

        val orderedSeasons = orderSeasonChainMedia(
            anchorId = anchorId,
            mediaMap = allMediaMap,
            konohaSeriesOrder = konohaSeriesIds,
        ).take(12)

        return coroutineScope {
            orderedSeasons.mapIndexed { idx, sMedia ->
                async {
                    val sId = sMedia.int("id") ?: anchorId
                    val sNum = idx + 1
                    val sStatus = sMedia.str("status")?.uppercase()
                    val kEps = if (sId == anchorId) {
                        anchorKonohaEpisodes
                    } else {
                        withTimeoutOrNull(2_500L) { fetchKonohaEpisodes(sId) }.orEmpty()
                    }
                    val zRoot = if (sId == anchorId) {
                        anchorAniZip
                    } else {
                        withTimeoutOrNull(2_500L) { fetchAniZip(sId) }
                    }
                    val nextAiring = sMedia.obj("nextAiringEpisode")?.int("episode")
                    val totalHint = sMedia.int("episodes")
                    val maxKnown = maxOf(
                        kEps.maxOfOrNull { it.number } ?: 0,
                        parseAniZipMaxEpisode(zRoot),
                        nextAiring?.minus(1) ?: 0,
                        totalHint ?: 0,
                        sMedia.array("streamingEpisodes").size,
                    )
                    val released = when {
                        sStatus == "NOT_YET_RELEASED" -> 0
                        nextAiring != null && nextAiring > 1 -> maxOf(nextAiring - 1, kEps.maxOfOrNull { it.number } ?: 0)
                        maxKnown > 0 -> maxKnown
                        sStatus == "RELEASING" -> 1
                        else -> 12
                    }
                    val cover = sMedia.obj("coverImage")
                    val sPoster = cover?.str("extraLarge") ?: cover?.str("large")
                    SeasonEntryData(
                        anilistId = sId,
                        seasonNumber = sNum,
                        media = sMedia,
                        poster = sPoster,
                        konohaEpisodes = kEps,
                        aniZipRoot = zRoot,
                        releasedEpisodeCount = released,
                    )
                }
            }.map { it.await() }.filter { it.releasedEpisodeCount > 0 || it.anilistId == anchorId }
        }.let { entries ->
            entries.mapIndexed { index, entry -> entry.copy(seasonNumber = index + 1) }
        }
    }

    internal fun orderSeasonChainMedia(
        anchorId: Int,
        mediaMap: Map<Int, JsonObject>,
        konohaSeriesOrder: List<Int> = emptyList(),
    ): List<JsonObject> {
        val filtered = mediaMap.entries.mapNotNull { (id, m) ->
            val fmt = m.str("format")?.uppercase()
            if (id == anchorId || fmt == null || fmt in SERIES_FORMATS) id to m else null
        }.toMap()
        if (filtered.size <= 1) return listOfNotNull(filtered[anchorId])

        if (konohaSeriesOrder.size > 1 && anchorId in konohaSeriesOrder) {
            val fromKonoha = konohaSeriesOrder.mapNotNull { filtered[it] }
            if (fromKonoha.size > 1) return fromKonoha.distinctBy { it.int("id") }
        }

        // Chronological sort using AniList startDate -> seasonYear -> id
        return filtered.values.sortedWith(
            compareBy<JsonObject>(
                { mediaStartSortKey(it) },
                { it.int("id") ?: Int.MAX_VALUE },
            ),
        ).distinctBy { it.int("id") }
    }

    private fun mediaStartSortKey(media: JsonObject): Long {
        val sd = media.obj("startDate")
        val y = sd?.int("year") ?: media.int("seasonYear") ?: 9999
        val m = sd?.int("month") ?: 1
        val d = sd?.int("day") ?: 1
        return y.toLong() * 10_000L + m.toLong() * 100L + d.toLong()
    }

    private suspend fun fetchAniListDetail(anilistId: Int): JsonObject? {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                idMal
                title { romaji english native userPreferred }
                description(asHtml: false)
                coverImage { large extraLarge color }
                bannerImage
                format
                season
                seasonYear
                episodes
                duration
                status
                averageScore
                genres
                studios(isMain: true) { nodes { id name isAnimationStudio } }
                trailer { id site thumbnail }
                nextAiringEpisode { episode airingAt }
                startDate { year month day }
                endDate { year month day }
                streamingEpisodes { title thumbnail url site }
                relations {
                  edges {
                    relationType
                    node {
                      id
                      idMal
                      type
                      format
                      status
                      episodes
                      duration
                      seasonYear
                      startDate { year month day }
                      title { romaji english native userPreferred }
                      coverImage { large extraLarge }
                      bannerImage
                      isAdult
                    }
                  }
                }
                recommendations(perPage: 12, sort: [RATING_DESC]) {
                  nodes {
                    mediaRecommendation {
                      id
                      title { romaji english native userPreferred }
                      description(asHtml: false)
                      coverImage { large extraLarge color }
                      bannerImage
                      format
                      episodes
                      seasonYear
                      startDate { year month day }
                      averageScore
                      genres
                      isAdult
                    }
                  }
                }
                characters(sort: [ROLE, RELEVANCE], perPage: 16) {
                  edges {
                    role
                    node { name { full } image { large } }
                    voiceActors(language: JAPANESE) { name { full } image { large } }
                  }
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject { put("id", anilistId) }
        return postAniList(gql, vars)?.obj("data")?.obj("Media")
    }

    private suspend fun fetchAniListBatch(ids: List<Int>): List<JsonObject> {
        val cleanIds = ids.distinct().take(20)
        if (cleanIds.isEmpty()) return emptyList()
        val gql = """
            query (${'$'}ids: [Int]) {
              Page(page: 1, perPage: 20) {
                media(id_in: ${'$'}ids, type: ANIME) {
                  id
                  idMal
                  title { romaji english native userPreferred }
                  format
                  status
                  episodes
                  duration
                  seasonYear
                  startDate { year month day }
                  coverImage { large extraLarge }
                  bannerImage
                  nextAiringEpisode { episode airingAt }
                  relations {
                    edges {
                      relationType
                      node {
                        id
                        type
                        format
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            putJsonArray("ids") { cleanIds.forEach(::add) }
        }
        return postAniList(gql, vars)?.obj("data")?.obj("Page")?.array("media").orEmpty()
            .mapNotNull { it as? JsonObject }
    }

    private suspend fun fetchKonohaIndex(anilistId: Int): JsonObject? {
        val shard = anilistId / 1000
        return getJson(
            url = "$KONOHA_DATA_BASE/anime/$shard/$anilistId/index.json",
            headers = mapOf("Accept" to "application/json"),
            maxBytes = MAX_LARGE_RESPONSE_BYTES,
        )
    }

    private suspend fun fetchKonohaEpisodes(anilistId: Int): List<KonohaEpisodeEntry> {
        val shard = anilistId / 1000
        val text = getText(
            url = "$KONOHA_DATA_BASE/anime/$shard/$anilistId/episodes.json",
            headers = mapOf("Accept" to "application/json"),
            maxBytes = MAX_LARGE_RESPONSE_BYTES,
        ) ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val num = obj.dbl("number")?.roundToInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            KonohaEpisodeEntry(
                number = num,
                title = obj.str("title"),
                overview = cleanSynopsis(obj.str("overview")),
                airDate = obj.str("air_date"),
                still = obj.str("still"),
                runtime = obj.int("runtime"),
            )
        }
    }

    private suspend fun fetchAniZip(anilistId: Int): JsonObject? =
        getJson(
            url = "$ANIZIP_BASE?anilist_id=$anilistId",
            headers = mapOf("Accept" to "application/json"),
            maxBytes = MAX_LARGE_RESPONSE_BYTES,
        )

    private fun extractAniZipLogo(root: JsonObject?): String? {
        val images = root?.array("images").orEmpty().mapNotNull { it as? JsonObject }
        return images.firstOrNull { it.str("coverType").equals("Clearlogo", ignoreCase = true) }
            ?.str("url")
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseAniZipMaxEpisode(root: JsonObject?): Int {
        val eps = root?.obj("episodes") ?: return 0
        return eps.keys.mapNotNull { it.toIntOrNull()?.takeIf { n -> n > 0 } }.maxOrNull() ?: 0
    }

    private fun parseAniZipEpisodes(root: JsonObject?): Map<Int, AniZipEpisodeEntry> {
        val eps = root?.obj("episodes") ?: return emptyMap()
        val map = LinkedHashMap<Int, AniZipEpisodeEntry>()
        for ((key, value) in eps) {
            val epNum = key.toIntOrNull()?.takeIf { it > 0 } ?: continue
            val obj = value as? JsonObject ?: continue
            val titleObj = obj.obj("title")
            val epTitle = titleObj?.str("en")
                ?: titleObj?.str("x-jat")
                ?: titleObj?.str("ja")
            val ratingRaw = obj.str("rating")?.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: obj.dbl("rating")?.takeIf { it > 0.0 }
            map[epNum] = AniZipEpisodeEntry(
                title = epTitle,
                overview = cleanSynopsis(obj.str("summary") ?: obj.str("overview")),
                still = obj.str("image"),
                airDate = obj.str("airDate") ?: obj.str("airdate"),
                runtime = obj.int("length") ?: obj.int("runtime"),
                rating = ratingRaw?.let(::formatTenPointRating),
            )
        }
        return map
    }

    private fun rememberAniListSummary(media: JsonObject, konohaIndex: JsonObject? = null) {
        val id = media.int("id") ?: return
        val titlesObj = media.obj("title") ?: konohaIndex?.obj("titles")
        val titles = listOfNotNull(
            titlesObj?.str("english"),
            titlesObj?.str("romaji"),
            titlesObj?.str("userPreferred"),
        ).map(String::trim).filter(String::isNotEmpty).distinct()
        val malId = media.int("idMal") ?: konohaIndex?.obj("ids")?.int("mal")
        synchronized(lock) {
            if (metaSummaryCache.size >= 120) {
                metaSummaryCache.entries.firstOrNull()?.key?.let(metaSummaryCache::remove)
            }
            metaSummaryCache[id] = CachedMetaSummary(id, malId, titles, clock.markNow())
        }
    }

    private fun konohaIndexAsFallbackAniList(konoha: JsonObject): JsonObject = buildJsonObject {
        val ids = konoha.obj("ids")
        ids?.int("anilist")?.let { put("id", it) }
        ids?.int("mal")?.let { put("idMal", it) }
        konoha.obj("titles")?.let { put("title", it) }
        konoha.str("format")?.let { put("format", it) }
        konoha.str("status")?.let { put("status", it) }
        konoha.int("season_year")?.let { put("seasonYear", it) }
        konoha.int("episodes")?.let { put("episodes", it) }
        konoha.int("duration")?.let { put("duration", it) }
        konoha.str("description")?.let { put("description", it) }
        konoha.int("score_anilist")?.let { put("averageScore", it) }
        put("genres", JsonArray(konoha.array("genres")))
        konoha.obj("images")?.let { imgs ->
            putJsonObject("coverImage") {
                imgs.str("poster")?.let { put("large", it); put("extraLarge", it) }
            }
            imgs.str("banner")?.let { put("bannerImage", it) }
        }
    }

    // ---- HTML & Network Helpers (KMP safe) ---------------------------------------------

    internal data class AnimeHeavenSearchResult(val path: String, val title: String)
    internal data class AnimeHeavenEpisode(val number: Int, val gateKey: String)

    private data class KonohaEpisodeEntry(
        val number: Int,
        val title: String?,
        val overview: String?,
        val airDate: String?,
        val still: String?,
        val runtime: Int?,
    )

    private data class AniZipEpisodeEntry(
        val title: String?,
        val overview: String?,
        val still: String?,
        val airDate: String?,
        val runtime: Int?,
        val rating: String?,
    )

    internal fun parseAnimeHeavenSearch(html: String): List<AnimeHeavenSearchResult> {
        val starts = AH_CARD_START_REGEX.findAll(html).map { it.range.last }.toList()
        return starts.mapIndexedNotNull { idx, start ->
            val end = starts.getOrNull(idx + 1) ?: html.length
            val block = html.substring(start, end)
            val path = AH_ANCHOR_REGEX.find(block)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val rawName = AH_NAME_REGEX.find(block)?.groupValues?.get(1)
                ?.let { HTML_TAG_REGEX.replace(it, "").trim() }
                ?.takeIf { it.isNotBlank() }
                ?: AH_IMG_ALT_REGEX.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            AnimeHeavenSearchResult(path = path, title = decodeHtmlEntities(rawName))
        }.distinctBy { it.path }
    }

    internal fun parseAnimeHeavenEpisodes(html: String): List<AnimeHeavenEpisode> {
        val anchors = AH_GATE_ANCHOR_REGEX.findAll(html)
            .mapNotNull { m -> m.groupValues[1].takeIf { it.isNotBlank() }?.let { it to m.range.last } }
            .toList()
        return anchors.mapIndexedNotNull { idx, (key, start) ->
            val end = anchors.getOrNull(idx + 1)?.second ?: html.length
            val num = AH_WATCH_NUMBER_REGEX.find(html.substring(start, end))
                ?.groupValues?.get(1)
                ?.toFloatOrNull()
                ?.roundToInt()
                ?: return@mapIndexedNotNull null
            AnimeHeavenEpisode(number = num, gateKey = key)
        }.distinctBy { it.number }.sortedBy { it.number }
    }

    internal fun parseAnimeHeavenVideoUrls(html: String, gateKey: String? = null): List<String> {
        val cleaned = HTML_COMMENT_REGEX.replace(html, "")
        val urls = AH_SOURCE_SRC_REGEX.findAll(cleaned)
            .mapNotNull { m -> m.groupValues[1].trim().takeIf { it.contains(".mp4", ignoreCase = true) } }
            .distinct()
            .toList()
        if (gateKey.isNullOrBlank()) return urls
        return urls.filter { it.contains("?$gateKey", ignoreCase = true) }
    }

    private fun pickBestAnimeHeavenMatch(
        results: List<AnimeHeavenSearchResult>,
        titles: List<String>,
    ): AnimeHeavenSearchResult? {
        if (results.isEmpty()) return null
        val targetTokens = titles.map { normalizeTitle(it) }.filter { it.isNotBlank() }
        return results.maxByOrNull { item ->
            val candidate = normalizeTitle(item.title)
            var bestScore = 0
            for (target in targetTokens) {
                if (candidate == target) bestScore = maxOf(bestScore, 1000)
                else if (candidate.contains(target) || target.contains(candidate)) bestScore = maxOf(bestScore, 500)
                else {
                    val tWords = target.split(' ').filter { it.isNotBlank() }.toSet()
                    val cWords = candidate.split(' ').filter { it.isNotBlank() }.toSet()
                    val overlap = tWords.intersect(cWords).size
                    bestScore = maxOf(bestScore, overlap * 20 - kotlin.math.abs(tWords.size - cWords.size))
                }
            }
            bestScore
        } ?: results.firstOrNull()
    }

    private suspend fun getJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = 1024 * 1024,
    ): JsonObject? {
        val text = getText(url, headers, maxBytes) ?: return null
        return runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

    private suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = 1024 * 1024,
    ): String? {
        val resp = runCatching {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                httpRequestRaw(
                    method = "GET",
                    url = url,
                    headers = headers,
                    body = "",
                    followRedirects = true,
                    maxResponseBodyBytes = maxBytes,
                )
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return null
        if (resp.status !in 200..299 || resp.body.isBlank()) return null
        return resp.body
    }

    private fun aniPmHeaders(): Map<String, String> = mapOf(
        "User-Agent" to CHROME_MOBILE_UA,
        "Accept" to "application/json",
    )

    private fun extractQueryParam(url: String, key: String): String? {
        val query = url.substringAfter('?', "").ifBlank { return null }
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }
            ?.percentDecode()
    }

    private fun absoluteAniChanUrl(url: String): String {
        val prefixed = if (url.startsWith("/")) "$ANICHAN_BASE$url" else url
        return prefixed.replace(" ", "%20")
    }

    private fun normalizeSubtitleLangLabel(label: String): String {
        val lower = label.lowercase()
        return when {
            lower.startsWith("eng") -> "en"
            lower.startsWith("spa") -> "es"
            lower.startsWith("por") -> "pt"
            lower.startsWith("fre") || lower.startsWith("fra") -> "fr"
            lower.startsWith("ger") || lower.startsWith("deu") -> "de"
            lower.startsWith("ita") -> "it"
            lower.startsWith("rus") -> "ru"
            lower.startsWith("ara") -> "ar"
            lower.startsWith("ind") -> "id"
            lower.startsWith("hin") -> "hi"
            lower.startsWith("jpn") || lower.startsWith("jap") -> "ja"
            lower.startsWith("kor") -> "ko"
            else -> "en"
        }
    }

    private fun preferredAniListTitle(titleObj: JsonObject?): String? =
        titleObj?.str("english")?.takeIf { it.isNotBlank() }
            ?: titleObj?.str("romaji")?.takeIf { it.isNotBlank() }
            ?: titleObj?.str("userPreferred")?.takeIf { it.isNotBlank() }
            ?: titleObj?.str("native")?.takeIf { it.isNotBlank() }

    private fun formatStartDate(obj: JsonObject?): String? {
        val year = obj?.int("year") ?: return null
        val month = (obj.int("month") ?: 1).toString().padStart(2, '0')
        val day = (obj.int("day") ?: 1).toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private fun cleanSynopsis(text: String?): String? {
        val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return decodeHtmlEntities(
            HTML_TAG_REGEX.replace(raw.replace("<br>", "\n", ignoreCase = true).replace("<br/>", "\n", ignoreCase = true), ""),
        ).trim().takeIf { it.isNotEmpty() }
    }

    private fun decodeHtmlEntities(input: String): String =
        input.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

    private fun normalizeTitle(value: String): String =
        buildString {
            var lastSpace = true
            value.lowercase().forEach { ch ->
                if (ch.isLetterOrDigit()) {
                    append(ch)
                    lastSpace = false
                } else if (!lastSpace) {
                    append(' ')
                    lastSpace = true
                }
            }
        }.trim()

    private fun formatTenPointRating(value: Double): String {
        val tenths = kotlin.math.round(value * 10).toInt()
        return "${tenths / 10}.${tenths % 10}"
    }

    private fun JsonObject.str(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.dbl(name: String): Double? =
        (this[name] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.bool(name: String): Boolean? =
        (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(name: String): JsonObject? =
        this[name] as? JsonObject

    private fun JsonObject.array(name: String): List<JsonElement> =
        (this[name] as? JsonArray).orEmpty()

    private val SETTLAR_LANG_MAP = mapOf(
        "ind" to "id", "tha" to "th", "chi" to "zh", "zho" to "zh", "kor" to "ko", "pol" to "pl",
        "tur" to "tr", "jpn" to "ja", "eng" to "en", "spa" to "es", "por" to "pt", "fre" to "fr",
        "fra" to "fr", "ger" to "de", "deu" to "de", "ita" to "it", "rus" to "ru", "ara" to "ar",
        "vie" to "vi", "may" to "ms", "msa" to "ms", "hin" to "hi",
    )

    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val HTML_COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")
    private val ANICHAN_COOKIE_REGEX = Regex("""anichan_ws=([^;\s,]+)""")
    private val AH_CARD_START_REGEX = Regex(
        """<div\b[^>]*\bclass\s*=\s*['"][^'"]*\b(?:similarimg|chart)\b[^'"]*['"][^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val AH_ANCHOR_REGEX = Regex(
        """<a\b[^>]*\bhref\s*=\s*['"]?(?:/)?(anime\.php\?[^'"\s>]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val AH_NAME_REGEX = Regex(
        """<div\b[^>]*\bclass\s*=\s*['"][^'"]*\b(?:similarname|charttitle)\b[^'"]*['"][^>]*>\s*<a\b[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val AH_IMG_ALT_REGEX = Regex(
        """<img\b[^>]*\balt\s*=\s*['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE,
    )
    private val AH_GATE_ANCHOR_REGEX = Regex(
        """gatea\(\s*(?:"|&quot;|')([0-9a-f]+)(?:"|&quot;|')\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val AH_WATCH_NUMBER_REGEX = Regex(
        """<div\b[^>]*\bwatch2\b[^>]*>\s*(\d+(?:\.\d+)?)\s*</div>""",
        RegexOption.IGNORE_CASE,
    )
    private val AH_SOURCE_SRC_REGEX = Regex(
        """<source\b[^>]*\bsrc\s*=\s*['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE,
    )
}
