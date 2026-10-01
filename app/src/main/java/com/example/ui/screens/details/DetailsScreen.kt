package com.example.ui.screens.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaDetail
import com.example.data.model.MediaType
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexDarkSurface
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
    onPlay: (MediaDetail) -> Unit,
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
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Top Backdrop Hero Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(detail.backdropUrl.ifEmpty { detail.posterUrl })
                                .crossfade(true)
                                .build(),
                            contentDescription = detail.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Deep atmospheric scrim fading to True Black
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Transparent,
                                            VortexDarkBackground.copy(alpha = 0.7f),
                                            VortexDarkBackground
                                        )
                                    )
                                )
                        )

                        // Top bar inside backdrop
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.testTag("details_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Content Details Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Title
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = VortexTextPrimary,
                                fontSize = 28.sp
                            )
                        )

                        if (detail.tagline.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "“${detail.tagline}”",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = VortexPurpleLight,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Metadata Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (detail.rating > 0) {
                                Surface(
                                    color = VortexDarkCard,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VortexGold.copy(alpha = 0.4f))
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
                                        border = androidx.compose.foundation.BorderStroke(1.dp, VortexDarkCardBorder)
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
                            onClick = { onPlay(detail) },
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
                                text = "PLAY STREAM",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

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
                            border = androidx.compose.foundation.BorderStroke(1.dp, VortexPurple.copy(alpha = 0.3f)),
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
