package com.lesicnik.wrench.data.repository.expense

import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.repository.FuelStatistics

class FuelStatsCalculator {

    fun computeFuelEconomyByOdometer(
        fuelRecords: List<FuelRecord>,
        parseMileage: (String?) -> Int?,
        parseNumber: (String?) -> Double
    ): Map<Int, Double> {
        return computeFuelEconomies(fuelRecords, parseMileage, parseNumber)
            .associate { it.odometer to it.economy }
    }

    fun computeFuelStatistics(
        fuelRecords: List<FuelRecord>,
        parseMileage: (String?) -> Int?,
        parseNumber: (String?) -> Double
    ): FuelStatistics {
        if (fuelRecords.isEmpty()) {
            return FuelStatistics(null, null, null)
        }

        val sortedFuel = fuelRecords
            .filter { parseMileage(it.odometer) != null }
            .sortedBy { parseMileage(it.odometer) }
        val fuelEconomies = computeFuelEconomies(sortedFuel, parseMileage, parseNumber)
            .map { it.economy }

        val averageFuelEconomy = fuelEconomies.takeIf { it.isNotEmpty() }?.average()
        val lastOdometer = sortedFuel.lastOrNull()?.let { parseMileage(it.odometer) }

        return FuelStatistics(
            averageFuelConsumption = averageFuelEconomy,
            lastFuelConsumption = fuelEconomies.lastOrNull(),
            lastOdometer = lastOdometer
        )
    }

    private fun computeFuelEconomies(
        fuelRecords: List<FuelRecord>,
        parseMileage: (String?) -> Int?,
        parseNumber: (String?) -> Double
    ): List<FuelEconomyAtOdometer> {
        val sortedFuel = fuelRecords
            .filter { parseMileage(it.odometer) != null }
            .sortedBy { parseMileage(it.odometer) }

        val fuelEconomies = mutableListOf<FuelEconomyAtOdometer>()
        var lastFullOdometer: Int? = null
        var pendingLiters = 0.0

        sortedFuel.forEach { record ->
            val odometer = parseMileage(record.odometer) ?: return@forEach
            val liters = record.fuelConsumed?.let { parseNumber(it) } ?: 0.0
            val isFillToFull = record.isFillToFull.toBooleanLenientOrNull() != false

            if (record.missedFuelUp.toBooleanLenientOrNull() == true) {
                pendingLiters = 0.0
                lastFullOdometer = odometer.takeIf { isFillToFull }
                return@forEach
            }

            if (!isFillToFull) {
                if (liters > 0) {
                    pendingLiters += liters
                }
                return@forEach
            }

            val previousFullOdometer = lastFullOdometer
            if (previousFullOdometer != null) {
                val distance = odometer - previousFullOdometer
                val totalLiters = pendingLiters + liters
                if (distance > 0 && totalLiters > 0) {
                    fuelEconomies += FuelEconomyAtOdometer(
                        odometer = odometer,
                        economy = (totalLiters / distance) * 100
                    )
                }
            }

            lastFullOdometer = odometer
            pendingLiters = 0.0
        }

        return fuelEconomies
    }

    private data class FuelEconomyAtOdometer(
        val odometer: Int,
        val economy: Double
    )
}
