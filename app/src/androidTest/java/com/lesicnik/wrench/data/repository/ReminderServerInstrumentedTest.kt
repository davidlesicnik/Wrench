package com.lesicnik.wrench.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Optional, read-only smoke check using the emulator's existing signed-in session. */
@RunWith(AndroidJUnit4::class)
class ReminderServerInstrumentedTest {
    @Test
    fun signedInServerReturnsRemindersAndPersistsThem() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = CredentialsRepository(context).getCredentials()
        assumeNotNull(credentials)
        val session = requireNotNull(credentials)
        val vehicles = NetworkModule.getApi(session.serverUrl).getVehicles(session.apiKey)
        assertTrue("Signed-in vehicle API should be accessible", vehicles.isSuccessful)
        // Live checks are deliberately restricted to the dedicated test car.
        val vehicle = vehicles.body()?.firstOrNull {
            it.make.equals("Test", ignoreCase = true) && it.model.equals("Test", ignoreCase = true)
        }
        assumeNotNull(vehicle)
        val repository = ReminderRepository(WrenchDatabase.getInstance(context))
        val result = repository.refresh(session, requireNotNull(vehicle).id)
        assertTrue((result as? ApiResult.Error)?.message ?: "Reminders should load", result is ApiResult.Success)
        assertEquals((result as ApiResult.Success).data, repository.cached(session, vehicle.id))
    }
}
