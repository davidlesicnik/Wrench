package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.lesicnik.wrench.ui.statistics.FuelEconomyPoint
import com.lesicnik.wrench.ui.theme.FuelGreen
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun FuelEconomyChart(
    fuelEconomyTrends: List<FuelEconomyPoint>,
    modifier: Modifier = Modifier
) {
    // Only show if we have at least 2 data points
    if (fuelEconomyTrends.size < 2) {
        return
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Fuel Economy",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            val values = fuelEconomyTrends.map { it.consumption.toFloat() }
            val maxValue = remember(values) { values.max() }
            val minValue = remember(values) { values.min() }
            val range = (maxValue - minValue).coerceAtLeast(0.5f)
            // Add padding to range for better visualization
            val paddedMax = maxValue + range * 0.1f
            val paddedMin = (minValue - range * 0.1f).coerceAtLeast(0f)
            val paddedRange = paddedMax - paddedMin

            var selectedPoint by remember { mutableStateOf<Int?>(null) }
            var tooltipPosition by remember { mutableStateOf(Offset.Zero) }

            // Reset selection when data changes
            LaunchedEffect(fuelEconomyTrends) {
                selectedPoint = null
            }

            val density = LocalDensity.current

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val boxWidth = with(density) { maxWidth.toPx() }

                // Y-axis labels
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(160.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f", paddedMax),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f", (paddedMax + paddedMin) / 2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f", paddedMin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(start = 40.dp, end = 8.dp, top = 16.dp, bottom = 24.dp)
                        .pointerInput(fuelEconomyTrends) {
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

                                if (closestDistance < with(density) { 40.dp.toPx() }) {
                                    selectedPoint = closestIndex
                                    val normalizedY = ((paddedMax - values[closestIndex]) / paddedRange) * chartHeight
                                    tooltipPosition = Offset(closestIndex * xStep, normalizedY)
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
                    val normalizedPoints = values.map { ((paddedMax - it) / paddedRange) * chartHeight }

                    path.moveTo(0f, normalizedPoints[0])
                    for (i in 1 until normalizedPoints.size) {
                        path.lineTo(i * xStep, normalizedPoints[i])
                    }

                    drawPath(
                        path = path,
                        color = FuelGreen,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // X-axis labels
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 40.dp, end = 8.dp, bottom = 4.dp)
                ) {
                    val labelsToShow = when {
                        fuelEconomyTrends.size <= 4 -> fuelEconomyTrends.mapIndexed { i, p -> i to p.date.format(dateFormatter) }
                        else -> listOf(
                            0 to fuelEconomyTrends.first().date.format(dateFormatter),
                            fuelEconomyTrends.lastIndex to fuelEconomyTrends.last().date.format(dateFormatter)
                        )
                    }
                    labelsToShow.forEach { (index, label) ->
                        val fraction = if (fuelEconomyTrends.size > 1) {
                            index.toFloat() / (fuelEconomyTrends.size - 1)
                        } else 0f
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(
                                when {
                                    fraction < 0.3f -> Alignment.CenterStart
                                    fraction > 0.7f -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }
                            )
                        )
                    }
                }

                // Tooltip
                selectedPoint?.takeIf { it < fuelEconomyTrends.size }?.let { index ->
                    val point = fuelEconomyTrends[index]
                    val xOffset = with(density) {
                        (40.dp.toPx() + tooltipPosition.x - 50.dp.toPx()).coerceIn(
                            0f,
                            boxWidth - 100.dp.toPx()
                        )
                    }
                    val yOffset = with(density) {
                        (16.dp.toPx() + tooltipPosition.y - 56.dp.toPx()).coerceAtLeast(0f)
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
                                text = point.date.format(dateFormatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f L/100km", point.consumption),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }

            // Min, Max, Avg stats
            val avgValue = values.average()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Best",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f L/100", minValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Average",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f L/100", avgValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Worst",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f L/100", maxValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
