package com.lesicnik.wrench.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.RepairRed
import com.lesicnik.wrench.ui.theme.ServiceBlue
import com.lesicnik.wrench.ui.theme.TaxOrange
import com.lesicnik.wrench.ui.theme.UpgradePurple
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    vehicleName: String,
    odometerUnit: String = "km",
    onNavigateBack: () -> Unit,
    onAddExpense: (lastOdometer: Int?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh expenses when screen resumes (e.g., after adding an expense)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadExpenses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = vehicleName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.loadExpenses() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading && uiState.expenses.isNotEmpty(),
                onRefresh = { viewModel.loadExpenses() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading && uiState.expenses.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.errorMessage != null && uiState.expenses.isEmpty() -> {
                        ErrorContent(
                            message = uiState.errorMessage ?: "Unknown error",
                            onRetry = { viewModel.loadExpenses() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    uiState.expenses.isEmpty() -> {
                        EmptyContent(
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = BottomBarHeight + 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.expenses, key = { "${it.type}_${it.id}" }) { expense ->
                                ExpenseCard(expense = expense, odometerUnit = odometerUnit)
                            }
                        }
                    }
                }
            }

            // Bottom bar overlaid on top of content
            val lastOdometer = uiState.expenses.firstNotNullOfOrNull { it.odometer }
            ExpensesBottomBar(
                selectedTab = ExpensesTab.EXPENSES,
                onTabSelected = { /* TODO: Handle navigation */ },
                onAddClick = { onAddExpense(lastOdometer) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    odometerUnit: String,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpenseIcon(type = expense.type)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.date.format(dateFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    expense.odometer?.let { odometer ->
                        Text(
                            text = "${NumberFormat.getNumberInstance().format(odometer)} $odometerUnit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    expense.liters?.let { liters ->
                        if (expense.odometer != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f L", liters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    expense.fuelEconomy?.let { economy ->
                        if (expense.odometer != null || expense.liters != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f L/100km", economy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = currencyFormatter.format(expense.cost),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ExpenseIcon(type: ExpenseType) {
    val (icon, color) = when (type) {
        ExpenseType.SERVICE -> Icons.Default.Build to ServiceBlue
        ExpenseType.REPAIR -> Icons.Default.Handyman to RepairRed
        ExpenseType.UPGRADE -> Icons.AutoMirrored.Filled.TrendingUp to UpgradePurple
        ExpenseType.FUEL -> Icons.Default.LocalGasStation to FuelGreen
        ExpenseType.TAX -> Icons.Default.Receipt to TaxOrange
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.name,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun EmptyContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No expenses found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add records in LubeLogger to see them here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Failed to load expenses",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

enum class ExpensesTab {
    HOME,
    EXPENSES,
    STATISTICS,
    CALENDAR
}

private val FabRadius = 28.dp
private val BottomBarHeight = 80.dp

private class BottomBarCutoutShape(
    private val fabRadiusPx: Float,
    private val cutoutMarginPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val cutoutRadius = fabRadiusPx + cutoutMarginPx
            val centerX = size.width / 2
            val cutoutDepth = fabRadiusPx + cutoutMarginPx / 2

            // Start from top-left
            moveTo(0f, 0f)

            // Line to start of cutout curve
            lineTo(centerX - cutoutRadius - cutoutMarginPx, 0f)

            // Curve down into the cutout
            cubicTo(
                centerX - cutoutRadius, 0f,
                centerX - cutoutRadius, cutoutDepth,
                centerX, cutoutDepth
            )

            // Curve back up from center
            cubicTo(
                centerX + cutoutRadius, cutoutDepth,
                centerX + cutoutRadius, 0f,
                centerX + cutoutRadius + cutoutMarginPx, 0f
            )

            // Line to top-right
            lineTo(size.width, 0f)

            // Down the right side
            lineTo(size.width, size.height)

            // Along the bottom
            lineTo(0f, size.height)

            // Back up to start
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun ExpensesBottomBar(
    selectedTab: ExpensesTab,
    onTabSelected: (ExpensesTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fabRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { FabRadius.toPx() }
    val cutoutMarginPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BottomBarHeight)
            .graphicsLayer { clip = false }
    ) {
        // Navigation bar with cutout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = BottomBarCutoutShape(fabRadiusPx, cutoutMarginPx)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarIcon(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = selectedTab == ExpensesTab.HOME,
                    onClick = { onTabSelected(ExpensesTab.HOME) }
                )

                BottomBarIcon(
                    icon = Icons.Default.Receipt,
                    label = "Expenses",
                    selected = selectedTab == ExpensesTab.EXPENSES,
                    onClick = { onTabSelected(ExpensesTab.EXPENSES) }
                )

                // Spacer for FAB
                Spacer(modifier = Modifier.width(FabRadius * 2 + 24.dp))

                BottomBarIcon(
                    icon = Icons.Default.BarChart,
                    label = "Stats",
                    selected = selectedTab == ExpensesTab.STATISTICS,
                    onClick = { onTabSelected(ExpensesTab.STATISTICS) }
                )

                BottomBarIcon(
                    icon = Icons.Default.CalendarMonth,
                    label = "Calendar",
                    selected = selectedTab == ExpensesTab.CALENDAR,
                    onClick = { onTabSelected(ExpensesTab.CALENDAR) }
                )
            }
        }

        // FAB positioned in the cutout - offset to sit in the notch
        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = -FabRadius.toPx() + cutoutMarginPx / 2
                }
                .size(FabRadius * 2),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Expense"
            )
        }
    }
}

@Composable
private fun BottomBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
