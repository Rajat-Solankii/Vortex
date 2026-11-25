package com.rajatsolankii.vortex.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStallResumeTest {

    @Test
    fun closedStreamAfterPlaybackKeepsTheStalledPositionForTheNextPrepare() {
        val initialPositionMs = 1_193_023L
        val stalledPositionMs = 1_245_041L
        val session = PlaybackReopenSession(initialPositionMs)

        assertEquals(initialPositionMs, session.prepareStartPositionMs())
        session.onReady()

        val reopened = session.onFailure(
            failure = PlaybackSourceFailure.ClosedStream,
            positionMs = stalledPositionMs,
            durationMs = 2_721_803L,
        )

        assertTrue(reopened)
        assertEquals(stalledPositionMs, session.prepareStartPositionMs())
        assertTrue(session.probeAttempted)

        val adoptedRewoundPosition = session.onFailure(
            failure = PlaybackSourceFailure.ReadPositionOutOfRange,
            positionMs = initialPositionMs,
            durationMs = 2_721_803L,
        )

        assertFalse(adoptedRewoundPosition)
        assertEquals(stalledPositionMs, session.prepareStartPositionMs())
        assertFalse(
            useVodCacheForSource(
                vodCacheEnabled = true,
                youtubeChunked = false,
                adaptive = false,
                loopback = true,
            ),
        )
    }

    @Test
    fun startupFailureDoesNotReplaceTheInitialPosition() {
        val session = PlaybackReopenSession(initialPositionMs = 1_193_023L)

        val reopened = session.onFailure(
            failure = PlaybackSourceFailure.ClosedStream,
            positionMs = 0L,
            durationMs = 0L,
        )

        assertFalse(reopened)
        assertEquals(1_193_023L, session.prepareStartPositionMs())
        assertFalse(session.probeAttempted)
    }

    @Test
    fun startupTimeoutAtZeroPositionWithKnownDurationEscalatesImmediately() {
        val session = PlaybackReopenSession(initialPositionMs = null)

        val reopened = session.onFailure(
            failure = PlaybackSourceFailure.ClosedStream,
            positionMs = 0L,
            durationMs = 10_355_680L,
        )

        assertFalse(reopened)
        assertFalse(session.probeAttempted)
    }

    @Test
    fun repeatedStallWithoutForwardProgressStopsReopeningEvenIfOnReadyFired() {
        val session = PlaybackReopenSession(initialPositionMs = null)
        session.onReady()

        assertTrue(
            session.onFailure(
                failure = PlaybackSourceFailure.ClosedStream,
                positionMs = 3_200L,
                durationMs = 10_355_680L,
            ),
        )

        // Simulate ExoPlayer briefly hitting STATE_READY on a throttled CDN, then stalling again 4.7s later.
        session.onReady()
        assertFalse(
            session.onFailure(
                failure = PlaybackSourceFailure.ClosedStream,
                positionMs = 7_900L,
                durationMs = 10_355_680L,
            ),
        )
    }
}
