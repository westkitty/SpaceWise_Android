from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
OLD_PACKAGE = "com.example"
NEW_PACKAGE = "com.stinkyweasel.spacewise"


def replace_text(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old in text:
        path.write_text(text.replace(old, new))


def remove_composable_function(text: str, function_name: str) -> str:
    pattern = re.compile(r"\n@Composable\s*\nfun\s+" + re.escape(function_name) + r"\s*\(")
    match = pattern.search(text)
    if not match:
        return text

    brace = text.find("{", match.end())
    if brace < 0:
        return text

    depth = 0
    index = brace
    in_string = False
    escaped = False
    while index < len(text):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        else:
            if char == '"':
                in_string = True
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    end = index + 1
                    while end < len(text) and text[end] in "\r\n":
                        end += 1
                    return text[: match.start()] + "\n" + text[end:]
        index += 1
    return text


def migrate_packages() -> None:
    for source_set in ("main", "test", "androidTest"):
        base = APP / "src" / source_set / "java"
        old_dir = base / "com" / "example"
        new_dir = base / "com" / "stinkyweasel" / "spacewise"
        if old_dir.exists():
            new_dir.parent.mkdir(parents=True, exist_ok=True)
            if new_dir.exists():
                for source in old_dir.rglob("*"):
                    if source.is_file():
                        target = new_dir / source.relative_to(old_dir)
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.move(str(source), str(target))
                shutil.rmtree(old_dir)
            else:
                shutil.move(str(old_dir), str(new_dir))

    for kotlin_file in APP.rglob("*.kt"):
        replace_text(kotlin_file, OLD_PACKAGE, NEW_PACKAGE)

    build_file = APP / "build.gradle.kts"
    replace_text(build_file, 'namespace = "com.example"', f'namespace = "{NEW_PACKAGE}"')


def add_truth_models() -> None:
    models = APP / "src/main/java/com/stinkyweasel/spacewise/data/models/StorageModels.kt"
    text = models.read_text()
    if "enum class DataConfidence" not in text:
        marker = "data class CategoryStorage("
        definitions = '''enum class DataConfidence {
    SYSTEM_MEASURED,
    MEDIASTORE_MEASURED,
    PACKAGE_STATS_MEASURED,
    ESTIMATED,
    PARTIAL,
    UNAVAILABLE
}

enum class DataAvailability {
    AVAILABLE,
    PARTIAL,
    PERMISSION_REQUIRED,
    QUERY_FAILED,
    UNSUPPORTED
}

'''
        text = text.replace(marker, definitions + marker)

    text = text.replace(
        "    val color: Color,\n    val percentage: Float\n)",
        "    val color: Color,\n"
        "    val percentage: Float,\n"
        "    val confidence: DataConfidence = DataConfidence.SYSTEM_MEASURED,\n"
        "    val availability: DataAvailability = DataAvailability.AVAILABLE\n"
        ")",
    )
    models.write_text(text)

    access_models = APP / "src/main/java/com/stinkyweasel/spacewise/data/models/AccessModels.kt"
    access_models.write_text(
        '''package com.stinkyweasel.spacewise.data.models

enum class CategoryAccessState {
    AVAILABLE,
    PARTIAL,
    PERMISSION_REQUIRED,
    QUERY_FAILED,
    UNSUPPORTED
}

data class DeletionResult(
    val requestedCount: Int,
    val verifiedDeletedCount: Int,
    val stillPresentCount: Int,
    val skippedCount: Int,
    val verifiedReclaimedBytes: Long,
    val confirmationMayBeRequired: Boolean = false
) {
    val fullyDeleted: Boolean
        get() = requestedCount > 0 && verifiedDeletedCount == requestedCount

    val deletedAnything: Boolean
        get() = verifiedDeletedCount > 0
}
'''
    )


def patch_repository() -> None:
    path = APP / "src/main/java/com/stinkyweasel/spacewise/data/repository/StorageStatsRepository.kt"
    text = path.read_text()

    imports = "import com.stinkyweasel.spacewise.data.models.AppStorageInfo\n"
    additions = (
        "import com.stinkyweasel.spacewise.data.models.CategoryAccessState\n"
        "import com.stinkyweasel.spacewise.data.models.DataAvailability\n"
        "import com.stinkyweasel.spacewise.data.models.DataConfidence\n"
    )
    if "import com.stinkyweasel.spacewise.data.models.CategoryAccessState" not in text:
        text = text.replace(imports, imports + additions)

    old_mapping = '''        val categories = StorageCategoryMath.buildBreakdown(usedBytes, rawCategories).map { value ->
            CategoryStorage(
                name = value.name,
                bytes = value.bytes,
                color = Color(value.colorArgb),
                percentage = value.percentage
            )
        }
'''
    new_mapping = '''        val categories = StorageCategoryMath.buildBreakdown(usedBytes, rawCategories).map { value ->
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
'''
    text = text.replace(old_mapping, new_mapping)

    marker = "    suspend fun getMediaItemsForCategory(categoryName: String, limit: Int = 100): List<MediaItem> ="
    if "fun getCategoryAccessState(" not in text:
        method = '''    fun getCategoryAccessState(categoryName: String): CategoryAccessState {
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

'''
        text = text.replace(marker, method + marker)

    path.write_text(text)


def patch_category_view_model() -> None:
    path = APP / "src/main/java/com/stinkyweasel/spacewise/viewmodel/CategoryDetailViewModel.kt"
    path.write_text(
        '''package com.stinkyweasel.spacewise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stinkyweasel.spacewise.data.models.CategoryAccessState
import com.stinkyweasel.spacewise.data.models.DeletionResult
import com.stinkyweasel.spacewise.data.models.MediaItem
import com.stinkyweasel.spacewise.data.repository.StorageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryDetailViewModel(private val repository: StorageStatsRepository) : ViewModel() {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _accessState = MutableStateFlow(CategoryAccessState.AVAILABLE)
    val accessState: StateFlow<CategoryAccessState> = _accessState.asStateFlow()

    private var activeCategory: String? = null

    fun loadMediaForCategory(categoryName: String) {
        activeCategory = categoryName
        viewModelScope.launch {
            _isLoading.value = true
            _accessState.value = repository.getCategoryAccessState(categoryName)
            try {
                _mediaItems.value = when (_accessState.value) {
                    CategoryAccessState.AVAILABLE,
                    CategoryAccessState.PARTIAL -> repository.getMediaItemsForCategory(categoryName)
                    else -> emptyList()
                }
            } catch (e: SecurityException) {
                _mediaItems.value = emptyList()
                _accessState.value = CategoryAccessState.PERMISSION_REQUIRED
            } catch (e: Exception) {
                _mediaItems.value = emptyList()
                _accessState.value = CategoryAccessState.QUERY_FAILED
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSelectedItems(selectedIds: Set<Long>, onComplete: (DeletionResult) -> Unit) {
        viewModelScope.launch {
            val category = activeCategory
            val before = _mediaItems.value
            val requested = before.filter { it.id in selectedIds }
            if (category == null || requested.isEmpty()) {
                onComplete(DeletionResult(0, 0, 0, 0, 0L))
                return@launch
            }

            _isLoading.value = true
            try {
                repository.deleteMediaItems(requested)
                val after = repository.getMediaItemsForCategory(category)
                val remainingUris = after.mapNotNull { it.uriString }.toSet()
                val verifiedDeleted = requested.filter { item ->
                    item.uriString != null && item.uriString !in remainingUris
                }
                val skipped = requested.count { it.uriString == null }
                val stillPresent = requested.size - verifiedDeleted.size - skipped
                _mediaItems.value = after
                onComplete(
                    DeletionResult(
                        requestedCount = requested.size,
                        verifiedDeletedCount = verifiedDeleted.size,
                        stillPresentCount = stillPresent.coerceAtLeast(0),
                        skippedCount = skipped,
                        verifiedReclaimedBytes = verifiedDeleted.sumOf { it.sizeBytes },
                        confirmationMayBeRequired = stillPresent > 0
                    )
                )
            } catch (e: Exception) {
                onComplete(
                    DeletionResult(
                        requestedCount = requested.size,
                        verifiedDeletedCount = 0,
                        stillPresentCount = requested.size,
                        skippedCount = 0,
                        verifiedReclaimedBytes = 0L,
                        confirmationMayBeRequired = true
                    )
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
'''
    )


def patch_category_screen() -> None:
    path = APP / "src/main/java/com/stinkyweasel/spacewise/ui/screens/CategoryDetailScreen.kt"
    text = path.read_text()
    if "import com.stinkyweasel.spacewise.data.models.CategoryAccessState" not in text:
        text = text.replace(
            "import com.stinkyweasel.spacewise.data.models.ByteFormatting\n",
            "import com.stinkyweasel.spacewise.data.models.ByteFormatting\n"
            "import com.stinkyweasel.spacewise.data.models.CategoryAccessState\n",
        )

    text = text.replace(
        "    val isLoading by viewModel.isLoading.collectAsState()\n",
        "    val isLoading by viewModel.isLoading.collectAsState()\n"
        "    val accessState by viewModel.accessState.collectAsState()\n",
    )
    text = text.replace('text = "Confirm Secure Deletion"', 'text = "Request deletion"')
    text = text.replace(
        'text = "Are you sure you want to permanently delete these ${selectedIds.size} selected items? " +\n'
        '                           "This will instantly reclaim ${ByteFormatting.formatByteCount(selectedItemsSize)} of device space. " +\n'
        '                           "This operation is safe, offline, and cannot be undone.",',
        'text = "Android will attempt to delete ${selectedIds.size} selected items. " +\n'
        '                           "Up to ${ByteFormatting.formatByteCount(selectedItemsSize)} may be reclaimed, " +\n'
        '                           "but SpaceWise will report only deletions verified by a fresh media scan.",',
    )
    text = text.replace('Text("Securely Delete", color = MaterialTheme.colorScheme.onError)', 'Text("Request Deletion", color = MaterialTheme.colorScheme.onError)')

    callback_pattern = re.compile(
        r"viewModel\.deleteSelectedItems\(selectedIds\) \{ success, bytesReclaimed ->.*?selectedIds = emptySet\(\)\n\s*\}",
        re.S,
    )
    callback_replacement = '''viewModel.deleteSelectedItems(selectedIds) { result ->
                            if (result.deletedAnything) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            scope.launch {
                                val reclaimed = ByteFormatting.formatByteCount(result.verifiedReclaimedBytes)
                                val message = when {
                                    result.fullyDeleted -> "Verified ${result.verifiedDeletedCount} deletions. Reclaimed $reclaimed."
                                    result.deletedAnything -> "Verified ${result.verifiedDeletedCount} of ${result.requestedCount} deletions. Reclaimed $reclaimed."
                                    result.confirmationMayBeRequired -> "No deletion was verified. Android may require confirmation or additional access."
                                    else -> "No deletion was verified."
                                }
                                snackbarHostState.showSnackbar(message)
                            }
                            selectedIds = emptySet()
                        }'''
    text, count = callback_pattern.subn(callback_replacement, text, count=1)
    if count != 1:
        raise RuntimeError("Could not replace deletion callback in CategoryDetailScreen")

    text = text.replace(
        'text = "Reclaims ${ByteFormatting.formatByteCount(selectedItemsSize)}",',
        'text = "Potentially frees ${ByteFormatting.formatByteCount(selectedItemsSize)}",',
    )
    text = text.replace('Text("Reclaim Space", fontWeight = FontWeight.Bold)', 'Text("Request Deletion", fontWeight = FontWeight.Bold)')

    text = text.replace(
        '''                mediaItems.isEmpty() -> {
                    EmptyMediaState(categoryName)
                }
''',
        '''                accessState != CategoryAccessState.AVAILABLE &&
                    accessState != CategoryAccessState.PARTIAL -> {
                    CategoryAccessStateCard(categoryName, accessState)
                }
                mediaItems.isEmpty() -> {
                    EmptyMediaState(categoryName)
                }
''',
    )

    if "fun CategoryAccessStateCard(" not in text:
        text += '''

@Composable
fun CategoryAccessStateCard(categoryName: String, state: CategoryAccessState) {
    val message = when (state) {
        CategoryAccessState.PERMISSION_REQUIRED -> "SpaceWise does not have permission to read $categoryName. No zero-byte claim is being made."
        CategoryAccessState.QUERY_FAILED -> "Android allowed this category, but the current query failed. Try refreshing."
        CategoryAccessState.UNSUPPORTED -> "$categoryName cannot be listed through the Android APIs available to SpaceWise."
        CategoryAccessState.PARTIAL -> "Only media explicitly selected in Android is visible. This is not the full library."
        CategoryAccessState.AVAILABLE -> "No visible items were found."
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Category unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
'''
    path.write_text(text)


def patch_dashboard() -> None:
    path = APP / "src/main/java/com/stinkyweasel/spacewise/ui/screens/DashboardScreen.kt"
    text = path.read_text()

    for line in (
        "    val storageTrends by viewModel.storageTrends.collectAsState()\n",
        "    val smartCleanItems by viewModel.smartCleanItems.collectAsState()\n",
        "    val largeRedundantTempFiles by viewModel.largeRedundantTempFiles.collectAsState()\n",
        "    var successData by remember { mutableStateOf<Pair<Long, List<String>>?>(null) }\n",
    ):
        text = text.replace(line, "")

    call_pattern = re.compile(
        r"\s*storageTrends = storageTrends,\n\s*smartCleanItems = smartCleanItems,\n\s*largeRedundantTempFiles = largeRedundantTempFiles,\n\s*onDeleteSmartCleanItems = \{ items ->.*?onDeleteLargeRedundantTempFiles = \{ items ->.*?\},\n\s*hasMediaPerm = hasMediaPerm,",
        re.S,
    )
    text, count = call_pattern.subn("\n                        hasMediaPerm = hasMediaPerm,", text, count=1)
    if count != 1:
        raise RuntimeError("Could not remove legacy DashboardContent cleaner arguments")

    signature_pattern = re.compile(
        r"\s*storageTrends: List<StorageTrendPoint>,\n\s*smartCleanItems: List<SmartCleanItem>,\n\s*largeRedundantTempFiles: List<LargeRedundantTempFile>,\n\s*onDeleteSmartCleanItems: \(List<SmartCleanItem>\) -> Unit,\n\s*onDeleteLargeRedundantTempFiles: \(List<LargeRedundantTempFile>\) -> Unit,"
    )
    text, count = signature_pattern.subn("", text, count=1)
    if count != 1:
        raise RuntimeError("Could not remove legacy DashboardContent cleaner signature")

    text = re.sub(
        r"\n\s*// 30-Day Storage Trend Chart\n\s*StorageTrendChart\(trends = storageTrends\)\n\s*// Smart Clean Widget\n\s*SmartCleanWidget\(.*?\)\n\s*// Large, Redundant & Temporary Files Cleaner\n\s*LargeRedundantTempFileWidget\(.*?\)\n",
        "\n",
        text,
        count=1,
        flags=re.S,
    )

    overlay_pattern = re.compile(r"\n\s*// Animated Success Overlay\n\s*AnimatedVisibility\(.*?\n\s*}\n\s*}\n\s*\n@Composable\nfun DashboardContent", re.S)
    text, count = overlay_pattern.subn("\n    }\n}\n\n@Composable\nfun DashboardContent", text, count=1)
    if count != 1:
        raise RuntimeError("Could not remove simulated cleaner success overlay")

    text = text.replace(
        'text = "Scanning for large, redundant, and temporary files to optimize your device space.",',
        'text = "Reading storage values currently available through Android system APIs.",',
    )
    text = text.replace(
        'text = "Grant media and usage access for complete and accurate storage analysis.",',
        'text = "Some categories are unavailable or partial. Grant only the access needed for the categories you want to inspect.",',
    )
    text = text.replace('text = "Advisory Cleaning Insights"', 'text = "Measured Storage Insights"')

    for function_name in (
        "StorageTrendChart",
        "SmartCleanWidget",
        "LargeRedundantTempFileWidget",
        "CleanSuccessOverlay",
    ):
        text = remove_composable_function(text, function_name)

    path.write_text(text)


def add_tests() -> None:
    test_dir = APP / "src/test/java/com/stinkyweasel/spacewise/data/models"
    test_dir.mkdir(parents=True, exist_ok=True)
    (test_dir / "DataTruthModelsTest.kt").write_text(
        '''package com.stinkyweasel.spacewise.data.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTruthModelsTest {
    @Test
    fun deletionIsFullyVerifiedOnlyWhenEveryRequestedItemDisappears() {
        val partial = DeletionResult(3, 2, 1, 0, 100L)
        val complete = DeletionResult(3, 3, 0, 0, 150L)

        assertFalse(partial.fullyDeleted)
        assertTrue(partial.deletedAnything)
        assertTrue(complete.fullyDeleted)
    }

    @Test
    fun unavailableAndPartialAreDistinctTruthStates() {
        assertTrue(DataConfidence.PARTIAL != DataConfidence.UNAVAILABLE)
        assertTrue(DataAvailability.PARTIAL != DataAvailability.PERMISSION_REQUIRED)
    }
}
'''
    )


def main() -> None:
    migrate_packages()
    add_truth_models()
    patch_repository()
    patch_category_view_model()
    patch_category_screen()
    patch_dashboard()
    add_tests()
    print("Trust refactor migration completed.")


if __name__ == "__main__":
    main()
