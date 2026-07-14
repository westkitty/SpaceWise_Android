package com.stinkyweasel.spacewise.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.stinkyweasel.spacewise.data.models.DeletionResult
import com.stinkyweasel.spacewise.data.models.MediaItem
import com.stinkyweasel.spacewise.viewmodel.CategoryDetailViewModel
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MediaSortMode(val label: String) {
    SIZE("Largest"), NEWEST("Newest"), OLDEST("Oldest"), NAME("Name")
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeleteKeys by remember { mutableStateOf(emptySet<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(MediaSortMode.SIZE) }
    var oldOnly by rememberSaveable { mutableStateOf(false) }
    var duplicatesOnly by rememberSaveable { mutableStateOf(false) }
    var duplicateKeys by remember { mutableStateOf<Set<String>?>(null) }
    var scanningDuplicates by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun showResult(result: DeletionResult) {
        if (result.deletedAnything) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val reclaimed = ByteFormatting.formatByteCount(result.verifiedReclaimedBytes)
            snackbar.showSnackbar(
                when {
                    result.fullyDeleted -> "Deleted ${result.verifiedDeletedCount} items. Reclaimed $reclaimed."
                    result.deletedAnything -> "Deleted ${result.verifiedDeletedCount} of ${result.requestedCount}. Reclaimed $reclaimed."
                    result.confirmationMayBeRequired -> "No deletion was verified. Android may have denied access."
                    else -> "No deletion was verified."
                }
            )
        }
        selectedKeys = emptySet()
        pendingDeleteKeys = emptySet()
        duplicateKeys = null
        duplicatesOnly = false
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val keys = pendingDeleteKeys
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.verifySelectedItemsDeleted(keys, ::showResult)
        } else {
            pendingDeleteKeys = emptySet()
            scope.launch { snackbar.showSnackbar("Deletion cancelled.") }
        }
    }

    fun requestDelete(keys: Set<String>) {
        val requested = mediaItems.filter { it.selectionKey in keys }
        if (requested.isEmpty()) return
        pendingDeleteKeys = keys

        val contentUris = requested.mapNotNull { item ->
            item.uriString?.let(Uri::parse)?.takeIf { it.scheme == "content" }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && contentUris.isNotEmpty()) {
            runCatching {
                val request = MediaStore.createDeleteRequest(context.contentResolver, contentUris)
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure {
                pendingDeleteKeys = emptySet()
                scope.launch { snackbar.showSnackbar("Android could not open the deletion confirmation.") }
            }
        } else {
            viewModel.deleteSelectedItems(keys, ::showResult)
        }
    }

    LaunchedEffect(categoryName) {
        viewModel.loadMediaForCategory(categoryName)
        selectedKeys = emptySet()
        duplicateKeys = null
        duplicatesOnly = false
    }

    val filtered by remember(mediaItems, search, sortMode, oldOnly, duplicatesOnly, duplicateKeys) {
        derivedStateOf {
            val cutoff = (System.currentTimeMillis() / 1000L) - ONE_YEAR_SECONDS
            mediaItems.asSequence()
                .filter { search.isBlank() || it.displayName.contains(search, true) || it.mimeType.orEmpty().contains(search, true) }
                .filter { !oldOnly || it.dateAdded in 1L until cutoff }
                .filter { !duplicatesOnly || it.selectionKey in duplicateKeys.orEmpty() }
                .sortedWith(
                    when (sortMode) {
                        MediaSortMode.SIZE -> compareByDescending<MediaItem> { it.sizeBytes }
                        MediaSortMode.NEWEST -> compareByDescending<MediaItem> { it.dateAdded }
                        MediaSortMode.OLDEST -> compareBy<MediaItem> { it.dateAdded }
                        MediaSortMode.NAME -> compareBy<MediaItem> { it.displayName.lowercase() }
                    }
                ).toList()
        }
    }

    val selectedBytes by remember(selectedKeys, mediaItems) {
        derivedStateOf { mediaItems.filter { it.selectionKey in selectedKeys }.sumOf { it.sizeBytes } }
    }

    fun preview(item: MediaItem) {
        val uri = item.uriString?.let(Uri::parse) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Open ${item.displayName}")) }
            .onFailure { scope.launch { snackbar.showSnackbar("No installed app can preview this item.") } }
    }

    fun toggleDuplicates() {
        if (duplicatesOnly) {
            duplicatesOnly = false
            return
        }
        duplicateKeys?.let {
            duplicatesOnly = true
            return
        }
        scope.launch {
            scanningDuplicates = true
            val found = findExactDuplicateKeys(context, mediaItems)
            duplicateKeys = found
            duplicatesOnly = true
            scanningDuplicates = false
            snackbar.showSnackbar(
                if (found.isEmpty()) "No exact duplicates found."
                else "Found ${found.size} files in exact duplicate groups."
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete selected files?") },
            text = { Text("Delete ${selectedKeys.size} selected items from this screen? Android may show one system confirmation. Up to ${ByteFormatting.formatByteCount(selectedBytes)} may be reclaimed.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        requestDelete(selectedKeys)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(categoryName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    if (categoryName != "System & Other") {
                        IconButton(onClick = { viewModel.loadMediaForCategory(categoryName) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh files")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when {
                categoryName == "System & Other" -> SystemAndOtherExplanation()
                isLoading && mediaItems.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                accessState !in setOf(CategoryAccessState.AVAILABLE, CategoryAccessState.PARTIAL) -> CategoryAccessStateCard(categoryName, accessState)
                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth().testTag("media_search_input"),
                        placeholder = { Text("Search visible files") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MediaSortMode.entries) { mode ->
                            FilterChip(selected = sortMode == mode, onClick = { sortMode = mode }, label = { Text(mode.label) })
                        }
                        item { FilterChip(selected = oldOnly, onClick = { oldOnly = !oldOnly }, label = { Text("Older than 1 year") }) }
                        item {
                            FilterChip(
                                selected = duplicatesOnly,
                                onClick = ::toggleDuplicates,
                                enabled = !scanningDuplicates,
                                label = { Text(if (scanningDuplicates) "Scanning…" else "Exact duplicates") }
                            )
                        }
                    }
                    if (accessState == CategoryAccessState.PARTIAL) {
                        Text("Only files Android currently exposes to SpaceWise are included.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (filtered.isEmpty()) {
                        EmptyMediaState(categoryName, search.isNotBlank() || oldOnly || duplicatesOnly)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${filtered.size} visible items", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                val keys = filtered.map { it.selectionKey }.toSet()
                                selectedKeys = if (keys.all(selectedKeys::contains)) selectedKeys - keys else selectedKeys + keys
                            }) { Text(if (filtered.all { it.selectionKey in selectedKeys }) "Deselect visible" else "Select visible") }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filtered, key = { it.selectionKey }) { item ->
                                MediaItemRow(
                                    item = item,
                                    selected = item.selectionKey in selectedKeys,
                                    onToggle = { checked ->
                                        selectedKeys = if (checked) selectedKeys + item.selectionKey else selectedKeys - item.selectionKey
                                    },
                                    onOpen = { preview(item) },
                                    onDelete = { requestDelete(setOf(item.selectionKey)) }
                                )
                            }
                        }
                    }
                    if (selectedKeys.isNotEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${selectedKeys.size} selected", fontWeight = FontWeight.Bold)
                                    Text(ByteFormatting.formatByteCount(selectedBytes))
                                }
                                Button(
                                    onClick = { confirmDelete = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete here")
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
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!selected) },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = selected, onCheckedChange = onToggle)
            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
            }
            Column(Modifier.weight(1f)) {
                Text(item.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.mimeType, item.formattedDate).joinToString(" • "), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.formattedSize, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Preview ${item.displayName}")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${item.displayName}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun SystemAndOtherExplanation() {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(64.dp))
        Text("OS, System & Private App Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Android does not let ordinary apps inspect system partitions or other apps' private files. SpaceWise reports this as residual used storage rather than inventing a file list.")
    }
}

@Composable
fun EmptyMediaState(categoryName: String, filtersActive: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(56.dp))
        Text(if (filtersActive) "No Matching Items" else "No Visible Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (filtersActive) "Change the search or filters." else "Android exposed no listable items for $categoryName.")
    }
}

