package com.lesicnik.wrench.data.repository.expense

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.remote.records.RepairRecord
import com.lesicnik.wrench.data.remote.records.ServiceRecord
import com.lesicnik.wrench.data.remote.records.TaxRecord
import com.lesicnik.wrench.data.remote.records.UpgradeRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class ExpenseMapper {

    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("d. MM. yyyy"), // 4. 02. 2025 (LubeLogger format)
        DateTimeFormatter.ofPattern("d. M. yyyy"), // 4. 2. 2025
        DateTimeFormatter.ofPattern("dd. MM. yyyy"), // 04. 02. 2025
        DateTimeFormatter.ISO_LOCAL_DATE, // 2024-01-15
        DateTimeFormatter.ofPattern("M/d/yyyy"), // 1/15/2024
        DateTimeFormatter.ofPattern("MM/dd/yyyy"), // 01/15/2024
        DateTimeFormatter.ofPattern("d/M/yyyy"), // 15/1/2024
        DateTimeFormatter.ofPattern("dd/MM/yyyy"), // 15/01/2024
        DateTimeFormatter.ofPattern("yyyy/MM/dd"), // 2024/01/15
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH), // Jan 15, 2024
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH), // January 15, 2024
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH), // 15 Jan 2024
    )

    fun mapServiceRecord(record: ServiceRecord): Expense = Expense(
        id = record.id.toIntOrNull() ?: 0,
        type = ExpenseType.SERVICE,
        date = parseDate(record.date),
        cost = parseCost(record.cost),
        odometer = parseMileage(record.odometer),
        description = record.description ?: "Service",
        notes = record.notes
    )

    fun mapRepairRecord(record: RepairRecord): Expense = Expense(
        id = record.id.toIntOrNull() ?: 0,
        type = ExpenseType.REPAIR,
        date = parseDate(record.date),
        cost = parseCost(record.cost),
        odometer = parseMileage(record.odometer),
        description = record.description ?: "Repair",
        notes = record.notes
    )

    fun mapUpgradeRecord(record: UpgradeRecord): Expense = Expense(
        id = record.id.toIntOrNull() ?: 0,
        type = ExpenseType.UPGRADE,
        date = parseDate(record.date),
        cost = parseCost(record.cost),
        odometer = parseMileage(record.odometer),
        description = record.description ?: "Upgrade",
        notes = record.notes
    )

    fun mapFuelRecord(record: FuelRecord, fuelEconomy: Double?): Expense = Expense(
        id = record.id.toIntOrNull() ?: 0,
        type = ExpenseType.FUEL,
        date = parseDate(record.date),
        cost = parseCost(record.cost),
        odometer = parseMileage(record.odometer),
        description = "Fuel",
        notes = record.notes,
        liters = record.fuelConsumed?.let { parseNumber(it).takeIf { v -> v > 0 } },
        fuelEconomy = fuelEconomy
    )

    fun mapTaxRecord(record: TaxRecord): Expense = Expense(
        id = record.id.toIntOrNull() ?: 0,
        type = ExpenseType.TAX,
        date = parseDate(record.date),
        cost = parseCost(record.cost),
        odometer = null,
        description = record.description ?: "Tax",
        notes = record.notes
    )

    fun parseMileage(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val parsed = parseNumber(value).toInt()
        return if (parsed == 0) null else parsed
    }

    fun parseNumber(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        val cleaned = value.replace(" ", "").trim()
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')

        val hasComma = lastComma >= 0
        val hasDot = lastDot >= 0

        // If both comma and dot are present, assume the last one is the decimal separator.
        if (hasComma && hasDot) {
            return if (lastComma > lastDot) {
                cleaned.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
            } else {
                cleaned.replace(",", "").toDoubleOrNull() ?: 0.0
            }
        }

        if (hasComma) {
            val afterComma = cleaned.substringAfterLast(",")
            val commaCount = cleaned.count { it == ',' }
            val shouldTreatCommasAsThousands = afterComma.length == 3 && commaCount >= 1
            return if (shouldTreatCommasAsThousands) {
                cleaned.replace(",", "").toDoubleOrNull() ?: 0.0
            } else {
                cleaned.replace(",", ".").toDoubleOrNull() ?: 0.0
            }
        }

        if (hasDot) {
            val afterDot = cleaned.substringAfterLast(".")
            val dotCount = cleaned.count { it == '.' }
            val shouldTreatDotsAsThousands = afterDot.length == 3 && dotCount >= 1
            return if (shouldTreatDotsAsThousands) {
                cleaned.replace(".", "").toDoubleOrNull() ?: 0.0
            } else {
                cleaned.toDoubleOrNull() ?: 0.0
            }
        }

        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun parseCost(value: String?): Double = parseNumber(value)

    fun parseDate(dateString: String): LocalDate {
        val cleanedDate = dateString.trim()
        if (cleanedDate.isBlank()) {
            throw IllegalArgumentException("Date is blank")
        }

        for (formatter in dateFormatters) {
            try {
                return LocalDate.parse(cleanedDate, formatter)
            } catch (_: DateTimeParseException) {
                // Try next formatter
            }

            if (cleanedDate.contains("T")) {
                try {
                    return LocalDate.parse(cleanedDate.substringBefore("T"), formatter)
                } catch (_: DateTimeParseException) {
                    // Try next formatter
                }
            }
        }

        val isoMatch = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(cleanedDate)
        if (isoMatch != null) {
            try {
                val (year, month, day) = isoMatch.destructured
                return LocalDate.of(year.toInt(), month.toInt(), day.toInt())
            } catch (_: Exception) {
                // Fall through
            }
        }

        throw IllegalArgumentException("Unsupported date format: $cleanedDate")
    }
}
