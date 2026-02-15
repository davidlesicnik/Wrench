package com.lesicnik.wrench.ui.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.sync.ExpenseSyncState
import com.lesicnik.wrench.ui.components.BottomBarWithFab
import com.lesicnik.wrench.ui.components.BottomBarHeight
import com.lesicnik.wrench.ui.components.BottomTab
import com.lesicnik.wrench.ui.components.EmptyContent
import com.lesicnik.wrench.ui.components.ErrorContent
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import com.lesicnik.wrench.ui.utils.RefreshOnResumeEffect
import com.lesicnik.wrench.ui.utils.color
import com.lesicnik.wrench.ui.utils.getStyle
import com.lesicnik.wrench.ui.utils.icon
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    vehicleName: String,
    odometerUnit: String = "km",
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onAddExpense: (lastOdometer: Int?) -> Unit,
    onEditExpense: (Expense) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val hasActiveFilters = uiState.selectedFilters.size < ExpenseType.entries.size

    // Handle system back button to go directly to vehicle list
    BackHandler {
        onNavigateBack()
    }

    // Refresh expenses when screen resumes (e.g., after adding an expense)
    RefreshOnResumeEffect(onResume = { viewModel.loadExpenses() })

    ErrorSnackbarEffect(
        errorMessage = uiState.errorMessage,
        snackbarHostState = snackbarHostState,
        onErrorConsumed = viewModel::clearError
    )

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
                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (hasActiveFilters) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = uiState.selectedFilters.size.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter"
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.loadExpenses(forceRefresh = true) }) {
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
                onRefresh = { viewModel.loadExpenses(forceRefresh = true) },
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
                            title = "Failed to load expenses",
                            message = uiState.errorMessage ?: "Unknown error",
                            onRetry = { viewModel.loadExpenses() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    uiState.expenses.isEmpty() -> {
                        EmptyContent(
                            icon = Icons.Default.Receipt,
                            title = "No expenses found",
                            message = "Add records in LubeLogger to see them here",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    uiState.filteredExpenses.isEmpty() -> {
                        EmptyFilterContent(
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        val sortedExpenses = remember(uiState.filteredExpenses) {
                            uiState.filteredExpenses.sortedWith(
                                compareByDescending<Expense> { it.date }
                                    .thenByDescending { it.odometer ?: Int.MIN_VALUE }
                                    .thenByDescending { it.id }
                            )
                        }
                        val expensesByMonth = remember(sortedExpenses) {
                            sortedExpenses.groupBy { YearMonth.from(it.date) }
                        }
                        val monthOrder = remember(expensesByMonth) {
                            expensesByMonth.keys.sortedDescending()
                        }
                        val listState = rememberLazyListState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(
                                bottom = BottomBarHeight + 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            monthOrder.forEach { month ->
                                val headerKey = "month_${month}"
                                stickyHeader(key = headerKey) {
                                    val isSticky =
                                        listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == headerKey }
                                            ?.offset == 0 &&
                                        // At the absolute top of the list we don't need the "sticky" shadow.
                                        (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset > 0)

                                    MonthDivider(month = month, isSticky = isSticky)
                                }
                                items(expensesByMonth[month].orEmpty(), key = { "${it.type}_${it.id}" }) { expense ->
                                    ExpenseCard(
                                        expense = expense,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        odometerUnit = odometerUnit,
                                        onClick = { onEditExpense(expense) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom bar overlaid on top of content
            val lastOdometer = uiState.expenses.firstNotNullOfOrNull { it.odometer }
            BottomBarWithFab(
                selectedTab = BottomTab.EXPENSES,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.HOME -> onNavigateToHome()
                        BottomTab.STATISTICS -> onNavigateToStatistics()
                        else -> { /* TODO: Handle other tabs */ }
                    }
                },
                onAddClick = { onAddExpense(lastOdometer) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            sheetState = sheetState,
            onDismiss = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        showFilterSheet = false
                    }
                }
            },
            selectedFilters = uiState.selectedFilters,
            onToggleFilter = { viewModel.toggleFilter(it) },
            onSelectAll = { viewModel.selectAllFilters() },
            onClearAll = { viewModel.clearAllFilters() }
        )
    }

}

@Composable
private fun ExpenseCard(
    expense: Expense,
    odometerUnit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpenseIcon(type = expense.type)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Date and odometer on the same line
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = expense.date.format(dateFormatter),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    expense.odometer?.let { odometer ->
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${NumberFormat.getNumberInstance().format(odometer)} $odometerUnit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (expense.syncState != ExpenseSyncState.SYNCED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (expense.syncState) {
                            ExpenseSyncState.PENDING_CREATE,
                            ExpenseSyncState.PENDING_UPDATE,
                            ExpenseSyncState.PENDING_DELETE -> "Pending sync"
                            ExpenseSyncState.CONFLICT -> "Conflict - open and save to keep local changes"
                            ExpenseSyncState.SYNC_ERROR -> "Sync error"
                            ExpenseSyncState.SYNCED -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (expense.syncState) {
                            ExpenseSyncState.CONFLICT,
                            ExpenseSyncState.SYNC_ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }

                // Fuel-specific details: liters and economy
                if (expense.liters != null || expense.fuelEconomy != null) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        expense.liters?.let { liters ->
                            Text(
                                text = String.format(Locale.getDefault(), "%.2f L", liters),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        expense.fuelEconomy?.let { economy ->
                            if (expense.liters != null) {
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
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(type.color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = type.name,
            tint = type.color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MonthDivider(
    month: YearMonth,
    isSticky: Boolean,
    modifier: Modifier = Modifier
) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        // Match Scaffold's base color so the sticky header doesn't "flash" a different tone.
        color = MaterialTheme.colorScheme.background,
        shadowElevation = if (isSticky) 6.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = month.format(monthFormatter),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}


@Composable
private fun EmptyFilterContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No matching expenses",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Try adjusting your filters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    selectedFilters: Set<ExpenseType>,
    onToggleFilter: (ExpenseType) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter by type",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSelectAll) {
                        Text("All")
                    }
                    TextButton(onClick = onClearAll) {
                        Text("None")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Filter chips
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpenseType.entries.forEach { type ->
                    val isSelected = type in selectedFilters
                    val style = type.getStyle()
                    val icon = style.icon
                    val color = style.color

                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleFilter(type) },
                        label = {
                            Text(
                                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = color
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            selectedContainerColor = color.copy(alpha = 0.15f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = color
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            selectedBorderColor = color.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                }
            }
        }
    }
}
