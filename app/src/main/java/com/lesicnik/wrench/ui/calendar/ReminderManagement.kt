package com.lesicnik.wrench.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.data.repository.ReminderMetric
import com.lesicnik.wrench.data.sync.MaintenanceState
import com.lesicnik.wrench.data.sync.ReminderValues
import java.time.LocalDate

@Composable
internal fun ReminderChangeDialog(reminder: Reminder, scheduleOnly: Boolean, pending: MaintenanceState?, onDismiss: () -> Unit, onSave: (ReminderValues) -> Unit) {
    val initial = remember(reminder, pending) {
        pending?.reminder?.takeIf { it.status == "QUEUED" }?.targetJson?.let { Gson().fromJson(it, ReminderValues::class.java) }
            ?: ReminderValues.from(reminder)
    }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var metric by rememberSaveable { mutableStateOf(initial.metric) }
    var date by rememberSaveable { mutableStateOf(if (scheduleOnly) "" else initial.dueDate.orEmpty()) }
    var odometer by rememberSaveable { mutableStateOf(if (scheduleOnly) "" else initial.dueOdometer?.toString().orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var tags by rememberSaveable { mutableStateOf(initial.tags) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (scheduleOnly) "Reschedule reminder" else "Edit reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!scheduleOnly) OutlinedTextField(description, { description = it }, label = { Text("Description") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderMetric.entries.forEach { value ->
                        FilterChip(metric == value, { metric = value }, label = { Text(when (value) {
                            ReminderMetric.DATE -> "Date"; ReminderMetric.ODOMETER -> "Mileage"; ReminderMetric.BOTH -> "Both"
                        }) })
                    }
                }
                if (metric != ReminderMetric.ODOMETER) OutlinedTextField(date, { date = it }, label = { Text("Due date (YYYY-MM-DD)") }, singleLine = true)
                if (metric != ReminderMetric.DATE) OutlinedTextField(odometer, { odometer = it.filter(Char::isDigit).take(10) }, label = { Text("Odometer target") }, singleLine = true)
                if (!scheduleOnly) {
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tags (space separated)") })
                }
                Text("Recurring settings stay in LubeLogger.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsedDate = if (metric != ReminderMetric.ODOMETER) runCatching { LocalDate.parse(date) }.getOrNull() else null
            val target = ReminderValues(description, metric, parsedDate?.toString(), odometer.toIntOrNull()?.takeIf { metric != ReminderMetric.DATE }, notes, tags)
            error = target.draft().validationError()
            if (error == null) onSave(target)
        }) { Text("Save on this device") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun MaintenanceActivity(
    states: Map<Int, MaintenanceState>,
    descriptions: Map<Int, String>,
    onCancel: (Int) -> Unit,
    onResolve: (Int, Boolean) -> Unit,
    onReview: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        states.forEach { (id, state) ->
            if (state.reminder?.status == "CANCELLED" && state.upload == null || state.upload?.status == "CANCELLED" && state.reminder?.status in setOf(null, "CANCELLED")) return@forEach
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(descriptions[id] ?: "Reminder #$id", style = MaterialTheme.typography.titleSmall)
                    if (state.savedLocally) Text("Saved on this device", style = MaterialTheme.typography.bodySmall)
                    Text(state.label, style = MaterialTheme.typography.labelLarge)
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (state.upload?.status in setOf("NEEDS_REVIEW", "PAUSED", "DISPATCHING")) {
                        TextButton(onClick = { onReview(requireNotNull(state.upload).expenseLocalId) }) { Text("Review service upload") }
                    }
                    if (state.reminder?.status in setOf("QUEUED", "CONFLICT", "PAUSED")) {
                        Row {
                            TextButton(onClick = { onCancel(id) }) { Text(if (state.reminder?.status == "CONFLICT") "Use server version" else "Cancel reminder change") }
                            if (state.reminder?.status == "CONFLICT" && state.reminder.error?.startsWith("This reminder was removed") != true) TextButton(onClick = { onResolve(id, true) }) { Text("Apply my change") }
                            if (state.reminder?.status == "PAUSED") TextButton(onClick = { onResolve(id, true) }) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}
