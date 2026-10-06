package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MediaType
import com.example.data.remote.GitHubConfigService
import com.example.ui.screens.home.HomeCategory
import com.example.ui.screens.player.StreamServer
import com.example.ui.screens.player.StreamUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vortex", appName)
    }

    @Test
    fun `stream servers match requested 3 servers`() {
        assertEquals(3, StreamServer.values().size)
        assertEquals("Server 1", StreamServer.SERVER1.displayName)
        assertEquals("Server 2", StreamServer.SERVER2.displayName)
        assertEquals("Server 3", StreamServer.SERVER3.displayName)

        val server1Movie = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER1, 100, MediaType.MOVIE)
        assertTrue(server1Movie.contains("vaplayer.ru/embed/movie/100"))

        val server2Tv = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER2, 200, MediaType.TV, 2, 5)
        assertTrue(server2Tv.contains("player.videasy.net/tv/200/2/5"))

        val server3Movie = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER3, 300, MediaType.MOVIE)
        assertTrue(server3Movie.contains("nhdapi.com/embed/movie/300"))
    }

    @Test
    fun `home categories match requested list`() {
        val categories = HomeCategory.values().map { it.title }
        assertTrue(categories.contains("Anime"))
        assertTrue(categories.contains("Bollywood"))
        assertTrue(categories.contains("Cartoons"))
        assertTrue(categories.contains("Drama"))
        assertTrue(categories.contains("Hollywood"))
        assertTrue(categories.contains("Movies"))
        assertTrue(categories.contains("TV Shows"))
    }

    @Test
    fun `github config service current version`() {
        assertEquals(2, GitHubConfigService.CURRENT_VERSION_CODE)
        assertEquals("1.0", GitHubConfigService.CURRENT_VERSION_NAME)
    }
}
