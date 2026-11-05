package com.lesicnik.wrench.ui.statistics

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatisticsCalculatorTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun expense(date: LocalDate, cost: Double = 10.0) = Expense(1, ExpenseType.FUEL, date, cost, 1000, "Fuel")

    @Test fun `rolling periods include exactly the selected number of days and exclude future records`() {
        for ((period, days) in listOf(TimePeriod.LAST_30_DAYS to 30L, TimePeriod.LAST_90_DAYS to 90L)) {
            val records = (-100L..1L).map { expense(today.plusDays(it)) }
            val filtered = StatisticsCalculator.filterExpensesByPeriod(records, period, today)
            assertEquals(days.toInt(), filtered.size)
            assertEquals(today.minusDays(days - 1), filtered.first().date)
            assertEquals(today, filtered.last().date)
        }
    }

    @Test fun `comparison uses adjacent equal length periods without overlapping the boundary`() {
        val start = today.minusDays(29)
        val comparison = StatisticsCalculator.spendingComparison(listOf(
            expense(today, 20.0), expense(start, 30.0), expense(start.minusDays(1), 40.0),
            expense(start.minusDays(30), 50.0), expense(start.minusDays(31), 999.0), expense(today.plusDays(1), 999.0)
        ), TimePeriod.LAST_30_DAYS, today)!!
        assertEquals(50.0, comparison.current, 0.001)
        assertEquals(90.0, comparison.previous, 0.001)
        assertEquals(start.minusDays(30), comparison.previousStart)
        assertEquals(start.minusDays(1), comparison.previousEnd)
        assertNull(StatisticsCalculator.spendingComparison(emptyList(), TimePeriod.ALL_TIME, today))
    }

    @Test fun `cost chart retains every month intersecting the selected period`() {
        val records = listOf(expense(today, 20.0), expense(today.minusDays(10), 30.0), expense(today.minusDays(31), 999.0))
        val trends = StatisticsCalculator.calculateCostTrends(records, TimePeriod.LAST_30_DAYS, today)
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), trends.map { it.date })
        assertEquals(50.0, trends.sumOf { it.fuelCost + it.otherCost }, 0.001)
    }

    @Test fun `twelve month periods handle leap days and all time excludes future entries`() {
        val leapDay = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2023, 3, 1), StatisticsCalculator.periodStart(TimePeriod.LAST_12_MONTHS, leapDay))
        assertEquals(1, StatisticsCalculator.filterExpensesByPeriod(listOf(expense(today), expense(today.plusDays(1))), TimePeriod.ALL_TIME, today).size)
    }
}
