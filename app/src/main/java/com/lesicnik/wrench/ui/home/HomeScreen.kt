package com.lesicnik.wrench.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.components.BottomBarWithFab
import com.lesicnik.wrench.ui.components.BottomBarHeight
import com.lesicnik.wrench.ui.components.BottomTab
import com.lesicnik.wrench.ui.components.ErrorContent
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.ServiceBlue
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import com.lesicnik.wrench.ui.utils.RefreshOnResumeEffect
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
    onNavigateToStatistics: () -> Unit,
    onAddExpense: (lastOdometer: Int?) -> Unit,
    animateEntry: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var runEntryAnimation by rememberSaveable { mutableStateOf(animateEntry) }
    val entryProgress = remember { Animatable(if (runEntryAnimation) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (runEntryAnimation) {
            // Uses Compose's animation clock so reduced-motion settings are respected.
            entryProgress.animateTo(1f, tween(420, easing = LinearEasing))
            runEntryAnimation = false
        }
    }
    val showSkeleton = uiState.isLoading && uiState.fuelStatistics == null

    // Refresh statistics when screen resumes
    RefreshOnResumeEffect(onResume = { viewModel.loadStatistics() })

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
                uiState.errorMessage != null && uiState.fuelStatistics == null -> {
                    ErrorContent(
                        title = "Failed to load statistics",
                        message = uiState.errorMessage ?: "Unknown error",
                        onRetry = { viewModel.loadStatistics(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    HomeContent(
                        uiState = uiState,
                        isPlaceholder = showSkeleton,
                        entryProgress = entryProgress.value,
                        placeholderAlpha = 0.6f + 0.4f * entryProgress.value,
                        odometerUnit = odometerUnit,
                        onExpensesClick = onNavigateToExpenses,
                        onStatisticsClick = onNavigateToStatistics,
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
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
            BottomBarWithFab(
                selectedTab = BottomTab.HOME,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.EXPENSES -> onNavigateToExpenses()
                        BottomTab.STATISTICS -> onNavigateToStatistics()
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
    isPlaceholder: Boolean,
    entryProgress: Float,
    placeholderAlpha: Float,
    odometerUnit: String,
    onExpensesClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = uiState.fuelStatistics
    val currencyFormatter = com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Overview",
            modifier = Modifier.overviewEntry(entryProgress, 0),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        StatCard(
            loading = isPlaceholder,
            placeholderAlpha = placeholderAlpha,
            title = "Average Fuel Consumption",
            value = stats?.averageFuelConsumption?.let {
                String.format(Locale.getDefault(), "%.1f L/100km", it)
            } ?: "--",
            icon = Icons.AutoMirrored.Filled.ShowChart,
            iconColor = FuelGreen,
            modifier = Modifier.overviewEntry(entryProgress, 1).clickable(enabled = !isPlaceholder, onClick = onStatisticsClick)
        )

        StatCard(
            loading = isPlaceholder,
            placeholderAlpha = placeholderAlpha,
            title = "Last Fuel Consumption",
            value = stats?.lastFuelConsumption?.let {
                String.format(Locale.getDefault(), "%.1f L/100km", it)
            } ?: "--",
            icon = Icons.Default.LocalGasStation,
            iconColor = FuelGreen,
            modifier = Modifier.overviewEntry(entryProgress, 2).clickable(enabled = !isPlaceholder, onClick = onExpensesClick)
        )

        StatCard(
            loading = isPlaceholder,
            placeholderAlpha = placeholderAlpha,
            title = "Last Odometer",
            value = stats?.lastOdometer?.let {
                "${NumberFormat.getNumberInstance().format(it)} $odometerUnit"
            } ?: "--",
            icon = Icons.Default.Speed,
            iconColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.overviewEntry(entryProgress, 3).clickable(enabled = !isPlaceholder, onClick = onExpensesClick)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Costs",
            modifier = Modifier.overviewEntry(entryProgress, 4),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        CostsCard(
            loading = isPlaceholder,
            placeholderAlpha = placeholderAlpha,
            thisMonthFuel = stats?.thisMonthFuelCost ?: 0.0,
            thisMonthOther = stats?.thisMonthOtherCost ?: 0.0,
            lastMonthFuel = stats?.lastMonthFuelCost ?: 0.0,
            lastMonthOther = stats?.lastMonthOtherCost ?: 0.0,
            modifier = Modifier.overviewEntry(entryProgress, 5).clickable(enabled = !isPlaceholder, onClick = onExpensesClick)
        )
    }
}

@Composable
private fun StatCard(
    loading: Boolean,
    placeholderAlpha: Float,
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
                OverviewValue(
                    value = value,
                    loading = loading,
                    placeholderAlpha = placeholderAlpha,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CostsCard(
    loading: Boolean,
    placeholderAlpha: Float,
    thisMonthFuel: Double,
    thisMonthOther: Double,
    lastMonthFuel: Double,
    lastMonthOther: Double,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter()

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
                OverviewValue(
                    value = currencyFormatter.format(thisMonthFuel),
                    loading = loading,
                    placeholderAlpha = placeholderAlpha,
                    placeholderWidth = 64.dp,
                    placeholderHeight = 20.dp,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FuelGreen,
                    modifier = Modifier.width(80.dp)
                )
                OverviewValue(
                    value = currencyFormatter.format(thisMonthOther),
                    loading = loading,
                    placeholderAlpha = placeholderAlpha,
                    placeholderWidth = 64.dp,
                    placeholderHeight = 20.dp,
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
                OverviewValue(
                    value = currencyFormatter.format(lastMonthFuel),
                    loading = loading,
                    placeholderAlpha = placeholderAlpha,
                    placeholderWidth = 64.dp,
                    placeholderHeight = 20.dp,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FuelGreen,
                    modifier = Modifier.width(80.dp)
                )
                OverviewValue(
                    value = currencyFormatter.format(lastMonthOther),
                    loading = loading,
                    placeholderAlpha = placeholderAlpha,
                    placeholderWidth = 64.dp,
                    placeholderHeight = 20.dp,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ServiceBlue,
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    }
}


@Composable
private fun OverviewValue(
    value: String,
    loading: Boolean,
    placeholderAlpha: Float,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    placeholderWidth: Dp = 136.dp,
    placeholderHeight: Dp = 28.dp
) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Crossfade(targetState = loading, animationSpec = tween(160), modifier = modifier.heightIn(min = lineHeight), label = "overviewValue") { isLoading ->
        if (isLoading) {
            Box(Modifier.width(placeholderWidth).height(lineHeight)
                .clearAndSetSemantics { contentDescription = "Loading" }) {
                Box(Modifier.fillMaxWidth().height(placeholderHeight.coerceAtMost(lineHeight))
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * placeholderAlpha)))
            }
        } else {
            Text(value, style = style, color = color)
        }
    }
}


/** Stagger the overview's content while leaving the page surface fully opaque. */
private fun Modifier.overviewEntry(progress: Float, index: Int): Modifier = composed {
    val distance = with(LocalDensity.current) { 24.dp.toPx() }
    val itemProgress = ((progress * 420f - index * 25f) / 280f).coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(itemProgress)
    graphicsLayer {
        translationY = (1f - eased) * distance
        alpha = eased
    }
}
