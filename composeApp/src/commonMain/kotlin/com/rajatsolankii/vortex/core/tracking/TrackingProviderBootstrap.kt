package com.rajatsolankii.vortex.core.tracking

import com.rajatsolankii.vortex.features.simkl.SimklAuthRepository
import com.rajatsolankii.vortex.features.simkl.SimklMutationRepository
import com.rajatsolankii.vortex.features.simkl.SimklLibraryRepository
import com.rajatsolankii.vortex.features.simkl.SimklProgressRepository
import com.rajatsolankii.vortex.features.simkl.SimklRatings
import com.rajatsolankii.vortex.features.simkl.SimklTrackingLibraryProvider
import com.rajatsolankii.vortex.features.simkl.SimklTrackingProgressProvider
import com.rajatsolankii.vortex.features.simkl.SimklWatchedSyncAdapter
import com.rajatsolankii.vortex.features.simkl.SimklSyncRepository
import com.rajatsolankii.vortex.features.tracking.TrackingProviderRegistry
import com.rajatsolankii.vortex.features.mdblist.MdbListTracker
import com.rajatsolankii.vortex.features.trakt.TraktAuthRepository
import com.rajatsolankii.vortex.features.trakt.TraktScrobbleRepository
import com.rajatsolankii.vortex.features.trakt.TraktTrackingLibraryProvider
import com.rajatsolankii.vortex.features.trakt.TraktTrackingProgressProvider
import com.rajatsolankii.vortex.features.watching.sync.TraktWatchedSyncAdapter

fun ensureTrackingProvidersRegistered() {
    MdbListTracker.register()
    TraktAuthRepository.descriptor
    TraktScrobbleRepository.ensureRegistered()
    SimklAuthRepository.descriptor
    SimklSyncRepository.state
    SimklLibraryRepository.uiState
    SimklProgressRepository.uiState
    SimklMutationRepository.ensureRegistered()
    TrackingProviderRegistry.registerLibraryProvider(TraktTrackingLibraryProvider)
    TrackingProviderRegistry.registerLibraryProvider(SimklTrackingLibraryProvider)
    TrackingProviderRegistry.registerWatchedProvider(TraktWatchedSyncAdapter)
    TrackingProviderRegistry.registerWatchedProvider(SimklWatchedSyncAdapter)
    TrackingProviderRegistry.registerProgressProvider(TraktTrackingProgressProvider)
    TrackingProviderRegistry.registerProgressProvider(SimklTrackingProgressProvider)
    TrackingProviderRegistry.registerRatingProvider(SimklRatings)
}
