package com.stinkyweasel.spacewise.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stinkyweasel.spacewise.data.models.AppStorageInfo
import com.stinkyweasel.spacewise.data.models.ByteFormatting
import com.stinkyweasel.spacewise.viewmodel.AppListViewModel

private enum class AppSortMode(val label: String) {
    SIZE("Largest"),
    NAME("Name"),
    LAST_USED("Least recent")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val appsList by viewModel.appsList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(AppSortMode.SIZE) }
    var unusedOnly by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadApps() }

    val filteredApps by remember(appsList, searchQuery, sortMode, unusedOnly) {
        derivedStateOf {
            val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
            appsList
                .asSequence()
                .filter { app ->
                    searchQuery.isBlank() ||
                        app.appName.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
                }
                .filter { app ->
                    !unusedOnly || app.lastUsedTimestamp == null || app.lastUsedTimestamp < cutoff
                }
                .sortedWith(
                    when (sortMode) {
                        AppSortMode.SIZE -> compareByDescending<AppStorageInfo> { it.sizeBytes }
                        AppSortMode.NAME -> compareBy { it.appName.lowercase() }
                        AppSortMode.LAST_USED -> compareBy<AppStorageInfo> { it.lastUsedTimestamp ?: Long.MIN_VALUE }
                    }
                )
                .toList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Storage", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }, modifier = Modifier.testTag("refresh_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh apps")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().testTag("app_search_input"),
                placeholder = { Text("Search apps or package names") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(AppSortMode.entries) { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(mode.label) }
                    )
                }
                item {
                    FilterChip(
                        selected = unusedOnly,
                        onClick = { unusedOnly = !unusedOnly },
                        label = { Text("Unused 30+ days") }
                    )
                }
            }

            when {
                isLoading && appsList.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                appsList.isEmpty() -> NoAppsEmptyState(onNavigateToPermissions)
                filteredApps.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No apps match the current search and filters.")
                    }
                }
                else -> {
                    Text(
                        text = "${filteredApps.size} visible apps. Tap one for Android controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppInfoRow(
                                app = app,
                                onOpenSettings = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                        .onFailure {
                                            context.startActivity(
                                                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                },
                                onUninstall = {
                                    val intent = Intent(Intent.ACTION_DELETE).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoRow(
    app: AppStorageInfo,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit
) {
    val iconBitmap = (app.icon as? BitmapDrawable)?.bitmap?.asImageBitmap()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
            .testTag("app_row_${app.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = "Icon for ${app.appName}",
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ByteFormatting.formatLastUsed(app.lastUsedTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = app.formattedSize,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(36.dp).testTag("uninstall_${app.packageName}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Uninstall ${app.appName}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun NoAppsEmptyState(onNavigateToPermissions: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No app stats",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text("No App Data Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Grant Usage Access so SpaceWise can show the app storage statistics Android exposes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onNavigateToPermissions, modifier = Modifier.testTag("grant_usage_btn")) {
            Text("Grant Usage Access")
        }
    }
}

private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
