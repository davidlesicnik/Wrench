package com.lesicnik.wrench.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.components.entryFieldColors

@Composable
internal fun ReminderMileageFields(
    state: AddReminderUiState,
    unit: String,
    enabled: Boolean,
    onUpdate: ((AddReminderUiState) -> AddReminderUiState) -> Unit,
    finishInput: () -> Unit
) {
    var moreOptions by rememberSaveable { mutableStateOf(state.mileageInputMode == MileageInputMode.ODOMETER || state.startingReadingEdited) }
    val current = state.startingOdometer.toIntOrNull()
    val absolute = state.mileageInputMode == MileageInputMode.ODOMETER
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = if (absolute) state.odometer else state.distance,
            onValueChange = { value -> onUpdate { if (absolute) it.copy(odometer = value) else it.copy(distance = value) } },
            modifier = Modifier.fillMaxWidth().testTag(if (absolute) "reminder-odometer" else "reminder-distance"),
            shape = RoundedCornerShape(14.dp), colors = entryFieldColors(), label = { Text(if (absolute) "At odometer reading" else "Remind me in") },
            suffix = { Text(unit) }, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { finishInput() }),
            isError = if (absolute) state.odometerInvalid else state.distanceInvalid,
            supportingText = if ((absolute && state.odometerInvalid) || (!absolute && state.distanceInvalid)) {
                { Text(if (absolute) "Enter a valid whole-number reading." else "Enter a positive distance that gives a valid target.") }
            } else null
        )
        if (!absolute) {
            val presets = if (unit.lowercase() in listOf("h", "hr", "hrs", "hour", "hours")) listOf(50, 100, 250) else listOf(5000, 10000, 15000)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { interval ->
                    FilterChip(selected = state.distance.toIntOrNull() == interval, enabled = enabled,
                        modifier = Modifier.testTag("reminder-distance-$interval"),
                        onClick = { finishInput(); onUpdate { it.copy(distance = interval.toString()) } },
                        label = { Text("${reading(interval)} $unit") })
                }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Flag, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.targetOdometer?.let { target ->
                        Text("Reminder at ${reading(target)} $unit", modifier = Modifier.testTag("reminder-target-preview"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(when {
                        absolute -> "Exact odometer target"
                        state.isLoadingOdometer && current == null -> "Getting the recorded odometer…"
                        current != null && state.startingReadingEdited -> "Based on ${reading(current)} $unit for this reminder"
                        current != null -> "Based on recorded ${reading(current)} $unit"
                        else -> "Recorded odometer unavailable"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = { finishInput(); moreOptions = !moreOptions }, enabled = enabled,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
            Text(if (moreOptions) "Fewer mileage options" else if (!absolute && current == null && !state.isLoadingOdometer) "Enter a starting reading" else "More mileage options")
            Spacer(Modifier.width(4.dp))
            Icon(if (moreOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        AnimatedVisibility(moreOptions) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().testTag("reminder-use-exact-target").toggleable(value = absolute, enabled = enabled, role = Role.Checkbox,
                    onValueChange = { checked -> finishInput(); onUpdate { it.copy(mileageInputMode = if (checked) MileageInputMode.ODOMETER else MileageInputMode.DISTANCE) } }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = absolute, enabled = enabled, onCheckedChange = null)
                    Text("At a specific odometer reading", style = MaterialTheme.typography.bodyMedium)
                }
                if (!absolute) {
                    OutlinedTextField(value = state.startingOdometer,
                        onValueChange = { value -> onUpdate { it.copy(startingOdometer = value, startingReadingEdited = true) } },
                        modifier = Modifier.fillMaxWidth().testTag("reminder-starting-odometer"), shape = RoundedCornerShape(14.dp), colors = entryFieldColors(),
                        label = { Text("Starting reading for this reminder") }, suffix = { Text(unit) },
                        enabled = enabled, singleLine = true,
                        isError = state.startingOdometer.isNotBlank() && current?.let { it >= 0 } != true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finishInput() }))
                    Text("Only used to calculate this reminder. Your vehicle's recorded mileage won't change.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
