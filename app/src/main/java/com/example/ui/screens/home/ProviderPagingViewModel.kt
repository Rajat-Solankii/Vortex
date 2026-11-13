package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaItem
import com.example.data.repository.VortexRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProviderPagingViewModel(
    private val repository: VortexRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryPagingState())
    val state: StateFlow<CategoryPagingState> = _state.asStateFlow()

    private var currentProviderId: Int? = null

    fun initialize(providerId: Int) {
        if (currentProviderId == providerId) return
        currentProviderId = providerId
        _state.value = CategoryPagingState()
        loadNextPage()
    }

    fun loadNextPage() {
        val currentState = _state.value
        if (currentState.isLoading || currentState.isEndOfPaginationReached || currentProviderId == null) return

        _state.value = currentState.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val page = currentState.page
            
            val result = repository.getMediaForProvider(currentProviderId!!, page)

            result.onSuccess { newItems ->
                val existingIds = currentState.items.map { it.id }.toSet()
                val uniqueNewItems = newItems.filter { it.id !in existingIds }
                _state.value = currentState.copy(
                    items = currentState.items + uniqueNewItems,
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
