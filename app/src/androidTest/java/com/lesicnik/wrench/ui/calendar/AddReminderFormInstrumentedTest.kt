package com.lesicnik.wrench.ui.calendar

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lesicnik.wrench.ui.theme.WrenchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AddReminderFormInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun validatesDateMileageAndCombinedSchedules() {
        var state by mutableStateOf(AddReminderUiState(metric = com.lesicnik.wrench.data.repository.ReminderMetric.DATE))
        var saves = 0
        compose.setContent {
            WrenchTheme { AddReminderContent(state, "2000 Test Test", "km", { state = it(state) }, { saves++ }, {}) }
        }
        compose.onNodeWithText("Save reminder").assertIsNotEnabled()
        compose.onNodeWithTag("reminder-description").performTextInput("Oil and filter")
        compose.onNodeWithText("Save reminder").assertIsEnabled()
        compose.onNodeWithText("Both").performClick()
        compose.onNodeWithText("Save reminder").assertIsNotEnabled()
        compose.onNodeWithText("Enter a starting reading").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-use-exact-target").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-odometer").performScrollTo().performTextInput("-5")
        compose.onNodeWithText("Save reminder").assertIsNotEnabled()
        compose.onNodeWithTag("reminder-odometer").performTextClearance()
        compose.onNodeWithTag("reminder-odometer").performTextInput("3000")
        compose.onNodeWithText("Save reminder").assertIsEnabled()
        compose.onNodeWithText("Mileage").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-date").assertDoesNotExist()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save reminder").performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun backsOutWithoutLosingEditsUnlessDiscarded() {
        var state by mutableStateOf(AddReminderUiState(metric = com.lesicnik.wrench.data.repository.ReminderMetric.DATE))
        var exits = 0
        compose.setContent { WrenchTheme {
            AddReminderContent(state, "2000 Test Test", "km", { state = it(state) }, {}, { exits++ })
        } }
        compose.onNodeWithTag("reminder-description").performTextInput("Oil change")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard this reminder?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("reminder-description").assertTextContains("Oil change")
        compose.runOnIdle { assertEquals(0, exits) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun dateShortcutsAndOptionalDetailsKeepTheirValues() {
        val today = LocalDate.of(2026, 11, 5)
        var state by mutableStateOf(AddReminderUiState(dueDate = today, metric = com.lesicnik.wrench.data.repository.ReminderMetric.DATE))
        compose.setContent { WrenchTheme {
            AddReminderContent(state, "2000 Test Test", "km", { state = it(state) }, {}, {}, today)
        } }
        compose.onNodeWithText("In 1 month").performClick()
        compose.runOnIdle { assertEquals(today.plusMonths(1), state.dueDate) }
        compose.onNodeWithText("In 1 year").performClick()
        compose.runOnIdle { assertEquals(today.plusYears(1), state.dueDate) }
        compose.onNodeWithText("Notes and tags (optional)").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-notes").performScrollTo().performTextInput("Approved oil only")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Notes and tags (optional)").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-notes").assertDoesNotExist()
        compose.onNodeWithText("Notes and tags (optional)").performClick()
        compose.onNodeWithTag("reminder-notes").assertTextContains("Approved oil only")
    }

    @Test
    fun defaultMileageFlowNeedsOnlyTheReminderAndPresetBeforeSaving() {
        var state by mutableStateOf(AddReminderUiState(description = "Oil change", startingOdometer = "1500", recordedOdometer = 1500))
        var savedTarget: Int? = null
        compose.setContent { WrenchTheme {
            AddReminderContent(state, "2000 Test Test", "km", { state = it(state) }, { savedTarget = state.draft.dueOdometer }, {})
        } }
        compose.onNodeWithText("Mileage").assertIsSelected()
        compose.onNodeWithTag("reminder-starting-odometer").assertDoesNotExist()
        compose.onNodeWithTag("reminder-use-exact-target").assertDoesNotExist()
        compose.onNodeWithTag("reminder-distance-10000").performScrollTo().performClick()
        compose.onNodeWithText("Reminder at 11,500 km").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Based on recorded 1,500 km").assertIsDisplayed()
        compose.onNodeWithText("Save reminder").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(11500, savedTarget) }
        compose.onNodeWithText("More mileage options").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-starting-odometer").performScrollTo().performTextReplacement("2000")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Reminder at 12,000 km").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reminder-use-exact-target").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-odometer").performScrollTo().performTextInput("9000")
        compose.runOnIdle { assertEquals(9000, state.draft.dueOdometer) }
    }

    @Test
    fun uncertainSaveKeepsInputAndOffersCalendarCheck() {
        var checks = 0
        val state = AddReminderUiState(description = "Inspection", notes = "Keep my note", requiresCalendarCheck = true,
            errorMessage = "Couldn't confirm whether the reminder was saved. Check the calendar before adding it again.")
        compose.setContent {
            WrenchTheme { AddReminderContent(state, "2000 Test Test", "km", {}, {}, { checks++ }) }
        }
        compose.onNodeWithText("Check calendar").assertIsEnabled()
        compose.onNodeWithTag("reminder-description").assertTextContains("Inspection")
        compose.onNodeWithText("Check calendar").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
    }
}
