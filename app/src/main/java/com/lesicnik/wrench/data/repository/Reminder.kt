package com.lesicnik.wrench.data.repository

import com.lesicnik.wrench.data.remote.ReminderRecord
import java.time.LocalDate

enum class ReminderMetric { DATE, ODOMETER, BOTH }

enum class ReminderUrgency(val label: String, val rank: Int) {
    NOT_URGENT("Scheduled", 0), URGENT("Due soon", 1), VERY_URGENT("Due very soon", 2), PAST_DUE("Overdue", 3), UNKNOWN("Reminder", -1)
}

data class Reminder(
    val id: Int,
    val vehicleId: Int,
    val description: String,
    val metric: ReminderMetric,
    val urgency: ReminderUrgency,
    val dueDate: LocalDate?,
    val dueOdometer: Int?,
    val dueDistance: Int?,
    val notes: String,
    val tags: String
)

fun ReminderRecord.toReminder(): Reminder {
    // Metric is the reason for urgency. UserMetric is the actual schedule (Both, Date, Odometer).
    val schedule = when ((userMetric ?: metric)?.lowercase()) {
        "date" -> ReminderMetric.DATE
        "odometer" -> ReminderMetric.ODOMETER
        "both" -> ReminderMetric.BOTH
        else -> error("Unsupported reminder schedule")
    }
    val date = if (schedule != ReminderMetric.ODOMETER) {
        // culture-invariant requests return yyyy-MM-dd, independent of the server locale.
        requireNotNull(dueDate).let { LocalDate.parse(it.take(10)) }.also {
            require(it.year > 1) { "Missing reminder date" }
        }
    } else null
    return Reminder(
        id = requireNotNull(id?.toIntOrNull()),
        vehicleId = requireNotNull(vehicleId?.toIntOrNull()),
        description = description?.takeIf { it.isNotBlank() } ?: "Maintenance reminder",
        metric = schedule,
        urgency = when (urgency?.lowercase()) {
            "noturgent" -> ReminderUrgency.NOT_URGENT
            "urgent" -> ReminderUrgency.URGENT
            "veryurgent" -> ReminderUrgency.VERY_URGENT
            "pastdue" -> ReminderUrgency.PAST_DUE
            else -> ReminderUrgency.UNKNOWN
        },
        dueDate = date,
        dueOdometer = if (schedule != ReminderMetric.DATE) requireNotNull(dueOdometer?.toIntOrNull()) else null,
        dueDistance = if (schedule != ReminderMetric.DATE) dueDistance?.toIntOrNull() else null,
        notes = notes.orEmpty(),
        tags = tags.orEmpty()
    )
}
