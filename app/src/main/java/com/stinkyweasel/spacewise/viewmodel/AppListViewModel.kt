package com.stinkyweasel.spacewise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stinkyweasel.spacewise.data.models.AppStorageInfo
import com.stinkyweasel.spacewise.data.repository.StorageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppListViewModel(private val repository: StorageStatsRepository) : ViewModel() {

    private val _appsList = MutableStateFlow<List<AppStorageInfo>>(emptyList())
    val appsList: StateFlow<List<AppStorageInfo>> = _appsList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadApps() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _appsList.value = repository.getTopAppsByStorageSize()
            } catch (e: Exception) {
                _appsList.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
