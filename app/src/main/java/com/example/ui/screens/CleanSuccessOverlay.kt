package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ByteFormatting

@Composable
fun CleanSuccessOverlay(
    data: Pair<Long, List<String>>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ConfettiInfinite")
    
    // Smooth angle rotation for confetti particles
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AngleRotation"
    )
    
    // Slow bouncing pulse for the checkmark halo glow
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Confetti positions around the center
    val particles = remember {
        List(18) { index ->
            val radius = (80..130).random().toFloat()
            val size = (6..12).random().toFloat()
            val startAngle = (0..360).random().toDouble() * Math.PI / 180.0
            val color = listOf(
                Color(0xFF00BFA5), // SpaceWise Teal
                Color(0xFF4CAF50), // Vibrant Green
                Color(0xFF2196F3), // Tech Blue
                Color(0xFFFFB300), // Sunshine Amber
                Color(0xFFE91E63)  // Playful Pink
            ).random()
            Triple(radius, size, startAngle) to color
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = true, onClick = {}) // Intercept and block clicks on background
            .testTag("success_overlay_backdrop"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.92f)
                .testTag("success_overlay_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top header bar with absolute-positioned close button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("success_overlay_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Checkmark and floating sparkles canvas container
                Box(
                    modifier = Modifier
                        .size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Confetti rotation canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        particles.forEach { (config, color) ->
                            val (radius, size, startAngle) = config
                            val currentAngle = startAngle + (angle * Math.PI / 180.0)
                            val x = center.x + (radius.dp.toPx() * Math.cos(currentAngle)).toFloat()
                            val y = center.y + (radius.dp.toPx() * Math.sin(currentAngle)).toFloat()
                            
                            drawCircle(
                                color = color,
                                radius = (size.dp.toPx() / 2),
                                center = Offset(x, y)
                            )
                        }
                    }

                    // Pulsing outer border halo ring
                    Box(
                        modifier = Modifier
                            .size(114.dp)
                            .scale(pulseScale)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    )

                    // Glow background bubble
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .scale(pulseScale)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Title and subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Clean Successful!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Redundant files were securely disposed of.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Oversized Reclaimed Storage Badge
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "TOTAL RECLAIMED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = ByteFormatting.formatByteCount(data.first),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                // Details checklist of cleared categories
                if (data.second.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Items Cleared:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        data.second.forEach { categoryName ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Satisfying primary completion button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("success_overlay_done_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Text(
                        text = "Excellent!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
