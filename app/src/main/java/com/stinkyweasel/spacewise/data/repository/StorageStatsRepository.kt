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
import com.stinkyweasel.spacewise.data.models.CategoryStorage
import com.stinkyweasel.spacewise.data.models.DataAvailability
import com.stinkyweasel.spacewise.data.models.DataConfidence
import com.stinkyweasel.spacewise.data.models.LargeRedundantTempFile
import com.stinkyweasel.spacewise.data.models.MediaItem
import com.stinkyweasel.spacewise.data.models.RawStorageCategory
import com.stinkyweasel.spacewise.data.models.SmartCleanItem
import com.stinkyweasel.spacewise.data.models.StorageCategoryMath
import com.stinkyweasel.spacewise.data.models.StorageSnapshot
import com.stinkyweasel.spacewise.data.models.StorageTrendPoint
import com.stinkyweasel.spacewise.utils.PermissionUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageStatsRepository(private val context: Context) {

    // Temporary compatibility surface for old UI callers. These APIs never fabricate data or mutate state.
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
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.totalBytes.coerceAtLeast(0L)
        val freeBytes = statFs.availableBytes.coerceIn(0L, totalBytes)
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

        val measures = listOf(
            measureApps(),
            measureMedia("Photos", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageAccessState()),
            measureMedia("Videos", MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoAccessState()),
            measureMedia("Audio & Music", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioAccessState()),
            measureDownloads()
        )

        val accessByName = measures.associate { it.name to it.access }
        val raw = measures.map { RawStorageCategory(it.name, it.bytes, it.colorArgb) }
        val categories = StorageCategoryMath.buildBreakdown(usedBytes, raw).map { value ->
            val access = accessByName[value.name] ?: CategoryAccessState.UNSUPPORTED
            CategoryStorage(
                name = value.name,
                bytes = value.bytes,
                color = Color(value.colorArgb),
                percentage = value.percentage,
                confidence = confidenceFor(value.name, access),
                availability = availabilityFor(access)
            )
        }
        StorageSnapshot(totalBytes, usedBytes, freeBytes, categories)
    }

    fun getCategoryAccessState(categoryName: String): CategoryAccessState = when (categoryName) {
        "Apps & Games" -> if (PermissionUtils.hasUsageStatsPermission(context)) {
            CategoryAccessState.AVAILABLE
        } else CategoryAccessState.PERMISSION_REQUIRED
        "Photos" -> imageAccessState()
        "Videos" -> videoAccessState()
        "Audio & Music" -> audioAccessState()
        "Downloads & Documents" -> downloadsAccessState()
        else -> CategoryAccessState.UNSUPPORTED
    }

    suspend fun getTopAppsByStorageSize(limit: Int = 30): List<AppStorageInfo> =
        withContext(Dispatchers.IO) {
            if (!PermissionUtils.hasUsageStatsPermission(context)) return@withContext emptyList()
            val packageManager = context.packageManager
            val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                ?: error("StorageStatsManager unavailable")
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val usageByPackage = usageManager
                ?.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - ONE_YEAR_MS, now)
                ?.associate { stats ->
                    stats.packageName to maxOf(
                        stats.lastTimeUsed,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stats.lastTimeVisible else 0L,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stats.lastTimeForegroundServiceUsed else 0L
                    )
                }
                .orEmpty()

            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .mapNotNull { app ->
                    val size = queryAppSize(manager, app) ?: return@mapNotNull null
                    AppStorageInfo(
                        packageName = app.packageName,
                        appName = app.loadLabel(packageManager).toString(),
                        sizeBytes = size,
                        lastUsedTimestamp = usageByPackage[app.packageName]?.takeIf { it > 0L },
                        icon = getBoundedIcon(packageManager, app)
                    )
                }
                .sortedByDescending { it.sizeBytes }
                .take(limit.coerceAtLeast(0))
        }

    suspend fun getMediaItemsForCategory(categoryName: String, limit: Int = 100): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceAtLeast(0)
            when (categoryName) {
                "Photos" -> if (PermissionUtils.hasImageReadAccess(context)) {
                    queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, safeLimit)
                } else emptyList()
                "Videos" -> if (PermissionUtils.hasVideoReadAccess(context)) {
                    queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, safeLimit)
                } else emptyList()
                "Audio & Music" -> if (PermissionUtils.hasAudioPermission(context)) {
                    queryMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, safeLimit)
                } else emptyList()
                "Downloads & Documents" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryMediaStore(MediaStore.Downloads.EXTERNAL_CONTENT_URI, safeLimit)
                        .ifEmpty { queryDocumentsFromFiles(safeLimit) }
                } else if (PermissionUtils.hasMediaPermissions(context)) {
                    getLegacyDownloads(safeLimit)
                } else emptyList()
                else -> emptyList()
            }
        }

    suspend fun deleteMediaItems(items: List<MediaItem>): Boolean = withContext(Dispatchers.IO) {
        items.any { item ->
            val uri = item.uriString?.let(Uri::parse) ?: return@any false
            try {
                when {
                    uri.scheme == "file" -> uri.path?.let(::File)?.let { it.exists() && it.delete() } == true
                    else -> context.contentResolver.delete(uri, null, null) > 0
                }
            } catch (_: SecurityException) {
                false
            }
        }
    }

    suspend fun getVerifiedDeletedItems(items: List<MediaItem>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            items.filter { item ->
                val uri = item.uriString?.let(Uri::parse) ?: return@filter false
                when (uri.scheme) {
                    "file" -> uri.path?.let(::File)?.exists() == false
                    else -> isContentUriDefinitelyAbsent(uri)
                }
            }
        }

    private fun isContentUriDefinitelyAbsent(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { cursor -> !cursor.moveToFirst() }
                ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun measureApps(): CategoryMeasure {
        val access = getCategoryAccessState("Apps & Games")
        if (access != CategoryAccessState.AVAILABLE) {
            return CategoryMeasure("Apps & Games", 0L, access, COLOR_APPS)
        }
        return try {
            CategoryMeasure("Apps & Games", getAppsTotalStorageSize(), access, COLOR_APPS)
        } catch (_: Exception) {
            CategoryMeasure("Apps & Games", 0L, CategoryAccessState.QUERY_FAILED, COLOR_APPS)
        }
    }

    private fun measureMedia(name: String, uri: Uri, access: CategoryAccessState): CategoryMeasure {
        if (access != CategoryAccessState.AVAILABLE && access != CategoryAccessState.PARTIAL) {
            return CategoryMeasure(name, 0L, access, colorFor(name))
        }
        return try {
            CategoryMeasure(name, getMediaCategorySize(uri), access, colorFor(name))
        } catch (_: Exception) {
            CategoryMeasure(name, 0L, CategoryAccessState.QUERY_FAILED, colorFor(name))
        }
    }

    private fun measureDownloads(): CategoryMeasure {
        val access = downloadsAccessState()
        if (access == CategoryAccessState.PERMISSION_REQUIRED) {
            return CategoryMeasure("Downloads & Documents", 0L, access, COLOR_DOWNLOADS)
        }
        return try {
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getMediaCategorySize(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            } else getLegacyDownloadsSize()
            CategoryMeasure("Downloads & Documents", bytes, access, COLOR_DOWNLOADS)
        } catch (_: Exception) {
            CategoryMeasure("Downloads & Documents", 0L, CategoryAccessState.QUERY_FAILED, COLOR_DOWNLOADS)
        }
    }

    private fun imageAccessState(): CategoryAccessState = when {
        PermissionUtils.hasFullImagePermission(context) -> CategoryAccessState.AVAILABLE
        PermissionUtils.hasSelectedVisualPermission(context) -> CategoryAccessState.PARTIAL
        else -> CategoryAccessState.PERMISSION_REQUIRED
    }

    private fun videoAccessState(): CategoryAccessState = when {
        PermissionUtils.hasFullVideoPermission(context) -> CategoryAccessState.AVAILABLE
        PermissionUtils.hasSelectedVisualPermission(context) -> CategoryAccessState.PARTIAL
        else -> CategoryAccessState.PERMISSION_REQUIRED
    }

    private fun audioAccessState(): CategoryAccessState =
        if (PermissionUtils.hasAudioPermission(context)) CategoryAccessState.AVAILABLE
        else CategoryAccessState.PERMISSION_REQUIRED

    private fun downloadsAccessState(): CategoryAccessState =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) CategoryAccessState.PARTIAL
        else if (PermissionUtils.hasMediaPermissions(context)) CategoryAccessState.AVAILABLE
        else CategoryAccessState.PERMISSION_REQUIRED

    private fun confidenceFor(name: String, access: CategoryAccessState): DataConfidence = when {
        name == "System & Other" -> DataConfidence.ESTIMATED
        access == CategoryAccessState.PARTIAL -> DataConfidence.PARTIAL
        access != CategoryAccessState.AVAILABLE -> DataConfidence.UNAVAILABLE
        name == "Apps & Games" -> DataConfidence.PACKAGE_STATS_MEASURED
        else -> DataConfidence.MEDIASTORE_MEASURED
    }

    private fun availabilityFor(access: CategoryAccessState): DataAvailability = when (access) {
        CategoryAccessState.AVAILABLE -> DataAvailability.AVAILABLE
        CategoryAccessState.PARTIAL -> DataAvailability.PARTIAL
        CategoryAccessState.PERMISSION_REQUIRED -> DataAvailability.PERMISSION_REQUIRED
        CategoryAccessState.QUERY_FAILED -> DataAvailability.QUERY_FAILED
        CategoryAccessState.UNSUPPORTED -> DataAvailability.UNSUPPORTED
    }

    private fun queryDocumentsFromFiles(limit: Int): List<MediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND (" +
            "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/%' OR " +
            "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'text/%')"
        return queryRows(MediaStore.Files.getContentUri("external"), selection, limit)
    }

    private fun queryMediaStore(uri: Uri, limit: Int): List<MediaItem> = queryRows(uri, null, limit)

    private fun queryRows(uri: Uri, selection: String?, limit: Int): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(
            uri,
            mediaProjection(),
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
        return items
    }

    private fun mediaProjection(): Array<String> = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.MIME_TYPE
    )

    private fun getMediaCategorySize(uri: Uri): Long {
        var total = 0L
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) total += cursor.getLong(sizeColumn).coerceAtLeast(0L)
        }
        return total
    }

    private fun getLegacyDownloadsSize(): Long =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .listFiles().orEmpty().filter { it.isFile }.sumOf { it.length().coerceAtLeast(0L) }

    private fun getLegacyDownloads(limit: Int): List<MediaItem> =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .listFiles().orEmpty()
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

    private fun getAppsTotalStorageSize(): Long {
        val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: error("StorageStatsManager unavailable")
        return context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { queryAppSize(manager, it) }
            .sum()
    }

    private fun queryAppSize(manager: StorageStatsManager, app: ApplicationInfo): Long? = try {
        val stats = manager.queryStatsForPackage(
            StorageManager.UUID_DEFAULT,
            app.packageName,
            android.os.Process.myUserHandle()
        )
        (stats.appBytes + stats.dataBytes + stats.cacheBytes).coerceAtLeast(0L)
    } catch (_: Exception) {
        try {
            val stats = manager.queryStatsForUid(StorageManager.UUID_DEFAULT, app.uid)
            (stats.appBytes + stats.dataBytes + stats.cacheBytes).coerceAtLeast(0L)
        } catch (_: Exception) {
            null
        }
    }

    private fun getBoundedIcon(packageManager: PackageManager, app: ApplicationInfo): Drawable? = try {
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
    } catch (_: Exception) {
        null
    }

    private fun colorFor(name: String): Long = when (name) {
        "Photos" -> COLOR_PHOTOS
        "Videos" -> COLOR_VIDEOS
        "Audio & Music" -> COLOR_AUDIO
        else -> COLOR_DOWNLOADS
    }

    private data class CategoryMeasure(
        val name: String,
        val bytes: Long,
        val access: CategoryAccessState,
        val colorArgb: Long
    )

    companion object {
        private const val ONE_YEAR_MS = 365L * 24L * 60L * 60L * 1000L
        private const val COLOR_APPS = 0xFF00BFA5L
        private const val COLOR_PHOTOS = 0xFF4CAF50L
        private const val COLOR_VIDEOS = 0xFF2196F3L
        private const val COLOR_AUDIO = 0xFFFF9800L
        private const val COLOR_DOWNLOADS = 0xFF795548L
    }
}
