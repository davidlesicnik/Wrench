package com.lesicnik.wrench.ui.calendar

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lesicnik.wrench.MainActivity
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.remote.LocalHttpOnlyInterceptor
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.remote.ReminderWriteResponse
import com.lesicnik.wrench.data.repository.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Query
import java.io.File
import java.time.LocalDate

/** Explicitly opt in with enableLiveReminderWrites=true. Only the dedicated Test Test car is eligible. */
@RunWith(AndroidJUnit4::class)
class CalendarCreationLiveInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun createDateMileageAndCombinedRemindersThroughTheApp() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("enableLiveReminderWrites") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val session = CredentialsRepository(context).getCredentials()
        assumeNotNull(session)
        val credentials = requireNotNull(session)
        val api = NetworkModule.getApi(credentials.serverUrl)
        val vehicles = api.getVehicles(credentials.apiKey)
        assertTrue(vehicles.isSuccessful)
        val vehicle = vehicles.body().orEmpty().singleOrNull {
            it.make.equals("Test", true) && it.model.equals("Test", true)
        }
        assumeNotNull(vehicle)
        val testCar = requireNotNull(vehicle)
        val baselineResponse = api.getLatestOdometer(credentials.apiKey, testCar.id)
        assertTrue("Test car's latest odometer should load", baselineResponse.isSuccessful)
        val baseline = requireNotNull(baselineResponse.body())
        val marker = "wrench-calendar-test-${System.currentTimeMillis()}"
        val descriptions = listOf("Calendar test: inspection", "Oil change", "Calendar test: oil and filter")
        val cleanup = Retrofit.Builder().baseUrl(credentials.serverUrl.trimEnd('/') + "/")
            .client(OkHttpClient.Builder().addInterceptor(LocalHttpOnlyInterceptor()).followRedirects(false)
                .followSslRedirects(false).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(ReminderCleanupApi::class.java)
        try {
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Test Test").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Test Test").performClick()
            compose.onNodeWithText("Calendar").performClick()
            for (metric in listOf(ReminderMetric.ODOMETER, ReminderMetric.DATE, ReminderMetric.BOTH)) {
                val index = ReminderMetric.entries.indexOf(metric)
                compose.onNodeWithContentDescription("Add reminder").performClick()
                compose.onNodeWithTag("reminder-description").performTextInput(descriptions[index])
                closeSoftKeyboard()
                when (metric) {
                    ReminderMetric.DATE -> {
                        compose.onNodeWithText("Date").performClick()
                        compose.onNodeWithTag("reminder-date").performClick()
                        compose.onNodeWithText("OK").performClick()
                    }
                    ReminderMetric.ODOMETER -> compose.onNodeWithText("Mileage").assertIsSelected()
                    ReminderMetric.BOTH -> compose.onNodeWithText("Both").performClick()
                }
                if (metric != ReminderMetric.DATE) {
                    compose.onNodeWithTag("reminder-distance").performScrollTo()
                    if (metric == ReminderMetric.ODOMETER) {
                        compose.onNodeWithTag("reminder-distance-10000").performScrollTo().performClick()
                    } else compose.onNodeWithTag("reminder-distance").performTextInput("3000")
                    compose.waitUntil(10_000) { compose.onAllNodesWithText("Loading the latest recorded odometer…").fetchSemanticsNodes().isEmpty() }
                    closeSoftKeyboard()
                    val interval = if (metric == ReminderMetric.ODOMETER) 10000 else 3000
                    compose.onNodeWithText("Reminder at ${reading(baseline + interval)} km").performScrollTo().assertIsDisplayed()
                }
                closeSoftKeyboard()
                screenshot("reminder-form-main-${metric.name.lowercase()}")
                compose.onNodeWithText("Notes and tags (optional)").performScrollTo().performClick()
                compose.onNodeWithTag("reminder-notes").performScrollTo().performTextInput("Temporary calendar creation check.")
                compose.onNodeWithTag("reminder-tags").performScrollTo().performTextInput(marker)
                closeSoftKeyboard()
                if (metric == ReminderMetric.ODOMETER) {
                    compose.activityRule.scenario.recreate()
                    compose.onNodeWithTag("reminder-description").assertTextContains(descriptions[index])
                }
                screenshot("reminder-create-${metric.name.lowercase()}")
                compose.onNodeWithText("Save reminder").assertIsEnabled().performClick()
                compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Add reminder").fetchSemanticsNodes().isNotEmpty() }
                compose.waitUntil(10_000) { compose.onNodeWithText(descriptions[index]).isDisplayed() }
                compose.onNodeWithText(descriptions[index]).assertIsDisplayed()
                val response = api.getReminders(credentials.apiKey, testCar.id)
                assertTrue(response.isSuccessful)
                val saved = response.body().orEmpty().single { it.description == descriptions[index] && it.tags.orEmpty().split(' ').contains(marker) }.toReminder()
                assertEquals(testCar.id, saved.vehicleId)
                assertEquals(metric, saved.metric)
                assertEquals("Temporary calendar creation check.", saved.notes)
                if (metric != ReminderMetric.ODOMETER) assertEquals(LocalDate.now(), saved.dueDate)
                if (metric != ReminderMetric.DATE) assertEquals(baseline + if (metric == ReminderMetric.ODOMETER) 10000 else 3000, saved.dueOdometer)
                screenshot("reminder-saved-${metric.name.lowercase()}")
            }
        } finally {
            // Clean up only this run's tagged reminders on the guarded test vehicle.
            val response = api.getReminders(credentials.apiKey, testCar.id)
            assertTrue("Must be able to locate temporary test reminders for cleanup", response.isSuccessful)
            response.body().orEmpty().filter {
                it.vehicleId?.toIntOrNull() == testCar.id && it.description in descriptions && it.tags.orEmpty().split(' ').contains(marker)
            }.forEach {
                val result = cleanup.delete(credentials.apiKey, requireNotNull(it.id?.toIntOrNull()))
                assertTrue("Temporary reminder cleanup failed", result.isSuccessful && result.body()?.success == true)
            }
            val remaining = api.getReminders(credentials.apiKey, testCar.id)
            assertTrue(remaining.isSuccessful)
            assertFalse(remaining.body().orEmpty().any { it.tags.orEmpty().split(' ').contains(marker) })
            assertEquals("Reminder creation must not change the vehicle odometer", baseline, api.getLatestOdometer(credentials.apiKey, testCar.id).body())
            ReminderRepository(WrenchDatabase.getInstance(context)).refresh(credentials, testCar.id)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = instrumentation.targetContext.getExternalFilesDir("calendar-test")!!
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private interface ReminderCleanupApi {
        @DELETE("api/vehicle/reminders/delete")
        suspend fun delete(@Header("x-api-key") key: String, @Query("id") id: Int): Response<ReminderWriteResponse>
    }
}
