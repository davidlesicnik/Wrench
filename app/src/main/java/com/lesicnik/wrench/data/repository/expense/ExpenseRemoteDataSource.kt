package com.lesicnik.wrench.data.repository.expense

import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.remote.records.RepairRecord
import com.lesicnik.wrench.data.remote.records.ServiceRecord
import com.lesicnik.wrench.data.remote.records.TaxRecord
import com.lesicnik.wrench.data.remote.records.UpgradeRecord
import com.lesicnik.wrench.data.repository.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response

data class ExpenseRecordBundle(
    val serviceRecords: List<ServiceRecord>,
    val repairRecords: List<RepairRecord>,
    val upgradeRecords: List<UpgradeRecord>,
    val fuelRecords: List<FuelRecord>,
    val taxRecords: List<TaxRecord>
)

class ExpenseRemoteDataSource {

    suspend fun fetchAllExpenseRecords(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int
    ): ApiResult<ExpenseRecordBundle> {
        return try {
            val api = NetworkModule.getApi(serverUrl)

            val responses = coroutineScope {
                val serviceDeferred = async { api.getServiceRecords(apiKey, vehicleId) }
                val repairDeferred = async { api.getRepairRecords(apiKey, vehicleId) }
                val upgradeDeferred = async { api.getUpgradeRecords(apiKey, vehicleId) }
                val fuelDeferred = async { api.getFuelRecords(apiKey, vehicleId) }
                val taxDeferred = async { api.getTaxRecords(apiKey, vehicleId) }

                ExpenseResponses(
                    serviceRecords = serviceDeferred.await(),
                    repairRecords = repairDeferred.await(),
                    upgradeRecords = upgradeDeferred.await(),
                    fuelRecords = fuelDeferred.await(),
                    taxRecords = taxDeferred.await()
                )
            }

            val errors = mutableListOf<String>()
            errors.addIfFailed("service records", responses.serviceRecords)
            errors.addIfFailed("repair records", responses.repairRecords)
            errors.addIfFailed("upgrade records", responses.upgradeRecords)
            errors.addIfFailed("fuel records", responses.fuelRecords)
            errors.addIfFailed("tax records", responses.taxRecords)

            if (errors.isNotEmpty()) {
                ApiResult.Error("Failed to load expenses: ${errors.joinToString("; ")}")
            } else {
                ApiResult.Success(
                    ExpenseRecordBundle(
                        serviceRecords = responses.serviceRecords.body().orEmpty(),
                        repairRecords = responses.repairRecords.body().orEmpty(),
                        upgradeRecords = responses.upgradeRecords.body().orEmpty(),
                        fuelRecords = responses.fuelRecords.body().orEmpty(),
                        taxRecords = responses.taxRecords.body().orEmpty()
                    )
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun fetchFuelRecords(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int
    ): ApiResult<List<FuelRecord>> {
        return try {
            val api = NetworkModule.getApi(serverUrl)
            val response = api.getFuelRecords(apiKey, vehicleId)

            if (response.isSuccessful) {
                ApiResult.Success(response.body().orEmpty())
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                ApiResult.Error("Failed to get fuel records: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun addExpense(
        serverUrl: String,
        apiKey: String,
        vehicleId: Int,
        type: ExpenseType,
        date: String,
        odometer: String,
        description: String,
        cost: String,
        notes: String,
        fuelConsumed: String,
        isFillToFull: String,
        missedFuelUp: String,
        isRecurring: String
    ): ApiResult<Unit> {
        return try {
            val api = NetworkModule.getApi(serverUrl)
            val response = when (type) {
                ExpenseType.SERVICE -> api.addServiceRecord(apiKey, vehicleId, date, odometer, description, cost, notes)
                ExpenseType.REPAIR -> api.addRepairRecord(apiKey, vehicleId, date, odometer, description, cost, notes)
                ExpenseType.UPGRADE -> api.addUpgradeRecord(apiKey, vehicleId, date, odometer, description, cost, notes)
                ExpenseType.FUEL -> api.addFuelRecord(
                    apiKey = apiKey,
                    vehicleId = vehicleId,
                    date = date,
                    odometer = odometer,
                    fuelConsumed = fuelConsumed,
                    isFillToFull = isFillToFull,
                    missedFuelUp = missedFuelUp,
                    cost = cost,
                    notes = notes
                )

                ExpenseType.TAX -> api.addTaxRecord(apiKey, vehicleId, date, description, cost, isRecurring, notes)
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

    suspend fun deleteExpense(
        serverUrl: String,
        apiKey: String,
        expenseId: Int,
        type: ExpenseType
    ): ApiResult<Unit> {
        return try {
            val api = NetworkModule.getApi(serverUrl)
            val response = when (type) {
                ExpenseType.SERVICE -> api.deleteServiceRecord(apiKey, expenseId)
                ExpenseType.REPAIR -> api.deleteRepairRecord(apiKey, expenseId)
                ExpenseType.UPGRADE -> api.deleteUpgradeRecord(apiKey, expenseId)
                ExpenseType.FUEL -> api.deleteFuelRecord(apiKey, expenseId)
                ExpenseType.TAX -> api.deleteTaxRecord(apiKey, expenseId)
            }

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                ApiResult.Error("Failed to delete expense: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}

private data class ExpenseResponses(
    val serviceRecords: Response<List<ServiceRecord>>,
    val repairRecords: Response<List<RepairRecord>>,
    val upgradeRecords: Response<List<UpgradeRecord>>,
    val fuelRecords: Response<List<FuelRecord>>,
    val taxRecords: Response<List<TaxRecord>>
)

private fun <T> MutableList<String>.addIfFailed(label: String, response: Response<T>) {
    if (!response.isSuccessful) {
        val errorBody = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
        val detail = if (errorBody != null) "$label (${response.code()}): $errorBody" else "$label (${response.code()})"
        add(detail)
    }
}

