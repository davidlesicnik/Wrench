package com.lesicnik.wrench.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity
import com.lesicnik.wrench.data.local.entity.ServiceUploadEntity
import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.data.remote.records.ServiceRecord
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.Credentials
import com.lesicnik.wrench.data.repository.ReminderRepository
import com.lesicnik.wrench.data.repository.expense.ExpenseRecordBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineSyncEngineTest {
    private lateinit var database: WrenchDatabase
    private val credentials = Credentials("https://test.example", "test-key")
    private val vehicle = Vehicle(1, null, 2020, "Test", "Car", null, null, false, false, "km")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WrenchDatabase::class.java).build()
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun `sync and record conversion leave the caller thread and read only sync downloads once`() = runBlocking {
        val callerThread = Thread.currentThread()
        var downloads = 0
        var converted = 0
        val records = object : AbstractList<ServiceRecord>() {
            override val size = 200
            override fun get(index: Int): ServiceRecord {
                assertNotSame("Record conversion must not block the UI caller", callerThread, Thread.currentThread())
                converted++
                return record(index + 1)
            }
        }
        val engine = OfflineSyncEngine(database, loadVehicles = { _, _ ->
            assertNotSame(callerThread, Thread.currentThread())
            Response.success(listOf(vehicle))
        }, loadExpenseRecords = { _, _, _ ->
            downloads++
            ApiResult.Success(bundle(records))
        })

        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        assertEquals(1, downloads)
        assertEquals(200, converted)
        assertEquals(200, database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1).size)
    }

    @Test
    fun `reconciliation preserves local identity updates removes and links records`() = runBlocking {
        val existingId = database.expenseDao().insertExpense(expense(1))
        val removedId = database.expenseDao().insertExpense(expense(99))
        val unlinkedId = database.expenseDao().insertExpense(expense(null).copy(description = "Service 2"))
        val ownedId = database.expenseDao().insertExpense(expense(4).copy(accountKey = "other-account"))
        val engine = engine { bundle(listOf(record(1).copy(cost = "50"), record(2), record(3), record(4))) }

        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        assertEquals(50.0, database.expenseDao().getByLocalId(existingId.toInt())!!.cost, 0.0)
        assertNull(database.expenseDao().getByLocalId(removedId.toInt()))
        assertEquals(2, database.expenseDao().getByLocalId(unlinkedId.toInt())!!.remoteId)
        assertEquals("other-account", database.expenseDao().getByLocalId(ownedId.toInt())!!.accountKey)
        val rows = database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1)
        assertEquals(5, rows.size)
        assertTrue(rows.any { it.remoteId == 4 && it.accountKey == null })
        assertNotNull(database.syncMetaDao().getMeta(credentials.serverUrl, 1))
    }

    @Test
    fun `confirmed linked services follow server edits and deletions without removing receipts`() = runBlocking {
        val key = ReminderRepository.accountKey(credentials)
        val localId = database.expenseDao().insertExpense(expense(1).copy(accountKey = key)).toInt()
        val receipt = ServiceUploadEntity(localId, key, credentials.serverUrl, 1, 7, "CONFIRMED", 1)
        database.maintenanceDao().save(receipt)
        var records = listOf(record(1).copy(cost = "75", notes = "Updated on server"))
        val engine = engine { bundle(records) }

        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        val updated = database.expenseDao().getByLocalId(localId)!!
        assertEquals(75.0, updated.cost, 0.0)
        assertEquals("Updated on server", updated.notes)
        assertEquals(key, updated.accountKey)
        assertEquals(receipt, database.maintenanceDao().upload(localId))
        records = emptyList()
        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        assertNull(database.expenseDao().getByLocalId(localId))
        assertEquals(receipt, database.maintenanceDao().upload(localId))
    }

    @Test
    fun `another identity receives a visible server copy without changing the original account row`() = runBlocking {
        val key = ReminderRepository.accountKey(credentials)
        val localId = database.expenseDao().insertExpense(expense(1).copy(accountKey = key)).toInt()
        val original = database.expenseDao().getByLocalId(localId)
        val other = credentials.copy(apiKey = "other-account")
        val engine = engine { bundle(listOf(record(1).copy(cost = "75"))) }
        repeat(2) { assertEquals(ApiResult.Success(Unit), engine.syncServer(other.serverUrl, other.apiKey)) }

        assertEquals(original, database.expenseDao().getByLocalId(localId))
        val shared = database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1).filter { it.accountKey == null }
        assertEquals(1, shared.size)
        assertEquals(1, shared.single().remoteId)
        assertEquals(75.0, shared.single().cost, 0.0)
    }

    @Test
    fun `uncertain service uploads are preserved during snapshot reconciliation`() = runBlocking {
        val key = ReminderRepository.accountKey(credentials)
        val localId = database.expenseDao().insertExpense(expense(1).copy(accountKey = key, syncState = "SYNC_ERROR")).toInt()
        val before = database.expenseDao().getByLocalId(localId)
        val receipt = ServiceUploadEntity(localId, key, credentials.serverUrl, 1, 7, "NEEDS_REVIEW", 1)
        database.maintenanceDao().save(receipt)
        val engine = engine { bundle(listOf(record(1).copy(cost = "75"))) }

        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        assertEquals(before, database.expenseDao().getByLocalId(localId))
        assertEquals(receipt, database.maintenanceDao().upload(localId))
    }

    @Test
    fun `owned synced ordinary expenses can link to their remote records`() = runBlocking {
        val key = ReminderRepository.accountKey(credentials)
        val localId = database.expenseDao().insertExpense(expense(null).copy(accountKey = key, description = "Service 1")).toInt()
        val engine = engine { bundle(listOf(record(1))) }

        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        assertEquals(1, database.expenseDao().getByLocalId(localId)?.remoteId)
        assertEquals(1, database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1).size)
    }

    @Test
    fun `sync restores preexisting expense ownership from another account pending operation`() = runBlocking {
        val owner = ReminderRepository.accountKey(credentials.copy(apiKey = "other-account"))
        val localId = database.expenseDao().insertExpense(expense(null).copy(syncState = "PENDING_CREATE")).toInt()
        database.pendingSyncOpDao().insertOperation(PendingSyncOpEntity(
            serverUrl = credentials.serverUrl, vehicleRemoteId = 1, expenseLocalId = localId,
            opType = "CREATE", payloadJson = null, baseFingerprint = null,
            createdAt = 1, attemptCount = 0, lastError = null, accountKey = owner))
        val engine = engine { bundle(emptyList()) }

        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        assertEquals(owner, database.expenseDao().getByLocalId(localId)?.accountKey)
        assertEquals(owner, database.pendingSyncOpDao().getPendingOperations(credentials.serverUrl).single().accountKey)
    }

    @Test
    fun `failed reconciliation rolls back all rows and sync timestamp`() = runBlocking {
        database.expenseDao().insertExpenses(listOf(expense(1), expense(99)))
        val before = database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1)
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_test_record BEFORE INSERT ON expenses
            WHEN NEW.description = 'Service 2'
            BEGIN SELECT RAISE(ABORT, 'Simulated disk failure'); END
        """.trimIndent())
        val engine = engine { bundle(listOf(record(1).copy(cost = "50"), record(2))) }

        try {
            engine.syncServer(credentials.serverUrl, credentials.apiKey)
            fail("Expected the injected database failure")
        } catch (expected: android.database.sqlite.SQLiteException) {
            assertTrue(expected.message.orEmpty().contains("Simulated disk failure"))
        }
        assertEquals(before, database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1))
        assertNull(database.syncMetaDao().getMeta(credentials.serverUrl, 1))
    }

    @Test
    fun `queued operations still cause a fresh snapshot after processing`() = runBlocking {
        val localId = database.expenseDao().insertExpense(expense(null).copy(isDeletedLocal = true)).toInt()
        database.pendingSyncOpDao().insertOperation(PendingSyncOpEntity(
            serverUrl = credentials.serverUrl, vehicleRemoteId = 1, expenseLocalId = localId,
            opType = SyncOperationType.DELETE.name, payloadJson = null, baseFingerprint = null,
            createdAt = 1, attemptCount = 0, lastError = null,
            accountKey = ReminderRepository.accountKey(credentials)
        ))
        var downloads = 0
        val engine = engine { downloads++; bundle(listOf(record(downloads))) }

        assertEquals(ApiResult.Success(Unit), engine.syncServer(credentials.serverUrl, credentials.apiKey))
        assertEquals(2, downloads)
        assertTrue(database.pendingSyncOpDao().getPendingOperations(credentials.serverUrl).isEmpty())
        assertEquals(2, database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1).single().remoteId)
    }

    @Test
    fun `cancelling vehicle refresh propagates cancellation`() = runBlocking {
        val engine = OfflineSyncEngine(database, loadVehicles = { _, _ -> throw CancellationException("Cancelled") })
        try {
            engine.syncServer(credentials.serverUrl, credentials.apiKey)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    private fun engine(load: () -> ExpenseRecordBundle) = OfflineSyncEngine(database,
        loadVehicles = { _, _ -> Response.success(listOf(vehicle)) },
        loadExpenseRecords = { _, _, _ -> ApiResult.Success(load()) })

    private fun record(id: Int) = ServiceRecord(id.toString(), "2026-11-06", "10000", "Service $id", "45", null)

    private fun bundle(records: List<ServiceRecord>) = ExpenseRecordBundle(records, emptyList(), emptyList(), emptyList(), emptyList())

    private fun expense(remoteId: Int?) = ExpenseEntity(
        serverUrl = credentials.serverUrl, vehicleRemoteId = 1, remoteId = remoteId,
        type = "SERVICE", date = "2026-11-06", cost = 45.0, odometer = 10000,
        description = "Service $remoteId", notes = null, liters = null, fuelEconomy = null,
        isFillToFull = null, isMissedFuelUp = null, isRecurring = null, syncState = "SYNCED",
        isDeletedLocal = false, updatedAt = 1, lastSyncedFingerprint = null, lastSyncError = null
    )
}
