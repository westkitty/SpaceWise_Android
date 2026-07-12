package com.stinkyweasel.spacewise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stinkyweasel.spacewise.data.models.CategoryStorage
import com.stinkyweasel.spacewise.data.models.StorageSnapshot
import com.stinkyweasel.spacewise.data.repository.StorageStatsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BreakdownViewModel(private val repository: StorageStatsRepository) : ViewModel() {

    private val _snapshot = MutableStateFlow<StorageSnapshot?>(null)
    val snapshot: StateFlow<StorageSnapshot?> = _snapshot.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredCategories: StateFlow<List<CategoryStorage>> = combine(_snapshot, _searchQuery) { snapshot, query ->
        val categories = snapshot?.categories ?: emptyList()
        if (query.isBlank()) {
            categories
        } else {
            categories.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadData() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _snapshot.value = repository.getStorageSnapshot()
            } catch (e: Exception) {
                _snapshot.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
