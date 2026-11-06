package com.lesicnik.wrench.ui.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
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
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.sync.ExpenseSyncState
import com.lesicnik.wrench.ui.components.AddRecordButton
import com.lesicnik.wrench.ui.components.BottomTab
import com.lesicnik.wrench.ui.components.EmptyContent
import com.lesicnik.wrench.ui.components.ErrorContent
import com.lesicnik.wrench.ui.components.VehicleNavigationBar
import com.lesicnik.wrench.ui.components.VehicleTopBar
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import com.lesicnik.wrench.ui.utils.RefreshOnResumeEffect
import com.lesicnik.wrench.ui.utils.color
import com.lesicnik.wrench.ui.utils.getStyle
import com.lesicnik.wrench.ui.utils.icon
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    vehicleName: String,
    odometerUnit: String = "km",
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onAddExpense: (lastOdometer: Int?) -> Unit,
    onEditExpense: (Expense) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var reviewLegacy by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var showDateRange by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hasActiveFilters = uiState.selectedFilters.size < ExpenseType.entries.size || uiState.dateFrom != null || uiState.dateTo != null || uiState.searchQuery.isNotBlank()

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

    if (reviewLegacy) androidx.compose.material3.AlertDialog(
        onDismissRequest = { reviewLegacy = false },
        title = { Text("Assign existing queued changes?") },
        text = { Text("Older offline changes have no account identity. Review this vehicle's pending expenses first. Continue only if these changes belong to the currently signed-in LubeLogger account.") },
        confirmButton = { TextButton(onClick = { reviewLegacy = false; viewModel.claimLegacyOperations() }) { Text("Assign and sync") } },
        dismissButton = { TextButton(onClick = { reviewLegacy = false }) { Text("Cancel") } })

    Scaffold(
        topBar = {
            VehicleTopBar("Expenses", vehicleName, onNavigateBack,
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (hasActiveFilters) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = ((if (uiState.selectedFilters.size < ExpenseType.entries.size) 1 else 0) +
                                                (if (uiState.searchQuery.isNotBlank()) 1 else 0) +
                                                (if (uiState.dateFrom != null || uiState.dateTo != null) 1 else 0)).toString(),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { VehicleNavigationBar(BottomTab.EXPENSES) {
            when (it) {
                BottomTab.HOME -> onNavigateToHome()
                BottomTab.STATISTICS -> onNavigateToStatistics()
                BottomTab.CALENDAR -> onNavigateToCalendar()
                else -> Unit
            }
        } },
        floatingActionButton = { AddRecordButton { onAddExpense(uiState.expenses.mapNotNull { it.odometer }.maxOrNull()) } }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(Modifier.fillMaxSize()) {
            if (hasActiveFilters) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${uiState.filteredExpenses.size} ${if (uiState.filteredExpenses.size == 1) "expense" else "expenses"} shown",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::resetFilters) { Text("Reset filters") }
                }
            }
            PullToRefreshBox(
                isRefreshing = uiState.isLoading && uiState.expenses.isNotEmpty(),
                onRefresh = { viewModel.loadExpenses(forceRefresh = true) },
                modifier = Modifier.fillMaxWidth().weight(1f)
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
                            message = "Track a fill-up, service or another vehicle expense",
                            actionLabel = "Add first expense",
                            onAction = { onAddExpense(null) },
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
                                bottom = 96.dp
                            )
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

                                    MonthDivider(
                                        month = month,
                                        isSticky = isSticky,
                                        total = expensesByMonth[month].orEmpty().sumOf { it.cost }
                                    )
                                }
                                val monthExpenses = expensesByMonth[month].orEmpty()
                                itemsIndexed(monthExpenses, key = { _, expense -> "${expense.type}_${expense.id}" }) { index, expense ->
                                    ExpenseRow(
                                        expense = expense,
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        odometerUnit = odometerUnit,
                                        onClick = { selectedExpense = expense },
                                        isFirst = index == 0,
                                        isLast = index == monthExpenses.lastIndex
                                    )
                                }
                            }
                        }
                    }
                }
            }

            } // Expense list


        }
    }

    selectedExpense?.let { selected ->
        val expense = uiState.expenses.find { it.id == selected.id && it.type == selected.type }
        if (expense != null) ExpenseDetailSheet(
            expense = expense,
            odometerUnit = odometerUnit,
            onDismiss = { selectedExpense = null },
            onEdit = { selectedExpense = null; onEditExpense(expense) }
        )
    }
    if (showDateRange) {
        val dateState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.dateFrom?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = uiState.dateTo?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDateRange = false },
            confirmButton = {
                TextButton(enabled = dateState.selectedStartDateMillis != null && dateState.selectedEndDateMillis != null,
                    onClick = {
                        fun date(millis: Long?) = millis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                        viewModel.setDateRange(date(dateState.selectedStartDateMillis), date(dateState.selectedEndDateMillis))
                        showDateRange = false
                    }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showDateRange = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(
                state = dateState,
                modifier = Modifier.height(500.dp),
                title = {
                    Text(
                        text = "Select dates",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
                    )
                },
                headline = {
                    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
                    fun label(millis: Long?, placeholder: String) = millis?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate().format(formatter)
                    } ?: placeholder
                    Text(
                        text = if (dateState.selectedStartDateMillis == null && dateState.selectedEndDateMillis == null) {
                            "Choose a date range"
                        } else {
                            "${label(dateState.selectedStartDateMillis, "Start date")} – ${label(dateState.selectedEndDateMillis, "End date")}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 20.dp)
                    )
                }
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
            hasActiveFilters = hasActiveFilters,
            onReset = viewModel::resetFilters,
            selectedFilters = uiState.selectedFilters,
            onToggleFilter = { viewModel.toggleFilter(it) },
            onSelectAll = { viewModel.selectAllFilters() },
            onClearAll = { viewModel.clearAllFilters() },
            filterControls = {
                ExpenseFilterFields(
                    query = uiState.searchQuery,
                    onQueryChanged = viewModel::setSearchQuery,
                    dateFrom = uiState.dateFrom,
                    dateTo = uiState.dateTo,
                    onDateClick = { showDateRange = true },
                    onClearDates = { viewModel.setDateRange(null, null) }
                )
            },
            syncDetails = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val summary = uiState.syncSummary
                    val lastSync = summary.lastSyncedAt?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
                    } ?: "Never"
                    Text(
                        text = buildString {
                            append(if (uiState.isLoading) "Refreshing…" else if (summary.refreshError != null) "Refresh failed · Cached data" else "Saved on device")
                            append(" · Last synced: $lastSync")
                            if (summary.pendingCount > 0) append(" · ${summary.pendingCount} pending")
                            if (summary.problemCount > 0) append(" · ${summary.problemCount} need attention")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (summary.unownedCount > 0) TextButton(onClick = { reviewLegacy = true }) { Text("Review ${summary.unownedCount} older changes") }
                    TextButton(enabled = !uiState.isLoading, onClick = { viewModel.loadExpenses(forceRefresh = true) }) {
                        Text(if (summary.refreshError != null || summary.problemCount > 0) "Retry" else "Sync")
                    }
                }
            }
        )
    }

}

