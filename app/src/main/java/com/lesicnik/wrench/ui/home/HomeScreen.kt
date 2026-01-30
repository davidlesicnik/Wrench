package com.lesicnik.wrench.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.ServiceBlue
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    vehicleName: String,
    odometerUnit: String = "km",
    onNavigateBack: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onAddExpense: (lastOdometer: Int?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh statistics when screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadStatistics()
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
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when {
                uiState.isLoading && uiState.fuelStatistics == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null && uiState.fuelStatistics == null -> {
                    ErrorContent(
                        message = uiState.errorMessage ?: "Unknown error",
                        onRetry = { viewModel.loadStatistics() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    HomeContent(
                        uiState = uiState,
                        odometerUnit = odometerUnit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = BottomBarHeight + 16.dp
                            )
                    )
                }
            }

            // Bottom bar overlaid on top of content
            HomeBottomBar(
                selectedTab = HomeTab.HOME,
                onTabSelected = { tab ->
                    when (tab) {
                        HomeTab.EXPENSES -> onNavigateToExpenses()
                        else -> { /* TODO: Handle other tabs */ }
                    }
                },
                onAddClick = { onAddExpense(uiState.fuelStatistics?.lastOdometer) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    odometerUnit: String,
    modifier: Modifier = Modifier
) {
    val stats = uiState.fuelStatistics
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Overview",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        StatCard(
            title = "Average Fuel Consumption",
            value = stats?.averageFuelConsumption?.let {
                String.format(Locale.getDefault(), "%.1f L/100km", it)
            } ?: "--",
            icon = Icons.AutoMirrored.Filled.ShowChart,
            iconColor = FuelGreen
        )

        StatCard(
            title = "Last Fuel Consumption",
            value = stats?.lastFuelConsumption?.let {
                String.format(Locale.getDefault(), "%.1f L/100km", it)
            } ?: "--",
            icon = Icons.Default.LocalGasStation,
            iconColor = FuelGreen
        )

        StatCard(
            title = "Last Odometer",
            value = stats?.lastOdometer?.let {
                "${NumberFormat.getNumberInstance().format(it)} $odometerUnit"
            } ?: "--",
            icon = Icons.Default.Speed,
            iconColor = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Costs",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        CostsCard(
            thisMonthFuel = stats?.thisMonthFuelCost ?: 0.0,
            thisMonthOther = stats?.thisMonthOtherCost ?: 0.0,
            lastMonthFuel = stats?.lastMonthFuelCost ?: 0.0,
            lastMonthOther = stats?.lastMonthOtherCost ?: 0.0
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CostsCard(
    thisMonthFuel: Double,
    thisMonthOther: Double,
    lastMonthFuel: Double,
    lastMonthOther: Double,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Fuel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = "Other",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // This month row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "This month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = currencyFormatter.format(thisMonthFuel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FuelGreen,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = currencyFormatter.format(thisMonthOther),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ServiceBlue,
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last month row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = currencyFormatter.format(lastMonthFuel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FuelGreen,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = currencyFormatter.format(lastMonthOther),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ServiceBlue,
                    modifier = Modifier.width(80.dp)
                )
            }
        }
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
            text = "Failed to load statistics",
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

enum class HomeTab {
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

            moveTo(0f, 0f)
            lineTo(centerX - cutoutRadius - cutoutMarginPx, 0f)

            cubicTo(
                centerX - cutoutRadius, 0f,
                centerX - cutoutRadius, cutoutDepth,
                centerX, cutoutDepth
            )

            cubicTo(
                centerX + cutoutRadius, cutoutDepth,
                centerX + cutoutRadius, 0f,
                centerX + cutoutRadius + cutoutMarginPx, 0f
            )

            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun HomeBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
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
                    selected = selectedTab == HomeTab.HOME,
                    onClick = { onTabSelected(HomeTab.HOME) }
                )

                BottomBarIcon(
                    icon = Icons.Default.Receipt,
                    label = "Expenses",
                    selected = selectedTab == HomeTab.EXPENSES,
                    onClick = { onTabSelected(HomeTab.EXPENSES) }
                )

                Spacer(modifier = Modifier.width(FabRadius * 2 + 24.dp))

                BottomBarIcon(
                    icon = Icons.Default.BarChart,
                    label = "Stats",
                    selected = selectedTab == HomeTab.STATISTICS,
                    onClick = { onTabSelected(HomeTab.STATISTICS) }
                )

                BottomBarIcon(
                    icon = Icons.Default.CalendarMonth,
                    label = "Calendar",
                    selected = selectedTab == HomeTab.CALENDAR,
                    onClick = { onTabSelected(HomeTab.CALENDAR) }
                )
            }
        }

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
    icon: ImageVector,
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
