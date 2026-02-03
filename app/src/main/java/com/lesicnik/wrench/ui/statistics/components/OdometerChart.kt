package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import com.lesicnik.wrench.ui.statistics.OdometerPoint
import java.text.NumberFormat

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
                                    val normalizedY = ((maxValue - values[closestIndex]) / range) * chartHeight
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
                    drawLinePath(values, maxValue, minValue, chartHeight, xStep, chartColor)
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
                    val offset = calculateTooltipOffset(
                        density = density,
                        tooltipPosition = tooltipPosition,
                        chartStartPadding = 48.dp,
                        tooltipWidth = 120.dp,
                        tooltipHeight = 48.dp,
                        containerWidth = boxWidth
                    )

                    ChartTooltip(offset = offset) {
                        TooltipContent(
                            label = point.label,
                            value = "${numberFormatter.format(point.odometer)} $odometerUnit"
                        )
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
