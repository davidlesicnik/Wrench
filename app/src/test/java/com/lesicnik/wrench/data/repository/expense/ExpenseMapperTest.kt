package com.lesicnik.wrench.data.repository.expense

import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.remote.records.ServiceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpenseMapperTest {

    private val mapper = ExpenseMapper()

    @Test
    fun mapServiceRecord_parses_lubelogger_date_format() {
        val record = ServiceRecord(
            id = "1",
            date = "4. 02. 2025",
            odometer = "12345",
            description = "Oil change",
            cost = "45.55",
            notes = "done"
        )

        val expense = mapper.mapServiceRecord(record)
        assertEquals(LocalDate.of(2025, 2, 4), expense.date)
        assertEquals(12345, expense.odometer)
        assertEquals(45.55, expense.cost, 0.0001)
    }

    @Test
    fun parseMileage_returns_null_for_zero() {
        assertNull(mapper.parseMileage("0"))
        assertNull(mapper.parseMileage("0.0"))
        assertNull(mapper.parseMileage(null))
    }

    @Test
    fun mapFuelRecord_parses_european_number_formats() {
        val record = FuelRecord(
            id = "10",
            date = "2024-01-15",
            odometer = "1.234",
            fuelConsumed = "31,98",
            isFillToFull = "true",
            cost = "1.234,56",
            notes = null
        )

        val expense = mapper.mapFuelRecord(record, fuelEconomy = null)
        assertEquals(1234, expense.odometer)
        assertEquals(31.98, expense.liters ?: 0.0, 0.0001)
        assertEquals(1234.56, expense.cost, 0.0001)
    }
}

