package com.rajatsolankii.vortex.features.player

import androidx.compose.ui.Modifier
import com.rajatsolankii.vortex.features.streams.StreamItem
import com.rajatsolankii.vortex.features.vortex.VortexBuiltinAddon
import com.rajatsolankii.vortex.features.vortex.VortexServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerPortraitServerSelectionTest {

    @Test
    fun highlightsOnlyTheSinglePlayingServerEvenWhenNamesWereGeneric() {
        val servers = listOf(
            StreamItem(
                name = "Vortex",
                title = "Vortex Server 1",
                url = "https://nxsha.space/embed/movie/550",
                addonName = "Vortex",
                addonId = VortexBuiltinAddon.ADDON_ID,
            ),
            StreamItem(
                name = "Vortex",
                title = "Vortex Server 2",
                url = "https://rozgarlelo.modiplay.xyz/embed/tmdb/movie?id=550",
                addonName = "Vortex",
                addonId = VortexBuiltinAddon.ADDON_ID,
            ),
            StreamItem(
                name = "Vortex",
                title = "Vortex Server 3",
                url = "https://primesrc.me/embed/movie?tmdb=550",
                addonName = "Vortex",
                addonId = VortexBuiltinAddon.ADDON_ID,
            ),
        )

        assertEquals("Vortex Server 2", servers[1].displayServerLabel())

        // Even with generic activeStreamTitle = "Vortex" and extracted CDN activeSourceUrl,
        // only index 1 matches activeVortexEmbedUrl.
        val selected = resolveSelectedServerIndex(
            allServers = servers,
            activeSourceUrl = "https://s2.cdn.example/master.m3u8",
            activeVortexEmbedUrl = "https://rozgarlelo.modiplay.xyz/embed/tmdb/movie?id=550",
            activeStreamTitle = "Vortex",
        )
        assertEquals(1, selected)
    }

    @Test
    fun highlightsNothingWhenOnlyGenericTitleMatches() {
        val servers = VortexBuiltinAddon.instantStreamsFor(
            type = "movie",
            videoId = "tmdb:550",
            parentMetaId = "tmdb:550",
            season = null,
            episode = null,
        )
        val selected = resolveSelectedServerIndex(
            allServers = servers,
            activeSourceUrl = "https://unknown.cdn.example/video.mp4",
            activeVortexEmbedUrl = null,
            activeStreamTitle = "Vortex",
        )
        assertEquals(-1, selected)
    }

    @Test
    fun backupServerCandidateAndWebFallbackSkipFailedServer() {
        val servers = VortexBuiltinAddon.instantStreamsFor(
            type = "movie",
            videoId = "tmdb:1726",
            parentMetaId = "tmdb:1726",
            season = null,
            episode = null,
        )
        val server1Url = servers[0].playableDirectUrl!!
        val runtime = PlayerScreenRuntime(
            PlayerScreenArgs(
                profileId = 1,
                title = "Iron Man",
                sourceUrl = server1Url,
                sourceAudioUrl = null,
                sourceHeaders = emptyMap(),
                sourceResponseHeaders = emptyMap(),
                streamType = null,
                providerName = VortexBuiltinAddon.NAME,
                streamTitle = VortexServer.SERVER1.displayName,
                streamSubtitle = null,
                initialBingeGroup = null,
                pauseDescription = null,
                onBack = {},
                onOpenInExternalPlayer = null,
                onOpenExternalUrl = null,
                modifier = Modifier,
                logo = null,
                poster = null,
                background = null,
                seasonNumber = null,
                episodeNumber = null,
                episodeTitle = null,
                episodeThumbnail = null,
                contentType = "movie",
                videoId = "tmdb:1726",
                parentMetaId = "tmdb:1726",
                parentMetaType = "movie",
                providerAddonId = VortexBuiltinAddon.ADDON_ID,
                torrentInfoHash = null,
                torrentFileIdx = null,
                torrentFilename = null,
                torrentTrackers = emptyList(),
                initialPositionMs = 0L,
                initialProgressFraction = null,
            ),
        ).apply {
            activeVortexEmbedUrl = server1Url
            triedVortexServers.add(VortexServer.SERVER1)
            triedVortexServers.add(VortexServer.SERVER2)
        }

        val next = runtime.nextBackupServerCandidate(servers)
        assertNotNull(next)
        assertEquals(VortexServer.SERVER3.displayName, next.displayServerLabel())

        val fallbackUrl = runtime.resolveWebFallbackEmbedUrl()
        assertEquals(server1Url, fallbackUrl)
        assertTrue(runtime.openWebPlayerFallback())
        assertEquals(server1Url, runtime.webFallbackEmbedUrl)
    }
}
