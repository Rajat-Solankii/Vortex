package com.example.data.repository

import com.example.data.local.WatchHistoryDao
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.MediaDetail
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.toDomain
import com.example.data.remote.VortexApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VortexRepository(
    private val apiService: VortexApiService,
    private val watchHistoryDao: WatchHistoryDao
) {

    val watchHistory: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAllWatchHistory()

    suspend fun getTrending(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(path = "trending/all/week", page = page)
            val domainList = response.results.map { it.toDomain() }
            if (domainList.isNotEmpty()) domainList else getCuratedFallback().shuffled()
        }.recoverCatching {
            getCuratedFallback()
        }
    }

    suspend fun getAnime(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "discover/tv",
                page = page,
                includeAdult = false,
                extraQueries = mapOf("with_genres" to "16", "with_original_language" to "ja")
            )
            val domainList = response.results.map { it.toDomain(MediaType.TV) }
            if (domainList.isNotEmpty()) domainList else getCuratedFallbackAnime()
        }.recoverCatching {
            getCuratedFallbackAnime()
        }
    }

    suspend fun getBollywood(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "discover/movie",
                page = page,
                includeAdult = false,
                extraQueries = mapOf("with_original_language" to "hi", "sort_by" to "popularity.desc")
            )
            val domainList = response.results.map { it.toDomain(MediaType.MOVIE) }
            if (domainList.isNotEmpty()) domainList else getCuratedFallbackBollywood()
        }.recoverCatching {
            getCuratedFallbackBollywood()
        }
    }

    suspend fun getCartoons(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "discover/tv",
                page = page,
                includeAdult = false,
                extraQueries = mapOf("with_genres" to "16,10751", "sort_by" to "popularity.desc")
            )
            val domainList = response.results.map { it.toDomain(MediaType.TV) }
            if (domainList.isNotEmpty()) domainList else getCuratedFallbackCartoons()
        }.recoverCatching {
            getCuratedFallbackCartoons()
        }
    }

    suspend fun getDrama(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "discover/movie",
                page = page,
                includeAdult = false,
                extraQueries = mapOf("with_genres" to "18", "sort_by" to "popularity.desc")
            )
            val domainList = response.results.map { it.toDomain(MediaType.MOVIE) }
            if (domainList.isNotEmpty()) domainList else getCuratedFallbackDrama()
        }.recoverCatching {
            getCuratedFallbackDrama()
        }
    }

    suspend fun getHollywood(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "discover/movie",
                page = page,
                includeAdult = false,
                extraQueries = mapOf("with_original_language" to "en", "sort_by" to "popularity.desc")
            )
            val domainList = response.results.map { it.toDomain(MediaType.MOVIE) }
            if (domainList.isNotEmpty()) domainList else getCuratedFallbackHollywood()
        }.recoverCatching {
            getCuratedFallbackHollywood()
        }
    }

    suspend fun getMovies(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "movie/popular",
                page = page,
                includeAdult = false
            )
            val domainList = response.results.map { it.toDomain(MediaType.MOVIE) }
            if (domainList.isNotEmpty()) domainList else getCuratedFallbackMovies()
        }.recoverCatching {
            getCuratedFallbackMovies()
        }
    }

    suspend fun getTvShows(page: Int = 1): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMediaList(
                path = "tv/popular",
                page = page,
                includeAdult = false
            )
            val domainList = response.results.map { it.toDomain(MediaType.TV) }
            if (domainList.isNotEmpty()) domainList else getCuratedFallbackTv()
        }.recoverCatching {
            getCuratedFallbackTv()
        }
    }

    suspend fun getRecommendations(mediaId: Int, mediaType: MediaType): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val recPath = if (mediaType == MediaType.TV) "tv/$mediaId/recommendations" else "movie/$mediaId/recommendations"
            val response = apiService.getMediaList(path = recPath, page = 1)
            val list = response.results.map { it.toDomain(mediaType) }
            if (list.isNotEmpty()) {
                list
            } else {
                val simPath = if (mediaType == MediaType.TV) "tv/$mediaId/similar" else "movie/$mediaId/similar"
                val simResp = apiService.getMediaList(path = simPath, page = 1)
                val simList = simResp.results.map { it.toDomain(mediaType) }
                if (simList.isNotEmpty()) simList else getCuratedFallback().filter { it.id != mediaId }
            }
        }.recoverCatching {
            getCuratedFallback().filter { it.id != mediaId }
        }
    }

    suspend fun getMovieDetails(id: Int): Result<MediaDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getMovieDetails(path = "movie/$id")
            val posterFull = if (!dto.posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500${dto.posterPath}" else ""
            val backdropFull = if (!dto.backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w780${dto.backdropPath}" else posterFull
            val durationText = dto.runtime?.let { "${it / 60}h ${it % 60}m" } ?: "Movie"
            val genreNames = dto.genres?.map { it.name } ?: emptyList()

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
                rawBackdropPath = dto.backdropPath
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
            val posterFull = if (!dto.posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500${dto.posterPath}" else ""
            val backdropFull = if (!dto.backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w780${dto.backdropPath}" else posterFull
            val seasonsText = dto.numberOfSeasons?.let { "$it Seasons" } ?: "TV Series"
            val genreNames = dto.genres?.map { it.name } ?: emptyList()

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
                rawBackdropPath = dto.backdropPath
            )
        }.recoverCatching {
            val item = getCuratedFallback().find { it.id == id }
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
                genres = listOf("Animation", "Sci-Fi", "Mystery")
            )
        }
    }

    suspend fun search(query: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.search(query = query)
            response.results.map { it.toDomain() }
        }.recoverCatching {
            getCuratedFallback().filter {
                it.title.contains(query, ignoreCase = true) || it.overview.contains(query, ignoreCase = true)
            }
        }
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
    }

    suspend fun deleteWatchHistory(id: Int) = withContext(Dispatchers.IO) {
        watchHistoryDao.deleteById(id)
    }

    suspend fun clearWatchHistory() = withContext(Dispatchers.IO) {
        watchHistoryDao.clearAll()
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
