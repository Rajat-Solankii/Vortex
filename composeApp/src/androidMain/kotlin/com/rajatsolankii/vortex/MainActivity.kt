package com.rajatsolankii.vortex

import android.content.Intent
import android.content.res.Configuration
import com.rajatsolankii.vortex.features.autosync.AutoSyncPreferencesAndroid
import android.os.Bundle
import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rajatsolankii.vortex.core.auth.AuthStorage
import com.rajatsolankii.vortex.core.network.ServerConfigurationStorage
import com.rajatsolankii.vortex.core.diagnostics.SentryInitializer
import com.rajatsolankii.vortex.core.deeplink.handleAppUrl
import com.rajatsolankii.vortex.core.storage.PlatformLocalAccountDataCleaner
import com.rajatsolankii.vortex.core.sync.SyncClientIdentityStorage
import com.rajatsolankii.vortex.features.addons.AddonHttpClientProvider
import com.rajatsolankii.vortex.features.addons.AddonStorage
import com.rajatsolankii.vortex.features.collection.CollectionMobileSettingsStorage
import com.rajatsolankii.vortex.features.collection.CollectionStorage
import com.rajatsolankii.vortex.features.debrid.DebridSettingsStorage
import com.rajatsolankii.vortex.features.downloads.DownloadsLiveStatusPlatform
import com.rajatsolankii.vortex.features.downloads.DownloadsPlatformDownloader
import com.rajatsolankii.vortex.features.downloads.DownloadsStorage
import com.rajatsolankii.vortex.features.library.LibraryDisplaySettingsStorage
import com.rajatsolankii.vortex.features.membership.MemberAssetStorage
import com.rajatsolankii.vortex.features.library.LibraryStorage
import com.rajatsolankii.vortex.features.details.MetaScreenSettingsStorage
import com.rajatsolankii.vortex.features.home.HomeCatalogSettingsStorage
import com.rajatsolankii.vortex.features.mdblist.MdbListSettingsStorage
import com.rajatsolankii.vortex.features.notifications.EpisodeReleaseNotificationPlatform
import com.rajatsolankii.vortex.features.notifications.EpisodeReleaseNotificationsStorage
import com.rajatsolankii.vortex.features.player.PlayerSettingsStorage
import com.rajatsolankii.vortex.features.player.PlayerTrackPreferenceStorage
import com.rajatsolankii.vortex.features.player.ExternalPlayerPlatform
import com.rajatsolankii.vortex.features.player.SubtitleFileCache
import com.rajatsolankii.vortex.features.player.PlayerPictureInPictureManager
import com.rajatsolankii.vortex.features.player.PipRemoteActionReceiver
import com.rajatsolankii.vortex.features.p2p.P2pSettingsStorage
import com.rajatsolankii.vortex.features.p2p.P2pStreamingEngine
import com.rajatsolankii.vortex.features.plugins.PluginStorage
import com.rajatsolankii.vortex.features.profiles.AvatarStorage
import com.rajatsolankii.vortex.features.profiles.ProfilePinCacheStorage
import com.rajatsolankii.vortex.features.profiles.ProfileStorage
import com.rajatsolankii.vortex.features.details.SeasonViewModeStorage
import com.rajatsolankii.vortex.features.search.DiscoverSelectionStorage
import com.rajatsolankii.vortex.features.search.SearchHistoryStorage
import com.rajatsolankii.vortex.features.settings.SentrySettingsStorage
import com.rajatsolankii.vortex.features.settings.AppIconPlatform
import com.rajatsolankii.vortex.features.settings.ThemeSettingsStorage
import com.rajatsolankii.vortex.features.trakt.TraktAuthStorage
import com.rajatsolankii.vortex.features.trakt.TraktCommentsStorage
import com.rajatsolankii.vortex.features.trakt.TraktLibraryStorage
import com.rajatsolankii.vortex.features.trakt.TraktSettingsStorage
import com.rajatsolankii.vortex.features.mdblist.PlatformMdbListAuthPersistence
import com.rajatsolankii.vortex.features.mdblist.PlatformMdbListSyncStorage
import com.rajatsolankii.vortex.features.servers.ServerStorage
import com.rajatsolankii.vortex.features.simkl.SimklAuthStorage
import com.rajatsolankii.vortex.features.simkl.SimklSyncStorage
import com.rajatsolankii.vortex.features.tmdb.TmdbSettingsStorage
import com.rajatsolankii.vortex.features.updater.AndroidAppUpdaterPlatform
import com.rajatsolankii.vortex.core.ui.CardDepthStyleStorage
import com.rajatsolankii.vortex.core.ui.PosterCardStyleStorage
import com.rajatsolankii.vortex.core.poster.CustomPosterUrlStorage
import com.rajatsolankii.vortex.features.watched.WatchedStorage
import com.rajatsolankii.vortex.features.streams.StreamLinkCacheStorage
import com.rajatsolankii.vortex.features.streams.StreamBadgeSettingsStorage
import com.rajatsolankii.vortex.features.streams.BingeGroupCacheStorage
import com.rajatsolankii.vortex.features.watchprogress.ContinueWatchingEnrichmentStorage
import com.rajatsolankii.vortex.features.watchprogress.ContinueWatchingPreferencesStorage
import com.rajatsolankii.vortex.features.watchprogress.ResumePromptStorage
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressStorage

