package com.lesicnik.wrench.data.repository.offline

import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.VehicleEntity
import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.sync.ExpenseSyncState
import java.time.LocalDate

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = remoteId,
    imageLocation = imageLocation,
    year = year,
    make = make,
    model = model,
    licensePlate = licensePlate,
    soldDate = soldDate,
    isElectric = isElectric,
    useHours = useHours,
    odometerUnit = odometerUnit
)

fun Vehicle.toEntity(serverUrl: String, now: Long): VehicleEntity = VehicleEntity(
    serverUrl = serverUrl,
    remoteId = id,
    imageLocation = imageLocation,
    year = year,
    make = make,
    model = model,
    licensePlate = licensePlate,
    soldDate = soldDate,
    isElectric = isElectric,
    useHours = useHours,
    odometerUnit = odometerUnit,
    updatedAt = now
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = localId,
    type = runCatching { ExpenseType.valueOf(type) }.getOrDefault(ExpenseType.SERVICE),
    date = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() },
    cost = cost,
    odometer = odometer,
    description = description,
    notes = notes,
    liters = liters,
    fuelEconomy = fuelEconomy,
    syncState = runCatching { ExpenseSyncState.valueOf(syncState) }.getOrDefault(ExpenseSyncState.SYNCED),
    syncError = lastSyncError
)
