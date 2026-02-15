package com.lesicnik.wrench.data.remote.records

import com.lesicnik.wrench.data.sync.ExpenseSyncState
import java.time.LocalDate

enum class ExpenseType {
    SERVICE,
    REPAIR,
    UPGRADE,
    FUEL,
    TAX
}

data class Expense(
    val id: Int,
    val type: ExpenseType,
    val date: LocalDate,
    val cost: Double,
    val odometer: Int?,
    val description: String,
    val notes: String? = null,
    val liters: Double? = null,
    val fuelEconomy: Double? = null, // L/100km
    val syncState: ExpenseSyncState = ExpenseSyncState.SYNCED,
    val syncError: String? = null
)
