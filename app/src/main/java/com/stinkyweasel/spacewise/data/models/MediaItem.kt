package com.stinkyweasel.spacewise.data.models

data class MediaItem(
    val id: Long,
    val displayName: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val mimeType: String?,
    val uriString: String?
) {
    val selectionKey: String
        get() = uriString ?: "media:$id:$displayName"

    val formattedSize: String
        get() = ByteFormatting.formatByteCount(sizeBytes)

    val formattedDate: String
        get() = ByteFormatting.formatDate(dateAdded)
}
