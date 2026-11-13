package com.example.data.repository

import com.example.data.local.WatchHistoryDao
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.EpisodeItem
import com.example.data.model.MediaDetail
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SeasonItem
import com.example.data.model.toDomain
import com.example.data.remote.VortexApiService
import com.example.data.model.Bookmark
import com.example.data.remote.SupabaseService
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VortexRepository(
    private val apiService: VortexApiService,
    private val watchHistoryDao: WatchHistoryDao,
    private val bookmarkDao: com.example.data.local.BookmarkDao,
    private val cachedHomeItemDao: com.example.data.local.CachedHomeItemDao,
    private val cachedProviderDao: com.example.data.local.CachedProviderDao
) {

    private val _watchHistory = kotlinx.coroutines.flow.MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
    val watchHistory: Flow<List<WatchHistoryEntity>> = _watchHistory

    val bookmarksFlow: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks().map { list ->
        list.map { entity ->
            Bookmark(
                id = null,
                userId = null,
                mediaId = entity.mediaId,
                mediaType = entity.mediaType,
                title = entity.title,
                posterPath = entity.posterPath,
                createdAt = null
            )
        }
    }

    suspend fun refreshWatchHistory() = withContext(Dispatchers.IO) {
        runCatching {
            val user = SupabaseService.client.auth.currentSessionOrNull()?.user
            if (user == null) {
                _watchHistory.value = watchHistoryDao.getAllWatchHistoryList()
                return@withContext
            }
            val profile = SupabaseService.client.postgrest["user_profiles"]
                .select {
                    filter { eq("user_id", user.id) }
                }.decodeSingleOrNull<com.example.data.model.UserProfile>()
            
            val dtoList = profile?.watchHistory ?: emptyList()
            val entities = dtoList.map { dto ->
                WatchHistoryEntity(
                    id = dto.id,
                    title = dto.title ?: dto.name ?: "Unknown",
                    posterPath = dto.posterPath,
                    backdropPath = dto.backdropPath,
                    mediaType = dto.mediaType,
                    lastPositionMs = dto.lastPositionMs ?: 0L,
                    durationMs = dto.durationMs ?: 0L,
                    seasonNumber = dto.season,
                    episodeNumber = dto.episode,
                    lastWatchedTimestamp = dto.timestamp
                )
            }.sortedByDescending { it.lastWatchedTimestamp }
            
            _watchHistory.value = entities
        }.onFailure {
            it.printStackTrace()
        }
    }
    suspend fun getTrending(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        if (page == 1) {
            getCachedHomeCategory(
                category = "trending",
                path = "trending/all/week",
                mediaType = MediaType.MOVIE,
                extraQueries = emptyMap(),
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallback().shuffled() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(path = "trending/all/week", page = page)
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain() })
                if (domainList.isNotEmpty()) domainList else getCuratedFallback().shuffled()
            }.recoverCatching {
                getCuratedFallback()
            }
        }
    }

    suspend fun getAnime(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        val extra = mapOf("with_genres" to "16", "with_original_language" to "ja")
        if (page == 1) {
            getCachedHomeCategory(
                category = "anime",
                path = "discover/tv",
                mediaType = MediaType.TV,
                extraQueries = extra,
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallbackAnime() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(
                    path = "discover/tv",
                    page = page,
                    includeAdult = false,
                    extraQueries = extra
                )
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.TV) })
                if (domainList.isNotEmpty()) domainList else getCuratedFallbackAnime()
            }.recoverCatching {
                getCuratedFallbackAnime()
            }
        }
    }

    suspend fun getBollywood(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        val extra = mapOf("with_original_language" to "hi", "sort_by" to "popularity.desc")
        if (page == 1) {
            getCachedHomeCategory(
                category = "bollywood",
                path = "discover/movie",
                mediaType = MediaType.MOVIE,
                extraQueries = extra,
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallbackBollywood() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(
                    path = "discover/movie",
                    page = page,
                    includeAdult = false,
                    extraQueries = extra
                )
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.MOVIE) })
                if (domainList.isNotEmpty()) domainList else getCuratedFallbackBollywood()
            }.recoverCatching {
                getCuratedFallbackBollywood()
            }
        }
    }

    suspend fun getCartoons(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        val extra = mapOf("with_genres" to "16,10751", "sort_by" to "popularity.desc")
        if (page == 1) {
            getCachedHomeCategory(
                category = "cartoons",
                path = "discover/tv",
                mediaType = MediaType.TV,
                extraQueries = extra,
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallbackCartoons() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(
                    path = "discover/tv",
                    page = page,
                    includeAdult = false,
                    extraQueries = extra
                )
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.TV) })
                if (domainList.isNotEmpty()) domainList else getCuratedFallbackCartoons()
            }.recoverCatching {
                getCuratedFallbackCartoons()
            }
        }
    }

    suspend fun getDrama(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        val extra = mapOf("with_genres" to "18", "sort_by" to "popularity.desc")
        if (page == 1) {
            getCachedHomeCategory(
                category = "drama",
                path = "discover/movie",
                mediaType = MediaType.MOVIE,
                extraQueries = extra,
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallbackDrama() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(
                    path = "discover/movie",
                    page = page,
                    includeAdult = false,
                    extraQueries = extra
                )
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.MOVIE) })
                if (domainList.isNotEmpty()) domainList else getCuratedFallbackDrama()
            }.recoverCatching {
                getCuratedFallbackDrama()
            }
        }
    }

    suspend fun getHollywood(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        val extra = mapOf("with_original_language" to "en", "sort_by" to "popularity.desc")
        if (page == 1) {
            getCachedHomeCategory(
                category = "hollywood",
                path = "discover/movie",
                mediaType = MediaType.MOVIE,
                extraQueries = extra,
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallbackHollywood() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(
                    path = "discover/movie",
                    page = page,
                    includeAdult = false,
                    extraQueries = extra
                )
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.MOVIE) })
                if (domainList.isNotEmpty()) domainList else getCuratedFallbackHollywood()
            }.recoverCatching {
                getCuratedFallbackHollywood()
            }
        }
    }

    suspend fun getMovies(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        if (page == 1) {
            getCachedHomeCategory(
                category = "movies",
                path = "movie/popular",
                mediaType = MediaType.MOVIE,
                extraQueries = emptyMap(),
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallbackMovies() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(
                    path = "movie/popular",
                    page = page,
                    includeAdult = false
                )
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.MOVIE) })
                if (domainList.isNotEmpty()) domainList else getCuratedFallbackMovies()
            }.recoverCatching {
                getCuratedFallbackMovies()
            }
        }
    }

    suspend fun getProviders(): Result<List<com.example.data.model.ProviderDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val firstItem = cachedProviderDao.getFirstProvider()
            val isStale = firstItem == null || (System.currentTimeMillis() - firstItem.updatedAt > 24 * 60 * 60 * 1000)

            if (!isStale) {
                val cached = cachedProviderDao.getAllProviders()
                if (cached.isNotEmpty()) {
                    return@runCatching cached.map {
                        com.example.data.model.ProviderDto(
                            providerId = it.providerId,
                            providerName = it.providerName,
                            logoPath = it.logoPath,
                            displayPriority = it.displayPriority
                        )
                    }
                }
            }

            val response = apiService.getProviders()
            val domainList = response.results

            if (domainList.isNotEmpty()) {
                cachedProviderDao.clearAll()
                val newEntities = domainList.map { item ->
                    com.example.data.local.CachedProvider(
                        providerId = item.providerId,
                        providerName = item.providerName,
                        logoPath = item.logoPath,
                        displayPriority = item.displayPriority ?: 0
                    )
                }
                cachedProviderDao.insertAll(newEntities)
                domainList
            } else {
                emptyList()
            }
        }.recoverCatching {
            val cached = cachedProviderDao.getAllProviders()
            if (cached.isNotEmpty()) {
                cached.map {
                    com.example.data.model.ProviderDto(
                        providerId = it.providerId,
                        providerName = it.providerName,
                        logoPath = it.logoPath,
                        displayPriority = it.displayPriority
                    )
                }
            } else {
                emptyList()
            }
        }
    }

    suspend fun getMediaForProvider(providerId: Int, page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "discover/movie",
                page = page,
                includeAdult = false,
                extraQueries = mapOf("with_watch_providers" to providerId.toString(), "watch_region" to "US", "sort_by" to "popularity.desc")
            )
            ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.MOVIE) })
        }.recoverCatching {
            emptyList()
        }
    }

    suspend fun getTvShows(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        if (page == 1) {
            getCachedHomeCategory(
                category = "tvShows",
                path = "tv/popular",
                mediaType = MediaType.TV,
                extraQueries = emptyMap(),
                cachedHomeItemDao = cachedHomeItemDao,
                apiService = apiService,
                fallback = { getCuratedFallbackTv() }
            )
        } else {
            runCatching {
                val response = apiService.getMediaList(
                    path = "tv/popular",
                    page = page,
                    includeAdult = false
                )
                val domainList = ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.TV) })
                if (domainList.isNotEmpty()) domainList else getCuratedFallbackTv()
            }.recoverCatching {
                getCuratedFallbackTv()
            }
        }
    }

    suspend fun getTopRated(isTv: Boolean = false, page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val path = if (isTv) "tv/top_rated" else "movie/top_rated"
            val response = apiService.getMediaList(path = path, page = page, includeAdult = false)
            ContentFilter.filterDomain(response.results.map { it.toDomain(if (isTv) MediaType.TV else MediaType.MOVIE) })
        }.recoverCatching {
            emptyList()
        }
    }

    suspend fun getNowPlaying(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(path = "movie/now_playing", page = page, includeAdult = false)
            ContentFilter.filterDomain(response.results.map { it.toDomain(MediaType.MOVIE) })
        }.recoverCatching {
            emptyList()
        }
    }

    suspend fun getMediaByGenre(genreId: Int, isTv: Boolean = false, page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val path = if (isTv) "discover/tv" else "discover/movie"
            val response = apiService.getMediaList(
                path = path,
                page = page,
                includeAdult = false,
                extraQueries = mapOf("with_genres" to genreId.toString(), "sort_by" to "popularity.desc")
            )
            ContentFilter.filterDomain(response.results.map { it.toDomain(if (isTv) MediaType.TV else MediaType.MOVIE) })
        }.recoverCatching {
            emptyList()
        }
    }

    suspend fun getRecommendations(mediaId: Int, mediaType: MediaType): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val recPath = if (mediaType == MediaType.TV) "tv/$mediaId/recommendations" else "movie/$mediaId/recommendations"
            val response = apiService.getMediaList(path = recPath, page = 1)
            val list = ContentFilter.filterDomain(response.results.map { it.toDomain(mediaType) })
            if (list.isNotEmpty()) {
                list
            } else {
                val simPath = if (mediaType == MediaType.TV) "tv/$mediaId/similar" else "movie/$mediaId/similar"
                val simResp = apiService.getMediaList(path = simPath, page = 1)
                val simList = ContentFilter.filterDomain(simResp.results.map { it.toDomain(mediaType) })
                if (simList.isNotEmpty()) simList else getCuratedFallback().filter { it.id != mediaId }
            }
        }.recoverCatching {
            getCuratedFallback().filter { it.id != mediaId }
        }
    }

    suspend fun getMovieDetails(id: Int): Result<MediaDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getMovieDetails(path = "movie/$id")
            val posterFull = if (!dto.posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getPosterQuality()}${dto.posterPath}" else ""
            val backdropFull = if (!dto.backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getBackdropQuality()}${dto.backdropPath}" else posterFull
            val durationText = dto.runtime?.let { "${it / 60}h ${it % 60}m" } ?: "Movie"
            val genreNames = dto.genres?.map { it.name } ?: emptyList()

            val creditsResult = runCatching { apiService.getCredits(path = "movie/$id/credits") }.getOrNull()
            val castMembers = creditsResult?.cast?.take(15)?.mapNotNull { c ->
                val profileUrl = if (!c.profilePath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185${c.profilePath}" else ""
                com.example.data.model.CastMember(
                    id = c.id,
                    name = c.name ?: "Unknown",
                    character = c.character ?: "",
                    profileUrl = profileUrl
                )
            } ?: emptyList()

            val videosResult = runCatching { apiService.getVideos(path = "movie/$id/videos") }.getOrNull()
            val trailerKey = videosResult?.results?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) && (it.type.equals("Trailer", ignoreCase = true) || it.type.equals("Teaser", ignoreCase = true)) }?.key

            MediaDetail(
                id = dto.id,
                title = dto.title ?: "Untitled Movie",
                overview = dto.overview ?: "No overview available.",
                posterUrl = posterFull,
                backdropUrl = backdropFull,
                mediaType = MediaType.MOVIE,
                rating = dto.voteAverage ?: 0.0,
                releaseDate = dto.releaseDate ?: "",
                durationOrSeasons = durationText,
                tagline = dto.tagline ?: "",
                genres = genreNames,
                rawPosterPath = dto.posterPath,
                rawBackdropPath = dto.backdropPath,
                cast = castMembers,
                trailerKey = trailerKey,
                voteCount = dto.voteCount ?: 0
            )
        }.recoverCatching {
            val item = getCuratedFallback().find { it.id == id }
            MediaDetail(
                id = id,
                title = item?.title ?: "Solaris Horizon",
                overview = item?.overview ?: "An epic odyssey across desert moons and ancient cosmic monuments.",
                posterUrl = item?.posterUrl ?: "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                backdropUrl = item?.backdropUrl ?: "https://image.tmdb.org/t/p/w780/xOMo8BRK7PfcJv9JCnx7s520b22.jpg",
                mediaType = MediaType.MOVIE,
                rating = item?.rating ?: 8.7,
                releaseDate = item?.releaseDate ?: "2024",
                durationOrSeasons = "2h 18m",
                tagline = "Enter the vortex of tomorrow.",
                genres = listOf("Action", "Sci-Fi", "Adventure")
            )
        }
    }

    suspend fun getTvDetails(id: Int): Result<MediaDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getTvDetails(path = "tv/$id")
            val posterFull = if (!dto.posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getPosterQuality()}${dto.posterPath}" else ""
            val backdropFull = if (!dto.backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getBackdropQuality()}${dto.backdropPath}" else posterFull
            val seasonsCount = dto.numberOfSeasons ?: dto.seasons?.count { it.seasonNumber > 0 } ?: 1
            val seasonsText = "$seasonsCount Seasons"
            val genreNames = dto.genres?.map { it.name } ?: emptyList()

            val parsedSeasons = dto.seasons
                ?.filter { it.seasonNumber > 0 }
                ?.map { s ->
                    val seasonPoster = if (!s.posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getPosterQuality()}${s.posterPath}" else posterFull
                    SeasonItem(
                        id = s.id,
                        name = s.name?.takeIf { it.isNotBlank() } ?: "Season ${s.seasonNumber}",
                        seasonNumber = s.seasonNumber,
                        episodeCount = s.episodeCount ?: 10,
                        posterUrl = seasonPoster
                    )
                } ?: emptyList()

            val finalSeasons = if (parsedSeasons.isNotEmpty()) {
                parsedSeasons
            } else {
                (1..seasonsCount).map { seasonNum ->
                    SeasonItem(
                        id = seasonNum,
                        name = "Season $seasonNum",
                        seasonNumber = seasonNum,
                        episodeCount = 10,
                        posterUrl = posterFull
                    )
                }
            }

            val creditsResult = runCatching { apiService.getCredits(path = "tv/$id/credits") }.getOrNull()
            val castMembers = creditsResult?.cast?.take(15)?.mapNotNull { c ->
                val profileUrl = if (!c.profilePath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185${c.profilePath}" else ""
                com.example.data.model.CastMember(
                    id = c.id,
                    name = c.name ?: "Unknown",
                    character = c.character ?: "",
                    profileUrl = profileUrl
                )
            } ?: emptyList()

            val videosResult = runCatching { apiService.getVideos(path = "tv/$id/videos") }.getOrNull()
            val trailerKey = videosResult?.results?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) && (it.type.equals("Trailer", ignoreCase = true) || it.type.equals("Teaser", ignoreCase = true)) }?.key

            MediaDetail(
                id = dto.id,
                title = dto.name ?: "Untitled Series",
                overview = dto.overview ?: "No overview available.",
                posterUrl = posterFull,
                backdropUrl = backdropFull,
                mediaType = MediaType.TV,
                rating = dto.voteAverage ?: 0.0,
                releaseDate = dto.firstAirDate ?: "",
                durationOrSeasons = seasonsText,
                tagline = dto.tagline ?: "",
                genres = genreNames,
                rawPosterPath = dto.posterPath,
                rawBackdropPath = dto.backdropPath,
                seasons = finalSeasons,
                cast = castMembers,
                trailerKey = trailerKey,
                voteCount = dto.voteCount ?: 0
            )
        }.recoverCatching {
            val item = getCuratedFallback().find { it.id == id }
            val fallbackSeasons = (1..3).map { s ->
                SeasonItem(
                    id = s,
                    name = "Season $s",
                    seasonNumber = s,
                    episodeCount = 10,
                    posterUrl = item?.posterUrl ?: ""
                )
            }
            MediaDetail(
                id = id,
                title = item?.title ?: "Neon Genesis Chronos",
                overview = item?.overview ?: "Twin guardians defend their sister cities from dimensional rifts.",
                posterUrl = item?.posterUrl ?: "https://image.tmdb.org/t/p/w500/abf8tHznhSvl9BAElD2cQYRr6fZ.jpg",
                backdropUrl = item?.backdropUrl ?: "https://image.tmdb.org/t/p/w780/9faGSFi5jam6pDWGNdFaF8IRneP.jpg",
                mediaType = MediaType.TV,
                rating = item?.rating ?: 8.9,
                releaseDate = item?.releaseDate ?: "2024",
                durationOrSeasons = "3 Seasons",
                tagline = "Time bends to no one.",
                genres = listOf("Animation", "Sci-Fi", "Mystery"),
                seasons = fallbackSeasons
            )
        }
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): Result<List<EpisodeItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getSeasonDetails(path = "tv/$tvId/season/$seasonNumber")
            val episodes = dto.episodes?.map { ep ->
                val stillFull = if (!ep.stillPath.isNullOrBlank()) "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getPosterQuality()}${ep.stillPath}" else ""
                EpisodeItem(
                    id = ep.id,
                    name = ep.name?.takeIf { it.isNotBlank() } ?: "Episode ${ep.episodeNumber}",
                    overview = ep.overview ?: "",
                    episodeNumber = ep.episodeNumber,
                    seasonNumber = ep.seasonNumber,
                    stillUrl = stillFull,
                    rating = ep.voteAverage ?: 0.0,
                    airDate = ep.airDate ?: "",
                    runtime = ep.runtime
                )
            } ?: emptyList()

            if (episodes.isNotEmpty()) {
                episodes
            } else {
                generateFallbackEpisodes(tvId, seasonNumber, 10)
            }
        }.recoverCatching {
            generateFallbackEpisodes(tvId, seasonNumber, 10)
        }
    }

    private fun generateFallbackEpisodes(tvId: Int, seasonNumber: Int, count: Int): List<EpisodeItem> {
        return (1..count).map { epNum ->
            EpisodeItem(
                id = tvId * 1000 + seasonNumber * 100 + epNum,
                name = "Episode $epNum",
                overview = "Episode $epNum of Season $seasonNumber.",
                episodeNumber = epNum,
                seasonNumber = seasonNumber,
                stillUrl = "",
                rating = 8.0,
                airDate = "",
                runtime = 45
            )
        }
    }

    suspend fun getTvSeasons(id: Int): Result<List<com.example.data.model.SeasonDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getTvDetails(path = "tv/$id")
            val filteredSeasons = dto.seasons?.filter { it.seasonNumber > 0 } ?: emptyList()
            filteredSeasons
        }
    }

    suspend fun search(query: String, includeAdult: Boolean = false): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.search(query = query, includeAdult = includeAdult)
            ContentFilter.filterDomain(response.results.map { it.toDomain() })
        }.recoverCatching {
            getCuratedFallback().filter {
                it.title.contains(query, ignoreCase = true) || it.overview.contains(query, ignoreCase = true)
            }
        }
    }

    suspend fun getWatchHistoryById(id: Int): WatchHistoryEntity? = withContext(Dispatchers.IO) {
        watchHistoryDao.getWatchHistoryById(id)
    }

    suspend fun saveWatchProgress(
        id: Int,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        mediaType: MediaType,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        season: Int? = null,
        episode: Int? = null
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val entity = WatchHistoryEntity(
                id = id,
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                mediaType = if (mediaType == MediaType.TV) "tv" else "movie",
                lastPositionMs = positionMs,
                durationMs = durationMs,
                seasonNumber = season,
                episodeNumber = episode,
                lastWatchedTimestamp = System.currentTimeMillis()
            )
            watchHistoryDao.insertOrUpdate(entity)

            val user = SupabaseService.client.auth.currentSessionOrNull()?.user
            if (user == null) {
                refreshWatchHistory()
                return@withContext
            }

            // Get existing profile
            val profile = SupabaseService.client.postgrest["user_profiles"]
                .select { filter { eq("user_id", user.id) } }
                .decodeSingleOrNull<com.example.data.model.UserProfile>()
            
            val currentHistory = profile?.watchHistory?.toMutableList() ?: mutableListOf()
            
            // Remove existing entry for this media if it exists
            currentHistory.removeAll { it.id == id && it.mediaType == (if (mediaType == MediaType.TV) "tv" else "movie") }
            
            // Add new entry to the front
            val newEntry = com.example.data.model.WatchHistoryDto(
                id = id,
                title = title,
                name = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                mediaType = if (mediaType == MediaType.TV) "tv" else "movie",
                timestamp = System.currentTimeMillis(),
                season = season,
                episode = episode,
                durationMs = durationMs,
                lastPositionMs = positionMs
            )
            currentHistory.add(0, newEntry) // Add to top
            
            // Keep only latest 50 items to keep JSON small
            val limitedHistory = currentHistory.take(50)
            
            if (profile == null) {
                val newProfile = com.example.data.model.UserProfile(user.id, limitedHistory)
                SupabaseService.client.postgrest["user_profiles"].insert(newProfile)
            } else {
                SupabaseService.client.postgrest["user_profiles"].update(
                    { set("watch_history", limitedHistory) }
                ) { filter { eq("user_id", user.id) } }
            }
            refreshWatchHistory()
        }
    }

    suspend fun deleteWatchHistory(id: Int) = withContext(Dispatchers.IO) {
        runCatching {
            watchHistoryDao.deleteById(id)
            val user = SupabaseService.client.auth.currentSessionOrNull()?.user
            if (user == null) {
                refreshWatchHistory()
                return@withContext
            }
            
            val profile = SupabaseService.client.postgrest["user_profiles"]
                .select { filter { eq("user_id", user.id) } }
                .decodeSingleOrNull<com.example.data.model.UserProfile>() ?: return@withContext
                
            val currentHistory = profile.watchHistory?.toMutableList() ?: return@withContext
            val removed = currentHistory.removeAll { it.id == id }
            
            if (removed) {
                SupabaseService.client.postgrest["user_profiles"].update(
                    { set("watch_history", currentHistory) }
                ) { filter { eq("user_id", user.id) } }
                refreshWatchHistory()
            }
        }
    }

    suspend fun clearWatchHistory() = withContext(Dispatchers.IO) {
        runCatching {
            watchHistoryDao.clearAll()
            _watchHistory.value = emptyList()
            val user = SupabaseService.client.auth.currentSessionOrNull()?.user
            if (user == null) {
                return@withContext
            }
            
            SupabaseService.client.postgrest["user_profiles"].update(
                { set("watch_history", emptyList<com.example.data.model.WatchHistoryDto>()) }
            ) { filter { eq("user_id", user.id) } }
            refreshWatchHistory()
        }
    }

    suspend fun getBookmarks(): List<Bookmark> = withContext(Dispatchers.IO) {
        runCatching {
            val user = SupabaseService.client.auth.currentSessionOrNull()?.user
            if (user == null) {
                return@withContext bookmarkDao.getAllBookmarksList().map { entity ->
                    Bookmark(
                        id = null,
                        userId = null,
                        mediaId = entity.mediaId,
                        mediaType = entity.mediaType,
                        title = entity.title,
                        posterPath = entity.posterPath,
                        createdAt = null
                    )
                }
            }
            SupabaseService.client.postgrest["bookmarks"]
                .select {
                    filter {
                        eq("user_id", user.id)
                    }
                }
                .decodeList<Bookmark>()
        }.getOrDefault(emptyList())
    }

    suspend fun addBookmark(bookmark: Bookmark): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            bookmarkDao.insertOrUpdate(
                com.example.data.local.BookmarkEntity(
                    mediaId = bookmark.mediaId,
                    mediaType = bookmark.mediaType,
                    title = bookmark.title,
                    posterPath = bookmark.posterPath
                )
            )
            val user = SupabaseService.client.auth.currentSessionOrNull()?.user
            if (user != null) {
                val bookmarkWithUser = bookmark.copy(userId = user.id)
                SupabaseService.client.postgrest["bookmarks"].insert(bookmarkWithUser)
            }
            true
        }.getOrDefault(false)
    }

    suspend fun removeBookmark(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            bookmarkDao.deleteById(mediaId)
            val user = SupabaseService.client.auth.currentSessionOrNull()?.user
            if (user != null) {
                SupabaseService.client.postgrest["bookmarks"].delete {
                    filter {
                        eq("user_id", user.id)
                        eq("media_id", mediaId)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun isBookmarked(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val local = bookmarkDao.getBookmarkById(mediaId)
            if (local != null) return@withContext true
            val user = SupabaseService.client.auth.currentSessionOrNull()?.user ?: return@withContext false
            val result = SupabaseService.client.postgrest["bookmarks"]
                .select {
                    filter {
                        eq("user_id", user.id)
                        eq("media_id", mediaId)
                    }
                }.decodeList<Bookmark>()
            result.isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun clearLocalDataOnLogout() = withContext(Dispatchers.IO) {
        watchHistoryDao.clearAll()
        bookmarkDao.clearAll()
        _watchHistory.value = emptyList()
    }
    
    // High fidelity curated fallback list
    private fun getCuratedFallback(): List<MediaItem> = listOf(
        MediaItem(
            id = 101,
            title = "Echoes of the Cosmos",
            overview = "An intrepid crew journeys across deep space through an uncharted cosmic rift to find a sanctuary world for humanity.",
            posterUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/xJHokMbljvjADYDit5fK5VQsXEG.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.8,
            releaseDate = "2024-05-12"
        ),
        MediaItem(
            id = 102,
            title = "Solaris Horizon",
            overview = "A rebel pilot navigates the harsh desert storms of a desolate moon to unite splintered factions against an iron empire.",
            posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/xOMo8BRK7PfcJv9JCnx7s520b22.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.5,
            releaseDate = "2024-03-18"
        ),
        MediaItem(
            id = 103,
            title = "Neon Genesis Chronos",
            overview = "In a hyper-technological metropolis, two cybernetic guardians uncover a clandestine plot altering the timelines of civilization.",
            posterUrl = "https://image.tmdb.org/t/p/w500/abf8tHznhSvl9BAElD2cQYRr6fZ.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/9faGSFi5jam6pDWGNdFaF8IRneP.jpg",
            mediaType = MediaType.TV,
            rating = 8.9,
            releaseDate = "2023-11-10"
        ),
        MediaItem(
            id = 104,
            title = "Cyberdrift Protocol",
            overview = "Two rogue bounty hunters clash in a neon-drenched subterranean mega-city while pursuing a dangerous synth fugitive.",
            posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.1,
            releaseDate = "2024-07-20"
        ),
        MediaItem(
            id = 105,
            title = "Blade of the Spirit Realm",
            overview = "A courageous young warrior trains under the mountain sages to master elemental sword arts and defend his realm from shadow beasts.",
            posterUrl = "https://image.tmdb.org/t/p/w500/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/nTvM4mhqZlHIvUkIvdikWSt262d.jpg",
            mediaType = MediaType.TV,
            rating = 9.2,
            releaseDate = "2023-08-15"
        )
    )

    private fun getCuratedFallbackAnime(): List<MediaItem> = listOf(
        MediaItem(
            id = 401,
            title = "Blade of the Spirit Realm",
            overview = "A young swordsman sets out with companions to heal an ancient mystical curse.",
            posterUrl = "https://image.tmdb.org/t/p/w500/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/nTvM4mhqZlHIvUkIvdikWSt262d.jpg",
            mediaType = MediaType.TV,
            rating = 9.2,
            releaseDate = "2023-08-15"
        ),
        MediaItem(
            id = 402,
            title = "Wings of the Colossus",
            overview = "Defenders pilot gear to protect humanity's towering sanctuary walls against ancient leviathans.",
            posterUrl = "https://image.tmdb.org/t/p/w500/hTP1IIv546Pfunnn9KbY599h907.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/rqbCbjB19amt0rFQv09Em853340.jpg",
            mediaType = MediaType.TV,
            rating = 8.9,
            releaseDate = "2022-01-09"
        ),
        MediaItem(
            id = 403,
            title = "Phantom Sorcery",
            overview = "Students at an occult academy master cursed techniques to safeguard their realm.",
            posterUrl = "https://image.tmdb.org/t/p/w500/hFWP5HkbVEe40hrXgtCeEZvX96I.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/gmECBs1egq0onvgxjh2y168v3jg.jpg",
            mediaType = MediaType.TV,
            rating = 8.8,
            releaseDate = "2023-07-06"
        )
    )

    private fun getCuratedFallbackBollywood(): List<MediaItem> = listOf(
        MediaItem(
            id = 501,
            title = "Jawan",
            overview = "A high-octane emotional action thriller outlining the journey of a man who sets out to rectify the wrongs in society.",
            posterUrl = "https://image.tmdb.org/t/p/w500/jJw9rF9Fj9UuI8sR4dMUpk2pE0F.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/t5zCBSB5xMDKcDqe91qahCOUYVV.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.2,
            releaseDate = "2023-09-07"
        ),
        MediaItem(
            id = 502,
            title = "RRR",
            overview = "A fearless revolutionary and an officer in the British force decide to join forces for a historic freedom battle.",
            posterUrl = "https://image.tmdb.org/t/p/w500/kdOqPqH18q4Vp1qB7gVf7uH14.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/7gZZ9bO7Zk4nL5bU3P0fM3K8dJ.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.6,
            releaseDate = "2022-03-24"
        ),
        MediaItem(
            id = 503,
            title = "Kalki 2898 AD",
            overview = "A modern avatar of the legendary Hindu deity Vishnu descends to Earth to protect the world from evil forces in a futuristic era.",
            posterUrl = "https://image.tmdb.org/t/p/w500/sD3kGqFvVq0xV5L8M9K8p1d2D4.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
            mediaType = MediaType.MOVIE,
            rating = 7.9,
            releaseDate = "2024-06-27"
        )
    )

    private fun getCuratedFallbackCartoons(): List<MediaItem> = listOf(
        MediaItem(
            id = 601,
            title = "Mindscape Horizons",
            overview = "A whimsical exploration through the colorful subconscious architectures of young explorers.",
            posterUrl = "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/stKGOmPAgL6yPnjh0TJH8h3jhqi.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.0,
            releaseDate = "2024-06-11"
        ),
        MediaItem(
            id = 602,
            title = "The Spider Guild",
            overview = "Teenagers cross parallel universes to restore balance to the dimensional web.",
            posterUrl = "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.4,
            releaseDate = "2023-06-02"
        ),
        MediaItem(
            id = 603,
            title = "Dragon Valley Adventures",
            overview = "Two young vikings befriend mythical winged dragons in an enchanted northern fjord.",
            posterUrl = "https://image.tmdb.org/t/p/w500/ygGmAO60t8GyqUo9xTa0QqkyWg9.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",
            mediaType = MediaType.TV,
            rating = 8.3,
            releaseDate = "2022-09-14"
        )
    )

    private fun getCuratedFallbackDrama(): List<MediaItem> = listOf(
        MediaItem(
            id = 701,
            title = "The Oppenheimer Echo",
            overview = "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.",
            posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.5,
            releaseDate = "2023-07-21"
        ),
        MediaItem(
            id = 702,
            title = "Dynasty of the North",
            overview = "Noble kingdoms battle for power and the Iron Spire amidst an encroaching icy winter.",
            posterUrl = "https://image.tmdb.org/t/p/w500/1XS1oqL89opfnbLl8WnZY1O1uJx.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/2OMB0ynKlyIenMJWI2Dy9IWT4c.jpg",
            mediaType = MediaType.TV,
            rating = 8.7,
            releaseDate = "2022-04-10"
        ),
        MediaItem(
            id = 703,
            title = "Succession Protocols",
            overview = "A media conglomerate dynasty fractures as the patriarch contemplates the future of his global empire.",
            posterUrl = "https://image.tmdb.org/t/p/w500/7udW4F9b4rO4z0L3bM2k6x.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/9faGSFi5jam6pDWGNdFaF8IRneP.jpg",
            mediaType = MediaType.TV,
            rating = 8.8,
            releaseDate = "2023-03-26"
        )
    )

    private fun getCuratedFallbackHollywood(): List<MediaItem> = listOf(
        MediaItem(
            id = 801,
            title = "Solaris Horizon",
            overview = "A warrior navigates desert tempests and political intrigue across an alien expanse.",
            posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/xOMo8BRK7PfcJv9JCnx7s520b22.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.5,
            releaseDate = "2024-03-18"
        ),
        MediaItem(
            id = 802,
            title = "Echoes of the Cosmos",
            overview = "Astronauts chart unknown interstellar routes beyond the edge of charted solar systems.",
            posterUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/xJHokMbljvjADYDit5fK5VQsXEG.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.8,
            releaseDate = "2024-05-12"
        ),
        MediaItem(
            id = 803,
            title = "Cyberdrift Protocol",
            overview = "High octane street pursuits and digital espionage erupt in a high-tech underworld.",
            posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.1,
            releaseDate = "2024-07-20"
        )
    )

    private fun getCuratedFallbackMovies(): List<MediaItem> = getCuratedFallbackHollywood()

    private fun getCuratedFallbackTv(): List<MediaItem> = listOf(
        MediaItem(
            id = 901,
            title = "Neon Genesis Chronos",
            overview = "Twin sisters separated by an ideological divide fight for the technological destiny of two sister cities.",
            posterUrl = "https://image.tmdb.org/t/p/w500/abf8tHznhSvl9BAElD2cQYRr6fZ.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/9faGSFi5jam6pDWGNdFaF8IRneP.jpg",
            mediaType = MediaType.TV,
            rating = 8.9,
            releaseDate = "2023-11-10"
        ),
        MediaItem(
            id = 902,
            title = "Dynasty of the North",
            overview = "Noble kingdoms battle for power and the Iron Spire amidst an encroaching icy winter.",
            posterUrl = "https://image.tmdb.org/t/p/w500/1XS1oqL89opfnbLl8WnZY1O1uJx.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/2OMB0ynKlyIenMJWI2Dy9IWT4c.jpg",
            mediaType = MediaType.TV,
            rating = 8.7,
            releaseDate = "2022-04-10"
        ),
        MediaItem(
            id = 903,
            title = "The Apex Trials",
            overview = "Competitors test their strategic cunning in high-stakes survival games with unexpected revelations.",
            posterUrl = "https://image.tmdb.org/t/p/w500/dDlG06x4a7y7p7R4v2g4k1eQJ1Q.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/oaGvjB0DvdurWhf9ed97AJ23ZQI.jpg",
            mediaType = MediaType.TV,
            rating = 8.2,
            releaseDate = "2023-09-17"
        )
    )
}
