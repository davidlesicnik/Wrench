package com.lesicnik.wrench.ui.expenses

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.RepairRed
import com.lesicnik.wrench.ui.theme.ServiceBlue
import com.lesicnik.wrench.ui.theme.TaxOrange
import com.lesicnik.wrench.ui.theme.UpgradePurple
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: AddExpenseViewModel,
    odometerUnit: String = "km",
    lastOdometer: Int? = null,
    onNavigateBack: () -> Unit,
    onExpenseSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    var showDatePicker by remember { mutableStateOf(false) }

    // Detect keyboard visibility
    val view = LocalView.current
    var isKeyboardOpen by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            isKeyboardOpen = keypadHeight > screenHeight * 0.15
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    val expenseTypeOrder = remember {
        listOf(
            ExpenseType.FUEL,
            ExpenseType.SERVICE,
            ExpenseType.REPAIR,
            ExpenseType.UPGRADE,
            ExpenseType.TAX
        )
    }

    // Get color for current expense type
    val currentTypeColor = remember(uiState.expenseType) {
        when (uiState.expenseType) {
            ExpenseType.SERVICE -> ServiceBlue
            ExpenseType.REPAIR -> RepairRed
            ExpenseType.UPGRADE -> UpgradePurple
            ExpenseType.FUEL -> FuelGreen
            ExpenseType.TAX -> TaxOrange
        }
    }

    // Animated button color
    val animatedButtonColor by animateColorAsState(
        targetValue = currentTypeColor,
        animationSpec = tween(durationMillis = 300),
        label = "buttonColor"
    )

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onExpenseSaved()
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
                title = { Text("Add Expense") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Scrollable Form Fields
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Form Fields
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Date Field
                    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.date.format(dateFormatter),
                            onValueChange = { },
                            label = { Text("Date") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null
                                )
                            },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Clickable overlay
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = !uiState.isLoading) { showDatePicker = true }
                        )
                    }

                    // Odometer (not for TAX)
                    AnimatedVisibility(visible = uiState.expenseType != ExpenseType.TAX) {
                        OutlinedTextField(
                            value = uiState.odometer,
                            onValueChange = viewModel::onOdometerChanged,
                            label = { Text("Odometer ($odometerUnit)") },
                            placeholder = lastOdometer?.let {
                                { Text("Last: ${NumberFormat.getNumberInstance().format(it)}") }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            enabled = !uiState.isLoading
                        )
                    }

                    // Fuel-specific fields
                    AnimatedVisibility(visible = uiState.expenseType == ExpenseType.FUEL) {
                        Column {
                            OutlinedTextField(
                                value = uiState.fuelConsumed,
                                onValueChange = viewModel::onFuelConsumedChanged,
                                label = { Text("Fuel Amount (L)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LocalGasStation,
                                        contentDescription = null
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                enabled = !uiState.isLoading
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !uiState.isLoading) {
                                            viewModel.onFillToFullChanged(!uiState.isFillToFull)
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = uiState.isFillToFull,
                                        onCheckedChange = null,
                                        enabled = !uiState.isLoading
                                    )
                                    Text(
                                        text = "Fill to full",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !uiState.isLoading) {
                                            viewModel.onMissedFuelUpChanged(!uiState.isMissedFuelUp)
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = uiState.isMissedFuelUp,
                                        onCheckedChange = null,
                                        enabled = !uiState.isLoading
                                    )
                                    Text(
                                        text = "Missed last",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    // Description (not for FUEL)
                    AnimatedVisibility(visible = uiState.expenseType != ExpenseType.FUEL) {
                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChanged,
                            label = { Text("Description") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            enabled = !uiState.isLoading
                        )
                    }

                    // Cost
                    OutlinedTextField(
                        value = uiState.cost,
                        onValueChange = viewModel::onCostChanged,
                        label = { Text("Cost") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        enabled = !uiState.isLoading
                    )

                    // Tax-specific: Is Recurring
                    AnimatedVisibility(visible = uiState.expenseType == ExpenseType.TAX) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.isRecurring,
                                onCheckedChange = viewModel::onRecurringChanged,
                                enabled = !uiState.isLoading
                            )
                            Text(
                                text = "Recurring expense",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = viewModel::onNotesChanged,
                        label = { Text("Notes (optional)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.saveExpense()
                            }
                        ),
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        enabled = !uiState.isLoading
                    )
                }
            }
            }

            // Bottom pinned section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expense Type Selector (hidden when keyboard is open)
                AnimatedVisibility(
                    visible = !isKeyboardOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Expense Type",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                expenseTypeOrder.forEach { type ->
                                    ExpenseTypeChip(
                                        type = type,
                                        selected = uiState.expenseType == type,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.onExpenseTypeChanged(type)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Save Button with animated color
                Button(
                    onClick = { viewModel.saveExpense() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animatedButtonColor
                    )
                ) {
                    AnimatedVisibility(visible = uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = if (uiState.isLoading) "Saving..." else "Save Expense",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            viewModel.onDateChanged(selectedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ExpenseTypeChip(
    type: ExpenseType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val (icon, color, label) = when (type) {
        ExpenseType.SERVICE -> Triple(Icons.Default.Build, ServiceBlue, "Service")
        ExpenseType.REPAIR -> Triple(Icons.Default.Handyman, RepairRed, "Repair")
        ExpenseType.UPGRADE -> Triple(Icons.AutoMirrored.Filled.TrendingUp, UpgradePurple, "Upgrade")
        ExpenseType.FUEL -> Triple(Icons.Default.LocalGasStation, FuelGreen, "Fuel")
        ExpenseType.TAX -> Triple(Icons.Default.Receipt, TaxOrange, "Tax")
    }

    // Animated scale for selection
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "chipScale"
    )

    // Animated background color
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) color else color.copy(alpha = 0.15f),
        animationSpec = tween(durationMillis = 200),
        label = "chipBgColor"
    )

    // Animated icon tint
    val iconTint by animateColorAsState(
        targetValue = if (selected) Color.White else color,
        animationSpec = tween(durationMillis = 200),
        label = "chipIconTint"
    )

    // Animated label color
    val labelColor by animateColorAsState(
        targetValue = if (selected) color else Color.Gray,
        animationSpec = tween(durationMillis = 200),
        label = "chipLabelColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = backgroundColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}