@Composable
private fun ExpenseRow(
    expense: Expense,
    odometerUnit: String,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val currencyFormatter = com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter()
    val colors = MaterialTheme.colorScheme
    val isLight = colors.surface.luminance() > 0.5f
    val typeStyle = expense.type.getStyle()
    val fuelDetails = buildList {
        expense.liters?.let { add(String.format(locale, "%.2f L", it)) }
        expense.fuelEconomy?.let { add(String.format(locale, "%.1f L/100 km", it)) }
        if (expense.isFillToFull == false) add("Partial fill")
        if (expense.isMissedFuelUp == true) add("Missed last")
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = if (isFirst) 20.dp else 0.dp,
            topEnd = if (isFirst) 20.dp else 0.dp,
            bottomStart = if (isLast) 20.dp else 0.dp,
            bottomEnd = if (isLast) 20.dp else 0.dp
        ),
        color = if (isLight) colors.surfaceContainerLowest else colors.surfaceContainerLow
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                ExpenseIcon(type = expense.type)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = expense.description.ifBlank { typeStyle.label },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currencyFormatter.format(expense.cost),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp
                        )
                    }
                    Text(
                        text = buildList {
                            add(expense.date.format(dateFormatter))
                            expense.odometer?.let { add("${NumberFormat.getIntegerInstance().format(it)} $odometerUnit") }
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    if (fuelDetails.isNotEmpty()) {
                        Text(
                            text = fuelDetails.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    if (expense.syncState != ExpenseSyncState.SYNCED) {
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
                                ExpenseSyncState.SYNC_ERROR -> colors.error
                                else -> colors.tertiary
                            }
                        )
                    }
                }
            }
            if (!isLast) {
                HorizontalDivider(
                    Modifier.padding(start = 64.dp, end = 16.dp),
                    color = colors.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun ExpenseIcon(type: ExpenseType) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(type.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = type.getStyle().label,
            tint = androidx.compose.ui.graphics.lerp(type.color, MaterialTheme.colorScheme.onSurface, 0.25f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MonthDivider(
    month: YearMonth,
    isSticky: Boolean,
    total: Double,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val formatter = com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = if (isSticky) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                month.format(monthFormatter).uppercase(locale),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatter.format(total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    hasActiveFilters: Boolean,
    onReset: () -> Unit,
    selectedFilters: Set<ExpenseType>,
    onToggleFilter: (ExpenseType) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    filterControls: @Composable () -> Unit,
    syncDetails: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 96.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Search & filter", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onReset, enabled = hasActiveFilters) { Text("Reset") }
            }
            filterControls()
            Spacer(Modifier.height(20.dp))
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Expense types",
                    style = MaterialTheme.typography.titleSmall
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

            Spacer(modifier = Modifier.height(8.dp))

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
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                syncDetails()
            }
        }
    }
}
