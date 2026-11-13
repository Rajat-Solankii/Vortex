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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Clear
import com.example.ui.components.CastToTvBottomSheet
import com.example.ui.screens.player.StreamServer
import com.example.ui.screens.player.StreamUrlBuilder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexGold

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
    val colors = LocalVortexColors.current

    LaunchedEffect(mediaId, mediaType) {
        viewModel.loadDetails(mediaId, mediaType)
    }

    BackHandler {
        onBack()
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCastDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        when (val uiState = state) {
            is DetailsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
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
                        style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadDetails(mediaId, mediaType) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            }

            is DetailsUiState.Success -> {
                val detail = uiState.detail
                val seasonState = uiState.seasonState
                val scrollState = rememberScrollState()

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
                                            colors.background.copy(alpha = 0.85f),
                                            colors.background
                                        )
                                    )
                                )
                        )

                        // Back Button (Top Left)
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
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

                        // Cast to TV Button (Top Right)
                        IconButton(
                            onClick = { showCastDialog = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(8.dp)
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("details_cast_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Cast to TV",
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
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(150.dp)
                                    .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                                            color = colors.primaryLight,
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
                                        color = colors.textPrimary,
                                        fontSize = 22.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // One UI Curved Bottom Sheet Body
                    Surface(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = colors.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Badges Row: Rating, Year, Duration/Seasons, Quality
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (detail.rating > 0) {
                                    Surface(
                                        color = colors.card,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, colors.cardBorder)
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
                                            if (detail.voteCount > 0) {
                                                Text(
                                                    text = " (${detail.voteCount})",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = colors.textMuted,
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                if (detail.releaseDate.isNotEmpty()) {
                                    Text(
                                        text = detail.releaseDate.take(4),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(text = "•", color = colors.textMuted)
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Text(
                                    text = detail.durationOrSeasons,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)
                                )

                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = "•", color = colors.textMuted)
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
                                            color = colors.card,
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, colors.cardBorder)
                                        ) {
                                            Text(
                                                text = genre,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = colors.textSecondary,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // One UI Thumb-Level Action Bar: Watch (Single-line, non-clipping) + Bookmark + Download + Share
                            val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
                            val context = LocalContext.current

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val s = if (detail.mediaType == MediaType.TV) seasonState.selectedSeasonNumber else 1
                                        onPlay(detail, s, 1)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("details_play_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val playTitle = if (detail.mediaType == MediaType.TV) {
                                            "Watch S${seasonState.selectedSeasonNumber}:E1"
                                        } else {
                                            "Play Movie"
                                        }
                                        Text(
                                            text = playTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                letterSpacing = 0.3.sp,
                                                color = Color.White
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Bookmark Pill
                                Surface(
                                    color = colors.card,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, if (isBookmarked) colors.primary else colors.cardBorder),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clickable { viewModel.toggleBookmark(detail) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (isBookmarked) colors.primaryLight else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Download Pill
                                Surface(
                                    color = colors.card,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clickable { openDownloadLink(context, detail.id, mediaType) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Trailer Pill (if available)
                                if (!detail.trailerKey.isNullOrBlank()) {
                                    Surface(
                                        color = colors.card,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, VortexGold.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clickable {
                                                val trailerIntent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.youtube.com/watch?v=${detail.trailerKey}")
                                                )
                                                context.startActivity(trailerIntent)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Trailer",
                                                tint = VortexGold,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                // Share Pill
                                Surface(
                                    color = colors.card,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clickable {
                                            val typeStr = if (detail.mediaType == MediaType.TV) "tv" else "movie"
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Watch ${detail.title} on Vortex: https://www.watchvortex.me/$typeStr/${detail.id}"
                                                )
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share ${detail.title}"))
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = VortexCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Watch on TV / Cast Option Banner Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCastDialog = true }
                                    .testTag("details_watch_on_tv_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(colors.primary.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = "Cast to TV",
                                            tint = colors.primaryLight,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Watch on TV",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = colors.primary.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Smart View • Cast",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = colors.primaryLight,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Mirror to Smart TV, Windows PC, Chromecast, or browser",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = colors.textSecondary,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Cast,
                                        contentDescription = null,
                                        tint = colors.primaryLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // SEASONS & EPISODES SCROLLER WINDOW WITH SEARCH & JUMP (For TV Shows)
                            if (detail.mediaType == MediaType.TV && detail.seasons.isNotEmpty()) {
                                SeasonsAndEpisodesScrollerWindow(
                                    detail = detail,
                                    seasonState = seasonState,
                                    onSelectSeason = { sNum -> viewModel.selectSeason(detail.id, sNum) },
                                    onPlayEpisode = { sNum, epNum -> onPlay(detail, sNum, epNum) }
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            // Overview Section
                            Text(
                                text = "STORYLINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = colors.textMuted
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = detail.overview,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.textPrimary,
                                    lineHeight = 22.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Top Cast Carousel
                            if (detail.cast.isNotEmpty()) {
                                Text(
                                    text = "TOP CAST",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        color = colors.textMuted
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(detail.cast) { actor ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(72.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.surfaceVariant)
                                            ) {
                                                if (actor.profileUrl.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = actor.profileUrl,
                                                        contentDescription = actor.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = actor.name,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.textPrimary,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Text(
                                                text = actor.character,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = colors.textMuted,
                                                    fontSize = 10.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCastDialog && state is DetailsUiState.Success) {
        val detail = (state as DetailsUiState.Success).detail
        val streamUrl = StreamUrlBuilder.buildWebEmbedUrl(
            server = StreamServer.SERVER1,
            mediaId = detail.id,
            mediaType = detail.mediaType,
            season = 1,
            episode = 1
        )
        CastToTvBottomSheet(
            title = detail.title,
            streamUrl = streamUrl,
            posterUrl = detail.posterUrl.ifEmpty { detail.backdropUrl },
            onDismissRequest = { showCastDialog = false }
        )
    }
}

/**
 * Dedicated Seasons & Episodes Scroller Window with real-time Episode Search,
 * Quick-Jump range batches (1-25, 26-50...), and List/Grid view toggle.
 * Solves endless page scrolling on series/anime with dozens to hundreds of episodes.
 */
@Composable
private fun SeasonsAndEpisodesScrollerWindow(
    detail: MediaDetail,
    seasonState: TvSeasonState,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    val allEpisodes: List<EpisodeItem> = seasonState.episodes

    var episodeSearchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    var selectedRangeIndex by remember(seasonState.selectedSeasonNumber) { mutableIntStateOf(0) }

    // Calculate 25-episode batches for long anime / series (e.g. 1-25, 26-50, 51-75...)
    val batchRanges: List<Pair<Int, Int>> = remember(allEpisodes.size) {
        if (allEpisodes.size <= 25) emptyList()
        else {
            val ranges = mutableListOf<Pair<Int, Int>>()
            var start = 1
            while (start <= allEpisodes.size) {
                val end = (start + 24).coerceAtMost(allEpisodes.size)
                ranges.add(start to end)
                start += 25
            }
            ranges
        }
    }

    // Filter episodes based on search query or selected range batch
    val filteredEpisodes: List<EpisodeItem> = remember(allEpisodes, episodeSearchQuery, selectedRangeIndex) {
        if (episodeSearchQuery.isNotBlank()) {
            val q = episodeSearchQuery.trim().lowercase()
            allEpisodes.filter { ep ->
                ep.episodeNumber.toString() == q ||
                "ep ${ep.episodeNumber}".contains(q) ||
                "episode ${ep.episodeNumber}".contains(q) ||
                ep.name.lowercase().contains(q) ||
                ep.overview.lowercase().contains(q)
            }
        } else if (batchRanges.isNotEmpty() && selectedRangeIndex > 0 && selectedRangeIndex <= batchRanges.size) {
            val (rangeStart, rangeEnd) = batchRanges[selectedRangeIndex - 1]
            allEpisodes.filter { it.episodeNumber in rangeStart..rangeEnd }
        } else {
            allEpisodes
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header with Episode Count Badge and List/Grid Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "SEASONS & EPISODES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = colors.textMuted
                )
            )

            if (allEpisodes.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${allEpisodes.size} eps",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.primaryLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // View toggle: List view vs Grid tile view
            IconButton(
                onClick = { isGridView = !isGridView },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                    contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                    tint = if (isGridView) colors.primary else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

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
                    color = if (isSelected) colors.primary else colors.card,
                    border = if (!isSelected) BorderStroke(1.dp, colors.cardBorder) else null,
                    modifier = Modifier.clickable {
                        onSelectSeason(season.seasonNumber)
                        episodeSearchQuery = ""
                        selectedRangeIndex = 0
                    }
                ) {
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else colors.textSecondary
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Episode Search Input Bar
        OutlinedTextField(
            value = episodeSearchQuery,
            onValueChange = { episodeSearchQuery = it },
            placeholder = {
                Text(
                    text = "Search episode (e.g. 50 or title)...",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = colors.primaryLight,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (episodeSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { episodeSearchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.primary,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        // Quick-Jump Range Chips (Displayed when episodes > 25, e.g. Black Clover, Naruto, One Piece)
        if (batchRanges.isNotEmpty() && episodeSearchQuery.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedRangeIndex == 0,
                    onClick = { selectedRangeIndex = 0 },
                    label = { Text("All (${allEpisodes.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = Color.White,
                        containerColor = colors.card,
                        labelColor = colors.textSecondary
                    )
                )
                batchRanges.forEachIndexed { idx, (start, end) ->
                    val isRangeSelected = selectedRangeIndex == idx + 1
                    FilterChip(
                        selected = isRangeSelected,
                        onClick = { selectedRangeIndex = idx + 1 },
                        label = { Text("$start - $end", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primary,
                            selectedLabelColor = Color.White,
                            containerColor = colors.card,
                            labelColor = colors.textSecondary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bounded Scroller Window (prevents the page from stretching down indefinitely)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.card.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp)
        ) {
            if (seasonState.isLoadingEpisodes) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (filteredEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (episodeSearchQuery.isNotBlank()) "No episodes match '$episodeSearchQuery'" else "No episodes available",
                            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)
                        )
                        if (episodeSearchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { episodeSearchQuery = "" }) {
                                Text("Clear Search", color = colors.primary)
                            }
                        }
                    }
                }
            } else if (isGridView) {
                // Compact Numeric Squircle Grid View (Ideal for long anime)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = filteredEpisodes, key = { it.id }) { ep ->
                        EpisodeGridTile(
                            episode = ep,
                            onClick = { onPlayEpisode(seasonState.selectedSeasonNumber, ep.episodeNumber) }
                        )
                    }
                }
            } else {
                // Detailed Card List View
                LazyColumn(
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = filteredEpisodes, key = { it.id }) { episode ->
                        EpisodeCard(
                            episode = episode,
                            onClick = { onPlayEpisode(seasonState.selectedSeasonNumber, episode.episodeNumber) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Numbered Episode Tile for Grid View.
 */
@Composable
private fun EpisodeGridTile(
    episode: EpisodeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier
            .height(58.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "EP ${episode.episodeNumber}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 12.sp
                )
            )
            if (episode.runtime != null && episode.runtime > 0) {
                Text(
                    text = "${episode.runtime}m",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.primaryLight,
                        fontSize = 10.sp
                    )
                )
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
    val colors = LocalVortexColors.current
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.cardBorder),
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
                    .background(colors.surfaceVariant)
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
                        color = colors.primary.copy(alpha = 0.9f),
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
                        color = colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.overview.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = episode.overview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary,
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
                            color = colors.primaryLight,
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
            ex.printStackTrace()
        }
    }
}