open class MainActivity : AppCompatActivity() {
    private var pipRemoteActionReceiver: PipRemoteActionReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(
                scrim = 0xFF020404.toInt(),
            ),
        )
        ThemeSettingsStorage.initialize(applicationContext)
        AppIconPlatform.initialize(applicationContext)
        SentrySettingsStorage.initialize(applicationContext)
        SentryInitializer.start(application)
        super.onCreate(savedInstanceState)
        AutoSyncPreferencesAndroid.initialize(applicationContext) // AutoSync hook
        window.setBackgroundDrawableResource(R.color.nuvio_background)
        pipRemoteActionReceiver = PipRemoteActionReceiver.register(this)
        SyncClientIdentityStorage.initialize(applicationContext)
        AddonHttpClientProvider.initialize(applicationContext)
        AddonStorage.initialize(applicationContext)
        AuthStorage.initialize(applicationContext)
        ServerConfigurationStorage.initialize(applicationContext)
        LibraryStorage.initialize(applicationContext)
        WatchedStorage.initialize(applicationContext)
        MetaScreenSettingsStorage.initialize(applicationContext)
        com.rajatsolankii.vortex.features.shuffle.EpisodeShuffleStorage.initialize(applicationContext)
        HomeCatalogSettingsStorage.initialize(applicationContext)
        PlayerSettingsStorage.initialize(applicationContext)
        com.rajatsolankii.vortex.features.vortex.VortexPlatformSniffer.initialize(applicationContext)
        PlayerTrackPreferenceStorage.initialize(applicationContext)
        P2pSettingsStorage.initialize(applicationContext)
        P2pStreamingEngine.initialize(applicationContext)
        ExternalPlayerPlatform.initialize(applicationContext)
        SubtitleFileCache.initialize(applicationContext)
        ProfileStorage.initialize(applicationContext)
        AvatarStorage.initialize(applicationContext)
        ProfilePinCacheStorage.initialize(applicationContext)
        MemberAssetStorage.initialize(applicationContext)
        DiscoverSelectionStorage.initialize(applicationContext)
        SearchHistoryStorage.initialize(applicationContext)
        SeasonViewModeStorage.initialize(applicationContext)
        PosterCardStyleStorage.initialize(applicationContext)
        CustomPosterUrlStorage.initialize(applicationContext)
        CardDepthStyleStorage.initialize(applicationContext)
        DebridSettingsStorage.initialize(applicationContext)
        TmdbSettingsStorage.initialize(applicationContext)
        MdbListSettingsStorage.initialize(applicationContext)
        TraktAuthStorage.initialize(applicationContext)
        TraktCommentsStorage.initialize(applicationContext)
        TraktLibraryStorage.initialize(applicationContext)
        TraktSettingsStorage.initialize(applicationContext)
        PlatformMdbListAuthPersistence.initialize(applicationContext)
        ServerStorage.initialize(applicationContext)
        PlatformMdbListSyncStorage.initialize(applicationContext)
        SimklAuthStorage.initialize(applicationContext)
        SimklSyncStorage.initialize(applicationContext)
        LibraryDisplaySettingsStorage.initialize(applicationContext)
        ContinueWatchingPreferencesStorage.initialize(applicationContext)
        ResumePromptStorage.initialize(applicationContext)
        ContinueWatchingEnrichmentStorage.initialize(applicationContext)
        EpisodeReleaseNotificationsStorage.initialize(applicationContext)
        WatchProgressStorage.initialize(applicationContext)
        StreamLinkCacheStorage.initialize(applicationContext)
        StreamBadgeSettingsStorage.initialize(applicationContext)
        BingeGroupCacheStorage.initialize(applicationContext)
        PluginStorage.initialize(applicationContext)
        CollectionMobileSettingsStorage.initialize(applicationContext)
        CollectionStorage.initialize(applicationContext)
        DownloadsStorage.initialize(applicationContext)
        DownloadsPlatformDownloader.initialize(applicationContext)
        DownloadsLiveStatusPlatform.initialize(applicationContext)
        AndroidAppUpdaterPlatform.initialize(applicationContext)
        PlatformLocalAccountDataCleaner.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.bindActivity(this)
        handleIncomingAppIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAppIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlayerPictureInPictureManager.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerPictureInPictureManager.onPictureInPictureModeChanged(this, isInPictureInPictureMode)
    }

    override fun onDestroy() {
        EpisodeReleaseNotificationPlatform.unbindActivity(this)
        val receiver = pipRemoteActionReceiver
        if (receiver != null) {
            runCatching { unregisterReceiver(receiver) }
            pipRemoteActionReceiver = null
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (EpisodeReleaseNotificationPlatform.handlePermissionRequestResult(requestCode, grantResults)) {
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun handleIncomingAppIntent(intent: Intent?) {
        val appUrl = (intent?.dataString ?: intent?.getStringExtra(LinkActivity.EXTRA_LINK))?.trim().orEmpty()
        if (appUrl.isBlank()) return
        handleAppUrl(appUrl)
    }
}
