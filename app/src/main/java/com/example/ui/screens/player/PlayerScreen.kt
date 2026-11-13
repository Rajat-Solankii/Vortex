package com.example.ui.screens.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.ui.components.CastToTvBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.model.EpisodeItem
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SeasonItem
import com.example.data.repository.VortexRepository
import com.example.ui.components.MediaCarousel
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexDarkSurfaceVariant
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserPreferencesManager
import kotlinx.coroutines.launch

private fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(
    mediaId: Int,
    mediaType: MediaType,
    title: String,
    posterPath: String?,
    backdropPath: String?,
    initialSeason: Int = 1,
    initialEpisode: Int = 1,
    repository: VortexRepository,
    userPreferencesManager: UserPreferencesManager? = null,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onVideoBoundsChanged: (android.graphics.Rect) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context.findActivity()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current

    var isPipActive by remember { mutableStateOf(isInPipMode || (activity?.isInPictureInPictureMode == true)) }

    // Synchronize isPipActive with incoming parameter
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            isPipActive = true
        } else if (activity?.isInPictureInPictureMode != true) {
            isPipActive = false
        }
    }

    // Direct listener on ComponentActivity for instantaneous PiP state transitions
    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isPipActive = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    // Determine if effectively in PiP using all authoritative system signals:
    // 1. isPipActive (instantaneous local state)
    // 2. isInPipMode parameter from MainActivity
    // 3. Direct Activity.isInPictureInPictureMode
    // 4. Compact window dimensions check (PiP is always < 300dp height and < 450dp width)
    val isEffectivelyInPip = isPipActive ||
        isInPipMode ||
        (activity?.isInPictureInPictureMode == true) ||
        (configuration.screenHeightDp < 300 && configuration.screenWidthDp < 450)

    // Reactive current playing media state (allowing instant in-player recommendation shifts)
    var currentMediaId by remember(mediaId) { mutableIntStateOf(mediaId) }
    var currentMediaType by remember(mediaType) { mutableStateOf(mediaType) }
    var currentTitle by remember(title) { mutableStateOf(title) }
    var currentPoster by remember(posterPath) { mutableStateOf(posterPath) }
    var currentBackdrop by remember(backdropPath) { mutableStateOf(backdropPath) }
    var currentEmbedUrl by remember { mutableStateOf("") }
    var showCastDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentTitle) {
        onTitleChanged(currentTitle)
    }

    val defaultServerPref by (userPreferencesManager?.defaultServer?.collectAsStateWithLifecycle(initialValue = "Server 1")
        ?: remember { mutableStateOf("Server 1") })

    val initialServer = remember(defaultServerPref) {
        when {
            defaultServerPref.startsWith("Server 2") -> StreamServer.SERVER2
            defaultServerPref.startsWith("Server 3") -> StreamServer.SERVER3
            defaultServerPref.startsWith("Server 4") -> StreamServer.SERVER4
            defaultServerPref.startsWith("Server 5") -> StreamServer.SERVER5
            defaultServerPref.startsWith("Server 6") -> StreamServer.SERVER6
            defaultServerPref.startsWith("Server 7") -> StreamServer.SERVER7
            else -> StreamServer.SERVER1
        }
    }

    var selectedServer by remember(initialServer) { mutableStateOf(initialServer) }
    var selectedSeason by remember(initialSeason) { mutableIntStateOf(initialSeason) }
    var selectedEpisode by remember(initialEpisode) { mutableIntStateOf(initialEpisode) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var blockedAdsCount by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Recommendations state based on the current playing movie/show
    var recommendations by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoadingRecommendations by remember { mutableStateOf(false) }

    // Real TV Season and Episode data fetched from TMDB
    var tvSeasons by remember { mutableStateOf<List<SeasonItem>>(emptyList()) }
    var tvEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    // Fetch TV seasons when media opens
    LaunchedEffect(currentMediaId, currentMediaType) {
        if (currentMediaType == MediaType.TV) {
            val detailResult = repository.getTvDetails(currentMediaId)
            detailResult.onSuccess { detail ->
                if (detail.seasons.isNotEmpty()) {
                    tvSeasons = detail.seasons
                }
            }
        }
    }

    // Fetch TV episodes for currently selected season
    LaunchedEffect(currentMediaId, currentMediaType, selectedSeason) {
        if (currentMediaType == MediaType.TV) {
            isLoadingEpisodes = true
            val epResult = repository.getSeasonEpisodes(currentMediaId, selectedSeason)
            tvEpisodes = epResult.getOrDefault(emptyList())
            isLoadingEpisodes = false
        }
    }

    // Synchronize landscape orientation and immersive system bars with isFullscreen state
    LaunchedEffect(isFullscreen) {
        val activity = context.findActivity()
        val window = activity?.window
        if (activity != null) {
            activity.requestedOrientation = if (isFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Wake-Lock: Keep screen on while playing video
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Picture-in-Picture mode handler
    fun enterPip() {
        // Trigger PiP through onEnterPip() which calls enterPictureInPictureMode().
        // Do not force isPipActive = true prematurely so the system smoothly transitions
        // from the existing player bounds without offset artifacts.
        onEnterPip()
    }

    var savedPositionMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(currentMediaId) {
        val history = repository.getWatchHistoryById(currentMediaId)
        if (history != null && history.lastPositionMs > 5000L && (history.durationMs == 0L || history.lastPositionMs < history.durationMs * 0.90f)) {
            savedPositionMs = history.lastPositionMs
        }
    }

    // Restore portrait/default orientation on exit
    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            val window = activity?.window
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, it.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Save to Watch History when player opens or updates
    LaunchedEffect(currentMediaId, currentTitle, selectedSeason, selectedEpisode) {
        repository.saveWatchProgress(
            id = currentMediaId,
            title = currentTitle,
            posterPath = currentPoster,
            backdropPath = currentBackdrop,
            mediaType = currentMediaType,
            positionMs = 120000L,
            durationMs = 7200000L,
            season = if (currentMediaType == MediaType.TV) selectedSeason else null,
            episode = if (currentMediaType == MediaType.TV) selectedEpisode else null
        )
    }

    // Fetch recommendations based on current playing movie/show
    LaunchedEffect(currentMediaId, currentMediaType) {
        isLoadingRecommendations = true
        val recResult = repository.getRecommendations(currentMediaId, currentMediaType)
        recommendations = recResult.getOrDefault(emptyList())
        isLoadingRecommendations = false
        
        if (currentMediaType == MediaType.TV) {
            val seasonsResult = repository.getTvSeasons(currentMediaId)
            val dtos = seasonsResult.getOrDefault(emptyList())
            tvSeasons = dtos.map { dto ->
                SeasonItem(
                    id = dto.id,
                    name = dto.name ?: "Season ${dto.seasonNumber}",
                    seasonNumber = dto.seasonNumber,
                    episodeCount = dto.episodeCount ?: 0,
                    posterUrl = if (!dto.posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getPosterQuality()}${dto.posterPath}" else ""
                )
            }
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isEffectivelyInPip) Color.Black else VortexDarkBackground)
    ) {
        // Top Player Bar (Hidden in Fullscreen Landscape and PiP mode)
        if (!isFullscreen && !isEffectivelyInPip) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = VortexTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${selectedServer.displayName} • ${if (currentMediaType == MediaType.TV) "S$selectedSeason:E$selectedEpisode" else "Movie"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = VortexPurpleLight,
                            fontSize = 11.sp
                        )
                    )
                }

                // Cast Screen / Smart View / Direct TV Cast Button
                IconButton(
                    onClick = { showCastDialog = true },
                    modifier = Modifier.testTag("top_cast_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Cast to TV",
                        tint = Color.White
                    )
                }

                // Picture-in-Picture Button
                IconButton(
                    onClick = { enterPip() },
                    modifier = Modifier.testTag("top_pip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Picture-in-Picture",
                        tint = Color.White
                    )
                }

                // Fullscreen Landscape Toggle Button
                IconButton(
                    onClick = { isFullscreen = true },
                    modifier = Modifier.testTag("top_fullscreen_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Landscape Fullscreen",
                        tint = Color.White
                    )
                }

                // Reload Current Server
                IconButton(
                    onClick = { reloadKey++ },
                    modifier = Modifier.testTag("reload_stream_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Server",
                        tint = Color.White
                    )
                }
            }
        }

        // The Video Viewport:
        // In PiP Mode or Fullscreen Landscape -> fills 100% of the display container with black background.
        // In Normal Portrait -> standard 16:9 aspect ratio box.
        val viewportModifier = if (isEffectivelyInPip || isFullscreen) {
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        }

        Box(
            modifier = viewportModifier.onGloballyPositioned { coordinates ->
                if (!isEffectivelyInPip) {
                    val bounds = coordinates.boundsInWindow()
                    onVideoBoundsChanged(
                        android.graphics.Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                    )
                }
            }
        ) {
            val embedUrl = remember(selectedServer, currentMediaId, currentMediaType, selectedSeason, selectedEpisode) {
                StreamUrlBuilder.buildWebEmbedUrl(
                    server = selectedServer,
                    mediaId = currentMediaId,
                    mediaType = currentMediaType,
                    season = selectedSeason,
                    episode = selectedEpisode
                )
            }
            LaunchedEffect(embedUrl) {
                currentEmbedUrl = embedUrl
            }

            key(currentMediaId, selectedServer, selectedSeason, selectedEpisode, reloadKey) {
                WebStreamPlayer(
                    embedUrl = embedUrl,
                    server = selectedServer,
                    isInPipMode = isEffectivelyInPip,
                    onToggleFullscreen = { shouldBeFullscreen ->
                        if (!isEffectivelyInPip) {
                            isFullscreen = shouldBeFullscreen
                        }
                    },
                    onRedirectBlocked = {
                        blockedAdsCount++
                    }
                )
            }

            // Floating Fullscreen / Landscape Toggle Button & Fullscreen Cast Button (Hidden in PiP mode)
            if (!isEffectivelyInPip) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFullscreen) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(
                                onClick = { showCastDialog = true },
                                modifier = Modifier.testTag("fullscreen_cast_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = "Cast to TV",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier.testTag("viewport_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen Landscape",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Server and Source Controls & Recommendations (Hidden in Landscape Fullscreen mode and Picture-in-Picture mode)
        if (!isFullscreen && !isEffectivelyInPip) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                // Ad Blocker Shield Badge
                Surface(
                    color = Color(0xFF0F2618),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E6334)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vortex Shield Active",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784)
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (blockedAdsCount > 0) "$blockedAdsCount Popups Blocked" else "Protected from Redirects",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFA5D6A7),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Text(
                    text = "SELECT SERVER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = VortexTextMuted
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // The 3 Streaming Servers: Server 1 (vaplayer), Server 2 (videasy), Server 3 (nhdapi)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreamServer.values().forEach { server ->
                        val isSelected = selectedServer == server
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (selectedServer != server) {
                                    selectedServer = server
                                    reloadKey++
                                }
                            },
                            label = {
                                Text(
                                    text = if (server.isRecommendedForAnime) "${server.displayName} (Anime Recommended)" else server.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else VortexTextSecondary
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VortexPurple,
                                containerColor = VortexDarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("server_chip_${server.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Watch on TV Button / Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VortexPurple.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, VortexPurple.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCastDialog = true }
                        .testTag("watch_on_tv_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(VortexPurple.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Cast to TV",
                                tint = VortexPurpleLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Watch on TV",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Cast to Smart TV, Chromecast, Roku, or Fire TV",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = VortexTextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = VortexPurpleLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TV Season and Episode Selector if TV show
                if (currentMediaType == MediaType.TV) {
                    val seasonsList = if (tvSeasons.isNotEmpty()) {
                        tvSeasons
                    } else {
                        (1..4).map { SeasonItem(it, "Season $it", it, 10, "") }
                    }

                    val episodesList = if (tvEpisodes.isNotEmpty()) {
                        tvEpisodes
                    } else {
                        (1..10).map { EpisodeItem(it, "Episode $it", "", it, selectedSeason, "", 8.0, "") }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "SEASONS & EPISODES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = VortexTextMuted
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        val nextEp = episodesList.firstOrNull { it.episodeNumber == selectedEpisode + 1 }
                        if (nextEp != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VortexPurple.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, VortexPurple.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    selectedEpisode = nextEp.episodeNumber
                                    reloadKey++
                                }
                            ) {
                                Text(
                                    text = "Next: Ep ${nextEp.episodeNumber} ›",
                                    color = VortexPurpleLight,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal Season Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seasonsList.forEach { season ->
                            val isSelected = selectedSeason == season.seasonNumber
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) VortexPurple else VortexDarkCard,
                                border = if (!isSelected) BorderStroke(1.dp, VortexDarkCardBorder) else null,
                                modifier = Modifier.clickable {
                                    if (selectedSeason != season.seasonNumber) {
                                        selectedSeason = season.seasonNumber
                                        selectedEpisode = 1
                                        reloadKey++
                                    }
                                }
                            ) {
                                Text(
                                    text = season.name,
                                    color = if (isSelected) Color.White else VortexTextSecondary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Episode Chips with real episode numbers
                    if (isLoadingEpisodes) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VortexPurple, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            episodesList.forEach { ep ->
                                val isSelected = selectedEpisode == ep.episodeNumber
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) VortexPurple else VortexDarkCard,
                                    border = if (!isSelected) BorderStroke(1.dp, VortexDarkCardBorder) else null,
                                    modifier = Modifier.clickable {
                                        if (selectedEpisode != ep.episodeNumber) {
                                            selectedEpisode = ep.episodeNumber
                                            reloadKey++
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Ep ${ep.episodeNumber}",
                                        color = if (isSelected) Color.White else VortexTextSecondary,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Current Server Details Card
                Surface(
                    color = VortexDarkCard,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VortexDarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Server info",
                            tint = VortexPurpleLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedServer.displayName} (Active)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = VortexTextPrimary
                                )
                            )
                            Text(
                                text = selectedServer.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = VortexTextSecondary
                                )
                            )
                        }
                        IconButton(onClick = { reloadKey++ }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = VortexPurpleLight
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // RECOMMENDATIONS SECTION: Based on current playing movie/show
                if (recommendations.isNotEmpty()) {
                    MediaCarousel(
                        title = "More Like This",
                        items = recommendations,
                        accentColor = VortexCyan,
                        onItemClick = { recItem ->
                            // Instantly switch current playing title to the recommended item!
                            currentMediaId = recItem.id
                            currentMediaType = recItem.mediaType
                            currentTitle = recItem.title
                            currentPoster = recItem.rawPosterPath
                            currentBackdrop = recItem.rawBackdropPath
                            selectedSeason = 1
                            selectedEpisode = 1
                            reloadKey++
                        }
                    )
                } else if (isLoadingRecommendations) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = VortexPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Cast to TV Bottom Sheet Dialog
    if (showCastDialog) {
        val targetUrl = currentEmbedUrl.ifEmpty {
            StreamUrlBuilder.buildWebEmbedUrl(
                server = selectedServer,
                mediaId = currentMediaId,
                mediaType = currentMediaType,
                season = selectedSeason,
                episode = selectedEpisode
            )
        }
        CastToTvBottomSheet(
            title = currentTitle,
            streamUrl = targetUrl,
            posterUrl = currentPoster,
            onDismissRequest = { showCastDialog = false }
        )
    }
}

/**
 * AndroidView wrapping WebView with embedded AdBlocker and Anti-Redirect Shield.
 * Stops intrusive external page hijacks, market:// redirects, popups, and ad tracking scripts.
 * Supports HTML5 and iframe video fullscreen callback directly into landscape orientation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebStreamPlayer(
    embedUrl: String,
    server: StreamServer,
    isInPipMode: Boolean = false,
    onToggleFullscreen: (Boolean) -> Unit = {},
    onRedirectBlocked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isBuffering by remember { mutableStateOf(true) }

    val currentHost = remember(embedUrl) {
        try {
            Uri.parse(embedUrl).host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(embedUrl) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    // Proactively apply and re-apply PiP CSS/JS adjustments when entering/exiting PiP mode
    LaunchedEffect(isInPipMode) {
        webViewRef?.let { wv ->
            wv.requestLayout()
            wv.evaluateJavascript(AdBlocker.buildPipAdjustmentJs(isInPipMode), null)
            if (isInPipMode) {
                // Secondary trigger after the window resize animation settles
                kotlinx.coroutines.delay(250)
                wv.requestLayout()
                wv.evaluateJavascript(AdBlocker.buildPipAdjustmentJs(true), null)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFF000000.toInt())
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT

                        // ANTI-REDIRECT & ANTI-POPUP HARDENING:
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        allowFileAccess = false
                        allowContentAccess = false
                    }

                    // Chrome Client intercepts window.open requests, alert spam, and fullscreen requests
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            // Block all popup windows and popunder hijacks
                            onRedirectBlocked()
                            return false
                        }

                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                            onToggleFullscreen(true)
                        }

                        override fun onHideCustomView() {
                            onToggleFullscreen(false)
                        }

                        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                            result?.confirm()
                            return true
                        }

                        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                            result?.confirm()
                            return true
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            if (newProgress >= 30) {
                                view?.evaluateJavascript(AdBlocker.ANTI_REDIRECT_JS, null)
                            }
                            if (newProgress >= 80) {
                                isBuffering = false
                            }
                        }
                    }

                    // WebViewClient blocks off-site navigation and ad resources
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (AdBlocker.shouldBlockNavigation(url, currentHost)) {
                                onRedirectBlocked()
                                return true // Block external navigation / ad redirect!
                            }
                            return false
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url == null) return false
                            if (AdBlocker.shouldBlockNavigation(url, currentHost)) {
                                onRedirectBlocked()
                                return true // Block external navigation / ad redirect!
                            }
                            return false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            if (AdBlocker.isAdUrl(reqUrl)) {
                                onRedirectBlocked()
                                return AdBlocker.createEmptyResourceResponse() // Block ad network script/beacon!
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isBuffering = true
                            view?.evaluateJavascript(AdBlocker.ANTI_REDIRECT_JS, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isBuffering = false
                            view?.evaluateJavascript(AdBlocker.ANTI_REDIRECT_JS, null)
                            view?.evaluateJavascript(AdBlocker.buildPipAdjustmentJs(isInPipMode), null)
                        }
                    }

                    loadUrl(embedUrl)
                    webViewRef = this
                }
            },
            update = { webView ->
                webView.requestLayout()
                webView.evaluateJavascript(AdBlocker.buildPipAdjustmentJs(isInPipMode), null)
            }
        )

        // Loading and server status overlay (hidden in PiP mode)
        if (isBuffering && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = VortexPurple,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connecting to ${server.displayName}...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VortexPurpleLight,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

/**
 * Opens system Screen Casting / Mirroring settings with graceful fallback.
 * Checks whether ACTION_CAST_SETTINGS can be resolved; if not, falls back to general system settings.
 * Displays an informative Toast guiding the user to Quick Settings > Cast.
 */
fun openCastSettings(context: Context) {
    try {
        val castIntent = Intent(android.provider.Settings.ACTION_CAST_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (castIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(castIntent)
        } else {
            val fallbackIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
        android.widget.Toast.makeText(
            context,
            "Use Cast Settings or Quick Settings > Cast to mirror your screen to your TV",
            android.widget.Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        try {
            val fallbackIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            android.widget.Toast.makeText(
                context,
                "Open Quick Settings > Cast to mirror your screen to your TV",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                context,
                "Cast screen settings are not available on this device",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
