package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMediaPerm by remember { mutableStateOf(PermissionUtils.hasMediaPermissions(context)) }
    var hasFullMedia by remember { mutableStateOf(PermissionUtils.hasFullMediaPermissions(context)) }
    var hasUsagePerm by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }

    // Re-check permissions when user resumes back to app (e.g. from usage settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
                hasFullMedia = PermissionUtils.hasFullMediaPermissions(context)
                hasUsagePerm = PermissionUtils.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Media permissions request launcher
    val permissionsToRequest = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
        hasFullMedia = PermissionUtils.hasFullMediaPermissions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configure Access",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "SpaceWise operates entirely locally on your phone. To scan your storage and categorize files, we require the following permissions. No data is ever collected or sent to any server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Media Files Permission Card
            PermissionCard(
                title = "Media Files",
                description = "Required to accurately scan and calculate sizes for Photos, Videos, and Audio. Supports Android 14 partial access.",
                isGranted = hasMediaPerm,
                isLimited = hasMediaPerm && !hasFullMedia,
                icon = Icons.Default.PermMedia,
                actionText = if (hasMediaPerm) "Access Allowed" else "Grant Media Permission",
                onAction = { launcher.launch(permissionsToRequest) },
                tag = "media_permission_card"
            )

            // 2. Usage Stats Permission Card
            PermissionCard(
                title = "App Usage Stats",
                description = "Required to query package sizes (app, cache, and user data) and find long-neglected apps using system statistics.",
                isGranted = hasUsagePerm,
                isLimited = false,
                icon = Icons.Default.Storage,
                actionText = if (hasUsagePerm) "Access Granted" else "Configure in Settings",
                onAction = {
                    try {
                        context.startActivity(PermissionUtils.getUsageAccessSettingsIntent(context))
                    } catch (e: Exception) {
                        // Safe fallback in case some OEMs block settings intents
                    }
                },
                tag = "usage_permission_card"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Info Card about Privacy
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Privacy Commitment",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Privacy Guarantee",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "We do not declare Internet permissions in our manifest. Your files and usage habits never leave this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (hasMediaPerm && hasUsagePerm) {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("continue_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Set! Continue", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("skip_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Analyze with Limited Access")
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    isLimited: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionText: String,
    onAction: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isGranted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Granted",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isLimited) "Limited/Selected Access" else "Active",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLimited) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "Access Required",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onAction,
                enabled = !isGranted || isLimited,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionText, fontWeight = FontWeight.Bold)
            }
        }
    }
}
