package com.lesicnik.wrench.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["serverUrl"]),
        Index(value = ["expenseLocalId"])
    ]
)
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true)
    val conflictId: Long = 0,
    val serverUrl: String,
    val vehicleRemoteId: Int,
    val expenseLocalId: Int,
    val remoteSnapshotJson: String,
    val localSnapshotJson: String,
    val reason: String,
    val createdAt: Long,
    val resolvedAt: Long?
)
