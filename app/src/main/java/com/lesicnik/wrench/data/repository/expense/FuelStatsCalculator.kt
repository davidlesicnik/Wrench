package com.lesicnik.wrench.data.repository.expense

import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.repository.FuelStatistics

class FuelStatsCalculator {

    fun computeFuelEconomyByOdometer(
        fuelRecords: List<FuelRecord>,
        parseMileage: (String?) -> Int?,
        parseNumber: (String?) -> Double
    ): Map<Int, Double> {
        val sortedFuel = fuelRecords
            .filter { parseMileage(it.odometer) != null }
            .sortedBy { parseMileage(it.odometer) }

        val odometerToFuelEconomy = mutableMapOf<Int, Double>()
        for (i in 1 until sortedFuel.size) {
            val current = sortedFuel[i]
            val previous = sortedFuel[i - 1]

            val currentOdo = parseMileage(current.odometer) ?: continue
            val prevOdo = parseMileage(previous.odometer) ?: continue
            val liters = current.fuelConsumed?.let { parseNumber(it) } ?: continue

            val distance = currentOdo - prevOdo
            if (distance > 0 && liters > 0) {
                val economy = (liters / distance) * 100
                odometerToFuelEconomy[currentOdo] = economy
            }
        }

        return odometerToFuelEconomy
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

        val fuelEconomies = mutableListOf<Double>()
        var lastFuelEconomy: Double? = null

        for (i in 1 until sortedFuel.size) {
            val current = sortedFuel[i]
            val previous = sortedFuel[i - 1]

            val currentOdo = parseMileage(current.odometer) ?: continue
            val prevOdo = parseMileage(previous.odometer) ?: continue
            val liters = current.fuelConsumed?.let { parseNumber(it) } ?: continue

            val distance = currentOdo - prevOdo
            if (distance > 0 && liters > 0) {
                val economy = (liters / distance) * 100
                fuelEconomies.add(economy)
                lastFuelEconomy = economy
            }
        }

        val averageFuelEconomy = fuelEconomies.takeIf { it.isNotEmpty() }?.average()
        val lastOdometer = sortedFuel.lastOrNull()?.let { parseMileage(it.odometer) }

        return FuelStatistics(
            averageFuelConsumption = averageFuelEconomy,
            lastFuelConsumption = lastFuelEconomy,
            lastOdometer = lastOdometer
        )
    }
}

