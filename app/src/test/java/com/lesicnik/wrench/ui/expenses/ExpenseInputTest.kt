package com.lesicnik.wrench.ui.expenses

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ExpenseInputTest {
    @Test
    fun `decimal input accepts either decimal separator without shifting cents`() {
        assertEquals(BigDecimal("84.82"), parseDecimalInput("84.82"))
        assertEquals(BigDecimal("84.82"), parseDecimalInput("84,82"))
        assertEquals(BigDecimal("84"), parseDecimalInput("84"))
        assertEquals(BigDecimal("0.50"), parseDecimalInput(".50"))
        assertEquals(BigDecimal("52.135"), parseDecimalInput("52,135", 3))
    }

    @Test
    fun `pasted amounts accept conventional grouping`() {
        assertEquals(BigDecimal("1234.56"), parseDecimalInput("1,234.56"))
        assertEquals(BigDecimal("1234.56"), parseDecimalInput("1.234,56"))
        assertEquals(BigDecimal("1234.56"), parseDecimalInput("1\u00a0234,56"))
    }

    @Test
    fun `invalid amounts are rejected rather than silently rewritten`() {
        listOf("-1", "1.234", "1,2,3", "abc", "", ".", "12,34.56").forEach {
            assertNull(it, parseDecimalInput(it))
        }
    }

    @Test
    fun `loading existing amounts preserves cents`() {
        assertEquals("19.99", decimalInputValue(19.99))
        assertEquals("84.82", decimalInputValue(84.82))
    }

    @Test
    fun `new drafts warn on unsaved values but pristine form does not`() {
        val empty = AddEditExpenseUiState()
        assertFalse(empty.isDirty)
        assertTrue(empty.copy(notes = "Receipt details").isDirty)
        assertTrue(empty.copy(cost = "84.82").isDirty)
        assertTrue(empty.copy(date = empty.initialDate.minusDays(1)).isDirty)
        assertTrue(empty.copy(expenseType = ExpenseType.SERVICE).isDirty)
        assertFalse(empty.copy(notes = "").isDirty)
    }

    @Test
    fun `editing equivalent decimal representations does not dirty the record`() {
        val original = expense(1, LocalDate.of(2026, 11, 1), "Fuel", null, ExpenseType.FUEL)
        val edit = AddEditExpenseUiState(
            isEditMode = true, originalExpense = original, date = original.date,
            description = original.description, cost = "19.990", fuelConsumed = ""
        )
        // More than two decimal places is invalid input even when the numeric value matches.
        assertTrue(edit.isDirty)
        assertFalse(edit.copy(cost = "19,99").isDirty)
        assertTrue(edit.copy(cost = "19.98").isDirty)
    }

    @Test
    fun `adding fuel to a record with no previous fuel amount marks it dirty`() {
        val original = expense(1, LocalDate.of(2026, 11, 1), "Fuel", null, ExpenseType.FUEL)
        val edit = AddEditExpenseUiState(isEditMode = true, originalExpense = original,
            date = original.date, description = original.description, cost = "19.99")
        assertFalse(edit.isDirty)
        assertTrue(edit.copy(fuelConsumed = "40.5").isDirty)
    }

    @Test
    fun `search type and inclusive date boundaries combine`() {
        val first = expense(1, LocalDate.of(2026, 10, 1), "Service", "Cabin filter", ExpenseType.SERVICE)
        val last = expense(2, LocalDate.of(2026, 10, 31), "Filter replacement", null, ExpenseType.SERVICE)
        val outside = expense(3, LocalDate.of(2026, 11, 1), "Filter replacement", null, ExpenseType.SERVICE)
        val fuel = expense(4, LocalDate.of(2026, 10, 20), "Fuel", "Filter", ExpenseType.FUEL)
        val state = ExpensesUiState(expenses = listOf(first, last, outside, fuel), searchQuery = " FILTER ",
            selectedFilters = setOf(ExpenseType.SERVICE), dateFrom = first.date, dateTo = last.date)
        assertEquals(listOf(first, last), state.filteredExpenses)
        assertTrue(state.copy(selectedFilters = emptySet()).filteredExpenses.isEmpty())
    }

    private fun expense(id: Int, date: LocalDate, description: String, notes: String?, type: ExpenseType) =
        Expense(id = id, date = date, type = type, description = description, notes = notes, cost = 19.99, odometer = null)
}
