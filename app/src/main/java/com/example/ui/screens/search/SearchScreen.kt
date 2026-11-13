package com.example.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserPreferencesManager
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.VortexRepository
import com.example.ui.components.MediaPosterCard
import com.example.ui.theme.LocalVortexColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ContentFilter {
    ALL, MOVIES, TV, ANIME
}

enum class YearFilter(val label: String) {
    ALL("All Years"),
    CURRENT("2024+"),
    RECENT("2020-2023"),
    CLASSIC("Pre-2020")
}

enum class SortOption(val label: String) {
    RELEVANCE("Relevance"),
    RATING("Top Rated")
}

@Composable
fun SearchScreen(
    repository: VortexRepository,
    userPreferencesManager: UserPreferencesManager? = null,
    onMediaClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    val coroutineScope = rememberCoroutineScope()

    val isSafeSearchEnabled by (userPreferencesManager?.isSafeSearchEnabled?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) })
    val recentSearches by (userPreferencesManager?.recentSearches?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) })

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedContentFilter by remember { mutableStateOf(ContentFilter.ALL) }
    var selectedYearFilter by remember { mutableStateOf(YearFilter.ALL) }
    var selectedSort by remember { mutableStateOf(SortOption.RELEVANCE) }

    val handleBack: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onBack()
    }

    BackHandler {
        handleBack()
    }

    LaunchedEffect(query, isSafeSearchEnabled) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            isSearching = true
            delay(300) // Debounce search
            val searchResult = repository.search(trimmed, includeAdult = !isSafeSearchEnabled)
            results = searchResult.getOrDefault(emptyList())
            isSearching = false
            userPreferencesManager?.addRecentSearch(trimmed)
        } else {
            results = emptyList()
            isSearching = false
        }
    }

    // Filter and sort results
    val processedResults = remember(results, selectedContentFilter, selectedYearFilter, selectedSort) {
        var list = results

        // Content Filter
        list = when (selectedContentFilter) {
            ContentFilter.ALL -> list
            ContentFilter.MOVIES -> list.filter { it.mediaType == MediaType.MOVIE }
            ContentFilter.TV -> list.filter { it.mediaType == MediaType.TV }
            ContentFilter.ANIME -> list.filter {
                it.title.contains("anime", ignoreCase = true) ||
                it.overview.contains("anime", ignoreCase = true) ||
                it.overview.contains("manga", ignoreCase = true)
            }
        }

        // Year Filter
        list = when (selectedYearFilter) {
            YearFilter.ALL -> list
            YearFilter.CURRENT -> list.filter {
                val year = it.releaseDate.take(4).toIntOrNull() ?: 0
                year >= 2024
            }
            YearFilter.RECENT -> list.filter {
                val year = it.releaseDate.take(4).toIntOrNull() ?: 0
                year in 2020..2023
            }
            YearFilter.CLASSIC -> list.filter {
                val year = it.releaseDate.take(4).toIntOrNull() ?: 0
                year in 1..2019
            }
        }

        // Sort Option
        when (selectedSort) {
            SortOption.RELEVANCE -> list
            SortOption.RATING -> list.sortedByDescending { it.rating }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Pixel-Inspired Search Bar (Compact 58dp Height, 16dp Horizontal Padding, Single-Line 16sp Text)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = handleBack,
                modifier = Modifier.testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = colors.textPrimary
                ),
                placeholder = {
                    Text(
                        text = "Search movies, shows, anime...",
                        color = colors.textMuted,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.primaryLight,
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = colors.textSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.primary,
                    focusedContainerColor = colors.surfaceVariant,
                    unfocusedContainerColor = colors.surfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .padding(end = 4.dp)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    }
                    .testTag("search_input_field")
            )
        }

        // Horizontal Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Content Filters
            FilterChip(
                selected = selectedContentFilter == ContentFilter.ALL,
                onClick = { selectedContentFilter = ContentFilter.ALL },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = Color.White,
                    containerColor = colors.card,
                    labelColor = colors.textSecondary
                )
            )

            FilterChip(
                selected = selectedContentFilter == ContentFilter.MOVIES,
                onClick = { selectedContentFilter = if (selectedContentFilter == ContentFilter.MOVIES) ContentFilter.ALL else ContentFilter.MOVIES },
                label = { Text("Movies") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = Color.White,
                    containerColor = colors.card,
                    labelColor = colors.textSecondary
                )
            )

            FilterChip(
                selected = selectedContentFilter == ContentFilter.TV,
                onClick = { selectedContentFilter = if (selectedContentFilter == ContentFilter.TV) ContentFilter.ALL else ContentFilter.TV },
                label = { Text("TV Shows") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = Color.White,
                    containerColor = colors.card,
                    labelColor = colors.textSecondary
                )
            )

            FilterChip(
                selected = selectedContentFilter == ContentFilter.ANIME,
                onClick = { selectedContentFilter = if (selectedContentFilter == ContentFilter.ANIME) ContentFilter.ALL else ContentFilter.ANIME },
                label = { Text("Anime") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = Color.White,
                    containerColor = colors.card,
                    labelColor = colors.textSecondary
                )
            )

            // Year Filters
            FilterChip(
                selected = selectedYearFilter == YearFilter.CURRENT,
                onClick = { selectedYearFilter = if (selectedYearFilter == YearFilter.CURRENT) YearFilter.ALL else YearFilter.CURRENT },
                label = { Text("2024+") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primaryDeep,
                    selectedLabelColor = Color.White,
                    containerColor = colors.card,
                    labelColor = colors.textSecondary
                )
            )

            // Rating Sort
            FilterChip(
                selected = selectedSort == SortOption.RATING,
                onClick = { selectedSort = if (selectedSort == SortOption.RATING) SortOption.RELEVANCE else SortOption.RATING },
                label = { Text("★ Top Rated") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = Color.White,
                    containerColor = colors.card,
                    labelColor = colors.textSecondary
                )
            )
        }

        // Search Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (query.trim().length >= 2 && processedResults.isEmpty()) {
                // No Results State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No matches for \"$query\"",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Check your spelling or try clearing some filters.",
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TextButton(
                            onClick = {
                                selectedContentFilter = ContentFilter.ALL
                                selectedYearFilter = YearFilter.ALL
                                selectedSort = SortOption.RELEVANCE
                            }
                        ) {
                            Text("Reset Filters", color = colors.primaryLight)
                        }
                    }
                }
            } else if (query.trim().isEmpty()) {
                // Initial State: Show Recent Searches if any
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RECENT SEARCHES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = colors.textMuted
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colors.primaryLight,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        userPreferencesManager?.clearRecentSearches()
                                    }
                                }
                            )
                        }

                        // Recent Searches Flow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.forEach { term ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.card,
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier.clickable { query = term }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = term,
                                            style = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = colors.textMuted,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    coroutineScope.launch {
                                                        userPreferencesManager?.removeRecentSearch(term)
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Suggestion Box (Fades out when search field is focused or typing)
                    AnimatedVisibility(
                        visible = !isFocused,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = colors.primaryLight,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Discover Titles Across the Vortex",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Find movies, anime series, TV seasons, and trending media.",
                                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
            } else {
                // Results Grid - 3 Fixed Columns for Phone Screens
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = processedResults,
                        key = { it.id }
                    ) { media ->
                        MediaPosterCard(
                            media = media,
                            onClick = { onMediaClick(media) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
