package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Finds the closest data point index to a tap position.
 * Returns the index and whether it's within the tap threshold.
 */
fun findClosestPointIndex(
    tapX: Float,
    pointCount: Int,
    chartWidth: Float,
    tapThresholdPx: Float
): Pair<Int, Boolean> {
    if (pointCount < 2) return 0 to false

    val xStep = chartWidth / (pointCount - 1)
    var closestIndex = 0
    var closestDistance = Float.MAX_VALUE

    for (index in 0 until pointCount) {
        val pointX = index * xStep
        val distance = abs(tapX - pointX)
        if (distance < closestDistance) {
            closestDistance = distance
            closestIndex = index
        }
    }

    return closestIndex to (closestDistance < tapThresholdPx)
}

/**
 * Draws horizontal grid lines on a chart canvas.
 */
fun DrawScope.drawGridLines(
    gridColor: Color,
    chartWidth: Float,
    chartHeight: Float,
    gridLines: Int = 4
) {
    for (i in 0..gridLines) {
        val y = (chartHeight / gridLines) * i
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * Draws a line chart path for given values.
 */
fun DrawScope.drawLinePath(
    values: List<Float>,
    maxValue: Float,
    minValue: Float,
    chartHeight: Float,
    xStep: Float,
    color: Color,
    strokeWidth: Dp = 3.dp
) {
    if (values.size < 2) return

    val range = (maxValue - minValue).coerceAtLeast(0.001f)
    val path = Path()
    val normalizedPoints = values.map { ((maxValue - it) / range) * chartHeight }

    path.moveTo(0f, normalizedPoints[0])
    for (i in 1 until normalizedPoints.size) {
        path.lineTo(i * xStep, normalizedPoints[i])
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth.toPx())
    )
}

/**
 * Calculates tooltip offset position, ensuring it stays within bounds.
 */
fun calculateTooltipOffset(
    density: Density,
    tooltipPosition: Offset,
    chartStartPadding: Dp,
    tooltipWidth: Dp,
    tooltipHeight: Dp,
    containerWidth: Float
): IntOffset {
    val xOffset = with(density) {
        (chartStartPadding.toPx() + tooltipPosition.x - tooltipWidth.toPx() / 2).coerceIn(
            0f,
            containerWidth - tooltipWidth.toPx()
        )
    }
    val yOffset = with(density) {
        (16.dp.toPx() + tooltipPosition.y - tooltipHeight.toPx()).coerceAtLeast(0f)
    }
    return IntOffset(xOffset.toInt(), yOffset.toInt())
}

/**
 * Base tooltip container with consistent styling.
 */
@Composable
fun ChartTooltip(
    offset: IntOffset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .offset { offset }
            .background(
                MaterialTheme.colorScheme.inverseSurface,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        content()
    }
}

/**
 * Standard two-line tooltip content (label + value).
 */
@Composable
fun TooltipContent(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}
