package com.rajatsolankii.vortex.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoPlaybackEngineResolverTest {

    @Test
    fun nonAutoEngineSettingsAreLeftAlone() {
        assertNull(resolve(setting = AndroidPlaybackEngine.ExoPlayer))
        assertNull(resolve(setting = AndroidPlaybackEngine.Libmpv))
    }

    @Test
    fun explicitEngineSettingIsNotOverriddenByAnimeSignals() {
        assertNull(
            resolve(
                setting = AndroidPlaybackEngine.ExoPlayer,
                videoId = "kitsu:7442",
                genres = listOf("Anime"),
            ),
        )
    }

    // Every case here pairs the HDR signal with an anime signal (an anime id prefix), because
    // ExoPlayer is also the plain fall-through result. Without the anime signal these assertions
    // would pass even if the HDR scan did not exist at all.

    @Test
    fun hdrInFilenameOverridesAnimeDetection() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "mal:1", filename = "Show.S01E01.2160p.HDR.mkv"),
        )
    }

    @Test
    fun hdrInStreamNameOverridesAnimeDetection() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "mal:1", streamName = "1080p HDR WEB-DL"),
        )
    }

    @Test
    fun hdrInStreamDescriptionOverridesAnimeDetection() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "mal:1", description = "Dolby Vision profile 8"),
        )
    }

    @Test
    fun hdrInContentTitleOverridesAnimeDetection() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "mal:1", title = "Some Movie HDR"),
        )
    }

    @Test
    fun hdrTokenVariantsAreDetected() {
        listOf(
            "movie.hdr.mkv",
            "movie.hdr10.mkv",
            "movie.hdr10+.mkv",
            "movie.dv.mkv",
            "movie.dolby vision.mkv",
            "movie.DOLBY VISION.mkv",
        ).forEach { filename ->
            assertEquals(
                AndroidPlaybackEngine.ExoPlayer,
                resolve(videoId = "mal:1", filename = filename),
                "expected HDR/DV detection for $filename",
            )
        }
    }

    @Test
    fun nonWordSeparatedAndSuffixedTokensAreNotDetected() {
        listOf(
            "movie.hdr10plus.mkv",
            "movie.dolby.vision.mkv",
        ).forEach { filename ->
            assertEquals(
                AndroidPlaybackEngine.ExoPlayer,
                resolve(videoId = "mal:1", filename = filename),
                "expected ExoPlayer for $filename",
            )
        }
    }

    @Test
    fun dvSubstringInsideAnotherWordIsNotMistakenForDolbyVision() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "mal:12345", filename = "Show.S01.DVDRip.1080p.mkv"),
        )
    }

    @Test
    fun hdrTakesPrecedenceOverAnimeDetection() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "mal:12345", filename = "Anime.Movie.2024.DV.mkv"),
        )
    }

    @Test
    fun hdrTakesPrecedenceOverAnimeGenre() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(genres = listOf("Animation", "Anime"), title = "Anime Movie HDR10"),
        )
    }

    @Test
    fun animeIdPrefixesSelectExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "kitsu:7442"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "mal:12345"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "anilist:21"))
    }

    @Test
    fun animeIdPrefixWithEpisodeSuffixSelectsExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "mal:12345:3"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "kitsu:7442:12"))
    }

    @Test
    fun nonAnimeIdsSelectExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "tt0137523"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "tmdb:1396"))
    }

    @Test
    fun animeGenreSelectsExoPlayerRegardlessOfCase() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("anime")))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Anime")))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Action", "ANIME", "Fantasy")))
    }

    @Test
    fun unrelatedGenresFallThroughToExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Action", "Drama")))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = emptyList()))
    }

    @Test
    fun urlBasenameIsUsedAsFilenameOnlyWhenItLooksLikeAFile() {
        // URL-basename fallback for streams without behaviorHints.filename.
        assertEquals(
            "Movie.2024.HDR10.mkv",
            sourceFilenameFromUrl("https://example.com/dl/Movie.2024.HDR10.mkv?token=abc"),
        )
        assertNull(sourceFilenameFromUrl("https://example.com/dl/segment"))
        assertNull(sourceFilenameFromUrl("https://example.com/"))
        assertNull(sourceFilenameFromUrl(null))
    }

    @Test
    fun animationFromJapanFullNameSelectsExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = "Japan"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("animation"), country = "japan"))
    }

    @Test
    fun animationFromJapanIsoCodeSelectsExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = "JP"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = "jp"))
    }

    @Test
    fun japanIsMatchedInCommaSeparatedCountriesAndSelectsExoPlayer() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(genres = listOf("Animation"), country = "United States, Japan"),
        )
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(genres = listOf("Animation"), country = "US, JP"),
        )
    }

    @Test
    fun animationFromAnotherCountrySelectsExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = "US, GB"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = "United States"))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = "JPX"))
    }

    @Test
    fun animationWithMissingCountrySelectsExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = null))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = ""))
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Animation"), country = "   "))
    }

    @Test
    fun japanCountryWithoutAnimationGenreSelectsExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(genres = listOf("Drama"), country = "Japan"))
    }

    @Test
    fun animeIdPrefixWithAnimationSelectsExoPlayer() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "kitsu:7442", genres = listOf("Animation"), country = "United States"),
        )
    }

    @Test
    fun animeIdPrefixEvaluatedWithoutMetadataSelectsExoPlayer() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolve(videoId = "mal:1", genres = emptyList(), country = null),
        )
    }

    @Test
    fun noSignalsAtAllFallThroughToExoPlayer() {
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve())
        assertEquals(AndroidPlaybackEngine.ExoPlayer, resolve(videoId = "", title = ""))
    }

    private fun resolve(
        setting: AndroidPlaybackEngine = AndroidPlaybackEngine.Auto,
        videoId: String? = null,
        filename: String? = null,
        streamName: String? = null,
        description: String? = null,
        title: String? = null,
        genres: List<String> = emptyList(),
        country: String? = null,
    ): AndroidPlaybackEngine? = resolveAutoPlaybackEngine(
        playbackEngineSetting = setting,
        videoId = videoId,
        streamFilename = filename,
        streamName = streamName,
        streamDescription = description,
        contentTitle = title,
        genres = genres,
        country = country,
    )
}
