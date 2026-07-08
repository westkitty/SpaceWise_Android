package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    fun startViewIntent(item: MediaItem) {
        val uriString = item.uriString
        if (uriString.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("No openable media URI is available for this item.") }
            return
        }
        try {
            val uri = Uri.parse(uriString)
            val mimeType = item.mimeType ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "Open ${item.displayName}").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("No app is available to open ${item.displayName}. Try Share instead.") }
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Unable to open ${item.displayName}. It may have moved since the scan.") }
        }
    }

    fun shareItem(item: MediaItem) {
        val uriString = item.uriString
        if (uriString.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("No shareable media URI is available for this item.") }
            return
        }
        try {
            val uri = Uri.parse(uriString)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${item.displayName}"))
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Unable to share ${item.displayName}.") }
        }
    }

    LaunchedEffect(categoryName) {
        viewModel.loadMediaForCategory(categoryName)
        selectedIds = emptySet()
    }

    val selectedItemsSize = remember(selectedIds, mediaItems) {
        mediaItems.filter { it.id in selectedIds }.sumOf { it.sizeBytes }
    }

    if (showCleanupConfirmation) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirmation = false },
            title = { Text("Review selected items", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selected: ${selectedIds.size} items")
                    Text("Possible space change: ${ByteFormatting.formatByteCount(selectedItemsSize)}")
                    Text(
                        text = "This is a dry-run summary before action. Files can disappear or require Android confirmation between scan and action.",
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
                            if (success) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Action completed. ${ByteFormatting.formatByteCount(bytesReclaimed)} processed.")
                                }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("No files were processed. Review permissions or stale scan state.") }
                            }
                            selectedIds = emptySet()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_dialog_btn")
                ) { Text("Proceed", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirmation = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
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
                        if (selectedIds.isNotEmpty()) {
                            TextButton(onClick = { selectedIds = emptySet() }) { Text("Deselect") }
                        } else if (visibleItems.isNotEmpty()) {
                            TextButton(onClick = { selectedIds = visibleItems.map { it.id }.toSet() }) { Text("Select visible") }
                        }
                        IconButton(
                            onClick = {
                                viewModel.loadMediaForCategory(categoryName)
                                selectedIds = emptySet()
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
                            item { ScanAuditCard(scanAudit, mediaItems.size, visibleItems.size, onCancel = { viewModel.cancelScan() }) }
                            item {
                                FileControls(
                                    searchText = searchText,
                                    onSearchChange = {
                                        searchText = it
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
                                        viewModel.updateKindFilter(it)
                                    },
                                    compactMode = compactMode,
                                    onCompactModeChange = { compactMode = it }
                                )
                            }
                            item {
                                Text(
                                    text = "Tap a file to open it. Use checkboxes to select. Each row lists why it appears here.",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            items(visibleItems, key = { it.id }) { item ->
                                val isSelected = item.id in selectedIds
                                MediaItemRow(
                                    item = item,
                                    isSelected = isSelected,
                                    compact = compactMode,
                                    onOpenMedia = { startViewIntent(it) },
                                    onShareMedia = { shareItem(it) },
                                    onSelectedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                    }
                                )
                            }

                            if (cleanupResults.isNotEmpty()) {
                                item { CleanupResultsCard(cleanupResults.size, cleanupResults.groupBy { it.status }.mapValues { it.value.size }) }
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${selectedIds.size} files selected", fontWeight = FontWeight.Bold)
                                        Text("Dry-run estimate: ${ByteFormatting.formatByteCount(selectedItemsSize)}")
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
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onCancel) { Text("Cancel scan") }
    }
}

@Composable
fun ScanAuditCard(scanAudit: ScanAuditInfo?, totalCount: Int, visibleCount: Int, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Scan transparency", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Session: ${scanAudit?.sessionId ?: "not started"}", style = MaterialTheme.typography.bodySmall)
            Text("Stage: ${scanAudit?.stage?.label ?: "Idle"} • ${scanAudit?.progressPercent ?: 0}%", style = MaterialTheme.typography.bodySmall)
            Text("Rows: $visibleCount visible / $totalCount scanned", style = MaterialTheme.typography.bodySmall)
            Text(scanAudit?.note ?: "MediaStore only. No private app storage is inspected or uploaded.", style = MaterialTheme.typography.bodySmall)
            if (scanAudit?.isStale == true) {
                Text("Scan may be stale. Rescan before acting.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            if (scanAudit?.stage?.name != "COMPLETE") {
                TextButton(onClick = onCancel) { Text("Cancel current scan") }
            }
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = selectedKind == null, onClick = { onKindChange(null) }, label = { Text("All") })
            MediaKind.values().take(4).forEach { kind ->
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media_item_${item.id}")
            .semantics { contentDescription = "Open ${item.displayName}. Checkbox selects it." }
            .clickable { onOpenMedia(item) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = triggerSelection,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Select ${item.displayName}" }.testTag("checkbox_${item.id}")
            )
            Box(
                modifier = Modifier.size(if (compact) 34.dp else 40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.fileKind.label} • ${item.formattedSize} • ${item.formattedDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${item.confidence.label}: ${item.categoryReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    val meta = listOfNotNull(item.mimeType, item.formattedDuration, item.formattedResolution, item.sourceCollection).joinToString(" • ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tap to open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onShareMedia(item) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) { Text("Share") }
                }
            }
        }
    }
}

@Composable
fun CleanupResultsCard(total: Int, counts: Map<com.example.data.models.CleanupStatus, Int>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = CardDefaults.outlinedCardBorder()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last action results", fontWeight = FontWeight.Bold)
            Text("$total item results recorded.")
            counts.forEach { (status, count) -> Text("${status.name.lowercase()}: $count", style = MaterialTheme.typography.bodySmall) }
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
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(Icons.Default.Description, contentDescription = "No items found", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Text("No Files Detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("No large items found under \"$categoryName\". This can mean no matching MediaStore rows, missing permission, or a stale scan.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
    }
}
