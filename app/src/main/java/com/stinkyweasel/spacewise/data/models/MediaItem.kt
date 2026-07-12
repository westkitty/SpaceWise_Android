package com.stinkyweasel.spacewise.data.models

data class MediaItem(
    val id: Long,
    val displayName: String,
    val sizeBytes: Long,
    val dateAdded: Long, // seconds since epoch
    val mimeType: String?,
    val uriString: String?
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(sizeBytes)
        
    val formattedDate: String
        get() = ByteFormatting.formatDate(dateAdded)
}
