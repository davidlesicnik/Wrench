package com.lesicnik.wrench.data.repository.expense

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.repository.FuelStatistics
import java.util.concurrent.ConcurrentHashMap

class ExpenseCache {
    private val expensesCache = ConcurrentHashMap<Int, List<Expense>>()
    private val fuelStatsCache = ConcurrentHashMap<Int, FuelStatistics>()
    private val preloadingVehicles = ConcurrentHashMap.newKeySet<Int>()

    fun getExpenses(vehicleId: Int): List<Expense>? = expensesCache[vehicleId]

    fun putExpenses(vehicleId: Int, expenses: List<Expense>) {
        expensesCache[vehicleId] = expenses
    }

    fun getFuelStatistics(vehicleId: Int): FuelStatistics? = fuelStatsCache[vehicleId]

    fun putFuelStatistics(vehicleId: Int, stats: FuelStatistics) {
        fuelStatsCache[vehicleId] = stats
    }

    fun invalidate(vehicleId: Int) {
        expensesCache.remove(vehicleId)
        fuelStatsCache.remove(vehicleId)
    }

    fun clearAll() {
        expensesCache.clear()
        fuelStatsCache.clear()
        preloadingVehicles.clear()
    }

    fun tryBeginPreload(vehicleId: Int): Boolean = preloadingVehicles.add(vehicleId)

    fun endPreload(vehicleId: Int) {
        preloadingVehicles.remove(vehicleId)
    }
}

