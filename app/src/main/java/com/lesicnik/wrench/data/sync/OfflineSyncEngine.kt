package com.lesicnik.wrench.data.sync

import androidx.room.withTransaction
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity
import com.lesicnik.wrench.data.local.entity.SyncConflictEntity
import com.lesicnik.wrench.data.local.entity.SyncMetaEntity
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.expense.ExpenseMapper
import com.lesicnik.wrench.data.repository.expense.ExpenseRecordBundle
import com.lesicnik.wrench.data.repository.expense.ExpenseRemoteDataSource
import com.lesicnik.wrench.data.repository.offline.RemoteExpenseSnapshot
import com.lesicnik.wrench.data.repository.offline.computeFingerprint
import com.lesicnik.wrench.data.repository.offline.parseExpenseOperationPayload
import com.lesicnik.wrench.data.repository.offline.toEntity
import com.lesicnik.wrench.data.repository.offline.toJson as entityToJson
import com.lesicnik.wrench.data.repository.offline.toJson as snapshotToJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class OfflineSyncEngine(
    private val database: WrenchDatabase,
    private val remoteDataSource: ExpenseRemoteDataSource = ExpenseRemoteDataSource(),
    private val mapper: ExpenseMapper = ExpenseMapper()
) {

    companion object {
        // Guard sync execution process-wide so worker and UI-triggered sync cannot overlap.
        private val syncMutex = Mutex()
    }

    suspend fun syncServer(serverUrl: String, apiKey: String): ApiResult<Unit> {
        return syncMutex.withLock {
            val vehicleIds = mutableSetOf<Int>()

            val remoteVehiclesResult = refreshVehiclesFromRemote(serverUrl, apiKey)
            when (remoteVehiclesResult) {
                is ApiResult.Success -> {
                    vehicleIds += remoteVehiclesResult.data.map { it.id }
                }
                is ApiResult.Error -> {
                    val localVehicles = database.vehicleDao().getVehicles(serverUrl)
                    if (localVehicles.isEmpty()) {
                        return@withLock ApiResult.Error(remoteVehiclesResult.message)
                    }
                    vehicleIds += localVehicles.map { it.remoteId }
                }
            }

            val pendingOps = database.pendingSyncOpDao().getPendingOperations(serverUrl)
            vehicleIds += pendingOps.map { it.vehicleRemoteId }

            for (vehicleId in vehicleIds) {
                val vehicleSyncResult = syncVehicle(serverUrl, apiKey, vehicleId)
                if (vehicleSyncResult is ApiResult.Error) {
                    return@withLock vehicleSyncResult
                }
            }

            ApiResult.Success(Unit)
        }
    }

    private suspend fun refreshVehiclesFromRemote(serverUrl: String, apiKey: String): ApiResult<List<Vehicle>> {
        return try {
            val response = NetworkModule.getApi(serverUrl).getVehicles(apiKey)
            if (!response.isSuccessful) {
                ApiResult.Error("Failed to fetch vehicles: ${response.code()}")
            } else {
                val vehicles = response.body().orEmpty()
                val now = System.currentTimeMillis()
                val entities = vehicles.map { it.toEntity(serverUrl, now) }
                database.withTransaction {
                    database.vehicleDao().upsertVehicles(entities)
                }
                ApiResult.Success(vehicles)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Failed to fetch vehicles")
        }
    }

    private suspend fun syncVehicle(serverUrl: String, apiKey: String, vehicleRemoteId: Int): ApiResult<Unit> {
        val pendingDao = database.pendingSyncOpDao()
        val expenseDao = database.expenseDao()
        val conflictDao = database.syncConflictDao()
        val now = System.currentTimeMillis()

        var remoteSnapshots = fetchRemoteSnapshots(serverUrl, apiKey, vehicleRemoteId)
            .let {
                when (it) {
                    is ApiResult.Success -> it.data
                    is ApiResult.Error -> {
                        if (pendingDao.getPendingOperations(serverUrl).none { op -> op.vehicleRemoteId == vehicleRemoteId }) {
                            emptyList()
                        } else {
                            return ApiResult.Error(it.message)
                        }
                    }
                }
            }

        val remoteByKey = remoteSnapshots.associateBy { it.type.name to it.remoteId }
        val pendingOps = pendingDao.getPendingOperations(serverUrl)
            .filter { it.vehicleRemoteId == vehicleRemoteId }

        for (op in pendingOps) {
            val entity = expenseDao.getByLocalId(op.expenseLocalId)
            val payload = parseExpenseOperationPayload(op.payloadJson)

            when (SyncOperationType.valueOf(op.opType)) {
                SyncOperationType.CREATE -> {
                    if (entity == null || entity.isDeletedLocal) {
                        pendingDao.deleteById(op.opId)
                        continue
                    }

                    val createResult = addRemoteExpense(serverUrl, apiKey, entity)
                    if (createResult is ApiResult.Error) {
                        return handleSyncFailure(op, createResult.message)
                    }

                    database.withTransaction {
                        pendingDao.deleteById(op.opId)
                        expenseDao.updateExpense(
                            entity.copy(
                                syncState = ExpenseSyncState.SYNCED.name,
                                lastSyncError = null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                SyncOperationType.UPDATE -> {
                    if (entity == null || entity.isDeletedLocal) {
                        pendingDao.deleteById(op.opId)
                        continue
                    }

                    val baseTypeName = payload.baseType ?: entity.type
                    val baseType = runCatching { ExpenseType.valueOf(baseTypeName) }.getOrNull()
                    val baseRemoteId = payload.baseRemoteId ?: entity.remoteId

                    if (baseType == null || baseRemoteId == null) {
                        pendingDao.updateOperation(
                            op.copy(
                                opType = SyncOperationType.CREATE.name,
                                payloadJson = null,
                                baseFingerprint = null,
                                lastError = null
                            )
                        )
                        continue
                    }

                    val remoteSnapshot = remoteByKey[baseType.name to baseRemoteId]
                    if (remoteSnapshot == null) {
                        registerConflict(
                            serverUrl = serverUrl,
                            vehicleRemoteId = vehicleRemoteId,
                            expense = entity,
                            remoteSnapshot = null,
                            reason = "Record changed on server before sync"
                        )
                        pendingDao.deleteById(op.opId)
                        continue
                    }

                    if (!payload.baseFingerprint.isNullOrBlank() &&
                        remoteSnapshot.computeFingerprint() != payload.baseFingerprint
                    ) {
                        registerConflict(
                            serverUrl = serverUrl,
                            vehicleRemoteId = vehicleRemoteId,
                            expense = entity,
                            remoteSnapshot = remoteSnapshot,
                            reason = "Record changed on server before sync"
                        )
                        pendingDao.deleteById(op.opId)
                        continue
                    }

                    val deleteResult = remoteDataSource.deleteExpense(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        expenseId = baseRemoteId,
                        type = baseType
                    )
                    if (deleteResult is ApiResult.Error && !isNotFound(deleteResult.message)) {
                        return handleSyncFailure(op, deleteResult.message)
                    }

                    val createOp = op.copy(
                        opType = SyncOperationType.CREATE.name,
                        payloadJson = null,
                        baseFingerprint = null,
                        lastError = null
                    )
                    pendingDao.updateOperation(createOp)

                    val addResult = addRemoteExpense(serverUrl, apiKey, entity)
                    if (addResult is ApiResult.Error) {
                        return handleSyncFailure(createOp, addResult.message)
                    }

                    database.withTransaction {
                        pendingDao.deleteById(op.opId)
                        expenseDao.updateExpense(
                            entity.copy(
                                remoteId = null,
                                syncState = ExpenseSyncState.SYNCED.name,
                                lastSyncError = null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                SyncOperationType.DELETE -> {
                    if (entity == null) {
                        pendingDao.deleteById(op.opId)
                        continue
                    }

                    val baseTypeName = payload.baseType ?: entity.type
                    val baseType = runCatching { ExpenseType.valueOf(baseTypeName) }.getOrNull()
                    val baseRemoteId = payload.baseRemoteId ?: entity.remoteId

                    if (baseType == null || baseRemoteId == null) {
                        database.withTransaction {
                            pendingDao.deleteById(op.opId)
                            expenseDao.deleteByLocalId(entity.localId)
                        }
                        continue
                    }

                    val remoteSnapshot = remoteByKey[baseType.name to baseRemoteId]
                    if (
                        remoteSnapshot != null &&
                        !payload.baseFingerprint.isNullOrBlank() &&
                        remoteSnapshot.computeFingerprint() != payload.baseFingerprint
                    ) {
                        registerConflict(
                            serverUrl = serverUrl,
                            vehicleRemoteId = vehicleRemoteId,
                            expense = entity,
                            remoteSnapshot = remoteSnapshot,
                            reason = "Record changed on server before delete sync"
                        )
                        pendingDao.deleteById(op.opId)
                        continue
                    }

                    val deleteResult = remoteDataSource.deleteExpense(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        expenseId = baseRemoteId,
                        type = baseType
                    )
                    if (deleteResult is ApiResult.Error && !isNotFound(deleteResult.message)) {
                        return handleSyncFailure(op, deleteResult.message)
                    }

                    database.withTransaction {
                        pendingDao.deleteById(op.opId)
                        expenseDao.deleteByLocalId(entity.localId)
                    }
                }
            }
        }

        remoteSnapshots = when (val remoteResult = fetchRemoteSnapshots(serverUrl, apiKey, vehicleRemoteId)) {
            is ApiResult.Success -> remoteResult.data
            is ApiResult.Error -> return ApiResult.Error(remoteResult.message)
        }

        val activePending = pendingDao.getPendingOperations(serverUrl)
            .filter { it.vehicleRemoteId == vehicleRemoteId }
            .associateBy { it.expenseLocalId }

        val localExpenses = expenseDao.getVisibleExpenses(serverUrl, vehicleRemoteId)
        val nowForReconcile = System.currentTimeMillis()
        val consumedRemoteKeys = mutableSetOf<Pair<String, Int>>()

        for (local in localExpenses) {
            val state = runCatching { ExpenseSyncState.valueOf(local.syncState) }
                .getOrDefault(ExpenseSyncState.SYNCED)
            if (state == ExpenseSyncState.CONFLICT || local.isDeletedLocal || activePending.containsKey(local.localId)) {
                continue
            }

            val remoteId = local.remoteId
            if (remoteId != null) {
                val key = local.type to remoteId
                val remote = remoteSnapshots.firstOrNull { it.type.name == key.first && it.remoteId == key.second }
                if (remote == null) {
                    expenseDao.deleteByLocalId(local.localId)
                    continue
                }

                consumedRemoteKeys += key
                expenseDao.updateExpense(local.mergeFromRemote(remote, nowForReconcile))
            }
        }

        for (remote in remoteSnapshots) {
            val key = remote.type.name to remote.remoteId
            if (consumedRemoteKeys.contains(key)) continue

            val existingByRemote = expenseDao.findByRemoteId(
                serverUrl = serverUrl,
                vehicleRemoteId = vehicleRemoteId,
                type = remote.type.name,
                remoteId = remote.remoteId
            )

            if (existingByRemote != null) {
                expenseDao.updateExpense(existingByRemote.mergeFromRemote(remote, nowForReconcile))
                consumedRemoteKeys += key
                continue
            }

            val fingerprint = remote.computeFingerprint()
            val candidate = expenseDao.findBySyncStates(
                serverUrl = serverUrl,
                vehicleRemoteId = vehicleRemoteId,
                syncStates = listOf(ExpenseSyncState.SYNCED.name)
            ).firstOrNull {
                it.remoteId == null &&
                    !it.isDeletedLocal &&
                    it.computeFingerprint() == fingerprint &&
                    !activePending.containsKey(it.localId)
            }

            if (candidate != null) {
                expenseDao.updateExpense(candidate.mergeFromRemote(remote, nowForReconcile))
                consumedRemoteKeys += key
                continue
            }

            expenseDao.insertExpense(remote.toEntity(serverUrl, vehicleRemoteId, nowForReconcile))
        }

        database.syncMetaDao().upsertMeta(
            SyncMetaEntity(
                serverUrl = serverUrl,
                vehicleRemoteId = vehicleRemoteId,
                lastFullSyncAt = now
            )
        )

        val unresolvedConflictIds = conflictDao.getUnresolvedConflicts(serverUrl)
            .map { it.expenseLocalId }
            .toSet()

        val syncedCandidates = expenseDao.getVisibleExpenses(serverUrl, vehicleRemoteId)
            .filter { it.localId !in unresolvedConflictIds }

        for (item in syncedCandidates) {
            val state = runCatching { ExpenseSyncState.valueOf(item.syncState) }
                .getOrDefault(ExpenseSyncState.SYNCED)
            if (state == ExpenseSyncState.SYNC_ERROR) {
                expenseDao.updateExpense(item.copy(syncState = ExpenseSyncState.SYNCED.name, lastSyncError = null))
            }
        }

        return ApiResult.Success(Unit)
    }

    private suspend fun handleSyncFailure(op: PendingSyncOpEntity, message: String): ApiResult.Error {
        database.withTransaction {
            database.pendingSyncOpDao().updateOperation(
                op.copy(
                    attemptCount = op.attemptCount + 1,
                    lastError = message
                )
            )
            val expense = database.expenseDao().getByLocalId(op.expenseLocalId)
            if (expense != null) {
                database.expenseDao().updateExpense(
                    expense.copy(
                        syncState = ExpenseSyncState.SYNC_ERROR.name,
                        lastSyncError = message,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        return ApiResult.Error(message)
    }

    private suspend fun registerConflict(
        serverUrl: String,
        vehicleRemoteId: Int,
        expense: ExpenseEntity,
        remoteSnapshot: RemoteExpenseSnapshot?,
        reason: String
    ) {
        database.withTransaction {
            database.syncConflictDao().insertConflict(
                SyncConflictEntity(
                    serverUrl = serverUrl,
                    vehicleRemoteId = vehicleRemoteId,
                    expenseLocalId = expense.localId,
                    remoteSnapshotJson = remoteSnapshot?.snapshotToJson() ?: "",
                    localSnapshotJson = expense.entityToJson(),
                    reason = reason,
                    createdAt = System.currentTimeMillis(),
                    resolvedAt = null
                )
            )
            database.expenseDao().updateExpense(
                expense.copy(
                    syncState = ExpenseSyncState.CONFLICT.name,
                    lastSyncError = reason,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun fetchRemoteSnapshots(
        serverUrl: String,
        apiKey: String,
        vehicleRemoteId: Int
    ): ApiResult<List<RemoteExpenseSnapshot>> {
        return when (val result = remoteDataSource.fetchAllExpenseRecords(serverUrl, apiKey, vehicleRemoteId)) {
            is ApiResult.Error -> ApiResult.Error(result.message)
            is ApiResult.Success -> ApiResult.Success(result.data.toSnapshots(vehicleRemoteId, mapper))
        }
    }

    private suspend fun addRemoteExpense(serverUrl: String, apiKey: String, entity: ExpenseEntity): ApiResult<Unit> {
        val date = runCatching { LocalDate.parse(entity.date) }.getOrElse { LocalDate.now() }
        val type = runCatching { ExpenseType.valueOf(entity.type) }.getOrDefault(ExpenseType.SERVICE)

        return remoteDataSource.addExpense(
            serverUrl = serverUrl,
            apiKey = apiKey,
            vehicleId = entity.vehicleRemoteId,
            type = type,
            date = date.toString(),
            odometer = entity.odometer?.toString().orEmpty(),
            description = if (type == ExpenseType.FUEL) "Fuel" else entity.description,
            cost = String.format(java.util.Locale.US, "%.2f", entity.cost),
            notes = entity.notes.orEmpty(),
            fuelConsumed = entity.liters?.let { String.format(java.util.Locale.US, "%.2f", it) }.orEmpty(),
            isFillToFull = (entity.isFillToFull ?: true).toString(),
            missedFuelUp = (entity.isMissedFuelUp ?: false).toString(),
            isRecurring = (entity.isRecurring ?: false).toString()
        )
    }

    private fun isNotFound(message: String): Boolean {
        return message.contains("404") || message.contains("Not Found", ignoreCase = true)
    }

    private fun ExpenseEntity.mergeFromRemote(remote: RemoteExpenseSnapshot, now: Long): ExpenseEntity {
        return copy(
            remoteId = remote.remoteId,
            type = remote.type.name,
            date = remote.date,
            cost = remote.cost,
            odometer = remote.odometer,
            description = remote.description,
            notes = remote.notes,
            liters = remote.liters,
            isFillToFull = remote.isFillToFull,
            isMissedFuelUp = remote.isMissedFuelUp,
            isRecurring = remote.isRecurring,
            syncState = ExpenseSyncState.SYNCED.name,
            isDeletedLocal = false,
            lastSyncedFingerprint = remote.computeFingerprint(),
            lastSyncError = null,
            updatedAt = now
        )
    }

    private fun RemoteExpenseSnapshot.toEntity(serverUrl: String, vehicleRemoteId: Int, now: Long): ExpenseEntity {
        return ExpenseEntity(
            serverUrl = serverUrl,
            vehicleRemoteId = vehicleRemoteId,
            remoteId = remoteId,
            type = type.name,
            date = date,
            cost = cost,
            odometer = odometer,
            description = description,
            notes = notes,
            liters = liters,
            fuelEconomy = null,
            isFillToFull = isFillToFull,
            isMissedFuelUp = isMissedFuelUp,
            isRecurring = isRecurring,
            syncState = ExpenseSyncState.SYNCED.name,
            isDeletedLocal = false,
            updatedAt = now,
            lastSyncedFingerprint = computeFingerprint(),
            lastSyncError = null
        )
    }
}

private fun ExpenseRecordBundle.toSnapshots(
    vehicleRemoteId: Int,
    mapper: ExpenseMapper
): List<RemoteExpenseSnapshot> {
    val snapshots = mutableListOf<RemoteExpenseSnapshot>()

    serviceRecords.forEach { record ->
        val expense = mapper.mapServiceRecord(record)
        snapshots += RemoteExpenseSnapshot(
            vehicleRemoteId = vehicleRemoteId,
            remoteId = record.id.toIntOrNull() ?: return@forEach,
            type = ExpenseType.SERVICE,
            date = expense.date.toString(),
            cost = expense.cost,
            odometer = expense.odometer,
            description = expense.description,
            notes = expense.notes,
            liters = null,
            isFillToFull = null,
            isMissedFuelUp = null,
            isRecurring = null
        )
    }

    repairRecords.forEach { record ->
        val expense = mapper.mapRepairRecord(record)
        snapshots += RemoteExpenseSnapshot(
            vehicleRemoteId = vehicleRemoteId,
            remoteId = record.id.toIntOrNull() ?: return@forEach,
            type = ExpenseType.REPAIR,
            date = expense.date.toString(),
            cost = expense.cost,
            odometer = expense.odometer,
            description = expense.description,
            notes = expense.notes,
            liters = null,
            isFillToFull = null,
            isMissedFuelUp = null,
            isRecurring = null
        )
    }

    upgradeRecords.forEach { record ->
        val expense = mapper.mapUpgradeRecord(record)
        snapshots += RemoteExpenseSnapshot(
            vehicleRemoteId = vehicleRemoteId,
            remoteId = record.id.toIntOrNull() ?: return@forEach,
            type = ExpenseType.UPGRADE,
            date = expense.date.toString(),
            cost = expense.cost,
            odometer = expense.odometer,
            description = expense.description,
            notes = expense.notes,
            liters = null,
            isFillToFull = null,
            isMissedFuelUp = null,
            isRecurring = null
        )
    }

    fuelRecords.forEach { record ->
        val expense = mapper.mapFuelRecord(record, fuelEconomy = null)
        snapshots += RemoteExpenseSnapshot(
            vehicleRemoteId = vehicleRemoteId,
            remoteId = record.id.toIntOrNull() ?: return@forEach,
            type = ExpenseType.FUEL,
            date = expense.date.toString(),
            cost = expense.cost,
            odometer = expense.odometer,
            description = expense.description,
            notes = expense.notes,
            liters = expense.liters,
            isFillToFull = record.isFillToFull.toBooleanStrictOrNullLenient(),
            isMissedFuelUp = null,
            isRecurring = null
        )
    }

    taxRecords.forEach { record ->
        val expense = mapper.mapTaxRecord(record)
        snapshots += RemoteExpenseSnapshot(
            vehicleRemoteId = vehicleRemoteId,
            remoteId = record.id.toIntOrNull() ?: return@forEach,
            type = ExpenseType.TAX,
            date = expense.date.toString(),
            cost = expense.cost,
            odometer = null,
            description = expense.description,
            notes = expense.notes,
            liters = null,
            isFillToFull = null,
            isMissedFuelUp = null,
            isRecurring = record.isRecurring.toBooleanStrictOrNullLenient()
        )
    }

    return snapshots
}

private fun String?.toBooleanStrictOrNullLenient(): Boolean? {
    return when (this?.trim()?.lowercase()) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> null
    }
}
