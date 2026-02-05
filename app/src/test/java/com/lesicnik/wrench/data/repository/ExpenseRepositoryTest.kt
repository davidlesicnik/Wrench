package com.lesicnik.wrench.data.repository

import com.lesicnik.wrench.data.repository.expense.ExpenseMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ExpenseRepositoryTest {

    private lateinit var mapper: ExpenseMapper

    @Before
    fun setup() {
        mapper = ExpenseMapper()
    }

    // Date Parsing Tests

    @Test
    fun `parseDate handles LubeLogger format with spaces`() {
        val result = mapper.parseDate("4. 02. 2025")
        assertEquals(LocalDate.of(2025, 2, 4), result)
    }

    @Test
    fun `parseDate handles LubeLogger format single digit month`() {
        val result = mapper.parseDate("4. 2. 2025")
        assertEquals(LocalDate.of(2025, 2, 4), result)
    }

    @Test
    fun `parseDate handles LubeLogger format with leading zeros`() {
        val result = mapper.parseDate("04. 02. 2025")
        assertEquals(LocalDate.of(2025, 2, 4), result)
    }

    @Test
    fun `parseDate handles ISO format`() {
        val result = mapper.parseDate("2024-01-15")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles US format MM-dd-yyyy`() {
        val result = mapper.parseDate("1/15/2024")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles US format with leading zeros`() {
        val result = mapper.parseDate("01/15/2024")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles European format dd-MM-yyyy`() {
        val result = mapper.parseDate("15/01/2024")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles ISO datetime format`() {
        val result = mapper.parseDate("2024-01-15T10:30:00")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate throws for malformed input`() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.parseDate("not-a-date")
        }
    }

    @Test
    fun `parseDate throws for empty string`() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.parseDate("")
        }
    }

    // Number Parsing Tests

    @Test
    fun `parseNumber handles European format with comma decimal`() {
        val result = mapper.parseNumber("1.234,56")
        assertEquals(1234.56, result, 0.01)
    }

    @Test
    fun `parseNumber handles US format with dot decimal`() {
        val result = mapper.parseNumber("1,234.56")
        assertEquals(1234.56, result, 0.01)
    }

    @Test
    fun `parseNumber handles simple decimal with comma`() {
        val result = mapper.parseNumber("123,45")
        assertEquals(123.45, result, 0.01)
    }

    @Test
    fun `parseNumber handles simple decimal with dot`() {
        val result = mapper.parseNumber("123.45")
        assertEquals(123.45, result, 0.01)
    }

    @Test
    fun `parseNumber handles integer`() {
        val result = mapper.parseNumber("1234")
        assertEquals(1234.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles thousand separator only comma`() {
        val result = mapper.parseNumber("1,234")
        assertEquals(1234.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles null input`() {
        val result = mapper.parseNumber(null)
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles empty string`() {
        val result = mapper.parseNumber("")
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles blank string`() {
        val result = mapper.parseNumber("   ")
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles spaces in number`() {
        val result = mapper.parseNumber("1 234.56")
        assertEquals(1234.56, result, 0.01)
    }

    @Test
    fun `parseNumber handles malformed input`() {
        val result = mapper.parseNumber("not-a-number")
        assertEquals(0.0, result, 0.01)
    }

    // Mileage Parsing Tests

    @Test
    fun `parseMileage returns integer from valid input`() {
        val result = mapper.parseMileage("12345")
        assertEquals(12345, result)
    }

    @Test
    fun `parseMileage handles decimal input`() {
        val result = mapper.parseMileage("12345.67")
        assertEquals(12345, result)
    }

    @Test
    fun `parseMileage returns null for null input`() {
        val result = mapper.parseMileage(null)
        assertNull(result)
    }

    @Test
    fun `parseMileage returns null for empty string`() {
        val result = mapper.parseMileage("")
        assertNull(result)
    }

    @Test
    fun `parseMileage returns null for zero value`() {
        val result = mapper.parseMileage("0")
        assertNull(result)
    }

    @Test
    fun `parseMileage handles European format`() {
        val result = mapper.parseMileage("12.345")
        assertEquals(12345, result)
    }
}
