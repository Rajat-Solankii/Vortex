package com.example.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.VortexDatabase
import com.example.data.remote.ApiClient
import com.example.data.repository.VortexRepository
import com.example.ui.screens.details.DetailsViewModel
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.bookmarks.BookmarksViewModel

class AppContainer(private val context: Context) {

    private val database: VortexDatabase by lazy {
        VortexDatabase.getInstance(context)
    }

    val repository: VortexRepository by lazy {
        VortexRepository(
            apiService = ApiClient.vortexService,
            watchHistoryDao = database.watchHistoryDao(),
            bookmarkDao = database.bookmarkDao(),
            cachedHomeItemDao = database.cachedHomeItemDao(),
            cachedProviderDao = database.cachedProviderDao()
        )
    }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                    HomeViewModel(repository) as T
                }
                modelClass.isAssignableFrom(DetailsViewModel::class.java) -> {
                    DetailsViewModel(repository) as T
                }
                modelClass.isAssignableFrom(BookmarksViewModel::class.java) -> {
                    BookmarksViewModel(repository) as T
                }
                modelClass.isAssignableFrom(com.example.ui.screens.home.CategoryPagingViewModel::class.java) -> {
                    com.example.ui.screens.home.CategoryPagingViewModel(repository) as T
                }
                modelClass.isAssignableFrom(com.example.ui.screens.home.ProviderPagingViewModel::class.java) -> {
                    com.example.ui.screens.home.ProviderPagingViewModel(repository) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
