package com.lesicnik.wrench.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "vehicles",
    primaryKeys = ["serverUrl", "remoteId"],
    indices = [
        Index(value = ["serverUrl"]),
        Index(value = ["serverUrl", "year"])
    ]
)
data class VehicleEntity(
    val serverUrl: String,
    val remoteId: Int,
    val imageLocation: String?,
    val year: Int,
    val make: String,
    val model: String,
    val licensePlate: String?,
    val soldDate: String?,
    val isElectric: Boolean,
    val useHours: Boolean,
    val odometerUnit: String?,
    val updatedAt: Long
)
