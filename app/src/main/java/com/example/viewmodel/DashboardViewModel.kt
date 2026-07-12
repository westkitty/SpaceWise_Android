package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.AdvisorySuggestion
import com.example.data.models.LargeRedundantTempFile
import com.example.data.models.SmartCleanItem
import com.example.data.models.StorageSnapshot
import com.example.data.models.StorageTrendPoint
import com.example.data.models.SuggestionType
import com.example.data.repository.StorageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: StorageStatsRepository) : ViewModel() {

    private val _snapshot = MutableStateFlow<StorageSnapshot?>(null)
    val snapshot: StateFlow<StorageSnapshot?> = _snapshot.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AdvisorySuggestion>>(emptyList())
    val suggestions: StateFlow<List<AdvisorySuggestion>> = _suggestions.asStateFlow()

    private val _storageTrends = MutableStateFlow<List<StorageTrendPoint>>(emptyList())
    val storageTrends: StateFlow<List<StorageTrendPoint>> = _storageTrends.asStateFlow()

    private val _smartCleanItems = MutableStateFlow<List<SmartCleanItem>>(emptyList())
    val smartCleanItems: StateFlow<List<SmartCleanItem>> = _smartCleanItems.asStateFlow()

    private val _largeRedundantTempFiles = MutableStateFlow<List<LargeRedundantTempFile>>(emptyList())
    val largeRedundantTempFiles: StateFlow<List<LargeRedundantTempFile>> = _largeRedundantTempFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun refreshStorageData() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _snapshot.value = repository.getStorageSnapshot()

                // Historical trends are intentionally disabled until real snapshots are persisted.
                _storageTrends.value = emptyList()

                // These lists previously displayed hard-coded example files. Empty is more honest
                // than presenting demo content as if it came from the user's device.
                _smartCleanItems.value = emptyList()
                _largeRedundantTempFiles.value = emptyList()

                _suggestions.value = buildMeasuredSuggestions()
            } catch (e: Exception) {
                _snapshot.value = null
                _suggestions.value = emptyList()
                _storageTrends.value = emptyList()
                _smartCleanItems.value = emptyList()
                _largeRedundantTempFiles.value = emptyList()
                _errorMessage.value = "Storage data could not be read. Check permissions and try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun buildMeasuredSuggestions(): List<AdvisorySuggestion> {
        val suggestions = mutableListOf<AdvisorySuggestion>()

        val topApps = repository.getTopAppsByStorageSize(limit = 30)
        val rarelyUsed = topApps
            .filter {
                it.lastUsedTimestamp != null &&
                    it.lastUsedTimestamp < System.currentTimeMillis() - FOURTEEN_DAYS_MS
            }
            .maxByOrNull { it.sizeBytes }

        if (rarelyUsed != null && rarelyUsed.sizeBytes > 50_000_000L) {
            suggestions += AdvisorySuggestion(
                id = "unused_app_${rarelyUsed.packageName}",
                title = "Review unused app: ${rarelyUsed.appName}",
                description = "Android usage history indicates this app has not been opened in over two weeks.",
                type = SuggestionType.UNUSED_APP,
                potentialSavingsBytes = rarelyUsed.sizeBytes,
                actionText = "Review App",
                targetCategory = "Apps"
            )
        }

        val largestVideo = repository.getMediaItemsForCategory("Videos", limit = 1).firstOrNull()
        if (largestVideo != null && largestVideo.sizeBytes > 20_000_000L) {
            suggestions += AdvisorySuggestion(
                id = "large_video_${largestVideo.id}",
                title = "Review large video",
                description = "${largestVideo.displayName} is one of the largest videos visible to SpaceWise.",
                type = SuggestionType.LARGE_VIDEO,
                potentialSavingsBytes = largestVideo.sizeBytes,
                actionText = "Review Video",
                targetCategory = "Videos"
            )
        }

        val largestDownload = repository
            .getMediaItemsForCategory("Downloads & Documents", limit = 1)
            .firstOrNull()
        if (largestDownload != null && largestDownload.sizeBytes > 8_000_000L) {
            suggestions += AdvisorySuggestion(
                id = "large_download_${largestDownload.id}",
                title = "Review large download",
                description = "${largestDownload.displayName} is one of the largest downloads visible to SpaceWise.",
                type = SuggestionType.LARGE_DOCUMENT,
                potentialSavingsBytes = largestDownload.sizeBytes,
                actionText = "Review Download",
                targetCategory = "Downloads & Documents"
            )
        }

        return suggestions
    }

    /**
     * Legacy demo-clean methods remain callable by old UI wiring, but they deliberately perform no
     * mutation because the underlying entries are not verified device files.
     */
    fun deleteSmartCleanItems(items: List<SmartCleanItem>) {
        if (items.isNotEmpty()) {
            _errorMessage.value = "Demo cleanup entries are disabled because they are not verified device files."
        }
    }

    fun deleteLargeRedundantTempFiles(items: List<LargeRedundantTempFile>) {
        if (items.isNotEmpty()) {
            _errorMessage.value = "Unverified cleanup entries cannot be deleted."
        }
    }

    companion object {
        private const val FOURTEEN_DAYS_MS = 14L * 24L * 60L * 60L * 1000L
    }
}