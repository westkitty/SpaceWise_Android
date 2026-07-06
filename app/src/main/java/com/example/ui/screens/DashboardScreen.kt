package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.models.LargeRedundantTempFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.models.AdvisorySuggestion
import com.example.data.models.ByteFormatting
import com.example.data.models.CategoryStorage
import com.example.data.models.StorageSnapshot
import com.example.data.models.SuggestionType
import com.example.data.models.StorageTrendPoint
import com.example.data.models.SmartCleanItem
import com.example.data.models.SmartCleanType
import com.example.utils.PermissionUtils
import com.example.viewmodel.DashboardViewModel
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToBreakdown: () -> Unit,
    onNavigateToAppList: () -> Unit,
    onNavigateToCategoryDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val snapshot by viewModel.snapshot.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val storageTrends by viewModel.storageTrends.collectAsState()
    val smartCleanItems by viewModel.smartCleanItems.collectAsState()
    val largeRedundantTempFiles by viewModel.largeRedundantTempFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Check permission state to show warning card
    var hasMediaPerm by remember { mutableStateOf(PermissionUtils.hasMediaPermissions(context)) }
    var hasUsagePerm by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }

    // Lifecycle observer to re-check permissions and refresh data on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
                hasUsagePerm = PermissionUtils.hasUsageStatsPermission(context)
                viewModel.refreshStorageData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var successData by remember { mutableStateOf<Pair<Long, List<String>>?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "SpaceWise",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Your device, your space",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refreshStorageData() },
                            modifier = Modifier.testTag("refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh storage analysis",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isLoading && snapshot == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    DashboardContent(
                        snapshot = snapshot,
                        suggestions = suggestions,
                        storageTrends = storageTrends,
                        smartCleanItems = smartCleanItems,
                        largeRedundantTempFiles = largeRedundantTempFiles,
                        onDeleteSmartCleanItems = { items ->
                            val bytes = items.sumOf { it.sizeBytes }
                            val categories = items.map { item ->
                                if (item.type == SmartCleanType.DUPLICATE) "Duplicates"
                                else "Cached Files"
                            }.distinct()
                            successData = Pair(bytes, categories)
                            viewModel.deleteSmartCleanItems(items)
                        },
                        onDeleteLargeRedundantTempFiles = { items ->
                            val bytes = items.sumOf { it.sizeBytes }
                            val categories = items.map { item ->
                                when (item.category) {
                                    "Large File" -> "Large Files"
                                    "Redundant File" -> "Redundant Files"
                                    else -> "Temporary Files"
                                }
                            }.distinct()
                            successData = Pair(bytes, categories)
                            viewModel.deleteLargeRedundantTempFiles(items)
                        },
                        hasMediaPerm = hasMediaPerm,
                        hasUsagePerm = hasUsagePerm,
                        onNavigateToPermissions = onNavigateToPermissions,
                        onNavigateToBreakdown = onNavigateToBreakdown,
                        onNavigateToAppList = onNavigateToAppList,
                        onNavigateToCategoryDetail = onNavigateToCategoryDetail
                    )
                }

                // Global scanning and loading progress indicator
                if (isLoading) {
                    // Sleek progress bar at the very top of the content area
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                    
                    // Semi-transparent overlay spinner card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable(enabled = true, onClick = {}), // intercept clicks
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(48.dp).testTag("global_loading_spinner")
                                )
                                Text(
                                    text = "Analyzing Storage...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Scanning for large, redundant, and temporary files to optimize your device space.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Animated Success Overlay
        AnimatedVisibility(
            visible = successData != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(durationMillis = 200)
            )
        ) {
            successData?.let { data ->
                CleanSuccessOverlay(
                    data = data,
                    onDismiss = { successData = null }
                )
            }
        }
    }
}

