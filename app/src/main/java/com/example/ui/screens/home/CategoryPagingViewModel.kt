package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaItem
import com.example.data.repository.VortexRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryPagingState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val isEndOfPaginationReached: Boolean = false
)

class CategoryPagingViewModel(
    private val repository: VortexRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryPagingState())
    val state: StateFlow<CategoryPagingState> = _state.asStateFlow()

    private var currentCategory: HomeCategory? = null

    fun initialize(category: HomeCategory) {
        if (currentCategory == category) return
        currentCategory = category
        _state.value = CategoryPagingState() // reset
        loadNextPage()
    }

    fun loadNextPage() {
        val currentState = _state.value
        if (currentState.isLoading || currentState.isEndOfPaginationReached || currentCategory == null) return

        _state.value = currentState.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val page = currentState.page
            val result = when (currentCategory!!) {
                HomeCategory.ANIME -> repository.getAnime(page)
                HomeCategory.BOLLYWOOD -> repository.getBollywood(page)
                HomeCategory.CARTOONS -> repository.getCartoons(page)
                HomeCategory.DRAMA -> repository.getDrama(page)
                HomeCategory.HOLLYWOOD -> repository.getHollywood(page)
                HomeCategory.MOVIES -> repository.getMovies(page)
                HomeCategory.TV_SHOWS -> repository.getTvShows(page)
                HomeCategory.ALL -> repository.getTrending(page)
            }

            result.onSuccess { newItems ->
                _state.value = currentState.copy(
                    items = currentState.items + newItems,
                    isLoading = false,
                    page = page + 1,
                    isEndOfPaginationReached = newItems.isEmpty()
                )
            }.onFailure {
                _state.value = currentState.copy(
                    isLoading = false,
                    error = it.localizedMessage ?: "Failed to fetch data"
                )
            }
        }
    }
}
