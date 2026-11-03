package com.lesicnik.wrench.ui.vehicles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lesicnik.wrench.data.remote.hasVehiclePhoto
import com.lesicnik.wrench.data.remote.photoUrl
import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.ui.components.EmptyContent
import com.lesicnik.wrench.ui.components.ErrorContent
import com.lesicnik.wrench.ui.settings.DisplaySettingsButton
import com.lesicnik.wrench.ui.settings.LocalAppPreferences
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    viewModel: VehiclesViewModel,
    onLogout: () -> Unit,
    onVehicleClick: (Vehicle) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSoldVehicles by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLogout()
    }
    ErrorSnackbarEffect(
        errorMessage = uiState.errorMessage,
        snackbarHostState = snackbarHostState,
        onErrorConsumed = viewModel::clearError
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My vehicles") },
                colors = TopAppBarDefaults.topAppBarColors(),
                actions = {
                    DisplaySettingsButton()
                    IconButton(onClick = { viewModel.loadVehicles(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadVehicles(forceRefresh = true) },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.vehicles.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                uiState.errorMessage != null && uiState.vehicles.isEmpty() -> {
                    ErrorContent(
                        title = "Failed to load vehicles",
                        message = uiState.errorMessage ?: "Unknown error",
                        onRetry = { viewModel.loadVehicles(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.vehicles.isEmpty() -> {
                    EmptyContent(
                        icon = Icons.Default.DirectionsCar,
                        title = "No vehicles found",
                        message = "Add a vehicle in LubeLogger to get started",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    val owned = uiState.vehicles.filter { !it.isSold }
                    val sold = uiState.vehicles.filter { it.isSold }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "garage_heading") {
                            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Your garage", style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${owned.size} ${if (owned.size == 1) "vehicle" else "vehicles"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                            }
                        }
                        items(owned, key = { "vehicle_${it.id}" }) { vehicle ->
                            VehicleCard(vehicle, uiState.serverUrl, uiState.apiKey, uiState.lastReadings[vehicle.id],
                                onClick = { onVehicleClick(vehicle) })
                        }
                        if (sold.isNotEmpty()) {
                            item(key = "sold_heading") {
                                Column(Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    val rotation by animateFloatAsState(if (showSoldVehicles) 180f else 0f, label = "soldExpand")
                                    Row(
                                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                                            .clickable { showSoldVehicles = !showSoldVehicles }
                                            .heightIn(min = 64.dp).padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                        Text("Sold vehicles", style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Text(sold.size.toString(), style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(Icons.Default.ExpandMore,
                                            contentDescription = if (showSoldVehicles) "Collapse sold vehicles" else "Expand sold vehicles",
                                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (showSoldVehicles) {
                                items(sold, key = { "vehicle_${it.id}" }) { vehicle ->
                                            VehicleCard(vehicle, uiState.serverUrl, uiState.apiKey, uiState.lastReadings[vehicle.id],
                                        onClick = { onVehicleClick(vehicle) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: Vehicle,
    serverUrl: String,
    apiKey: String,
    lastReading: Int?,
    onClick: () -> Unit
) {
    val compact = LocalAppPreferences.current.compactVehicles || vehicle.isSold
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (vehicle.isSold) colors.surfaceContainerLow else colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (compact) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VehicleImage(vehicle, serverUrl, apiKey, Modifier.size(96.dp).clip(MaterialTheme.shapes.medium))
                VehicleInfo(vehicle, lastReading, Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.6f))
            }
        } else {
            Column(Modifier.padding(12.dp)) {
                VehicleImage(vehicle, serverUrl, apiKey,
                    Modifier.fillMaxWidth().height(if (hasVehiclePhoto(vehicle.imageLocation)) 170.dp else 110.dp).clip(MaterialTheme.shapes.medium))
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VehicleInfo(vehicle, lastReading, Modifier.weight(1f))
                    Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                            modifier = Modifier.padding(8.dp).size(20.dp), tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleInfo(vehicle: Vehicle, lastReading: Int?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${vehicle.make} ${vehicle.model}".trim(), style = MaterialTheme.typography.titleLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(vehicle.year.toString(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { plate ->
                Surface(shape = MaterialTheme.shapes.extraSmall, color = colors.surfaceContainerHighest,
                    modifier = Modifier.weight(1f, fill = false)) {
                    Text(plate, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }
        if (lastReading != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Text("${NumberFormat.getNumberInstance().format(lastReading)} ${if (vehicle.useHours) "h" else vehicle.odometerUnit ?: "km"}",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        } else if (vehicle.isElectric) {
            Text("Electric", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun VehicleImage(vehicle: Vehicle, serverUrl: String, apiKey: String, modifier: Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.background(colors.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(68.dp),
            contentAlignment = Alignment.Center) {
            Icon(if (vehicle.isElectric) Icons.Default.ElectricCar else Icons.Default.DirectionsCar,
                contentDescription = null, modifier = Modifier.size(38.dp), tint = colors.onSecondaryContainer)
        }
        if (hasVehiclePhoto(vehicle.imageLocation) && serverUrl.isNotBlank()) {
            val request = remember(vehicle.imageLocation, serverUrl, apiKey) {
                val imageUrl = vehicle.photoUrl(serverUrl)
                ImageRequest.Builder(context).data(imageUrl).addHeader("x-api-key", apiKey).crossfade(true).build()
            }
            AsyncImage(model = request, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
