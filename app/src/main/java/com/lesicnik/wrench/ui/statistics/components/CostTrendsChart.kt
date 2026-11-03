package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.settings.LocalAppPreferences
import com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter
import com.lesicnik.wrench.ui.statistics.CostTrendPoint
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.ServiceBlue
import kotlin.math.roundToInt

@Composable
fun CostTrendsChart(trends: List<CostTrendPoint>, modifier: Modifier = Modifier) {
    val formatter = rememberCurrencyFormatter()
    val axisFormatter = rememberCurrencyFormatter().apply { maximumFractionDigits = 0 }
    val currencyCode = LocalAppPreferences.current.currencyCode
    val grid = MaterialTheme.colorScheme.outlineVariant
    val selectionColor = MaterialTheme.colorScheme.onSurface
    var selected by remember(trends) { mutableStateOf(trends.lastIndex.coerceAtLeast(0)) }
    Card(modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cost Trends ($currencyCode)", style = MaterialTheme.typography.titleMedium)
            if (trends.isEmpty() || trends.all { it.fuelCost == 0.0 && it.otherCost == 0.0 }) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("No cost data in this period")
                }
            } else {
                val rawMax = trends.maxOf { maxOf(it.fuelCost, it.otherCost) }.coerceAtLeast(1.0)
                val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(rawMax / 4)))
                val max = (kotlin.math.ceil(rawMax / 4 / magnitude) * magnitude * 4).toFloat()
                Row(Modifier.fillMaxWidth().height(180.dp)) {
                    Column(Modifier.widthIn(min = 56.dp).fillMaxHeight().padding(end = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        (4 downTo 0).forEach { tick ->
                            Text(axisFormatter.format(max * tick / 4), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)
                        .semantics { contentDescription = "Fuel and other monthly costs in $currencyCode. Choose a month below for exact values." }
                        .pointerInput(trends) {
                            detectTapGestures { tap ->
                                selected = if (trends.size == 1) 0 else
                                    (tap.x / size.width * (trends.size - 1)).roundToInt().coerceIn(0, trends.lastIndex)
                            }
                        }) {
                        val step = if (trends.size > 1) size.width / (trends.size - 1) else 0f
                        fun x(index: Int) = if (trends.size == 1) size.width / 2 else index * step
                        drawGridLines(grid, size.width, size.height)
                        if (trends.size > 1) {
                            drawLinePath(trends.map { it.fuelCost.toFloat() }, max, 0f, size.height, step, FuelGreen)
                            drawLinePath(trends.map { it.otherCost.toFloat() }, max, 0f, size.height, step, ServiceBlue)
                        }
                        trends.forEachIndexed { index, point ->
                            drawCircle(FuelGreen, 3.dp.toPx(), Offset(x(index), (1 - point.fuelCost.toFloat() / max) * size.height))
                            drawCircle(ServiceBlue, 3.dp.toPx(), Offset(x(index), (1 - point.otherCost.toFloat() / max) * size.height))
                        }
                        val point = trends[selected.coerceIn(0, trends.lastIndex)]
                        drawLine(selectionColor.copy(alpha = 0.4f), Offset(x(selected), 0f), Offset(x(selected), size.height), 1.dp.toPx())
                        drawCircle(FuelGreen, 6.dp.toPx(), Offset(x(selected), (1 - point.fuelCost.toFloat() / max) * size.height), style = Stroke(2.dp.toPx()))
                        drawCircle(ServiceBlue, 6.dp.toPx(), Offset(x(selected), (1 - point.otherCost.toFloat() / max) * size.height), style = Stroke(2.dp.toPx()))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 56.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(trends.first().label, style = MaterialTheme.typography.labelSmall)
                    if (trends.size > 1) Text(trends.last().label, style = MaterialTheme.typography.labelSmall)
                }
                Text("Tap a point or choose a month", style = MaterialTheme.typography.bodySmall)
                val monthListState = rememberLazyListState()
                LaunchedEffect(selected) { monthListState.animateScrollToItem(selected) }
                LazyRow(Modifier.fillMaxWidth(), state = monthListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(trends) { index, point ->
                        FilterChip(selected = index == selected, onClick = { selected = index }, label = { Text(point.label) })
                    }
                }
                val point = trends[selected.coerceIn(0, trends.lastIndex)]
                Text(point.label, style = MaterialTheme.typography.titleSmall)
                Text("Fuel: ${formatter.format(point.fuelCost)}", color = FuelGreen)
                Text("Other: ${formatter.format(point.otherCost)}", color = ServiceBlue)
            }
        }
    }
}
