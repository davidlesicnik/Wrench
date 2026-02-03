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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.statistics.CostTrendPoint
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.ServiceBlue
import java.text.NumberFormat
import kotlin.math.abs

@Composable
fun CostTrendsChart(
    trends: List<CostTrendPoint>,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Cost Trends",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (trends.isEmpty() || trends.all { it.fuelCost == 0.0 && it.otherCost == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No cost data in this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val fuelValues = trends.map { it.fuelCost.toFloat() }
                val otherValues = trends.map { it.otherCost.toFloat() }
                val maxValue = remember(trends) {
                    maxOf(fuelValues.max(), otherValues.max()).coerceAtLeast(1f)
                }

                var selectedPoint by remember { mutableStateOf<Int?>(null) }
                var tooltipPosition by remember { mutableStateOf(Offset.Zero) }

                // Reset selection when data changes
                LaunchedEffect(trends) {
                    selectedPoint = null
                }

                val density = LocalDensity.current

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    val boxWidth = with(density) { maxWidth.toPx() }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 24.dp)
                            .pointerInput(trends) {
                                detectTapGestures { tapOffset ->
                                    val chartWidth = size.width.toFloat()
                                    val chartHeight = size.height.toFloat()
                                    val pointCount = trends.size
                                    if (pointCount < 2) return@detectTapGestures
                                    val xStep = chartWidth / (pointCount - 1)

                                    // Find closest point
                                    var closestIndex = 0
                                    var closestDistance = Float.MAX_VALUE
                                    trends.forEachIndexed { index, _ ->
                                        val pointX = index * xStep
                                        val distance = abs(tapOffset.x - pointX)
                                        if (distance < closestDistance) {
                                            closestDistance = distance
                                            closestIndex = index
                                        }
                                    }

                                    if (closestDistance < with(density) { 40.dp.toPx() }) {
                                        selectedPoint = closestIndex
                                        val fuelY = (1 - fuelValues[closestIndex] / maxValue) * chartHeight
                                        tooltipPosition = Offset(closestIndex * xStep, fuelY)
                                    } else {
                                        selectedPoint = null
                                    }
                                }
                            }
                    ) {
                        val chartWidth = size.width
                        val chartHeight = size.height
                        val pointCount = trends.size

                        if (pointCount < 2) return@Canvas

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

                        // Draw fuel line
                        drawLine(
                            values = fuelValues,
                            maxValue = maxValue,
                            chartHeight = chartHeight,
                            xStep = xStep,
                            color = FuelGreen
                        )

                        // Draw other costs line
                        drawLine(
                            values = otherValues,
                            maxValue = maxValue,
                            chartHeight = chartHeight,
                            xStep = xStep,
                            color = ServiceBlue
                        )
                    }

                    // X-axis labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val labelsToShow = when {
                            trends.size <= 6 -> trends.map { it.label }
                            trends.size <= 12 -> trends.filterIndexed { i, _ -> i % 2 == 0 }.map { it.label }
                            else -> listOf(trends.first().label, trends[trends.size / 2].label, trends.last().label)
                        }
                        labelsToShow.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Tooltip
                    selectedPoint?.takeIf { it < trends.size }?.let { index ->
                        val point = trends[index]
                        val xOffset = with(density) {
                            (8.dp.toPx() + tooltipPosition.x - 70.dp.toPx()).coerceIn(
                                0f,
                                boxWidth - 140.dp.toPx()
                            )
                        }
                        val yOffset = with(density) {
                            (16.dp.toPx() + tooltipPosition.y - 80.dp.toPx()).coerceAtLeast(0f)
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
                            Column {
                                Text(
                                    text = point.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(FuelGreen, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Fuel: ${currencyFormatter.format(point.fuelCost)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(ServiceBlue, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Other: ${currencyFormatter.format(point.otherCost)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(FuelGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fuel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(ServiceBlue, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Other",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLine(
    values: List<Float>,
    maxValue: Float,
    chartHeight: Float,
    xStep: Float,
    color: Color
) {
    if (values.size < 2) return

    val path = Path()
    val normalizedPoints = values.map { (1 - it / maxValue) * chartHeight }

    path.moveTo(0f, normalizedPoints[0])
    for (i in 1 until normalizedPoints.size) {
        path.lineTo(i * xStep, normalizedPoints[i])
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3.dp.toPx())
    )
}
