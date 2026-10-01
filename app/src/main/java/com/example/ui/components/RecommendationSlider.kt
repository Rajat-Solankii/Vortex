package com.example.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaItem
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexGold
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleDeep
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RecommendationSlider(
    recommendations: List<MediaItem>,
    onPlayClick: (MediaItem) -> Unit,
    onDetailsClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recommendations.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { recommendations.size }
    )

    // Auto-scroll loop every 5 seconds (paused if user is actively scrolling)
    LaunchedEffect(pagerState, recommendations.size) {
        while (isActive && recommendations.size > 1) {
            delay(5000L)
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % recommendations.size
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(durationMillis = 500)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(390.dp)
            .testTag("recommendation_slider")
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val media = recommendations[page]
            SliderPage(
                media = media,
                onPlayClick = { onPlayClick(media) },
                onDetailsClick = { onDetailsClick(media) }
            )
        }

        // Pager Indicator Dots at the bottom-right
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(recommendations.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (isSelected) 22.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) VortexPurple else Color.White.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun SliderPage(
    media: MediaItem,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUrl = remember(media.backdropUrl, media.posterUrl) {
        media.backdropUrl.ifEmpty { media.posterUrl }
    }
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(coil.size.Size(1080, 720))
            .precision(coil.size.Precision.INEXACT)
            .allowHardware(true)
            .build()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Backdrop poster with smooth high-res loading
        AsyncImage(
            model = imageRequest,
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Cinematic vignette gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            VortexDarkBackground.copy(alpha = 0.75f),
                            VortexDarkBackground
                        ),
                        startY = 0f,
                        endY = 950f
                    )
                )
        )

        // Movie Info Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(0.92f)
        ) {
            // Category Badge
            Surface(
                color = VortexPurpleDeep.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "FEATURED RECOMMENDATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Movie Title
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = VortexTextPrimary,
                    fontSize = 24.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Rating & Meta info
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (media.rating > 0) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = VortexGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", media.rating),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "•", color = VortexTextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (media.releaseDate.isNotEmpty()) {
                    Text(
                        text = media.releaseDate.take(4),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = VortexTextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "•", color = VortexTextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = "Ultra HD 4K",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = VortexCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Overview
            Text(
                text = media.overview,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = VortexTextSecondary,
                    lineHeight = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Watch Now & Details Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("slider_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Watch Now",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onDetailsClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("slider_details_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Details",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Details",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
