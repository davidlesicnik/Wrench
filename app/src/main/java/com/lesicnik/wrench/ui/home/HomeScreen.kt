package com.lesicnik.wrench.ui.home

import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.sync.ExpenseSyncState
import com.lesicnik.wrench.ui.components.VehicleTopBar
import com.lesicnik.wrench.ui.components.VehicleNavigationBar
import com.lesicnik.wrench.ui.components.AddRecordButton
import com.lesicnik.wrench.ui.expenses.ExpenseDetailSheet
import com.lesicnik.wrench.data.repository.maintenanceAgenda
import com.lesicnik.wrench.data.repository.ReminderUrgency
import com.lesicnik.wrench.ui.calendar.distanceLabel
import com.lesicnik.wrench.ui.calendar.relativeDate
import com.lesicnik.wrench.ui.components.BottomTab
import com.lesicnik.wrench.ui.components.ErrorContent
import com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import com.lesicnik.wrench.ui.utils.RefreshOnResumeEffect
import com.lesicnik.wrench.ui.utils.getStyle
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    vehicleName: String,
    odometerUnit: String = "km",
    onNavigateBack: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onMonthlyExpenses: () -> Unit,
    onAddExpense: (lastOdometer: Int?) -> Unit,
    onEditExpense: (Expense) -> Unit,
    animateEntry: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    RefreshOnResumeEffect(onResume = { viewModel.loadStatistics() })
    ErrorSnackbarEffect(uiState.errorMessage, snackbarHostState, viewModel::clearError)
    HomeScreenContent(uiState, vehicleName, odometerUnit, onNavigateBack, onNavigateToExpenses,
        onNavigateToStatistics, onNavigateToCalendar, onMonthlyExpenses, onAddExpense, onEditExpense,
        { viewModel.loadStatistics(forceRefresh = true) }, animateEntry, snackbarHostState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    vehicleName: String,
    odometerUnit: String,
    onNavigateBack: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onMonthlyExpenses: () -> Unit,
    onAddExpense: (Int?) -> Unit,
    onEditExpense: (Expense) -> Unit,
    onRefresh: () -> Unit,
    animateEntry: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    BackHandler(onBack = onNavigateBack)
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var animate by rememberSaveable { mutableStateOf(animateEntry) }
    val entry = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            entry.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
            animate = false
        }
    }
    val selectTab: (BottomTab) -> Unit = {
        when (it) {
            BottomTab.EXPENSES -> onNavigateToExpenses()
            BottomTab.STATISTICS -> onNavigateToStatistics()
            BottomTab.CALENDAR -> onNavigateToCalendar()
            else -> Unit
        }
    }
    Scaffold(
        topBar = { VehicleTopBar("Home", vehicleName, onNavigateBack) },
        bottomBar = { VehicleNavigationBar(BottomTab.HOME, selectTab) },
        floatingActionButton = { AddRecordButton { onAddExpense(uiState.overview?.lastOdometer ?: uiState.fuelStatistics?.lastOdometer) } },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading && uiState.fuelStatistics != null,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.fuelStatistics == null && uiState.isLoading -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text("Loading your overview…", style = MaterialTheme.typography.bodyLarge)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            repeat(2) {
                                Surface(Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(24.dp), color = homeCardColor()) {}
                            }
                        }
                    }
                    uiState.fuelStatistics == null -> ErrorContent(
                        title = "Couldn't load your overview",
                        message = "Pull down to refresh or try again.",
                        onRetry = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> HomeContent(
                        uiState, odometerUnit, onNavigateToExpenses, onNavigateToStatistics, onMonthlyExpenses, { selectedExpense = it }, onNavigateToCalendar,
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = entry.value
                            translationY = (1f - entry.value) * 16.dp.toPx()
                        }
                    )
                }
            }

        }
    }
    selectedExpense?.let { expense ->
        ExpenseDetailSheet(expense, odometerUnit, onDismiss = { selectedExpense = null }, onEdit = {
            selectedExpense = null
            onEditExpense(expense)
        })
    }
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    odometerUnit: String,
    onExpenses: () -> Unit,
    onStatistics: () -> Unit,
    onMonthlyExpenses: () -> Unit,
    onOpenExpense: (Expense) -> Unit,
    onMaintenance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = state.fuelStatistics ?: return
    val overview = state.overview
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(
        modifier.verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Default.Speed, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column {
                Text("Recorded mileage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text((overview?.lastOdometer ?: stats.lastOdometer)?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(odometerUnit, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                overview?.odometerDate?.let {
                    Text(it.format(dateFormat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        NextMaintenanceCard(state, odometerUnit, onMaintenance)

        Card(
            onClick = onStatistics,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = homeCardColor()),
            border = homeCardBorder()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalGasStation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Fuel consumption", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "View fuel statistics", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    FuelValue("Average", stats.averageFuelConsumption, Modifier.weight(1f), emphasize = true)
                    FuelValue("Latest", stats.lastFuelConsumption, Modifier.weight(1f))
                }
                if (stats.averageFuelConsumption == null || stats.lastFuelConsumption == null) {
                    Text("Consumption appears after consecutive full fill-ups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SpendingCard(overview, onMonthlyExpenses)

        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onExpenses) { Text("View all") }
            }
            if (overview == null) {
                Text("Activity unavailable. Pull down to retry.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (overview.recentExpenses.isEmpty()) {
                Surface(shape = RoundedCornerShape(20.dp), color = homeCardColor()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your vehicle's story starts here", style = MaterialTheme.typography.titleSmall)
                        Text("Tap Add expense to record your first fill-up or expense.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = homeCardColor()), border = homeCardBorder()) {
                    overview.recentExpenses.forEachIndexed { index, expense ->
                        ActivityRow(expense, odometerUnit, onClick = { onOpenExpense(expense) })
                        if (index < overview.recentExpenses.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelValue(label: String, value: Double?, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.let { String.format(locale, "%.1f", it) } ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text("L/100 km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpendingCard(overview: HomeOverview?, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val money = rememberCurrencyFormatter()
    val today = LocalDate.now()
    val monthFormat = DateTimeFormatter.ofPattern("MMMM", locale)
    val month = today.format(monthFormat)
    val previousMonth = today.minusMonths(1).format(monthFormat)
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        border = if (light) BorderStroke(1.dp, colors.primary.copy(alpha = 0.12f)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (light) lerp(colors.surface, colors.primaryContainer, 0.32f) else colors.primaryContainer,
            contentColor = if (light) colors.onSurface else colors.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Spending · $month", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "View expenses", modifier = Modifier.size(20.dp))
            }
            if (overview == null) {
                Text("Unavailable", style = MaterialTheme.typography.headlineMedium)
                Text("Pull down to retry loading expenses.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val currentTotal = overview.thisMonthFuel + overview.thisMonthOther
                val previousTotal = overview.previousMonthToDate
                val difference = currentTotal - previousTotal
                val secondaryColor = if (light) colors.onSurfaceVariant else colors.onPrimaryContainer
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(money.format(currentTotal), style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold, color = if (light) colors.primary else colors.onPrimaryContainer)
                    Text("Spent so far this month", style = MaterialTheme.typography.bodySmall, color = secondaryColor)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpendingPart("Fuel", money.format(overview.thisMonthFuel), Modifier.weight(1f))
                    SpendingPart("Other", money.format(overview.thisMonthOther), Modifier.weight(1f))
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = if (light) 0.6f else 0.35f))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(previousMonth, style = MaterialTheme.typography.titleSmall)
                        Text("Through ${overview.comparisonEnd.format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.bodySmall, color = secondaryColor)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(money.format(previousTotal), style = MaterialTheme.typography.titleMedium)
                        val comparison = when {
                            abs(difference) < 0.005 -> "Same total so far"
                            difference < 0 -> "${money.format(abs(difference))} less so far"
                            else -> "${money.format(difference)} more so far"
                        }
                        Text(comparison, style = MaterialTheme.typography.bodySmall, color = secondaryColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingPart(label: String, amount: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    Surface(modifier, shape = RoundedCornerShape(14.dp),
        color = if (light) colors.surfaceContainerLowest.copy(alpha = 0.8f) else colors.surface.copy(alpha = 0.45f),
        contentColor = if (light) colors.onSurface else colors.onPrimaryContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (light) colors.onSurfaceVariant else colors.onPrimaryContainer)
            Text(amount, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ActivityRow(expense: Expense, odometerUnit: String, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val style = expense.type.getStyle()
    val money = rememberCurrencyFormatter()
    val date = expense.date.format(DateTimeFormatter.ofPattern("d MMM", locale))
    val syncLabel = when (expense.syncState) {
        ExpenseSyncState.SYNCED -> null
        ExpenseSyncState.SYNC_ERROR, ExpenseSyncState.CONFLICT -> "Needs sync attention"
        else -> "Pending sync"
    }
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = style.color.copy(alpha = 0.12f)) {
                Icon(style.icon, null, Modifier.padding(10.dp).size(20.dp), tint = style.color)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(expense.description.ifBlank { style.label }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(date, expense.odometer?.let { "${NumberFormat.getIntegerInstance().format(it)} $odometerUnit" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                syncLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
            Text(money.format(expense.cost), style = MaterialTheme.typography.titleSmall)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open ${style.label.lowercase()} record", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun homeCardColor() = MaterialTheme.colorScheme.let { colors ->
    if (colors.surface.luminance() > 0.5f) colors.surfaceContainerLowest else colors.surfaceContainerLow
}

@Composable
private fun homeCardBorder(): BorderStroke? = MaterialTheme.colorScheme.let { colors ->
    if (colors.surface.luminance() > 0.5f) BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)) else null
}

@Composable
private fun NextMaintenanceCard(state: HomeUiState, unit: String, onClick: () -> Unit) {
    val next = maintenanceAgenda(state.reminders).firstOrNull()
    val overdue = state.reminders.count { it.urgency == ReminderUrgency.PAST_DUE }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (overdue > 0) MaterialTheme.colorScheme.errorContainer else homeCardColor())) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next maintenance", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "View maintenance", Modifier.size(20.dp))
            }
            if (overdue > 0) Text("$overdue overdue", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error)
            if (next != null) {
                Text(next.description, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(next.dueDate?.let { relativeDate(it, LocalDate.now()) },
                    next.dueDistance?.let { distanceLabel(it, unit) },
                    next.dueOdometer?.takeIf { next.dueDistance == null }?.let { "At ${NumberFormat.getIntegerInstance().format(it)} $unit" })
                    .joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (state.maintenanceError != null) Text("Showing saved reminders", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(when {
                    state.maintenanceError != null -> "Couldn't refresh maintenance. Tap to review or retry."
                    !state.remindersLoaded -> "Loading maintenance…"
                    else -> "Nothing scheduled. Plan your next service."
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