@Composable
fun CategoryAccessStateCard(categoryName: String, state: CategoryAccessState) {
    val message = when (state) {
        CategoryAccessState.PERMISSION_REQUIRED -> "SpaceWise does not have permission to read $categoryName."
        CategoryAccessState.QUERY_FAILED -> "Android allowed this category, but the query failed. Try refreshing."
        CategoryAccessState.UNSUPPORTED -> "$categoryName cannot be listed through the Android APIs available to SpaceWise."
        CategoryAccessState.PARTIAL -> "Only media explicitly exposed by Android is visible."
        CategoryAccessState.AVAILABLE -> "No visible items were found."
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Category unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message)
        }
    }
}

private suspend fun findExactDuplicateKeys(context: Context, items: List<MediaItem>): Set<String> = withContext(Dispatchers.IO) {
    val candidates = items.filter { it.sizeBytes > 0L && it.uriString != null }
        .groupBy { it.sizeBytes }.values.filter { it.size > 1 }.flatten()
    val byDigest = mutableMapOf<String, MutableList<MediaItem>>()
    candidates.forEach { item ->
        val uri = item.uriString?.let(Uri::parse) ?: return@forEach
        val digest = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val md = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    md.update(buffer, 0, count)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
        }.getOrNull() ?: return@forEach
        byDigest.getOrPut(digest) { mutableListOf() }.add(item)
    }
    byDigest.values.filter { it.size > 1 }.flatten().mapTo(linkedSetOf()) { it.selectionKey }
}

private const val ONE_YEAR_SECONDS = 365L * 24L * 60L * 60L
