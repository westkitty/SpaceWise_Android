package com.stinkyweasel.spacewise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stinkyweasel.spacewise.data.models.CategoryAccessState
import com.stinkyweasel.spacewise.data.models.DeletionResult
import com.stinkyweasel.spacewise.data.models.MediaItem
import com.stinkyweasel.spacewise.data.repository.StorageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryDetailViewModel(private val repository: StorageStatsRepository) : ViewModel() {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _accessState = MutableStateFlow(CategoryAccessState.AVAILABLE)
    val accessState: StateFlow<CategoryAccessState> = _accessState.asStateFlow()

    private var activeCategory: String? = null

    fun loadMediaForCategory(categoryName: String) {
        activeCategory = categoryName
        viewModelScope.launch {
            _isLoading.value = true
            _accessState.value = repository.getCategoryAccessState(categoryName)
            try {
                _mediaItems.value = when (_accessState.value) {
                    CategoryAccessState.AVAILABLE,
                    CategoryAccessState.PARTIAL -> repository.getMediaItemsForCategory(categoryName)
                    else -> emptyList()
                }
            } catch (e: SecurityException) {
                _mediaItems.value = emptyList()
                _accessState.value = CategoryAccessState.PERMISSION_REQUIRED
            } catch (e: Exception) {
                _mediaItems.value = emptyList()
                _accessState.value = CategoryAccessState.QUERY_FAILED
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSelectedItems(selectedIds: Set<Long>, onComplete: (DeletionResult) -> Unit) {
        viewModelScope.launch {
            val category = activeCategory
            val before = _mediaItems.value
            val requested = before.filter { it.id in selectedIds }
            if (category == null || requested.isEmpty()) {
                onComplete(DeletionResult(0, 0, 0, 0, 0L))
                return@launch
            }

            _isLoading.value = true
            try {
                repository.deleteMediaItems(requested)
                val after = repository.getMediaItemsForCategory(category)
                val remainingUris = after.mapNotNull { it.uriString }.toSet()
                val verifiedDeleted = requested.filter { item ->
                    item.uriString != null && item.uriString !in remainingUris
                }
                val skipped = requested.count { it.uriString == null }
                val stillPresent = requested.size - verifiedDeleted.size - skipped
                _mediaItems.value = after
                onComplete(
                    DeletionResult(
                        requestedCount = requested.size,
                        verifiedDeletedCount = verifiedDeleted.size,
                        stillPresentCount = stillPresent.coerceAtLeast(0),
                        skippedCount = skipped,
                        verifiedReclaimedBytes = verifiedDeleted.sumOf { it.sizeBytes },
                        confirmationMayBeRequired = stillPresent > 0
                    )
                )
            } catch (e: Exception) {
                onComplete(
                    DeletionResult(
                        requestedCount = requested.size,
                        verifiedDeletedCount = 0,
                        stillPresentCount = requested.size,
                        skippedCount = 0,
                        verifiedReclaimedBytes = 0L,
                        confirmationMayBeRequired = true
                    )
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
