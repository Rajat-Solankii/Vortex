package com.rajatsolankii.vortex

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

internal expect val isIos: Boolean

internal expect val supportsPosterNavigationMotion: Boolean
