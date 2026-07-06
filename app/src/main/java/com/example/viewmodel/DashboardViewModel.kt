package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.AdvisorySuggestion
import com.example.data.models.StorageSnapshot
import com.example.data.models.SuggestionType
import com.example.data.models.StorageTrendPoint
import com.example.data.models.SmartCleanItem
import com.example.data.models.SmartCleanType
import com.example.data.models.LargeRedundantTempFile
import com.example.data.repository.StorageStatsRepository
import kotlinx.coroutines.delay
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

    fun refreshStorageData() {
        if (_isLoading.value) return // Prevent concurrent duplicate heavy storage refreshes
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Add an intentional slight scanning delay so the global progress bar/spinner is beautifully visible
                delay(1200)
                val snapshotResult = repository.getStorageSnapshot()
                _snapshot.value = snapshotResult
                
                // Fetch trends and Smart Clean list
                _storageTrends.value = repository.getStorageTrends()
                _smartCleanItems.value = repository.getSmartCleanItems()
                _largeRedundantTempFiles.value = repository.getLargeRedundantTempFiles()
                
                // Generate live recommendations based on filesystem scan
                val suggestionsList = mutableListOf<AdvisorySuggestion>()
                
                // 1. App Cache & Unused Apps Recommendation
                val topApps = repository.getTopAppsByStorageSize(limit = 10)
                if (topApps.isNotEmpty()) {
                    // Check for particularly large apps
                    val largestApp = topApps.first()
                    if (largestApp.sizeBytes > 150_000_000L) { // > 150MB
                        suggestionsList.add(
                            AdvisorySuggestion(
                                id = "cache_cleanup_${largestApp.packageName}",
                                title = "Clear Cache for ${largestApp.appName}",
                                description = "Temporary cached files take up space. Clear them in Settings without losing personal data.",
                                type = SuggestionType.CACHE_CLEANUP,
                                potentialSavingsBytes = (largestApp.sizeBytes * 0.25).toLong(), // Est. 25% cache
                                actionText = "Manage App",
                                targetCategory = "Apps"
                            )
                        )
                    }
                    
                    // Check for rarely used apps (unused in last 14 days or never tracked)
                    val rarelyUsed = topApps.filter { 
                        it.lastUsedTimestamp == null || 
                        it.lastUsedTimestamp < System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
                    }
                    if (rarelyUsed.isNotEmpty()) {
                        val appToSuggest = rarelyUsed.maxByOrNull { it.sizeBytes }
                        if (appToSuggest != null && appToSuggest.sizeBytes > 50_000_000L) {
                            suggestionsList.add(
                                AdvisorySuggestion(
                                    id = "unused_app_${appToSuggest.packageName}",
                                    title = "Offload Unused App: ${appToSuggest.appName}",
                                    description = "This application hasn't been opened in over two weeks. Offload it to reclaim space.",
                                    type = SuggestionType.UNUSED_APP,
                                    potentialSavingsBytes = appToSuggest.sizeBytes,
                                    actionText = "Review Apps",
                                    targetCategory = "Apps"
                                )
                            )
                        }
                    }
                }
                
                // 2. Large Videos Suggestion
                val videos = repository.getMediaItemsForCategory("Videos", limit = 5)
                if (videos.isNotEmpty()) {
                    val largestVideo = videos.maxByOrNull { it.sizeBytes }
                    if (largestVideo != null && largestVideo.sizeBytes > 20_000_000L) { // > 20MB
                        suggestionsList.add(
                            AdvisorySuggestion(
                                id = "large_video_${largestVideo.id}",
                                title = "Large Video Found",
                                description = "\"${largestVideo.displayName}\" takes up substantial space. Reclaim bytes by deleting it.",
                                type = SuggestionType.LARGE_VIDEO,
                                potentialSavingsBytes = largestVideo.sizeBytes,
                                actionText = "Review Videos",
                                targetCategory = "Videos"
                            )
                        )
                    }
                }
                
                // 3. Large Documents/Downloads Suggestion
                val docs = repository.getMediaItemsForCategory("Downloads & Documents", limit = 5)
                if (docs.isNotEmpty()) {
                    val largestDoc = docs.maxByOrNull { it.sizeBytes }
                    if (largestDoc != null && largestDoc.sizeBytes > 8_000_000L) { // > 8MB
                        suggestionsList.add(
                            AdvisorySuggestion(
                                id = "large_doc_${largestDoc.id}",
                                title = "Review Bulk Download",
                                description = "\"${largestDoc.displayName}\" was saved to Downloads. Delete if no longer needed.",
                                type = SuggestionType.LARGE_DOCUMENT,
                                potentialSavingsBytes = largestDoc.sizeBytes,
                                actionText = "Review Downloads",
                                targetCategory = "Downloads & Documents"
                            )
                        )
                    }
                }
                
                // Guard: Provide rich static cache suggestions if local sandbox is empty (e.g. fresh simulator run)
                if (suggestionsList.size < 2) {
                    suggestionsList.add(
                        AdvisorySuggestion(
                            id = "general_cache_advice",
                            title = "Clear Device Application Cache",
                            description = "Browser and streaming apps gather temporary cached images and scripts over time.",
                            type = SuggestionType.CACHE_CLEANUP,
                            potentialSavingsBytes = 350_000_000L,
                            actionText = "Manage Apps",
                            targetCategory = "Apps"
                        )
                    )
                    suggestionsList.add(
                        AdvisorySuggestion(
                            id = "general_system_advice",
                            title = "Review Large Downloads",
                            description = "Inspect your downloads folder for redundant installers, old PDF sheets, and zip folders.",
                            type = SuggestionType.SYSTEM_RECOMMENDATION,
                            potentialSavingsBytes = 120_000_000L,
                            actionText = "View Downloads",
                            targetCategory = "Downloads & Documents"
                        )
                    )
                }
                
                _suggestions.value = suggestionsList
            } catch (e: Exception) {
                _snapshot.value = null
                _suggestions.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSmartCleanItems(items: List<SmartCleanItem>) {
        viewModelScope.launch {
            val ids = items.map { it.id }.toSet()
            repository.markSmartCleanItemsDeleted(ids)
            
            // Reclaim space based on file type
            items.forEach { item ->
                val cat = when (item.type) {
                    SmartCleanType.DUPLICATE -> {
                        if (item.name.endsWith(".mp4")) "Videos"
                        else if (item.name.endsWith(".jpg") || item.name.endsWith(".png")) "Photos"
                        else "Downloads & Documents"
                    }
                    SmartCleanType.CACHE -> "Apps & Games"
                }
                repository.addReclaimedByCategory(cat, item.sizeBytes)
            }
            
            // Refresh data to instantly show the reclaimed space and updated trend line!
            refreshStorageData()
        }
    }

    fun deleteLargeRedundantTempFiles(items: List<LargeRedundantTempFile>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Add delay for realism during deletion and storage re-indexing
                delay(1000)
                val ids = items.map { it.id }.toSet()
                repository.markLargeRedundantTempItemsDeleted(ids)
                
                // Reclaim space based on file category
                items.forEach { item ->
                    val cat = when (item.category) {
                        "Large File" -> "Downloads & Documents"
                        "Redundant File" -> "Photos"
                        else -> "Apps & Games"
                    }
                    repository.addReclaimedByCategory(cat, item.sizeBytes)
                }
                
                // Refresh data to instantly show the reclaimed space and updated trend line!
                refreshStorageData()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
