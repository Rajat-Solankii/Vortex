package com.example.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.VortexRepository
import com.example.ui.components.MediaPosterCard
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    repository: VortexRepository,
    onMediaClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<MediaType?>(null) }

    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        onBack()
    }

    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            isSearching = true
            delay(400) // Debounce search queries
            val searchResult = repository.search(query.trim())
            results = searchResult.getOrDefault(emptyList())
            isSearching = false
        } else {
            results = emptyList()
            isSearching = false
        }
    }

    val filteredResults = remember(results, selectedFilter) {
        if (selectedFilter == null) results
        else results.filter { it.mediaType == selectedFilter }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
            .statusBarsPadding()
    ) {
        // Search Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text("Search movies, TV shows, anime...", color = VortexTextMuted)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = VortexPurpleLight
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = VortexTextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VortexPurple,
                    unfocusedBorderColor = VortexDarkCardBorder,
                    focusedTextColor = VortexTextPrimary,
                    unfocusedTextColor = VortexTextPrimary,
                    cursorColor = VortexPurple
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .testTag("search_input_field")
            )
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VortexPurple,
                    containerColor = VortexDarkCard
                )
            )

            FilterChip(
                selected = selectedFilter == MediaType.MOVIE,
                onClick = { selectedFilter = if (selectedFilter == MediaType.MOVIE) null else MediaType.MOVIE },
                label = { Text("Movies") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VortexPurple,
                    containerColor = VortexDarkCard
                )
            )

            FilterChip(
                selected = selectedFilter == MediaType.TV,
                onClick = { selectedFilter = if (selectedFilter == MediaType.TV) null else MediaType.TV },
                label = { Text("TV Series") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VortexPurple,
                    containerColor = VortexDarkCard
                )
            )
        }

        // Search Content
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
                    CircularProgressIndicator(color = VortexPurple)
                }
            } else if (query.trim().length >= 2 && filteredResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No titles found for '$query'",
                            style = MaterialTheme.typography.titleMedium.copy(color = VortexTextSecondary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching with different keywords or title names",
                            style = MaterialTheme.typography.bodySmall.copy(color = VortexTextMuted)
                        )
                    }
                }
            } else if (query.trim().isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VortexPurple.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Discover on Vortex",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = VortexTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Explore millions of movies, series, and anime with instant streaming sources.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VortexTextSecondary
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredResults,
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
