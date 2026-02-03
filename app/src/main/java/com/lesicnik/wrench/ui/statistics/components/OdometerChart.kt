package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.statistics.OdometerPoint
import java.text.NumberFormat
import kotlin.math.abs

@Composable
fun OdometerChart(
    odometerTrends: List<OdometerPoint>,
    odometerUnit: String,
    modifier: Modifier = Modifier
) {
    // Only show if we have at least 2 data points
    if (odometerTrends.size < 2) {
        return
    }

    val chartColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val numberFormatter = remember { NumberFormat.getNumberInstance() }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Odometer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            val values = odometerTrends.map { it.odometer.toFloat() }
            val maxValue = remember(values) { values.max() }
            val minValue = remember(values) { values.min() }
            val range = (maxValue - minValue).coerceAtLeast(1f)

            var selectedPoint by remember { mutableStateOf<Int?>(null) }
            var tooltipPosition by remember { mutableStateOf(Offset.Zero) }

            // Reset selection when data changes
            LaunchedEffect(odometerTrends) {
                selectedPoint = null
            }

            val density = LocalDensity.current

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val boxWidth = with(density) { maxWidth.toPx() }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(start = 48.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                        .pointerInput(odometerTrends) {
                            detectTapGestures { tapOffset ->
                                val chartWidth = size.width.toFloat()
                                val chartHeight = size.height.toFloat()
                                val pointCount = values.size
                                val xStep = chartWidth / (pointCount - 1)

                                // Find closest point
                                var closestIndex = 0
                                var closestDistance = Float.MAX_VALUE
                                values.forEachIndexed { index, _ ->
                                    val pointX = index * xStep
                                    val distance = abs(tapOffset.x - pointX)
                                    if (distance < closestDistance) {
                                        closestDistance = distance
                                        closestIndex = index
                                    }
                                }

                                // Only select if tap is close enough (within 40dp)
                                if (closestDistance < with(density) { 40.dp.toPx() }) {
                                    selectedPoint = closestIndex
                                    val normalizedY =
                                        ((maxValue - values[closestIndex]) / range) * chartHeight
                                    tooltipPosition =
                                        Offset(closestIndex * xStep, normalizedY)
                                } else {
                                    selectedPoint = null
                                }
                            }
                        }
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    val pointCount = values.size
                    val xStep = chartWidth / (pointCount - 1)

                    // Draw horizontal grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = (chartHeight / gridLines) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Draw line chart
                    val path = Path()
                    val normalizedPoints = values.map { ((maxValue - it) / range) * chartHeight }

                    path.moveTo(0f, normalizedPoints[0])
                    for (i in 1 until normalizedPoints.size) {
                        path.lineTo(i * xStep, normalizedPoints[i])
                    }

                    drawPath(
                        path = path,
                        color = chartColor,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // Y-axis labels
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(180.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = numberFormatter.format(maxValue.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = numberFormatter.format(((maxValue + minValue) / 2).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = numberFormatter.format(minValue.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Tooltip
                selectedPoint?.takeIf { it < odometerTrends.size }?.let { index ->
                    val point = odometerTrends[index]
                    val xOffset = with(density) {
                        (48.dp.toPx() + tooltipPosition.x - 60.dp.toPx()).coerceIn(
                            0f,
                            boxWidth - 120.dp.toPx()
                        )
                    }
                    val yOffset = with(density) {
                        (16.dp.toPx() + tooltipPosition.y - 48.dp.toPx()).coerceAtLeast(0f)
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(xOffset.toInt(), yOffset.toInt()) }
                            .background(
                                MaterialTheme.colorScheme.inverseSurface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                            Text(
                                text = "${numberFormatter.format(point.odometer)} $odometerUnit",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }

            Text(
                text = "$odometerUnit over time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
