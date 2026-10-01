package com.example.ui.screens.home

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.components.ContinueWatchingCard
import com.example.ui.components.MediaCarousel
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.RecommendationSlider
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkSurface
import com.example.ui.theme.VortexGold
import com.example.ui.theme.VortexPink
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleDeep
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onWatchHistoryClick: (WatchHistoryEntity) -> Unit,
    onSearchClick: () -> Unit,
    onHistoryScreenClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
            .statusBarsPadding()
    ) {
        // 1. Vortex Top Header Bar (Pinned to the very top)
        TopVortexAppBar(
            onSearchClick = onSearchClick,
            onHistoryScreenClick = onHistoryScreenClick,
            onSettingsClick = onSettingsClick,
            onRefreshClick = { viewModel.loadHomeFeed() }
        )

        // 2. Horizontal Categories Navigation Bar directly below the header
        CategoryNavigationBar(
            selectedCategory = feedState.selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) }
        )

        // 3. Main Content
        if (feedState.isLoading) {
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
        } else if (feedState.selectedCategory == HomeCategory.ALL) {
            // Full Home Feed with Dynamic Recommendation Slider & Category Carousels
            LazyColumn(
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

                // Continue Watching Section (from Room DB)
                if (watchHistory.isNotEmpty()) {
                    item(key = "continue_watching") {
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

                // Category: Anime
                item(key = "cat_anime") {
                    MediaCarousel(
                        title = "Anime",
                        items = feedState.anime,
                        onItemClick = onMediaClick,
                        accentColor = VortexPurple
                    )
                }

                // Category: Bollywood
                item(key = "cat_bollywood") {
                    MediaCarousel(
                        title = "Bollywood",
                        items = feedState.bollywood,
                        onItemClick = onMediaClick,
                        accentColor = VortexGold
                    )
                }

                // Category: Cartoons
                item(key = "cat_cartoons") {
                    MediaCarousel(
                        title = "Cartoons",
                        items = feedState.cartoons,
                        onItemClick = onMediaClick,
                        accentColor = VortexCyan
                    )
                }

                // Category: Drama
                item(key = "cat_drama") {
                    MediaCarousel(
                        title = "Drama",
                        items = feedState.drama,
                        onItemClick = onMediaClick,
                        accentColor = VortexPink
                    )
                }

                // Category: Hollywood
                item(key = "cat_hollywood") {
                    MediaCarousel(
                        title = "Hollywood",
                        items = feedState.hollywood,
                        onItemClick = onMediaClick,
                        accentColor = VortexPurpleLight
                    )
                }

                // Category: Movies
                item(key = "cat_movies") {
                    MediaCarousel(
                        title = "Movies",
                        items = feedState.movies,
                        onItemClick = onMediaClick,
                        accentColor = VortexGold
                    )
                }

                // Category: TV Shows
                item(key = "cat_tv_shows") {
                    MediaCarousel(
                        title = "TV Shows",
                        items = feedState.tvShows,
                        onItemClick = onMediaClick,
                        accentColor = VortexCyan
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        } else {
            // Filtered Category Grid View
            val itemsForCategory = when (feedState.selectedCategory) {
                HomeCategory.ANIME -> feedState.anime
                HomeCategory.BOLLYWOOD -> feedState.bollywood
                HomeCategory.CARTOONS -> feedState.cartoons
                HomeCategory.DRAMA -> feedState.drama
                HomeCategory.HOLLYWOOD -> feedState.hollywood
                HomeCategory.MOVIES -> feedState.movies
                HomeCategory.TV_SHOWS -> feedState.tvShows
                HomeCategory.ALL -> emptyList()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = feedState.selectedCategory.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = VortexTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${itemsForCategory.size} titles",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = VortexPurpleLight
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = itemsForCategory,
                        key = { "${it.id}_${it.mediaType}" }
                    ) { media ->
                        MediaPosterCard(
                            media = media,
                            onClick = { onMediaClick(media) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Vortex App Bar with logo and action icons
 */
@Composable
fun TopVortexAppBar(
    onSearchClick: () -> Unit,
    onHistoryScreenClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VortexDarkBackground,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Brand Logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = VortexPurple,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "V",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "VORTEX",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Search Icon
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("home_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White
                )
            }

            // Watch History Screen Icon
            IconButton(
                onClick = onHistoryScreenClick,
                modifier = Modifier.testTag("home_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = Color.White
                )
            }

            // Settings Screen Icon
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("home_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }

            // Refresh button
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier.testTag("home_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Feed",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Clean horizontal category navigation bar matching the design:
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
            .background(Color.Black.copy(alpha = 0.6f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeCategory.values().forEach { category ->
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
