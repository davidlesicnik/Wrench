package com.lesicnik.wrench.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.remote.records.*
import com.lesicnik.wrench.data.repository.*
import com.lesicnik.wrench.ui.expenses.*
import com.lesicnik.wrench.ui.home.*
import com.lesicnik.wrench.ui.statistics.*
import com.lesicnik.wrench.ui.theme.WrenchTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EverydayUxInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val dark = InstrumentationRegistry.getArguments().getString("darkUx") == "true"
    private val today = LocalDate.now()
    private val expenses = listOf(
        Expense(1, ExpenseType.FUEL, today, 84.82, 146848, "Fuel stop", liters = 52.13, fuelEconomy = 7.9, isFillToFull = true),
        Expense(2, ExpenseType.SERVICE, today.minusDays(10), 160.0, 146184, "Oil and filter"),
        Expense(3, ExpenseType.FUEL, today.minusDays(20), 82.16, 145582, "Fuel", liters = 48.27, fuelEconomy = 7.5)
    )

    @Test fun homeShowsMaintenanceAndOpensDetailsBeforeEditing() {
        var edits = 0
        var maintenance = 0
        val reminder = Reminder(1, 1, "Brake fluid", ReminderMetric.DATE, ReminderUrgency.PAST_DUE, today.minusDays(3), null, null, "", "")
        compose.setContent { WrenchTheme(darkTheme = dark, dynamicColor = false) {
            HomeScreenContent(HomeUiState(FuelStatistics(7.5, 7.9, 146848), buildHomeOverview(expenses), false,
                reminders = listOf(reminder), remindersLoaded = true), "2021 Skoda Superb", "km", {}, {}, {}, { maintenance++ }, {}, {}, { edits++ }, {})
        } }
        compose.onNodeWithText("Next maintenance").assertIsDisplayed()
        compose.onNodeWithText("1 overdue").assertIsDisplayed()
        screenshot("home")
        compose.onNodeWithText("Brake fluid").performClick()
        compose.runOnIdle { assertEquals(1, maintenance) }
        compose.onNodeWithText("Fuel stop").performScrollTo().performClick()
        compose.onNodeWithText("Edit expense").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, edits) }
        screenshot("expense-detail")
        compose.onNodeWithText("Edit expense").performClick()
        compose.runOnIdle { assertEquals(1, edits) }
    }

    @Test fun expenseEntryKeepsOptionalValuesAndSupportsDirectTypeSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, WrenchDatabase::class.java).build()
        try {
            val viewModel = AddEditExpenseViewModel(CredentialsRepository(context), ExpenseRepository(context, database), 1)
            compose.setContent { WrenchTheme(darkTheme = dark, dynamicColor = false) {
                AddEditExpenseScreen(viewModel, "km", 146184, {}, {}, "2021 Skoda Superb")
            } }
            compose.onNodeWithTag("expense-notes").assertDoesNotExist()
            compose.onNodeWithTag("expense-odometer").performTextInput("146848")
            compose.onNodeWithTag("expense-fuel").performTextInput("52.13")
            compose.onNodeWithTag("expense-cost").performTextInput("84.82")
            compose.onNodeWithText("Save fuel").assertIsDisplayed()
            compose.onNodeWithTag("expense-type-selector").assertIsDisplayed()
            closeSoftKeyboard()
            compose.onNodeWithText("Full tank").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(viewModel.uiState.value.isFillToFull) }
            screenshot("add-expense")
            compose.onNodeWithText("Notes (optional)").performScrollTo().performClick()
            compose.onNodeWithTag("expense-notes").performScrollTo().performTextInput("Receipt kept")
            closeSoftKeyboard()
            compose.onNodeWithText("Notes added").performScrollTo().performClick()
            compose.onNodeWithTag("expense-notes").assertDoesNotExist()
            compose.onNodeWithText("Notes added").performClick()
            compose.onNodeWithTag("expense-notes").assertTextContains("Receipt kept")
            compose.onNodeWithTag("expense-type-selector").assertIsDisplayed()
            compose.onNodeWithText("Service", substring = false).assertIsDisplayed().performClick()
            compose.onNodeWithTag("expense-description").assertExists()
            compose.onNodeWithTag("expense-fuel").assertDoesNotExist()
            compose.onNodeWithText("Fuel", substring = false).performClick()
            compose.onNodeWithTag("expense-fuel").assertTextContains("52.13")
            compose.runOnIdle { assertEquals("84.82", viewModel.uiState.value.cost) }
        } finally { database.close() }
    }

    @Test fun serviceShortcutPrefillsAnEditableServiceDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, WrenchDatabase::class.java).build()
        try {
            val viewModel = AddEditExpenseViewModel(CredentialsRepository(context), ExpenseRepository(context, database), 1, "Oil and filter")
            compose.setContent { WrenchTheme(darkTheme = dark, dynamicColor = false) {
                AddEditExpenseScreen(viewModel, "km", 146848, {}, {}, "2021 Skoda Superb")
            } }
            compose.onNodeWithText("Service", substring = false).assertIsSelected()
            compose.onNodeWithTag("expense-description").assertTextContains("Oil and filter")
            compose.onNodeWithTag("expense-description").performTextReplacement("Oil, filter and inspection")
            compose.runOnIdle { assertEquals("Oil, filter and inspection", viewModel.uiState.value.description) }
        } finally { database.close() }
    }

    @Test fun statisticsLeadWithAnswersAndRetainDomainSections() {
        val selected = StatisticsCalculator.filterExpensesByPeriod(expenses, TimePeriod.LAST_30_DAYS)
        val state = StatisticsUiState(isLoading = false, selectedPeriod = TimePeriod.LAST_30_DAYS,
            expenseBreakdown = StatisticsCalculator.calculateExpenseBreakdown(selected),
            costTrends = StatisticsCalculator.calculateCostTrends(selected, TimePeriod.LAST_30_DAYS),
            fuelEconomyTrends = StatisticsCalculator.calculateFuelEconomyTrends(selected),
            odometerTrends = StatisticsCalculator.calculateOdometerTrends(selected),
            keyStatistics = StatisticsCalculator.calculateKeyStatistics(selected, TimePeriod.LAST_30_DAYS),
            spendingComparison = StatisticsCalculator.spendingComparison(expenses, TimePeriod.LAST_30_DAYS))
        compose.setContent { WrenchTheme(darkTheme = dark, dynamicColor = false) {
            StatisticsContent(state, "2021 Skoda Superb", "km", {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("What is this vehicle costing me?").assertIsDisplayed()
        screenshot("statistics-spending")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Cost per km"))
        compose.onNodeWithText("Cost per km").assertIsDisplayed()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Fuel", substring = false) and hasClickAction())
        compose.onNode(hasText("Fuel", substring = false) and hasClickAction()).performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("How efficiently am I driving?"))
        compose.onNodeWithText("How efficiently am I driving?").assertIsDisplayed()
        screenshot("statistics-fuel")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Mileage", substring = false) and hasClickAction())
        compose.onNode(hasText("Mileage", substring = false) and hasClickAction()).performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("How much am I driving?"))
        compose.onNodeWithText("How much am I driving?").assertIsDisplayed()
        screenshot("statistics-mileage")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("ux-test")!!
        directory.mkdirs()
        File(directory, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
