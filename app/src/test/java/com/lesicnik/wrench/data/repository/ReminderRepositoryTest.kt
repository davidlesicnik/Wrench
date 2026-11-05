package com.lesicnik.wrench.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.remote.ReminderRecord
import com.lesicnik.wrench.data.remote.ReminderWriteResponse
import com.lesicnik.wrench.data.remote.ReminderWriteData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderRepositoryTest {
    private lateinit var database: WrenchDatabase
    private lateinit var repository: ReminderRepository
    private val credentials = Credentials("https://test.example", "test-key")
    private val record = ReminderRecord(id = "1", vehicleId = "2", description = "Inspection", metric = "Date", dueDate = "2026-11-05", urgency = "Urgent")
    private var response: () -> Response<List<ReminderRecord>> = { Response.success(listOf(record)) }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WrenchDatabase::class.java).build()
        repository = ReminderRepository(database) { _, _ -> response() }
    }

    @After fun tearDown() { database.close() }

    @Test
    fun `latest odometer returns the server reading including a genuine zero`() = runBlocking {
        for (value in listOf(0, 1500)) {
            val source = ReminderRepository(database, fetchOdometer = { _, id -> assertEquals(2, id); Response.success(value) })
            assertEquals(ApiResult.Success(value), source.latestOdometer(credentials, 2))
        }
    }

    @Test
    fun `missing invalid or unavailable readings do not invent an odometer`() = runBlocking {
        for (result in listOf(Response.success<Int>(null), Response.success(-1), Response.error(403, "{}".toResponseBody()))) {
            val source = ReminderRepository(database, fetchOdometer = { _, _ -> result })
            assertTrue(source.latestOdometer(credentials, 2) is ApiResult.Error)
        }
        val offline = ReminderRepository(database, fetchOdometer = { _, _ -> throw IOException() })
        assertTrue(offline.latestOdometer(credentials, 2) is ApiResult.Error)
    }

    @Test
    fun `HTTP 200 with success false does not report a saved reminder`() = runBlocking {
        val writer = ReminderRepository(database, writeReminder = { _, _, _ -> Response.success(ReminderWriteResponse(false)) })
        val result = writer.create(credentials, 2, draft())
        assertTrue(result is ReminderCreateResult.Failed)
        assertFalse((result as ReminderCreateResult.Failed).mayHaveSaved)
    }

    @Test
    fun `confirmed creation succeeds even when subsequent calendar refresh fails`() = runBlocking {
        val writer = ReminderRepository(database,
            writeReminder = { _, id, input ->
                assertEquals(2, id)
                assertEquals("Date", input.apiMetric)
                Response.success(ReminderWriteResponse(true, ReminderWriteData(123)))
            }, fetchReminders = { _, _ -> throw IOException() })
        assertEquals(ReminderCreateResult.Created(123), writer.create(credentials, 2, draft()))
        assertTrue(writer.refresh(credentials, 2) is ApiResult.Error)
    }

    @Test
    fun `invalid reminders never reach the server and ambiguous writes require a calendar check`() = runBlocking {
        var writes = 0
        val writer = ReminderRepository(database, writeReminder = { _, _, _ -> writes++; throw IOException() })
        assertTrue(writer.create(credentials, 2, draft().copy(description = " ")) is ReminderCreateResult.Failed)
        assertEquals(0, writes)
        val result = writer.create(credentials, 2, draft()) as ReminderCreateResult.Failed
        assertTrue(result.mayHaveSaved)
        assertEquals(1, writes)
    }

    @Test
    fun `permission errors and unsupported endpoints are definite failures`() = runBlocking {
        for (code in listOf(403, 404, 405)) {
            val writer = ReminderRepository(database, writeReminder = { _, _, _ -> Response.error(code, "{}".toResponseBody()) })
            assertFalse((writer.create(credentials, 2, draft()) as ReminderCreateResult.Failed).mayHaveSaved)
        }
    }

    private fun draft() = ReminderDraft("Inspection", ReminderMetric.DATE, LocalDate.of(2026, 11, 5), null)

    @Test
    fun `cache is isolated by API identity server and vehicle`() = runBlocking {
        assertTrue(repository.refresh(credentials, 2) is ApiResult.Success)
        assertEquals("Inspection", repository.cached(credentials, 2)!!.reminders.single().description)
        assertNull(repository.cached(credentials.copy(apiKey = "another-key"), 2))
        assertNull(repository.cached(credentials.copy(serverUrl = "https://other.example"), 2))
        assertNull(repository.cached(credentials, 3))
    }

    @Test
    fun `connection failures malformed data and wrong vehicles preserve the previous snapshot`() = runBlocking {
        repository.refresh(credentials, 2)
        val before = repository.cached(credentials, 2)
        response = { throw IOException("Offline") }
        assertEquals(ApiErrorType.NETWORK, (repository.refresh(credentials, 2) as ApiResult.Error).type)
        assertEquals(before, repository.cached(credentials, 2))
        response = { Response.success(listOf(record.copy(dueDate = "invalid"))) }
        assertTrue(repository.refresh(credentials, 2) is ApiResult.Error)
        assertEquals(before, repository.cached(credentials, 2))
        response = { Response.success(listOf(record.copy(vehicleId = "99"))) }
        assertTrue(repository.refresh(credentials, 2) is ApiResult.Error)
        assertEquals(before, repository.cached(credentials, 2))
    }

    @Test
    fun `successful empty response removes deleted reminders and remains cached`() = runBlocking {
        repository.refresh(credentials, 2)
        response = { Response.success(emptyList()) }
        assertTrue(repository.refresh(credentials, 2) is ApiResult.Success)
        assertEquals(emptyList<Reminder>(), repository.cached(credentials, 2)!!.reminders)
    }

    @Test
    fun `denied requests report authentication errors and cancellation propagates`() = runBlocking {
        response = { Response.error(403, "{}".toResponseBody()) }
        assertEquals(ApiErrorType.AUTHENTICATION, (repository.refresh(credentials, 2) as ApiResult.Error).type)
        response = { throw CancellationException() }
        try {
            repository.refresh(credentials, 2)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
