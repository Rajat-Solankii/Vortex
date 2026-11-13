package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.components.ContinueWatchingCard
import com.example.ui.components.MediaCarousel
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.OneUiHeader
import com.example.ui.components.RecommendationSlider
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkSurface
import com.example.ui.theme.VortexGold
import com.example.ui.theme.VortexPink
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleDeep
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onWatchHistoryClick: (WatchHistoryEntity) -> Unit,
    onCategoryViewAllClick: (HomeCategory) -> Unit,
    onProviderClick: (Int, String) -> Unit,
    onAllProvidersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val collapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 1. One UI Large Collapsible Header
        OneUiHeader(collapseFraction = collapseFraction)

        // 2. Horizontal Categories Navigation Bar directly below the header
        CategoryNavigationBar(
            selectedCategory = feedState.selectedCategory,
            onCategorySelected = { category ->
                if (category == HomeCategory.ALL) {
                    viewModel.selectCategory(HomeCategory.ALL)
                } else {
                    onCategoryViewAllClick(category)
                }
            }
        )

        // 3. Main Content
        PullToRefreshBox(
            isRefreshing = feedState.isLoading,
            onRefresh = { viewModel.loadHomeFeed() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (feedState.isLoading && feedState.recommendedSliders.isEmpty() && feedState.anime.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = VortexPurple,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading Vortex Stream...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = VortexPurpleLight)
                        )
                    }
                }
            } else {
                // Full Home Feed with Dynamic Recommendation Slider & Category Carousels
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Movie Recommendations in Slider Format
                    val sliderItems = feedState.recommendedSliders.ifEmpty {
                        listOfNotNull(feedState.featuredHero)
                    }

                    if (sliderItems.isNotEmpty()) {
                        item(key = "recommendation_slider") {
                            RecommendationSlider(
                                recommendations = sliderItems,
                                onPlayClick = { media ->
                                    onWatchHistoryClick(
                                        WatchHistoryEntity(
                                            id = media.id,
                                            title = media.title,
                                            posterPath = media.rawPosterPath,
                                            backdropPath = media.rawBackdropPath,
                                            mediaType = if (media.mediaType == MediaType.TV) "tv" else "movie"
                                        )
                                    )
                                },
                                onDetailsClick = { media ->
                                    onMediaClick(media)
                                }
                            )
                        }
                    }

                    // Continue Watching Section (One UI squircle card)
                    if (watchHistory.isNotEmpty()) {
                        item(key = "continue_watching") {
                            Surface(
                                color = VortexDarkCard.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(VortexCyan)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Continue Watching",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = VortexTextPrimary
                                            )
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "${watchHistory.size} items",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = VortexCyan,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(
                                            items = watchHistory,
                                            key = { it.id }
                                        ) { historyItem ->
                                            ContinueWatchingCard(
                                                item = historyItem,
                                                onClick = { onWatchHistoryClick(historyItem) },
                                                onRemove = { viewModel.removeWatchHistory(historyItem.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Streaming Providers (One UI squircle container)
                    if (feedState.providers.isNotEmpty()) {
                        item(key = "providers_row") {
                            Surface(
                                color = VortexDarkCard.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(VortexCyan)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = "Streaming Providers",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = VortexTextPrimary
                                            )
                                        )

                                        Spacer(modifier = Modifier.weight(1f))

                                        Text(
                                            text = "View All",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = VortexCyan,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onAllProvidersClick() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = feedState.providers,
                                            key = { it.providerId }
                                        ) { provider ->
                                            val imageUrl = if (!provider.logoPath.isNullOrBlank()) {
                                                "https://image.tmdb.org/t/p/w200${provider.logoPath}"
                                            } else ""

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .width(70.dp)
                                                    .clickable { onProviderClick(provider.providerId, provider.providerName) }
                                            ) {
                                                coil.compose.AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = provider.providerName,
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(VortexDarkSurface),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = provider.providerName,
                                                    style = MaterialTheme.typography.labelSmall.copy(color = VortexTextSecondary),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Trending Now
                    if (feedState.trending.isNotEmpty()) {
                        item(key = "cat_trending") {
                            MediaCarousel(
                                title = "Trending Now",
                                items = feedState.trending,
                                onItemClick = onMediaClick,
                                onViewAllClick = { onCategoryViewAllClick(HomeCategory.TRENDING) },
                                accentColor = VortexPurple
                            )
                        }
                    }

                    // Top Rated
                    if (feedState.topRated.isNotEmpty()) {
                        item(key = "cat_top_rated") {
                            MediaCarousel(
                                title = "Top Rated",
                                items = feedState.topRated,
                                onItemClick = onMediaClick,
                                onViewAllClick = { onCategoryViewAllClick(HomeCategory.TOP_RATED) },
                                accentColor = VortexGold
                            )
                        }
                    }

                    // Popular Movies
                    if (feedState.movies.isNotEmpty()) {
                        item(key = "cat_popular_movies") {
                            MediaCarousel(
                                title = "Popular Movies",
                                items = feedState.movies,
                                onItemClick = onMediaClick,
                                onViewAllClick = { onCategoryViewAllClick(HomeCategory.MOVIES) },
                                accentColor = VortexCyan
                            )
                        }
                    }

                    // Popular TV Shows
                    if (feedState.tvShows.isNotEmpty()) {
                        item(key = "cat_popular_tv") {
                            MediaCarousel(
                                title = "Popular TV Shows",
                                items = feedState.tvShows,
                                onItemClick = onMediaClick,
                                onViewAllClick = { onCategoryViewAllClick(HomeCategory.TV_SHOWS) },
                                accentColor = VortexPink
                            )
                        }
                    }

                    // Now Streaming & New
                    if (feedState.nowPlaying.isNotEmpty()) {
                        item(key = "cat_now_playing") {
                            MediaCarousel(
                                title = "Now Streaming & New",
                                items = feedState.nowPlaying,
                                onItemClick = onMediaClick,
                                onViewAllClick = { onCategoryViewAllClick(HomeCategory.NOW_PLAYING) },
                                accentColor = VortexPurpleLight
                            )
                        }
                    }

                    // Category: Anime
                    item(key = "cat_anime") {
                        MediaCarousel(
                            title = "Anime Collection",
                            items = feedState.anime,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(HomeCategory.ANIME) },
                            accentColor = VortexPurple
                        )
                    }

                    // Category: Bollywood
                    item(key = "cat_bollywood") {
                        MediaCarousel(
                            title = "Bollywood",
                            items = feedState.bollywood,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(HomeCategory.BOLLYWOOD) },
                            accentColor = VortexGold
                        )
                    }

                    // Category: Cartoons
                    item(key = "cat_cartoons") {
                        MediaCarousel(
                            title = "Cartoons",
                            items = feedState.cartoons,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(HomeCategory.CARTOONS) },
                            accentColor = VortexCyan
                        )
                    }

                    // Category: Drama
                    item(key = "cat_drama") {
                        MediaCarousel(
                            title = "Drama",
                            items = feedState.drama,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(HomeCategory.DRAMA) },
                            accentColor = VortexPink
                        )
                    }

                    // Category: Hollywood
                    item(key = "cat_hollywood") {
                        MediaCarousel(
                            title = "Hollywood",
                            items = feedState.hollywood,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(HomeCategory.HOLLYWOOD) },
                            accentColor = VortexPurpleLight
                        )
                    }

                    // Category: Movies
                    item(key = "cat_movies") {
                        MediaCarousel(
                            title = "Movies",
                            items = feedState.movies,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(HomeCategory.MOVIES) },
                            accentColor = VortexGold
                        )
                    }

                    // Category: TV Shows
                    item(key = "cat_tv_shows") {
                        MediaCarousel(
                            title = "TV Shows",
                            items = feedState.tvShows,
                            onItemClick = onMediaClick,
                            onViewAllClick = { onCategoryViewAllClick(HomeCategory.TV_SHOWS) },
                            accentColor = VortexCyan
                        )
                    }

                }
            }
        }
    }
}

/**
 * Clean horizontal category navigation bar matching One UI design:
 * Anime, Bollywood, Cartoons, Drama, Hollywood, Movies, TV Shows
 */
@Composable
fun CategoryNavigationBar(
    selectedCategory: HomeCategory,
    onCategorySelected: (HomeCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val navCategories = listOf(
            HomeCategory.ALL,
            HomeCategory.ANIME,
            HomeCategory.BOLLYWOOD,
            HomeCategory.CARTOONS,
            HomeCategory.DRAMA,
            HomeCategory.HOLLYWOOD,
            HomeCategory.MOVIES,
            HomeCategory.TV_SHOWS
        )
        navCategories.forEach { category ->
            val isSelected = selectedCategory == category
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("category_tab_${category.name.lowercase()}")
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) Color.White else VortexTextSecondary,
                        fontSize = 14.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom indicator dot or line for active category
                Box(
                    modifier = Modifier
                        .width(if (isSelected) 18.dp else 0.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) VortexPurple else Color.Transparent)
                )
            }
        }
    }
}
