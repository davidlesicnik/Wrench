package com.lesicnik.wrench.data.repository

import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.expense.ExpenseCache
import com.lesicnik.wrench.data.repository.expense.ExpenseMapper
import com.lesicnik.wrench.data.repository.expense.ExpenseRemoteDataSource
import com.lesicnik.wrench.data.repository.expense.FuelStatsCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExpenseRepository(
    private val cache: ExpenseCache = ExpenseCache(),
    private val remoteDataSource: ExpenseRemoteDataSource = ExpenseRemoteDataSource(),
    private val mapper: ExpenseMapper = ExpenseMapper(),
    private val fuelStatsCalculator: FuelStatsCalculator = FuelStatsCalculator()
) {

    fun getCachedExpenses(vehicleId: Int): List<Expense>? = cache.getExpenses(vehicleId)

    fun getCachedFuelStatistics(vehicleId: Int): FuelStatistics? = cache.getFuelStatistics(vehicleId)

    fun getExpenseById(vehicleId: Int, expenseId: Int, type: ExpenseType): Expense? {
        return cache.getExpenses(vehicleId)?.find { it.id == expenseId && it.type == type }
    }

    fun invalidateCache(vehicleId: Int) {
        cache.invalidate(vehicleId)
    }

    fun clearAllCaches() {
        cache.clearAll()
    }

    suspend fun preloadVehicleData(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int
    ) {
        if (cache.getExpenses(vehicleId) != null && cache.getFuelStatistics(vehicleId) != null) {
            return
        }
        if (!cache.tryBeginPreload(vehicleId)) {
            return
        }

        try {
            coroutineScope {
                val expensesDeferred = async {
                    if (cache.getExpenses(vehicleId) == null) {
                        getExpenses(serverUrl, apiKey, vehicleId)
                    }
                }
                val statsDeferred = async {
                    if (cache.getFuelStatistics(vehicleId) == null) {
                        getFuelStatistics(serverUrl, apiKey, vehicleId)
                    }
                }
                expensesDeferred.await()
                statsDeferred.await()
            }
        } finally {
            cache.endPreload(vehicleId)
        }
    }

    suspend fun getExpenses(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        forceRefresh: Boolean = false
    ): ApiResult<List<Expense>> {
        if (!forceRefresh) {
            cache.getExpenses(vehicleId)?.let { cached ->
                return ApiResult.Success(cached)
            }
        }

        return when (val remoteResult = remoteDataSource.fetchAllExpenseRecords(serverUrl, apiKey, vehicleId)) {
            is ApiResult.Error -> remoteResult
            is ApiResult.Success -> {
                try {
                    val bundle = remoteResult.data
                    val fuelEconomyByOdometer = fuelStatsCalculator.computeFuelEconomyByOdometer(
                        fuelRecords = bundle.fuelRecords,
                        parseMileage = mapper::parseMileage,
                        parseNumber = mapper::parseNumber
                    )

                    val allExpenses = buildList {
                        bundle.serviceRecords.forEach { add(mapper.mapServiceRecord(it)) }
                        bundle.repairRecords.forEach { add(mapper.mapRepairRecord(it)) }
                        bundle.upgradeRecords.forEach { add(mapper.mapUpgradeRecord(it)) }
                        bundle.fuelRecords.forEach { record ->
                            val odometer = mapper.parseMileage(record.odometer)
                            val economy = odometer?.let { fuelEconomyByOdometer[it] }
                            add(mapper.mapFuelRecord(record, economy))
                        }
                        bundle.taxRecords.forEach { add(mapper.mapTaxRecord(it)) }
                    }

                    val sorted = allExpenses.sortedByDescending { it.date }
                    cache.putExpenses(vehicleId, sorted)
                    ApiResult.Success(sorted)
                } catch (e: Exception) {
                    ApiResult.Error(e.message ?: "Unknown error occurred")
                }
            }
        }
    }

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
        val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val odometerString = odometer?.toString() ?: ""
        val costString = String.format(Locale.getDefault(), "%.2f", cost)
        val fuelConsumedString = fuelConsumed?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: ""

        val result = remoteDataSource.addExpense(
            serverUrl = serverUrl,
            apiKey = apiKey,
            vehicleId = vehicleId,
            type = type,
            date = dateString,
            odometer = odometerString,
            description = description,
            cost = costString,
            notes = notes,
            fuelConsumed = fuelConsumedString,
            isFillToFull = isFillToFull.toString(),
            missedFuelUp = isMissedFuelUp.toString(),
            isRecurring = isRecurring.toString()
        )

        if (result is ApiResult.Success) {
            cache.invalidate(vehicleId)
        }

        return result
    }

    suspend fun deleteExpense(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        expenseId: Int,
        type: ExpenseType
    ): ApiResult<Unit> {
        val result = remoteDataSource.deleteExpense(
            serverUrl = serverUrl,
            apiKey = apiKey,
            expenseId = expenseId,
            type = type
        )

        if (result is ApiResult.Success) {
            cache.invalidate(vehicleId)
        }

        return result
    }

    suspend fun updateExpense(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        originalExpenseId: Int,
        originalType: ExpenseType,
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
        val deleteResult = deleteExpense(serverUrl, apiKey, vehicleId, originalExpenseId, originalType)
        if (deleteResult is ApiResult.Error) {
            return deleteResult
        }

        return addExpense(
            serverUrl = serverUrl,
            apiKey = apiKey,
            vehicleId = vehicleId,
            type = type,
            date = date,
            odometer = odometer,
            description = description,
            cost = cost,
            notes = notes,
            fuelConsumed = fuelConsumed,
            isFillToFull = isFillToFull,
            isMissedFuelUp = isMissedFuelUp,
            isRecurring = isRecurring
        )
    }

    suspend fun getFuelStatistics(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        forceRefresh: Boolean = false
    ): ApiResult<FuelStatistics> {
        if (!forceRefresh) {
            cache.getFuelStatistics(vehicleId)?.let { cached ->
                return ApiResult.Success(cached)
            }
        }

        return when (val fuelResult = remoteDataSource.fetchFuelRecords(serverUrl, apiKey, vehicleId)) {
            is ApiResult.Error -> ApiResult.Error(fuelResult.message)
            is ApiResult.Success -> {
                val stats = fuelStatsCalculator.computeFuelStatistics(
                    fuelRecords = fuelResult.data,
                    parseMileage = mapper::parseMileage,
                    parseNumber = mapper::parseNumber
                )
                cache.putFuelStatistics(vehicleId, stats)
                ApiResult.Success(stats)
            }
        }
    }
}

