package com.rajatsolankii.vortex.features.library

internal expect object LibraryClock {
    fun nowEpochMs(): Long
}
