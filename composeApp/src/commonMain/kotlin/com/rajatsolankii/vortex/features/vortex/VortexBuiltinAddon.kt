package com.rajatsolankii.vortex.features.vortex

import co.touchlab.kermit.Logger
import com.rajatsolankii.vortex.features.streams.StreamBehaviorHints
import com.rajatsolankii.vortex.features.streams.StreamItem
import com.rajatsolankii.vortex.features.tmdb.TmdbService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Vortex's built-in content source. It speaks the same catalog/meta/stream protocol the
 * rest of the app already understands, but every request is answered locally from the
 * Vortex API, and streams are the Vortex embed servers. It is always installed and cannot
 * be removed; it replaces user-installed add-ons.
 */
object VortexBuiltinAddon {
    const val ADDON_ID = "me.watchvortex.builtin"
    const val NAME = "Vortex"
    private const val VIRTUAL_MANIFEST_HOST = "https://vortex-builtin.local"
    val MANIFEST_URL: String
        get() = "${VortexApi.HOST.ifBlank { VIRTUAL_MANIFEST_HOST }}/vortex-builtin/manifest.json"
    private val baseUrl: String
        get() = MANIFEST_URL.removeSuffix("/manifest.json")
    private val log = Logger.withTag("VortexBuiltinAddon")

    private const val TMDB_PAGE_SIZE = 20
    private const val PAGES_PER_REQUEST = 2

    fun handles(url: String): Boolean = url.substringBefore('?').startsWith("$baseUrl/")

    /** Answers a catalog/meta/stream request for the built-in source as Stremio JSON. */
    suspend fun respond(url: String): String {
        val path = url.substringBefore('?').removePrefix("$baseUrl/").removeSuffix(".json")
        if (path == "manifest") return manifest().toString()
        val segments = path.split('/')
        val resource = segments.getOrNull(0).orEmpty()
        val type = segments.getOrNull(1).orEmpty()
        val id = segments.getOrNull(2)?.percentDecode().orEmpty()
        val extra = parseExtra(segments.getOrNull(3))
        return when (resource) {
            "catalog" -> catalog(type, id, extra).toString()
            "meta" -> meta(type, id).toString()
            "stream" -> streams(type, id).toString()
            "subtitles" -> """{"subtitles":[]}"""
            else -> error("Unsupported Vortex resource: $resource")
        }
    }

    // ---- Manifest ---------------------------------------------------------------------

    private data class CatalogDef(
        val type: String,
        val id: String,
        val name: String,
        val path: String,
        val query: Map<String, String> = emptyMap(),
        val supportsGenre: Boolean = false,
        val isSearch: Boolean = false,
    )

    private val catalogs = listOf(
        CatalogDef("movie", "vortex.trending", "Trending", "trending/movie/week"),
        CatalogDef("series", "vortex.trending", "Trending", "trending/tv/week"),
        CatalogDef("movie", "vortex.popular", "Popular", "movie/popular", supportsGenre = true),
        CatalogDef("series", "vortex.popular", "Popular", "tv/popular", supportsGenre = true),
        CatalogDef("series", "vortex.anime", "Anime", "anilumos/trending", supportsGenre = true),
        CatalogDef("movie", "vortex.bollywood", "Bollywood", "discover/movie", mapOf("with_original_language" to "hi", "sort_by" to "popularity.desc")),
        CatalogDef("movie", "vortex.hollywood", "Hollywood", "discover/movie", mapOf("with_original_language" to "en", "sort_by" to "popularity.desc")),
        CatalogDef("series", "vortex.cartoons", "Cartoons", "discover/tv", mapOf("with_genres" to "16,10751", "sort_by" to "popularity.desc")),
        CatalogDef("movie", "vortex.drama", "Drama", "discover/movie", mapOf("with_genres" to "18", "sort_by" to "popularity.desc")),
        CatalogDef("movie", "vortex.now_playing", "Now Playing", "movie/now_playing"),
        CatalogDef("movie", "vortex.top_rated", "Top Rated", "movie/top_rated", supportsGenre = true),
        CatalogDef("series", "vortex.top_rated", "Top Rated", "tv/top_rated", supportsGenre = true),
        CatalogDef("anime", "vortex.anime_popular", "Popular Anime", "anilumos/popular", supportsGenre = true),
        CatalogDef("anime", "vortex.anime_top_rated", "Top Rated Anime", "anilumos/top_rated", supportsGenre = true),
        CatalogDef("movie", "vortex.search", "Movies", "search/movie", isSearch = true),
        CatalogDef("series", "vortex.search", "TV Shows", "search/tv", isSearch = true),
        CatalogDef("anime", "vortex.search", "Anime", "anilumos/search", isSearch = true),
    )

