package com.example.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.EpisodeItem
import com.example.data.model.MediaDetail
import com.example.data.model.MediaType
import com.example.data.repository.VortexRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TvSeasonState(
    val selectedSeasonNumber: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val isLoadingEpisodes: Boolean = false
)

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(
        val detail: MediaDetail,
        val seasonState: TvSeasonState = TvSeasonState()
    ) : DetailsUiState
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
                    if (mediaType == MediaType.TV && detail.seasons.isNotEmpty()) {
                        val initialSeason = detail.seasons.first().seasonNumber
                        _uiState.value = DetailsUiState.Success(
                            detail = detail,
                            seasonState = TvSeasonState(
                                selectedSeasonNumber = initialSeason,
                                isLoadingEpisodes = true
                            )
                        )
                        loadEpisodes(mediaId, initialSeason)
                    } else {
                        _uiState.value = DetailsUiState.Success(detail = detail)
                    }
                },
                onFailure = { error ->
                    _uiState.value = DetailsUiState.Error(error.localizedMessage ?: "Failed to load details")
                }
            )
        }
    }

    fun selectSeason(tvId: Int, seasonNumber: Int) {
        val currentState = _uiState.value
        if (currentState is DetailsUiState.Success) {
            _uiState.value = currentState.copy(
                seasonState = currentState.seasonState.copy(
                    selectedSeasonNumber = seasonNumber,
                    isLoadingEpisodes = true
                )
            )
            loadEpisodes(tvId, seasonNumber)
        }
    }

    private fun loadEpisodes(tvId: Int, seasonNumber: Int) {
        viewModelScope.launch {
            val episodesResult = repository.getSeasonEpisodes(tvId, seasonNumber)
            val currentState = _uiState.value
            if (currentState is DetailsUiState.Success) {
                _uiState.value = currentState.copy(
                    seasonState = currentState.seasonState.copy(
                        selectedSeasonNumber = seasonNumber,
                        episodes = episodesResult.getOrDefault(emptyList()),
                        isLoadingEpisodes = false
                    )
                )
            }
        }
    }
}
