package com.example.ui.screens.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.EpisodeItem
import com.example.data.model.MediaDetail
import com.example.data.model.MediaType
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexDarkSurface
import com.example.ui.theme.VortexDarkSurfaceVariant
import com.example.ui.theme.VortexGold
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleDeep
import com.example.ui.theme.VortexPurpleGlow
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    mediaId: Int,
    mediaType: MediaType,
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onPlay: (MediaDetail, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(mediaId, mediaType) {
        viewModel.loadDetails(mediaId, mediaType)
    }

    BackHandler {
        onBack()
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
    ) {
        when (val uiState = state) {
            is DetailsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VortexPurple)
                }
            }

            is DetailsUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Unable to load details",
                        style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium.copy(color = VortexTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadDetails(mediaId, mediaType) },
                        colors = ButtonDefaults.buttonColors(containerColor = VortexPurple)
                    ) {
                        Text("Retry")
                    }
                }
            }

            is DetailsUiState.Success -> {
                val detail = uiState.detail
                val seasonState = uiState.seasonState

                var visibleEpisodesCount by remember(seasonState.selectedSeasonNumber) { mutableIntStateOf(10) }
                val scrollState = rememberScrollState()

                LaunchedEffect(scrollState.value) {
                    if (scrollState.value >= scrollState.maxValue - 500 && scrollState.maxValue > 0) {
                        if (visibleEpisodesCount < seasonState.episodes.size) {
                            visibleEpisodesCount += 10
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Hero Backdrop & Poster Header Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        // Backdrop
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(detail.backdropUrl.ifEmpty { detail.posterUrl })
                                .crossfade(true)
                                .build(),
                            contentDescription = detail.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Vignette Gradients
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Transparent,
                                            VortexDarkBackground.copy(alpha = 0.85f),
                                            VortexDarkBackground
                                        )
                                    )
                                )
                        )

                        // Back Button (Top Left)
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(8.dp)
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("details_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        // Floating Poster Card (Bottom Left)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(150.dp)
                                    .border(1.dp, VortexPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(detail.posterUrl.ifEmpty { detail.backdropUrl })
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = detail.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Header Text info
                            Column(modifier = Modifier.weight(1f)) {
                                if (detail.tagline.isNotEmpty()) {
                                    Text(
                                        text = detail.tagline.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = VortexPurpleLight,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Text(
                                    text = detail.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = VortexTextPrimary,
                                        fontSize = 22.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleBookmark(detail) },
                                    modifier = Modifier.testTag("details_bookmark_button")
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) VortexPurpleLight else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Content Details Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Badges Row: Rating, Year, Duration/Seasons, Quality
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (detail.rating > 0) {
                                Surface(
                                    color = VortexDarkCard,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, VortexDarkCardBorder)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = VortexGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = String.format("%.1f", detail.rating),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            if (detail.releaseDate.isNotEmpty()) {
                                Text(
                                    text = detail.releaseDate.take(4),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = VortexTextSecondary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = "•", color = VortexTextMuted)
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            Text(
                                text = detail.durationOrSeasons,
                                style = MaterialTheme.typography.bodyMedium.copy(color = VortexTextSecondary)
                            )

                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "•", color = VortexTextMuted)
                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                color = VortexCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "4K HDR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = VortexCyan,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Genres Flow
                        if (detail.genres.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                detail.genres.forEach { genre ->
                                    Surface(
                                        color = VortexDarkCard,
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, VortexDarkCardBorder)
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = VortexTextSecondary,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // PROMINENT PLAY BUTTON
                        Button(
                            onClick = {
                                val s = if (detail.mediaType == MediaType.TV) seasonState.selectedSeasonNumber else 1
                                onPlay(detail, s, 1)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("details_play_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (detail.mediaType == MediaType.TV) "WATCH SEASON ${seasonState.selectedSeasonNumber} EP 1" else "PLAY STREAM",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // DOWNLOAD BUTTON
                        val context = LocalContext.current
                        Button(
                            onClick = {
                                openDownloadLink(
                                    context = context,
                                    mediaId = detail.id,
                                    mediaType = mediaType
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VortexDarkCard),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, VortexPurple.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DOWNLOAD",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // REAL SEASONS & EPISODES LIST (For TV Shows)
                        if (detail.mediaType == MediaType.TV && detail.seasons.isNotEmpty()) {
                            Text(
                                text = "SEASONS & EPISODES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = VortexTextMuted
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Horizontal Season Selector Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                detail.seasons.forEach { season ->
                                    val isSelected = season.seasonNumber == seasonState.selectedSeasonNumber
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) VortexPurple else VortexDarkCard,
                                        border = if (!isSelected) BorderStroke(1.dp, VortexDarkCardBorder) else null,
                                        modifier = Modifier.clickable {
                                            viewModel.selectSeason(detail.id, season.seasonNumber)
                                        }
                                    ) {
                                        Text(
                                            text = season.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else VortexTextSecondary
                                            ),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Episodes List
                            if (seasonState.isLoadingEpisodes) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = VortexPurple,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    seasonState.episodes.take(visibleEpisodesCount).forEach { episode ->
                                        EpisodeCard(
                                            episode = episode,
                                            onClick = {
                                                onPlay(detail, seasonState.selectedSeasonNumber, episode.episodeNumber)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Overview Section
                        Text(
                            text = "STORYLINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = VortexTextMuted
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = detail.overview,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = VortexTextPrimary,
                                lineHeight = 22.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Source Engine Info Card
                        Surface(
                            color = VortexDarkCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, VortexPurple.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = VortexPurpleLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Vortex Dual Player Engine",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = VortexTextPrimary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Supports native embed web sources (VAPlayer, Videasy, NHDAPI) and direct Media3 ExoPlayer (.m3u8/.mp4) playback with auto-saved Watch History.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = VortexTextSecondary,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VortexDarkCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VortexDarkCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail with Play Overlay
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(65.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VortexDarkSurfaceVariant)
            ) {
                if (episode.stillUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(episode.stillUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = VortexPurple.copy(alpha = 0.9f),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.overview.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = episode.overview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VortexTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (episode.runtime != null && episode.runtime > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${episode.runtime}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = VortexPurpleLight,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

fun openDownloadLink(context: Context, mediaId: Int, mediaType: MediaType) {
    val typeString = if (mediaType == MediaType.MOVIE) "movie" else "tv"
    val url = "https://moviesdl.cc/p/info.html?id=$mediaId&type=$typeString"
    
    try {
        val customTabsIntent = androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        // Fallback to standard browser if Chrome Custom Tabs fail or aren't supported
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (ex: Exception) {
            // Handle edge cases where no browser is installed at all
            ex.printStackTrace()
        }
    }
}
