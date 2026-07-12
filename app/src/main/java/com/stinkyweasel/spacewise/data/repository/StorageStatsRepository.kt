package com.stinkyweasel.spacewise.data.repository

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import com.stinkyweasel.spacewise.data.models.AppStorageInfo
import com.stinkyweasel.spacewise.data.models.CategoryAccessState
import com.stinkyweasel.spacewise.data.models.DataAvailability
import com.stinkyweasel.spacewise.data.models.DataConfidence
import com.stinkyweasel.spacewise.data.models.CategoryStorage
import com.stinkyweasel.spacewise.data.models.LargeRedundantTempFile
import com.stinkyweasel.spacewise.data.models.MediaItem
import com.stinkyweasel.spacewise.data.models.RawStorageCategory
import com.stinkyweasel.spacewise.data.models.SmartCleanItem
import com.stinkyweasel.spacewise.data.models.StorageCategoryMath
import com.stinkyweasel.spacewise.data.models.StorageSnapshot
import com.stinkyweasel.spacewise.data.models.StorageTrendPoint
import com.stinkyweasel.spacewise.utils.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageStatsRepository(private val context: Context) {

    /*
     * Compatibility methods retained for existing UI wiring. The hardened application does not
     * simulate reclaimed storage, fabricated cleanup records, or retrospective history.
     */
    fun getReclaimedBytesOffset(): Long = 0L
    fun addReclaimedBytes(bytes: Long) = Unit
    fun getReclaimedByCategory(categoryName: String): Long = 0L
    fun addReclaimedByCategory(categoryName: String, bytes: Long) = Unit
    fun getDeletedSmartCleanIds(): Set<String> = emptySet()
    fun markSmartCleanItemsDeleted(ids: Set<String>) = Unit
    fun getDeletedLargeRedundantTempIds(): Set<String> = emptySet()
    fun markLargeRedundantTempItemsDeleted(ids: Set<String>) = Unit
    fun getLargeRedundantTempFiles(): List<LargeRedundantTempFile> = emptyList()
    fun getStorageTrends(): List<StorageTrendPoint> = emptyList()
    fun getSmartCleanItems(): List<SmartCleanItem> = emptyList()

    suspend fun getStorageSnapshot(): StorageSnapshot = withContext(Dispatchers.IO) {
        val statFs = try {
            StatFs(Environment.getDataDirectory().path)
        } catch (e: Exception) {
            return@withContext StorageSnapshot(0L, 0L, 0L, emptyList())
        }

        val totalBytes = statFs.totalBytes.coerceAtLeast(0L)
        val freeBytes = statFs.availableBytes.coerceIn(0L, totalBytes)
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

        val photoSize = if (PermissionUtils.hasImagePermission(context)) {
            getMediaCategorySize(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        } else 0L

        val videoSize = if (PermissionUtils.hasVideoPermission(context)) {
            getMediaCategorySize(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        } else 0L

        val audioSize = if (PermissionUtils.hasAudioPermission(context)) {
            getMediaCategorySize(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        } else 0L

        val downloadsSize = if (hasAnyDocumentVisibility()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getMediaCategorySize(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            } else {
                getLegacyDownloadsSize()
            }
        } else 0L

        val appSize = if (PermissionUtils.hasUsageStatsPermission(context)) {
            getAppsTotalStorageSize()
        } else 0L

        val rawCategories = listOf(
            RawStorageCategory("Apps & Games", appSize, 0xFF00BFA5L),
            RawStorageCategory("Photos", photoSize, 0xFF4CAF50L),
            RawStorageCategory("Videos", videoSize, 0xFF2196F3L),
            RawStorageCategory("Audio & Music", audioSize, 0xFFFF9800L),
            RawStorageCategory("Downloads & Documents", downloadsSize, 0xFF795548L)
        )

        val categories = StorageCategoryMath.buildBreakdown(usedBytes, rawCategories).map { value ->
            val access = getCategoryAccessState(value.name)
            val confidence = when (value.name) {
                "Apps & Games" -> if (access == CategoryAccessState.AVAILABLE) {
                    DataConfidence.PACKAGE_STATS_MEASURED
                } else {
                    DataConfidence.UNAVAILABLE
                }
                "Photos", "Videos", "Audio & Music", "Downloads & Documents" -> when (access) {
                    CategoryAccessState.AVAILABLE -> DataConfidence.MEDIASTORE_MEASURED
                    CategoryAccessState.PARTIAL -> DataConfidence.PARTIAL
                    else -> DataConfidence.UNAVAILABLE
                }
                "System & Other" -> DataConfidence.ESTIMATED
                else -> DataConfidence.UNAVAILABLE
            }
            CategoryStorage(
                name = value.name,
                bytes = value.bytes,
                color = Color(value.colorArgb),
                percentage = value.percentage,
                confidence = confidence,
                availability = when (access) {
                    CategoryAccessState.AVAILABLE -> DataAvailability.AVAILABLE
                    CategoryAccessState.PARTIAL -> DataAvailability.PARTIAL
                    CategoryAccessState.PERMISSION_REQUIRED -> DataAvailability.PERMISSION_REQUIRED
                    CategoryAccessState.QUERY_FAILED -> DataAvailability.QUERY_FAILED
                    CategoryAccessState.UNSUPPORTED -> DataAvailability.UNSUPPORTED
                }
            )
        }

        StorageSnapshot(totalBytes, usedBytes, freeBytes, categories)
    }

    suspend fun getTopAppsByStorageSize(limit: Int = 30): List<AppStorageInfo> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasUsageStatsPermission(context)) return@withContext emptyList()

        val packageManager = context.packageManager
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val storageStats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return@withContext emptyList()
        val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - ONE_YEAR_MS
        val usageMap = usageStats
            ?.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
            ?.associate { stats ->
                val lastUsed = maxOf(
                    stats.lastTimeUsed,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stats.lastTimeVisible else 0L,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stats.lastTimeForegroundServiceUsed else 0L
                )
                stats.packageName to lastUsed
            }
            .orEmpty()

        packages.mapNotNull { app ->
            val size = queryAppSize(storageStats, app) ?: return@mapNotNull null
            AppStorageInfo(
                packageName = app.packageName,
                appName = app.loadLabel(packageManager).toString(),
                sizeBytes = size,
                lastUsedTimestamp = usageMap[app.packageName]?.takeIf { it > 0L },
                icon = getBoundedIcon(packageManager, app)
            )
        }.sortedByDescending { it.sizeBytes }.take(limit)
    }

    fun getCategoryAccessState(categoryName: String): CategoryAccessState {
        return when (categoryName) {
            "Apps & Games" -> if (PermissionUtils.hasUsageStatsPermission(context)) {
                CategoryAccessState.AVAILABLE
            } else {
                CategoryAccessState.PERMISSION_REQUIRED
            }
            "Photos" -> when {
                PermissionUtils.hasImagePermission(context) -> CategoryAccessState.AVAILABLE
                PermissionUtils.getMediaAccess(context) == PermissionUtils.MediaAccess.PARTIAL_VISUAL -> CategoryAccessState.PARTIAL
                else -> CategoryAccessState.PERMISSION_REQUIRED
            }
            "Videos" -> when {
                PermissionUtils.hasVideoPermission(context) -> CategoryAccessState.AVAILABLE
                PermissionUtils.getMediaAccess(context) == PermissionUtils.MediaAccess.PARTIAL_VISUAL -> CategoryAccessState.PARTIAL
                else -> CategoryAccessState.PERMISSION_REQUIRED
            }
            "Audio & Music" -> if (PermissionUtils.hasAudioPermission(context)) {
                CategoryAccessState.AVAILABLE
            } else {
                CategoryAccessState.PERMISSION_REQUIRED
            }
            "Downloads & Documents" -> if (hasAnyDocumentVisibility()) {
                CategoryAccessState.AVAILABLE
            } else {
                CategoryAccessState.PERMISSION_REQUIRED
            }
            "System & Other" -> CategoryAccessState.UNSUPPORTED
            else -> CategoryAccessState.UNSUPPORTED
        }
    }

    suspend fun getMediaItemsForCategory(categoryName: String, limit: Int = 100): List<MediaItem> =
        withContext(Dispatchers.IO) {
            when (categoryName) {
                "Photos" -> if (PermissionUtils.hasImagePermission(context)) {
                    queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, limit)
                } else emptyList()

                "Videos" -> if (PermissionUtils.hasVideoPermission(context)) {
                    queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, limit)
                } else emptyList()

                "Audio & Music" -> if (PermissionUtils.hasAudioPermission(context)) {
                    queryMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, limit)
                } else emptyList()

                "Downloads & Documents" -> if (hasAnyDocumentVisibility()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val downloads = queryMediaStore(MediaStore.Downloads.EXTERNAL_CONTENT_URI, limit)
                        if (downloads.isNotEmpty()) downloads else queryDocumentsFromFiles(limit)
                    } else {
                        getLegacyDownloads(limit)
                    }
                } else emptyList()

                else -> emptyList()
            }
        }

    suspend fun deleteMediaItems(items: List<MediaItem>): Boolean = withContext(Dispatchers.IO) {
        var deletedCount = 0
        items.forEach { item ->
            val uri = item.uriString?.let(Uri::parse) ?: return@forEach
            try {
                if (context.contentResolver.delete(uri, null, null) > 0) {
                    deletedCount++
                } else if (uri.scheme == "file") {
                    val file = uri.path?.let(::java.io.File)
                    if (file != null && file.exists() && file.delete()) deletedCount++
                }
            } catch (e: SecurityException) {
                // Android may require user-mediated confirmation. The caller verifies by re-querying.
            } catch (e: Exception) {
                // The caller verifies the source of truth and reports only confirmed deletions.
            }
        }
        deletedCount > 0
    }

    private fun hasAnyDocumentVisibility(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionUtils.hasImagePermission(context) ||
                PermissionUtils.hasVideoPermission(context) ||
                PermissionUtils.hasAudioPermission(context)
        } else {
            PermissionUtils.hasMediaPermissions(context)
        }
    }

    private fun queryDocumentsFromFiles(limit: Int): List<MediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND (" +
            "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/%' OR " +
            "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'text/%')"

        return queryRows(uri, projection, selection, limit)
    }

    private fun getMediaCategorySize(uri: Uri): Long {
        var total = 0L
        try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    total += cursor.getLong(sizeColumn).coerceAtLeast(0L)
                }
            }
        } catch (e: Exception) {
            return 0L
        }
        return total
    }

    private fun getLegacyDownloadsSize(): Long {
        return try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .sumOf { it.length().coerceAtLeast(0L) }
        } catch (e: Exception) {
            0L
        }
    }

    private fun getLegacyDownloads(limit: Int): List<MediaItem> {
        return try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.length() > 0L }
                .sortedByDescending { it.length() }
                .take(limit)
                .mapIndexed { index, file ->
                    MediaItem(
                        id = index.toLong(),
                        displayName = file.name,
                        sizeBytes = file.length(),
                        dateAdded = file.lastModified() / 1000L,
                        mimeType = null,
                        uriString = Uri.fromFile(file).toString()
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getAppsTotalStorageSize(): Long {
        val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return 0L
        return context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { queryAppSize(manager, it) }
            .sum()
    }

    private fun queryAppSize(manager: StorageStatsManager, app: ApplicationInfo): Long? {
        return try {
            val stats = manager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                app.packageName,
                android.os.Process.myUserHandle()
            )
            (stats.appBytes + stats.dataBytes + stats.cacheBytes).coerceAtLeast(0L)
        } catch (packageError: Exception) {
            try {
                val stats = manager.queryStatsForUid(StorageManager.UUID_DEFAULT, app.uid)
                (stats.appBytes + stats.dataBytes + stats.cacheBytes).coerceAtLeast(0L)
            } catch (uidError: Exception) {
                null
            }
        }
    }

    private fun queryMediaStore(uri: Uri, limit: Int): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        return queryRows(uri, projection, null, limit)
    }

    private fun queryRows(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        limit: Int
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                "${MediaStore.MediaColumns.SIZE} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                while (cursor.moveToNext() && items.size < limit) {
                    val size = cursor.getLong(sizeColumn)
                    if (size <= 0L) continue
                    val id = cursor.getLong(idColumn)
                    items += MediaItem(
                        id = id,
                        displayName = cursor.getString(nameColumn) ?: "Unknown",
                        sizeBytes = size,
                        dateAdded = cursor.getLong(dateColumn),
                        mimeType = cursor.getString(mimeColumn),
                        uriString = ContentUris.withAppendedId(uri, id).toString()
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return items
    }

    private fun getBoundedIcon(packageManager: PackageManager, app: ApplicationInfo): Drawable? {
        return try {
            val original = packageManager.getApplicationIcon(app)
            val width = original.intrinsicWidth.coerceIn(48, 96)
            val height = original.intrinsicHeight.coerceIn(48, 96)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val oldBounds = original.bounds
            original.setBounds(0, 0, width, height)
            original.draw(canvas)
            original.bounds = oldBounds
            BitmapDrawable(context.resources, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val ONE_YEAR_MS = 365L * 24L * 60L * 60L * 1000L
    }
}