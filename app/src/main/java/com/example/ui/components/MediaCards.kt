package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexDarkSurfaceVariant
import com.example.ui.theme.VortexGold
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleDeep
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary

// Static reusable shapes to avoid heap allocations on 120Hz frames
private val CardCornerShape = RoundedCornerShape(14.dp)
private val RatingPillShape = RoundedCornerShape(bottomStart = 8.dp)
private val TagShape = RoundedCornerShape(5.dp)

private val CardBorderColor = VortexDarkCardBorder.copy(alpha = 0.6f)
private val ContinueCardBorderColor = VortexPurple.copy(alpha = 0.4f)

private val CardScrimGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.2f),
        Color.Black.copy(alpha = 0.8f)
    )
)

@Composable
fun MediaPosterCard(
    media: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp? = null
) {
    val context = LocalContext.current
    val imageUrl = remember(media.posterUrl, media.backdropUrl) {
        media.posterUrl.ifEmpty { media.backdropUrl }
    }

    // Memoize the ImageRequest with exact thumbnail dimensions for instant 120 FPS decode
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(220, 330))
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .build()
    }

    val widthModifier = if (cardWidth != null) Modifier.width(cardWidth) else Modifier

    Column(
        modifier = modifier
            .then(widthModifier)
            .testTag("media_card_${media.id}")
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CardCornerShape)
                .background(VortexDarkSurfaceVariant)
                .border(1.dp, CardBorderColor, CardCornerShape)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Rating Pill
            if (media.rating > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RatingPillShape,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = VortexGold,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", media.rating),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            // Media Type Tag
            Surface(
                color = VortexPurpleDeep.copy(alpha = 0.85f),
                shape = TagShape,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = if (media.mediaType == MediaType.TV) "TV" else "MOVIE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = media.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = VortexTextPrimary,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (media.releaseDate.length >= 4) media.releaseDate.take(4) else "Stream",
            style = MaterialTheme.typography.labelSmall.copy(
                color = VortexTextMuted,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun ContinueWatchingCard(
    item: WatchHistoryEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress = remember(item.lastPositionMs, item.durationMs) {
        if (item.durationMs > 0) {
            (item.lastPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.05f, 1f)
        } else 0.35f
    }

    val imageSource = remember(item.backdropPath, item.posterPath) {
        when {
            !item.backdropPath.isNullOrBlank() -> "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getBackdropQuality()}${item.backdropPath}"
            !item.posterPath.isNullOrBlank() -> "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getPosterQuality()}${item.posterPath}"
            else -> ""
        }
    }

    val imageRequest = remember(imageSource) {
        if (imageSource.isNotEmpty()) {
            ImageRequest.Builder(context)
                .data(imageSource)
                .size(Size(400, 230))
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .build()
        } else null
    }

    Column(
        modifier = modifier
            .width(200.dp)
            .testTag("continue_watching_${item.id}")
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(CardCornerShape)
                .background(VortexDarkCard)
                .border(1.dp, ContinueCardBorderColor, CardCornerShape)
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardScrimGradient)
            )

            // Play Icon circle in center
            Surface(
                shape = CircleShape,
                color = VortexPurple.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp)
                )
            }

            // Remove button top right
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from history",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Progress bar at bottom
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = VortexPurple,
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = VortexTextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (item.mediaType == "tv") "TV Series • Continue" else "Movie • Resume",
            style = MaterialTheme.typography.labelSmall.copy(
                color = VortexPurpleLight
            )
        )
    }
}
