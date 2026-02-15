package com.lesicnik.wrench.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "sync_meta",
    primaryKeys = ["serverUrl", "vehicleRemoteId"]
)
data class SyncMetaEntity(
    val serverUrl: String,
    val vehicleRemoteId: Int,
    val lastFullSyncAt: Long
)
