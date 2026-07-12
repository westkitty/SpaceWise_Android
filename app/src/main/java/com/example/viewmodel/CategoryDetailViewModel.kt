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

    private var activeCategory: String? = null

    fun loadMediaForCategory(categoryName: String) {
        activeCategory = categoryName
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
            val category = activeCategory
            val before = _mediaItems.value
            val requested = before.filter { it.id in selectedIds }

            if (category == null || requested.isEmpty()) {
                onComplete(false, 0L)
                return@launch
            }

            _isLoading.value = true
            try {
                repository.deleteMediaItems(requested)

                // Do not trust a deletion request result alone. Re-query MediaStore and only count
                // entries that are actually gone from the source of truth.
                val after = repository.getMediaItemsForCategory(category)
                val remainingUris = after.mapNotNull { it.uriString }.toSet()
                val deleted = requested.filter { item ->
                    val uri = item.uriString
                    uri != null && uri !in remainingUris
                }

                _mediaItems.value = after
                onComplete(deleted.isNotEmpty(), deleted.sumOf { it.sizeBytes })
            } catch (e: Exception) {
                onComplete(false, 0L)
            } finally {
                _isLoading.value = false
            }
        }
    }
}