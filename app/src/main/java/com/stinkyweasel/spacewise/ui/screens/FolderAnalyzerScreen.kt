package com.stinkyweasel.spacewise.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.stinkyweasel.spacewise.data.models.ByteFormatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FolderEntry(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val mimeType: String?
)

private enum class FolderSortMode(val label: String) {
    SIZE("Largest"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderAnalyzerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var treeUri by rememberSaveable { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<FolderEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(FolderSortMode.SIZE) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    suspend fun scan(uriString: String) {
        isLoading = true
        selectedUris = emptySet()
        entries = withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                ?: return@withContext emptyList()
            val found = mutableListOf<FolderEntry>()
            val stack = ArrayDeque<Pair<DocumentFile, Int>>()
            stack.add(root to 0)
            while (stack.isNotEmpty() && found.size < MAX_FOLDER_FILES) {
                val (node, depth) = stack.removeLast()
                if (node.isDirectory && depth < MAX_FOLDER_DEPTH) {
                    runCatching { node.listFiles() }.getOrDefault(emptyArray()).forEach { child ->
                        if (child.isDirectory) stack.add(child to depth + 1)
                        else if (child.isFile) {
                            found += FolderEntry(
                                uri = child.uri,
                                name = child.name ?: "Unnamed file",
                                sizeBytes = child.length().coerceAtLeast(0L),
                                modifiedMillis = child.lastModified().coerceAtLeast(0L),
                                mimeType = child.type
                            )
                        }
                    }
                }
            }
            found.sortedByDescending { it.sizeBytes }
        }
        isLoading = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            treeUri = uri.toString()
            scope.launch { scan(uri.toString()) }
        }
    }

    LaunchedEffect(treeUri) {
        treeUri?.let { if (entries.isEmpty()) scan(it) }
    }

    val visibleEntries by remember(entries, searchQuery, sortMode) {
        derivedStateOf {
            entries.asSequence()
                .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                .sortedWith(
                    when (sortMode) {
                        FolderSortMode.SIZE -> compareByDescending<FolderEntry> { it.sizeBytes }
                        FolderSortMode.NEWEST -> compareByDescending<FolderEntry> { it.modifiedMillis }
                        FolderSortMode.OLDEST -> compareBy<FolderEntry> { it.modifiedMillis }
                        FolderSortMode.NAME -> compareBy<FolderEntry> { it.name.lowercase() }
                    }
                )
                .toList()
        }
    }

    val selectedBytes by remember(entries, selectedUris) {
        derivedStateOf { entries.filter { it.uri.toString() in selectedUris }.sumOf { it.sizeBytes } }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete selected files?") },
            text = {
                Text(
                    "Delete ${selectedUris.size} files from the folder you granted? " +
                        "SpaceWise will verify each file is gone before counting ${ByteFormatting.formatByteCount(selectedBytes)} as reclaimed."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmation = false
                    scope.launch {
                        isLoading = true
                        val selected = entries.filter { it.uri.toString() in selectedUris }
                        val verified = withContext(Dispatchers.IO) {
                            selected.filter { entry ->
                                val deleted = runCatching {
                                    DocumentsContract.deleteDocument(context.contentResolver, entry.uri)
                                }.getOrDefault(false)
                                deleted && runCatching {
                                    DocumentFile.fromSingleUri(context, entry.uri)?.exists() == false
                                }.getOrDefault(false)
                            }
                        }
                        val reclaimed = verified.sumOf { it.sizeBytes }
                        treeUri?.let { scan(it) }
                        snackbarHostState.showSnackbar(
                            "Verified ${verified.size} of ${selected.size} deletions. Reclaimed ${ByteFormatting.formatByteCount(reclaimed)}."
                        )
                        selectedUris = emptySet()
                        isLoading = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Folder Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    if (treeUri != null) {
                        IconButton(onClick = { scope.launch { scan(treeUri!!) } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan folder")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { picker.launch(null) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Column {
                        Text(if (treeUri == null) "Choose a folder" else "Change analyzed folder", fontWeight = FontWeight.Bold)
                        Text(
                            "Android grants SpaceWise access only to the folder you select.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (treeUri != null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search this folder") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FolderSortMode.entries) { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { sortMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                treeUri == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Choose Downloads or another folder to inspect it recursively.")
                }
                visibleEntries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files match the current folder and search.")
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${visibleEntries.size} files • ${ByteFormatting.formatByteCount(visibleEntries.sumOf { it.sizeBytes })}")
                        if (selectedUris.isNotEmpty()) {
                            Button(onClick = { showDeleteConfirmation = true }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Delete ${selectedUris.size}")
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(visibleEntries, key = { it.uri.toString() }) { entry ->
                            val selected = entry.uri.toString() in selectedUris
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedUris = if (selected) selectedUris - entry.uri.toString()
                                    else selectedUris + entry.uri.toString()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { checked ->
                                            selectedUris = if (checked) selectedUris + entry.uri.toString()
                                            else selectedUris - entry.uri.toString()
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(entry.mimeType ?: "Unknown type", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(ByteFormatting.formatByteCount(entry.sizeBytes), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(entry.uri, entry.mimeType ?: "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(intent, "Open ${entry.name}")) }
                                    }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Preview ${entry.name}")
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

private const val MAX_FOLDER_FILES = 5000
private const val MAX_FOLDER_DEPTH = 12
