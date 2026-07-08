package com.example.data.models

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color

data class CategoryStorage(
    val name: String,
    val bytes: Long,
    val color: Color,
    val percentage: Float,
    val confidence: ConfidenceLevel = ConfidenceLevel.ESTIMATED,
    val explanation: String = "Calculated from Android storage signals available to SpaceWise."
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(bytes)
}

data class StorageSnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val categories: List<CategoryStorage>,
    val auditInfo: ScanAuditInfo? = null
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
    val category: String,
    val sizeBytes: Long,
    val filePath: String,
    val description: String,
    val isDemoData: Boolean = true,
    val evidence: String = "Demo-only placeholder. Must not be treated as a verified real device file."
) {
    val formattedSize: String
        get() = ByteFormatting.formatByteCount(sizeBytes)
}

data class ReleaseReadinessCheck(
    val name: String,
    val passed: Boolean,
    val detail: String
)

object SpaceWiseBuildInfo {
    const val VERSION_NAME = "0.2-auditability"
    const val BUILD_CHANNEL = "debug"
    const val APK_VERIFICATION_POLICY = "APK must be produced by CI, unzip-tested, and apksigner-verified before sharing."

    val releaseChecklist = listOf(
        ReleaseReadinessCheck("Install test", false, "Install the generated APK on a real Android device."),
        ReleaseReadinessCheck("Media open test", false, "Tap image, video, audio, and document rows to confirm native viewers launch."),
        ReleaseReadinessCheck("Permission test", false, "Verify denied, partial, and granted permission states."),
        ReleaseReadinessCheck("Cleanup dry-run test", false, "Review item summary before action and inspect per-item results."),
        ReleaseReadinessCheck("APK signature test", true, APK_VERIFICATION_POLICY)
    )
}
