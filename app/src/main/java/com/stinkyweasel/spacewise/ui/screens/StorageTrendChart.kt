package com.stinkyweasel.spacewise.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stinkyweasel.spacewise.data.models.ByteFormatting
import com.stinkyweasel.spacewise.data.models.StorageTrendPoint

@Composable
fun StorageTrendChart(
    trends: List<StorageTrendPoint>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    // Calculate vertical scaling limits with 10% breathing padding
    val rawMin = remember(trends) { trends.minOfOrNull { it.usedBytes } ?: 0L }
    val rawMax = remember(trends) { trends.maxOfOrNull { it.usedBytes } ?: 1L }
    val diff = (rawMax - rawMin).coerceAtLeast(1L)
    val pad = (diff * 0.12f).toLong().coerceAtLeast(100_000_000L) // Min padding 100MB
    val minVal = (rawMin - pad).coerceAtLeast(0L)
    val maxVal = rawMax + pad

    val maxFormatted = remember(maxVal) { ByteFormatting.formatByteCount(maxVal) }
    val minFormatted = remember(minVal) { ByteFormatting.formatByteCount(minVal) }
    val midFormatted = remember(minVal, maxVal) { ByteFormatting.formatByteCount((minVal + maxVal) / 2) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "30-Day Storage Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reclaimed space updates are instantly plotted below",
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor
                    )
                }

                // Small legend badge
                Box(
                    modifier = Modifier
                        .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Live Space",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 8.dp)
            ) {
                // Background grid markings
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    GridLineRow(maxFormatted, gridColor)
                    GridLineRow(midFormatted, gridColor)
                    GridLineRow(minFormatted, gridColor)
                }

                // Canvas line drawing
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val pointsCount = trends.size

                    if (pointsCount < 2) return@Canvas

                    val valueRange = (maxVal - minVal).toFloat().coerceAtLeast(1f)
                    val stepX = width / (pointsCount - 1)

                    val coords = trends.mapIndexed { idx, point ->
                        val x = idx * stepX
                        val fraction = (point.usedBytes - minVal).toFloat() / valueRange
                        val y = height - (fraction * height)
                        Offset(x, y)
                    }

                    // Create Bezier path for curve smoothness
                    val linePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.size - 1) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            // Cubic Bezier control points for organic curves
                            val controlX = (p0.x + p1.x) / 2f
                            cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                        }
                    }

                    // Create full fill path
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(coords.last().x, height)
                        lineTo(coords.first().x, height)
                        close()
                    }

                    // Draw filled gradient area
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw main trend line
                    drawPath(
                        path = linePath,
                        color = primaryColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw dynamic glow and indicator dot on the final (30th) day
                    val lastCoord = coords.last()
                    drawCircle(
                        color = secondaryColor,
                        radius = 6.dp.toPx(),
                        center = lastCoord
                    )
                    drawCircle(
                        color = secondaryColor.copy(alpha = 0.4f),
                        radius = 12.dp.toPx(),
                        center = lastCoord
                    )
                }
            }

            // Bottom axis labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = trends.firstOrNull()?.dateLabel ?: "",
                    fontSize = 11.sp,
                    color = labelColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = trends.getOrNull(trends.size / 2)?.dateLabel ?: "",
                    fontSize = 11.sp,
                    color = labelColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Today",
                    fontSize = 11.sp,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GridLineRow(
    label: String,
    gridColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
            drawLine(
                color = gridColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = gridColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(max = 70.dp)
        )
    }
}
