package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.MediaItem
import com.example.data.repository.StorageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryDetailViewModel(private val repository: StorageStatsRepository) : ViewModel() {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadMediaForCategory(categoryName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _mediaItems.value = repository.getMediaItemsForCategory(categoryName)
            } catch (e: Exception) {
                _mediaItems.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSelectedItems(selectedIds: Set<Long>, onComplete: (Boolean, Long) -> Unit) {
        viewModelScope.launch {
            val currentList = _mediaItems.value
            val itemsToDelete = currentList.filter { it.id in selectedIds }
            if (itemsToDelete.isEmpty()) {
                onComplete(false, 0L)
                return@launch
            }

            val totalBytesReclaimed = itemsToDelete.sumOf { it.sizeBytes }
            try {
                // Call the repository to securely delete from filesystem/MediaStore
                repository.deleteMediaItems(itemsToDelete)
                
                // Dynamically update state so they vanish immediately
                _mediaItems.value = currentList.filter { it.id !in selectedIds }
                onComplete(true, totalBytesReclaimed)
            } catch (e: Exception) {
                onComplete(false, 0L)
            }
        }
    }
}
