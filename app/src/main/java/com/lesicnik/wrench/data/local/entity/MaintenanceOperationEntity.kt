package com.lesicnik.wrench.data.local.entity

import androidx.room.Entity

/** One durable intent per account/vehicle/reminder. Completed intents remain as receipts. */
@Entity(tableName = "reminder_operations", primaryKeys = ["accountKey", "vehicleId", "reminderId"])
data class ReminderOperationEntity(
    val accountKey: String,
    val serverUrl: String,
    val vehicleId: Int,
    val reminderId: Int,
    val originalJson: String,
    val targetJson: String?,
    val expenseLocalId: Int?,
    val status: String = "QUEUED",
    val error: String? = null,
    val force: Boolean = false
)

@Entity(tableName = "service_uploads", primaryKeys = ["expenseLocalId"])
data class ServiceUploadEntity(
    val expenseLocalId: Int,
    val accountKey: String,
    val serverUrl: String,
    val vehicleId: Int,
    val reminderId: Int,
    val status: String = "QUEUED",
    val remoteId: Int? = null,
    val error: String? = null
)
