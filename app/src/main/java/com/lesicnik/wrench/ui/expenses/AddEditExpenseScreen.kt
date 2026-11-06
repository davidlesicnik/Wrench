package com.lesicnik.wrench.ui.expenses

import com.lesicnik.wrench.data.repository.ReminderMetric
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.ui.components.EntryOptionalDetails
import com.lesicnik.wrench.ui.components.EntrySection
import com.lesicnik.wrench.ui.components.VehicleTopBar
import com.lesicnik.wrench.ui.components.entryFieldColors
import com.lesicnik.wrench.ui.settings.LocalAppPreferences
import com.lesicnik.wrench.ui.utils.ErrorSnackbarEffect
import com.lesicnik.wrench.ui.utils.getStyle
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExpenseScreen(
    viewModel: AddEditExpenseViewModel,
    odometerUnit: String = "km",
    lastOdometer: Int? = null,
    onNavigateBack: () -> Unit,
    onExpenseSaved: () -> Unit,
    vehicleName: String = ""
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showFuelOptions by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isEditMode) {
        if (uiState.notes.isNotBlank()) showNotes = true
        if (uiState.isMissedFuelUp) showFuelOptions = true
    }
    val enabled = !uiState.isLoading && !uiState.isDeleting && !uiState.isResolvingConflict && (!uiState.isLinkedService || uiState.linkedReminder != null)
    val back = { if (uiState.isDirty) viewModel.showDiscardDialog() else onNavigateBack() }
    BackHandler { if (enabled) back() }
    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onExpenseSaved() }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onNavigateBack() }
    ErrorSnackbarEffect(uiState.errorMessage, snackbarHostState, viewModel::clearError)
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val finish = KeyboardActions(onDone = { focusManager.clearFocus() })
    val fieldModifier = Modifier.fillMaxWidth()
    val currencyCode = LocalAppPreferences.current.currencyCode
    val expenseStyle = uiState.expenseType.getStyle()
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (darkSurface) lerp(expenseStyle.color, MaterialTheme.colorScheme.onSurface, 0.35f) else expenseStyle.color
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { VehicleTopBar(if (uiState.isEditMode) "Edit expense" else if (uiState.isLinkedService) "Record service" else "Add expense", vehicleName, { if (enabled) back() }) {
            if (uiState.isEditMode) IconButton(onClick = viewModel::showDeleteDialog, enabled = enabled) {
                Icon(Icons.Default.Delete, "Delete expense", tint = MaterialTheme.colorScheme.error)
            }
        } },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!uiState.isEditMode && !uiState.isLinkedService) {
                        Row(Modifier.fillMaxWidth().testTag("expense-type-selector").selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(ExpenseType.FUEL, ExpenseType.SERVICE, ExpenseType.REPAIR, ExpenseType.UPGRADE, ExpenseType.TAX).forEach { type ->
                                val style = type.getStyle()
                                val typeColor = if (darkSurface) lerp(style.color, MaterialTheme.colorScheme.onSurface, 0.35f) else style.color
                                val selected = uiState.expenseType == type
                                Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).selectable(
                                    selected = selected, enabled = enabled, role = Role.RadioButton,
                                    onClick = { viewModel.onExpenseTypeChanged(type) }),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) typeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.heightIn(min = 64.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(style.icon, null, tint = if (selected) typeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(style.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = { focusManager.clearFocus(); viewModel.saveExpense() }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        if (uiState.isLoading) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        if (!uiState.isLoading) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (uiState.isLoading) "Saving…" else if (uiState.isEditMode) "Save changes" else "Save ${uiState.expenseType.getStyle().label.lowercase()}")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp).testTag("expense-form"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EntrySection(
                title = if (uiState.expenseType == ExpenseType.FUEL) "Fill-up details" else "${expenseStyle.label} details",
                icon = expenseStyle.icon,
                accent = accent
            ) {
                Surface(onClick = { focusManager.clearFocus(); showDatePicker = true }, enabled = enabled,
                    shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((if (uiState.date == LocalDate.now()) "Today · " else "") + uiState.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Default.ExpandMore, "Change date", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (uiState.date != LocalDate.now()) TextButton(onClick = { viewModel.onDateChanged(LocalDate.now()) }, enabled = enabled) { Text("Today") }
                if (uiState.expenseType != ExpenseType.FUEL) OutlinedTextField(
                    value = uiState.description, onValueChange = viewModel::onDescriptionChanged,
                    label = { Text("Description") }, modifier = fieldModifier.testTag("expense-description"), enabled = enabled, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                    isError = uiState.fieldErrors["description"] != null,
                    supportingText = uiState.fieldErrors["description"]?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = next)
                if (uiState.expenseType != ExpenseType.TAX) OutlinedTextField(
                    value = uiState.odometer, onValueChange = viewModel::onOdometerChanged,
                    label = { Text("Odometer") }, suffix = { Text(odometerUnit) }, enabled = enabled, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                    modifier = fieldModifier.testTag("expense-odometer"), isError = uiState.fieldErrors["odometer"] != null,
                    supportingText = { (uiState.fieldErrors["odometer"] ?: lastOdometer?.let {
                        "Previous: ${NumberFormat.getIntegerInstance().format(it)} $odometerUnit"
                    })?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), keyboardActions = next)
                if (uiState.expenseType == ExpenseType.FUEL) OutlinedTextField(
                    value = uiState.fuelConsumed, onValueChange = viewModel::onFuelConsumedChanged,
                    label = { Text("Fuel amount") }, suffix = { Text("L") }, enabled = enabled, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                    modifier = fieldModifier.testTag("expense-fuel"), isError = uiState.fieldErrors["fuel"] != null,
                    supportingText = uiState.fieldErrors["fuel"]?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next), keyboardActions = next)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                OutlinedTextField(value = uiState.cost, onValueChange = viewModel::onCostChanged,
                    label = { Text("Total paid") }, suffix = { Text(currencyCode) },
                    placeholder = { Text("0.00") },
                    shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                    enabled = enabled, singleLine = true,
                    modifier = fieldModifier.testTag("expense-cost"), isError = uiState.fieldErrors["cost"] != null,
                    supportingText = uiState.fieldErrors["cost"]?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (showNotes) ImeAction.Next else ImeAction.Done),
                    keyboardActions = if (showNotes) next else finish)
            }
            if (uiState.expenseType == ExpenseType.FUEL) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(value = uiState.isFillToFull,
                            enabled = enabled, role = Role.Switch, onValueChange = viewModel::onFillToFullChanged).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Full tank", style = MaterialTheme.typography.titleSmall)
                                Text("Filled completely at this stop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = uiState.isFillToFull, onCheckedChange = null, enabled = enabled)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        TextButton(onClick = { focusManager.clearFocus(); showFuelOptions = !showFuelOptions }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                            Text("Fill-up options", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                            Icon(if (showFuelOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp))
                        }
                        AnimatedVisibility(showFuelOptions) {
                            Column(Modifier.padding(bottom = 8.dp)) {
                                Row(Modifier.fillMaxWidth().toggleable(value = uiState.isMissedFuelUp, enabled = enabled,
                                    role = Role.Checkbox, onValueChange = viewModel::onMissedFuelUpChanged), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = uiState.isMissedFuelUp, onCheckedChange = null, enabled = enabled)
                                    Text("Previous fill-up not recorded", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("Use this if you skipped recording a stop. Consumption won't be calculated across that gap.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (uiState.expenseType == ExpenseType.TAX) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.fillMaxWidth().toggleable(value = uiState.isRecurring, enabled = enabled,
                    role = Role.Checkbox, onValueChange = viewModel::onRecurringChanged).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.isRecurring, onCheckedChange = null, enabled = enabled)
                    Text("Recurring expense", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (uiState.linkedReminder != null) {
                AfterRecordingFields(uiState, viewModel, odometerUnit, enabled)
            }
            EntryOptionalDetails(
                title = if (uiState.notes.isBlank()) "Notes (optional)" else "Notes added",
                expanded = showNotes, enabled = enabled,
                onClick = { focusManager.clearFocus(); showNotes = !showNotes }
            ) {
                OutlinedTextField(value = uiState.notes, onValueChange = viewModel::onNotesChanged, label = { Text("Notes") },
                    shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                    modifier = fieldModifier.testTag("expense-notes"), minLines = 2, maxLines = 4, enabled = enabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = finish)
            }
        }
    }

    if (uiState.confirmReminderRemoval) AlertDialog(
        onDismissRequest = viewModel::dismissReminderRemoval,
        title = { Text("Permanently remove reminder?") },
        text = { Text("After the service uploads, this reminder and any recurring schedule will be permanently removed from LubeLogger.") },
        confirmButton = { TextButton(onClick = viewModel::confirmReminderRemoval) { Text("Save service and remove reminder") } },
        dismissButton = { TextButton(onClick = viewModel::dismissReminderRemoval) { Text("Cancel") } }
    )
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

@Composable
private fun AfterRecordingFields(state: AddEditExpenseUiState, vm: AddEditExpenseViewModel, unit: String, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("After recording", style = MaterialTheme.typography.titleMedium)
        listOf("UNCHANGED" to "Leave reminder unchanged", "SCHEDULE" to "Set next due date / mileage", "REMOVE" to "Remove reminder permanently").forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.afterRecording == value, enabled = enabled, role = Role.RadioButton,
                onClick = { vm.afterRecording(value) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.afterRecording == value, onClick = null, enabled = enabled, modifier = Modifier.padding(end = 12.dp))
                Text(label)
            }
        }
        if (state.afterRecording == "SCHEDULE") {
            val metric = state.linkedReminder?.metric
            if (metric != ReminderMetric.ODOMETER)
                OutlinedTextField(state.nextDueDate, vm::nextDueDate, label = { Text("Next due date (YYYY-MM-DD)") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (metric != ReminderMetric.DATE) {
                Row(Modifier.fillMaxWidth().toggleable(state.additionalDistance, enabled = enabled, role = Role.Checkbox,
                    onValueChange = vm::additionalDistance), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(state.additionalDistance, onCheckedChange = null, enabled = enabled)
                    Text("Add distance to service odometer")
                }
                OutlinedTextField(state.nextDueOdometer, vm::nextDueOdometer,
                    label = { Text(if (state.additionalDistance) "Distance after this service ($unit)" else "Next odometer target ($unit)") },
                    enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("Enter explicit targets. Recurring intervals stay in LubeLogger.", style = MaterialTheme.typography.bodySmall)
        }
        if (state.afterRecording == "REMOVE") Text("Permanent removal includes any recurring schedule. You will confirm separately before saving.", style = MaterialTheme.typography.bodySmall)
    }
}