    private fun manifest(): JsonObject = buildJsonObject {
        put("id", ADDON_ID)
        put("name", NAME)
        put("version", "1.0.0")
        put("description", "Movies, TV shows and anime from Vortex & Anilumos.")
        putJsonArray("types") { add("movie"); add("series"); add("anime") }
        putJsonArray("idPrefixes") { add("tmdb:"); add("tt"); add("anilist:") }
        putJsonArray("resources") { add("catalog"); add("meta"); add("stream") }
        putJsonArray("catalogs") {
            catalogs.forEach { def ->
                addJsonObject {
                    put("type", def.type)
                    put("id", def.id)
                    put("name", def.name)
                    putJsonArray("extra") {
                        if (def.isSearch) {
                            addJsonObject { put("name", "search"); put("isRequired", true) }
                        } else {
                            if (def.supportsGenre) {
                                addJsonObject {
                                    put("name", "genre")
                                    putJsonArray("options") {
                                        if (def.path.startsWith("anilumos/")) {
                                            AnilumosAnimeBackend.GENRES.forEach { add(it) }
                                        } else {
                                            genresFor(def.type).values.forEach { add(it) }
                                        }
                                    }
                                }
                            }
                            addJsonObject { put("name", "skip") }
                        }
                    }
                }
            }
        }
    }

    // ---- Catalogs ---------------------------------------------------------------------

    private suspend fun catalog(type: String, id: String, extra: Map<String, String>): JsonObject {
        val def = catalogs.firstOrNull { it.type == type && it.id == id }
            ?: error("Unknown Vortex catalog $type/$id")
        val skip = extra["skip"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val firstPage = skip / TMDB_PAGE_SIZE + 1
        val genreName = extra["genre"]
        val search = extra["search"]?.trim().orEmpty()
        if (def.isSearch && search.isBlank()) return buildJsonObject { putJsonArray("metas") {} }
        if (def.path.startsWith("anilumos/")) {
            return AnilumosAnimeBackend.catalog(
                catalogId = def.id,
                skip = skip,
                genre = genreName,
                search = search,
            )
        }

        val (path, query) = when {
            def.isSearch -> def.path to mapOf("query" to search, "include_adult" to "false")
            genreName != null -> {
                val genreId = genresFor(type).entries.firstOrNull { it.value.equals(genreName, true) }?.key
                val discoverPath = if (type == "series") "discover/tv" else "discover/movie"
                val sort = if (def.id == "vortex.top_rated") "vote_average.desc" else "popularity.desc"
                discoverPath to buildMap {
                    put("sort_by", sort)
                    if (def.id == "vortex.top_rated") put("vote_count.gte", "300")
                    genreId?.let { put("with_genres", it.toString()) }
                }
            }
            else -> def.path to def.query
        }

        val pageCount = if (def.isSearch) 1 else PAGES_PER_REQUEST
        val results = coroutineScope {
            (firstPage until firstPage + pageCount).map { page ->
                async {
                    runCatching {
                        VortexApi.tmdb(path, query + ("page" to page.toString()) + ("include_adult" to "false"))
                    }.onFailure { log.w { "Catalog page $page of $path failed: ${it.message}" } }
                        .getOrNull()
                        ?.array("results")
                        .orEmpty()
                }
            }.awaitAll().flatten()
        }

        val seen = mutableSetOf<Int>()
        val excludeJapaneseAnime = def.id != "vortex.now_playing"
        return buildJsonObject {
            putJsonArray("metas") {
                results.forEach { element ->
                    val item = element as? JsonObject ?: return@forEach
                    val tmdbId = item.int("id") ?: return@forEach
                    if (!seen.add(tmdbId)) return@forEach
                    if (excludeJapaneseAnime && isTmdbJapaneseAnime(item)) return@forEach
                    add(previewMeta(item, type))
                }
            }
        }
    }

    internal fun isTmdbJapaneseAnime(item: JsonObject): Boolean {
        val genreIds = item.array("genre_ids").mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        if (16 !in genreIds) return false
        val lang = item.string("original_language")?.trim()?.lowercase()
        val countries = item.array("origin_country")
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase() }
        return lang == "ja" || "JP" in countries
    }

