package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class StorageTip(
    val id: Int,
    val title: String,
    val category: String,
    val icon: ImageVector,
    val advice: String,
    val actionLabel: String,
    val actionType: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StorageTipsWidget(
    onNavigateToPermissions: () -> Unit,
    onNavigateToAppList: () -> Unit,
    onNavigateToBreakdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val tips = remember {
        listOf(
            StorageTip(
                id = 1,
                title = "Clear Browser Cache",
                category = "Privacy Footprint",
                icon = Icons.Default.Lock,
                advice = "Your browser cache stores unencrypted session data, cookies, and local trackers. Clearing browser storage regularly protects your digital identity and reclaims significant space.",
                actionLabel = "Manage Permissions",
                actionType = "PERMISSIONS"
            ),
            StorageTip(
                id = 2,
                title = "Audit Offline Maps",
                category = "Location History",
                icon = Icons.Default.Storage,
                advice = "Downloaded offline map databases store detailed location histories. Purging map databases you no longer need prevents historic location tracking and frees gigabytes of space.",
                actionLabel = "View Breakdown",
                actionType = "BREAKDOWN"
            ),
            StorageTip(
                id = 3,
                title = "Clean Chat Media",
                category = "Data Exposure",
                icon = Icons.Default.Warning,
                advice = "Chat apps automatically download photos, audio logs, and videos in the background. Disabling auto-downloads in chat settings keeps personal files private and saves major storage.",
                actionLabel = "Explore Apps",
                actionType = "APPS"
            ),
            StorageTip(
                id = 4,
                title = "Revoke Unused App Rights",
                category = "Background Telemetry",
                icon = Icons.Default.Lock,
                advice = "Inactive apps with All-Files-Access or Usage-Stats permissions continuously stream diagnostic logs and accumulate telemetry caches. Revoke access for zombie apps.",
                actionLabel = "Manage Permissions",
                actionType = "PERMISSIONS"
            ),
            StorageTip(
                id = 5,
                title = "Scrub Hidden Thumbnails",
                category = "Hidden Duplicates",
                icon = Icons.Default.Storage,
                advice = "Android caches hidden `.thumbnails` folders to render galleries. These folders persist long after original photos are deleted, hoarding massive gigabytes of redundant imagery.",
                actionLabel = "View Breakdown",
                actionType = "BREAKDOWN"
            ),
            StorageTip(
                id = 6,
                title = "Audit Accumulating Apps",
                category = "App Storage",
                icon = Icons.Default.Apps,
                advice = "Unopened apps store local state logs, track diagnostic parameters, and grow heavier. Offloading apps you haven't opened in 30 days is a secure way to maximize storage.",
                actionLabel = "Explore Apps",
                actionType = "APPS"
            )
        )
    }

    var currentTipIndex by remember { mutableStateOf(0) }
    val currentTip = tips[currentTipIndex]

    // Periodic rotation timer (rotates every 12 seconds)
    var isTimerRunning by remember { mutableStateOf(true) }
    LaunchedEffect(isTimerRunning, currentTipIndex) {
        if (isTimerRunning) {
            delay(12000L)
            currentTipIndex = (currentTipIndex + 1) % tips.size
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_tips_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Section title & Manual/Auto controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Storage & Privacy Tips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Play / Pause auto rotation
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isTimerRunning = !isTimerRunning
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("toggle_rotation_btn")
                ) {
                    Icon(
                        imageVector = if (isTimerRunning) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isTimerRunning) "Pause rotation" else "Resume rotation",
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Crossfade transitions for smooth rotation animation
            AnimatedContent(
                targetState = currentTip,
                transitionSpec = {
                    fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                },
                label = "TipRotationAnimation"
            ) { tip ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Category Badge and Index indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tip.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = "${tip.id} / ${tips.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }

                    // Content details
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = tip.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(top = 2.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = tip.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tip.advice,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Action buttons (Dynamic Callback depending on type)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Action Button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (tip.actionType) {
                                    "PERMISSIONS" -> onNavigateToPermissions()
                                    "APPS" -> onNavigateToAppList()
                                    "BREAKDOWN" -> onNavigateToBreakdown()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("tip_action_btn_${tip.id}")
                        ) {
                            Text(
                                text = tip.actionLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Arrow cycling buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentTipIndex = if (currentTipIndex == 0) tips.size - 1 else currentTipIndex - 1
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("prev_tip_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Tip",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentTipIndex = (currentTipIndex + 1) % tips.size
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("next_tip_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Tip",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
