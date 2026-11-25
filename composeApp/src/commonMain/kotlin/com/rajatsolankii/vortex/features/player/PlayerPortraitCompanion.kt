package com.rajatsolankii.vortex.features.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatsolankii.vortex.core.i18n.localizedEpisodeTitle
import com.rajatsolankii.vortex.core.ui.AppIconResource
import com.rajatsolankii.vortex.core.ui.NuvioCardDepthSurface
import com.rajatsolankii.vortex.core.ui.NuvioTokens
import com.rajatsolankii.vortex.core.ui.appIconPainter
import com.rajatsolankii.vortex.core.ui.nuvio
import com.rajatsolankii.vortex.core.ui.nuvioCardDepth
import com.rajatsolankii.vortex.features.details.MetaVideo
import com.rajatsolankii.vortex.features.home.MetaPreview
import com.rajatsolankii.vortex.features.home.components.HomePosterCard
import com.rajatsolankii.vortex.features.streams.StreamItem
import com.rajatsolankii.vortex.features.vortex.AnilumosAnimeBackend
import com.rajatsolankii.vortex.features.vortex.VortexServers
import com.rajatsolankii.vortex.features.watching.application.WatchingState
import com.rajatsolankii.vortex.features.watchprogress.WatchProgressEntry
import com.rajatsolankii.vortex.features.watchprogress.buildPlaybackVideoId
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val GENERIC_SERVER_LABELS = setOf("vortex", "vortex anime", "anilumos", "stream", "")

internal fun StreamItem.displayServerLabel(): String {
    val rawName = name?.trim().orEmpty()
    val rawTitle = title?.trim().orEmpty()
    if (rawName.lowercase() !in GENERIC_SERVER_LABELS) return rawName
    if (rawTitle.lowercase() !in GENERIC_SERVER_LABELS) return rawTitle
    return addonName.trim().ifBlank { "Server" }
}

internal fun resolveSelectedServerIndex(
    allServers: List<StreamItem>,
    activeSourceUrl: String,
    activeVortexEmbedUrl: String?,
    activeStreamTitle: String,
): Int {
    if (allServers.isEmpty()) return -1
    if (activeSourceUrl.isNotBlank()) {
        val exactSourceIdx = allServers.indexOfFirst { stream ->
            val urlCandidate = stream.playableDirectUrl ?: stream.externalUrl
            urlCandidate != null && urlCandidate == activeSourceUrl
        }
        if (exactSourceIdx >= 0) return exactSourceIdx
    }
    if (!activeVortexEmbedUrl.isNullOrBlank()) {
        val exactEmbedIdx = allServers.indexOfFirst { stream ->
            val urlCandidate = stream.playableDirectUrl ?: stream.externalUrl
            urlCandidate != null && urlCandidate.equals(activeVortexEmbedUrl, ignoreCase = true)
        }
        if (exactEmbedIdx >= 0) return exactEmbedIdx
    }
    val urlDerivedLabel = AnilumosAnimeBackend.displayNameForStreamUrl(activeVortexEmbedUrl ?: activeSourceUrl)
        ?: VortexServers.serverFor(activeVortexEmbedUrl ?: activeSourceUrl)?.displayName
    if (!urlDerivedLabel.isNullOrBlank()) {
        val serverIdx = allServers.indexOfFirst { stream ->
            stream.displayServerLabel().equals(urlDerivedLabel, ignoreCase = true)
        }
        if (serverIdx >= 0) return serverIdx
    }
    val cleanedTitle = activeStreamTitle.trim()
    if (cleanedTitle.lowercase() !in GENERIC_SERVER_LABELS) {
        val titleIdx = allServers.indexOfFirst { stream ->
            stream.displayServerLabel().equals(cleanedTitle, ignoreCase = true)
        }
        if (titleIdx >= 0) return titleIdx
    }
    return -1
}

@Composable
private fun Modifier.nuvioGlassSurface(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState?,
    fillColor: Color,
    borderColor: Color,
    borderWidth: Dp = 0.75.dp,
): Modifier = this
    .clip(shape)
    // Use a single-pass specular frosted glass fill instead of 15+ per-item RenderEffect blurs
    // inside scrolling portrait lists so playback + scrolling stays smooth with low GPU load.
    .background(
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f),
                Color.Transparent,
            ),
        ),
    )
    .background(fillColor)
    .border(borderWidth, borderColor, shape)

