package com.stinkyweasel.spacewise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stinkyweasel.spacewise.data.models.CategoryAccessState
import com.stinkyweasel.spacewise.data.models.DeletionResult
import com.stinkyweasel.spacewise.data.models.MediaItem
import com.stinkyweasel.spacewise.data.repository.StorageStatsRepository
import kotlinx.coroutines.Job
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
    private var loadJob: Job? = null

    fun loadMediaForCategory(categoryName: String) {
        activeCategory = categoryName
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _accessState.value = repository.getCategoryAccessState(categoryName)
            try {
                _mediaItems.value = when (_accessState.value) {
                    CategoryAccessState.AVAILABLE,
                    CategoryAccessState.PARTIAL -> repository.getMediaItemsForCategory(categoryName, limit = 500)
                    else -> emptyList()
                }
            } catch (_: SecurityException) {
                _mediaItems.value = emptyList()
                _accessState.value = CategoryAccessState.PERMISSION_REQUIRED
            } catch (_: Exception) {
                _mediaItems.value = emptyList()
                _accessState.value = CategoryAccessState.QUERY_FAILED
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSelectedItems(selectedKeys: Set<String>, onComplete: (DeletionResult) -> Unit) {
        viewModelScope.launch {
            val category = activeCategory
            val requested = _mediaItems.value.filter { it.selectionKey in selectedKeys }
            if (category == null || requested.isEmpty()) {
                onComplete(DeletionResult(0, 0, 0, 0, 0L))
                return@launch
            }

            _isLoading.value = true
            try {
                repository.deleteMediaItems(requested)
                val verifiedDeleted = repository.getVerifiedDeletedItems(requested)
                val skipped = requested.count { it.uriString == null }
                val stillPresentOrUnknown = requested.size - verifiedDeleted.size - skipped

                _mediaItems.value = try {
                    repository.getMediaItemsForCategory(category, limit = 500)
                } catch (_: Exception) {
                    _mediaItems.value
                }

                onComplete(
                    DeletionResult(
                        requestedCount = requested.size,
                        verifiedDeletedCount = verifiedDeleted.size,
                        stillPresentCount = stillPresentOrUnknown.coerceAtLeast(0),
                        skippedCount = skipped,
                        verifiedReclaimedBytes = verifiedDeleted.sumOf { it.sizeBytes },
                        confirmationMayBeRequired = stillPresentOrUnknown > 0
                    )
                )
            } catch (_: Exception) {
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
