package com.lesicnik.wrench.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_sync_ops",
    indices = [
        Index(value = ["serverUrl"]),
        Index(value = ["expenseLocalId"]),
        Index(value = ["serverUrl", "vehicleRemoteId"])
    ]
)
data class PendingSyncOpEntity(
    @PrimaryKey(autoGenerate = true)
    val opId: Long = 0,
    val serverUrl: String,
    val vehicleRemoteId: Int,
    val expenseLocalId: Int,
    val opType: String,
    val payloadJson: String?,
    val baseFingerprint: String?,
    val createdAt: Long,
    val attemptCount: Int,
    val lastError: String?
)
