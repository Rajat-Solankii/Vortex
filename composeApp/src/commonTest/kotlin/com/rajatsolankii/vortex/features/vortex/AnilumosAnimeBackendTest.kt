package com.rajatsolankii.vortex.features.vortex

import com.rajatsolankii.vortex.features.addons.AddonManifestParser
import com.rajatsolankii.vortex.features.addons.buildAddonResourceUrl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnilumosAnimeBackendTest {

    @Test
    fun manifestIncludesAnilistPrefixAndAnimeSearch() = runTest {
        val manifest = AddonManifestParser.parse(
            manifestUrl = VortexBuiltinAddon.MANIFEST_URL,
            payload = VortexBuiltinAddon.respond(VortexBuiltinAddon.MANIFEST_URL),
        )
        assertTrue(manifest.idPrefixes.contains("anilist:"))
        assertTrue(manifest.catalogs.any { it.type == "series" && it.id == "vortex.anime" })
        assertTrue(manifest.catalogs.any { it.type == "anime" && it.id == "vortex.search" && it.extra.any { e -> e.name == "search" } })
        val streamUrl = buildAddonResourceUrl(
            manifestUrl = VortexBuiltinAddon.MANIFEST_URL,
            resource = "stream",
            type = "series",
            id = "anilist:151807:1:3",
        )
        assertTrue(VortexBuiltinAddon.handles(streamUrl))
    }

    @Test
    fun anilistTargetsAndStreamDescriptorsParseCleanly() {
        assertEquals(151807, AnilumosAnimeBackend.parseAnilistId("anilist:151807"))
        assertEquals(151807, AnilumosAnimeBackend.parseAnilistId("anilist:151807:1:12"))
        assertNull(AnilumosAnimeBackend.parseAnilistId("tmdb:1368337"))

        val target = AnilumosAnimeBackend.parseAnilistTarget(
            videoId = "anilist:151807:1:7",
            parentMetaId = "anilist:151807",
            episode = null,
        )
        assertEquals(AnilumosAnimeBackend.AnilumosTarget(151807, 7), target)

        val autoUrl = AnilumosAnimeBackend.autoStreamUrl(151807, 7)
        assertTrue(VortexMediaExtractor.isResolvableUrl(autoUrl))
        assertFalse(VortexServers.isEmbedUrl(autoUrl))

        val spec = AnilumosAnimeBackend.parseStreamUrl("anilumos://anipm/151807/7/dub")
        assertEquals(AnilumosAnimeBackend.AnilumosStreamSpec("anipm", 151807, 7, "dub"), spec)
        assertEquals("Vortex Anime Server 1", AnilumosAnimeBackend.displayNameForStreamUrl("anilumos://anipm/151807/7/sub"))
        assertEquals("Vortex Anime Server 2", AnilumosAnimeBackend.displayNameForStreamUrl("anilumos://anipm/151807/7/dub"))
        assertEquals("Vortex Anime Server 3", AnilumosAnimeBackend.displayNameForStreamUrl("anilumos://anichan/151807/7/sub"))
        assertEquals("Vortex Anime Server 4", AnilumosAnimeBackend.displayNameForStreamUrl("anilumos://anichan/151807/7/dub"))
        assertEquals("Vortex Anime Server 5", AnilumosAnimeBackend.displayNameForStreamUrl("anilumos://animeheaven/151807/7/sub"))

        val instant = AnilumosAnimeBackend.instantStreamsFor(151807, 3)
        assertEquals(
            listOf(
                "Vortex Anime Server 1",
                "Vortex Anime Server 2",
                "Vortex Anime Server 3",
                "Vortex Anime Server 4",
                "Vortex Anime Server 5",
            ),
            instant.map { it.name },
        )
    }

    @Test
    fun catalogDeduplicatesSequelSeasonsAndOrdersMultiSeasonChain() {
        val season1 = buildJsonObject {
            put("id", 101)
            put("format", "TV")
            putJsonObject("startDate") { put("year", 2020) }
            putJsonObject("relations") {
                put(
                    "edges",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("relationType", "SEQUEL")
                                putJsonObject("node") {
                                    put("id", 102)
                                    put("format", "TV")
                                }
                            },
                        )
                    },
                )
            }
        }
        val season2 = buildJsonObject {
            put("id", 102)
            put("format", "TV")
            putJsonObject("startDate") { put("year", 2022) }
            putJsonObject("relations") {
                put(
                    "edges",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("relationType", "PREQUEL")
                                putJsonObject("node") {
                                    put("id", 101)
                                    put("format", "TV")
                                }
                            },
                        )
                    },
                )
            }
        }
        val standalone = buildJsonObject {
            put("id", 201)
            put("format", "TV")
            putJsonObject("startDate") { put("year", 2023) }
        }

        val deduped = AnilumosAnimeBackend.deduplicateAnimeCatalogEntries(listOf(season2, season1, standalone))
        assertEquals(listOf(101, 201), deduped.map { it["id"].toString().toInt() })

        val orderedChain = AnilumosAnimeBackend.orderSeasonChainMedia(
            anchorId = 102,
            mediaMap = mapOf(102 to season2, 101 to season1),
        )
        assertEquals(listOf(101, 102), orderedChain.map { it["id"].toString().toInt() })
    }

    @Test
    fun animeHeavenParsersExtractResultsEpisodesAndRealVideoSources() {
        val searchHtml = """
            <div class="similarimg">
              <a href="anime.php?abc123"><img src="poster.jpg" alt="Solo Leveling"></a>
              <div class="similarname"><a href="anime.php?abc123">Solo Leveling</a></div>
            </div>
        """.trimIndent()
        val parsedSearch = AnilumosAnimeBackend.parseAnimeHeavenSearch(searchHtml)
        assertEquals(listOf(AnilumosAnimeBackend.AnimeHeavenSearchResult("anime.php?abc123", "Solo Leveling")), parsedSearch)

        val animeHtml = """
            <a onclick='gatea("deadbeef01")'><div class="watch2">1</div></a>
            <a onclick='gatea("deadbeef02")'><div class="watch2">2</div></a>
        """.trimIndent()
        val episodes = AnilumosAnimeBackend.parseAnimeHeavenEpisodes(animeHtml)
        assertEquals(
            listOf(
                AnilumosAnimeBackend.AnimeHeavenEpisode(1, "deadbeef01"),
                AnilumosAnimeBackend.AnimeHeavenEpisode(2, "deadbeef02"),
            ),
            episodes,
        )

        val gateHtml = """
            <!-- <video><source src="https://ax.animeheaven.me/decoy.mp4?2c523c8c" type="video/mp4"></video> -->
            <video>
              <source src="https://ax.animeheaven.me/ep2.mp4?deadbeef02&token=xyz" type="video/mp4">
              <source src="https://ct.animeheaven.me/ep2.mp4?deadbeef02&error" type="video/mp4">
            </video>
        """.trimIndent()
        val urls = AnilumosAnimeBackend.parseAnimeHeavenVideoUrls(gateHtml, "deadbeef02")
        assertEquals(
            listOf(
                "https://ax.animeheaven.me/ep2.mp4?deadbeef02&token=xyz",
                "https://ct.animeheaven.me/ep2.mp4?deadbeef02&error",
            ),
            urls,
        )
    }
}
