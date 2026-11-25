package com.rajatsolankii.vortex.features.vortex

import com.rajatsolankii.vortex.features.player.AndroidPlaybackEngine
import com.rajatsolankii.vortex.features.player.resolveAutoPlaybackEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VortexMediaExtractorTest {

    @Test
    fun extractsMasterPlaylistBlockWithTokenAndSubtitles() {
        val html = """
            <html>
              <script>
                window.masterPlaylist = {
                  params: {
                    'token': 'abc123',
                    'expires': '1760000000',
                    'asn': '1'
                  },
                  url: 'https://vixsrc.to/playlist/12345'
                };
                var tracks = [{"file":"https://cdn.example.com/subs/en.vtt","label":"English"}];
              </script>
            </html>
        """.trimIndent()

        val extracted = VortexMediaExtractor.parseDirectMediaFromHtml(
            embedUrl = "https://vixsrc.to/embed/movie/12345",
            html = html,
        )

        assertNotNull(extracted)
        assertEquals(
            "https://vixsrc.to/playlist/12345?token=abc123&expires=1760000000&h=1",
            extracted.url,
        )
        assertEquals("https://vixsrc.to/", extracted.headers["Referer"])
        assertEquals("https://vixsrc.to", extracted.headers["Origin"])
        assertEquals(1, extracted.subtitles.size)
        assertEquals("https://cdn.example.com/subs/en.vtt", extracted.subtitles.first().url)
        assertEquals("en", extracted.subtitles.first().language)
    }

    @Test
    fun extractsEscapedJsonHlsSourceAndIgnoresAdUrls() {
        val html = """
            <script>
              var ad = "https:\/\/doubleclick.net\/vast.xml";
              var player = {
                "file": "https:\/\/cdn.streamhost.example\/hls\/1080\/master.m3u8?sig=xyz",
                "tracks": [{"src": "https:\/\/cdn.streamhost.example\/sub_es.srt", "label": "Spanish"}]
              };
            </script>
        """.trimIndent()

        val extracted = VortexMediaExtractor.parseDirectMediaFromHtml(
            embedUrl = "https://vidcore.io/embed/tv/99/1/2",
            html = html,
        )

        assertNotNull(extracted)
        assertEquals("https://cdn.streamhost.example/hls/1080/master.m3u8?sig=xyz", extracted.url)
        assertEquals("hls", extracted.streamType)
        assertTrue(extracted.subtitles.any { it.url == "https://cdn.streamhost.example/sub_es.srt" && it.language == "es" })
    }

    @Test
    fun returnsNullWhenOnlyAdOrPageAssetsPresent() {
        val html = """
            <html>
              <script src="https://doubleclick.net/ads.js"></script>
              <video poster="https://cdn.example.com/poster.jpg"></video>
            </html>
        """.trimIndent()

        val extracted = VortexMediaExtractor.parseDirectMediaFromHtml(
            embedUrl = "https://nxsha.space/embed/movie/10",
            html = html,
        )
        assertNull(extracted)
    }

    @Test
    fun autoPlaybackEngineAlwaysUsesExoPlayerForAnimeAndMovies() {
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolveAutoPlaybackEngine(
                playbackEngineSetting = AndroidPlaybackEngine.Auto,
                videoId = "anilist:151807",
                streamFilename = "Solo.Leveling.S01E01.1080p.mkv",
                streamName = "Vortex",
                streamDescription = "Direct stream",
                contentTitle = "Solo Leveling",
                genres = listOf("Anime", "Action"),
                country = "Japan",
            ),
        )
        assertEquals(
            AndroidPlaybackEngine.ExoPlayer,
            resolveAutoPlaybackEngine(
                playbackEngineSetting = AndroidPlaybackEngine.Auto,
                videoId = "tmdb:550",
                streamFilename = null,
                streamName = "Vortex",
                streamDescription = null,
                contentTitle = "Fight Club",
                genres = listOf("Drama"),
                country = "US",
            ),
        )
    }

    @Test
    fun parsesRozgarleloProxyHtmlWithTxtManifestSegRefAndSubProxySubtitles() = runBlocking {
        val proxyHtml = """
            <!DOCTYPE html>
            <html>
            <body>
              <video id="video">
                <track kind="subtitles" src="/api/sub_proxy.php?url=https%3A%2F%2Fvsembed.com%2Fimg%2Fabc%2Fsub%2Fen.vtt&tok=123" label="EN - English" />
              </video>
              <script>
                window.CV_SUBTITLES = [{"url":"\/api\/sub_proxy.php?url=https%3A%2F%2Fvsembed.com%2Fimg%2Fabc%2Fsub%2Fen.vtt&tok=123","label":"EN - English"}];
                var EMBED_URL='https://vsembed.com/n84yk';
                var src="https:\/\/s2.pureharvesthub.shop\/cleie65e\/play\/qw8ts\/master.txt";
                var directSrc="https:\/\/s2.pureharvesthub.shop\/cleie65e\/play\/qw8ts\/master.txt";
                var SEG_REF="https:\/\/vsembed.com\/n84yk";
              </script>
            </body>
            </html>
        """.trimIndent()

        val extracted = VortexMediaExtractor.parseProxyPlayerHtml(
            embedUrl = "https://rozgarlelo.modiplay.xyz/embed/tmdb/movie?id=1368337",
            proxyUrl = "https://rozgarlelo.modiplay.xyz/proxy.php?p=vsembed&c=n84yk",
            proxyHtml = proxyHtml,
            verifyOnline = false,
        )

        assertNotNull(extracted)
        assertEquals("https://s2.pureharvesthub.shop/cleie65e/play/qw8ts/master.txt", extracted.url)
        assertEquals("hls", extracted.streamType)
        assertEquals("https://vsembed.com/n84yk", extracted.headers["Referer"])
        assertEquals("https://vsembed.com", extracted.headers["Origin"])
        assertTrue(extracted.subtitles.any {
            it.url == "https://rozgarlelo.modiplay.xyz/api/sub_proxy.php?url=https%3A%2F%2Fvsembed.com%2Fimg%2Fabc%2Fsub%2Fen.vtt&tok=123" &&
                it.language == "en"
        })
    }

    @Test
    fun parseProxyServerCandidatesPrioritizesFastCdnsAndDeduplicatesFrame() {
        val outerHtml = """
            <html>
              <body>
                <button class="server-btn" data-url="https://vidara.to/e/c721f8e89e6f" data-platform="vidara" data-pname="Vidara" data-code="c721f8e89e6f">Vidara</button>
                <button class="server-btn" data-url="https://filesunny-play.com/ymjg" data-platform="filesunny" data-pname="FileSunny" data-code="ymjg">FileSunny</button>
                <button class="server-btn" data-url="https://vsembed.com/n84yk" data-platform="vsembed" data-pname="VSEmbed" data-code="n84yk">VSEmbed</button>
                <iframe id="playerFrame" src="/proxy.php?p=vsembed&c=n84yk"></iframe>
              </body>
            </html>
        """.trimIndent()

        val candidates = VortexMediaExtractor.parseProxyServerCandidates(
            embedUrl = "https://rozgarlelo.modiplay.xyz/embed/tmdb/movie?id=1368337",
            outerHtml = outerHtml,
        )

        assertEquals(listOf("filesunny", "vidara", "vsembed"), candidates.map { it.platform })
        assertTrue(candidates.first().frameUrl.contains("p=filesunny&c=ymjg"))
    }

    @Test
    fun resolvesRelativeHlsVariantAndSegmentUrls() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1920x1080
            1080p/index.txt?sig=abc
        """.trimIndent()
        val variantUrl = VortexMediaExtractor.firstPlaylistEntryUrl(
            playlistUrl = "https://s10.brightcrestnetwork.cfd/v1/master.txt",
            playlistBody = master,
        )
        assertEquals("https://s10.brightcrestnetwork.cfd/v1/1080p/index.txt?sig=abc", variantUrl)

        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg-001.ts?tok=1
        """.trimIndent()
        val segmentUrl = VortexMediaExtractor.firstSegmentUrl(
            playlistUrl = variantUrl!!,
            playlistBody = mediaPlaylist,
        )
        assertEquals("https://s10.brightcrestnetwork.cfd/v1/1080p/seg-001.ts?tok=1", segmentUrl)
    }
}

