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
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vortex", appName)
    }

    @Test
    fun `stream servers match available list`() {
        assertTrue(StreamServer.entries.size >= 3)
        assertEquals("Server 1", StreamServer.SERVER1.displayName)
        assertEquals("Server 2", StreamServer.SERVER2.displayName)
        assertEquals("Server 3", StreamServer.SERVER3.displayName)

        val server1Movie = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER1, 100, MediaType.MOVIE)
        assertTrue(server1Movie.isNotEmpty())

        val server2Tv = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER2, 200, MediaType.TV, 2, 5)
        assertTrue(server2Tv.isNotEmpty())

        val server3Movie = StreamUrlBuilder.buildWebEmbedUrl(StreamServer.SERVER3, 300, MediaType.MOVIE)
        assertTrue(server3Movie.isNotEmpty())
    }

    @Test
    fun `home categories match requested list`() {
        val categories = HomeCategory.entries.map { it.title }
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
        assertEquals(GitHubConfigService.CURRENT_VERSION_CODE, 4)
        assertEquals(GitHubConfigService.CURRENT_VERSION_NAME, "1.3")
    }
}
