package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MediaItem
import com.example.di.AppContainer
import com.example.ui.components.MediaPosterCard
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPagingScreen(
    category: HomeCategory,
    onBackClick: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    appContainer: AppContainer
) {
    val viewModel: CategoryPagingViewModel = viewModel(
        factory = appContainer.viewModelFactory
    )

    LaunchedEffect(category) {
        viewModel.initialize(category)
    }

    val state by viewModel.state.collectAsState()
    val listState = rememberLazyGridState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = category.title, color = VortexTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VortexTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VortexDarkBackground)
            )
        },
        containerColor = VortexDarkBackground
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (state.items.isEmpty() && state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VortexPurple)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, media ->
                        // Load next page logic
                        if (index >= state.items.size - 6 && !state.isLoading && !state.isEndOfPaginationReached) {
                            LaunchedEffect(index) {
                                viewModel.loadNextPage()
                            }
                        }

                        MediaPosterCard(media = media, onClick = { onMediaClick(media) }, modifier = Modifier.fillMaxWidth())
                    }
                    
                    if (state.isLoading) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = VortexPurple, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
