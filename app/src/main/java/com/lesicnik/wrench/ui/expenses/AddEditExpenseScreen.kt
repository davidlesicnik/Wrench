package com.lesicnik.wrench.ui.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import com.lesicnik.wrench.ui.utils.color
import com.lesicnik.wrench.ui.utils.getStyle
import com.lesicnik.wrench.ui.utils.rememberKeyboardVisibility
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExpenseScreen(
    viewModel: AddEditExpenseViewModel,
    odometerUnit: String = "km",
    lastOdometer: Int? = null,
    onNavigateBack: () -> Unit,
    onExpenseSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val currencyCode = com.lesicnik.wrench.ui.settings.LocalAppPreferences.current.currencyCode
    var showDatePicker by remember { mutableStateOf(false) }
    val isKeyboardOpen by rememberKeyboardVisibility()
    val nextFieldKeyboardActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Down) }
    )
    val standardFieldModifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp)

    // Detect compact screen (e.g., Galaxy Flip cover screen ~524dp)
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenHeightDp < 550

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
        uiState.expenseType.color
    }

    // Animated button color
    val animatedButtonColor by animateColorAsState(
        targetValue = currentTypeColor,
        animationSpec = tween(durationMillis = 300),
        label = "buttonColor"
    )

    // Handle back button with discard dialog
    BackHandler {
        if (uiState.isDirty) {
            viewModel.showDiscardDialog()
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onExpenseSaved()
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    ErrorSnackbarEffect(
        errorMessage = uiState.errorMessage,
        snackbarHostState = snackbarHostState,
        onErrorConsumed = viewModel::clearError
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Expense" else "Add Expense") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isDirty) {
                            viewModel.showDiscardDialog()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        IconButton(
                            onClick = { viewModel.showDeleteDialog() },
                            enabled = !uiState.isLoading && !uiState.isDeleting && !uiState.isResolvingConflict
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                    .padding(if (isCompactScreen) 8.dp else 16.dp)
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
                            isError = uiState.fieldErrors["odometer"] != null,
                            supportingText = {
                                Text(uiState.fieldErrors["odometer"] ?: lastOdometer?.let {
                                    "Previous reading: ${NumberFormat.getNumberInstance().format(it)} $odometerUnit"
                                } ?: "Odometer reading at the time of this expense")
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
                            keyboardActions = nextFieldKeyboardActions,
                            singleLine = true,
                            modifier = standardFieldModifier,
                            enabled = !uiState.isLoading
                        )
                    }

                    // Fuel-specific fields
                    AnimatedVisibility(visible = uiState.expenseType == ExpenseType.FUEL) {
                        Column {
                            // Fuel amount with decimal keyboard input
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
                                isError = uiState.fieldErrors["fuel"] != null,
                                supportingText = { Text(uiState.fieldErrors["fuel"] ?: "Litres, for example 52.13") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = nextFieldKeyboardActions,
                                singleLine = true,
                                modifier = standardFieldModifier,
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
                                        text = "Missed a fill-up",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.expenseType == ExpenseType.FUEL) {
                        Text(
                            "Fill to full: the tank was filled completely. Missed a fill-up: a previous stop was not recorded, so consumption cannot be calculated reliably.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    // Description (not for FUEL)
                    AnimatedVisibility(visible = uiState.expenseType != ExpenseType.FUEL) {
                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChanged,
                            label = { Text("Description") },
                            isError = uiState.fieldErrors["description"] != null,
                            supportingText = { uiState.fieldErrors["description"]?.let { Text(it) } },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = nextFieldKeyboardActions,
                            singleLine = true,
                            modifier = standardFieldModifier,
                            enabled = !uiState.isLoading
                        )
                    }

                    // Cost with decimal keyboard input
                    OutlinedTextField(
                        value = uiState.cost,
                        onValueChange = viewModel::onCostChanged,
                        label = { Text("Cost ($currencyCode)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null
                            )
                        },
                        isError = uiState.fieldErrors["cost"] != null,
                        supportingText = { Text(uiState.fieldErrors["cost"] ?: "Total paid, for example 84.82") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = nextFieldKeyboardActions,
                        singleLine = true,
                        modifier = standardFieldModifier,
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
                                imageVector = Icons.AutoMirrored.Filled.Notes,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        minLines = 2,
                        maxLines = 4,
                        modifier = standardFieldModifier,
                        enabled = !uiState.isLoading
                    )
                }
            }
            }

            // Bottom pinned section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompactScreen) 8.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompactScreen) 8.dp else 16.dp)
            ) {
                if (!uiState.isEditMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .padding(6.dp)
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        expenseTypeOrder.forEach { type ->
                            val selected = uiState.expenseType == type
                            val style = type.getStyle()
                            val tileColor by animateColorAsState(
                                targetValue = if (selected) style.color.copy(alpha = 0.14f) else Color.Transparent,
                                animationSpec = tween(180),
                                label = "typeBackground"
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(tileColor)
                                    .border(1.dp, if (selected) style.color.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(18.dp))
                                    .selectable(
                                        selected = selected,
                                        enabled = !uiState.isLoading,
                                        role = Role.RadioButton,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.onExpenseTypeChanged(type)
                                        }
                                    )
                                    .heightIn(min = 72.dp)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                            ) {
                                Box(
                                    modifier = Modifier.size(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(style.icon, contentDescription = null,
                                        tint = if (selected) style.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp))
                                }
                                Text(style.label, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    enabled = !uiState.isLoading && !uiState.isDeleting && !uiState.isResolvingConflict,
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
                        text = when {
                            uiState.isLoading -> if (uiState.isEditMode) "Updating…" else "Saving…"
                            uiState.isEditMode -> "Update ${uiState.expenseType.getStyle().label} Expense"
                            else -> "Save ${uiState.expenseType.getStyle().label} Expense"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
    } // Box

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                .atStartOfDay(java.time.ZoneOffset.UTC)
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
                                .atZone(java.time.ZoneOffset.UTC)
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

    // Conflict Resolution Dialog
    if (uiState.showConflictResolutionDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Resolve Conflict") },
            text = {
                Column {
                    Text(
                        text = "This expense was changed on another device/server while you had local changes.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!uiState.conflictReason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.conflictReason ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose which version to keep:",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.resolveConflictKeepMine() },
                    enabled = !uiState.isResolvingConflict
                ) {
                    if (uiState.isResolvingConflict) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Keep mine")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.resolveConflictUseServer() },
                    enabled = !uiState.isResolvingConflict
                ) {
                    Text("Use server")
                }
            }
        )
    }

    // Discard Changes Dialog
    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardDialog() },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDiscardDialog()
                        onNavigateBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDiscardDialog() }) {
                    Text("Keep editing")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (uiState.showDeleteDialog) {
        val expense = uiState.originalExpense
        val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
        val currencyFormatter = com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter()

        AlertDialog(
            onDismissRequest = { if (!uiState.isDeleting) viewModel.dismissDeleteDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete expense?") },
            text = {
                Column {
                    Text(
                        text = "This will permanently delete:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    expense?.let {
                        Text(
                            text = it.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${it.date.format(dateFormatter)} - ${currencyFormatter.format(it.cost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    enabled = !uiState.isDeleting
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteDialog() },
                    enabled = !uiState.isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
