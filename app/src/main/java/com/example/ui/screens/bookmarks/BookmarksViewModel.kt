package com.example.ui.screens.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Bookmark
import com.example.data.repository.VortexRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookmarksViewModel(
    private val repository: VortexRepository
) : ViewModel() {

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            repository.bookmarksFlow.collect { localList ->
                _bookmarks.value = localList
                _isLoading.value = false
            }
        }
        loadBookmarks()
    }

    fun loadBookmarks() {
        viewModelScope.launch {
            val list = repository.getBookmarks()
            if (list.isNotEmpty()) {
                _bookmarks.value = list
            }
            _isLoading.value = false
        }
    }

    fun removeBookmark(mediaId: String) {
        viewModelScope.launch {
            val success = repository.removeBookmark(mediaId)
            if (success) {
                _bookmarks.value = _bookmarks.value.filter { it.mediaId != mediaId }
            }
        }
    }
}
