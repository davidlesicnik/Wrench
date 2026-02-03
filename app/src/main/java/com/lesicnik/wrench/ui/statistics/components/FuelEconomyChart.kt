package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.statistics.FuelEconomyPoint
import com.lesicnik.wrench.ui.theme.FuelGreen
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                                val xStep = chartWidth / (values.size - 1)
                                val tapThreshold = with(density) { 40.dp.toPx() }

                                val (closestIndex, isWithinThreshold) = findClosestPointIndex(
                                    tapX = tapOffset.x,
                                    pointCount = values.size,
                                    chartWidth = chartWidth,
                                    tapThresholdPx = tapThreshold
                                )

                                if (isWithinThreshold) {
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
                    val xStep = chartWidth / (values.size - 1)

                    drawGridLines(gridColor, chartWidth, chartHeight)
                    drawLinePath(values, paddedMax, paddedMin, chartHeight, xStep, FuelGreen)
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
                    val offset = calculateTooltipOffset(
                        density = density,
                        tooltipPosition = tooltipPosition,
                        chartStartPadding = 40.dp,
                        tooltipWidth = 100.dp,
                        tooltipHeight = 56.dp,
                        containerWidth = boxWidth
                    )

                    ChartTooltip(offset = offset) {
                        TooltipContent(
                            label = point.date.format(dateFormatter),
                            value = String.format(Locale.getDefault(), "%.1f L/100km", point.consumption)
                        )
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
