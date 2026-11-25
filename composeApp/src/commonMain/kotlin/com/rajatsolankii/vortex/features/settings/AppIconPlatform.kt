package com.rajatsolankii.vortex.features.settings

internal expect object AppIconPlatform {
    val requiresCloseConfirmation: Boolean
    fun loadSelectedIconKey(): String?
    fun saveSelectedIconKey(key: String)
    fun currentIconName(): String?
    suspend fun activateIcon(name: String?): Boolean
}
