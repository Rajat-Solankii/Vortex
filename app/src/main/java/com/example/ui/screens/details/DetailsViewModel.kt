package com.example.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaDetail
import com.example.data.model.MediaType
import com.example.data.repository.VortexRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(val detail: MediaDetail) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}

class DetailsViewModel(
    private val repository: VortexRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    fun loadDetails(mediaId: Int, mediaType: MediaType) {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            val result = if (mediaType == MediaType.MOVIE) {
                repository.getMovieDetails(mediaId)
            } else {
                repository.getTvDetails(mediaId)
            }

            result.fold(
                onSuccess = { detail ->
                    _uiState.value = DetailsUiState.Success(detail)
                },
                onFailure = { error ->
                    _uiState.value = DetailsUiState.Error(error.localizedMessage ?: "Failed to load details")
                }
            )
        }
    }
}