@Composable
fun DashboardContent(
    snapshot: StorageSnapshot?,
    suggestions: List<AdvisorySuggestion>,
    storageTrends: List<StorageTrendPoint>,
    smartCleanItems: List<SmartCleanItem>,
    largeRedundantTempFiles: List<LargeRedundantTempFile>,
    onDeleteSmartCleanItems: (List<SmartCleanItem>) -> Unit,
    onDeleteLargeRedundantTempFiles: (List<LargeRedundantTempFile>) -> Unit,
    hasMediaPerm: Boolean,
    hasUsagePerm: Boolean,
    onNavigateToPermissions: () -> Unit,
    onNavigateToBreakdown: () -> Unit,
    onNavigateToAppList: () -> Unit,
    onNavigateToCategoryDetail: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission Status Alert Card
        if (!hasMediaPerm || !hasUsagePerm) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPermissions() }
                    .testTag("permission_alert_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Permissions missing",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Limited Access Alert",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant media and usage access for complete and accurate storage analysis.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Go to permissions screen",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Storage Overview Card
        if (snapshot != null) {
            StorageOverviewCard(snapshot = snapshot)
            
            // Custom Donut Chart & Legend
            DonutChartCard(
                categories = snapshot.categories,
                onCategoryClick = { categoryName ->
                    if (categoryName == "Apps & Games") {
                        onNavigateToAppList()
                    } else {
                        onNavigateToCategoryDetail(categoryName)
                    }
                }
            )

            // Recharts Interactive Pie Chart Card
            RechartsPieChartCard(
                categories = snapshot.categories,
                onCategoryClick = { categoryName ->
                    if (categoryName == "Apps & Games") {
                        onNavigateToAppList()
                    } else {
                        onNavigateToCategoryDetail(categoryName)
                    }
                }
            )

            // 30-Day Storage Trend Chart
            StorageTrendChart(trends = storageTrends)

            // Smart Clean Widget
            SmartCleanWidget(
                items = smartCleanItems,
                onCleanItems = onDeleteSmartCleanItems
            )

            // Large, Redundant & Temporary Files Cleaner
            LargeRedundantTempFileWidget(
                items = largeRedundantTempFiles,
                onCleanItems = onDeleteLargeRedundantTempFiles
            )

            // Storage Tips & Privacy Advice Widget
            StorageTipsWidget(
                onNavigateToPermissions = onNavigateToPermissions,
                onNavigateToAppList = onNavigateToAppList,
                onNavigateToBreakdown = onNavigateToBreakdown
            )
        } else {
            // Error State / Empty State Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Error loading space",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Unable to Analyze Storage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "We couldn't access local file storage stats. Please try granting permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = onNavigateToPermissions,
                        modifier = Modifier.testTag("fix_permissions_btn")
                    ) {
                        Text("Manage Permissions")
                    }
                }
            }
        }

        // Advisory SpaceWise Cleaning Insights
        if (suggestions.isNotEmpty()) {
            Text(
                text = "Advisory Cleaning Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                suggestions.forEach { suggestion ->
                    AdvisorySuggestionCard(
                        suggestion = suggestion,
                        onActionClick = {
                            if (suggestion.targetCategory == "Apps") {
                                onNavigateToAppList()
                            } else if (suggestion.targetCategory != null) {
                                onNavigateToCategoryDetail(suggestion.targetCategory)
                            }
                        }
                    )
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToBreakdown() }
                    .testTag("breakdown_shortcut_card"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = "Breakdown",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Full Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Explore all categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToAppList() }
                    .testTag("app_list_shortcut_card"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = "Apps",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "App Sizes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Manage largest apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Bottom privacy note
        Text(
            text = "🔒 Privacy First: All storage calculations are performed entirely offline on your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
        )
    }
}

