package com.lesicnik.wrench.ui.home

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import java.time.LocalDate

data class HomeOverview(
    val recentExpenses: List<Expense>,
    val odometerDate: LocalDate?,
    val lastOdometer: Int?,
    val thisMonthFuel: Double,
    val thisMonthOther: Double,
    val lastMonthFuel: Double,
    val lastMonthOther: Double,
    val previousMonthToDate: Double = 0.0,
    val comparisonEnd: LocalDate = LocalDate.now().minusMonths(1)
)

internal fun buildHomeOverview(
    expenses: List<Expense>,
    today: LocalDate = LocalDate.now()
): HomeOverview {
    val monthStart = today.withDayOfMonth(1)
    val previousMonthStart = monthStart.minusMonths(1)
    val recorded = expenses.filter { it.date <= today }
    val current = recorded.filter { it.date >= monthStart }
    val previous = recorded.filter { it.date >= previousMonthStart && it.date < monthStart }
    val lastReading = recorded.filter { it.odometer != null }
        .maxWithOrNull(compareBy<Expense> { it.odometer }.thenBy { it.date })
    return HomeOverview(
        recentExpenses = recorded.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }).take(3),
        odometerDate = lastReading?.date,
        lastOdometer = lastReading?.odometer,
        thisMonthFuel = current.filter { it.type == ExpenseType.FUEL }.sumOf { it.cost },
        thisMonthOther = current.filter { it.type != ExpenseType.FUEL }.sumOf { it.cost },
        lastMonthFuel = previous.filter { it.type == ExpenseType.FUEL }.sumOf { it.cost },
        lastMonthOther = previous.filter { it.type != ExpenseType.FUEL }.sumOf { it.cost },
        previousMonthToDate = previous.filter { it.date <= today.minusMonths(1) }.sumOf { it.cost },
        comparisonEnd = today.minusMonths(1)
    )
}