    private fun previewMeta(item: JsonObject, type: String): JsonObject = buildJsonObject {
        val tmdbId = item.int("id")
        put("id", "tmdb:$tmdbId")
        put("type", type)
        put("name", item.string("title") ?: item.string("name") ?: "")
        VortexApi.image(item.string("poster_path"), "w500")?.let { put("poster", it) }
        VortexApi.image(item.string("backdrop_path"), "w1280")?.let { put("background", it) }
        item.string("overview")?.takeIf(String::isNotBlank)?.let { put("description", it) }
        val date = item.string("release_date") ?: item.string("first_air_date")
        date?.take(4)?.takeIf { it.length == 4 }?.let { put("releaseInfo", it) }
        date?.takeIf(String::isNotBlank)?.let { put("released", "${it}T00:00:00.000Z") }
        item.rating()?.let { put("imdbRating", it) }
        val genreNames = genresFor(type)
        val genres = item.array("genre_ids").mapNotNull { (it as? JsonPrimitive)?.intOrNull?.let(genreNames::get) }
        if (genres.isNotEmpty()) putJsonArray("genres") { genres.forEach { add(it) } }
    }

    // ---- Meta -------------------------------------------------------------------------

    private suspend fun meta(type: String, id: String): JsonObject {
        if (AnilumosAnimeBackend.isAnilistId(id)) {
            return AnilumosAnimeBackend.meta(id)
        }
        val isSeries = type == "series"
        val tmdbId = resolveTmdbId(id, type) ?: error("No TMDB match for $id")
        val tmdbType = if (isSeries) "tv" else "movie"
        val append = if (isSeries) {
            "credits,videos,external_ids,content_ratings,images,recommendations"
        } else {
            "credits,videos,external_ids,release_dates,images,recommendations"
        }
        val details = VortexApi.tmdb(
            "$tmdbType/$tmdbId",
            mapOf("append_to_response" to append, "include_image_language" to "en,null"),
        )
        val baseId = id.substringBefore(':').let { if (it == "tmdb") "tmdb:$tmdbId" else it }
        val seasons = if (isSeries) seasonEpisodes(tmdbId, details) else emptyList()

        val meta = buildJsonObject {
            put("id", if (id.startsWith("tt")) baseId else "tmdb:$tmdbId")
            put("type", type)
            put("name", details.string("title") ?: details.string("name") ?: "")
            (details.string("imdb_id") ?: details.obj("external_ids")?.string("imdb_id"))
                ?.let { put("imdb_id", it) }
            VortexApi.image(details.string("poster_path"), "w500")?.let { put("poster", it) }
            VortexApi.image(details.string("backdrop_path"), "original")?.let { put("background", it) }
            bestLogo(details)?.let { put("logo", it) }
            details.string("overview")?.takeIf(String::isNotBlank)?.let { put("description", it) }
            val date = details.string("release_date") ?: details.string("first_air_date")
            val endYear = details.string("last_air_date")?.take(4)
            val year = date?.take(4)
            if (year != null) {
                val info = if (isSeries && details.string("status") == "Ended" && endYear != null && endYear != year) {
                    "$year-$endYear"
                } else if (isSeries && details.string("status") != "Ended") {
                    "$year-"
                } else {
                    year
                }
                put("releaseInfo", info)
            }
            date?.let { put("released", "${it}T00:00:00.000Z") }
            details.string("last_air_date")?.let { put("lastAirDate", it) }
            details.string("status")?.let { put("status", it) }
            details.rating()?.let { put("imdbRating", it) }
            val runtime = details.int("runtime") ?: (details["episode_run_time"] as? JsonArray)
                ?.firstOrNull()?.jsonPrimitive?.intOrNull
            runtime?.takeIf { it > 0 }?.let { put("runtime", "$it min") }
            val genres = details.array("genres").mapNotNull { (it as? JsonObject)?.string("name") }
            if (genres.isNotEmpty()) putJsonArray("genres") { genres.forEach { add(it) } }
            details.array("production_countries").firstOrNull()?.let { (it as? JsonObject)?.string("name") }
                ?.let { put("country", it) }
            details.array("spoken_languages").firstOrNull()?.let { (it as? JsonObject)?.string("english_name") }
                ?.let { put("language", it) }
            details.string("homepage")?.takeIf(String::isNotBlank)?.let { put("website", it) }
            ageRating(details, isSeries)?.let { put("ageRating", it) }

            val credits = details.obj("credits")
            val directors = credits?.array("crew").orEmpty().mapNotNull { it as? JsonObject }
                .filter { it.string("job") == "Director" }.mapNotNull { it.string("name") }.distinct()
            val creators = details.array("created_by").mapNotNull { (it as? JsonObject)?.string("name") }
            (directors.ifEmpty { creators }).takeIf { it.isNotEmpty() }?.let { names ->
                putJsonArray("director") { names.take(4).forEach { add(it) } }
            }
            val writers = credits?.array("crew").orEmpty().mapNotNull { it as? JsonObject }
                .filter { it.string("department") == "Writing" }.mapNotNull { it.string("name") }.distinct()
            if (writers.isNotEmpty()) putJsonArray("writer") { writers.take(4).forEach { add(it) } }
            val cast = credits?.array("cast").orEmpty().mapNotNull { it as? JsonObject }.take(20)
            if (cast.isNotEmpty()) {
                putJsonArray("cast") { cast.forEach { person -> person.string("name")?.let { add(it) } } }
                putJsonObject("app_extras") {
                    putJsonArray("cast") {
                        cast.forEach { person ->
                            val name = person.string("name") ?: return@forEach
                            addJsonObject {
                                put("name", name)
                                person.string("character")?.let { put("character", it) }
                                VortexApi.image(person.string("profile_path"), "w185")?.let { put("photo", it) }
                            }
                        }
                    }
                    val seasonPosters = details.array("seasons").mapNotNull { it as? JsonObject }
                        .mapNotNull { season ->
                            val number = season.int("season_number") ?: return@mapNotNull null
                            val poster = VortexApi.image(season.string("poster_path"), "w500") ?: return@mapNotNull null
                            number to poster
                        }
                    if (seasonPosters.isNotEmpty()) {
                        putJsonObject("seasonPosters") { seasonPosters.forEach { (n, p) -> put(n.toString(), p) } }
                    }
                }
            }

            val trailers = details.obj("videos")?.array("results").orEmpty().mapNotNull { it as? JsonObject }
                .filter { it.string("site") == "YouTube" && (it.string("type") == "Trailer" || it.string("type") == "Teaser") }
                .sortedByDescending { if (it.string("type") == "Trailer") 1 else 0 }
            if (trailers.isNotEmpty()) {
                putJsonArray("trailers") {
                    trailers.take(6).forEach { video ->
                        addJsonObject {
                            put("source", video.string("key"))
                            put("key", video.string("key"))
                            put("name", video.string("name") ?: "Trailer")
                            put("type", video.string("type") ?: "Trailer")
                            put("site", "YouTube")
                            video.string("published_at")?.let { put("published_at", it) }
                            put("official", video.boolean("official") ?: false)
                        }
                    }
                }
            }

            if (isSeries) {
                putJsonArray("videos") {
                    seasons.forEach { episode ->
                        val season = episode.int("season_number") ?: return@forEach
                        val number = episode.int("episode_number") ?: return@forEach
                        addJsonObject {
                            put("id", "$baseId:$season:$number")
                            put("title", episode.string("name") ?: "Episode $number")
                            put("season", season)
                            put("episode", number)
                            episode.string("air_date")?.let { put("released", "${it}T00:00:00.000Z") }
                            VortexApi.image(episode.string("still_path"), "w500")?.let { put("thumbnail", it) }
                            episode.string("overview")?.takeIf(String::isNotBlank)?.let { put("overview", it) }
                            episode.int("runtime")?.let { put("runtime", "$it min") }
                            episode.double("vote_average")?.takeIf { it > 0 }?.let { put("rating", it.toString()) }
                        }
                    }
                }
            } else {
                putJsonObject("behaviorHints") { put("defaultVideoId", "tmdb:$tmdbId") }
            }

            val recs = details.obj("recommendations")?.array("results").orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { (it.int("id") ?: 0) > 0 && !isTmdbJapaneseAnime(it) }
                .take(12)
            if (recs.isNotEmpty()) {
                putJsonArray("moreLikeThis") {
                    recs.forEach { rec ->
                        val recId = rec.int("id") ?: return@forEach
                        val recMediaType = rec.string("media_type")?.lowercase()
                        val inferredType = when (recMediaType) {
                            "tv" -> "series"
                            "movie" -> "movie"
                            else -> if (isSeries) "series" else "movie"
                        }
                        val recName = rec.string("title") ?: rec.string("name") ?: return@forEach
                        addJsonObject {
                            put("id", "tmdb:$recId")
                            put("type", inferredType)
                            put("name", recName)
                            VortexApi.image(rec.string("poster_path"), "w500")?.let { put("poster", it) }
                            VortexApi.image(rec.string("backdrop_path"), "w1280")?.let { put("banner", it) }
                            rec.string("overview")?.takeIf(String::isNotBlank)?.let { put("description", it) }
                            (rec.string("release_date") ?: rec.string("first_air_date"))?.take(4)?.let { put("releaseInfo", it) }
                            rec.rating()?.let { put("imdbRating", it) }
                        }
                    }
                }
            }
        }
        return buildJsonObject { put("meta", meta) }
    }

