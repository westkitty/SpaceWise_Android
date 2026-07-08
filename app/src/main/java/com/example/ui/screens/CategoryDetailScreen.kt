package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.models.ByteFormatting
import com.example.data.models.CleanupStatus
import com.example.data.models.MediaItem
import com.example.data.models.MediaKind
import com.example.data.models.MediaSortMode
import com.example.data.models.ScanAuditInfo
import com.example.viewmodel.CategoryDetailViewModel
import kotlinx.coroutines.launch

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
    val visibleItems by viewModel.visibleItems.collectAsState()
    val scanAudit by viewModel.scanAudit.collectAsState()
    val cleanupResults by viewModel.lastCleanupResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showCleanupConfirmation by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var compactMode by remember { mutableStateOf(false) }
    var selectedKind by remember { mutableStateOf<MediaKind?>(null) }
    var selectedSortMode by remember { mutableStateOf(MediaSortMode.LARGEST) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(categoryName) {
        searchText = ""
        selectedKind = null
        selectedSortMode = MediaSortMode.LARGEST
        selectedIds = emptySet()
        viewModel.updateSearch("")
        viewModel.updateKindFilter(null)
        viewModel.updateSortMode(MediaSortMode.LARGEST)
        viewModel.loadMediaForCategory(categoryName)
    }

    LaunchedEffect(visibleItems) {
        val visibleIds = visibleItems.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(visibleIds)
    }

    fun openItem(item: MediaItem) {
        val uriString = item.uriString
        if (uriString.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("This item has no openable URI.") }
            return
        }
        try {
            val uri = Uri.parse(uriString)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Open ${item.displayName}"))
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("No installed app can open this file type.") }
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Could not open this item. Rescan if it moved.") }
        }
    }

    fun shareItem(item: MediaItem) {
        val uriString = item.uriString
        if (uriString.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("This item has no shareable URI.") }
            return
        }
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            scope.launch { snackbarHostState.showSnackbar("This legacy file path cannot be safely shared by Android. Open it instead.") }
            return
        }
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${item.displayName}"))
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Could not share this item.") }
        }
    }

    val selectedItemsSize = remember(selectedIds, visibleItems) {
        visibleItems.filter { it.id in selectedIds }.sumOf { it.sizeBytes }
    }

    if (showCleanupConfirmation) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirmation = false },
            title = { Text("Review visible selection", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selected visible items: ${selectedIds.size}")
                    Text("Estimated space: ${ByteFormatting.formatByteCount(selectedItemsSize)}")
                    Text(
                        "Filters and search are respected. Hidden rows are not included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Android may still require confirmation or deny access if a file moved after the scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanupConfirmation = false
                        viewModel.deleteSelectedItems(selectedIds) { success, bytesReclaimed ->
                            selectedIds = emptySet()
                            if (success) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { snackbarHostState.showSnackbar("Processed ${ByteFormatting.formatByteCount(bytesReclaimed)}.") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("No visible selected files were processed.") }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_dialog_btn")
                ) { Text("Proceed", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = { TextButton(onClick = { showCleanupConfirmation = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(categoryName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    if (categoryName != "System & Other") {
                        if (selectedIds.isNotEmpty()) {
                            TextButton(onClick = { selectedIds = emptySet() }) { Text("Clear") }
                        } else if (visibleItems.isNotEmpty()) {
                            TextButton(onClick = { selectedIds = visibleItems.map { it.id }.toSet() }) { Text("Select") }
                        }
                        IconButton(
                            onClick = {
                                selectedIds = emptySet()
                                viewModel.loadMediaForCategory(categoryName)
                            },
                            modifier = Modifier.testTag("refresh_button")
                        ) { Icon(Icons.Default.Refresh, contentDescription = "Rescan files") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                isLoading && mediaItems.isEmpty() -> LoadingScanState(scanAudit, onCancel = { viewModel.cancelScan() })
                mediaItems.isEmpty() -> EmptyMediaState(categoryName)
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 10.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                        ) {
                            item { ScanAuditCard(scanAudit, mediaItems.size, visibleItems.size, isLoading, onCancel = { viewModel.cancelScan() }) }
                            item {
                                FileControls(
                                    searchText = searchText,
                                    onSearchChange = {
                                        searchText = it
                                        selectedIds = emptySet()
                                        viewModel.updateSearch(it)
                                    },
                                    selectedSortMode = selectedSortMode,
                                    onSortChange = {
                                        selectedSortMode = it
                                        viewModel.updateSortMode(it)
                                    },
                                    selectedKind = selectedKind,
                                    onKindChange = {
                                        selectedKind = it
                                        selectedIds = emptySet()
                                        viewModel.updateKindFilter(it)
                                    },
                                    compactMode = compactMode,
                                    onCompactModeChange = { compactMode = it }
                                )
                            }
                            item {
                                Text(
                                    "Tap a row to open. Use the checkbox to select. Search/filter changes clear selection.",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            items(visibleItems, key = { it.id }) { item ->
                                MediaItemRow(
                                    item = item,
                                    isSelected = item.id in selectedIds,
                                    compact = compactMode,
                                    onOpenMedia = { openItem(it) },
                                    onShareMedia = { shareItem(it) },
                                    onSelectedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                    }
                                )
                            }
                            if (visibleItems.isEmpty()) {
                                item { NoFilteredResultsCard() }
                            }
                            if (cleanupResults.isNotEmpty()) {
                                item { CleanupResultsCard(cleanupResults.groupBy { it.status }.mapValues { it.value.size }) }
                            }
                        }

                        if (selectedIds.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("reclaim_action_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${selectedIds.size} visible selected", fontWeight = FontWeight.Bold)
                                        Text("Estimate: ${ByteFormatting.formatByteCount(selectedItemsSize)}")
                                    }
                                    Button(
                                        onClick = { showCleanupConfirmation = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Review selected action", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Review")
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
fun LoadingScanState(scanAudit: ScanAuditInfo?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(scanAudit?.stage?.label ?: "Scanning", fontWeight = FontWeight.Bold)
        Text(scanAudit?.note ?: "Reading Android MediaStore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onCancel) { Text("Cancel scan") }
    }
}

@Composable
fun ScanAuditCard(scanAudit: ScanAuditInfo?, totalCount: Int, visibleCount: Int, isLoading: Boolean, onCancel: () -> Unit) {
    val durationText = scanAudit?.durationMillis?.let { " • ${it / 1000.0}s" } ?: ""
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = CardDefaults.outlinedCardBorder()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Scan status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${scanAudit?.stage?.label ?: "Idle"} • ${scanAudit?.progressPercent ?: 0}%$durationText", style = MaterialTheme.typography.bodySmall)
            Text("Rows: $visibleCount visible / $totalCount scanned", style = MaterialTheme.typography.bodySmall)
            Text(scanAudit?.note ?: "MediaStore only. No private app storage is inspected or uploaded.", style = MaterialTheme.typography.bodySmall)
            if (scanAudit?.isStale == true) Text("Scan may be stale. Rescan before acting.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            if (isLoading) TextButton(onClick = onCancel) { Text("Cancel scan") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileControls(
    searchText: String,
    onSearchChange: (String) -> Unit,
    selectedSortMode: MediaSortMode,
    onSortChange: (MediaSortMode) -> Unit,
    selectedKind: MediaKind?,
    onKindChange: (MediaKind?) -> Unit,
    compactMode: Boolean,
    onCompactModeChange: (Boolean) -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search scanned files") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AssistChip(onClick = { sortMenuOpen = true }, label = { Text(selectedSortMode.label) })
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    MediaSortMode.values().forEach { mode ->
                        DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                            onSortChange(mode)
                            sortMenuOpen = false
                        })
                    }
                }
            }
            FilterChip(selected = compactMode, onClick = { onCompactModeChange(!compactMode) }, label = { Text("Dense") })
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            FilterChip(selected = selectedKind == null, onClick = { onKindChange(null) }, label = { Text("All") })
            MediaKind.values().forEach { kind ->
                FilterChip(selected = selectedKind == kind, onClick = { onKindChange(kind) }, label = { Text(kind.label) })
            }
        }
    }
}

@Composable
fun MediaItemRow(
    item: MediaItem,
    isSelected: Boolean,
    compact: Boolean,
    onOpenMedia: (MediaItem) -> Unit,
    onShareMedia: (MediaItem) -> Unit,
    onSelectedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val triggerSelection: (Boolean) -> Unit = { checked ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSelectedChange(checked)
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("media_item_${item.id}").semantics { contentDescription = "Open ${item.displayName}. Checkbox selects it." }.clickable { onOpenMedia(item) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else CardDefaults.outlinedCardBorder()
    ) {
        Row(modifier = Modifier.padding(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = triggerSelection,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Select ${item.displayName}" }.testTag("checkbox_${item.id}")
            )
            Box(modifier = Modifier.size(if (compact) 34.dp else 40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.fileKind.label} • ${item.formattedSize} • ${item.formattedDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${item.confidence.label}: ${item.categoryReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    val meta = listOfNotNull(item.mimeType, item.formattedDuration, item.formattedResolution, item.sourceCollection).joinToString(" • ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tap to open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onShareMedia(item) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Share") }
                }
            }
        }
    }
}

@Composable
fun NoFilteredResultsCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = CardDefaults.outlinedCardBorder()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No matches", fontWeight = FontWeight.Bold)
            Text("Adjust search or filters. The scan still contains files outside this view.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CleanupResultsCard(counts: Map<CleanupStatus, Int>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = CardDefaults.outlinedCardBorder()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last action results", fontWeight = FontWeight.Bold)
            counts.forEach { (status, count) -> Text("${status.name.lowercase()}: $count", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun SystemAndOtherExplanation() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Text("OS, System & Cached Workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("This category is residual and partly inaccessible. It is not a file list.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            "Android reserve" to "Space used by OS partitions and reserved runtime workspace.",
            "App-private storage" to "Other apps' private folders are sandboxed and cannot be inspected directly.",
            "Cache estimates" to "Some cache sizes come from Android storage APIs, not raw file access.",
            "Unknown residual" to "Used space not matched to visible categories is grouped here."
        ).forEach { (title, body) ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = CardDefaults.outlinedCardBorder()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun EmptyMediaState(categoryName: String) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(Icons.Default.Description, contentDescription = "No items found", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Text("No Files Detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("No large items found under \"$categoryName\". This can mean no matching MediaStore rows, missing permission, or a stale scan.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
    }
}
