package com.lesicnik.wrench.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.remote.ReminderRecord
import com.lesicnik.wrench.data.remote.ReminderWriteData
import com.lesicnik.wrench.data.remote.ReminderWriteResponse
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MaintenanceCoordinatorTest {
    private lateinit var db: WrenchDatabase
    private lateinit var coordinator: MaintenanceCoordinator
    private val remote = FakeRemote()
    private val c = Credentials("https://test.example", "test-account")
    private val key get() = ReminderRepository.accountKey(c)
    private val original = Reminder(7, 1, "Oil", ReminderMetric.BOTH, ReminderUrgency.URGENT,
        LocalDate.of(2026, 11, 6), 10000, 50, "Keep these notes", "oil annual")
    private val target get() = ReminderValues.from(original).copy(dueDate = "2027-11-06", dueOdometer = 20000)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WrenchDatabase::class.java).build()
        coordinator = MaintenanceCoordinator(db, remote)
        remote.current = ReminderValues.from(original)
    }
    @After fun close() { db.close() }
    private suspend fun sync() = MaintenanceCoordinator.writeMutex.withLock { coordinator.syncLocked(c) }
    private suspend fun save(r: Reminder = original) = coordinator.recordService(c, r, expense(), target, false)
    private suspend fun op() = db.maintenanceDao().reminders(key).single()
    private fun expense() = ExpenseEntity(serverUrl = c.serverUrl, vehicleRemoteId = 1, remoteId = null,
        type = "SERVICE", date = "2026-11-06", cost = 45.0, odometer = 10000, description = "Oil", notes = "receipt",
        liters = null, fuelEconomy = null, isFillToFull = null, isMissedFuelUp = null, isRecurring = null,
        syncState = "PENDING_CREATE", isDeletedLocal = false, updatedAt = 1, lastSyncedFingerprint = null, lastSyncError = null)

    @Test fun `service confirmation precedes reminder and repeated sync does not replay writes`() = runBlocking {
        val id = save()
        sync(); sync()
        assertEquals(listOf("create", "fetch", "update"), remote.calls)
        assertEquals("CONFIRMED", op().status)
        assertEquals(91, db.expenseDao().getByLocalId(id)?.remoteId)
        assertEquals(91, db.maintenanceDao().upload(id)?.remoteId)
        assertEquals("CONFIRMED", db.maintenanceDao().upload(id)?.status)
        assertEquals(20000, remote.current?.dueOdometer)
    }
    @Test fun `manual sync while offline leaves unsent service queued for reconnect`() = runBlocking {
        val id = save()
        var connected = false
        val engine = OfflineSyncEngine(db, maintenanceCoordinator = coordinator, loadVehicles = { _, _ ->
            if (connected) Response.success(emptyList()) else throw IOException("No network")
        })
        assertTrue(engine.syncServer(c.serverUrl, c.apiKey) is ApiResult.Error)
        assertEquals("QUEUED", db.maintenanceDao().upload(id)?.status)
        assertTrue(remote.calls.isEmpty())
        connected = true
        assertTrue(engine.syncServer(c.serverUrl, c.apiKey) is ApiResult.Success)
        assertEquals(listOf("create", "fetch", "update"), remote.calls)
        assertEquals("CONFIRMED", op().status)
    }

    @Test fun `offline transaction survives restart and reconnect in order`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "maintenance-restart-test.db"
        context.deleteDatabase(name)
        db.close()
        db = Room.databaseBuilder(context, WrenchDatabase::class.java, name).build()
        coordinator = MaintenanceCoordinator(db, remote)
        val id = save()
        db.close()
        db = Room.databaseBuilder(context, WrenchDatabase::class.java, name).build()
        coordinator = MaintenanceCoordinator(db, remote)
        assertEquals("QUEUED", db.maintenanceDao().upload(id)?.status)
        sync()
        assertEquals(listOf("create", "fetch", "update"), remote.calls)
        db.close(); context.deleteDatabase(name); Unit
    }
    @Test fun `partial failure retries only reminder while independent services continue`() = runBlocking {
        save()
        save(original.copy(id = 8))
        remote.updateCode = 503
        sync()
        assertEquals(2, remote.calls.count { it == "create" })
        assertTrue(db.maintenanceDao().uploads(key).all { it.status == "CONFIRMED" })
        remote.updateCode = 200
        sync(); sync()
        assertEquals(2, remote.calls.count { it == "create" })
        assertEquals("CONFIRMED", db.maintenanceDao().reminders(key).first().status)
    }
    @Test fun `lost creation response never replays and user can select verified server record`() = runBlocking {
        val id = save()
        remote.loseCreate = true
        sync(); sync()
        assertEquals(1, remote.calls.count { it == "create" })
        assertEquals("NEEDS_REVIEW", db.maintenanceDao().upload(id)?.status)
        assertEquals("QUEUED", op().status)
        remote.matches = listOf(Expense(91, ExpenseType.SERVICE, LocalDate.parse("2026-11-06"), 45.0, 10000, "Oil"))
        coordinator.reviewUpload(c, id, 91)
        sync()
        assertEquals("CONFIRMED", op().status)
        assertEquals(1, remote.calls.count { it == "create" })
    }
    @Test fun `process interruption before upload receipt needs review`() = runBlocking {
        val id = save()
        val upload = requireNotNull(db.maintenanceDao().upload(id))
        db.maintenanceDao().save(upload.copy(status = "DISPATCHING"))
        sync()
        assertTrue(remote.calls.isEmpty())
        assertEquals("NEEDS_REVIEW", db.maintenanceDao().upload(id)?.status)
    }
    @Test fun `lost reminder update response is verified without replay`() = runBlocking {
        save(); remote.loseUpdate = true
        sync(); sync()
        assertEquals(1, remote.calls.count { it == "update" })
        assertEquals("CONFIRMED", op().status)
    }
    @Test fun `lost deletion response and already absent reminder are successful`() = runBlocking {
        coordinator.queue(c, original, null)
        remote.loseDelete = true
        sync(); sync()
        assertEquals(1, remote.calls.count { it == "delete" })
        assertEquals("CONFIRMED", op().status)
    }
    @Test fun `only changed fields conflict and unrelated server fields survive`() = runBlocking {
        coordinator.queue(c, original, target)
        remote.current = remote.current!!.copy(notes = "Changed elsewhere", tags = "new tags")
        sync()
        assertEquals("Changed elsewhere", remote.current?.notes)
        assertEquals("new tags", remote.current?.tags)
        assertEquals("CONFIRMED", op().status)
    }
    @Test fun `server change pauses until explicit apply my change`() = runBlocking {
        coordinator.queue(c, original, target)
        remote.current = remote.current!!.copy(dueOdometer = 15000)
        sync()
        assertEquals("CONFLICT", op().status)
        assertFalse(remote.calls.contains("update"))
        coordinator.resolve(c, 1, 7, true)
        sync()
        assertEquals(20000, remote.current?.dueOdometer)
        assertEquals("CONFIRMED", op().status)
    }
    @Test fun `applying a schedule conflict restores its intended date and mileage metric`() = runBlocking {
        coordinator.queue(c, original, target)
        remote.current = remote.current!!.copy(metric = ReminderMetric.DATE, dueOdometer = null)
        sync()
        assertEquals("CONFLICT", op().status)
        coordinator.resolve(c, 1, 7, true)
        sync()
        assertEquals(ReminderMetric.BOTH, remote.current?.metric)
        assertEquals(20000, remote.current?.dueOdometer)
        assertEquals("2027-11-06", remote.current?.dueDate)
    }

    @Test fun `server deletion during update needs attention and can use server version`() = runBlocking {
        coordinator.queue(c, original, target); remote.current = null
        sync()
        assertEquals("CONFLICT", op().status)
        coordinator.resolve(c, 1, 7, false)
        sync()
        assertEquals("CANCELLED", op().status)
        assertFalse(remote.calls.contains("update"))
    }

    @Test fun `applying metric conflicts restores schedule fields that were unchanged locally`() = runBlocking {
        for (serverMetric in listOf(ReminderMetric.ODOMETER, ReminderMetric.DATE)) {
            val baseline = ReminderValues.from(original)
            val localTarget = if (serverMetric == ReminderMetric.ODOMETER) baseline.copy(dueOdometer = 20000)
                else baseline.copy(dueDate = "2027-11-06")
            coordinator.queue(c, original, localTarget)
            remote.current = baseline.copy(metric = serverMetric,
                dueDate = baseline.dueDate.takeIf { serverMetric != ReminderMetric.ODOMETER },
                dueOdometer = baseline.dueOdometer.takeIf { serverMetric != ReminderMetric.DATE },
                notes = "Changed on server", tags = "server tags")
            sync()
            assertEquals("CONFLICT", op().status)
            coordinator.resolve(c, 1, 7, true)
            sync()
            assertEquals("CONFIRMED", op().status)
            assertEquals(localTarget.copy(notes = "Changed on server", tags = "server tags"), remote.current)
            assertNull(remote.current!!.draft().validationError())
            val calls = remote.calls.size
            sync()
            assertEquals(calls, remote.calls.size)
        }
    }
    @Test fun `permissions unsupported endpoints and false success pause writes`() = runBlocking {
        for (code in listOf(401, 403, 404, 405, 501, 200)) {
            coordinator.queue(c, original, target)
            remote.updateCode = code
            remote.success = false
            sync()
            assertEquals("PAUSED", op().status)
            val calls = remote.calls.size
            sync()
            assertEquals(calls, remote.calls.size)
            coordinator.cancel(c, 1, 7)
        }
    }
    @Test fun `missing service record id requires verification even with success true`() = runBlocking {
        val id = save(); remote.recordId = null
        sync(); sync()
        assertEquals("NEEDS_REVIEW", db.maintenanceDao().upload(id)?.status)
        assertFalse(remote.calls.contains("update"))
    }
    @Test fun `account identity isolates dispatch review and cancellation`() = runBlocking {
        val id = save()
        val other = c.copy(apiKey = "another-account")
        MaintenanceCoordinator.writeMutex.withLock { coordinator.syncLocked(other) }
        coordinator.cancel(other, 1, 7)
        assertTrue(remote.calls.isEmpty())
        assertEquals("QUEUED", op().status)
        assertTrue(coordinator.observe(other, 1).first().isEmpty())
        try { coordinator.reviewUpload(other, id, null); fail("Must reject another account") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `one outstanding reminder change can be edited or cancelled and duplicate service rolls back`() = runBlocking {
        save()
        try { save(); fail("Duplicate must fail") } catch (_: IllegalStateException) { }
        assertEquals(1, db.expenseDao().getVisibleExpenses(c.serverUrl, 1).size)
        coordinator.queue(c, original, target.copy(dueOdometer = 30000))
        assertEquals(30000, coordinator.values(op().targetJson!!).dueOdometer)
        coordinator.cancel(c, 1, 7)
        sync()
        assertEquals(1, remote.calls.count { it == "create" })
        assertFalse(remote.calls.contains("update"))
    }
    @Test fun `deleting unsent service cancels dependency without a server write`() = runBlocking {
        val id = save()
        assertTrue(coordinator.deleteUnsent(c, id))
        sync()
        assertNull(db.expenseDao().getByLocalId(id))
        assertEquals("CANCELLED", op().status)
        assertTrue(remote.calls.isEmpty())
    }
    @Test fun `unsent service edits retain the absolute reminder target`() = runBlocking {
        val id = save()
        val local = requireNotNull(db.expenseDao().getByLocalId(id))
        coordinator.editUnsent(c, local.copy(odometer = 12000, notes = "Edited receipt"))
        assertEquals(20000, coordinator.values(op().targetJson!!).dueOdometer)
        sync()
        assertEquals(12000, remote.uploadedExpense?.odometer)
        assertEquals("Edited receipt", remote.uploadedExpense?.notes)
        assertEquals(20000, remote.current?.dueOdometer)
    }
    @Test fun `rejected service creation pauses dependency and can be cancelled as unsent`() = runBlocking {
        val id = save()
        remote.createCode = 403
        sync(); sync()
        assertEquals("PAUSED", db.maintenanceDao().upload(id)?.status)
        assertEquals(1, remote.calls.count { it == "create" })
        assertFalse(remote.calls.contains("update"))
        assertTrue(coordinator.deleteUnsent(c, id))
        assertEquals("CANCELLED", op().status)
    }

    @Test fun `new sync and caller cancellation do not interrupt an active service upload`() = runBlocking {
        save()
        remote.started = CompletableDeferred()
        remote.release = CompletableDeferred()
        val first = async { sync() }
        remote.started!!.await()
        val second = async { sync() }
        first.cancel()
        remote.release!!.complete(Unit)
        try { first.await() } catch (_: CancellationException) { }
        second.await()
        assertEquals(1, remote.calls.count { it == "create" })
        assertEquals(1, remote.calls.count { it == "update" })
        assertEquals("CONFIRMED", op().status)
    }
    @Test fun `rapid concurrent saves commit exactly one expense and dependency`() = runBlocking {
        val results = listOf(async { runCatching { save() } }, async { runCatching { save() } }).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, db.maintenanceDao().uploads(key).size)
        assertEquals(1, db.maintenanceDao().reminders(key).size)
        assertEquals(1, db.expenseDao().getVisibleExpenses(c.serverUrl, 1).size)
    }
    @Test fun `uncertain creation can only be resent after explicit review`() = runBlocking {
        val id = save()
        remote.loseCreate = true
        sync(); sync()
        remote.loseCreate = false
        coordinator.reviewUpload(c, id, null)
        sync()
        assertEquals(2, remote.calls.count { it == "create" })
        assertEquals("CONFIRMED", op().status)
    }
    @Test fun `interrupted reminder dispatch checks applied target on restart`() = runBlocking {
        coordinator.queue(c, original, target)
        db.maintenanceDao().save(op().copy(status = "DISPATCHING"))
        remote.current = target
        sync()
        assertEquals("CONFIRMED", op().status)
        assertEquals(listOf("fetch"), remote.calls)
    }

    @Test fun `confirmed writes stay confirmed when next display refresh fails`() = runBlocking {
        save(); sync()
        remote.fetchFails = true
        sync()
        assertEquals("CONFIRMED", op().status)
        assertEquals(1, remote.calls.count { it == "create" })
        assertEquals(1, remote.calls.count { it == "update" })
    }
    @Test fun `date mileage and combined targets validate and keep untouched fields`() = runBlocking {
        for (metric in ReminderMetric.entries) {
            val r = original.copy(metric = metric, dueDate = original.dueDate.takeIf { metric != ReminderMetric.ODOMETER }, dueOdometer = original.dueOdometer.takeIf { metric != ReminderMetric.DATE })
            remote.current = ReminderValues.from(r)
            val next = ReminderValues.from(r).copy(dueDate = "2027-01-01".takeIf { metric != ReminderMetric.ODOMETER }, dueOdometer = 20000.takeIf { metric != ReminderMetric.DATE })
            coordinator.queue(c, r, next)
            sync()
            assertEquals(next, remote.current)
            assertEquals("CONFIRMED", op().status)
        }
    }

    private class FakeRemote : MaintenanceRemote {
        var current: ReminderValues? = null
        var updateCode = 200
        var createCode = 200
        var uploadedExpense: ExpenseEntity? = null
        var success = true
        var loseCreate = false
        var loseUpdate = false
        var loseDelete = false
        var fetchFails = false
        var started: CompletableDeferred<Unit>? = null
        var release: CompletableDeferred<Unit>? = null
        var recordId: Int? = 91
        var matches: List<Expense> = emptyList()
        val calls = mutableListOf<String>()
        override suspend fun create(c: Credentials, expense: ExpenseEntity): Response<ReminderWriteResponse> {
            calls += "create"
            uploadedExpense = expense
            if (createCode != 200) return Response.error(createCode, "error".toResponseBody())
            started?.complete(Unit)
            release?.await()
            if (loseCreate) throw IOException("Response lost")
            return Response.success(ReminderWriteResponse(true, ReminderWriteData(recordId)))
        }
        override suspend fun reminders(c: Credentials, vehicleId: Int): Response<List<ReminderRecord>> {
            calls += "fetch"
            if (fetchFails) throw IOException("Offline")
            return Response.success(current?.let { listOf(ReminderRecord("7", vehicleId.toString(), it.description,
                it.draft().apiMetric, it.draft().apiMetric, "Urgent", it.dueDate, it.dueOdometer?.toString(), "50", it.notes, it.tags)) }.orEmpty())
        }
        override suspend fun update(c: Credentials, id: Int, values: ReminderValues): Response<ReminderWriteResponse> {
            calls += "update"
            if (updateCode != 200) return Response.error(updateCode, "error".toResponseBody())
            if (success) current = values
            if (loseUpdate) throw IOException("Response lost")
            return Response.success(ReminderWriteResponse(success))
        }
        override suspend fun delete(c: Credentials, id: Int): Response<ReminderWriteResponse> {
            calls += "delete"; current = null
            if (loseDelete) throw IOException("Response lost")
            return Response.success(ReminderWriteResponse(true))
        }
        override suspend fun services(c: Credentials, vehicleId: Int) = matches
    }
}
