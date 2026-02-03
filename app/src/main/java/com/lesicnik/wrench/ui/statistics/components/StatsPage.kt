package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.statistics.KeyStatistics
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.ServiceBlue
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StatsPage(
    statistics: KeyStatistics?,
    odometerUnit: String,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (statistics == null || statistics.expenseCount == 0) {
            EmptyStatsContent()
        } else {
            // First row: Total Cost and Cost per distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Total Spent",
                    value = currencyFormatter.format(statistics.totalCost),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Cost per $odometerUnit",
                    value = statistics.averageCostPerKm?.let {
                        currencyFormatter.format(it)
                    } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }

            // Second row: Fuel and Maintenance costs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Fuel Costs",
                    value = currencyFormatter.format(statistics.fuelCost),
                    valueColor = FuelGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Other Costs",
                    value = currencyFormatter.format(statistics.nonFuelCost),
                    valueColor = ServiceBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            // Third row: Monthly average and Fuel Economy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Monthly Avg",
                    value = currencyFormatter.format(statistics.averageMonthlyCost),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Fuel Economy",
                    value = statistics.averageFuelConsumption?.let {
                        String.format(Locale.getDefault(), "%.1f L/100", it)
                    } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }

            // Fourth row: Distance and Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Distance",
                    value = statistics.totalDistance?.let {
                        "${NumberFormat.getNumberInstance().format(it)} $odometerUnit"
                    } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Total Entries",
                    value = statistics.expenseCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyStatsContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No statistics available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add expenses to see your statistics",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
