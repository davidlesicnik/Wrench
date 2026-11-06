package com.lesicnik.wrench.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WrenchDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: WrenchDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `calendar migration preserves existing offline expenses and pending uploads`() = runBlocking {
        val localId = database.expenseDao().insertExpense(expense()).toInt()
        database.pendingSyncOpDao().insertOperation(operation(localId))
        database.close()
        // Recreate the v1 schema by removing only the new calendar tables.
        SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE reminder_operations")
            it.execSQL("DROP TABLE service_uploads")
            it.execSQL("ALTER TABLE expenses DROP COLUMN accountKey")
            it.execSQL("ALTER TABLE pending_sync_ops DROP COLUMN accountKey")
            it.execSQL("DROP TABLE reminders")
            it.execSQL("DROP TABLE reminder_sync")
            it.version = 1
        }
        database = openDatabase()
        assertEquals(expense().copy(localId = localId), database.expenseDao().getByLocalId(localId))
        assertEquals(1, database.pendingSyncOpDao().countPending(SERVER_URL))
        assertTrue(database.reminderDao().getReminders("account", 1).isEmpty())
    }

    @Test
    fun `maintenance migration preserves v2 expenses and queued changes`() = runBlocking {
        val localId = database.expenseDao().insertExpense(expense()).toInt()
        database.pendingSyncOpDao().insertOperation(operation(localId))
        database.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE reminder_operations")
            it.execSQL("DROP TABLE service_uploads")
            it.execSQL("ALTER TABLE expenses DROP COLUMN accountKey")
            it.execSQL("ALTER TABLE pending_sync_ops DROP COLUMN accountKey")
            it.version = 2
        }
        database = openDatabase()
        assertEquals(expense().copy(localId = localId), database.expenseDao().getByLocalId(localId))
        assertEquals(1, database.pendingSyncOpDao().countPending(SERVER_URL))
        assertTrue(database.maintenanceDao().reminders("account").isEmpty())
    }

    @Test
    fun `offline expense and queued operation survive reopening the database`() = runBlocking {
        val expense = expense()
        val localId = database.expenseDao().insertExpense(expense).toInt()
        val operation = operation(localId)
        val opId = database.pendingSyncOpDao().insertOperation(operation)

        database.close()
        database = openDatabase()

        assertEquals(expense.copy(localId = localId), database.expenseDao().getByLocalId(localId))
        assertEquals(
            listOf(expense.copy(localId = localId)),
            database.expenseDao().observeVisibleExpenses(SERVER_URL, 1).first()
        )
        assertEquals(
            listOf(operation.copy(opId = opId)),
            database.pendingSyncOpDao().getPendingOperations(SERVER_URL)
        )
        assertTrue(database.expenseDao().getVisibleExpenses("https://other.example", 1).isEmpty())
    }

    @Test
    fun `failed transaction rolls back both the expense and queued operation`() = runBlocking {
        try {
            database.withTransaction {
                val localId = database.expenseDao().insertExpense(expense()).toInt()
                database.pendingSyncOpDao().insertOperation(operation(localId))
                throw IllegalStateException("Abort offline edit")
            }
            fail("Expected the transaction to fail")
        } catch (exception: IllegalStateException) {
            assertEquals("Abort offline edit", exception.message)
        }

        assertTrue(database.expenseDao().getVisibleExpenses(SERVER_URL, 1).isEmpty())
        assertEquals(0, database.pendingSyncOpDao().countPending(SERVER_URL))
    }

    private fun openDatabase(): WrenchDatabase =
        Room.databaseBuilder(context, WrenchDatabase::class.java, DATABASE_NAME)
            .addMigrations(WrenchDatabase.MIGRATION_1_2, WrenchDatabase.MIGRATION_2_3).build()

    private fun expense() = ExpenseEntity(
        serverUrl = SERVER_URL,
        vehicleRemoteId = 1,
        remoteId = null,
        type = "fuel",
        date = "2026-11-04",
        cost = 60.5,
        odometer = 12345,
        description = "Offline fuel stop",
        notes = null,
        liters = 40.0,
        fuelEconomy = null,
        isFillToFull = true,
        isMissedFuelUp = false,
        isRecurring = null,
        syncState = "PENDING_CREATE",
        isDeletedLocal = false,
        updatedAt = 1000L,
        lastSyncedFingerprint = null,
        lastSyncError = null
    )

    private fun operation(localId: Int) = PendingSyncOpEntity(
        serverUrl = SERVER_URL,
        vehicleRemoteId = 1,
        expenseLocalId = localId,
        opType = "CREATE",
        payloadJson = "{}",
        baseFingerprint = null,
        createdAt = 1000L,
        attemptCount = 0,
        lastError = null
    )

    private companion object {
        const val DATABASE_NAME = "wrench-database-test.db"
        const val SERVER_URL = "https://test.example"
    }
}
