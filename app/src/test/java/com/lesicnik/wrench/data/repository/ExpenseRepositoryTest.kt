package com.lesicnik.wrench.data.repository

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ExpenseRepositoryTest {

    private lateinit var repository: ExpenseRepository

    @Before
    fun setup() {
        repository = ExpenseRepository()
    }

    // Date Parsing Tests

    @Test
    fun `parseDate handles LubeLogger format with spaces`() {
        val result = invokeParseDate("4. 02. 2025")
        assertEquals(LocalDate.of(2025, 2, 4), result)
    }

    @Test
    fun `parseDate handles LubeLogger format single digit month`() {
        val result = invokeParseDate("4. 2. 2025")
        assertEquals(LocalDate.of(2025, 2, 4), result)
    }

    @Test
    fun `parseDate handles LubeLogger format with leading zeros`() {
        val result = invokeParseDate("04. 02. 2025")
        assertEquals(LocalDate.of(2025, 2, 4), result)
    }

    @Test
    fun `parseDate handles ISO format`() {
        val result = invokeParseDate("2024-01-15")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles US format MM-dd-yyyy`() {
        val result = invokeParseDate("1/15/2024")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles US format with leading zeros`() {
        val result = invokeParseDate("01/15/2024")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles European format dd-MM-yyyy`() {
        val result = invokeParseDate("15/01/2024")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate handles ISO datetime format`() {
        val result = invokeParseDate("2024-01-15T10:30:00")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `parseDate returns today for malformed input`() {
        val result = invokeParseDate("not-a-date")
        assertEquals(LocalDate.now(), result)
    }

    @Test
    fun `parseDate handles empty string`() {
        val result = invokeParseDate("")
        assertEquals(LocalDate.now(), result)
    }

    // Number Parsing Tests

    @Test
    fun `parseNumber handles European format with comma decimal`() {
        val result = invokeParseNumber("1.234,56")
        assertEquals(1234.56, result, 0.01)
    }

    @Test
    fun `parseNumber handles US format with dot decimal`() {
        val result = invokeParseNumber("1,234.56")
        assertEquals(1234.56, result, 0.01)
    }

    @Test
    fun `parseNumber handles simple decimal with comma`() {
        val result = invokeParseNumber("123,45")
        assertEquals(123.45, result, 0.01)
    }

    @Test
    fun `parseNumber handles simple decimal with dot`() {
        val result = invokeParseNumber("123.45")
        assertEquals(123.45, result, 0.01)
    }

    @Test
    fun `parseNumber handles integer`() {
        val result = invokeParseNumber("1234")
        assertEquals(1234.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles thousand separator only comma`() {
        val result = invokeParseNumber("1,234")
        assertEquals(1234.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles null input`() {
        val result = invokeParseNumber(null)
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles empty string`() {
        val result = invokeParseNumber("")
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles blank string`() {
        val result = invokeParseNumber("   ")
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `parseNumber handles spaces in number`() {
        val result = invokeParseNumber("1 234.56")
        assertEquals(1234.56, result, 0.01)
    }

    @Test
    fun `parseNumber handles malformed input`() {
        val result = invokeParseNumber("not-a-number")
        assertEquals(0.0, result, 0.01)
    }

    // Mileage Parsing Tests

    @Test
    fun `parseMileage returns integer from valid input`() {
        val result = invokeParseMileage("12345")
        assertEquals(12345, result)
    }

    @Test
    fun `parseMileage handles decimal input`() {
        val result = invokeParseMileage("12345.67")
        assertEquals(12345, result)
    }

    @Test
    fun `parseMileage returns null for null input`() {
        val result = invokeParseMileage(null)
        assertNull(result)
    }

    @Test
    fun `parseMileage returns null for empty string`() {
        val result = invokeParseMileage("")
        assertNull(result)
    }

    @Test
    fun `parseMileage returns null for zero value`() {
        val result = invokeParseMileage("0")
        assertNull(result)
    }

    @Test
    fun `parseMileage handles European format`() {
        val result = invokeParseMileage("12.345")
        assertEquals(12345, result)
    }

    // Helper methods to access private functions via reflection

    private fun invokeParseDate(dateString: String): LocalDate {
        val method = ExpenseRepository::class.java.getDeclaredMethod("parseDate", String::class.java)
        method.isAccessible = true
        return method.invoke(repository, dateString) as LocalDate
    }

    private fun invokeParseNumber(value: String?): Double {
        val method = ExpenseRepository::class.java.getDeclaredMethod("parseNumber", String::class.java)
        method.isAccessible = true
        return method.invoke(repository, value) as Double
    }

    private fun invokeParseMileage(value: String?): Int? {
        val method = ExpenseRepository::class.java.getDeclaredMethod("parseMileage", String::class.java)
        method.isAccessible = true
        return method.invoke(repository, value) as Int?
    }
}
