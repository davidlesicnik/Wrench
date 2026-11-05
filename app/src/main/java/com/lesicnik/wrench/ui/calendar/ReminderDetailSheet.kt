package com.lesicnik.wrench.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.repository.Reminder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderDetailSheet(reminder: Reminder, unit: String, today: LocalDate, onDismiss: () -> Unit, onManage: (() -> Unit)?) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { UrgencyBadge(reminder.urgency) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            Text(reminder.description, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Surface(shape = RoundedCornerShape(20.dp), color = calendarPanelColor(), border = calendarPanelBorder()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    reminder.dueDate?.let {
                        DetailValue(Icons.Default.CalendarMonth, "Due date", it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), relativeDate(it, today))
                    }
                    if (reminder.dueDate != null && reminder.dueOdometer != null) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    reminder.dueOdometer?.let {
                        DetailValue(Icons.Default.Speed, "Odometer target", "${reading(it)} $unit", reminder.dueDistance?.let { distance -> distanceLabel(distance, unit) })
                    }
                    if (reminder.dueDate != null && reminder.dueOdometer != null) {
                        Text("Whichever comes first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (reminder.notes.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                Text(reminder.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (reminder.tags.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reminder.tags.split(' ').filter(String::isNotBlank).forEach {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(it, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (onManage != null) {
                OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Manage in LubeLogger")
                }
            }
        }
    }
}

@Composable
private fun DetailValue(icon: ImageVector, label: String, value: String, supporting: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
