package com.example

import com.example.data.model.update.UpdateCheckResult
import com.example.data.update.AppUpdateDownloadManager
import com.example.ui.components.AppUpdateBottomSheet

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.remote.GitHubConfigService
import com.example.data.remote.TelegramService
import com.example.di.AppContainer
import com.example.ui.components.ExpressiveFloatingNavBar
import com.example.ui.components.FpsStatsOverlay
import com.example.ui.screens.blocked.BlockedScreen
import com.example.ui.screens.bookmarks.BookmarksScreen
import com.example.ui.screens.bookmarks.BookmarksViewModel
import com.example.ui.screens.details.DetailsScreen
import com.example.ui.screens.details.DetailsViewModel
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.welcome.WelcomeScreen
import com.example.ui.theme.ExpressiveSprings
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.VortexTheme
import com.example.ui.theme.vortexEnterTransition
import com.example.ui.theme.vortexExitTransition
import com.example.util.AdaptiveRefreshRateManager

sealed interface Screen {
    data object Home : Screen
    data object Discover : Screen
    data object Search : Screen
    data object Bookmarks : Screen
    data object Profile : Screen
    data object History : Screen
    data object Settings : Screen

    data class Details(
        val mediaId: Int,
        val mediaType: MediaType
    ) : Screen
    data class Player(
        val mediaId: Int,
        val mediaType: MediaType,
        val title: String,
        val posterPath: String?,
        val backdropPath: String?,
        val initialSeason: Int = 1,
        val initialEpisode: Int = 1
    ) : Screen
    data class CategoryFeed(val category: com.example.ui.screens.home.HomeCategory) : Screen
    data class ProviderFeed(val providerId: Int, val providerName: String) : Screen
    data class GenreFeed(val genreId: Int, val genreName: String, val isTv: Boolean = false) : Screen
    data object AllProviders : Screen
    data object AllCategories : Screen
}

