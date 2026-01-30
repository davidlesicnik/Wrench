package com.lesicnik.wrench.data.repository

import com.lesicnik.wrench.data.remote.LubeLoggerApi
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.remote.records.RepairRecord
import com.lesicnik.wrench.data.remote.records.ServiceRecord
import com.lesicnik.wrench.data.remote.records.TaxRecord
import com.lesicnik.wrench.data.remote.records.UpgradeRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class ExpenseRepository {

    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("d. MM. yyyy"),           // 4. 02. 2025 (LubeLogger format)
        DateTimeFormatter.ofPattern("d. M. yyyy"),            // 4. 2. 2025
        DateTimeFormatter.ofPattern("dd. MM. yyyy"),          // 04. 02. 2025
        DateTimeFormatter.ISO_LOCAL_DATE,                     // 2024-01-15
        DateTimeFormatter.ofPattern("M/d/yyyy"),              // 1/15/2024
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),            // 01/15/2024
        DateTimeFormatter.ofPattern("d/M/yyyy"),              // 15/1/2024
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),            // 15/01/2024
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),            // 2024/01/15
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),   // Jan 15, 2024
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),  // January 15, 2024
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),    // 15 Jan 2024
    )

    suspend fun getExpenses(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int
    ): ApiResult<List<Expense>> {
        return try {
            val api = LubeLoggerApi.create(serverUrl)

            val expenses = coroutineScope {
                val serviceDeferred = async { api.getServiceRecords(apiKey, vehicleId) }
                val repairDeferred = async { api.getRepairRecords(apiKey, vehicleId) }
                val upgradeDeferred = async { api.getUpgradeRecords(apiKey, vehicleId) }
                val fuelDeferred = async { api.getFuelRecords(apiKey, vehicleId) }
                val taxDeferred = async { api.getTaxRecords(apiKey, vehicleId) }

                val serviceRecords = serviceDeferred.await()
                val repairRecords = repairDeferred.await()
                val upgradeRecords = upgradeDeferred.await()
                val fuelRecords = fuelDeferred.await()
                val taxRecords = taxDeferred.await()

                val allExpenses = mutableListOf<Expense>()

                if (serviceRecords.isSuccessful) {
                    serviceRecords.body()?.forEach { record ->
                        allExpenses.add(record.toExpense())
                    }
                }

                if (repairRecords.isSuccessful) {
                    repairRecords.body()?.forEach { record ->
                        allExpenses.add(record.toExpense())
                    }
                }

                if (upgradeRecords.isSuccessful) {
                    upgradeRecords.body()?.forEach { record ->
                        allExpenses.add(record.toExpense())
                    }
                }

                if (fuelRecords.isSuccessful) {
                    val fuelList = fuelRecords.body() ?: emptyList()
                    // Sort by odometer ascending to calculate fuel economy
                    val sortedFuel = fuelList
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

                    fuelList.forEach { record ->
                        val odometer = parseMileage(record.odometer)
                        val economy = odometer?.let { odometerToFuelEconomy[it] }
                        allExpenses.add(record.toExpense(economy))
                    }
                }

                if (taxRecords.isSuccessful) {
                    taxRecords.body()?.forEach { record ->
                        allExpenses.add(record.toExpense())
                    }
                }

                allExpenses
            }

            ApiResult.Success(expenses.sortedByDescending { it.date })
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    private fun parseDate(dateString: String): LocalDate {
        val cleanedDate = dateString.trim()

        for (formatter in dateFormatters) {
            try {
                return LocalDate.parse(cleanedDate, formatter)
            } catch (_: DateTimeParseException) {
                // Try next formatter
            }

            // Also try parsing just the date part (before T) for datetime formats
            if (cleanedDate.contains("T")) {
                try {
                    return LocalDate.parse(cleanedDate.substringBefore("T"), formatter)
                } catch (_: DateTimeParseException) {
                    // Try next formatter
                }
            }
        }

        // Last resort: try to extract date components with regex
        val isoMatch = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(cleanedDate)
        if (isoMatch != null) {
            try {
                val (year, month, day) = isoMatch.destructured
                return LocalDate.of(year.toInt(), month.toInt(), day.toInt())
            } catch (_: Exception) {
                // Fall through
            }
        }

        return LocalDate.now()
    }

    private fun parseNumber(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        // Remove any thousand separators and handle both comma and dot as decimal
        val cleaned = value.replace(" ", "").trim()
        // If there's both comma and dot, the last one is the decimal separator
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')

        return when {
            lastComma > lastDot -> {
                // Comma is decimal separator (European format: 1.234,56)
                cleaned.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
            }
            lastDot > lastComma -> {
                // Dot is decimal separator (US format: 1,234.56)
                cleaned.replace(",", "").toDoubleOrNull() ?: 0.0
            }
            lastComma >= 0 -> {
                // Only comma present - could be decimal or thousand separator
                // If 3 digits after comma, treat as thousand separator
                val afterComma = cleaned.substringAfter(",")
                if (afterComma.length == 3 && !afterComma.contains(",")) {
                    cleaned.replace(",", "").toDoubleOrNull() ?: 0.0
                } else {
                    cleaned.replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }
            else -> {
                cleaned.toDoubleOrNull() ?: 0.0
            }
        }
    }

    private fun parseMileage(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val parsed = parseNumber(value).toInt()
        return if (parsed == 0) null else parsed
    }

    private fun ServiceRecord.toExpense() = Expense(
        id = id.toIntOrNull() ?: 0,
        type = ExpenseType.SERVICE,
        date = parseDate(date),
        cost = parseNumber(cost),
        odometer = parseMileage(odometer),
        description = description ?: "Service",
        notes = notes
    )

    private fun RepairRecord.toExpense() = Expense(
        id = id.toIntOrNull() ?: 0,
        type = ExpenseType.REPAIR,
        date = parseDate(date),
        cost = parseNumber(cost),
        odometer = parseMileage(odometer),
        description = description ?: "Repair",
        notes = notes
    )

    private fun UpgradeRecord.toExpense() = Expense(
        id = id.toIntOrNull() ?: 0,
        type = ExpenseType.UPGRADE,
        date = parseDate(date),
        cost = parseNumber(cost),
        odometer = parseMileage(odometer),
        description = description ?: "Upgrade",
        notes = notes
    )

    private fun FuelRecord.toExpense(fuelEconomy: Double? = null) = Expense(
        id = id.toIntOrNull() ?: 0,
        type = ExpenseType.FUEL,
        date = parseDate(date),
        cost = parseNumber(cost),
        odometer = parseMileage(odometer),
        description = "Fuel",
        notes = notes,
        liters = fuelConsumed?.let { parseNumber(it).takeIf { v -> v > 0 } },
        fuelEconomy = fuelEconomy
    )

    private fun TaxRecord.toExpense() = Expense(
        id = id.toIntOrNull() ?: 0,
        type = ExpenseType.TAX,
        date = parseDate(date),
        cost = parseNumber(cost),
        odometer = null,
        description = description ?: "Tax",
        notes = notes
    )

    suspend fun addExpense(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        type: ExpenseType,
        date: LocalDate,
        odometer: Int?,
        description: String,
        cost: Double,
        notes: String,
        fuelConsumed: Double? = null,
        isFillToFull: Boolean = false,
        isMissedFuelUp: Boolean = false,
        isRecurring: Boolean = false
    ): ApiResult<Unit> {
        return try {
            val api = LubeLoggerApi.create(serverUrl)
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val odometerString = odometer?.toString() ?: ""
            val costString = String.format(java.util.Locale.US, "%.2f", cost)

            val response = when (type) {
                ExpenseType.SERVICE -> api.addServiceRecord(
                    apiKey = apiKey,
                    vehicleId = vehicleId,
                    date = dateString,
                    odometer = odometerString,
                    description = description,
                    cost = costString,
                    notes = notes
                )
                ExpenseType.REPAIR -> api.addRepairRecord(
                    apiKey = apiKey,
                    vehicleId = vehicleId,
                    date = dateString,
                    odometer = odometerString,
                    description = description,
                    cost = costString,
                    notes = notes
                )
                ExpenseType.UPGRADE -> api.addUpgradeRecord(
                    apiKey = apiKey,
                    vehicleId = vehicleId,
                    date = dateString,
                    odometer = odometerString,
                    description = description,
                    cost = costString,
                    notes = notes
                )
                ExpenseType.FUEL -> api.addFuelRecord(
                    apiKey = apiKey,
                    vehicleId = vehicleId,
                    date = dateString,
                    odometer = odometerString,
                    fuelConsumed = fuelConsumed?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "",
                    isFillToFull = isFillToFull.toString(),
                    missedFuelUp = isMissedFuelUp.toString(),
                    cost = costString,
                    notes = notes
                )
                ExpenseType.TAX -> api.addTaxRecord(
                    apiKey = apiKey,
                    vehicleId = vehicleId,
                    date = dateString,
                    description = description,
                    cost = costString,
                    isRecurring = isRecurring.toString(),
                    notes = notes
                )
            }

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                ApiResult.Error("Failed to add expense: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}
