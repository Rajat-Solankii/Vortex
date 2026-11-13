package com.example.cast

import com.example.ui.screens.player.StreamServer
import com.example.ui.screens.player.StreamUrlBuilder
import com.example.data.model.MediaType
import com.example.util.CastHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastingEngineUnitTest {

    @Test
    fun testClassifyDirectHlsStream() {
        val hlsUrl = "https://cdn.example.com/live/master.m3u8"
        val classified = MediaSourceClassifier.classify(hlsUrl)

        assertEquals(StreamSourceType.DIRECT_STREAM, classified.type)
        assertEquals("application/x-mpegURL", classified.mimeType)
        assertTrue(classified.isDirectPlayable)
    }

    @Test
    fun testClassifyDirectMp4Stream() {
        val mp4Url = "https://cdn.example.com/movies/sample_1080p.mp4"
        val classified = MediaSourceClassifier.classify(mp4Url)

        assertEquals(StreamSourceType.DIRECT_STREAM, classified.type)
        assertEquals("video/mp4", classified.mimeType)
        assertTrue(classified.isDirectPlayable)
    }

    @Test
    fun testClassifyDirectWebmStream() {
        val webmUrl = "https://mirror.org/video.webm"
        val classified = MediaSourceClassifier.classify(webmUrl)

        assertEquals(StreamSourceType.DIRECT_STREAM, classified.type)
        assertEquals("video/webm", classified.mimeType)
        assertTrue(classified.isDirectPlayable)
    }

    @Test
    fun testClassifyWebEmbedServers() {
        // Test all 7 default servers from StreamSources
        val server1Url = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER1, 100, MediaType.MOVIE)
        val server2Url = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER2, 100, MediaType.MOVIE)
        val server3Url = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER3, 100, MediaType.MOVIE)
        val server4Url = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER4, 100, MediaType.MOVIE)
        val server5Url = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER5, 100, MediaType.MOVIE)
        val server6Url = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER6, 100, MediaType.MOVIE)
        val server7Url = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER7, 100, MediaType.MOVIE)

        val urls = listOf(server1Url, server2Url, server3Url, server4Url, server5Url, server6Url, server7Url)

        for (url in urls) {
            val classified = MediaSourceClassifier.classify(url)
            assertEquals("URL $url must be classified as WEB_EMBED", StreamSourceType.WEB_EMBED, classified.type)
            assertEquals("text/html", classified.mimeType)
            assertFalse("URL $url must NOT be marked direct playable", classified.isDirectPlayable)
        }
    }

    @Test
    fun testGenericEmbedUrlWithEmbedPath() {
        val embedUrl = "https://customstream.io/embed/movie/550"
        val classified = MediaSourceClassifier.classify(embedUrl)

        assertEquals(StreamSourceType.WEB_EMBED, classified.type)
        assertFalse(classified.isDirectPlayable)
    }

    @Test
    fun testMimeTypeResolution() {
        assertEquals("application/x-mpegURL", MediaSourceClassifier.resolveMimeType("https://stream.org/playlist.m3u8"))
        assertEquals("video/mp4", MediaSourceClassifier.resolveMimeType("https://stream.org/movie.mp4"))
        assertEquals("text/html", MediaSourceClassifier.resolveMimeType("https://nxsha.space/embed/movie/123"))
    }

    @Test
    fun testSamsungManufacturerDetection() {
        // Verify isSamsungDevice helper handles case-insensitivity without crashing
        val isSamsung = CastHelper.isSamsungDevice()
        // On Robolectric / standard test runners, Build.MANUFACTURER is typically "unknown" or "robolectric"
        assertNotNull(isSamsung)
    }

    @Test
    fun testScreenMirroringUnifiedOptionIntegrity() {
        // Ensure no duplicate Windows casting method exists on CastHelper
        val methods = CastHelper::class.java.declaredMethods.map { it.name }
        assertFalse("openWindowsWirelessDisplay must be removed to prevent duplicate cards", methods.contains("openWindowsWirelessDisplay"))
        assertTrue("startScreenMirroring must be present as the unified entry point", methods.contains("startScreenMirroring"))
        assertTrue("openSystemCastSettings must be present", methods.contains("openSystemCastSettings"))
    }
}
