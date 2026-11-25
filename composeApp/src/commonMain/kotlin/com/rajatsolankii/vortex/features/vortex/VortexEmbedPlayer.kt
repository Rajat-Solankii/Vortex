package com.rajatsolankii.vortex.features.vortex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatsolankii.vortex.core.ui.PlatformBackHandler
import com.rajatsolankii.vortex.features.details.MetaDetailsRepository
import com.rajatsolankii.vortex.features.details.MetaVideo
import com.rajatsolankii.vortex.features.player.HidePlayerSystemBars
import com.rajatsolankii.vortex.features.player.LockPlayerToLandscape
import com.rajatsolankii.vortex.features.player.PlayerLaunch
import com.rajatsolankii.vortex.features.player.PlayerPlaybackSnapshot
import com.rajatsolankii.vortex.features.player.externalPlaybackSession
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressClock
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.delay

/** Vortex brand colors used by the player screen. */
internal object VortexColors {
    val Purple = Color(0xFF7C4DFF)
    val PurpleLight = Color(0xFFB388FF)
    val Cyan = Color(0xFF00E5FF)
    val Background = Color(0xFF0F0F14)
    val Surface = Color(0xFF161620)
    val Card = Color(0xFF1E1D2B)
    val CardBorder = Color(0xFF2E2C40)
    val TextSecondary = Color(0xFFA5A4B8)
    val ShieldGreen = Color(0xFF00E676)
}

/** Time in the player before the title is added to Continue Watching. */
private const val MIN_TRACKED_WATCH_MS = 60_000L

/**
 * Platform web view for one embed server. It applies [VortexAdShield]: ad requests are
 * dropped, pop-ups are refused and navigation away from the player is blocked.
 */
@Composable
internal expect fun VortexEmbedWebView(
    url: String,
    modifier: Modifier,
    onLoadingChange: (Boolean) -> Unit,
    onBlocked: () -> Unit,
    onError: ((String) -> Unit)? = null,
)

/**
 * The Vortex player screen. In portrait the server's web player sits at the top, with
 * the server picker and, for shows, a season and episode picker below it. The full-screen
 * button switches to landscape without reloading the player.
 */