enum class BottomNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER("Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private lateinit var refreshRateManager: AdaptiveRefreshRateManager
    private val pendingDeepLink = mutableStateOf<Screen?>(null)

    val isInPipMode = mutableStateOf(false)
    var isPlayerActive: Boolean = false
    var activePlayerTitle: String = ""
    private var isAutoPipEnabled: Boolean = true
    private var playerVideoBounds: android.graphics.Rect? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.DeviceConfigHelper.initialize(applicationContext)

        appContainer = AppContainer(applicationContext)

        // Initialize AdaptiveRefreshRateManager with SoundWave hysteresis engine
        refreshRateManager = AdaptiveRefreshRateManager(this)
        lifecycle.addObserver(refreshRateManager)

        // Track Auto PiP preference and update system params
        lifecycleScope.launch {
            appContainer.userPreferencesManager.isAutoPip.collect {
                isAutoPipEnabled = it
                updateAutoPipParams()
            }
        }

        // Listener for seamless PiP mode detection across all Android versions
        addOnPictureInPictureModeChangedListener { info ->
            handlePipModeTransition(info.isInPictureInPictureMode)
        }

        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            val isAmoled by appContainer.userPreferencesManager.isAmoledTheme
                .collectAsStateWithLifecycle(initialValue = false)
            val themeMode by appContainer.userPreferencesManager.themeMode
                .collectAsStateWithLifecycle(initialValue = "Dark")
            val isDynamicColor by appContainer.userPreferencesManager.isDynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            val accentColor by appContainer.userPreferencesManager.accentColor
                .collectAsStateWithLifecycle(initialValue = "Cyber Purple")
            val isInPip by isInPipMode

            VortexTheme(
                isAmoled = isAmoled,
                themeMode = themeMode,
                accentColor = accentColor,
                useDynamicColor = isDynamicColor
            ) {
                VortexApp(
                    appContainer = appContainer,
                    refreshRateManager = refreshRateManager,
                    isInPipMode = isInPip,
                    onEnterPip = { enterPipMode() },
                    onPlayerActiveStateChanged = { active, title ->
                        isPlayerActive = active
                        if (active) {
                            updatePlayerPipParams(title)
                        } else {
                            disableAutoPip()
                        }
                    },
                    onVideoBoundsChanged = { rect ->
                        updatePlayerPipParams(activePlayerTitle, rect)
                    },
                    initialDeepLinkScreen = pendingDeepLink.value,
                    onDeepLinkConsumed = { pendingDeepLink.value = null }
                )
            }
        }
    }

    fun updatePlayerPipParams(title: String, sourceRectHint: android.graphics.Rect? = null) {
        activePlayerTitle = title
        isPlayerActive = true
        if (sourceRectHint != null) {
            playerVideoBounds = sourceRectHint
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                playerVideoBounds?.let { rect ->
                    if (!rect.isEmpty) {
                        builder.setSourceRectHint(rect)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(isAutoPipEnabled)
                    builder.setSeamlessResizeEnabled(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && title.isNotEmpty()) {
                    builder.setTitle(title)
                }
                setPictureInPictureParams(builder.build())
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to update PiP params: ${e.message}")
            }
        }
    }

    private fun updateAutoPipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(isPlayerActive && isAutoPipEnabled)
                    .setSeamlessResizeEnabled(true)
                playerVideoBounds?.let { rect ->
                    if (!rect.isEmpty) {
                        builder.setSourceRectHint(rect)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activePlayerTitle.isNotEmpty()) {
                    builder.setTitle(activePlayerTitle)
                }
                setPictureInPictureParams(builder.build())
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to set auto PiP params: ${e.message}")
            }
        }
    }

    fun disableAutoPip() {
        isPlayerActive = false
        activePlayerTitle = ""
        playerVideoBounds = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(false)
                        .build()
                )
            } catch (_: Exception) {}
        }
    }

    fun enterPipMode() {
        if (!isPlayerActive) return
        isInPipMode.value = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                playerVideoBounds?.let { rect ->
                    if (!rect.isEmpty) {
                        builder.setSourceRectHint(rect)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(isAutoPipEnabled)
                    builder.setSeamlessResizeEnabled(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activePlayerTitle.isNotEmpty()) {
                    builder.setTitle(activePlayerTitle)
                }
                enterPictureInPictureMode(builder.build())
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to enter Picture-in-Picture mode: ${e.message}")
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && isAutoPipEnabled) {
            enterPipMode()
        }
    }

    private fun handlePipModeTransition(isInPip: Boolean) {
        isInPipMode.value = isInPip
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (isInPip) {
                    // CRITICAL FIX FOR SAMSUNG GALAXY & ANDROID PIP:
                    // Once inside PiP, the video view expands to fill the entire PiP window.
                    // Leaving sourceRectHint set to the old portrait bounds causes Samsung One UI
                    // and Android WindowManager to permanently offset the PiP surface by ~200px (leaving
                    // a black gap at the top and clipping the bottom of the video player).
                    // Clearing sourceRectHint to null once in PiP mode ensures full-window rendering!
                    builder.setSourceRectHint(null)
                } else {
                    playerVideoBounds?.let { rect ->
                        if (!rect.isEmpty) {
                            builder.setSourceRectHint(rect)
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(isPlayerActive && isAutoPipEnabled)
                    builder.setSeamlessResizeEnabled(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activePlayerTitle.isNotEmpty()) {
                    builder.setTitle(activePlayerTitle)
                }
                setPictureInPictureParams(builder.build())
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to update PiP params on transition: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        handlePipModeTransition(isInPictureInPictureMode)
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        handlePipModeTransition(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        try {
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()

            // Handle https://www.watchvortex.me/... or https://watchvortex.me/...
            if (scheme == "https" || scheme == "http") {
                if (host == "www.watchvortex.me" || host == "watchvortex.me") {
                    val segments = uri.pathSegments
                    if (segments.size >= 2) {
                        val type = segments[0].lowercase()
                        val id = segments[1].toIntOrNull()
                        if (id != null) {
                            val mediaType = if (type == "tv") MediaType.TV else MediaType.MOVIE
                            pendingDeepLink.value = Screen.Details(id, mediaType)
                            return
                        }
                    }
                }
            } else if (scheme == "vortex") {
                // Handle vortex://movie/123 or vortex://tv/123
                val segments = uri.pathSegments
                val hostOrType = host.orEmpty().lowercase()
                if (hostOrType == "movie" || hostOrType == "tv") {
                    val id = segments.firstOrNull()?.toIntOrNull()
                        ?: uri.getQueryParameter("id")?.toIntOrNull()
                    if (id != null) {
                        val mediaType = if (hostOrType == "tv") MediaType.TV else MediaType.MOVIE
                        pendingDeepLink.value = Screen.Details(id, mediaType)
                        return
                    }
                }
                // Handle vortex://watch?id=123&type=movie
                if (hostOrType == "watch") {
                    val id = uri.getQueryParameter("id")?.toIntOrNull()
                    val type = uri.getQueryParameter("type")?.lowercase()
                    if (id != null) {
                        val mediaType = if (type == "tv") MediaType.TV else MediaType.MOVIE
                        pendingDeepLink.value = Screen.Details(id, mediaType)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to parse deep link: ${e.message}")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VortexApp(
    appContainer: AppContainer,
    refreshRateManager: AdaptiveRefreshRateManager,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    onPlayerActiveStateChanged: (Boolean, String) -> Unit = { _, _ -> },
    onVideoBoundsChanged: (android.graphics.Rect) -> Unit = {},
    initialDeepLinkScreen: Screen? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = appContainer.repository
    val userPreferencesManager = appContainer.userPreferencesManager
    val factory = appContainer.viewModelFactory

    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val detailsViewModel: DetailsViewModel = viewModel(factory = factory)
    val bookmarksViewModel: BookmarksViewModel = viewModel(factory = factory)

    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home
    var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }

    // Notify activity about player active state and title for PiP management
    LaunchedEffect(currentScreen) {
        val player = currentScreen as? Screen.Player
        if (player != null) {
            onPlayerActiveStateChanged(true, player.title)
        } else {
            onPlayerActiveStateChanged(false, "")
        }
        when (currentScreen) {
            is Screen.Home -> selectedTab = BottomNavTab.HOME
            is Screen.Discover -> selectedTab = BottomNavTab.DISCOVER
            is Screen.Search -> selectedTab = BottomNavTab.SEARCH
            is Screen.Profile -> selectedTab = BottomNavTab.PROFILE
            else -> {}
        }
    }

    // User Preferences & Feature Toggles from DataStore
    val isOnboardingCompleted by userPreferencesManager.isOnboardingCompleted
        .collectAsStateWithLifecycle(initialValue = null)
    val isHighRefreshRate by userPreferencesManager.isHighRefreshRate
        .collectAsStateWithLifecycle(initialValue = true)
    val isFpsOverlayEnabled by userPreferencesManager.isFpsOverlayEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val isReducedMotion by userPreferencesManager.isReducedMotion
        .collectAsStateWithLifecycle(initialValue = false)
    val navBarStyle by userPreferencesManager.navBarStyle
        .collectAsStateWithLifecycle(initialValue = "Floating Expressive Pill")

    // Synchronize high refresh rate engine with user preference
    LaunchedEffect(isHighRefreshRate) {
        refreshRateManager.isHighRefreshRateEnabled = isHighRefreshRate
    }

    fun navigateTo(screen: Screen) {
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeLast()
        }
    }

    androidx.activity.compose.BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    var isDeviceBlocked by remember { mutableStateOf(false) }
    val deviceId = remember { TelegramService.getDeviceId(context) }

    // Handle incoming deep link
    LaunchedEffect(initialDeepLinkScreen) {
        initialDeepLinkScreen?.let { deepLink ->
            navigateTo(deepLink)
            onDeepLinkConsumed()
        }
    }

    var appUpdateAvailable by remember { mutableStateOf<UpdateCheckResult.Available?>(null) }
    val updateDownloadManager = remember { AppUpdateDownloadManager(context) }

    // Check device ban list from GitHub, send launch report, and check for updates
    LaunchedEffect(Unit) {
        val blocked = GitHubConfigService.isDeviceBlocked(deviceId)
        if (blocked) {
            isDeviceBlocked = true
        } else {
            // Asynchronously dispatch device launch metrics to Telegram channel
            TelegramService.sendAppLaunchReport(context)
            // Check for in-app updates in background
            try {
                val updateRes = GitHubConfigService.checkReleaseUpdate(context)
                if (updateRes is UpdateCheckResult.Available) {
                    appUpdateAvailable = updateRes
                }
            } catch (_: Exception) {}
        }
    }

    if (isDeviceBlocked) {
        BlockedScreen(deviceId = deviceId)
        return
    }

    // Show Welcome / Onboarding Screen if not completed
    if (isOnboardingCompleted == false) {
        WelcomeScreen(
            onFinishOnboarding = {
                // Completed, state update in DataStore will re-render main app
            },
            preferencesManager = userPreferencesManager
        )
        return
    }

    val showBottomNav = !isInPipMode && (
        currentScreen is Screen.Home ||
        currentScreen is Screen.Discover ||
        currentScreen is Screen.Search ||
        currentScreen is Screen.Profile
    )

    val isClassicNav = navBarStyle == "Classic Full Width"
    val colors = LocalVortexColors.current
    val rootBackground = if (isInPipMode) androidx.compose.ui.graphics.Color.Black else colors.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBackground)
    ) {
        // Main Screen Content with Transitions
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                vortexEnterTransition(isReducedMotion).togetherWith(vortexExitTransition(isReducedMotion))
            },
            label = "ScreenAnimatedContent",
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            when (screen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onMediaClick = { media ->
                            navigateTo(Screen.Details(media.id, media.mediaType))
                        },
                        onWatchHistoryClick = { historyItem ->
                            navigateTo(Screen.Player(
                                mediaId = historyItem.id,
                                mediaType = if (historyItem.mediaType == "tv") MediaType.TV else MediaType.MOVIE,
                                title = historyItem.title,
                                posterPath = historyItem.posterPath,
                                backdropPath = historyItem.backdropPath
                            ))
                        },
                        onCategoryViewAllClick = { category ->
                            navigateTo(Screen.CategoryFeed(category))
                        },
                        onProviderClick = { providerId, providerName ->
                            navigateTo(Screen.ProviderFeed(providerId, providerName))
                        },
                        onAllProvidersClick = {
                            navigateTo(Screen.AllProviders)
                        }
                    )
                }

                is Screen.AllProviders -> {
                    com.example.ui.screens.home.AllProvidersScreen(
                        providers = homeViewModel.feedState.value.providers,
                        onProviderClick = { id, name ->
                            navigateTo(Screen.ProviderFeed(id, name))
                        },
                        onBackClick = { navigateBack() }
                    )
                }

                is Screen.CategoryFeed -> {
                    com.example.ui.screens.home.CategoryPagingScreen(
                        category = screen.category,
                        onBackClick = { navigateBack() },
                        onMediaClick = { media ->
                            navigateTo(Screen.Details(media.id, media.mediaType))
                        },
                        appContainer = appContainer
                    )
                }

                is Screen.ProviderFeed -> {
                    com.example.ui.screens.home.ProviderPagingScreen(
                        providerId = screen.providerId,
                        providerName = screen.providerName,
                        onBackClick = { navigateBack() },
                        onMediaClick = { media ->
                            navigateTo(Screen.Details(media.id, media.mediaType))
                        },
                        appContainer = appContainer
                    )
                }

                is Screen.Search -> {
                    SearchScreen(
                        repository = repository,
                        userPreferencesManager = userPreferencesManager,
                        onMediaClick = { media ->
                            navigateTo(Screen.Details(media.id, media.mediaType))
                        },
                        onBack = {
                            if (backStack.size > 1) {
                                navigateBack()
                            } else {
                                // Search was opened as root destination from bottom navigation and no previous destination exists -> navigate to Home
                                backStack.clear()
                                backStack.add(Screen.Home)
                                selectedTab = BottomNavTab.HOME
                            }
                        }
                    )
                }

                is Screen.History -> {
                    HistoryScreen(
                        repository = repository,
                        onPlayHistoryItem = { historyItem ->
                            navigateTo(Screen.Player(
                                mediaId = historyItem.id,
                                mediaType = if (historyItem.mediaType == "tv") MediaType.TV else MediaType.MOVIE,
                                title = historyItem.title,
                                posterPath = historyItem.posterPath,
                                backdropPath = historyItem.backdropPath,
                                initialSeason = historyItem.seasonNumber ?: 1,
                                initialEpisode = historyItem.episodeNumber ?: 1
                            ))
                        },
                        onBack = {
                            navigateBack()
                        }
                    )
                }

                is Screen.Bookmarks -> {
                    BookmarksScreen(
                        viewModel = bookmarksViewModel,
                        onPlayBookmark = { bookmark ->
                            val mediaType = if (bookmark.mediaType == "tv") MediaType.TV else MediaType.MOVIE
                            navigateTo(Screen.Details(bookmark.mediaId.toInt(), mediaType))
                        },
                        onBack = {
                            navigateBack()
                        }
                    )
                }

                is Screen.Discover -> {
                    com.example.ui.screens.discover.DiscoverScreen(
                        repository = repository,
                        onMediaClick = { media ->
                            navigateTo(Screen.Details(media.id, media.mediaType))
                        },
                        onGenreClick = { genreId, genreName, isTv ->
                            navigateTo(Screen.GenreFeed(genreId, genreName, isTv))
                        },
                        onViewAllCategoriesClick = {
                            navigateTo(Screen.AllCategories)
                        },
                        onCategoryViewAllClick = { category ->
                            navigateTo(Screen.CategoryFeed(category))
                        },
                        onSearchClick = {
                            selectedTab = BottomNavTab.SEARCH
                            navigateTo(Screen.Search)
                        },
                        onProviderClick = { id, name ->
                            navigateTo(Screen.ProviderFeed(id, name))
                        }
                    )
                }

                is Screen.AllCategories -> {
                    com.example.ui.screens.discover.AllCategoriesScreen(
                        onGenreClick = { genreId, genreName, isTv ->
                            navigateTo(Screen.GenreFeed(genreId, genreName, isTv))
                        },
                        onBackClick = {
                            navigateBack()
                        }
                    )
                }

                is Screen.GenreFeed -> {
                    com.example.ui.screens.discover.GenrePagingScreen(
                        genreId = screen.genreId,
                        genreName = screen.genreName,
                        isTv = screen.isTv,
                        repository = repository,
                        onBackClick = { navigateBack() },
                        onMediaClick = { media ->
                            navigateTo(Screen.Details(media.id, media.mediaType))
                        }
                    )
                }

                is Screen.Profile -> {
                    com.example.ui.screens.profile.ProfileScreen(
                        repository = repository,
                        userPreferencesManager = userPreferencesManager,
                        onNavigateToHistory = { navigateTo(Screen.History) },
                        onNavigateToBookmarks = { navigateTo(Screen.Bookmarks) },
                        onNavigateToSettings = { navigateTo(Screen.Settings) }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        repository = repository,
                        userPreferencesManager = userPreferencesManager,
                        onRestartOnboarding = {
                            backStack.clear()
                            backStack.add(Screen.Home)
                        },
                        onBack = {
                            navigateBack()
                        }
                    )
                }

                is Screen.Details -> {
                    DetailsScreen(
                        mediaId = screen.mediaId,
                        mediaType = screen.mediaType,
                        viewModel = detailsViewModel,
                        onBack = {
                            navigateBack()
                        },
                        onPlay = { detail, season, episode ->
                            navigateTo(Screen.Player(
                                mediaId = detail.id,
                                mediaType = detail.mediaType,
                                title = detail.title,
                                posterPath = detail.rawPosterPath,
                                backdropPath = detail.rawBackdropPath,
                                initialSeason = season,
                                initialEpisode = episode
                            ))
                        }
                    )
                }

                is Screen.Player -> {
                    PlayerScreen(
                        mediaId = screen.mediaId,
                        mediaType = screen.mediaType,
                        title = screen.title,
                        posterPath = screen.posterPath,
                        backdropPath = screen.backdropPath,
                        initialSeason = screen.initialSeason,
                        initialEpisode = screen.initialEpisode,
                        repository = repository,
                        userPreferencesManager = userPreferencesManager,
                        isInPipMode = isInPipMode,
                        onEnterPip = onEnterPip,
                        onTitleChanged = { newTitle ->
                            onPlayerActiveStateChanged(true, newTitle)
                        },
                        onVideoBoundsChanged = onVideoBoundsChanged,
                        onBack = {
                            navigateBack()
                        }
                    )
                }
            }
        }

        // Floating / Classic Bottom Navigation Bar Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = showBottomNav,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = ExpressiveSprings.DefaultSpatialDampingRatio,
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness
                )
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 1f,
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness
                )
            ) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExpressiveFloatingNavBar(
                selectedTab = selectedTab,
                isClassic = isClassicNav,
                onTabSelected = { tab ->
                    if (selectedTab != tab) {
                        selectedTab = tab
                        val rootScreen = when (tab) {
                            BottomNavTab.HOME -> Screen.Home
                            BottomNavTab.DISCOVER -> Screen.Discover
                            BottomNavTab.SEARCH -> Screen.Search
                            BottomNavTab.PROFILE -> Screen.Profile
                        }
                        backStack.clear()
                        backStack.add(rootScreen)
                    }
                }
            )
        }

        // Real-Time FPS and Adaptive Refresh Rate HUD Overlay
        if (isFpsOverlayEnabled && !isInPipMode) {
            FpsStatsOverlay()
        }

        // In-App Update Installer Bottom Sheet
        if (appUpdateAvailable != null && !isInPipMode) {
            AppUpdateBottomSheet(
                release = appUpdateAvailable!!.release,
                targetAsset = appUpdateAvailable!!.targetAsset,
                currentVersion = appUpdateAvailable!!.currentVersion,
                downloadManager = updateDownloadManager,
                onDismissRequest = {
                    appUpdateAvailable = null
                }
            )
        }
    }
}
