package com.rajatsolankii.vortex.core.network

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NetworkStatusRepositoryTest {
    @Test
    fun `offline public internet returns NoInternet without probing backend`() = runBlocking {
        var probed = false
        val condition = NetworkStatusRepository.resolveNetworkCondition(
            hasInternet = false,
            backendEndpoints = listOf("https://example.supabase.co/rest/v1/"),
            shouldCheckBackend = true,
            probeEndpoint = {
                probed = true
                true
            },
        )

        assertEquals(NetworkCondition.NoInternet, condition)
        assertFalse(probed)
    }

    @Test
    fun `online with empty backend endpoints returns Online`() = runBlocking {
        var probed = false
        val condition = NetworkStatusRepository.resolveNetworkCondition(
            hasInternet = true,
            backendEndpoints = emptyList(),
            shouldCheckBackend = true,
            probeEndpoint = {
                probed = true
                false
            },
        )

        assertEquals(NetworkCondition.Online, condition)
        assertFalse(probed)
    }

    @Test
    fun `online in anonymous local mode returns Online without probing backend`() = runBlocking {
        var probed = false
        val condition = NetworkStatusRepository.resolveNetworkCondition(
            hasInternet = true,
            backendEndpoints = listOf("https://example.supabase.co/rest/v1/"),
            shouldCheckBackend = false,
            probeEndpoint = {
                probed = true
                false
            },
        )

        assertEquals(NetworkCondition.Online, condition)
        assertFalse(probed)
    }

    @Test
    fun `online with configured backend returns Online when any endpoint responds`() = runBlocking {
        val probedUrls = mutableListOf<String>()
        val condition = NetworkStatusRepository.resolveNetworkCondition(
            hasInternet = true,
            backendEndpoints = listOf(
                "https://primary.supabase.co/rest/v1/",
                "https://fallback.supabase.co/rest/v1/",
            ),
            shouldCheckBackend = true,
            probeEndpoint = { url ->
                probedUrls += url
                url.startsWith("https://fallback.")
            },
        )

        assertEquals(NetworkCondition.Online, condition)
        assertEquals(
            listOf(
                "https://primary.supabase.co/rest/v1/",
                "https://fallback.supabase.co/rest/v1/",
            ),
            probedUrls,
        )
    }

    @Test
    fun `online with configured backend returns ServersUnreachable when all endpoints fail`() = runBlocking {
        val probedUrls = mutableListOf<String>()
        val condition = NetworkStatusRepository.resolveNetworkCondition(
            hasInternet = true,
            backendEndpoints = listOf(
                "https://primary.supabase.co/rest/v1/",
                "https://fallback.supabase.co/rest/v1/",
            ),
            shouldCheckBackend = true,
            probeEndpoint = { url ->
                probedUrls += url
                false
            },
        )

        assertEquals(NetworkCondition.ServersUnreachable, condition)
        assertEquals(
            listOf(
                "https://primary.supabase.co/rest/v1/",
                "https://fallback.supabase.co/rest/v1/",
            ),
            probedUrls,
        )
    }
}
