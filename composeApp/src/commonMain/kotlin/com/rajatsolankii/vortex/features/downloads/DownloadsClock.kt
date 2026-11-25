package com.rajatsolankii.vortex.features.downloads

internal expect object DownloadsClock {
    fun nowEpochMs(): Long
}
