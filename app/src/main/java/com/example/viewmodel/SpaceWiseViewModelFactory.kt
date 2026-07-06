package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.StorageStatsRepository

class SpaceWiseViewModelFactory(private val repository: StorageStatsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(repository) as T
            }
            modelClass.isAssignableFrom(BreakdownViewModel::class.java) -> {
                BreakdownViewModel(repository) as T
            }
            modelClass.isAssignableFrom(CategoryDetailViewModel::class.java) -> {
                CategoryDetailViewModel(repository) as T
            }
            modelClass.isAssignableFrom(AppListViewModel::class.java) -> {
                AppListViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