@Composable
internal fun VortexEmbedPlayer(
    launch: PlayerLaunch,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSeries = launch.contentType.equals("series", true) || launch.contentType.equals("tv", true)
    val initialTarget = remember(launch) {
        launch.videoId?.let { VortexServers.targetFromVideoId(it, launch.contentType.orEmpty()) }
            ?: VortexServers.targetForUrl(launch.sourceUrl)
    }
    var target by remember(launch) { mutableStateOf(initialTarget) }
    var server by remember(launch) {
        mutableStateOf(VortexServers.serverFor(launch.sourceUrl) ?: VortexServers.preferredServer)
    }
    val url = target?.let(server::embedUrl) ?: launch.sourceUrl

    var currentVideoId by remember(launch) { mutableStateOf(launch.videoId) }
    var currentEpisodeTitle by remember(launch) { mutableStateOf(launch.episodeTitle) }
    var fullscreen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // Episodes for the season picker.
    var episodes by remember(launch.parentMetaId) { mutableStateOf<List<MetaVideo>>(emptyList()) }
    LaunchedEffect(launch.parentMetaId, isSeries) {
        if (!isSeries) return@LaunchedEffect
        val meta = runCatching { MetaDetailsRepository.fetch("series", launch.parentMetaId) }.getOrNull()
        episodes = meta?.videos.orEmpty()
            .filter { it.season != null && it.episode != null }
            .sortedWith(compareBy({ if (it.season == 0) Int.MAX_VALUE else it.season }, { it.episode }))
    }
    val seasons = remember(episodes) { episodes.mapNotNull { it.season }.distinct() }
    var selectedSeason by remember(launch) { mutableStateOf(launch.seasonNumber ?: initialTarget?.season ?: 1) }

    TrackWatchProgress(launch, currentVideoId, target, currentEpisodeTitle)

    if (fullscreen) {
        LockPlayerToLandscape()
        HidePlayerSystemBars()
    }
    PlatformBackHandler(enabled = true) {
        if (fullscreen) fullscreen = false else onBack()
    }

    val subtitle = buildString {
        append(server.displayName)
        append(" · ")
        val t = target
        if (isSeries && t != null) append("S${t.season} E${t.episode}") else append("Movie")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else VortexColors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!fullscreen) {
                PlayerTopBar(
                    title = launch.title,
                    subtitle = subtitle,
                    onBack = onBack,
                    onFullscreen = { fullscreen = true },
                    onReload = { reloadKey++ },
                )
            }

            // The web player keeps its place in the tree, so toggling full screen does not reload it.
            Box(
                modifier = if (fullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                }.background(Color.Black),
            ) {
                key(url, reloadKey) {
                    VortexEmbedWebView(
                        url = url,
                        modifier = Modifier.fillMaxSize(),
                        onLoadingChange = { loading = it },
                        onBlocked = { blockedCount++ },
                    )
                }
                if (loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = VortexColors.Purple, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Connecting to ${server.displayName}…",
                            color = VortexColors.PurpleLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (fullscreen) {
                    FullscreenExitHandle(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onExit = { fullscreen = false },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { fullscreen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = "Full screen", tint = Color.White)
                    }
                }
            }

            if (!fullscreen) {
                PlayerDetails(
                    title = launch.title,
                    isSeries = isSeries,
                    target = target,
                    server = server,
                    blockedCount = blockedCount,
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    episodes = episodes.filter { it.season == selectedSeason },
                    onServerSelected = { picked ->
                        if (picked != server) {
                            server = picked
                            VortexServers.preferredServer = picked
                            loading = true
                        }
                    },
                    onSeasonSelected = { selectedSeason = it },
                    onEpisodeSelected = { episode ->
                        val current = target ?: return@PlayerDetails
                        val season = episode.season ?: return@PlayerDetails
                        val number = episode.episode ?: return@PlayerDetails
                        if (current.season == season && current.episode == number) return@PlayerDetails
                        target = current.copy(isSeries = true, season = season, episode = number)
                        currentVideoId = episode.id
                        currentEpisodeTitle = episode.title
                        loading = true
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Adds the title (and the episode being watched) to Continue Watching when the viewer leaves. */
@Composable
private fun TrackWatchProgress(
    launch: PlayerLaunch,
    videoId: String?,
    target: VortexEmbedTarget?,
    episodeTitle: String?,
) {
    val enteredAtMs = remember(launch) { WatchProgressClock.nowEpochMs() }
    val latestVideoId by rememberUpdatedState(videoId)
    val latestTarget by rememberUpdatedState(target)
    val latestEpisodeTitle by rememberUpdatedState(episodeTitle)
    DisposableEffect(launch) {
        onDispose {
            val watchedMs = WatchProgressClock.nowEpochMs() - enteredAtMs
            if (watchedMs < MIN_TRACKED_WATCH_MS) return@onDispose
            val t = latestTarget
            val episodeLaunch = if (t != null && t.isSeries) {
                launch.copy(
                    videoId = latestVideoId ?: launch.videoId,
                    seasonNumber = t.season,
                    episodeNumber = t.episode,
                    episodeTitle = latestEpisodeTitle,
                )
            } else {
                launch
            }
            WatchProgressRepository.upsertPlaybackProgress(
                session = episodeLaunch.externalPlaybackSession(),
                snapshot = PlayerPlaybackSnapshot(
                    isLoading = false,
                    isPlaying = false,
                    isEnded = false,
                    durationMs = 0L,
                    positionMs = watchedMs,
                ),
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onReload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VortexColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = VortexColors.PurpleLight,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onFullscreen) {
            Icon(Icons.Rounded.Fullscreen, contentDescription = "Full screen", tint = Color.White)
        }
        IconButton(onClick = onReload) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Reload", tint = Color.White)
        }
    }
}

/** Exit button in full screen; fades out after a few seconds and comes back on tap. */
@Composable
private fun FullscreenExitHandle(modifier: Modifier, onExit: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(3_000)
            visible = false
        }
    }
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(10.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable { if (visible) onExit() else visible = true },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit full screen", tint = Color.White)
            }
        }
    }
}

@Composable
private fun PlayerDetails(
    title: String,
    isSeries: Boolean,
    target: VortexEmbedTarget?,
    server: VortexServer,
    blockedCount: Int,
    seasons: List<Int>,
    selectedSeason: Int,
    episodes: List<MetaVideo>,
    onServerSelected: (VortexServer) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (MetaVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "shield") { ShieldCard(blockedCount) }

        item(key = "servers-title") { SectionTitle("Servers") }
        item(key = "servers") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VortexServer.entries, key = { it.name }) { option ->
                    ServerChip(
                        server = option,
                        selected = option == server,
                        onClick = { onServerSelected(option) },
                    )
                }
            }
        }
        item(key = "server-hint") {
            Text(
                text = "Not playing? Try another server.",
                color = VortexColors.TextSecondary,
                fontSize = 12.sp,
            )
        }

        if (isSeries && seasons.isNotEmpty()) {
            item(key = "seasons-title") { SectionTitle("Seasons") }
            item(key = "seasons") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { it }) { season ->
                        PillChip(
                            label = if (season == 0) "Specials" else "Season $season",
                            selected = season == selectedSeason,
                            onClick = { onSeasonSelected(season) },
                        )
                    }
                }
            }
            item(key = "episodes-title") { SectionTitle("Episodes") }
            itemsIndexed(episodes, key = { _, video -> video.id }) { _, video ->
                EpisodeRow(
                    video = video,
                    playing = target != null && target.season == video.season && target.episode == video.episode,
                    onClick = { onEpisodeSelected(video) },
                )
            }
        } else if (!isSeries) {
            item(key = "movie-info") {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
            }
        }
        item(key = "bottom-inset") {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ShieldCard(blockedCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VortexColors.ShieldGreen.copy(alpha = 0.08f))
            .border(1.dp, VortexColors.ShieldGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = VortexColors.ShieldGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Vortex Shield on", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Ads, pop-ups and redirects are blocked",
                color = VortexColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        if (blockedCount > 0) {
            Text(
                text = "$blockedCount blocked",
                color = VortexColors.ShieldGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = VortexColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun ServerChip(server: VortexServer, selected: Boolean, onClick: () -> Unit) {
    PillChip(
        label = server.displayName + if (server.recommendedForAnime) " · Anime" else "",
        selected = selected,
        onClick = onClick,
        showCheck = true,
    )
}

@Composable
private fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, showCheck: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) VortexColors.Purple else VortexColors.Card)
            .border(1.dp, if (selected) VortexColors.PurpleLight else VortexColors.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected && showCheck) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EpisodeRow(video: MetaVideo, playing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (playing) VortexColors.Purple.copy(alpha = 0.18f) else VortexColors.Surface)
            .border(1.dp, if (playing) VortexColors.Purple else VortexColors.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(VortexColors.Card),
            contentAlignment = Alignment.Center,
        ) {
            if (!video.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (playing) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(VortexColors.Purple),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Playing", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Episode ${video.episode}",
                color = if (playing) VortexColors.PurpleLight else VortexColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            video.runtime?.let { minutes ->
                Text(text = "$minutes min", color = VortexColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
