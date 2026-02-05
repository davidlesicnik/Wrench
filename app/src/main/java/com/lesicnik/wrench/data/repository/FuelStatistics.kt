package com.lesicnik.wrench.data.repository

data class FuelStatistics(
    val averageFuelConsumption: Double?,
    val lastFuelConsumption: Double?,
    val lastOdometer: Int?,
    val thisMonthFuelCost: Double = 0.0,
    val thisMonthOtherCost: Double = 0.0,
    val lastMonthFuelCost: Double = 0.0,
    val lastMonthOtherCost: Double = 0.0
)

