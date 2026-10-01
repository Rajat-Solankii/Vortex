package com.example.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.VortexDatabase
import com.example.data.remote.ApiClient
import com.example.data.repository.VortexRepository
import com.example.ui.screens.details.DetailsViewModel
import com.example.ui.screens.home.HomeViewModel

class AppContainer(private val context: Context) {

    private val database: VortexDatabase by lazy {
        VortexDatabase.getInstance(context)
    }

    val repository: VortexRepository by lazy {
        VortexRepository(
            apiService = ApiClient.vortexService,
            watchHistoryDao = database.watchHistoryDao()
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
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
