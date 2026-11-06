package com.lesicnik.wrench.data.sync

import com.lesicnik.wrench.data.local.entity.ReminderOperationEntity
import com.lesicnik.wrench.data.local.entity.ServiceUploadEntity
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.data.repository.ReminderDraft
import com.lesicnik.wrench.data.repository.ReminderMetric
import java.time.LocalDate

/** Dates and mileage are absolute values before an intent reaches Room. */
data class ReminderValues(
    val description: String,
    val metric: ReminderMetric,
    val dueDate: String?,
    val dueOdometer: Int?,
    val notes: String,
    val tags: String
) {
    fun draft() = ReminderDraft(description, metric, dueDate?.let(LocalDate::parse), dueOdometer, notes, tags)
    fun fields(): List<Any?> = listOf(description, metric, dueDate, dueOdometer, notes, tags)
    fun merge(original: ReminderValues, current: ReminderValues): ReminderValues {
        val scheduleChanged = metric != original.metric || dueDate != original.dueDate || dueOdometer != original.dueOdometer
        val mergedMetric = if (scheduleChanged) metric else current.metric
        // Restoring a metric also restores its required values, even when they were unchanged locally.
        val restoreSchedule = scheduleChanged && mergedMetric != current.metric
        return ReminderValues(
            if (description != original.description) description else current.description,
            mergedMetric,
            if (mergedMetric == ReminderMetric.ODOMETER) null
            else if (dueDate != original.dueDate || restoreSchedule) dueDate else current.dueDate,
            if (mergedMetric == ReminderMetric.DATE) null
            else if (dueOdometer != original.dueOdometer || restoreSchedule) dueOdometer else current.dueOdometer,
            if (notes != original.notes) notes else current.notes,
            if (tags != original.tags) tags else current.tags
        )
    }
    fun conflicts(original: ReminderValues, current: ReminderValues): Boolean {
        if ((dueDate != original.dueDate || dueOdometer != original.dueOdometer) && current.metric != original.metric) return true
        return fields().indices.any { fields()[it] != original.fields()[it] &&
            current.fields()[it] != original.fields()[it] && current.fields()[it] != fields()[it] }
    }
    companion object {
        fun from(r: Reminder) = ReminderValues(r.description, r.metric, r.dueDate?.toString(), r.dueOdometer, r.notes, r.tags)
    }
}

data class MaintenanceState(val reminder: ReminderOperationEntity?, val upload: ServiceUploadEntity?) {
    val label: String get() = when {
        upload?.status in attention || reminder?.status in attention -> "Needs attention"
        upload != null && upload.status != "CONFIRMED" && upload.status != "CANCELLED" -> "Waiting for service upload"
        reminder != null && reminder.status !in terminal -> "Reminder change pending"
        else -> "Synced with LubeLogger"
    }
    val error: String? get() = upload?.error ?: reminder?.error
    val savedLocally: Boolean get() = upload?.status !in setOf(null, "CONFIRMED", "CANCELLED") || reminder?.status !in setOf(null, "CONFIRMED", "CANCELLED")
    companion object {
        val terminal = setOf("CONFIRMED", "CANCELLED")
        val attention = setOf("CONFLICT", "PAUSED", "NEEDS_REVIEW")
    }
}
