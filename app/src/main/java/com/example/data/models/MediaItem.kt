package com.example.data.models

import java.util.Locale

data class MediaItem(
    val id: Long,
    val displayName: String,
    val sizeBytes: Long,
    val dateAdded: Long, // seconds since epoch
    val mimeType: String?,
    val uriString: String?,
    val sourceCollection: String = "Unknown MediaStore collection",
    val categoryReason: String = "Matched the selected storage category.",
    val confidence: ConfidenceLevel = ConfidenceLevel.EXACT,
    val durationMs: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val partialHash: String? = null
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(sizeBytes)

    val formattedDate: String
        get() = ByteFormatting.formatDate(dateAdded)

    val fileKind: MediaKind
        get() = MediaKind.fromMime(mimeType)

    val formattedDuration: String?
        get() = durationMs?.takeIf { it > 0L }?.let { millis ->
            val totalSeconds = millis / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }

    val formattedResolution: String?
        get() = if (widthPx != null && heightPx != null && widthPx > 0 && heightPx > 0) {
            "${widthPx}x${heightPx}"
        } else {
            null
        }
}

enum class MediaKind(val label: String) {
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    ARCHIVE("Archives"),
    OTHER("Other");

    companion object {
        fun fromMime(mimeType: String?): MediaKind {
            val mime = mimeType?.lowercase(Locale.ROOT) ?: return OTHER
            return when {
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                mime.startsWith("text/") || mime.startsWith("application/pdf") || mime.contains("document") -> DOCUMENT
                mime.contains("zip") || mime.contains("archive") || mime.contains("compressed") -> ARCHIVE
                else -> OTHER
            }
        }
    }
}

enum class ConfidenceLevel(val label: String, val explanation: String) {
    EXACT("Exact", "Read directly from Android media/app storage APIs."),
    ESTIMATED("Estimated", "Calculated from available Android storage signals."),
    INACCESSIBLE("Inaccessible", "Android sandbox rules prevent direct inspection."),
    RESIDUAL("Residual", "Computed from used space that is not visible to this app.")
}

data class ScanAuditInfo(
    val sessionId: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val stage: ScanStage = ScanStage.IDLE,
    val progressPercent: Int = 0,
    val mediaPermissionGranted: Boolean = false,
    val usagePermissionGranted: Boolean = false,
    val note: String = "MediaStore scan only. No private app data is uploaded or inspected."
) {
    val durationMillis: Long?
        get() = finishedAtMillis?.let { (it - startedAtMillis).coerceAtLeast(0L) }

    val isStale: Boolean
        get() = finishedAtMillis?.let { System.currentTimeMillis() - it > 15L * 60L * 1000L } ?: false

    val permissionSignature: String
        get() = "media=$mediaPermissionGranted;usage=$usagePermissionGranted"
}

enum class ScanStage(val label: String) {
    IDLE("Idle"),
    PERMISSIONS("Checking permissions"),
    MEDIA_QUERY("Querying MediaStore"),
    CATEGORY_GROUPING("Grouping categories"),
    METADATA_ENRICHMENT("Enriching metadata"),
    SUMMARY("Preparing summary"),
    COMPLETE("Complete"),
    CANCELLED("Cancelled"),
    FAILED("Failed")
}

enum class MediaSortMode(val label: String) {
    LARGEST("Largest first"),
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name A-Z")
}

data class CleanupResult(
    val item: MediaItem,
    val status: CleanupStatus,
    val message: String
)

enum class CleanupStatus {
    PROCESSED,
    SKIPPED,
    PERMISSION_DENIED,
    MISSING,
    FAILED
}
