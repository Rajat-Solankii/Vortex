package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbResponse<T>(
    @Json(name = "page") val page: Int = 1,
    @Json(name = "results") val results: List<T> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 1,
    @Json(name = "total_results") val totalResults: Int = 0
)

@JsonClass(generateAdapter = true)
data class MediaItemDto(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class GenreDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class MovieDetailsDto(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "genres") val genres: List<GenreDto>? = null,
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class SeasonDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "season_number") val seasonNumber: Int = 1,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "air_date") val airDate: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "episode_number") val episodeNumber: Int = 1,
    @Json(name = "season_number") val seasonNumber: Int = 1,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "runtime") val runtime: Int? = null
)

@JsonClass(generateAdapter = true)
data class SeasonDetailsDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "episodes") val episodes: List<EpisodeDto>? = null
)

@JsonClass(generateAdapter = true)
data class TvDetailsDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "genres") val genres: List<GenreDto>? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "seasons") val seasons: List<SeasonDto>? = null
)

// Domain Models used across UI
enum class MediaType {
    MOVIE,
    TV
}

data class SeasonItem(
    val id: Int,
    val name: String,
    val seasonNumber: Int,
    val episodeCount: Int,
    val posterUrl: String
)

data class EpisodeItem(
    val id: Int,
    val name: String,
    val overview: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val stillUrl: String,
    val rating: Double,
    val airDate: String,
    val runtime: Int? = null
)

data class MediaItem(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val mediaType: MediaType,
    val rating: Double,
    val releaseDate: String,
    val rawPosterPath: String? = null,
    val rawBackdropPath: String? = null
)

data class MediaDetail(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val mediaType: MediaType,
    val rating: Double,
    val releaseDate: String,
    val durationOrSeasons: String,
    val tagline: String,
    val genres: List<String>,
    val rawPosterPath: String? = null,
    val rawBackdropPath: String? = null,
    val seasons: List<SeasonItem> = emptyList()
)

fun MediaItemDto.toDomain(fallbackType: MediaType = MediaType.MOVIE): MediaItem {
    val detectedType = when (mediaType?.lowercase()) {
        "tv" -> MediaType.TV
        "movie" -> MediaType.MOVIE
        else -> if (name != null && title == null) MediaType.TV else fallbackType
    }
    val displayTitle = title ?: name ?: "Untitled"
    val displayDate = releaseDate ?: firstAirDate ?: ""
    val posterFull = if (!posterPath.isNullOrBlank()) {
        "https://image.tmdb.org/t/p/w500$posterPath"
    } else ""
    val backdropFull = if (!backdropPath.isNullOrBlank()) {
        "https://image.tmdb.org/t/p/w780$backdropPath"
    } else posterFull

    return MediaItem(
        id = id,
        title = displayTitle,
        overview = overview ?: "",
        posterUrl = posterFull,
        backdropUrl = backdropFull,
        mediaType = detectedType,
        rating = voteAverage ?: 0.0,
        releaseDate = displayDate,
        rawPosterPath = posterPath,
        rawBackdropPath = backdropPath
    )
}
