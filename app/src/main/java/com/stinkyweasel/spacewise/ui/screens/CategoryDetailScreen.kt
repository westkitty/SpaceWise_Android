package com.stinkyweasel.spacewise.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stinkyweasel.spacewise.data.models.ByteFormatting
import com.stinkyweasel.spacewise.data.models.CategoryAccessState
import com.stinkyweasel.spacewise.data.models.MediaItem
import com.stinkyweasel.spacewise.viewmodel.CategoryDetailViewModel
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MediaSortMode(val label: String) {
    SIZE("Largest"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryName: String,
    viewModel: CategoryDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val mediaItems by viewModel.mediaItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val accessState by viewModel.accessState.collectAsState()

    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(MediaSortMode.SIZE) }
    var olderThanYearOnly by rememberSaveable { mutableStateOf(false) }
    var duplicateOnly by rememberSaveable { mutableStateOf(false) }
    var duplicateKeys by remember { mutableStateOf<Set<String>?>(null) }
    var isScanningDuplicates by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(categoryName) {
        viewModel.loadMediaForCategory(categoryName)
        selectedKeys = emptySet()
        duplicateOnly = false
        duplicateKeys = null
    }

    val filteredItems by remember(
        mediaItems,
        searchQuery,
        sortMode,
        olderThanYearOnly,
        duplicateOnly,
        duplicateKeys
    ) {
        derivedStateOf {
            val cutoff = (System.currentTimeMillis() / 1000L) - ONE_YEAR_SECONDS
            mediaItems
                .asSequence()
                .filter { item ->
                    searchQuery.isBlank() ||
                        item.displayName.contains(searchQuery, ignoreCase = true) ||
                        item.mimeType.orEmpty().contains(searchQuery, ignoreCase = true)
                }
                .filter { !olderThanYearOnly || item.dateAdded in 1L until cutoff }
                .filter { !duplicateOnly || item.selectionKey in duplicateKeys.orEmpty() }
                .sortedWith(
                    when (sortMode) {
                        MediaSortMode.SIZE -> compareByDescending<MediaItem> { it.sizeBytes }
                        MediaSortMode.NEWEST -> compareByDescending { it.dateAdded }
                        MediaSortMode.OLDEST -> compareBy { it.dateAdded }
                        MediaSortMode.NAME -> compareBy { it.displayName.lowercase() }
                    }
                )
                .toList()
        }
    }

    val selectedItemsSize by remember(selectedKeys, mediaItems) {
        derivedStateOf {
            mediaItems.filter { it.selectionKey in selectedKeys }.sumOf { it.sizeBytes }
        }
    }

    fun openItem(item: MediaItem) {
        val uri = item.uriString?.let(Uri::parse) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Open ${item.displayName}")) }
            .onFailure {
                scope.launch { snackbarHostState.showSnackbar("No installed app can preview this item.") }
            }
    }

    fun enableDuplicateReview() {
        if (duplicateOnly) {
            duplicateOnly = false
            return
        }
        if (duplicateKeys != null) {
            duplicateOnly = true
            return
        }
        scope.launch {
            isScanningDuplicates = true
            val matches = findExactDuplicateKeys(context, mediaItems)
            duplicateKeys = matches
            duplicateOnly = true
            isScanningDuplicates = false
            val message = if (matches.isEmpty()) {
                "No exact duplicates found among visible files."
            } else {
                "Found ${matches.size} files in exact duplicate groups."
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Request deletion", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Android will attempt to delete ${selectedKeys.size} selected items. " +
                        "Up to ${ByteFormatting.formatByteCount(selectedItemsSize)} may be reclaimed. " +
                        "SpaceWise will count only deletions it can verify afterward."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteSelectedItems(selectedKeys) { result ->
                            if (result.deletedAnything) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            scope.launch {
                                val reclaimed = ByteFormatting.formatByteCount(result.verifiedReclaimedBytes)
                                val message = when {
                                    result.fullyDeleted -> "Verified ${result.verifiedDeletedCount} deletions. Reclaimed $reclaimed."
                                    result.deletedAnything -> "Verified ${result.verifiedDeletedCount} of ${result.requestedCount}. Reclaimed $reclaimed."
                                    result.confirmationMayBeRequired -> "No deletion was verified. Android may require confirmation or additional access."
                                    else -> "No deletion was verified."
                                }
                                snackbarHostState.showSnackbar(message)
                            }
                            selectedKeys = emptySet()
                            duplicateKeys = null
                            duplicateOnly = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Request Deletion") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(categoryName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    if (categoryName != "System & Other") {
                        IconButton(
                            onClick = {
                                viewModel.loadMediaForCategory(categoryName)
                                selectedKeys = emptySet()
                                duplicateKeys = null
                                duplicateOnly = false
                            },
                            modifier = Modifier.testTag("refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh files")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when {
                categoryName == "System & Other" -> SystemAndOtherExplanation()
                isLoading && mediaItems.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                accessState != CategoryAccessState.AVAILABLE && accessState != CategoryAccessState.PARTIAL -> {
                    CategoryAccessStateCard(categoryName, accessState)
                }
                else -> {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().testTag("media_search_input"),
                            placeholder = { Text("Search visible files") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(MediaSortMode.entries) { mode ->
                                FilterChip(
                                    selected = sortMode == mode,
                                    onClick = { sortMode = mode },
                                    label = { Text(mode.label) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = olderThanYearOnly,
                                    onClick = { olderThanYearOnly = !olderThanYearOnly },
                                    label = { Text("Older than 1 year") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = duplicateOnly,
                                    onClick = ::enableDuplicateReview,
                                    enabled = !isScanningDuplicates,
                                    label = {
                                        Text(if (isScanningDuplicates) "Scanning duplicates…" else "Exact duplicates")
                                    }
                                )
                            }
                        }

                        if (accessState == CategoryAccessState.PARTIAL) {
                            Text(
                                "Only the media Android currently exposes to SpaceWise is included.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (filteredItems.isEmpty()) {
                            EmptyMediaState(
                                categoryName,
                                searchQuery.isNotBlank() || olderThanYearOnly || duplicateOnly
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${filteredItems.size} visible items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        val visibleKeys = filteredItems.map { it.selectionKey }.toSet()
                                        selectedKeys = if (visibleKeys.all { it in selectedKeys }) {
                                            selectedKeys - visibleKeys
                                        } else {
                                            selectedKeys + visibleKeys
                                        }
                                    }
                                ) {
                                    Text(if (filteredItems.all { it.selectionKey in selectedKeys }) "Deselect visible" else "Select visible")
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(filteredItems, key = { it.selectionKey }) { item ->
                                    MediaItemRow(
                                        item = item,
                                        isSelected = item.selectionKey in selectedKeys,
                                        onSelectedChange = { checked ->
                                            selectedKeys = if (checked) selectedKeys + item.selectionKey
                                            else selectedKeys - item.selectionKey
                                        },
                                        onOpen = { openItem(item) }
                                    )
                                }
                            }
                        }

                        if (selectedKeys.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("${selectedKeys.size} selected", fontWeight = FontWeight.Bold)
                                        Text(ByteFormatting.formatByteCount(selectedItemsSize))
                                    }
                                    Button(
                                        onClick = { showDeleteConfirmation = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaItemRow(
    item: MediaItem,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val select: (Boolean) -> Unit = { checked ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSelectedChange(checked)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { select(!isSelected) }
            .testTag("media_item_${item.selectionKey.hashCode()}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(checked = isSelected, onCheckedChange = select)
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(item.mimeType, item.formattedDate).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.formattedSize, fontWeight = FontWeight.Bold)
                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Preview ${item.displayName}")
                }
            }
        }
    }
}

@Composable
fun SystemAndOtherExplanation() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(64.dp))
        Text("OS, System & Private App Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Android does not let ordinary apps inspect system partitions or other apps' private files. " +
                "SpaceWise reports this category as residual used storage instead of inventing a file list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyMediaState(categoryName: String, filtersActive: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(56.dp))
        Text(
            if (filtersActive) "No Matching Items" else "No Visible Files",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (filtersActive) "Change the search or filters to review more items."
            else "Android exposed no listable items for $categoryName.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CategoryAccessStateCard(categoryName: String, state: CategoryAccessState) {
    val message = when (state) {
        CategoryAccessState.PERMISSION_REQUIRED -> "SpaceWise does not have permission to read $categoryName."
        CategoryAccessState.QUERY_FAILED -> "Android allowed this category, but the current query failed. Try refreshing."
        CategoryAccessState.UNSUPPORTED -> "$categoryName cannot be listed through the Android APIs available to SpaceWise."
        CategoryAccessState.PARTIAL -> "Only media explicitly exposed by Android is visible."
        CategoryAccessState.AVAILABLE -> "No visible items were found."
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Category unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private suspend fun findExactDuplicateKeys(context: Context, items: List<MediaItem>): Set<String> =
    withContext(Dispatchers.IO) {
        val candidates = items
            .filter { it.sizeBytes > 0L && it.uriString != null }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }
            .flatten()

        val byDigest = mutableMapOf<String, MutableList<MediaItem>>()
        for (item in candidates) {
            val uri = item.uriString?.let(Uri::parse) ?: continue
            val digest = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val messageDigest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        messageDigest.update(buffer, 0, count)
                    }
                    messageDigest.digest().joinToString("") { byte -> "%02x".format(byte) }
                }
            }.getOrNull() ?: continue
            byDigest.getOrPut(digest) { mutableListOf() }.add(item)
        }

        byDigest.values
            .filter { group -> group.size > 1 }
            .flatten()
            .mapTo(linkedSetOf()) { it.selectionKey }
    }

private const val ONE_YEAR_SECONDS = 365L * 24L * 60L * 60L
