package com.lesicnik.wrench.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.data.repository.ReminderUrgency
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
internal fun calendarPanelColor() = MaterialTheme.colorScheme.let {
    if (it.surface.luminance() > 0.5f) it.surfaceContainerLowest else it.surfaceContainerLow
}

@Composable
internal fun calendarPanelBorder() = MaterialTheme.colorScheme.let {
    BorderStroke(1.dp, it.outlineVariant.copy(alpha = if (it.surface.luminance() > 0.5f) 0.6f else 0.35f))
}

@Composable
internal fun CalendarViewSwitch(mileage: Boolean, calendarCount: Int, mileageCount: Int, onSelect: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false, true).forEach { isMileage ->
                val active = mileage == isMileage
                val color by animateColorAsState(if (active) calendarPanelColor() else Color.Transparent, label = "calendar-view")
                Surface(onClick = { onSelect(isMileage) }, modifier = Modifier.weight(1f).semantics { selected = active; role = Role.Tab },
                    shape = RoundedCornerShape(14.dp), color = color,
                    shadowElevation = if (active) 1.dp else 0.dp) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isMileage) Icons.Default.Speed else Icons.Default.CalendarMonth, null, Modifier.size(18.dp),
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isMileage) "Mileage · $mileageCount" else "Calendar · $calendarCount",
                            style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    reminders: List<Reminder>,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val locale = Locale.getDefault()
    val start = month.atDay(1)
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val offset = (start.dayOfWeek.value - firstDay.value + 7) % 7
    val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    val dates = reminders.groupBy { it.dueDate }
    Surface(shape = RoundedCornerShape(24.dp), color = calendarPanelColor(), border = calendarPanelBorder()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    Text(firstDay.plus(index.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, locale), Modifier.weight(1f),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            repeat(cells / 7) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { day ->
                        val date = start.plusDays((week * 7 + day - offset).toLong())
                        val inMonth = YearMonth.from(date) == month
                        val events = dates[date].orEmpty()
                        val active = date == selectedDate
                        val dot = events.maxByOrNull { it.urgency.rank }?.urgency
                        val fill by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, label = "calendar-day")
                        Surface(onClick = { onSelect(date) }, enabled = inMonth, color = Color.Transparent,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                                contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) +
                                    ", ${events.size} ${if (events.size == 1) "reminder" else "reminders"}"
                                selected = active
                                if (events.isNotEmpty()) stateDescription = events.map { it.urgency.label }.distinct().joinToString(", ")
                            }) {
                            Column(Modifier.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Surface(shape = CircleShape, color = fill,
                                    border = if (date == today && !active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        Text(if (inMonth) date.dayOfMonth.toString() else "", style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (date == today || active) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Box(Modifier.size(4.dp).background(if (dot != null && inMonth) urgencyColor(dot) else Color.Transparent, CircleShape))
                            }
                        }
                    }
                }
            }
            if (dates.keys.any { it != null && YearMonth.from(it) == month }) {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ReminderUrgency.NOT_URGENT, ReminderUrgency.URGENT, ReminderUrgency.PAST_DUE).forEach {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(5.dp).background(urgencyColor(it), CircleShape))
                            Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReminderCard(reminder: Reminder, unit: String, mileage: Boolean, today: LocalDate, onClick: () -> Unit) {
    // Lazy rows can recompose during a view transition before the old item is removed.
    val target = reminder.dueOdometer
    val showMileage = mileage && target != null
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = calendarPanelColor(), border = calendarPanelBorder()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = urgencyColor(reminder.urgency).copy(alpha = 0.1f)) {
                Icon(if (showMileage) Icons.Default.Speed else Icons.Default.CalendarMonth, null, Modifier.padding(10.dp).size(20.dp), tint = urgencyColor(reminder.urgency))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(reminder.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showMileage) {
                    reminder.dueDistance?.let { Text(distanceLabel(it, unit), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
                    Text("At ${reading(target)} $unit" +
                        (reminder.dueDate?.let { " · by ${it.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(listOfNotNull(reminder.dueDate?.let { relativeDate(it, today) }, reminder.dueOdometer?.let { "${reading(it)} $unit" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                UrgencyBadge(reminder.urgency)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "View reminder", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun UrgencyBadge(urgency: ReminderUrgency) {
    val color = urgencyColor(urgency)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(urgency.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun urgencyColor(urgency: ReminderUrgency) = when (urgency) {
    ReminderUrgency.PAST_DUE -> MaterialTheme.colorScheme.error
    ReminderUrgency.URGENT, ReminderUrgency.VERY_URGENT -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

internal fun reading(value: Int) = NumberFormat.getIntegerInstance().format(value)
internal fun distanceLabel(distance: Int, unit: String) = when {
    distance < 0 -> "${NumberFormat.getIntegerInstance().format(-distance.toLong())} $unit overdue"
    distance == 0 -> "Odometer target reached"
    else -> "${reading(distance)} $unit remaining"
}

internal fun relativeDate(date: LocalDate, today: LocalDate): String = when (val days = ChronoUnit.DAYS.between(today, date)) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    in 2L..30L -> "In $days days"
    in -30L..-2L -> "${-days} days ago"
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
