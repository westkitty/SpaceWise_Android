package com.example.data.models

data class StorageTrendPoint(
    val day: Int,           // 1 to 30
    val dateLabel: String,  // e.g. "06/15"
    val usedBytes: Long
)
