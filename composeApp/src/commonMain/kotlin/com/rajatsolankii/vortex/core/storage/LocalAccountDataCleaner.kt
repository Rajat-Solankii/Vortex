package com.rajatsolankii.vortex.core.storage

import com.rajatsolankii.vortex.core.build.AppFeaturePolicy
import com.rajatsolankii.vortex.core.sync.SyncManager
import com.rajatsolankii.vortex.core.sync.ProfileSettingsSync
import com.rajatsolankii.vortex.core.tracking.ensureTrackingProvidersRegistered
import com.rajatsolankii.vortex.features.addons.AddonRepository
import com.rajatsolankii.vortex.features.catalog.CatalogRepository
import com.rajatsolankii.vortex.features.collection.CollectionMobileSettingsRepository
import com.rajatsolankii.vortex.features.collection.CollectionRepository
import com.rajatsolankii.vortex.features.details.MetaDetailsRepository
import com.rajatsolankii.vortex.features.details.MetaScreenSettingsRepository
import com.rajatsolankii.vortex.features.home.HomeCatalogSettingsRepository
import com.rajatsolankii.vortex.features.home.HomeRepository
import com.rajatsolankii.vortex.features.library.LibraryRepository
import com.rajatsolankii.vortex.features.membership.MemberAccessRepository
import com.rajatsolankii.vortex.features.library.LibraryDisplaySettingsRepository
import com.rajatsolankii.vortex.features.notifications.EpisodeReleaseNotificationsRepository
import com.rajatsolankii.vortex.features.player.PlayerLaunchStore
import com.rajatsolankii.vortex.features.player.PlayerSettingsRepository
import com.rajatsolankii.vortex.features.p2p.P2pSettingsRepository
import com.rajatsolankii.vortex.features.plugins.PluginRepository
import com.rajatsolankii.vortex.features.player.SubtitleRepository
import com.rajatsolankii.vortex.features.profiles.ProfileRepository
import com.rajatsolankii.vortex.features.profiles.MAX_PROFILES
import com.rajatsolankii.vortex.features.search.SearchRepository
import com.rajatsolankii.vortex.features.servers.ServerRepository
import com.rajatsolankii.vortex.features.settings.ThemeSettingsRepository
import com.rajatsolankii.vortex.features.streams.StreamContextStore
import com.rajatsolankii.vortex.features.streams.StreamBadgeSettingsRepository
import com.rajatsolankii.vortex.features.streams.StreamLaunchStore
import com.rajatsolankii.vortex.features.streams.StreamsRepository
import com.rajatsolankii.vortex.features.tracking.TrackingProviderRegistry
import com.rajatsolankii.vortex.features.tracking.TrackingSettingsRepository
import com.rajatsolankii.vortex.core.ui.CardDepthStyleRepository
import com.rajatsolankii.vortex.core.ui.PosterCardStyleRepository
import com.rajatsolankii.vortex.features.watchprogress.ContinueWatchingPreferencesRepository
import com.rajatsolankii.vortex.features.watchprogress.ContinueWatchingEnrichmentCache
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressRepository
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressSourceCoordinator
import com.rajatsolankii.vortex.features.watched.WatchedRepository

internal object LocalAccountDataCleaner {
    fun wipe() {
        ensureTrackingProvidersRegistered()
        TrackingProviderRegistry.removeStoredProfiles(1..MAX_PROFILES)
        SyncManager.cancelAccountSync()
        WatchProgressSourceCoordinator.clearLocalState()
        ProfileSettingsSync.clearAccountState()
        ContinueWatchingEnrichmentCache.clearLocalState()
        WatchProgressRepository.clearLocalState()
        WatchedRepository.clearLocalState()
        LibraryRepository.runAccountStorageWipe {
            wipePlatformStorage()
        }

        ProfileRepository.clearInMemory()
        MemberAccessRepository.clearLocalState()
        AddonRepository.clearLocalState()
        ServerRepository.clearLocalState()
        if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.clearLocalState()
        }
        HomeRepository.clear()
        HomeCatalogSettingsRepository.clearLocalState()
        MetaScreenSettingsRepository.clearLocalState()
        com.rajatsolankii.vortex.features.shuffle.EpisodeShuffleRepository.clearLocalState()
        LibraryRepository.clearLocalState()
        LibraryDisplaySettingsRepository.clearLocalState()
        ContinueWatchingPreferencesRepository.clearLocalState()
        EpisodeReleaseNotificationsRepository.clearLocalState()
        CollectionMobileSettingsRepository.clearLocalState()
        CollectionRepository.clearLocalState()
        ThemeSettingsRepository.clearLocalState()
        PosterCardStyleRepository.clearLocalState()
        CardDepthStyleRepository.clearLocalState()
        TrackingProviderRegistry.clearLocalState()
        TrackingSettingsRepository.clearLocalState()
        PlayerSettingsRepository.clearLocalState()
        StreamBadgeSettingsRepository.clearLocalState()
        P2pSettingsRepository.clearLocalState()
        CatalogRepository.clear()
        StreamsRepository.clear()
        MetaDetailsRepository.clear()
        SearchRepository.reset()
        SubtitleRepository.clear()
        PlayerLaunchStore.clear()
        StreamLaunchStore.clear()
        StreamContextStore.clear()
    }

    internal fun wipePlatformStorage(wipeStorage: () -> Unit = PlatformLocalAccountDataCleaner::wipe) {
        try {
            wipeStorage()
        } finally {
            ContinueWatchingEnrichmentCache.clearLocalState()
        }
    }
}

internal expect object PlatformLocalAccountDataCleaner {
    fun wipe()
}
