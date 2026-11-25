package com.rajatsolankii.vortex.features.trakt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraktAuthRepositoryTest {

    @Test
    fun `hasRequiredCredentials requires only client id for PKCE`() {
        assertTrue(traktHasRequiredCredentials("client-123"))
        assertFalse(traktHasRequiredCredentials(""))
        assertFalse(traktHasRequiredCredentials("   "))
    }

    @Test
    fun `authorization URL uses auth trakt domain and S256 PKCE parameters`() {
        val url = buildTraktAuthorizationUrl(
            clientId = "client-123",
            redirectUri = "vortex://auth/trakt",
            state = "state-abc",
            codeChallenge = "challenge-xyz",
        )

        assertTrue(url.startsWith("https://auth.trakt.tv/oauth/authorize?"))
        assertTrue("response_type=code" in url)
        assertTrue("client_id=client-123" in url)
        assertTrue("redirect_uri=vortex%3A%2F%2Fauth%2Ftrakt" in url)
        assertTrue("state=state-abc" in url)
        assertTrue("code_challenge=challenge-xyz" in url)
        assertTrue("code_challenge_method=S256" in url)
    }

    @Test
    fun `PKCE token exchange omits client_secret and includes code_verifier`() {
        val body = buildTraktTokenExchangeBody(
            code = "auth-code-1",
            clientId = "client-123",
            clientSecret = "",
            redirectUri = "vortex://auth/trakt",
            codeVerifier = "verifier-123",
        )

        assertFalse("client_secret" in body)
        assertTrue("\"code_verifier\":\"verifier-123\"" in body)
        assertTrue("\"grant_type\":\"authorization_code\"" in body)
    }

    @Test
    fun `refresh and revoke payloads omit client_secret when blank`() {
        val refreshBody = buildTraktRefreshTokenBody(
            refreshToken = "refresh-1",
            clientId = "client-123",
            clientSecret = "",
            redirectUri = "vortex://auth/trakt",
        )
        val revokeBody = buildTraktRevokeBody(
            token = "access-1",
            clientId = "client-123",
            clientSecret = "",
        )

        assertFalse("client_secret" in refreshBody)
        assertTrue("\"grant_type\":\"refresh_token\"" in refreshBody)
        assertFalse("client_secret" in revokeBody)
        assertTrue("\"token\":\"access-1\"" in revokeBody)
    }

    @Test
    fun `HTTP 400 permanently invalidates a rejected refresh token`() {
        assertEquals(
            TraktTokenRefreshResponseAction.INVALIDATE,
            traktTokenRefreshResponseAction(400),
        )
    }

    @Test
    fun `successful token responses are accepted`() {
        assertEquals(
            TraktTokenRefreshResponseAction.ACCEPT,
            traktTokenRefreshResponseAction(200),
        )
        assertEquals(
            TraktTokenRefreshResponseAction.ACCEPT,
            traktTokenRefreshResponseAction(201),
        )
    }

    @Test
    fun `non-400 failures preserve credentials for a later attempt`() {
        listOf(401, 429, 500, 503).forEach { status ->
            assertEquals(
                TraktTokenRefreshResponseAction.TRANSIENT_FAILURE,
                traktTokenRefreshResponseAction(status),
            )
        }
    }
}
