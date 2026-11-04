package com.lesicnik.wrench.ui.home

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeOverviewTest {
    private val today = LocalDate.of(2026, 1, 4)
    private fun expense(id: Int, date: String, cost: Double = 10.0, type: ExpenseType = ExpenseType.FUEL, odometer: Int? = null) =
        Expense(id, type, LocalDate.parse(date), cost, odometer, "Record $id")

    @Test
    fun `monthly totals handle year boundaries and exclude future records`() {
        val summary = buildHomeOverview(listOf(
            expense(1, "2025-12-01", 30.0),
            expense(2, "2025-12-31", 50.0, ExpenseType.SERVICE),
            expense(3, "2026-01-01", 20.0),
            expense(4, "2026-01-04", 40.0, ExpenseType.TAX),
            expense(5, "2026-01-05", 999.0),
            expense(6, "2025-11-30", 999.0)
        ), today)
        assertEquals(20.0, summary.thisMonthFuel, 0.001)
        assertEquals(40.0, summary.thisMonthOther, 0.001)
        assertEquals(30.0, summary.lastMonthFuel, 0.001)
        assertEquals(50.0, summary.lastMonthOther, 0.001)
        assertEquals(listOf(4, 3, 2), summary.recentExpenses.map { it.id })
    }

    @Test
    fun `mileage date belongs to highest recorded reading rather than latest expense`() {
        val summary = buildHomeOverview(listOf(
            expense(1, "2026-01-01", odometer = 1000),
            expense(2, "2026-01-02", odometer = 900),
            expense(3, "2026-01-03", type = ExpenseType.TAX),
            expense(4, "2026-01-05", odometer = 2000)
        ), today)
        assertEquals(LocalDate.of(2026, 1, 1), summary.odometerDate)
    }

    @Test
    fun `empty history has no mileage date or activity and zero totals`() {
        val summary = buildHomeOverview(emptyList(), today)
        assertNull(summary.odometerDate)
        assertTrue(summary.recentExpenses.isEmpty())
        assertEquals(0.0, summary.thisMonthFuel + summary.thisMonthOther, 0.001)
    }
}
