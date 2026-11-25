package com.rajatsolankii.vortex.features.player

/**
 * Routes all automatic Android playback (including anime) to the Anilumos-backed
 * Media3 ExoPlayer stack, reserving libmpv for explicit user selection or decoder failover.
 */
internal fun resolveAutoPlaybackEngine(
    playbackEngineSetting: AndroidPlaybackEngine,
    videoId: String?,
    streamFilename: String?,
    streamName: String?,
    streamDescription: String?,
    contentTitle: String?,
    genres: List<String>,
    country: String?,
): AndroidPlaybackEngine? {
    if (playbackEngineSetting != AndroidPlaybackEngine.Auto) return null
    return AndroidPlaybackEngine.ExoPlayer
}
