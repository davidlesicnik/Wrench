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
import com.lesicnik.wrench.data.sync.MaintenanceCoordinator
import com.lesicnik.wrench.data.sync.OfflineSyncEngine
import com.lesicnik.wrench.data.sync.ReminderValues
import com.lesicnik.wrench.data.sync.SyncOperationType
import com.lesicnik.wrench.data.sync.SyncWorkScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ExpenseSyncSummary(
    val lastSyncedAt: Long? = null,
    val pendingCount: Int = 0,
    val problemCount: Int = 0,
    val refreshError: String? = null,
    val unownedCount: Int = 0
)

class ExpenseRepository(
    private val appContext: Context,
    private val database: WrenchDatabase = WrenchDatabase.getInstance(appContext),
    private val cache: ExpenseCache = ExpenseCache(),
    private val mapper: ExpenseMapper = ExpenseMapper(),
    private val fuelStatsCalculator: FuelStatsCalculator = FuelStatsCalculator(),
    private val syncEngine: OfflineSyncEngine = OfflineSyncEngine(database),
    credentialsStore: CredentialsStore? = null
) {

    val maintenance = MaintenanceCoordinator(database)
    private val credentials by lazy { credentialsStore ?: CredentialsRepository(appContext) }

    private val refreshErrors = MutableStateFlow<Map<Int, Pair<Long, String>>>(emptyMap())
    private val sessionRefreshes = ConcurrentHashMap<String, CompletableDeferred<ApiResult<Unit>>>()
    private val _sessionDataReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionDataReady: SharedFlow<Unit> = _sessionDataReady.asSharedFlow()

    /** Warm Room-backed caches immediately, then refresh the server once for the session. */
    suspend fun preloadSessionData(serverUrl: String, apiKey: String, vehicleIds: List<Int>) {
        val refresh = CompletableDeferred<ApiResult<Unit>>()
        val existing = sessionRefreshes.putIfAbsent(serverUrl, refresh)
        if (existing != null) {
            existing.await()
            return
        }
        try {
            val result = withContext(Dispatchers.IO) {
                for (vehicleId in vehicleIds) warmVehicleCache(serverUrl, vehicleId, includeEmpty = false)
                _sessionDataReady.emit(Unit)
                val synced = syncEngine.syncServer(serverUrl, apiKey, preferredVehicleIds = vehicleIds)
                currentCoroutineContext().ensureActive()
                for (vehicleId in vehicleIds) {
                    warmVehicleCache(serverUrl, vehicleId, includeEmpty = synced is ApiResult.Success)
                }
                refreshErrors.update { errors ->
                    when (synced) {
                        is ApiResult.Success -> errors - vehicleIds.toSet()
                        is ApiResult.Error -> errors + vehicleIds.associateWith { System.currentTimeMillis() to synced.message }
                    }
                }
                synced
            }
            refresh.complete(result)
            _sessionDataReady.emit(Unit)
        } catch (cancelled: CancellationException) {
            refresh.complete(ApiResult.Error("Background loading was interrupted"))
            throw cancelled
        } catch (error: Exception) {
            refresh.complete(ApiResult.Error(error.message ?: "Background loading failed"))
        } finally {
            sessionRefreshes.remove(serverUrl, refresh)
        }
    }

    private suspend fun warmVehicleCache(serverUrl: String, vehicleId: Int, includeEmpty: Boolean) {
        val expenses = readLocalExpenses(serverUrl, vehicleId)
        if (expenses.isNotEmpty() || includeEmpty || database.syncMetaDao().getMeta(serverUrl, vehicleId) != null) {
            val statistics = computeFuelStatistics(expenses)
            currentCoroutineContext().ensureActive()
            cache.putExpenses(vehicleId, expenses)
            cache.putFuelStatistics(vehicleId, statistics)
        }
    }

    fun observeExpenses(serverUrl: String, vehicleId: Int): Flow<List<Expense>> =
        database.expenseDao().observeVisibleExpenses(serverUrl, vehicleId)
            .distinctUntilChanged()
            .map { entities ->
                mapLocalExpenses(serverUrl, entities).also { expenses -> cache.putExpenses(vehicleId, expenses) }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeSyncSummary(serverUrl: String, vehicleId: Int): Flow<ExpenseSyncSummary> = combine(
        database.syncMetaDao().observeMeta(serverUrl, vehicleId),
        database.pendingSyncOpDao().observePending(serverUrl, vehicleId),
        database.expenseDao().observeVisibleExpenses(serverUrl, vehicleId),
        refreshErrors
    ) { meta, pending, expenses, errors ->
        val failure = errors[vehicleId]
        val credentials = credentials.getCredentials()
        val key = credentials?.takeIf { it.serverUrl == serverUrl }?.let(ReminderRepository::accountKey)
        ExpenseSyncSummary(
            lastSyncedAt = meta?.lastFullSyncAt,
            pendingCount = pending.count { it.accountKey == key },
            unownedCount = pending.count { it.accountKey == null },
            problemCount = expenses.count { expense ->
                val owner = expense.accountKey ?: pending.firstOrNull { it.expenseLocalId == expense.localId }?.accountKey
                (owner == null || owner == key) &&
                    (expense.syncState == ExpenseSyncState.CONFLICT.name || expense.syncState == ExpenseSyncState.SYNC_ERROR.name)
            },
            refreshError = failure?.takeIf { it.first > (meta?.lastFullSyncAt ?: 0L) }?.second
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun getCachedExpenses(vehicleId: Int): List<Expense>? = cache.getExpenses(vehicleId)

    fun getCachedFuelStatistics(vehicleId: Int): FuelStatistics? = cache.getFuelStatistics(vehicleId)

    fun getExpenseById(vehicleId: Int, expenseId: Int, type: ExpenseType): Expense? {
        return cache.getExpenses(vehicleId)?.find { it.id == expenseId && it.type == type }
    }

    fun invalidateCache(vehicleId: Int) {
        cache.invalidate(vehicleId)
    }

    suspend fun claimLegacyOperations(serverUrl: String, apiKey: String, vehicleId: Int) {
        MaintenanceCoordinator.writeMutex.withLock {
            database.withTransaction {
                val key = ReminderRepository.accountKey(Credentials(serverUrl, apiKey))
                database.pendingSyncOpDao().getPendingOperations(serverUrl)
                    .filter { it.vehicleRemoteId == vehicleId && it.accountKey == null }
                    .forEach { op ->
                        val expense = database.expenseDao().getByLocalId(op.expenseLocalId)
                        if (expense != null && (expense.accountKey == null || expense.accountKey == key)) {
                            database.expenseDao().updateExpense(expense.copy(accountKey = key))
                            database.pendingSyncOpDao().updateOperation(op.copy(accountKey = key))
                        }
                    }
            }
        }
    }

    suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
        cache.clearAll()
        refreshErrors.value = emptyMap()
        // Queued work and confirmed receipts survive logout. Credentials remain in secure preferences.
    }

    suspend fun getExpenses(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        forceRefresh: Boolean = false
    ): ApiResult<List<Expense>> = withContext(Dispatchers.Default) {
        val localExpenses = readLocalExpenses(serverUrl, vehicleId)
        if (!forceRefresh && (localExpenses.isNotEmpty() || database.syncMetaDao().getMeta(serverUrl, vehicleId) != null)) {
            cache.putExpenses(vehicleId, localExpenses)
            return@withContext ApiResult.Success(localExpenses)
        }

        val syncResult = sessionRefreshes[serverUrl]?.await() ?: syncEngine.syncServer(serverUrl, apiKey)
        refreshErrors.update { errors ->
            when (syncResult) {
                is ApiResult.Success -> errors - vehicleId
                is ApiResult.Error -> errors + (vehicleId to (System.currentTimeMillis() to syncResult.message))
            }
        }
        val refreshedLocalExpenses = readLocalExpenses(serverUrl, vehicleId)

        when (syncResult) {
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

    suspend fun linkedReminder(c: Credentials, vehicleId: Int, reminderId: Int): Reminder? =
        ReminderRepository(database).cached(c, vehicleId)?.reminders?.firstOrNull { it.id == reminderId }

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
        isRecurring: Boolean = false,
        reminderId: Int? = null,
        reminderTarget: ReminderValues? = null,
        removeReminder: Boolean = false,
        originalReminder: Reminder? = null
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
                lastSyncError = null,
                accountKey = ReminderRepository.accountKey(Credentials(serverUrl, apiKey))
            )

            if (reminderId != null) {
                val credentials = Credentials(serverUrl, apiKey)
                val reminder = originalReminder ?: ReminderRepository(database).cached(credentials, vehicleId)?.reminders?.firstOrNull { it.id == reminderId }
                    ?: return ApiResult.Error("Reminder unavailable. Open Maintenance and refresh before recording this service.")
                maintenance.recordService(credentials, reminder, entity, reminderTarget, removeReminder)
            } else database.withTransaction {
                val localId = database.expenseDao().insertExpense(entity).toInt()
                enqueueOperation(
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    vehicleId = vehicleId,
                    localExpenseId = localId,
                    opType = SyncOperationType.CREATE,
                    payload = null
                )
            }

            invalidateCache(vehicleId)
            runCatching { SyncWorkScheduler.enqueueImmediateSync(appContext) }
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
            val account = Credentials(serverUrl, apiKey)
            requireExpenseAccess(entity, account, vehicleId)

            if (database.maintenanceDao().upload(expenseId) != null) {
                if (maintenance.deleteUnsent(Credentials(serverUrl, apiKey), expenseId)) {
                    invalidateCache(vehicleId)
                    return ApiResult.Success(Unit)
                }
            }
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
                            accountKey = ReminderRepository.accountKey(account),
                            isDeletedLocal = true,
                            syncState = ExpenseSyncState.PENDING_DELETE.name,
                            updatedAt = System.currentTimeMillis(),
                            lastSyncError = null
                        )
                    )

                    database.pendingSyncOpDao().deleteForExpense(serverUrl, entity.localId)
                    enqueueOperation(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        vehicleId = vehicleId,
                        localExpenseId = entity.localId,
                        opType = SyncOperationType.DELETE,
                        payload = payload
                    )
                }
            }

            invalidateCache(vehicleId)
            runCatching { SyncWorkScheduler.enqueueImmediateSync(appContext) }
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
            val account = Credentials(serverUrl, apiKey)
            requireExpenseAccess(existing, account, vehicleId)

            val unresolvedConflict = database.syncConflictDao()
                .getUnresolvedConflictForExpense(serverUrl, existing.localId)
            val remoteConflictSnapshot = unresolvedConflict
                ?.remoteSnapshotJson
                ?.takeIf { it.isNotBlank() }
                ?.let(::parseRemoteExpenseSnapshot)

            val updatedEntity = existing.copy(
                accountKey = ReminderRepository.accountKey(account),
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

            if (database.maintenanceDao().upload(originalExpenseId) != null) {
                maintenance.editUnsent(Credentials(serverUrl, apiKey), updatedEntity)
                invalidateCache(vehicleId)
                runCatching { SyncWorkScheduler.enqueueImmediateSync(appContext) }
                return ApiResult.Success(Unit)
            }
            database.withTransaction {
                database.expenseDao().updateExpense(updatedEntity)
                database.pendingSyncOpDao().deleteForExpense(serverUrl, existing.localId)

                if (existing.remoteId == null) {
                    enqueueOperation(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
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
                        apiKey = apiKey,
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
            runCatching { SyncWorkScheduler.enqueueImmediateSync(appContext) }
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

            val account = credentials.getCredentials()?.takeIf { it.serverUrl == serverUrl }
                ?: return ApiResult.Error("Not logged in")
            requireExpenseAccess(entity, account, vehicleId)

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
                        accountKey = ReminderRepository.accountKey(account),
                        remoteId = null,
                        syncState = ExpenseSyncState.PENDING_CREATE.name,
                        isDeletedLocal = false,
                        updatedAt = System.currentTimeMillis(),
                        lastSyncError = null
                    )
                    database.expenseDao().updateExpense(recreated)
                    enqueueOperation(
                        serverUrl = serverUrl,
                        apiKey = account.apiKey,
                        vehicleId = vehicleId,
                        localExpenseId = entity.localId,
                        opType = SyncOperationType.CREATE,
                        payload = null
                    )
                } else {
                    val updated = entity.copy(
                        accountKey = ReminderRepository.accountKey(account),
                        syncState = ExpenseSyncState.PENDING_UPDATE.name,
                        isDeletedLocal = false,
                        updatedAt = System.currentTimeMillis(),
                        lastSyncError = null
                    )
                    database.expenseDao().updateExpense(updated)
                    enqueueOperation(
                        serverUrl = serverUrl,
                        apiKey = account.apiKey,
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
            runCatching { SyncWorkScheduler.enqueueImmediateSync(appContext) }
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

            val account = credentials.getCredentials()?.takeIf { it.serverUrl == serverUrl }
                ?: return ApiResult.Error("Not logged in")
            requireExpenseAccess(entity, account, vehicleId)

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
    ): ApiResult<FuelStatistics> = withContext(Dispatchers.Default) {
        if (!forceRefresh) {
            cache.getFuelStatistics(vehicleId)?.let { cached ->
                return@withContext ApiResult.Success(cached)
            }
        }

        when (val expensesResult = getExpenses(serverUrl, apiKey, vehicleId, forceRefresh = forceRefresh)) {
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
        apiKey: String? = null,
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
                lastError = null,
                accountKey = (apiKey?.let { Credentials(serverUrl, it) } ?: credentials.getCredentials()?.takeIf { it.serverUrl == serverUrl })?.let(ReminderRepository::accountKey)
            )
        )
    }

    private suspend fun readLocalExpenses(serverUrl: String, vehicleId: Int): List<Expense> =
        withContext(Dispatchers.Default) {
            mapLocalExpenses(serverUrl, database.expenseDao().getVisibleExpenses(serverUrl, vehicleId))
        }

    private suspend fun requireExpenseAccess(expense: ExpenseEntity, account: Credentials, vehicleId: Int) {
        val key = ReminderRepository.accountKey(account)
        require(expense.serverUrl == account.serverUrl && expense.vehicleRemoteId == vehicleId) { "Expense not found" }
        require(expense.accountKey == null || expense.accountKey == key) { "This expense belongs to another account." }
        val pending = database.pendingSyncOpDao().getOperationsForExpense(account.serverUrl, expense.localId)
        require(pending.all { it.accountKey == key }) { "Assign unowned changes to this account before editing, or sign in with the account that queued them." }
    }

    private suspend fun mapLocalExpenses(serverUrl: String, localExpenses: List<ExpenseEntity>): List<Expense> {
        val credentials = credentials.getCredentials()
        val key = credentials?.takeIf { it.serverUrl == serverUrl }?.let(ReminderRepository::accountKey)
        val pendingOwners = database.pendingSyncOpDao().getPendingOperations(serverUrl).associate { it.expenseLocalId to it.accountKey }
        val visible = localExpenses.filter {
            val owner = it.accountKey ?: pendingOwners[it.localId]
            owner == null || owner == key
        }
        val linkedKeys = visible.filter { it.accountKey != null && it.remoteId != null }.map { it.type to it.remoteId }.toSet()
        val entities = visible.filter { it.accountKey != null || (it.type to it.remoteId) !in linkedKeys }
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
                    missedFuelUp = entity.isMissedFuelUp?.toString(),
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
                    isFillToFull = expense.isFillToFull?.toString(),
                    missedFuelUp = expense.isMissedFuelUp?.toString(),
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
