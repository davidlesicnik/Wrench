package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.statistics.CostTrendPoint
import com.lesicnik.wrench.ui.statistics.ExpenseBreakdownItem
import com.lesicnik.wrench.ui.statistics.FuelEconomyPoint
import com.lesicnik.wrench.ui.statistics.OdometerPoint

@Composable
fun ChartsPage(
    expenseBreakdown: List<ExpenseBreakdownItem>,
    costTrends: List<CostTrendPoint>,
    fuelEconomyTrends: List<FuelEconomyPoint>,
    odometerTrends: List<OdometerPoint>,
    odometerUnit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExpenseBreakdownChart(breakdown = expenseBreakdown)

        CostTrendsChart(trends = costTrends)

        FuelEconomyChart(fuelEconomyTrends = fuelEconomyTrends)

        OdometerChart(odometerTrends = odometerTrends, odometerUnit = odometerUnit)
    }
}
