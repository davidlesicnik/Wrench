package com.lesicnik.wrench.ui.calendar

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lesicnik.wrench.data.repository.*
import com.lesicnik.wrench.ui.components.BottomTab
import com.lesicnik.wrench.ui.theme.WrenchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class CalendarInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 5)
    private val reminders = listOf(
        Reminder(1, 1, "Oil and filter", ReminderMetric.BOTH, ReminderUrgency.URGENT, today, 130000, 450,
            "Use manufacturer-approved oil. Replace the filter and check for leaks.", "engine service"),
        Reminder(2, 1, "Annual inspection", ReminderMetric.DATE, ReminderUrgency.NOT_URGENT, today.withDayOfMonth(20), null, null, "", ""),
        Reminder(3, 1, "Brake fluid", ReminderMetric.DATE, ReminderUrgency.PAST_DUE, today.withDayOfMonth(2), null, null, "", ""),
        Reminder(4, 1, "Tyre rotation", ReminderMetric.ODOMETER, ReminderUrgency.VERY_URGENT, null, 129590, 40, "Check tyre pressures.", "tyres")
    )

    @Test
    fun monthSelectionDetailsAndMileageWork() {
        show(CalendarUiState(reminders, false, System.currentTimeMillis()))
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onAllNodesWithText("December 2026").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").performClick()
        screenshot("calendar-populated")

        val day = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + ", 1 reminder"
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasContentDescription(day))
        compose.onNodeWithContentDescription(day).performClick()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Show month"))
        compose.onNodeWithText("Show month").assertIsDisplayed()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Oil and filter"))
        compose.onNodeWithText("Oil and filter").performClick()
        compose.onNodeWithText("Odometer target").assertIsDisplayed()
        compose.onNodeWithText("Notes").assertExists()
        screenshot("calendar-detail")
        compose.onNodeWithContentDescription("Close").performClick()

        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Mileage · 2"))
        compose.onNodeWithText("Mileage · 2").performClick()
        compose.onNodeWithText("Mileage reminders").assertIsDisplayed()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Tyre rotation"))
        compose.onNodeWithText("40 km remaining").assertIsDisplayed()
        screenshot("calendar-mileage")
    }

    @Test
    fun overdueListIncludesMileageOnlyRemindersAndOlderMonths() {
        val overdue = reminders + Reminder(5, 1, "Overdue belt check", ReminderMetric.ODOMETER,
            ReminderUrgency.PAST_DUE, null, 120000, -300, "", "") +
            Reminder(6, 1, "Overdue coolant", ReminderMetric.DATE, ReminderUrgency.PAST_DUE, today.minusMonths(1), null, null, "", "")
        show(CalendarUiState(overdue, false, System.currentTimeMillis()))
        compose.onNodeWithContentDescription("View overdue reminders").performClick()
        compose.onNodeWithText("Overdue reminders").assertIsDisplayed()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Overdue coolant"))
        compose.onNodeWithText("Overdue coolant").assertIsDisplayed()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Overdue belt check"))
        compose.onNodeWithText("300 km overdue").assertIsDisplayed()
    }

    @Test
    fun emptyStateAndTabNavigationWork() {
        var selected: BottomTab? = null
        show(CalendarUiState(isLoading = false, fetchedAt = System.currentTimeMillis()), onTab = { selected = it })
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Nothing scheduled"))
        compose.onNodeWithText("Nothing scheduled").assertIsDisplayed()
        screenshot("calendar-empty")
        compose.onNodeWithText("Expenses", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(BottomTab.EXPENSES, selected) }
    }

    @Test
    fun cachedRemindersRemainVisibleWithRetryWhenOffline() {
        var retries = 0
        show(CalendarUiState(reminders, false, System.currentTimeMillis(), "Couldn't connect to LubeLogger. Check your connection and try again."),
            refresh = { retries++ })
        compose.onNodeWithText("Showing saved reminders").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        screenshot("calendar-offline")
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Oil and filter"))
        compose.onNodeWithText("Oil and filter").assertIsDisplayed()
    }

    @Test
    fun addingUsesTheSelectedDateAndMileageView() {
        var selection: Pair<LocalDate, ReminderMetric>? = null
        show(CalendarUiState(reminders, false, System.currentTimeMillis()), onAdd = { date, metric -> selection = date to metric })
        compose.onNodeWithText("Mileage · 2").performClick()
        compose.onNodeWithContentDescription("Add reminder").performClick()
        compose.runOnIdle { assertEquals(today to ReminderMetric.ODOMETER, selection) }
        compose.onNodeWithText("Calendar · 3").performClick()
        val date = today.withDayOfMonth(20)
        val description = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + ", 1 reminder"
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performClick()
        compose.onNodeWithContentDescription("Add reminder").performClick()
        compose.runOnIdle { assertEquals(date to ReminderMetric.DATE, selection) }
    }

    private fun show(state: CalendarUiState, refresh: () -> Unit = {}, onTab: (BottomTab) -> Unit = {},
        onAdd: (LocalDate, ReminderMetric) -> Unit = { _, _ -> }) {
        compose.setContent {
            WrenchTheme {
                CalendarContent(state, "2000 Test Test", "km", refresh, {}, onTab, onAdd, today, onManage = {})
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("calendar-test")!!
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
