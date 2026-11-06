package com.lesicnik.wrench.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity
import com.lesicnik.wrench.data.local.entity.SyncMetaEntity
import com.lesicnik.wrench.data.remote.records.ExpenseType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExpenseRepositoryOfflineTest {
    private lateinit var database: WrenchDatabase
    private lateinit var repository: ExpenseRepository
    private val credentials = Credentials("https://test.example", "test-key")
    private var activeCredentials = credentials

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WrenchDatabase::class.java).build()
        val callerThread = Thread.currentThread()
        val store = object : CredentialsStore {
            override suspend fun getCredentials(): Credentials {
                assertNotSame("Local expense processing must leave the UI caller", callerThread, Thread.currentThread())
                return activeCredentials
            }
            override suspend fun saveCredentials(serverUrl: String, apiKey: String) = Unit
            override suspend fun deleteCredentials() = Unit
        }
        repository = ExpenseRepository(context, database, credentialsStore = store)
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun `local reads statistics and observed snapshots process off the caller thread`() = runBlocking {
        database.expenseDao().insertExpense(expense())

        val expenses = repository.getExpenses(credentials.serverUrl, credentials.apiKey, 1)
        assertTrue(expenses is ApiResult.Success)
        assertEquals("Service", (expenses as ApiResult.Success).data.single().description)
        assertTrue(repository.getFuelStatistics(credentials.serverUrl, credentials.apiKey, 1) is ApiResult.Success)
        withTimeout(5000) {
            assertEquals(expenses.data, repository.observeExpenses(credentials.serverUrl, 1).first())
            assertEquals(ExpenseSyncSummary(), repository.observeSyncSummary(credentials.serverUrl, 1).first())
        }
    }

    @Test
    fun `observed snapshot hides other accounts and deduplicates linked service copies`() = runBlocking {
        val accountKey = ReminderRepository.accountKey(credentials)
        database.expenseDao().insertExpenses(listOf(
            expense(),
            expense().copy(accountKey = accountKey, description = "Account service"),
            expense().copy(remoteId = 2, accountKey = "other-account", description = "Hidden service")
        ))

        val loaded = (repository.getExpenses(credentials.serverUrl, credentials.apiKey, 1) as ApiResult.Success).data
        val observed = withTimeout(5000) { repository.observeExpenses(credentials.serverUrl, 1).first() }
        assertEquals(loaded, observed)
        assertEquals("Account service", observed.single().description)
        assertEquals(observed, repository.getCachedExpenses(1))
    }

    @Test
    fun `ordinary queued expenses retain their owner and reject edits and deletion by another account`() = runBlocking {
        val added = repository.addExpense(credentials.serverUrl, credentials.apiKey, 1, ExpenseType.SERVICE,
            LocalDate.of(2026, 11, 6), 10000, "Service", 45.0, "")
        assertEquals(ApiResult.Success(Unit), added)
        val stored = database.expenseDao().getVisibleExpenses(credentials.serverUrl, 1).single()
        assertEquals(ReminderRepository.accountKey(credentials), stored.accountKey)
        val pending = database.pendingSyncOpDao().getPendingOperations(credentials.serverUrl)
        assertEquals(stored.accountKey, pending.single().accountKey)

        activeCredentials = credentials.copy(apiKey = "another-account")
        database.syncMetaDao().upsertMeta(SyncMetaEntity(credentials.serverUrl, 1, 1))
        assertTrue((repository.getExpenses(credentials.serverUrl, activeCredentials.apiKey, 1) as ApiResult.Success).data.isEmpty())
        assertTrue(withTimeout(5000) { repository.observeExpenses(credentials.serverUrl, 1).first() }.isEmpty())
        assertTrue(update(activeCredentials, stored.localId) is ApiResult.Error)
        assertTrue(repository.deleteExpense(credentials.serverUrl, activeCredentials.apiKey, 1, stored.localId, ExpenseType.SERVICE) is ApiResult.Error)
        assertEquals(stored, database.expenseDao().getByLocalId(stored.localId))
        assertEquals(pending, database.pendingSyncOpDao().getPendingOperations(credentials.serverUrl))

        activeCredentials = credentials
        assertEquals(ApiResult.Success(Unit), update(credentials, stored.localId))
        assertEquals(stored.accountKey, database.expenseDao().getByLocalId(stored.localId)?.accountKey)
        assertEquals(ApiResult.Success(Unit), repository.deleteExpense(credentials.serverUrl, credentials.apiKey, 1, stored.localId, ExpenseType.SERVICE))
        assertNull(database.expenseDao().getByLocalId(stored.localId))
    }

    @Test
    fun `preexisting queued rows use operation ownership for visibility and authorization`() = runBlocking {
        val localId = database.expenseDao().insertExpense(expense().copy(remoteId = null, syncState = "SYNC_ERROR")).toInt()
        val op = pending(localId, ReminderRepository.accountKey(credentials))
        database.pendingSyncOpDao().insertOperation(op)
        activeCredentials = credentials.copy(apiKey = "another-account")

        assertTrue(withTimeout(5000) { repository.observeExpenses(credentials.serverUrl, 1).first() }.isEmpty())
        assertEquals(0, withTimeout(5000) { repository.observeSyncSummary(credentials.serverUrl, 1).first() }.problemCount)
        assertTrue(update(activeCredentials, localId) is ApiResult.Error)
        assertTrue(repository.deleteExpense(credentials.serverUrl, activeCredentials.apiKey, 1, localId, ExpenseType.SERVICE) is ApiResult.Error)
        assertEquals(op.accountKey, database.pendingSyncOpDao().getPendingOperations(credentials.serverUrl).single().accountKey)
    }

    @Test
    fun `claiming legacy work assigns both the row and its pending operation`() = runBlocking {
        val localId = database.expenseDao().insertExpense(expense().copy(remoteId = null, syncState = "PENDING_CREATE")).toInt()
        database.pendingSyncOpDao().insertOperation(pending(localId, null))
        assertTrue(update(credentials, localId) is ApiResult.Error)
        repository.claimLegacyOperations(credentials.serverUrl, credentials.apiKey, 1)
        val key = ReminderRepository.accountKey(credentials)
        assertEquals(key, database.expenseDao().getByLocalId(localId)?.accountKey)
        assertEquals(key, database.pendingSyncOpDao().getPendingOperations(credentials.serverUrl).single().accountKey)
        assertEquals(ApiResult.Success(Unit), update(credentials, localId))
    }

    @Test
    fun `editing a shared server expense scopes the queued change to its author`() = runBlocking {
        val localId = database.expenseDao().insertExpense(expense()).toInt()
        assertEquals(ApiResult.Success(Unit), update(credentials, localId))
        assertEquals(ReminderRepository.accountKey(credentials), database.expenseDao().getByLocalId(localId)?.accountKey)
        activeCredentials = credentials.copy(apiKey = "another-account")
        assertTrue(withTimeout(5000) { repository.observeExpenses(credentials.serverUrl, 1).first() }.isEmpty())
    }

    private suspend fun update(account: Credentials, localId: Int) = repository.updateExpense(
        account.serverUrl, account.apiKey, 1, localId, ExpenseType.SERVICE, ExpenseType.SERVICE,
        LocalDate.of(2026, 11, 6), 10001, "Edited service", 50.0, "")

    private fun pending(localId: Int, accountKey: String?) = PendingSyncOpEntity(
        serverUrl = credentials.serverUrl, vehicleRemoteId = 1, expenseLocalId = localId,
        opType = "CREATE", payloadJson = null, baseFingerprint = null, createdAt = 1,
        attemptCount = 0, lastError = null, accountKey = accountKey)

    private fun expense() = ExpenseEntity(
        serverUrl = credentials.serverUrl, vehicleRemoteId = 1, remoteId = 1,
        type = "SERVICE", date = "2026-11-06", cost = 45.0, odometer = 10000,
        description = "Service", notes = null, liters = null, fuelEconomy = null,
        isFillToFull = null, isMissedFuelUp = null, isRecurring = null, syncState = "SYNCED",
        isDeletedLocal = false, updatedAt = 1, lastSyncedFingerprint = null, lastSyncError = null
    )
}