    private suspend fun seasonEpisodes(tmdbId: Int, details: JsonObject): List<JsonObject> {
        val seasonNumbers = details.array("seasons").mapNotNull { (it as? JsonObject)?.int("season_number") }
            .filter { it > 0 }
            .ifEmpty { (1..(details.int("number_of_seasons") ?: 1)).toList() }
        return coroutineScope {
            seasonNumbers.chunked(6).flatMap { chunk ->
                chunk.map { number ->
                    async {
                        runCatching { VortexApi.tmdb("tv/$tmdbId/season/$number") }
                            .onFailure { log.w { "Season $number of $tmdbId failed: ${it.message}" } }
                            .getOrNull()
                            ?.array("episodes")
                            .orEmpty()
                            .mapNotNull { it as? JsonObject }
                    }
                }.awaitAll().flatten()
            }
        }
    }

    private fun bestLogo(details: JsonObject): String? {
        val logos = details.obj("images")?.array("logos").orEmpty().mapNotNull { it as? JsonObject }
        val logo = logos.firstOrNull { it.string("iso_639_1") == "en" } ?: logos.firstOrNull()
        return VortexApi.image(logo?.string("file_path"), "w500")
    }

    private fun ageRating(details: JsonObject, isSeries: Boolean): String? =
        if (isSeries) {
            details.obj("content_ratings")?.array("results").orEmpty().mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("iso_3166_1") == "US" }?.string("rating")?.takeIf(String::isNotBlank)
        } else {
            details.obj("release_dates")?.array("results").orEmpty().mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("iso_3166_1") == "US" }
                ?.array("release_dates").orEmpty().mapNotNull { (it as? JsonObject)?.string("certification") }
                .firstOrNull(String::isNotBlank)
        }

    fun instantStreamsFor(
        type: String?,
        videoId: String?,
        parentMetaId: String?,
        season: Int?,
        episode: Int?,
        embedUrlHint: String? = null,
    ): List<StreamItem> {
        AnilumosAnimeBackend.parseAnilistTarget(
            videoId = videoId,
            parentMetaId = parentMetaId,
            episode = episode,
        )?.let { target ->
            return AnilumosAnimeBackend.instantStreamsFor(target.anilistId, target.episode)
        }
        val mediaType = type.orEmpty().ifBlank { if (season != null && episode != null) "series" else "movie" }
        val isSeries = mediaType.equals("series", true) || mediaType.equals("tv", true)
        val directTarget = videoId?.let { VortexServers.targetFromVideoId(it, mediaType) }
            ?: parentMetaId?.let { VortexServers.targetFromVideoId(it, mediaType) }?.let { base ->
                if (isSeries && season != null && episode != null) {
                    base.copy(season = season, episode = episode)
                } else {
                    base
                }
            }
            ?: embedUrlHint?.let(VortexServers::targetForUrl)?.let { base ->
                if (isSeries && season != null && episode != null) {
                    base.copy(season = season, episode = episode)
                } else {
                    base
                }
            }
            ?: return emptyList()
        return VortexServers.sourcesFor(directTarget).map { source ->
            val note = if (source.server.recommendedForAnime) "Direct extract · best for anime" else "Direct extract · fast native stream"
            StreamItem(
                name = source.server.displayName,
                title = source.server.displayName,
                description = note,
                url = source.url,
                addonName = NAME,
                addonId = ADDON_ID,
                behaviorHints = StreamBehaviorHints(
                    bingeGroup = "vortex-${source.server.name.lowercase()}",
                ),
            )
        }
    }

    // ---- Streams ----------------------------------------------------------------------

    private suspend fun streams(type: String, id: String): JsonObject {
        if (AnilumosAnimeBackend.isAnilistId(id)) {
            return AnilumosAnimeBackend.streams(id)
        }
        val parts = id.split(':')
        val tmdbId = resolveTmdbId(id, type) ?: return buildJsonObject { putJsonArray("streams") {} }
        val isSeries = type == "series"
        val episodeParts = if (parts.first() == "tmdb") parts.drop(2) else parts.drop(1)
        val target = VortexEmbedTarget(
            tmdbId = tmdbId,
            isSeries = isSeries,
            season = episodeParts.getOrNull(0)?.toIntOrNull() ?: 1,
            episode = episodeParts.getOrNull(1)?.toIntOrNull() ?: 1,
        )
        return buildJsonObject {
            putJsonArray("streams") {
                VortexServers.sourcesFor(target).forEach { source ->
                    addJsonObject {
                        put("name", source.server.displayName)
                        put("title", source.server.displayName)
                        val note = if (source.server.recommendedForAnime) "Direct extract · best for anime" else "Direct extract · web fallback"
                        put("description", "${source.server.displayName}\n$note")
                        put("url", source.url)
                        putJsonObject("behaviorHints") {
                            put("bingeGroup", "vortex-${source.server.name.lowercase()}")
                        }
                    }
                }
            }
        }
    }

    // ---- Helpers ----------------------------------------------------------------------

    private suspend fun resolveTmdbId(id: String, type: String): Int? {
        val parts = id.split(':')
        return when {
            parts.first() == "tmdb" -> parts.getOrNull(1)?.toIntOrNull()
            parts.first().startsWith("tt") -> TmdbService.ensureTmdbId(parts.first(), type)?.toIntOrNull()
            else -> parts.first().toIntOrNull()
        }
    }

    private fun parseExtra(segment: String?): Map<String, String> =
        segment.orEmpty().split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', "").takeIf(String::isNotBlank) ?: return@mapNotNull null
            key to pair.substringAfter('=').percentDecode()
        }.toMap()

    private fun genresFor(type: String): Map<Int, String> = if (type == "series") TV_GENRES else MOVIE_GENRES

    private val MOVIE_GENRES = linkedMapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance",
        878 to "Science Fiction", 53 to "Thriller", 10752 to "War", 37 to "Western",
    )

    private val TV_GENRES = linkedMapOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery",
        10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk",
        10768 to "War & Politics", 37 to "Western",
    )
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

private fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): List<JsonElement> = (this[name] as? JsonArray).orEmpty()

private fun JsonObject.rating(): String? =
    double("vote_average")?.takeIf { it > 0 }?.let { value ->
        val tenths = kotlin.math.round(value * 10).toInt()
        "${tenths / 10}.${tenths % 10}"
    }

internal fun String.percentDecode(): String {
    if ('%' !in this && '+' !in this) return this
    val bytes = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == '%' && index + 2 <= lastIndex -> {
                val value = substring(index + 1, index + 3).toIntOrNull(16)
                if (value != null) {
                    bytes.add(value.toByte()); index += 3; continue
                }
                bytes.add(char.code.toByte())
            }
            char == '+' -> bytes.add(' '.code.toByte())
            else -> char.toString().encodeToByteArray().forEach(bytes::add)
        }
        index++
    }
    return bytes.toByteArray().decodeToString()
}
