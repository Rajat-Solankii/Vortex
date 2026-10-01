package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.remote.GitHubConfigService
import com.example.data.remote.TelegramService
import com.example.di.AppContainer
import com.example.ui.screens.blocked.BlockedScreen
import com.example.ui.screens.details.DetailsScreen
import com.example.ui.screens.details.DetailsViewModel
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkSurface
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleDeep
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTheme
import com.example.util.HighRefreshRateHelper
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
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
        val backdropPath: String?
    ) : Screen
}

enum class BottomNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History)
}

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)

        // Enable 120 FPS / High Refresh Rate display mode if supported by device
        HighRefreshRateHelper.enableHighRefreshRate(this)

        enableEdgeToEdge()
        setContent {
            VortexTheme {
                VortexApp(appContainer = appContainer)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        HighRefreshRateHelper.enableHighRefreshRate(this)
    }
}

@Composable
fun VortexApp(appContainer: AppContainer) {
    val context = LocalContext.current
    val repository = appContainer.repository
    val factory = appContainer.viewModelFactory

    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val detailsViewModel: DetailsViewModel = viewModel(factory = factory)

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
    var isDeviceBlocked by remember { mutableStateOf(false) }
    val deviceId = remember { TelegramService.getDeviceId(context) }

    // Check device ban list from GitHub and send launch report via Telegram Monitor Bot
    LaunchedEffect(Unit) {
        val blocked = GitHubConfigService.isDeviceBlocked(deviceId)
        if (blocked) {
            isDeviceBlocked = true
        } else {
            // Asynchronously dispatch device launch metrics to Telegram channel
            TelegramService.sendAppLaunchReport(context)
        }
    }

    if (isDeviceBlocked) {
        BlockedScreen(deviceId = deviceId)
        return
    }

    val showBottomNav = currentScreen is Screen.Home ||
            currentScreen is Screen.Search ||
            currentScreen is Screen.History

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VortexDarkBackground,
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = VortexDarkSurface,
                    contentColor = VortexTextPrimary
                ) {
                    BottomNavTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedTab = tab
                                currentScreen = when (tab) {
                                    BottomNavTab.HOME -> Screen.Home
                                    BottomNavTab.SEARCH -> Screen.Search
                                    BottomNavTab.HISTORY -> Screen.History
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = VortexPurple,
                                indicatorColor = VortexPurpleDeep,
                                unselectedIconColor = VortexTextMuted,
                                unselectedTextColor = VortexTextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(VortexDarkBackground)
        ) {
            when (val screen = currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onMediaClick = { media ->
                            currentScreen = Screen.Details(media.id, media.mediaType)
                        },
                        onWatchHistoryClick = { historyItem ->
                            currentScreen = Screen.Player(
                                mediaId = historyItem.id,
                                mediaType = if (historyItem.mediaType == "tv") MediaType.TV else MediaType.MOVIE,
                                title = historyItem.title,
                                posterPath = historyItem.posterPath,
                                backdropPath = historyItem.backdropPath
                            )
                        },
                        onSearchClick = {
                            selectedTab = BottomNavTab.SEARCH
                            currentScreen = Screen.Search
                        },
                        onHistoryScreenClick = {
                            selectedTab = BottomNavTab.HISTORY
                            currentScreen = Screen.History
                        },
                        onSettingsClick = {
                            currentScreen = Screen.Settings
                        }
                    )
                }

                is Screen.Search -> {
                    SearchScreen(
                        repository = repository,
                        onMediaClick = { media ->
                            currentScreen = Screen.Details(media.id, media.mediaType)
                        },
                        onBack = {
                            selectedTab = BottomNavTab.HOME
                            currentScreen = Screen.Home
                        }
                    )
                }

                is Screen.History -> {
                    HistoryScreen(
                        repository = repository,
                        onPlayHistoryItem = { historyItem ->
                            currentScreen = Screen.Player(
                                mediaId = historyItem.id,
                                mediaType = if (historyItem.mediaType == "tv") MediaType.TV else MediaType.MOVIE,
                                title = historyItem.title,
                                posterPath = historyItem.posterPath,
                                backdropPath = historyItem.backdropPath
                            )
                        },
                        onBack = {
                            selectedTab = BottomNavTab.HOME
                            currentScreen = Screen.Home
                        }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        onBack = {
                            currentScreen = when (selectedTab) {
                                BottomNavTab.HOME -> Screen.Home
                                BottomNavTab.SEARCH -> Screen.Search
                                BottomNavTab.HISTORY -> Screen.History
                            }
                        }
                    )
                }

                is Screen.Details -> {
                    DetailsScreen(
                        mediaId = screen.mediaId,
                        mediaType = screen.mediaType,
                        viewModel = detailsViewModel,
                        onBack = {
                            currentScreen = when (selectedTab) {
                                BottomNavTab.HOME -> Screen.Home
                                BottomNavTab.SEARCH -> Screen.Search
                                BottomNavTab.HISTORY -> Screen.History
                            }
                        },
                        onPlay = { detail ->
                            currentScreen = Screen.Player(
                                mediaId = detail.id,
                                mediaType = detail.mediaType,
                                title = detail.title,
                                posterPath = detail.rawPosterPath,
                                backdropPath = detail.rawBackdropPath
                            )
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
                        repository = repository,
                        onBack = {
                            currentScreen = Screen.Details(
                                mediaId = screen.mediaId,
                                mediaType = screen.mediaType
                            )
                        }
                    )
                }
            }
        }
    }
}
