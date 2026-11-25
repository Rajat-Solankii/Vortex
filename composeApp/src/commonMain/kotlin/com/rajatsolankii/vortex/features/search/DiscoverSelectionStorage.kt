package com.rajatsolankii.vortex.features.search

internal expect object DiscoverSelectionStorage {
    fun loadCatalogKey(): String?
    fun saveCatalogKey(catalogKey: String)
}
