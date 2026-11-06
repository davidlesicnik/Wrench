package com.lesicnik.wrench.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.repository.ReminderMetric
import com.lesicnik.wrench.ui.components.EntryOptionalDetails
import com.lesicnik.wrench.ui.components.EntrySection
import com.lesicnik.wrench.ui.components.entryFieldColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AddReminderScreen(
    viewModel: AddReminderViewModel,
    vehicleName: String,
    odometerUnit: String,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved(state.savedLocation) }
    AddReminderContent(state, vehicleName, odometerUnit, viewModel::update, viewModel::save, onNavigateBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddReminderContent(
    state: AddReminderUiState,
    vehicleName: String,
    odometerUnit: String,
    onUpdate: ((AddReminderUiState) -> AddReminderUiState) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    today: LocalDate = LocalDate.now()
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(state.notes.isNotBlank() || state.tags.isNotBlank()) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.isSaving && !state.saved
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val finishInput = { focus.clearFocus(); keyboard?.hide(); Unit }
    val back = { if (state.hasChanges) showDiscard = true else onNavigateBack() }
    BackHandler { if (enabled) back() }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("New reminder", style = MaterialTheme.typography.titleLarge)
                    Text(vehicleName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                navigationIcon = { IconButton(onClick = back, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hint = when {
                        state.requiresCalendarCheck -> "Check the calendar before trying again."
                        state.description.isBlank() -> "Add a reminder to continue."
                        state.metric != ReminderMetric.DATE && state.mileageInputMode == MileageInputMode.DISTANCE && state.isLoadingOdometer -> "Loading the latest recorded odometer…"
                        state.metric != ReminderMetric.DATE && state.mileageInputMode == MileageInputMode.DISTANCE && state.startingOdometer.toIntOrNull() == null -> "Enter a starting odometer to continue."
                        state.metric != ReminderMetric.DATE && state.draft.dueOdometer == null -> if (state.mileageInputMode == MileageInputMode.DISTANCE) "Enter a distance to continue." else "Enter an odometer target to continue."
                        else -> null
                    }
                    if (hint != null && !state.isSaving) Text(hint, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button(onClick = { finishInput(); if (state.requiresCalendarCheck) onNavigateBack() else onSave() },
                        enabled = state.canSave || (state.requiresCalendarCheck && enabled),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text("Saving…")
                        } else {
                            Icon(if (state.requiresCalendarCheck) Icons.Default.CalendarMonth else Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.requiresCalendarCheck) "Check calendar" else "Save reminder")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag("reminder-form")
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.errorMessage != null) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(state.errorMessage, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            EntrySection(title = "What needs attention?", icon = Icons.Default.NotificationsNone) {
                OutlinedTextField(
                    value = state.description, onValueChange = { value -> onUpdate { it.copy(description = value) } },
                    modifier = Modifier.fillMaxWidth().testTag("reminder-description"), shape = RoundedCornerShape(14.dp),
                    colors = entryFieldColors(),
                    label = { Text("Reminder") }, placeholder = { Text("Oil change") }, maxLines = 3, enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finishInput() })
                )
            }
            EntrySection(title = "When should I remind you?", icon = Icons.Default.Schedule) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(ReminderMetric.ODOMETER, ReminderMetric.DATE, ReminderMetric.BOTH).forEach { metric ->
                            val active = state.metric == metric
                            val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, label = "schedule")
                            Surface(onClick = { finishInput(); onUpdate { it.copy(metric = metric) } }, enabled = enabled,
                                shape = RoundedCornerShape(10.dp), color = color,
                                modifier = Modifier.weight(1f).semantics { selected = active; role = Role.RadioButton }) {
                                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(when (metric) { ReminderMetric.DATE -> "Date"; ReminderMetric.ODOMETER -> "Mileage"; ReminderMetric.BOTH -> "Both" },
                                        style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (state.metric != ReminderMetric.ODOMETER) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedTextField(value = state.dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), onValueChange = {},
                            modifier = Modifier.fillMaxWidth(), label = { Text("Due date") }, readOnly = true, enabled = enabled,
                            shape = RoundedCornerShape(14.dp), colors = entryFieldColors(), trailingIcon = { Icon(Icons.Default.CalendarMonth, null) })
                        Surface(onClick = { finishInput(); showDatePicker = true }, enabled = enabled,
                            color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.matchParentSize().testTag("reminder-date").semantics {
                                contentDescription = "Choose due date, ${state.dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}"
                            }) { }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("In 1 month" to today.plusMonths(1), "In 1 year" to today.plusYears(1)).forEach { (label, date) ->
                            SuggestionChip(onClick = { finishInput(); onUpdate { it.copy(dueDate = date) } }, enabled = enabled, label = { Text(label) })
                        }
                    }
                }
                if (state.metric != ReminderMetric.DATE) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderMileageFields(state, odometerUnit, enabled, onUpdate, finishInput)
                    if (state.metric == ReminderMetric.BOTH) Text("The date or mileage, whichever comes first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            EntryOptionalDetails(title = "Notes and tags (optional)", expanded = showDetails, enabled = enabled,
                onClick = { finishInput(); showDetails = !showDetails }) {
                OutlinedTextField(value = state.notes, onValueChange = { value -> onUpdate { it.copy(notes = value) } },
                    modifier = Modifier.fillMaxWidth().testTag("reminder-notes"), shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                    label = { Text("Notes") }, minLines = 3, maxLines = 6, enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(value = state.tags, onValueChange = { value -> onUpdate { it.copy(tags = value) } },
                    modifier = Modifier.fillMaxWidth().testTag("reminder-tags"), shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                    label = { Text("Tags") }, supportingText = { Text("Separate tags with spaces.") }, singleLine = true, enabled = enabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { finishInput() }))
            }
        }
    }
    if (showDiscard) AlertDialog(onDismissRequest = { showDiscard = false }, title = { Text("Discard this reminder?") },
        text = { Text("Your changes haven't been saved.") },
        confirmButton = { TextButton(onClick = { showDiscard = false; onNavigateBack() },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } })
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { millis -> onUpdate { it.copy(dueDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) } }
                showDatePicker = false
            }) { Text("OK") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = picker, showModeToggle = false) }
    }
}
