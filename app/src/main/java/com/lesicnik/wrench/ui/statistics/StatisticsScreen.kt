package com.lesicnik.wrench.ui.statistics

import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.components.*
import com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter
import com.lesicnik.wrench.ui.statistics.components.*
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import com.lesicnik.wrench.ui.utils.RefreshOnResumeEffect
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    vehicleName: String,
    odometerUnit: String = "km",
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onAddExpense: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    RefreshOnResumeEffect { viewModel.loadStatistics() }
    ErrorSnackbarEffect(state.errorMessage, snackbar, viewModel::clearError)
    StatisticsContent(state, vehicleName, odometerUnit, viewModel::selectPeriod,
        { viewModel.loadStatistics(forceRefresh = true) }, onNavigateBack, { tab ->
            when (tab) {
                BottomTab.HOME -> onNavigateToHome()
                BottomTab.EXPENSES -> onNavigateToExpenses()
                BottomTab.CALENDAR -> onNavigateToCalendar()
                else -> Unit
            }
        }, onAddExpense, snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsContent(
    state: StatisticsUiState,
    vehicleName: String,
    odometerUnit: String,
    onPeriod: (TimePeriod) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onTab: (BottomTab) -> Unit,
    onAdd: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    val locale = LocalConfiguration.current.locales[0]
    BackHandler(onBack = onBack)
    var section by rememberSaveable { mutableIntStateOf(0) }
    val money = rememberCurrencyFormatter()
    val stats = state.keyStatistics
    Scaffold(
        topBar = { VehicleTopBar("Statistics", vehicleName, onBack) },
        bottomBar = { VehicleNavigationBar(BottomTab.STATISTICS, onTab) },
        floatingActionButton = { AddRecordButton(onClick = onAdd) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.isLoading && stats != null, onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { TimePeriodSelector(state.selectedPeriod, onPeriod) }
                when {
                    stats == null && state.isLoading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    stats == null -> item { ErrorContent("Couldn't load statistics", "Pull down to refresh or try again.", onRefresh) }
                    stats.expenseCount == 0 -> item {
                        Text("No expenses in this period", style = MaterialTheme.typography.titleMedium)
                        Text("Choose a longer period or record your first expense.", style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> {
                        item {
                            InsightCard("What is this vehicle costing me?", money.format(stats.totalCost), "Total spent in this period") {
                                state.spendingComparison?.let { comparison ->
                                    val difference = comparison.current - comparison.previous
                                    Text(when {
                                        abs(difference) < 0.005 -> "Same spending as the previous period"
                                        difference < 0 -> "${money.format(abs(difference))} less than the previous period"
                                        else -> "${money.format(difference)} more than the previous period"
                                    }, style = MaterialTheme.typography.bodyMedium)
                                    val format = DateTimeFormatter.ofPattern("d MMM yyyy")
                                    Text("Compared with ${comparison.previousStart.format(format)} – ${comparison.previousEnd.format(format)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("Spending", "Fuel", "Mileage").forEachIndexed { index, label ->
                                    SegmentedButton(selected = section == index, onClick = { section = index },
                                        shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                                }
                            }
                        }
                        when (section) {
                            0 -> {
                                item { InsightCard("Cost per $odometerUnit", stats.averageCostPerKm?.let { money.format(it) } ?: "—",
                                    "Based on recorded expenses and the odometer range") }
                                item { ExpenseBreakdownChart(state.expenseBreakdown) }
                                item { CostTrendsChart(state.costTrends) }
                                item { Text("Monthly average: ${money.format(stats.averageMonthlyCost)}", style = MaterialTheme.typography.bodyMedium) }
                            }
                            1 -> {
                                item {
                                    InsightCard("How efficiently am I driving?", stats.averageFuelConsumption?.let {
                                        String.format(locale, "%.1f L/100 km", it)
                                    } ?: "—", "Average of recorded fill-up consumption") {
                                        if (state.fuelEconomyTrends.size >= 2) {
                                            val latest = state.fuelEconomyTrends.last().consumption
                                            val previous = state.fuelEconomyTrends[state.fuelEconomyTrends.lastIndex - 1].consumption
                                            val change = latest - previous
                                            Text(String.format(locale, "Latest: %.1f L/100 km · %.1f %s than previous fill-up", latest,
                                                abs(change), if (change <= 0) "lower" else "higher"), style = MaterialTheme.typography.bodyMedium)
                                        } else Text("Consumption needs consecutive full fill-ups.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                item { FuelEconomyChart(state.fuelEconomyTrends) }
                                item { Text("Fuel spending: ${money.format(stats.fuelCost)}", style = MaterialTheme.typography.bodyMedium) }
                            }
                            2 -> {
                                item { InsightCard("How much am I driving?", stats.totalDistance?.let {
                                    "${NumberFormat.getIntegerInstance().format(it)} $odometerUnit"
                                } ?: "—", "Distance between recorded odometer readings in this period") }
                                item { OdometerChart(state.odometerTrends, odometerUnit) }
                                item { Text("${stats.expenseCount} recorded expenses in this period", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(title: String, value: String, explanation: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = screenCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
