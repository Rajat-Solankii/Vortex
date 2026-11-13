package com.example.data.remote

import com.example.data.model.MediaItemDto
import com.example.data.model.MovieDetailsDto
import com.example.data.model.SeasonDetailsDto
import com.example.data.model.TmdbResponse
import com.example.data.model.TvDetailsDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Retrofit interface communicating with the Next.js Vercel TMDB proxy.
 * Base URL: https://vortex-proxy-six.vercel.app/
 * The proxy routes requests using the `path` query parameter:
 * e.g. /api/tmdb?path=movie/popular&include_adult=false
 */
interface VortexApiService {

    @GET("api/tmdb")
    suspend fun getMediaList(
        @Query("path") path: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @QueryMap extraQueries: Map<String, String> = emptyMap()
    ): TmdbResponse<MediaItemDto>

    @GET("api/tmdb")
    suspend fun getMovieDetails(
        @Query("path") path: String,
        @QueryMap extraQueries: Map<String, String> = emptyMap()
    ): MovieDetailsDto

    @GET("api/tmdb")
    suspend fun getTvDetails(
        @Query("path") path: String,
        @QueryMap extraQueries: Map<String, String> = emptyMap()
    ): TvDetailsDto

    @GET("api/tmdb")
    suspend fun getSeasonDetails(
        @Query("path") path: String,
        @QueryMap extraQueries: Map<String, String> = emptyMap()
    ): SeasonDetailsDto

    @GET("api/tmdb")
    suspend fun search(
        @Query("path") path: String = "search/multi",
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbResponse<MediaItemDto>

    @GET("api/tmdb")
    suspend fun getProviders(
        @Query("path") path: String = "watch/providers/movie",
        @Query("watch_region") watchRegion: String = "US"
    ): com.example.data.model.ProviderResponse

    @GET("api/tmdb")
    suspend fun getCredits(
        @Query("path") path: String
    ): com.example.data.model.CreditsResponseDto

    @GET("api/tmdb")
    suspend fun getVideos(
        @Query("path") path: String
    ): com.example.data.model.VideosResponseDto
}
