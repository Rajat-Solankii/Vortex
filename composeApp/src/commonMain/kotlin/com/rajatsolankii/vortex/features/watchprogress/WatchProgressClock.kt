package com.rajatsolankii.vortex.features.watchprogress

internal expect object WatchProgressClock {
    fun nowEpochMs(): Long
}
