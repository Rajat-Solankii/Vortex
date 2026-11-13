package com.example.ui.screens.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Bookmark
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexPurple

enum class BookmarkTypeFilter {
    ALL, MOVIES, TV
}

enum class BookmarkSort {
    RECENT, TITLE
}

@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel,
    onPlayBookmark: (Bookmark) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var typeFilter by remember { mutableStateOf(BookmarkTypeFilter.ALL) }
    var sortOrder by remember { mutableStateOf(BookmarkSort.RECENT) }

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        viewModel.loadBookmarks()
    }

    // Filter and Sort bookmarks
    val filteredBookmarks = remember(bookmarks, typeFilter, sortOrder) {
        var list = when (typeFilter) {
            BookmarkTypeFilter.ALL -> bookmarks
            BookmarkTypeFilter.MOVIES -> bookmarks.filter { it.mediaType != "tv" }
            BookmarkTypeFilter.TV -> bookmarks.filter { it.mediaType == "tv" }
        }

        when (sortOrder) {
            BookmarkSort.RECENT -> list
            BookmarkSort.TITLE -> list.sortedBy { it.title.lowercase() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Pixel-Inspired Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My List",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                Text(
                    text = "${bookmarks.size} titles saved",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.textSecondary
                    )
                )
            }
        }

        // Filter and Sort Chips Row
        if (bookmarks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = typeFilter == BookmarkTypeFilter.ALL,
                    onClick = { typeFilter = BookmarkTypeFilter.ALL },
                    label = { Text("All (${bookmarks.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = Color.White,
                        containerColor = colors.card,
                        labelColor = colors.textSecondary
                    )
                )

                FilterChip(
                    selected = typeFilter == BookmarkTypeFilter.MOVIES,
                    onClick = { typeFilter = BookmarkTypeFilter.MOVIES },
                    label = { Text("Movies") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = Color.White,
                        containerColor = colors.card,
                        labelColor = colors.textSecondary
                    )
                )

                FilterChip(
                    selected = typeFilter == BookmarkTypeFilter.TV,
                    onClick = { typeFilter = BookmarkTypeFilter.TV },
                    label = { Text("TV Series") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = Color.White,
                        containerColor = colors.card,
                        labelColor = colors.textSecondary
                    )
                )

                FilterChip(
                    selected = sortOrder == BookmarkSort.TITLE,
                    onClick = {
                        sortOrder = if (sortOrder == BookmarkSort.TITLE) BookmarkSort.RECENT else BookmarkSort.TITLE
                    },
                    label = { Text(if (sortOrder == BookmarkSort.TITLE) "A-Z" else "Recently Added") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primaryDeep,
                        selectedLabelColor = Color.White,
                        containerColor = colors.card,
                        labelColor = colors.textSecondary
                    )
                )
            }
        }

        if (isLoading && bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (filteredBookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = colors.primaryLight,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (bookmarks.isEmpty()) "Your List is Empty" else "No matching items",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (bookmarks.isEmpty()) {
                            "Tap the bookmark button on any movie or TV series to save it for later."
                        } else {
                            "Try switching to the 'All' tab to see all your saved items."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredBookmarks,
                    key = { it.mediaId }
                ) { item ->
                    Surface(
                        color = colors.card,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayBookmark(item) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // High Resolution Poster Thumbnail
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(108.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceVariant)
                            ) {
                                val imageSource = if (!item.posterPath.isNullOrBlank()) {
                                    "https://image.tmdb.org/t/p/${com.example.util.DeviceConfigHelper.getPosterQuality()}${item.posterPath}"
                                } else ""

                                if (imageSource.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(imageSource)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (item.mediaType == "tv") VortexPurple.copy(alpha = 0.2f) else VortexCyan.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = if (item.mediaType == "tv") "TV SERIES" else "MOVIE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (item.mediaType == "tv") colors.primaryLight else VortexCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Actions
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Watch button
                                IconButton(
                                    onClick = { onPlayBookmark(item) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(colors.primary, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Watch",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Remove from list button
                                IconButton(
                                    onClick = { viewModel.removeBookmark(item.mediaId) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove from list",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
