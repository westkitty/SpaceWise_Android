package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.CleanupResult
import com.example.data.models.ConfidenceLevel
import com.example.data.models.MediaItem
import com.example.data.models.MediaKind
import com.example.data.models.MediaSortMode
import com.example.data.models.ScanAuditInfo
import com.example.data.models.ScanStage
import com.example.data.repository.StorageStatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryDetailViewModel(private val repository: StorageStatsRepository) : ViewModel() {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _visibleItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val visibleItems: StateFlow<List<MediaItem>> = _visibleItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _scanAudit = MutableStateFlow<ScanAuditInfo?>(null)
    val scanAudit: StateFlow<ScanAuditInfo?> = _scanAudit.asStateFlow()

    private val _lastCleanupResults = MutableStateFlow<List<CleanupResult>>(emptyList())
    val lastCleanupResults: StateFlow<List<CleanupResult>> = _lastCleanupResults.asStateFlow()

    private var loadJob: Job? = null
    private var query: String = ""
    private var kindFilter: MediaKind? = null
    private var sortMode: MediaSortMode = MediaSortMode.LARGEST

    fun loadMediaForCategory(categoryName: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _lastCleanupResults.value = emptyList()
            val started = System.currentTimeMillis()
            val sessionId = "scan-${started.toString(36)}"
            _scanAudit.value = ScanAuditInfo(
                sessionId = sessionId,
                startedAtMillis = started,
                stage = ScanStage.PERMISSIONS,
                progressPercent = 10,
                mediaPermissionGranted = repository.hasMediaPermission(),
                usagePermissionGranted = repository.hasUsagePermission()
            )
            try {
                _scanAudit.value = _scanAudit.value?.copy(stage = ScanStage.MEDIA_QUERY, progressPercent = 35)
                val loaded = repository.getMediaItemsForCategory(categoryName)
                _scanAudit.value = _scanAudit.value?.copy(stage = ScanStage.METADATA_ENRICHMENT, progressPercent = 70)
                _mediaItems.value = loaded
                applyFilters()
                _scanAudit.value = _scanAudit.value?.copy(
                    finishedAtMillis = System.currentTimeMillis(),
                    stage = ScanStage.COMPLETE,
                    progressPercent = 100,
                    note = "Scan complete. Rows come from Android MediaStore; inaccessible app-private data is not inspected."
                )
            } catch (e: Exception) {
                _mediaItems.value = emptyList()
                _visibleItems.value = emptyList()
                _scanAudit.value = _scanAudit.value?.copy(
                    finishedAtMillis = System.currentTimeMillis(),
                    stage = ScanStage.FAILED,
                    progressPercent = 100,
                    note = "Scan failed without modifying files."
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelScan() {
        loadJob?.cancel()
        _isLoading.value = false
        _scanAudit.value = _scanAudit.value?.copy(
            finishedAtMillis = System.currentTimeMillis(),
            stage = ScanStage.CANCELLED,
            progressPercent = 100,
            confidence = ConfidenceLevel.ESTIMATED
        )
    }

    fun updateSearch(text: String) {
        query = text
        applyFilters()
    }

    fun updateKindFilter(kind: MediaKind?) {
        kindFilter = kind
        applyFilters()
    }

    fun updateSortMode(mode: MediaSortMode) {
        sortMode = mode
        applyFilters()
    }

    private fun applyFilters() {
        val filtered = _mediaItems.value
            .asSequence()
            .filter { item -> kindFilter == null || item.fileKind == kindFilter }
            .filter { item ->
                query.isBlank() || item.displayName.contains(query, ignoreCase = true) ||
                    item.mimeType?.contains(query, ignoreCase = true) == true
            }
            .let { seq ->
                when (sortMode) {
                    MediaSortMode.LARGEST -> seq.sortedByDescending { it.sizeBytes }
                    MediaSortMode.NEWEST -> seq.sortedByDescending { it.dateAdded }
                    MediaSortMode.OLDEST -> seq.sortedBy { it.dateAdded }
                    MediaSortMode.NAME -> seq.sortedBy { it.displayName.lowercase() }
                }
            }
            .toList()
        _visibleItems.value = filtered
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
                val results = repository.deleteMediaItemsWithResults(itemsToDelete)
                _lastCleanupResults.value = results
                val processedIds = results
                    .filter { it.status.name == "PROCESSED" }
                    .map { it.item.id }
                    .toSet()
                _mediaItems.value = currentList.filter { it.id !in processedIds }
                applyFilters()
                onComplete(processedIds.isNotEmpty(), if (processedIds.isNotEmpty()) totalBytesReclaimed else 0L)
            } catch (e: Exception) {
                onComplete(false, 0L)
            }
        }
    }
}
