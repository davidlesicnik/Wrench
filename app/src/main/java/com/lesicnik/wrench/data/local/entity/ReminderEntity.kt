package com.lesicnik.wrench.data.local.entity

import androidx.room.Entity

@Entity(tableName = "reminders", primaryKeys = ["accountKey", "vehicleId", "id"])
data class ReminderEntity(
    val accountKey: String,
    val vehicleId: Int,
    val id: Int,
    val description: String,
    val metric: String,
    val urgency: String,
    val dueDate: String?,
    val dueOdometer: Int?,
    val dueDistance: Int?,
    val notes: String,
    val tags: String
)

@Entity(tableName = "reminder_sync", primaryKeys = ["accountKey", "vehicleId"])
data class ReminderSyncEntity(val accountKey: String, val vehicleId: Int, val fetchedAt: Long)
