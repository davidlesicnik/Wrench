package com.lesicnik.wrench.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["serverUrl"]),
        Index(value = ["serverUrl", "vehicleRemoteId"]),
        Index(value = ["serverUrl", "remoteId", "type"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Int = 0,
    val serverUrl: String,
    val vehicleRemoteId: Int,
    val remoteId: Int?,
    val type: String,
    val date: String,
    val cost: Double,
    val odometer: Int?,
    val description: String,
    val notes: String?,
    val liters: Double?,
    val fuelEconomy: Double?,
    val isFillToFull: Boolean?,
    val isMissedFuelUp: Boolean?,
    val isRecurring: Boolean?,
    val syncState: String,
    val isDeletedLocal: Boolean,
    val updatedAt: Long,
    val lastSyncedFingerprint: String?,
    val lastSyncError: String?
)
