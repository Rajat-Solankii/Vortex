package com.rajatsolankii.vortex.features.vortex

import com.rajatsolankii.vortex.features.addons.AddonManifestParser
import com.rajatsolankii.vortex.features.addons.buildAddonResourceUrl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VortexBuiltinAddonTest {

    @Test
    fun manifestIsServedLocallyAndParses() = runTest {
        assertTrue(VortexBuiltinAddon.handles(VortexBuiltinAddon.MANIFEST_URL))
        val manifest = AddonManifestParser.parse(
            manifestUrl = VortexBuiltinAddon.MANIFEST_URL,
            payload = VortexBuiltinAddon.respond(VortexBuiltinAddon.MANIFEST_URL),
        )
        assertEquals(VortexBuiltinAddon.ADDON_ID, manifest.id)
        assertEquals("Vortex", manifest.name)
        assertTrue(manifest.catalogs.any { it.type == "series" && it.id == "vortex.anime" })
        assertTrue(manifest.catalogs.any { it.id == "vortex.search" && it.extra.any { e -> e.name == "search" && e.isRequired } })
        assertTrue(manifest.resources.any { it.name == "stream" })
    }

    @Test
    fun resourceUrlsForTheBuiltinSourceAreHandledLocally() {
        val url = buildAddonResourceUrl(
            manifestUrl = VortexBuiltinAddon.MANIFEST_URL,
            resource = "stream",
            type = "series",
            id = "tmdb:1399:1:2",
        )
        assertTrue(VortexBuiltinAddon.handles(url))
        assertFalse(VortexBuiltinAddon.handles("https://v3-cinemeta.strem.io/manifest.json"))
    }

    @Test
    fun streamsListEveryServerForTheEpisode() = runTest {
        val url = buildAddonResourceUrl(
            manifestUrl = VortexBuiltinAddon.MANIFEST_URL,
            resource = "stream",
            type = "series",
            id = "tmdb:1399:2:5",
        )
        val payload = VortexBuiltinAddon.respond(url)
        assertTrue(payload.contains("https://nxsha.space/embed/tv/1399/2/5"))
        assertTrue(payload.contains("https://vidrock.net/embed/tv/1399/2/5"))
        assertTrue(payload.contains("\"name\":\"Vortex Server 1\""))
        assertTrue(payload.contains("\"name\":\"Vortex Server 7\""))
        assertEquals(VortexServer.entries.size, Regex("\"url\"").findAll(payload).count())
    }

    @Test
    fun embedUrlsAreRecognisedAndExpandToAllServers() {
        val target = VortexEmbedTarget(tmdbId = 550, isSeries = false)
        val movieUrl = VortexServer.SERVER5.embedUrl(target)
        assertEquals("https://vixsrc.to/embed/movie/550", movieUrl)
        assertTrue(VortexServers.isEmbedUrl(movieUrl))
        assertFalse(VortexServers.isEmbedUrl("https://cdn.example.com/video.mp4"))
        assertEquals(VortexServer.entries.size, VortexServers.sourcesForUrl(movieUrl).size)
    }

    @Test
    fun videoIdsParseIntoEmbedTargets() {
        assertEquals(
            VortexEmbedTarget(tmdbId = 1399, isSeries = true, season = 3, episode = 4),
            VortexServers.targetFromVideoId("tmdb:1399:3:4", "series"),
        )
        assertEquals(VortexEmbedTarget(tmdbId = 550, isSeries = false), VortexServers.targetFromVideoId("tmdb:550", "movie"))
        assertNull(VortexServers.targetFromVideoId("tt0137523", "movie"))
    }

    @Test
    fun adShieldBlocksAdsAndOffSiteNavigation() {
        assertTrue(VortexAdShield.isAdUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertFalse(VortexAdShield.isAdUrl("https://vidrock.net/embed/movie/550"))
        assertTrue(VortexAdShield.shouldBlockNavigation("market://details?id=x", "vidrock.net"))
        assertTrue(VortexAdShield.shouldBlockNavigation("https://random-casino.example/", "vidrock.net"))
        assertFalse(VortexAdShield.shouldBlockNavigation("https://vidrock.net/embed/tv/1/1/1", "vidrock.net"))
    }

    @Test
    fun tmdbUrlsGoThroughTheVortexProxy() {
        assertEquals(
            "https://api.example.test/api/tmdb?path=discover/tv&with_genres=16,10751&page=2",
            VortexApi.tmdbUrl(
                host = "https://api.example.test/",
                path = "discover/tv",
                query = linkedMapOf("api_key" to "ignored", "with_genres" to "16,10751", "page" to "2"),
            ),
        )
        assertEquals("fight club", "fight%20club".percentDecode())
    }

    @Test
    fun serversExposeOneToSevenAndTmdbAnimeFilterDetectsJapaneseAnimation() {
        assertEquals(
            listOf(
                "Vortex Server 1",
                "Vortex Server 2",
                "Vortex Server 3",
                "Vortex Server 4",
                "Vortex Server 5",
                "Vortex Server 6",
                "Vortex Server 7",
            ),
            VortexServer.entries.map { it.displayName },
        )
        val instant = VortexBuiltinAddon.instantStreamsFor(
            type = "series",
            videoId = "tmdb:1399:2:5",
            parentMetaId = "tmdb:1399",
            season = 2,
            episode = 5,
        )
        assertEquals(VortexServer.entries.size, instant.size)
        assertEquals("Vortex Server 1", instant.first().name)
        assertEquals("Vortex Server 7", instant.last().name)

        val japaneseAnime = buildJsonObject {
            putJsonArray("genre_ids") { add(16); add(10759) }
            put("original_language", "ja")
            putJsonArray("origin_country") { add("JP") }
        }
        val westernAnimation = buildJsonObject {
            putJsonArray("genre_ids") { add(16); add(35) }
            put("original_language", "en")
            putJsonArray("origin_country") { add("US") }
        }
        assertTrue(VortexBuiltinAddon.isTmdbJapaneseAnime(japaneseAnime))
        assertFalse(VortexBuiltinAddon.isTmdbJapaneseAnime(westernAnimation))
    }

    @Test
    fun targetParsingReversesAllSevenEmbedServerFormats() {
        val movieTarget = VortexEmbedTarget(tmdbId = 1726, isSeries = false)
        val tvTarget = VortexEmbedTarget(tmdbId = 1399, isSeries = true, season = 2, episode = 5)
        for (server in VortexServer.entries) {
            assertEquals(movieTarget, VortexServers.targetForUrl(server.embedUrl(movieTarget)), server.name)
            assertEquals(tvTarget, VortexServers.targetForUrl(server.embedUrl(tvTarget)), server.name)
        }
    }
}
