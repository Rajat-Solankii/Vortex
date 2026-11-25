package com.rajatsolankii.vortex.features.vortex

internal actual object VortexPlatformSniffer {
    actual suspend fun sniff(embedUrl: String, timeoutMs: Long): VortexExtractedMedia? = null
}
