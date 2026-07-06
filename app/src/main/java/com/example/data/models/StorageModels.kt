package com.example.data.models

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color

data class CategoryStorage(
    val name: String,
    val bytes: Long,
    val color: Color,
    val percentage: Float
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(bytes)
}

data class StorageSnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val categories: List<CategoryStorage>
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) {
            ((usedBytes.toFloat() / totalBytes.toFloat()) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
}

data class AppStorageInfo(
    val packageName: String,
    val appName: String,
    val sizeBytes: Long,
    val lastUsedTimestamp: Long?,
    val icon: Drawable? = null
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(sizeBytes)
}

data class LargeRedundantTempFile(
    val id: String,
    val name: String,
    val category: String, // e.g. "Large File", "Redundant File", "Temporary File"
    val sizeBytes: Long,
    val filePath: String,
    val description: String
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(sizeBytes)
}

