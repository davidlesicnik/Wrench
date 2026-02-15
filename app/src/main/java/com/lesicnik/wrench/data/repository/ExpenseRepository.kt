package com.lesicnik.wrench.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.repository.expense.ExpenseCache
import com.lesicnik.wrench.data.repository.expense.ExpenseMapper
import com.lesicnik.wrench.data.repository.expense.FuelStatsCalculator
import com.lesicnik.wrench.data.repository.offline.ExpenseOperationPayload
import com.lesicnik.wrench.data.repository.offline.computeFingerprint
import com.lesicnik.wrench.data.repository.offline.parseRemoteExpenseSnapshot
import com.lesicnik.wrench.data.repository.offline.toDomain
import com.lesicnik.wrench.data.repository.offline.toJson
import com.lesicnik.wrench.data.sync.ExpenseSyncState
import com.lesicnik.wrench.data.sync.OfflineSyncEngine
import com.lesicnik.wrench.data.sync.SyncOperationType
import com.lesicnik.wrench.data.sync.SyncWorkScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExpenseRepository(
    private val appContext: Context,
    private val database: WrenchDatabase = WrenchDatabase.getInstance(appContext),
    private val cache: ExpenseCache = ExpenseCache(),
    private val mapper: ExpenseMapper = ExpenseMapper(),
    private val fuelStatsCalculator: FuelStatsCalculator = FuelStatsCalculator(),
    private val syncEngine: OfflineSyncEngine = OfflineSyncEngine(database)
) {

    fun getCachedExpenses(vehicleId: Int): List<Expense>? = cache.getExpenses(vehicleId)

    fun getCachedFuelStatistics(vehicleId: Int): FuelStatistics? = cache.getFuelStatistics(vehicleId)

    fun getExpenseById(vehicleId: Int, expenseId: Int, type: ExpenseType): Expense? {
        return cache.getExpenses(vehicleId)?.find { it.id == expenseId && it.type == type }
    }

    fun invalidateCache(vehicleId: Int) {
        cache.invalidate(vehicleId)
    }

    suspend fun clearAllCaches() {
        cache.clearAll()
        database.clearAllTables()
    }

    suspend fun preloadVehicleData(serverUrl: String, apiKey: String, vehicleId: Int) {
        if (cache.getExpenses(vehicleId) != null && cache.getFuelStatistics(vehicleId) != null) {
            return
        }
        if (!cache.tryBeginPreload(vehicleId)) {
            return
        }

        try {
            getExpenses(serverUrl, apiKey, vehicleId)
            getFuelStatistics(serverUrl, apiKey, vehicleId)
        } finally {
            cache.endPreload(vehicleId)
        }
    }

    suspend fun getExpenses(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        forceRefresh: Boolean = false
    ): ApiResult<List<Expense>> {
        val localExpenses = readLocalExpenses(serverUrl, vehicleId)
        if (!forceRefresh && localExpenses.isNotEmpty()) {
            cache.putExpenses(vehicleId, localExpenses)
            return ApiResult.Success(localExpenses)
        }

        val syncResult = syncEngine.syncServer(serverUrl, apiKey)
        val refreshedLocalExpenses = readLocalExpenses(serverUrl, vehicleId)

        return when (syncResult) {
            is ApiResult.Success -> {
                cache.putExpenses(vehicleId, refreshedLocalExpenses)
                ApiResult.Success(refreshedLocalExpenses)
            }
            is ApiResult.Error -> {
                if (refreshedLocalExpenses.isNotEmpty()) {
                    cache.putExpenses(vehicleId, refreshedLocalExpenses)
                    ApiResult.Success(refreshedLocalExpenses)
                } else {
                    ApiResult.Error(syncResult.message)
                }
            }
        }
    }

    suspend fun addExpense(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        type: ExpenseType,
        date: LocalDate,
        odometer: Int?,
        description: String,
        cost: Double,
        notes: String,
        fuelConsumed: Double? = null,
        isFillToFull: Boolean = false,
        isMissedFuelUp: Boolean = false,
        isRecurring: Boolean = false
    ): ApiResult<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val entity = ExpenseEntity(
                serverUrl = serverUrl,
                vehicleRemoteId = vehicleId,
                remoteId = null,
                type = type.name,
                date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                cost = cost,
                odometer = odometer,
                description = if (type == ExpenseType.FUEL) "Fuel" else description,
                notes = notes.ifBlank { null },
                liters = fuelConsumed,
                fuelEconomy = null,
                isFillToFull = if (type == ExpenseType.FUEL) isFillToFull else null,
                isMissedFuelUp = if (type == ExpenseType.FUEL) isMissedFuelUp else null,
                isRecurring = if (type == ExpenseType.TAX) isRecurring else null,
                syncState = ExpenseSyncState.PENDING_CREATE.name,
                isDeletedLocal = false,
                updatedAt = now,
                lastSyncedFingerprint = null,
                lastSyncError = null
            )

            database.withTransaction {
                val localId = database.expenseDao().insertExpense(entity).toInt()
                enqueueOperation(
                    serverUrl = serverUrl,
                    vehicleId = vehicleId,
                    localExpenseId = localId,
                    opType = SyncOperationType.CREATE,
                    payload = null
                )
            }

            invalidateCache(vehicleId)
            SyncWorkScheduler.enqueueImmediateSync(appContext)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun deleteExpense(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        expenseId: Int,
        type: ExpenseType
    ): ApiResult<Unit> {
        return try {
            val entity = database.expenseDao().getByLocalId(expenseId)
                ?: return ApiResult.Error("Expense not found")

            database.withTransaction {
                if (entity.remoteId == null) {
                    database.pendingSyncOpDao().deleteForExpense(serverUrl, entity.localId)
                    database.expenseDao().deleteByLocalId(entity.localId)
                } else {
                    val payload = ExpenseOperationPayload(
                        baseRemoteId = entity.remoteId,
                        baseType = entity.type,
                        baseFingerprint = entity.lastSyncedFingerprint ?: entity.computeFingerprint()
                    )

                    database.expenseDao().updateExpense(
                        entity.copy(
                            isDeletedLocal = true,
                            syncState = ExpenseSyncState.PENDING_DELETE.name,
                            updatedAt = System.currentTimeMillis(),
                            lastSyncError = null
                        )
                    )

                    database.pendingSyncOpDao().deleteForExpense(serverUrl, entity.localId)
                    enqueueOperation(
                        serverUrl = serverUrl,
                        vehicleId = vehicleId,
                        localExpenseId = entity.localId,
                        opType = SyncOperationType.DELETE,
                        payload = payload
                    )
                }
            }

            invalidateCache(vehicleId)
            SyncWorkScheduler.enqueueImmediateSync(appContext)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun updateExpense(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        originalExpenseId: Int,
        originalType: ExpenseType,
        type: ExpenseType,
        date: LocalDate,
        odometer: Int?,
        description: String,
        cost: Double,
        notes: String,
        fuelConsumed: Double? = null,
        isFillToFull: Boolean = false,
        isMissedFuelUp: Boolean = false,
        isRecurring: Boolean = false
    ): ApiResult<Unit> {
        return try {
            val existing = database.expenseDao().getByLocalId(originalExpenseId)
                ?: return ApiResult.Error("Expense not found")

            val unresolvedConflict = database.syncConflictDao()
                .getUnresolvedConflictForExpense(serverUrl, existing.localId)
            val remoteConflictSnapshot = unresolvedConflict
                ?.remoteSnapshotJson
                ?.takeIf { it.isNotBlank() }
                ?.let(::parseRemoteExpenseSnapshot)

            val updatedEntity = existing.copy(
                type = type.name,
                date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                odometer = odometer,
                description = if (type == ExpenseType.FUEL) "Fuel" else description,
                cost = cost,
                notes = notes.ifBlank { null },
                liters = fuelConsumed,
                isFillToFull = if (type == ExpenseType.FUEL) isFillToFull else null,
                isMissedFuelUp = if (type == ExpenseType.FUEL) isMissedFuelUp else null,
                isRecurring = if (type == ExpenseType.TAX) isRecurring else null,
                updatedAt = System.currentTimeMillis(),
                lastSyncError = null,
                syncState = if (existing.remoteId == null) {
                    ExpenseSyncState.PENDING_CREATE.name
                } else {
                    ExpenseSyncState.PENDING_UPDATE.name
                },
                isDeletedLocal = false
            )

            database.withTransaction {
                database.expenseDao().updateExpense(updatedEntity)
                database.pendingSyncOpDao().deleteForExpense(serverUrl, existing.localId)

                if (existing.remoteId == null) {
                    enqueueOperation(
                        serverUrl = serverUrl,
                        vehicleId = vehicleId,
                        localExpenseId = existing.localId,
                        opType = SyncOperationType.CREATE,
                        payload = null
                    )
                } else {
                    val baseFingerprint = remoteConflictSnapshot?.computeFingerprint()
                        ?: existing.lastSyncedFingerprint
                        ?: existing.computeFingerprint()
                    val baseType = remoteConflictSnapshot?.type?.name ?: originalType.name
                    val baseRemoteId = remoteConflictSnapshot?.remoteId ?: existing.remoteId

                    enqueueOperation(
                        serverUrl = serverUrl,
                        vehicleId = vehicleId,
                        localExpenseId = existing.localId,
                        opType = SyncOperationType.UPDATE,
                        payload = ExpenseOperationPayload(
                            baseRemoteId = baseRemoteId,
                            baseType = baseType,
                            baseFingerprint = baseFingerprint
                        )
                    )
                }

                if (unresolvedConflict != null) {
                    database.syncConflictDao().updateConflict(
                        unresolvedConflict.copy(resolvedAt = System.currentTimeMillis())
                    )
                }
            }

            invalidateCache(vehicleId)
            SyncWorkScheduler.enqueueImmediateSync(appContext)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun resolveConflictKeepMine(
        serverUrl: String,
        vehicleId: Int,
        expenseId: Int
    ): ApiResult<Unit> {
        return try {
            val entity = database.expenseDao().getByLocalId(expenseId)
                ?: return ApiResult.Error("Expense not found")

            val conflict = database.syncConflictDao()
                .getUnresolvedConflictForExpense(serverUrl, expenseId)
                ?: return ApiResult.Error("No active conflict found")

            val remoteSnapshot = conflict.remoteSnapshotJson
                .takeIf { it.isNotBlank() }
                ?.let(::parseRemoteExpenseSnapshot)

            database.withTransaction {
                database.pendingSyncOpDao().deleteForExpense(serverUrl, entity.localId)

                if (remoteSnapshot == null) {
                    val recreated = entity.copy(
                        remoteId = null,
                        syncState = ExpenseSyncState.PENDING_CREATE.name,
                        isDeletedLocal = false,
                        updatedAt = System.currentTimeMillis(),
                        lastSyncError = null
                    )
                    database.expenseDao().updateExpense(recreated)
                    enqueueOperation(
                        serverUrl = serverUrl,
                        vehicleId = vehicleId,
                        localExpenseId = entity.localId,
                        opType = SyncOperationType.CREATE,
                        payload = null
                    )
                } else {
                    val updated = entity.copy(
                        syncState = ExpenseSyncState.PENDING_UPDATE.name,
                        isDeletedLocal = false,
                        updatedAt = System.currentTimeMillis(),
                        lastSyncError = null
                    )
                    database.expenseDao().updateExpense(updated)
                    enqueueOperation(
                        serverUrl = serverUrl,
                        vehicleId = vehicleId,
                        localExpenseId = entity.localId,
                        opType = SyncOperationType.UPDATE,
                        payload = ExpenseOperationPayload(
                            baseRemoteId = remoteSnapshot.remoteId,
                            baseType = remoteSnapshot.type.name,
                            baseFingerprint = remoteSnapshot.computeFingerprint()
                        )
                    )
                }

                database.syncConflictDao().updateConflict(
                    conflict.copy(resolvedAt = System.currentTimeMillis())
                )
            }

            invalidateCache(vehicleId)
            SyncWorkScheduler.enqueueImmediateSync(appContext)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun resolveConflictUseServer(
        serverUrl: String,
        vehicleId: Int,
        expenseId: Int
    ): ApiResult<Unit> {
        return try {
            val entity = database.expenseDao().getByLocalId(expenseId)
                ?: return ApiResult.Error("Expense not found")

            val conflict = database.syncConflictDao()
                .getUnresolvedConflictForExpense(serverUrl, expenseId)
                ?: return ApiResult.Error("No active conflict found")

            val remoteSnapshot = conflict.remoteSnapshotJson
                .takeIf { it.isNotBlank() }
                ?.let(::parseRemoteExpenseSnapshot)

            database.withTransaction {
                database.pendingSyncOpDao().deleteForExpense(serverUrl, entity.localId)

                if (remoteSnapshot == null) {
                    database.expenseDao().deleteByLocalId(entity.localId)
                } else {
                    database.expenseDao().updateExpense(
                        entity.copy(
                            remoteId = remoteSnapshot.remoteId,
                            type = remoteSnapshot.type.name,
                            date = remoteSnapshot.date,
                            cost = remoteSnapshot.cost,
                            odometer = remoteSnapshot.odometer,
                            description = remoteSnapshot.description,
                            notes = remoteSnapshot.notes,
                            liters = remoteSnapshot.liters,
                            isFillToFull = remoteSnapshot.isFillToFull,
                            isMissedFuelUp = remoteSnapshot.isMissedFuelUp,
                            isRecurring = remoteSnapshot.isRecurring,
                            syncState = ExpenseSyncState.SYNCED.name,
                            isDeletedLocal = false,
                            updatedAt = System.currentTimeMillis(),
                            lastSyncedFingerprint = remoteSnapshot.computeFingerprint(),
                            lastSyncError = null
                        )
                    )
                }

                database.syncConflictDao().updateConflict(
                    conflict.copy(resolvedAt = System.currentTimeMillis())
                )
            }

            invalidateCache(vehicleId)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun getFuelStatistics(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        forceRefresh: Boolean = false
    ): ApiResult<FuelStatistics> {
        if (!forceRefresh) {
            cache.getFuelStatistics(vehicleId)?.let { cached ->
                return ApiResult.Success(cached)
            }
        }

        return when (val expensesResult = getExpenses(serverUrl, apiKey, vehicleId, forceRefresh = forceRefresh)) {
            is ApiResult.Error -> ApiResult.Error(expensesResult.message)
            is ApiResult.Success -> {
                val stats = computeFuelStatistics(expensesResult.data)
                cache.putFuelStatistics(vehicleId, stats)
                ApiResult.Success(stats)
            }
        }
    }

    private suspend fun enqueueOperation(
        serverUrl: String,
        vehicleId: Int,
        localExpenseId: Int,
        opType: SyncOperationType,
        payload: ExpenseOperationPayload?
    ) {
        database.pendingSyncOpDao().insertOperation(
            PendingSyncOpEntity(
                serverUrl = serverUrl,
                vehicleRemoteId = vehicleId,
                expenseLocalId = localExpenseId,
                opType = opType.name,
                payloadJson = payload?.toJson(),
                baseFingerprint = payload?.baseFingerprint,
                createdAt = System.currentTimeMillis(),
                attemptCount = 0,
                lastError = null
            )
        )
    }

    private suspend fun readLocalExpenses(serverUrl: String, vehicleId: Int): List<Expense> {
        val entities = database.expenseDao().getVisibleExpenses(serverUrl, vehicleId)
        if (entities.isEmpty()) {
            return emptyList()
        }

        val fuelRecords = entities
            .filter { it.type == ExpenseType.FUEL.name }
            .map { entity ->
                FuelRecord(
                    id = entity.localId.toString(),
                    date = entity.date,
                    odometer = entity.odometer?.toString(),
                    fuelConsumed = entity.liters?.let { "%.2f".format(Locale.US, it) },
                    isFillToFull = entity.isFillToFull?.toString(),
                    cost = "%.2f".format(Locale.US, entity.cost),
                    notes = entity.notes
                )
            }

        val fuelEconomyByOdometer = fuelStatsCalculator.computeFuelEconomyByOdometer(
            fuelRecords = fuelRecords,
            parseMileage = mapper::parseMileage,
            parseNumber = mapper::parseNumber
        )

        return entities
            .map { entity ->
                val mapped = entity.toDomain()
                if (mapped.type == ExpenseType.FUEL) {
                    mapped.copy(fuelEconomy = mapped.odometer?.let { fuelEconomyByOdometer[it] })
                } else {
                    mapped
                }
            }
            .sortedByDescending { it.date }
    }

    private fun computeFuelStatistics(expenses: List<Expense>): FuelStatistics {
        val fuelRecords = expenses
            .filter { it.type == ExpenseType.FUEL }
            .map { expense ->
                FuelRecord(
                    id = expense.id.toString(),
                    date = expense.date.toString(),
                    odometer = expense.odometer?.toString(),
                    fuelConsumed = expense.liters?.let { "%.2f".format(Locale.US, it) },
                    isFillToFull = "true",
                    cost = "%.2f".format(Locale.US, expense.cost),
                    notes = expense.notes
                )
            }

        return fuelStatsCalculator.computeFuelStatistics(
            fuelRecords = fuelRecords,
            parseMileage = mapper::parseMileage,
            parseNumber = mapper::parseNumber
        )
    }
}
