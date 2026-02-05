package com.lesicnik.wrench.data.repository.expense

import com.lesicnik.wrench.data.remote.records.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelStatsCalculatorTest {

    private val mapper = ExpenseMapper()
    private val calculator = FuelStatsCalculator()

    @Test
    fun computeFuelEconomyByOdometer_calculates_l_per_100km() {
        val records = listOf(
            FuelRecord(id = "1", date = "2024-01-01", odometer = "1000", fuelConsumed = "10", isFillToFull = "true", cost = "0", notes = null),
            FuelRecord(id = "2", date = "2024-01-02", odometer = "1100", fuelConsumed = "5", isFillToFull = "true", cost = "0", notes = null),
        )

        val map = calculator.computeFuelEconomyByOdometer(
            fuelRecords = records,
            parseMileage = mapper::parseMileage,
            parseNumber = mapper::parseNumber
        )

        // 5 liters over 100km => 5.0 L/100km
        assertEquals(5.0, map[1100] ?: 0.0, 0.0001)
    }

    @Test
    fun computeFuelStatistics_handles_insufficient_data() {
        val records = listOf(
            FuelRecord(id = "1", date = "2024-01-01", odometer = "1000", fuelConsumed = "10", isFillToFull = "true", cost = "0", notes = null),
        )

        val stats = calculator.computeFuelStatistics(
            fuelRecords = records,
            parseMileage = mapper::parseMileage,
            parseNumber = mapper::parseNumber
        )

        assertNull(stats.averageFuelConsumption)
        assertNull(stats.lastFuelConsumption)
        assertEquals(1000, stats.lastOdometer)
    }
}

