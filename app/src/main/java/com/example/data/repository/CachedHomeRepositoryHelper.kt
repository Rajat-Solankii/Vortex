package com.example.data.repository

import com.example.data.local.CachedHomeItem
import com.example.data.local.CachedHomeItemDao
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.remote.VortexApiService
import com.example.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

suspend fun VortexRepository.getCachedHomeCategory(
    category: String,
    path: String,
    mediaType: MediaType,
    extraQueries: Map<String, String> = emptyMap(),
    cachedHomeItemDao: CachedHomeItemDao,
    apiService: VortexApiService,
    fallback: () -> List<MediaItem>
): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
    runCatching {
        coroutineScope {
            val firstItem = cachedHomeItemDao.getFirstItemInCategory(category)
        val isStale = firstItem == null || (System.currentTimeMillis() - firstItem.updatedAt > 24 * 60 * 60 * 1000)

        if (!isStale) {
            val cached = cachedHomeItemDao.getItemsByCategory(category)
            if (cached.isNotEmpty()) {
                return@coroutineScope ContentFilter.filterDomain(cached.map {
                    MediaItem(
                        id = it.mediaId,
                        title = it.title,
                        overview = it.overview,
                        posterUrl = if (it.posterPath != null) "https://image.tmdb.org/t/p/w500${it.posterPath}" else "",
                        backdropUrl = if (it.backdropPath != null) "https://image.tmdb.org/t/p/w780${it.backdropPath}" else "",
                        mediaType = if (it.mediaType == "TV") MediaType.TV else MediaType.MOVIE,
                        rating = it.rating,
                        releaseDate = it.releaseDate,
                        rawPosterPath = it.posterPath,
                        rawBackdropPath = it.backdropPath
                    )
                })
            }
        }

        // Fetch 4 pages from API
        val pagesToFetch = 1..4
        val deferredPages = pagesToFetch.map { page ->
            async {
                apiService.getMediaList(
                    path = path,
                    page = page,
                    includeAdult = false,
                    extraQueries = extraQueries
                )
            }
        }

        val allResults = ContentFilter.filterDtos(deferredPages.awaitAll().flatMap { it.results })
        val domainList = allResults.map { it.toDomain(mediaType) }.distinctBy { it.id }

        if (domainList.isNotEmpty()) {
            cachedHomeItemDao.deleteByCategory(category)
            val newEntities = domainList.map { item ->
                CachedHomeItem(
                    mediaId = item.id,
                    category = category,
                    title = item.title,
                    overview = item.overview,
                    posterPath = item.rawPosterPath,
                    backdropPath = item.rawBackdropPath,
                    mediaType = item.mediaType.name,
                    rating = item.rating,
                    releaseDate = item.releaseDate
                )
            }
            cachedHomeItemDao.insertAll(newEntities)
                domainList
            } else {
                fallback()
            }
        }
    }.recoverCatching {
        val cached = cachedHomeItemDao.getItemsByCategory(category)
        if (cached.isNotEmpty()) {
            cached.map {
                MediaItem(
                    id = it.mediaId,
                    title = it.title,
                    overview = it.overview,
                    posterUrl = if (it.posterPath != null) "https://image.tmdb.org/t/p/w500${it.posterPath}" else "",
                    backdropUrl = if (it.backdropPath != null) "https://image.tmdb.org/t/p/w780${it.backdropPath}" else "",
                    mediaType = if (it.mediaType == "TV") MediaType.TV else MediaType.MOVIE,
                    rating = it.rating,
                    releaseDate = it.releaseDate,
                    rawPosterPath = it.posterPath,
                    rawBackdropPath = it.backdropPath
                )
            }
        } else {
            fallback()
        }
    }
}
