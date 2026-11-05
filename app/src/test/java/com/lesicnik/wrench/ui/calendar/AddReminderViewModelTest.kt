package com.lesicnik.wrench.ui.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.remote.ReminderWriteResponse
import com.lesicnik.wrench.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddReminderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: WrenchDatabase
    private val today = LocalDate.of(2026, 11, 5)
    private val credentials = object : CredentialsStore {
        override suspend fun getCredentials() = Credentials("https://test.example", "test-key")
        override suspend fun saveCredentials(serverUrl: String, apiKey: String) { }
        override suspend fun deleteCredentials() { }
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WrenchDatabase::class.java).build()
    }
    @After fun tearDown() { database.close(); Dispatchers.resetMain() }

    @Test
    fun `initial mileage context is clean and schedule changes count as edits`() {
        val state = AddReminderUiState(dueDate = today, metric = ReminderMetric.ODOMETER)
        assertFalse(state.hasChanges)
        assertTrue(state.copy(metric = ReminderMetric.DATE).hasChanges)
        assertTrue(state.copy(dueDate = today.plusDays(1)).hasChanges)
        assertTrue(state.copy(description = "Inspection").hasChanges)
        assertFalse(state.copy(description = " ").hasChanges)
    }

    @Test
    fun `both schedule requires a valid whole odometer target`() {
        val initial = AddReminderUiState(description = "Oil", metric = ReminderMetric.BOTH, dueDate = today,
            mileageInputMode = MileageInputMode.ODOMETER)
        assertFalse(initial.canSave)
        assertTrue(initial.copy(odometer = "-20").odometerInvalid)
        assertTrue(initial.copy(odometer = "9999999999999999").odometerInvalid)
        assertTrue(initial.copy(odometer = "1.5").odometerInvalid)
        assertTrue(initial.copy(odometer = "2500").canSave)
        assertTrue(initial.copy(metric = ReminderMetric.DATE).canSave)
        assertNull(initial.copy(metric = ReminderMetric.ODOMETER, odometer = "2500").draft.dueDate)
    }

    @Test
    fun `rapid taps and field changes cannot duplicate or alter an in-flight save`() = runTest(dispatcher) {
        var writes = 0
        val response = CompletableDeferred<Response<ReminderWriteResponse>>()
        val repository = ReminderRepository(database, writeReminder = { _, _, draft ->
            writes++
            assertEquals("Oil", draft.description)
            response.await()
        })
        val vm = AddReminderViewModel(credentials, repository, 2, today, ReminderMetric.DATE)
        vm.update { it.copy(description = "Oil") }
        vm.save(); vm.save()
        vm.update { it.copy(description = "Changed") }
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals("Oil", vm.uiState.value.description)
        response.complete(Response.success(ReminderWriteResponse(true)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        vm.save()
        advanceUntilIdle()
        assertEquals(1, writes)
    }

    @Test
    fun `rejected save retains draft and permits a deliberate retry`() = runTest(dispatcher) {
        var accept = false
        val repository = ReminderRepository(database, writeReminder = { _, _, _ -> Response.success(ReminderWriteResponse(accept)) })
        val vm = AddReminderViewModel(credentials, repository, 2, today, ReminderMetric.DATE)
        vm.update { it.copy(description = "Inspection", notes = "Keep this note") }
        vm.save(); advanceUntilIdle()
        assertFalse(vm.uiState.value.saved)
        assertNotNull(vm.uiState.value.errorMessage)
        assertEquals("Keep this note", vm.uiState.value.notes)
        assertTrue(vm.uiState.value.canSave)
        accept = true
        vm.save(); advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `uncertain save retains input and prevents retry until the calendar is checked`() = runTest(dispatcher) {
        var writes = 0
        val repository = ReminderRepository(database, writeReminder = { _, _, _ -> writes++; throw IOException() })
        val vm = AddReminderViewModel(credentials, repository, 2, today, loadOdometer = { _, _ -> ApiResult.Success(1500) })
        vm.update { it.copy(description = "Tyres", metric = ReminderMetric.ODOMETER, odometer = "2500", mileageInputMode = MileageInputMode.ODOMETER) }
        vm.save(); advanceUntilIdle()
        assertTrue(vm.uiState.value.requiresCalendarCheck)
        assertEquals("2500", vm.uiState.value.odometer)
        vm.save(); advanceUntilIdle()
        assertEquals(1, writes)
        assertFalse(vm.uiState.value.saved)
    }

    @Test
    fun `relative intervals calculate the absolute target for both mileage schedules`() {
        val state = AddReminderUiState(description = "Oil", metric = ReminderMetric.ODOMETER,
            startingOdometer = "1500", distance = "5000")
        assertEquals(6500, state.targetOdometer)
        assertEquals(6500, state.draft.dueOdometer)
        assertTrue(state.canSave)
        assertEquals(11500, state.copy(distance = "10000", metric = ReminderMetric.BOTH).draft.dueOdometer)
        assertNull(state.copy(metric = ReminderMetric.DATE).draft.dueOdometer)
        assertTrue(state.copy(startingOdometer = "0").canSave)
    }

    @Test
    fun `relative intervals require a reading and reject nonpositive values and overflow`() {
        val state = AddReminderUiState(description = "Oil", metric = ReminderMetric.ODOMETER, distance = "5000")
        assertFalse(state.canSave)
        for (interval in listOf("0", "-5", "1.5", "999999999999")) {
            assertFalse(state.copy(startingOdometer = "1500", distance = interval).canSave)
        }
        assertFalse(state.copy(startingOdometer = Int.MAX_VALUE.toString(), distance = "1").canSave)
        assertTrue(state.copy(startingOdometer = (Int.MAX_VALUE - 5000).toString()).canSave)
    }

    @Test
    fun `loading a fresh reading preserves a manual starting reading and does not mark an untouched form dirty`() = runTest(dispatcher) {
        val pending = CompletableDeferred<ApiResult<Int>>()
        val repository = ReminderRepository(database)
        val vm = AddReminderViewModel(credentials, repository, 2, today, ReminderMetric.ODOMETER,
            loadOdometer = { _, _ -> pending.await() })
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoadingOdometer)
        assertFalse(vm.uiState.value.hasChanges)
        vm.update { it.copy(startingOdometer = "2000", startingReadingEdited = true, distance = "5000", description = "Oil") }
        assertFalse(vm.uiState.value.canSave)
        pending.complete(ApiResult.Success(1500))
        advanceUntilIdle()
        assertEquals(1500, vm.uiState.value.recordedOdometer)
        assertEquals("2000", vm.uiState.value.startingOdometer)
        assertEquals(7000, vm.uiState.value.targetOdometer)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `relative saves send the calculated total to LubeLogger`() = runTest(dispatcher) {
        var target: Int? = null
        val repository = ReminderRepository(database, writeReminder = { _, _, draft ->
            target = draft.dueOdometer
            Response.success(ReminderWriteResponse(true))
        })
        val vm = AddReminderViewModel(credentials, repository, 2, today, ReminderMetric.ODOMETER,
            loadOdometer = { _, _ -> ApiResult.Success(1500) })
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChanges)
        vm.update { it.copy(description = "Oil", distance = "5000") }
        vm.save(); advanceUntilIdle()
        assertEquals(6500, target)
        assertTrue(vm.uiState.value.saved)
    }
}
