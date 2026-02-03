package com.lesicnik.wrench.ui.statistics

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class TimePeriod {
    LAST_30_DAYS,
    LAST_90_DAYS,
    LAST_12_MONTHS,
    ALL_TIME
}

data class ExpenseBreakdownItem(
    val type: ExpenseType,
    val totalCost: Double,
    val percentage: Float,
    val count: Int
)

data class CostTrendPoint(
    val date: LocalDate,
    val label: String,
    val fuelCost: Double,
    val otherCost: Double
)

data class FuelEconomyPoint(
    val date: LocalDate,
    val consumption: Double
)

data class OdometerPoint(
    val date: LocalDate,
    val odometer: Int,
    val label: String
)

data class KeyStatistics(
    val totalCost: Double,
    val fuelCost: Double,
    val nonFuelCost: Double,
    val averageCostPerKm: Double?,
    val averageMonthlyCost: Double,
    val totalDistance: Int?,
    val averageFuelConsumption: Double?,
    val expenseCount: Int
)

object StatisticsCalculator {

    fun filterExpensesByPeriod(expenses: List<Expense>, period: TimePeriod): List<Expense> {
        val now = LocalDate.now()
        val startDate = when (period) {
            TimePeriod.LAST_30_DAYS -> now.minusDays(30)
            TimePeriod.LAST_90_DAYS -> now.minusDays(90)
            TimePeriod.LAST_12_MONTHS -> now.minusMonths(12)
            TimePeriod.ALL_TIME -> LocalDate.MIN
        }
        return expenses.filter { it.date >= startDate }
    }

    fun calculateExpenseBreakdown(expenses: List<Expense>): List<ExpenseBreakdownItem> {
        if (expenses.isEmpty()) return emptyList()

        val totalCost = expenses.sumOf { it.cost }
        if (totalCost == 0.0) return emptyList()

        return expenses
            .groupBy { it.type }
            .map { (type, typeExpenses) ->
                val typeCost = typeExpenses.sumOf { it.cost }
                ExpenseBreakdownItem(
                    type = type,
                    totalCost = typeCost,
                    percentage = (typeCost / totalCost * 100).toFloat(),
                    count = typeExpenses.size
                )
            }
            .sortedByDescending { it.totalCost }
    }

    fun calculateCostTrends(expenses: List<Expense>, period: TimePeriod): List<CostTrendPoint> {
        if (expenses.isEmpty()) return emptyList()

        val now = LocalDate.now()
        val currentMonth = YearMonth.now()

        // Determine how many months to show based on period
        val monthsToShow = when (period) {
            TimePeriod.LAST_30_DAYS -> 1
            TimePeriod.LAST_90_DAYS -> 3
            TimePeriod.LAST_12_MONTHS -> 12
            TimePeriod.ALL_TIME -> {
                val oldestDate = expenses.minOfOrNull { it.date } ?: now
                val monthsBetween = ChronoUnit.MONTHS.between(YearMonth.from(oldestDate), currentMonth).toInt() + 1
                monthsBetween.coerceIn(1, 24) // Cap at 24 months for readability
            }
        }

        val monthlyData = mutableMapOf<YearMonth, Pair<Double, Double>>()

        // Initialize all months with zeros
        for (i in 0 until monthsToShow) {
            val month = currentMonth.minusMonths(i.toLong())
            monthlyData[month] = 0.0 to 0.0
        }

        // Group expenses by month
        expenses.forEach { expense ->
            val month = YearMonth.from(expense.date)
            if (monthlyData.containsKey(month)) {
                val (fuel, other) = monthlyData[month]!!
                if (expense.type == ExpenseType.FUEL) {
                    monthlyData[month] = (fuel + expense.cost) to other
                } else {
                    monthlyData[month] = fuel to (other + expense.cost)
                }
            }
        }

        return monthlyData.entries
            .sortedBy { it.key }
            .map { (month, costs) ->
                CostTrendPoint(
                    date = month.atDay(1),
                    label = month.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    fuelCost = costs.first,
                    otherCost = costs.second
                )
            }
    }

    fun calculateFuelEconomyTrends(expenses: List<Expense>): List<FuelEconomyPoint> {
        return expenses
            .filter { it.type == ExpenseType.FUEL && it.fuelEconomy != null }
            .sortedBy { it.date }
            .map { expense ->
                FuelEconomyPoint(
                    date = expense.date,
                    consumption = expense.fuelEconomy!!
                )
            }
    }

    fun calculateOdometerTrends(expenses: List<Expense>): List<OdometerPoint> {
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d")
        return expenses
            .filter { it.odometer != null }
            .sortedBy { it.date }
            .distinctBy { it.date } // One point per date to avoid clutter
            .map { expense ->
                OdometerPoint(
                    date = expense.date,
                    odometer = expense.odometer!!,
                    label = expense.date.format(dateFormatter)
                )
            }
    }

    fun calculateKeyStatistics(expenses: List<Expense>, period: TimePeriod): KeyStatistics {
        if (expenses.isEmpty()) {
            return KeyStatistics(
                totalCost = 0.0,
                fuelCost = 0.0,
                nonFuelCost = 0.0,
                averageCostPerKm = null,
                averageMonthlyCost = 0.0,
                totalDistance = null,
                averageFuelConsumption = null,
                expenseCount = 0
            )
        }

        val totalCost = expenses.sumOf { it.cost }
        val fuelCost = expenses.filter { it.type == ExpenseType.FUEL }.sumOf { it.cost }
        val nonFuelCost = totalCost - fuelCost

        // Calculate distance from odometer readings
        val expensesWithOdometer = expenses.filter { it.odometer != null }.sortedBy { it.odometer }
        val totalDistance = if (expensesWithOdometer.size >= 2) {
            expensesWithOdometer.last().odometer!! - expensesWithOdometer.first().odometer!!
        } else {
            null
        }

        val averageCostPerKm = if (totalDistance != null && totalDistance > 0) {
            totalCost / totalDistance
        } else {
            null
        }

        // Calculate months in period for average
        val now = LocalDate.now()
        val oldestDate = expenses.minOf { it.date }
        val monthsInPeriod = when (period) {
            TimePeriod.LAST_30_DAYS -> 1.0
            TimePeriod.LAST_90_DAYS -> 3.0
            TimePeriod.LAST_12_MONTHS -> 12.0
            TimePeriod.ALL_TIME -> {
                val months = ChronoUnit.MONTHS.between(oldestDate, now).toDouble()
                (months + 1).coerceAtLeast(1.0)
            }
        }
        val averageMonthlyCost = totalCost / monthsInPeriod

        // Calculate average fuel consumption from fuel records that have economy data
        val fuelEconomies = expenses
            .filter { it.type == ExpenseType.FUEL && it.fuelEconomy != null }
            .map { it.fuelEconomy!! }
        val averageFuelConsumption = if (fuelEconomies.isNotEmpty()) {
            fuelEconomies.average()
        } else {
            null
        }

        return KeyStatistics(
            totalCost = totalCost,
            fuelCost = fuelCost,
            nonFuelCost = nonFuelCost,
            averageCostPerKm = averageCostPerKm,
            averageMonthlyCost = averageMonthlyCost,
            totalDistance = totalDistance,
            averageFuelConsumption = averageFuelConsumption,
            expenseCount = expenses.size
        )
    }
}