@Composable
fun StorageOverviewCard(snapshot: StorageSnapshot) {
    val usedFormatted = ByteFormatting.formatByteCount(snapshot.usedBytes)
    val totalFormatted = ByteFormatting.formatByteCount(snapshot.totalBytes)
    val freeFormatted = ByteFormatting.formatByteCount(snapshot.freeBytes)
    val pct = snapshot.usedPercentage
    
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Used Storage",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = usedFormatted,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "of $totalFormatted",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = String.format("%.0f%%", pct),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$freeFormatted Available",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "100% Secure & On-Device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun DonutChartCard(
    categories: List<CategoryStorage>,
    onCategoryClick: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Storage Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )

            // Donut Chart Canvas
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    val strokeWidthPx = 28.dp.toPx()
                    
                    categories.forEach { category ->
                        val sweepAngle = category.percentage * 3.6f
                        if (sweepAngle > 0f) {
                            drawArc(
                                color = category.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidthPx)
                            )
                            startAngle += sweepAngle
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Local",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Storage",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Categories Legend
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCategoryClick(category.name) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(category.color, CircleShape)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = category.formattedSize,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format("%.1f%%", category.percentage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Expand ${category.name}",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdvisorySuggestionCard(
    suggestion: AdvisorySuggestion,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (suggestion.type) {
        SuggestionType.CACHE_CLEANUP -> Icons.Default.Refresh
        SuggestionType.UNUSED_APP -> Icons.Default.Apps
        SuggestionType.LARGE_VIDEO -> Icons.Default.PieChart
        SuggestionType.LARGE_DOCUMENT -> Icons.AutoMirrored.Filled.InsertDriveFile
        SuggestionType.SYSTEM_RECOMMENDATION -> Icons.Default.Storage
    }

    val iconColor = when (suggestion.type) {
        SuggestionType.CACHE_CLEANUP -> MaterialTheme.colorScheme.primary
        SuggestionType.UNUSED_APP -> MaterialTheme.colorScheme.tertiary
        SuggestionType.LARGE_VIDEO -> MaterialTheme.colorScheme.secondary
        SuggestionType.LARGE_DOCUMENT -> MaterialTheme.colorScheme.primary
        SuggestionType.SYSTEM_RECOMMENDATION -> MaterialTheme.colorScheme.error
    }

    val iconBgColor = iconColor.copy(alpha = 0.12f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("suggestion_card_${suggestion.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = suggestion.formattedSavings,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = suggestion.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .align(Alignment.End)
                        .testTag("suggestion_action_${suggestion.id}")
                ) {
                    Text(
                        text = suggestion.actionText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SmartCleanWidget(
    items: List<SmartCleanItem>,
    onCleanItems: (List<SmartCleanItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val totalSavings = remember(items) { items.sumOf { it.sizeBytes } }
    val formattedSavings = remember(totalSavings) { ByteFormatting.formatByteCount(totalSavings) }
    var showDialog by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Space Clean",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "We found ${items.size} redundant and temporary files safe to delete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Potential Savings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formattedSavings,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("smart_clean_now_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clean Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDialog) {
        var selectedItemIds by remember { mutableStateOf(items.map { it.id }.toSet()) }
        val selectedItems = remember(selectedItemIds, items) { items.filter { it.id in selectedItemIds } }
        val selectedSavings = remember(selectedItems) { selectedItems.sumOf { it.sizeBytes } }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Confirm Smart Clean",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Select duplicate and cache items you wish to clear securely. All items are checked by default as they are redundant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items.size) { index ->
                                val item = items[index]
                                val isChecked = item.id in selectedItemIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedItemIds = if (isChecked) {
                                                selectedItemIds - item.id
                                            } else {
                                                selectedItemIds + item.id
                                            }
                                        }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedItemIds = if (checked) {
                                                selectedItemIds + item.id
                                            } else {
                                                selectedItemIds - item.id
                                            }
                                        }
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${if (item.type == SmartCleanType.DUPLICATE) "Duplicate" else "Cache"} • ${ByteFormatting.formatByteCount(item.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Total selected savings badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reclaiming:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = ByteFormatting.formatByteCount(selectedSavings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCleanItems(selectedItems)
                    },
                    enabled = selectedItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_smart_clean_btn")
                ) {
                    Text("Clean Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.testTag("dismiss_smart_clean_btn")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun RechartsPieChartCard(
    categories: List<CategoryStorage>,
    onCategoryClick: (String) -> Unit
) {
    var selectedIndex by remember { mutableStateOf(-1) }
    
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("recharts_pie_chart_card"),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Storage Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Interactive Recharts-inspired Pie Chart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Recharts brand badge
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RECHARTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Pie Chart Canvas
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val haptic = LocalHapticFeedback.current
                
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(categories) {
                            detectTapGestures { offset ->
                                // Calculate angle from center to find which slice was tapped
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val x = offset.x - center.x
                                val y = offset.y - center.y
                                var angle = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toFloat()
                                if (angle < 0) angle += 360f
                                
                                // Adjust for startAngle of -90
                                var relativeAngle = angle - (-90f)
                                if (relativeAngle < 0) relativeAngle += 360f
                                
                                var currentAngle = 0f
                                var foundIndex = -1
                                categories.forEachIndexed { index, cat ->
                                    val sweep = cat.percentage * 3.6f
                                    if (relativeAngle >= currentAngle && relativeAngle < currentAngle + sweep) {
                                        foundIndex = index
                                    }
                                    currentAngle += sweep
                                }
                                
                                if (foundIndex != -1) {
                                    selectedIndex = if (selectedIndex == foundIndex) -1 else foundIndex
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                ) {
                    var startAngle = -90f
                    val outerRadius = size.minDimension / 2f
                    val innerRadius = outerRadius * 0.45f // Classic Recharts donut/pie structure
                    
                    categories.forEachIndexed { index, category ->
                        val sweepAngle = category.percentage * 3.6f
                        if (sweepAngle > 0f) {
                            val isSelected = index == selectedIndex
                            val extraScale = if (isSelected) 10.dp.toPx() else 0f
                            
                            // Draw the main category slice arc
                            drawArc(
                                color = category.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = (outerRadius - innerRadius) + extraScale)
                            )
                            
                            // Draw highlight boundary accent if slice selected
                            if (isSelected) {
                                drawArc(
                                    color = category.color.copy(alpha = 0.25f),
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = 10.dp.toPx() + (outerRadius - innerRadius))
                                )
                            }
                            
                            startAngle += sweepAngle
                        }
                    }
                }
                
                // Center Tooltip text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedIndex in categories.indices) {
                        val activeCategory = categories[selectedIndex]
                        Text(
                            text = activeCategory.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = activeCategory.color,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeCategory.formattedSize,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format("%.1f%%", activeCategory.percentage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Tap a Slice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "To Inspect",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { index, category ->
                    val isSelected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) category.color.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable {
                                selectedIndex = if (selectedIndex == index) -1 else index
                            }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(category.color, CircleShape)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) category.color else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = category.formattedSize,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format("%.1f%%", category.percentage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LargeRedundantTempFileWidget(
    items: List<LargeRedundantTempFile>,
    onCleanItems: (List<LargeRedundantTempFile>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val totalSavings = remember(items) { items.sumOf { it.sizeBytes } }
    val formattedSavings = remember(totalSavings) { ByteFormatting.formatByteCount(totalSavings) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(items.map { it.id }.toSet()) }

    val haptic = LocalHapticFeedback.current

    val selectedItems = remember(selectedItemIds, items) { items.filter { it.id in selectedItemIds } }
    val selectedSavings = remember(selectedItems) { selectedItems.sumOf { it.sizeBytes } }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        ),
        modifier = modifier.fillMaxWidth().testTag("large_files_cleaner_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Large & Redundant Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "We found ${items.size} oversized, duplicate, or cache files taking up unnecessary space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scrollable list of files
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items.size) { index ->
                        val item = items[index]
                        val isChecked = item.id in selectedItemIds
                        
                        val categoryColor = when (item.category) {
                            "Large File" -> MaterialTheme.colorScheme.error
                            "Redundant File" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedItemIds = if (isChecked) {
                                        selectedItemIds - item.id
                                    } else {
                                        selectedItemIds + item.id
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedItemIds = if (checked) {
                                        selectedItemIds + item.id
                                    } else {
                                        selectedItemIds - item.id
                                    }
                                }
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = categoryColor.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, categoryColor.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = categoryColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = item.filePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            // Savings and clean button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Selected for Cleanup",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ByteFormatting.formatByteCount(selectedSavings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showConfirmDialog = true
                    },
                    enabled = selectedItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("clean_large_files_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clean Selected", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Permanent Deletion",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you absolutely sure you want to delete the ${selectedItems.size} selected file(s)?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "This action is irreversible and will permanently delete these files from your device to free up ${ByteFormatting.formatByteCount(selectedSavings)} of storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Files to be deleted:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        selectedItems.take(3).forEach { file ->
                            Text(
                                text = "• ${file.name} (${file.formattedSize})",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selectedItems.size > 3) {
                            Text(
                                text = "• and ${selectedItems.size - 3} more file(s)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCleanItems(selectedItems)
                        selectedItemIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_large_files_btn")
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier.testTag("dismiss_delete_large_files_btn")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

