package com.lesicnik.wrench.data.repository

import java.time.LocalDate

data class ReminderDraft(
    val description: String,
    val metric: ReminderMetric,
    val dueDate: LocalDate?,
    val dueOdometer: Int?,
    val notes: String = "",
    val tags: String = ""
) {
    fun validationError(): String? = when {
        description.isBlank() -> "Enter a description."
        metric != ReminderMetric.ODOMETER && dueDate == null -> "Choose a due date."
        metric != ReminderMetric.DATE && (dueOdometer == null || dueOdometer < 0) -> "Enter a valid odometer target."
        else -> null
    }

    val apiMetric: String get() = when (metric) {
        ReminderMetric.DATE -> "Date"
        ReminderMetric.ODOMETER -> "Odometer"
        ReminderMetric.BOTH -> "Both"
    }
}

sealed interface ReminderCreateResult {
    data class Created(val recordId: Int?) : ReminderCreateResult
    data class Failed(val message: String, val mayHaveSaved: Boolean = false) : ReminderCreateResult
}
