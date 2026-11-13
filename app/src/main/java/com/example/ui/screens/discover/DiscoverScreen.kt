package com.example.ui.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.VortexRepository
import com.example.ui.components.MediaCarousel
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexGold
import com.example.ui.theme.VortexPink
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleLight

data class GenreCategory(
    val id: Int,
    val name: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val isTv: Boolean = false
)

val DISCOVER_GENRES = listOf(
    GenreCategory(28, "Action", Icons.Default.RocketLaunch, listOf(Color(0xFFE53935), Color(0xFF8E0000))),
    GenreCategory(878, "Sci-Fi", Icons.Default.RocketLaunch, listOf(Color(0xFF7C4DFF), Color(0xFF311B92))),
    GenreCategory(16, "Animation", Icons.Default.Animation, listOf(Color(0xFF00E5FF), Color(0xFF006064))),
    GenreCategory(35, "Comedy", Icons.Default.TheaterComedy, listOf(Color(0xFFFFB300), Color(0xFFFF6F00))),
    GenreCategory(18, "Drama", Icons.Default.Movie, listOf(Color(0xFF8E24AA), Color(0xFF4A148C))),
    GenreCategory(27, "Horror", Icons.Default.Visibility, listOf(Color(0xFF212121), Color(0xFF000000))),
    GenreCategory(9648, "Mystery", Icons.Default.Psychology, listOf(Color(0xFF00897B), Color(0xFF004D40))),
    GenreCategory(10759, "Action & Adventure", Icons.Default.Tv, listOf(Color(0xFF1E88E5), Color(0xFF0D47A1)), isTv = true),
    GenreCategory(10765, "Sci-Fi & Fantasy", Icons.Default.Tv, listOf(Color(0xFFAB47BC), Color(0xFF6A1B9A)), isTv = true),
    GenreCategory(10749, "Romance", Icons.Default.Movie, listOf(Color(0xFFFF4081), Color(0xFFC2185B)))
)

@Composable
fun GenreCategoryCard(
    genre: GenreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .height(78.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(genre.gradientColors)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = genre.icon,
                        contentDescription = genre.name,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        lineHeight = 18.sp,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DiscoverScreen(
    repository: VortexRepository,
    onMediaClick: (MediaItem) -> Unit,
    onGenreClick: (Int, String, Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onProviderClick: (Int, String) -> Unit,
    onViewAllCategoriesClick: () -> Unit = {},
    onCategoryViewAllClick: (com.example.ui.screens.home.HomeCategory) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    var topRated by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var trendingTv by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val topResult = repository.getTopRated(isTv = false)
        val nowResult = repository.getNowPlaying()
        val tvResult = repository.getTvShows()

        topRated = topResult.getOrDefault(emptyList())
        nowPlaying = nowResult.getOrDefault(emptyList())
        trendingTv = tvResult.getOrDefault(emptyList())
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Pixel-Inspired Top App Bar (Without redundant search button, accessible via bottom nav)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)
        ) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Explore genres, collections & top picks",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary
                )
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Genre Explorer Grid Preview (First 6 Categories)
                item(key = "genres_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Section Header Row: Title & View All
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GENRES & CATEGORIES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.2.sp,
                                    color = colors.textMuted
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onViewAllCategoriesClick() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "View all",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = colors.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "View all categories",
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // 2-Column Responsive Flow with Compact 10dp Spacing (First 6 preview items)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val previewChunked = DISCOVER_GENRES.take(6).chunked(2)
                            previewChunked.forEach { rowGenres ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowGenres.forEach { genre ->
                                        GenreCategoryCard(
                                            genre = genre,
                                            onClick = { onGenreClick(genre.id, genre.name, genre.isTv) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowGenres.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Top Rated Carousel
                if (topRated.isNotEmpty()) {
                    item(key = "top_rated_carousel") {
                        MediaCarousel(
                            title = "Top Rated Movies",
                            items = topRated,
                            accentColor = VortexGold,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(com.example.ui.screens.home.HomeCategory.TOP_RATED) }
                        )
                    }
                }

                // Now Playing in Theaters / Recently Added
                if (nowPlaying.isNotEmpty()) {
                    item(key = "now_playing_carousel") {
                        MediaCarousel(
                            title = "Now Streaming & New",
                            items = nowPlaying,
                            accentColor = VortexCyan,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(com.example.ui.screens.home.HomeCategory.NOW_PLAYING) }
                        )
                    }
                }

                // Trending TV Shows
                if (trendingTv.isNotEmpty()) {
                    item(key = "trending_tv_carousel") {
                        MediaCarousel(
                            title = "Binge-Worthy TV Series",
                            items = trendingTv,
                            accentColor = VortexPink,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(com.example.ui.screens.home.HomeCategory.TRENDING_TV) }
                        )
                    }
                }
            }
        }
    }
}