@Composable
internal fun PlayerPortraitCompanion(
    title: String,
    overview: String?,
    genres: List<String>,
    backdropUrl: String? = null,
    isSeries: Boolean,
    activeSeasonNumber: Int?,
    activeEpisodeNumber: Int?,
    activeEpisodeTitle: String?,
    activeServerLabel: String,
    isExtractingStream: Boolean,
    allServers: List<StreamItem>,
    isSourcesLoading: Boolean,
    activeSourceUrl: String,
    activeVortexEmbedUrl: String?,
    activeStreamTitle: String,
    episodes: List<MetaVideo>,
    parentMetaType: String,
    parentMetaId: String,
    progressByVideoId: Map<String, WatchProgressEntry>,
    watchedKeys: Set<String>,
    blurUnwatchedEpisodes: Boolean,
    recommendations: List<MetaPreview>,
    isMetaLoading: Boolean,
    onServerSelected: (StreamItem) -> Unit,
    onReloadServers: () -> Unit,
    onEpisodeSelected: (MetaVideo) -> Unit,
    onOpenAllEpisodes: () -> Unit,
    onRecommendationClick: (MetaPreview) -> Unit,
    onOpenFullscreen: () -> Unit,
    onOpenSourcesPanel: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenResize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val glassHazeState = rememberHazeState()
    var descriptionExpanded by remember { mutableStateOf(false) }

    val groupedEpisodes = remember(episodes) {
        episodes
            .filter { it.season != null || it.episode != null }
            .groupBy { it.season?.coerceAtLeast(0) ?: 0 }
    }
    val availableSeasons = remember(groupedEpisodes) {
        groupedEpisodes.keys.filter { it > 0 }.sorted() + groupedEpisodes.keys.filter { it == 0 }
    }
    var selectedSeason by remember(activeSeasonNumber, availableSeasons) {
        mutableIntStateOf(
            when {
                activeSeasonNumber != null && activeSeasonNumber in availableSeasons -> activeSeasonNumber
                availableSeasons.isNotEmpty() -> availableSeasons.first()
                else -> 1
            },
        )
    }
    val seasonEpisodes = remember(groupedEpisodes, selectedSeason) {
        (groupedEpisodes[selectedSeason] ?: emptyList()).sortedBy { it.episode ?: 0 }
    }

    val episodeRowState = rememberLazyListState()
    LaunchedEffect(selectedSeason, seasonEpisodes, activeSeasonNumber, activeEpisodeNumber) {
        if (seasonEpisodes.isEmpty()) return@LaunchedEffect
        val activeIdx = if (selectedSeason == activeSeasonNumber && activeEpisodeNumber != null) {
            seasonEpisodes.indexOfFirst { it.season == activeSeasonNumber && it.episode == activeEpisodeNumber }
        } else {
            -1
        }
        if (activeIdx >= 0) {
            episodeRowState.animateScrollToItem(activeIdx)
        }
    }

    val platformContext = coil3.compose.LocalPlatformContext.current
    val backdropModel = remember(backdropUrl, platformContext) {
        backdropUrl?.takeIf { it.isNotBlank() }?.let { url ->
            coil3.request.ImageRequest.Builder(platformContext)
                .data(url)
                .size(640, 360)
                .build()
        }
    }

    Box(
        modifier = modifier.background(tokens.colors.background),
    ) {
        // Lightweight ambient backdrop without offscreen GPU blur passes
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (backdropModel != null) {
                AsyncImage(
                    model = backdropModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .alpha(0.16f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                tokens.colors.background.copy(alpha = 0.45f),
                                tokens.colors.background.copy(alpha = 0.90f),
                                tokens.colors.background,
                            ),
                        ),
                    ),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            // 1. Now Playing Header & Synopsis
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .animateContentSize(
                            animationSpec = tween(
                                durationMillis = NuvioTokens.Motion.normalMillis,
                                easing = NuvioTokens.Motion.standard,
                            ),
                        ),
                ) {
                    Text(
                        text = title,
                        color = tokens.colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isSeries && activeSeasonNumber != null && activeEpisodeNumber != null) {
                            Box(
                                modifier = Modifier.nuvioGlassSurface(
                                    shape = RoundedCornerShape(NuvioTokens.Radius.full),
                                    hazeState = glassHazeState,
                                    fillColor = tokens.colors.surfaceElevated.copy(alpha = 0.56f),
                                    borderColor = Color.White.copy(alpha = 0.22f),
                                    borderWidth = tokens.borders.hairline,
                                ).padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "S$activeSeasonNumber E$activeEpisodeNumber",
                                    color = tokens.colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.nuvioGlassSurface(
                                shape = RoundedCornerShape(NuvioTokens.Radius.full),
                                hazeState = glassHazeState,
                                fillColor = Color(0xFF1C1C1E).copy(alpha = 0.52f),
                                borderColor = Color.White.copy(alpha = 0.14f),
                                borderWidth = tokens.borders.hairline,
                            ).padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isExtractingStream) "Extracting · $activeServerLabel" else activeServerLabel,
                                color = if (isExtractingStream) tokens.colors.textPrimary else tokens.colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (!activeEpisodeTitle.isNullOrBlank() && isSeries) {
                            Text(
                                text = activeEpisodeTitle,
                                color = tokens.colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        } else if (genres.isNotEmpty()) {
                            Text(
                                text = genres.take(3).joinToString(" · "),
                                color = tokens.colors.textMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }

                    if (!overview.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = overview,
                            color = tokens.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            maxLines = if (descriptionExpanded) 8 else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded },
                        )
                    }
                }
            }

            // 2. Quick Glass Action Pills
            item(key = "quick_actions") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("fullscreen") {
                        QuickActionPill(
                            label = "Fullscreen",
                            icon = Icons.Rounded.Fullscreen,
                            hazeState = glassHazeState,
                            onClick = onOpenFullscreen,
                        )
                    }
                    item("servers") {
                        QuickActionPill(
                            label = "Servers (${allServers.size})",
                            painter = appIconPainter(AppIconResource.PlayerSource),
                            hazeState = glassHazeState,
                            onClick = onOpenSourcesPanel,
                        )
                    }
                    if (isSeries || episodes.isNotEmpty()) {
                        item("episodes") {
                            QuickActionPill(
                                label = "All Episodes",
                                painter = appIconPainter(AppIconResource.PlayerEpisodes),
                                hazeState = glassHazeState,
                                onClick = onOpenAllEpisodes,
                            )
                        }
                    }
                    item("subtitles") {
                        QuickActionPill(
                            label = "Subtitles",
                            painter = appIconPainter(AppIconResource.PlayerSubtitles),
                            hazeState = glassHazeState,
                            onClick = onOpenSubtitles,
                        )
                    }
                    item("audio") {
                        QuickActionPill(
                            label = "Audio",
                            painter = appIconPainter(AppIconResource.PlayerAudioFilled),
                            hazeState = glassHazeState,
                            onClick = onOpenAudio,
                        )
                    }
                    item("resize") {
                        QuickActionPill(
                            label = "Zoom / Fit",
                            painter = appIconPainter(AppIconResource.PlayerAspectRatio),
                            hazeState = glassHazeState,
                            onClick = onOpenResize,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 3. All Servers Strip
            item(key = "servers_section") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Servers",
                        color = tokens.colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (allServers.isNotEmpty()) "${allServers.size} servers" else "Searching...",
                        color = tokens.colors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isSourcesLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = tokens.colors.textPrimary,
                        )
                    } else {
                        IconButton(
                            onClick = onReloadServers,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reload servers",
                                tint = tokens.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (allServers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .nuvioGlassSurface(
                                shape = RoundedCornerShape(NuvioTokens.Radius.lg),
                                hazeState = glassHazeState,
                                fillColor = Color(0xFF1C1C1E).copy(alpha = 0.52f),
                                borderColor = Color.White.copy(alpha = 0.12f),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = if (isSourcesLoading) "Fetching servers..." else "No servers reported yet — tap Refresh to retry.",
                            color = tokens.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    val selectedServerIndex = remember(
                        allServers,
                        activeSourceUrl,
                        activeVortexEmbedUrl,
                        activeStreamTitle,
                    ) {
                        resolveSelectedServerIndex(
                            allServers = allServers,
                            activeSourceUrl = activeSourceUrl,
                            activeVortexEmbedUrl = activeVortexEmbedUrl,
                            activeStreamTitle = activeStreamTitle,
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            items = allServers,
                            key = { idx, item ->
                                "${item.addonId}:${item.playableDirectUrl ?: item.externalUrl ?: item.displayServerLabel()}#$idx"
                            },
                        ) { idx, stream ->
                            val isSelected = idx == selectedServerIndex
                            ServerChipCard(
                                stream = stream,
                                isSelected = isSelected,
                                isExtracting = isSelected && isExtractingStream,
                                hazeState = glassHazeState,
                                onClick = { onServerSelected(stream) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // 4. Series / Anime Seasons & Episodes Section
            if (isSeries || episodes.isNotEmpty()) {
                item(key = "episodes_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Episodes",
                            color = tokens.colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (seasonEpisodes.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${seasonEpisodes.size} in season",
                                color = tokens.colors.textMuted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .nuvioGlassSurface(
                                    shape = RoundedCornerShape(NuvioTokens.Radius.full),
                                    hazeState = glassHazeState,
                                    fillColor = Color(0xFF1C1C1E).copy(alpha = 0.56f),
                                    borderColor = Color.White.copy(alpha = 0.14f),
                                )
                                .clickable(onClick = onOpenAllEpisodes)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.List,
                                contentDescription = null,
                                tint = tokens.colors.textPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Full list",
                                color = tokens.colors.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (availableSeasons.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(availableSeasons, key = { it }) { season ->
                                val selected = season == selectedSeason
                                val chipFill by animateColorAsState(
                                    targetValue = if (selected) {
                                        Color.White.copy(alpha = 0.16f)
                                    } else {
                                        Color(0xFF1C1C1E).copy(alpha = 0.48f)
                                    },
                                    animationSpec = tween(
                                        durationMillis = NuvioTokens.Motion.normalMillis,
                                        easing = NuvioTokens.Motion.standard,
                                    ),
                                    label = "seasonFill",
                                )
                                val chipBorder by animateColorAsState(
                                    targetValue = if (selected) {
                                        Color.White.copy(alpha = 0.42f)
                                    } else {
                                        Color.White.copy(alpha = 0.12f)
                                    },
                                    animationSpec = tween(
                                        durationMillis = NuvioTokens.Motion.normalMillis,
                                        easing = NuvioTokens.Motion.standard,
                                    ),
                                    label = "seasonBorder",
                                )
                                Box(
                                    modifier = Modifier
                                        .nuvioGlassSurface(
                                            shape = RoundedCornerShape(NuvioTokens.Radius.full),
                                            hazeState = glassHazeState,
                                            fillColor = chipFill,
                                            borderColor = chipBorder,
                                        )
                                        .clickable { selectedSeason = season }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = if (season == 0) "Specials" else "Season $season",
                                        color = if (selected) tokens.colors.textPrimary else tokens.colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }

                item(key = "episodes_carousel") {
                    if (seasonEpisodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .nuvioGlassSurface(
                                    shape = RoundedCornerShape(NuvioTokens.Radius.lg),
                                    hazeState = glassHazeState,
                                    fillColor = Color(0xFF1C1C1E).copy(alpha = 0.52f),
                                    borderColor = Color.White.copy(alpha = 0.12f),
                                )
                                .padding(14.dp),
                        ) {
                            Text(
                                text = if (isMetaLoading) "Loading episodes..." else "No episodes available for this season.",
                                color = tokens.colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        LazyRow(
                            state = episodeRowState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                items = seasonEpisodes,
                                key = { idx, ep -> "${ep.season}:${ep.episode}:${ep.id}#$idx" },
                            ) { _, episode ->
                                val isCurrent = episode.season == activeSeasonNumber &&
                                    episode.episode == activeEpisodeNumber
                                val epVideoId = buildPlaybackVideoId(
                                    parentMetaId = parentMetaId,
                                    seasonNumber = episode.season,
                                    episodeNumber = episode.episode,
                                    fallbackVideoId = episode.id,
                                )
                                val progressEntry = progressByVideoId[epVideoId]
                                val isWatched = progressEntry?.isEffectivelyCompleted == true ||
                                    WatchingState.isEpisodeWatched(
                                        watchedKeys = watchedKeys,
                                        metaType = parentMetaType,
                                        metaId = parentMetaId,
                                        episode = episode,
                                    )
                                val progressFraction = (progressEntry?.progressPercent ?: 0f) / 100f
                                PortraitEpisodeCard(
                                    episode = episode,
                                    isCurrent = isCurrent,
                                    isWatched = isWatched,
                                    progressFraction = progressFraction.coerceIn(0f, 1f),
                                    blurThumbnail = blurUnwatchedEpisodes && !isWatched,
                                    hazeState = glassHazeState,
                                    onClick = { onEpisodeSelected(episode) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // 5. Recommendations ("More Like This")
            item(key = "recommendations_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "More Like This",
                        color = tokens.colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (recommendations.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${recommendations.size} picks",
                            color = tokens.colors.textMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (recommendations.isEmpty()) {
                item(key = "recommendations_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .nuvioGlassSurface(
                                shape = RoundedCornerShape(NuvioTokens.Radius.lg),
                                hazeState = glassHazeState,
                                fillColor = Color(0xFF1C1C1E).copy(alpha = 0.52f),
                                borderColor = Color.White.copy(alpha = 0.12f),
                            )
                            .padding(14.dp),
                    ) {
                        Text(
                            text = if (isMetaLoading) "Loading recommendations..." else "No recommendations found for this title.",
                            color = tokens.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                item(key = "recommendations_shelf") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = recommendations,
                            key = { item -> "rec_${item.id}_${item.name}" },
                        ) { item ->
                            HomePosterCard(
                                item = item,
                                onClick = { onRecommendationClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .nuvioGlassSurface(
                shape = RoundedCornerShape(NuvioTokens.Radius.full),
                hazeState = hazeState,
                fillColor = Color(0xFF1C1C1E).copy(alpha = 0.54f),
                borderColor = Color.White.copy(alpha = 0.14f),
                borderWidth = tokens.borders.hairline,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = label,
                tint = tokens.colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tokens.colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            color = tokens.colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ServerChipCard(
    stream: StreamItem,
    isSelected: Boolean,
    isExtracting: Boolean,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val label = stream.displayServerLabel()
    val subtitle = stream.streamSubtitle
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() && !it.equals(label, ignoreCase = true) }
        ?: stream.addonName
    val badgeText = when {
        isExtracting -> "Extracting"
        isSelected -> "Playing"
        else -> "Switch"
    }
    val cardFill by animateColorAsState(
        targetValue = if (isSelected) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color(0xFF1C1C1E).copy(alpha = 0.52f)
        },
        animationSpec = tween(
            durationMillis = NuvioTokens.Motion.normalMillis,
            easing = NuvioTokens.Motion.standard,
        ),
        label = "serverFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isExtracting || isSelected -> Color.White.copy(alpha = 0.44f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(
            durationMillis = NuvioTokens.Motion.normalMillis,
            easing = NuvioTokens.Motion.standard,
        ),
        label = "serverBorder",
    )

    Column(
        modifier = Modifier
            .width(210.dp)
            .nuvioGlassSurface(
                shape = RoundedCornerShape(NuvioTokens.Radius.lg),
                hazeState = hazeState,
                fillColor = cardFill,
                borderColor = borderColor,
                borderWidth = if (isSelected) 1.dp else 0.7.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = tokens.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                    .background(
                        when {
                            isSelected -> Color.White
                            else -> Color.White.copy(alpha = 0.10f)
                        },
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeText,
                    color = if (isSelected) Color.Black else tokens.colors.textSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = tokens.colors.textMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PortraitEpisodeCard(
    episode: MetaVideo,
    isCurrent: Boolean,
    isWatched: Boolean,
    progressFraction: Float,
    blurThumbnail: Boolean,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val thumb = episode.thumbnail ?: episode.seasonPoster
    val displayTitle = (localizedEpisodeTitle(episode.title, episode.episode) ?: episode.title)
        .ifBlank { "Episode ${episode.episode ?: 1}" }
    val cardShape = RoundedCornerShape(NuvioTokens.Radius.lg)
    val cardFill by animateColorAsState(
        targetValue = if (isCurrent) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color(0xFF1C1C1E).copy(alpha = 0.50f)
        },
        animationSpec = tween(
            durationMillis = NuvioTokens.Motion.normalMillis,
            easing = NuvioTokens.Motion.standard,
        ),
        label = "epFill",
    )
    val cardBorder by animateColorAsState(
        targetValue = if (isCurrent) Color.White.copy(alpha = 0.44f) else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(
            durationMillis = NuvioTokens.Motion.normalMillis,
            easing = NuvioTokens.Motion.standard,
        ),
        label = "epBorder",
    )

    Column(
        modifier = Modifier
            .width(196.dp)
            .nuvioGlassSurface(
                shape = cardShape,
                hazeState = hazeState,
                fillColor = cardFill,
                borderColor = cardBorder,
                borderWidth = if (isCurrent) 1.dp else 0.7.dp,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(tokens.colors.surface),
        ) {
            if (!thumb.isNullOrBlank()) {
                val platformContext = coil3.compose.LocalPlatformContext.current
                val thumbRequest = remember(thumb, platformContext) {
                    coil3.request.ImageRequest.Builder(platformContext)
                        .data(thumb)
                        .size(420, 236)
                        .build()
                }
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (blurThumbnail) it.blur(16.dp) else it },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .border(tokens.borders.hairline, Color.White.copy(alpha = 0.16f), RoundedCornerShape(NuvioTokens.Radius.full))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = buildString {
                        episode.season?.let { append("S$it ") }
                        append("E${episode.episode ?: 1}")
                    },
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Playing",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tokens.colors.success.copy(alpha = 0.90f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Watched",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (progressFraction > 0.02f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.90f)),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = displayTitle,
                color = tokens.colors.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val releasedText = episode.released?.take(10) ?: episode.overview
            if (!releasedText.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = releasedText,
                    color = tokens.colors.textMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
