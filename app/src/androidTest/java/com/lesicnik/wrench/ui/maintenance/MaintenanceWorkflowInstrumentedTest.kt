package com.lesicnik.wrench.ui.maintenance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.lesicnik.wrench.MainActivity
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.repository.*
import com.lesicnik.wrench.data.sync.OfflineSyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import com.lesicnik.wrench.data.sync.MaintenanceCoordinator
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Opt-in: writes only to a vehicle whose make AND model are Test; cleans up its own records. */
class MaintenanceWorkflowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recordServiceRescheduleEditAndPermanentlyRemoveOnControlledVehicle() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("maintenanceLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val c = CredentialsRepository(context).getCredentials()
        assumeNotNull(c)
        val credentials = requireNotNull(c)
        val api = NetworkModule.getApi(credentials.serverUrl)
        val vehicle = api.getVehicles(credentials.apiKey).body()?.firstOrNull {
            it.make.equals("Test", true) && it.model.equals("Test", true)
        }
        assumeNotNull(vehicle)
        val testVehicle = requireNotNull(vehicle)
        val name = "Wrench workflow ${UUID.randomUUID().toString().take(8)}"
        val db = WrenchDatabase.getInstance(context)
        val repository = ReminderRepository(db)
        val created = repository.create(credentials, testVehicle.id,
            ReminderDraft(name, ReminderMetric.BOTH, LocalDate.now(), 1500, "Preserve workflow notes", "workflow test"))
        assertTrue("Test reminder must be created", created is ReminderCreateResult.Created)
        val reminderId = requireNotNull((created as ReminderCreateResult.Created).recordId)
        var serviceId: Int? = null
        try {
            repository.refresh(credentials, testVehicle.id)
            compose.waitUntil(15000) { compose.onAllNodesWithText("Test Test").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Test Test").performScrollTo().performClick()
            compose.onNodeWithText("Maintenance", substring = false).performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithText("Record service", substring = false).performScrollTo().performClick()
            compose.onNodeWithTag("expense-description").assertTextContains(name)
            compose.onNodeWithTag("expense-odometer").performTextInput("1500")
            compose.onNodeWithTag("expense-cost").performTextInput("1.00")
            closeSoftKeyboard()
            compose.onNodeWithText("Set next due date / mileage").performScrollTo().performClick()
            compose.onNodeWithText("Next due date (YYYY-MM-DD)").performScrollTo().performTextInput("2030-11-06")
            compose.onNodeWithText("Next odometer target (km)").performScrollTo().performTextInput("11500")
            closeSoftKeyboard()
            screenshot("after-recording")
            compose.onNodeWithText("Save service", substring = false).performClick()
            val key = ReminderRepository.accountKey(credentials)
            compose.waitUntil(30000) { runBlocking {
                val upload = db.maintenanceDao().uploads(key).firstOrNull { it.reminderId == reminderId }
                serviceId = upload?.remoteId
                upload?.status == "CONFIRMED" && db.maintenanceDao().reminders(key).firstOrNull { it.reminderId == reminderId }?.status == "CONFIRMED"
            } }
            val updated = (repository.refresh(credentials, testVehicle.id) as ApiResult.Success).data.reminders.first { it.id == reminderId }
            assertEquals(LocalDate.parse("2030-11-06"), updated.dueDate)
            assertEquals(11500, updated.dueOdometer)
            assertEquals("Preserve workflow notes", updated.notes)
            assertEquals("workflow test", updated.tags)
            OfflineSyncEngine(db).syncServer(credentials.serverUrl, credentials.apiKey)
            val matching = api.getServiceRecords(credentials.apiKey, testVehicle.id).body().orEmpty().filter { it.description == name }
            assertEquals("Repeated sync must not create another service", 1, matching.size)
            assertEquals(serviceId, matching.single().id.toInt())
            compose.onAllNodesWithText(name).onLast().performScrollTo().performClick()
            compose.onNodeWithText("Edit", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("Description", substring = false).performTextReplacement("$name edited")
            compose.onNodeWithText("Save on this device", substring = false).performClick()
            compose.waitUntil(30000) { runBlocking {
                db.maintenanceDao().reminders(key).firstOrNull { it.reminderId == reminderId }?.status == "CONFIRMED" &&
                    repository.cached(credentials, testVehicle.id)?.reminders?.firstOrNull { it.id == reminderId }?.description == "$name edited"
            } }
            compose.onAllNodesWithText("$name edited").onLast().performScrollTo().performClick()
            compose.onNodeWithText("Reschedule", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("Due date (YYYY-MM-DD)").performTextInput("2031-11-06")
            compose.onNodeWithText("Odometer target", substring = false).performTextInput("21500")
            closeSoftKeyboard()
            compose.onNodeWithText("Save on this device", substring = false).performClick()
            compose.waitUntil(30000) { runBlocking {
                repository.cached(credentials, testVehicle.id)?.reminders?.firstOrNull { it.id == reminderId }?.dueOdometer == 21500
            } }
            compose.onAllNodesWithText("$name edited").onLast().performScrollTo().performClick()
            screenshot("reminder-details")
            compose.onNodeWithText("Remove reminder", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("Permanently remove reminder?").assertIsDisplayed()
            compose.onNodeWithText("Remove permanently", substring = false).performClick()
            compose.waitUntil(30000) { runBlocking {
                repository.cached(credentials, testVehicle.id)?.reminders?.none { it.id == reminderId } == true
            } }
            assertTrue(api.getServiceRecords(credentials.apiKey, testVehicle.id).body().orEmpty().any { it.id.toInt() == serviceId })
        } finally {
            // Only records created by this test are removed; ordinary vehicles are never touched.
            MaintenanceCoordinator.writeMutex.withLock {
                NetworkModule.getWriteApi(credentials.serverUrl).deleteReminder(credentials.apiKey, reminderId)
                val createdServices = api.getServiceRecords(credentials.apiKey, testVehicle.id).body().orEmpty().filter { it.description == name }
                for (record in createdServices) api.deleteServiceRecord(credentials.apiKey, record.id.toInt())
                val key = ReminderRepository.accountKey(credentials)
                val ids = db.maintenanceDao().uploads(key).filter { it.reminderId == reminderId }.map { it.expenseLocalId }
                ids.forEach { db.expenseDao().deleteByLocalId(it) }
                db.openHelper.writableDatabase.execSQL("DELETE FROM service_uploads WHERE accountKey = ? AND reminderId = ?", arrayOf<Any>(key, reminderId))
                db.openHelper.writableDatabase.execSQL("DELETE FROM reminder_operations WHERE accountKey = ? AND reminderId = ?", arrayOf<Any>(key, reminderId))
                repository.refresh(credentials, testVehicle.id)
            }

        }
    }
    @Test fun offlineServiceWaitsAcrossRecreationThenUploadsBeforeItsReminder() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("maintenanceLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val session = CredentialsRepository(context).getCredentials()
        assumeNotNull(session)
        val c = requireNotNull(session)
        val api = NetworkModule.getApi(c.serverUrl)
        val vehicle = api.getVehicles(c.apiKey).body()?.firstOrNull { it.make.equals("Test", true) && it.model.equals("Test", true) }
        assumeNotNull(vehicle)
        val car = requireNotNull(vehicle)
        val name = "Wrench offline ${UUID.randomUUID().toString().take(8)}"
        val db = WrenchDatabase.getInstance(context)
        val repository = ReminderRepository(db)
        val created = repository.create(c, car.id, ReminderDraft(name, ReminderMetric.ODOMETER, null, 1500))
        assertTrue(created is ReminderCreateResult.Created)
        val reminderId = requireNotNull((created as ReminderCreateResult.Created).recordId)
        val key = ReminderRepository.accountKey(c)
        val wifiWasOn = shell("settings get global wifi_on").trim() == "1"
        val dataWasOn = shell("settings get global mobile_data").trim() == "1"
        val airplaneWasOn = shell("cmd connectivity airplane-mode").trim() == "enabled"
        try {
            repository.refresh(c, car.id)
            compose.waitUntil(15000) { compose.onAllNodesWithText("Test Test").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Test Test").performScrollTo().performClick()
            compose.onNodeWithText("Maintenance", substring = false).performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithText("Record service", substring = false).performScrollTo().performClick()
            compose.onNodeWithTag("expense-odometer").performTextInput("1500")
            compose.onNodeWithTag("expense-cost").performTextInput("1.00")
            closeSoftKeyboard()
            compose.onNodeWithText("Set next due date / mileage").performScrollTo().performClick()
            compose.onNodeWithText("Add distance to service odometer").performScrollTo().performClick()
            compose.onNodeWithText("Distance after this service (km)").performScrollTo().performTextInput("10000")
            closeSoftKeyboard()
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable"); shell("svc data disable")
            compose.waitUntil(20000) { shell("dumpsys connectivity").contains("Active default network: none") }
            val connected = runCatching { api.getVehicles(c.apiKey).isSuccessful }.getOrDefault(false)
            assertFalse("Network must be offline before saving", connected)
            compose.onNodeWithText("Save service", substring = false).performClick()
            compose.waitUntil(15000) { runBlocking { db.maintenanceDao().uploads(key).any { it.reminderId == reminderId } } }
            val upload = db.maintenanceDao().uploads(key).single { it.reminderId == reminderId }
            assertEquals("QUEUED", upload.status)
            assertNull(upload.remoteId)
            val pending = db.maintenanceDao().reminders(key).single { it.reminderId == reminderId }
            assertEquals(11500, repository.maintenance.values(requireNotNull(pending.targetJson)).dueOdometer)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20000) { compose.onAllNodesWithText("Home", substring = false).fetchSemanticsNodes().isNotEmpty() }
            assertEquals("QUEUED", db.maintenanceDao().upload(upload.expenseLocalId)?.status)
            compose.onNodeWithText("Home", substring = false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Saved on this device · Waiting for service upload").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Saved on this device · Waiting for service upload").performScrollTo().assertIsDisplayed()
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable"); shell("svc data enable")
            compose.waitUntil(45000) { runBlocking {
                db.maintenanceDao().upload(upload.expenseLocalId)?.status == "CONFIRMED" &&
                    db.maintenanceDao().reminders(key).single { it.reminderId == reminderId }.status == "CONFIRMED"
            } }
            OfflineSyncEngine(db).syncServer(c.serverUrl, c.apiKey)
            val updated = (repository.refresh(c, car.id) as ApiResult.Success).data.reminders.single { it.id == reminderId }
            assertEquals(11500, updated.dueOdometer)
            assertEquals(1, api.getServiceRecords(c.apiKey, car.id).body().orEmpty().count { it.description == name })
        } finally {
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable"); shell("svc data enable")
            // Wait for the read-only connection check before cleaning up this test's records.
            var online = false
            repeat(20) {
                if (!online) { online = runCatching { api.getVehicles(c.apiKey).isSuccessful }.getOrDefault(false)
                    if (!online) kotlinx.coroutines.delay(500) }
            }
            assertTrue("Reconnect for test cleanup", online)
            MaintenanceCoordinator.writeMutex.withLock {
                NetworkModule.getWriteApi(c.serverUrl).deleteReminder(c.apiKey, reminderId)
                api.getServiceRecords(c.apiKey, car.id).body().orEmpty().filter { it.description == name }.forEach { api.deleteServiceRecord(c.apiKey, it.id.toInt()) }
                db.maintenanceDao().uploads(key).filter { it.reminderId == reminderId }.forEach { db.expenseDao().deleteByLocalId(it.expenseLocalId) }
                db.openHelper.writableDatabase.execSQL("DELETE FROM service_uploads WHERE accountKey = ? AND reminderId = ?", arrayOf<Any>(key, reminderId))
                db.openHelper.writableDatabase.execSQL("DELETE FROM reminder_operations WHERE accountKey = ? AND reminderId = ?", arrayOf<Any>(key, reminderId))
                repository.refresh(c, car.id)
            }
            if (airplaneWasOn) shell("cmd connectivity airplane-mode enable")
            if (!wifiWasOn) shell("svc wifi disable")
            if (!dataWasOn) shell("svc data disable")
        }
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("maintenance-tests")!!
        directory.mkdirs()
        java.io.File(directory, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

}
