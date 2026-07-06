package com.example.data.models

data class SmartCleanItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val filePath: String,
    val type: SmartCleanType
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(sizeBytes)
}

enum class SmartCleanType {
    DUPLICATE,
    CACHE
}
