package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.MediaItem
import com.example.data.repository.VortexRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeCategory(val title: String) {
    ALL("All"),
    ANIME("Anime"),
    BOLLYWOOD("Bollywood"),
    CARTOONS("Cartoons"),
    DRAMA("Drama"),
    HOLLYWOOD("Hollywood"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows")
}

data class HomeFeedState(
    val isLoading: Boolean = true,
    val selectedCategory: HomeCategory = HomeCategory.ALL,
    val featuredHero: MediaItem? = null,
    val recommendedSliders: List<MediaItem> = emptyList(),
    val trending: List<MediaItem> = emptyList(),
    val anime: List<MediaItem> = emptyList(),
    val bollywood: List<MediaItem> = emptyList(),
    val cartoons: List<MediaItem> = emptyList(),
    val drama: List<MediaItem> = emptyList(),
    val hollywood: List<MediaItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val tvShows: List<MediaItem> = emptyList(),
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: VortexRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow(HomeFeedState())
    val feedState: StateFlow<HomeFeedState> = _feedState.asStateFlow()

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadHomeFeed()
    }

    fun selectCategory(category: HomeCategory) {
        _feedState.value = _feedState.value.copy(selectedCategory = category)
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _feedState.value = _feedState.value.copy(isLoading = true, errorMessage = null)

            val trendingResult = repository.getTrending()
            val animeResult = repository.getAnime()
            val bollywoodResult = repository.getBollywood()
            val cartoonsResult = repository.getCartoons()
            val dramaResult = repository.getDrama()
            val hollywoodResult = repository.getHollywood()
            val moviesResult = repository.getMovies()
            val tvResult = repository.getTvShows()

            val trendingList = trendingResult.getOrDefault(emptyList())
            val animeList = animeResult.getOrDefault(emptyList())
            val bollywoodList = bollywoodResult.getOrDefault(emptyList())
            val cartoonsList = cartoonsResult.getOrDefault(emptyList())
            val dramaList = dramaResult.getOrDefault(emptyList())
            val hollywoodList = hollywoodResult.getOrDefault(emptyList())
            val moviesList = moviesResult.getOrDefault(emptyList())
            val tvList = tvResult.getOrDefault(emptyList())

            val sliderItems = (trendingList + moviesList + hollywoodList).distinctBy { it.id }.take(6)
            val hero = sliderItems.firstOrNull() ?: trendingList.firstOrNull()

            _feedState.value = HomeFeedState(
                isLoading = false,
                selectedCategory = _feedState.value.selectedCategory,
                featuredHero = hero,
                recommendedSliders = sliderItems,
                trending = trendingList,
                anime = animeList,
                bollywood = bollywoodList,
                cartoons = cartoonsList,
                drama = dramaList,
                hollywood = hollywoodList,
                movies = moviesList,
                tvShows = tvList,
                errorMessage = null
            )
        }
    }

    fun removeWatchHistory(id: Int) {
        viewModelScope.launch {
            repository.deleteWatchHistory(id)
        }
    }

    fun clearAllWatchHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
        }
    }
}
