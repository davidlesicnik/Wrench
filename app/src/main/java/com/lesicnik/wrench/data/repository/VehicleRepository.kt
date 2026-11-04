package com.lesicnik.wrench.data.repository

import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.data.repository.offline.toDomain
import com.lesicnik.wrench.data.repository.offline.toEntity
import com.lesicnik.wrench.data.sync.OfflineSyncEngine
import kotlinx.coroutines.CancellationException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface LoginVehicleSource {
    suspend fun fetchVehiclesRemote(serverUrl: String, apiKey: String): ApiResult<List<Vehicle>>
    suspend fun getCachedVehicles(serverUrl: String): List<Vehicle>
}

class VehicleRepository(
    private val database: WrenchDatabase,
    private val syncEngine: OfflineSyncEngine = OfflineSyncEngine(database)
) : LoginVehicleSource {

    override suspend fun getCachedVehicles(serverUrl: String): List<Vehicle> {
        return database.vehicleDao().getVehicles(serverUrl).map { it.toDomain() }
    }

    override suspend fun fetchVehiclesRemote(serverUrl: String, apiKey: String): ApiResult<List<Vehicle>> {
        return try {
            val api = NetworkModule.getApi(serverUrl)
            val response = api.getVehicles(apiKey)

            if (response.isSuccessful) {
                val vehicles = response.body().orEmpty()
                val now = System.currentTimeMillis()
                database.vehicleDao().upsertVehicles(
                    vehicles.map { it.toEntity(serverUrl, now) }
                )
                ApiResult.Success(vehicles)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Invalid API key"
                    403 -> "Access forbidden - check API key permissions"
                    404 -> "Server not found - check server URL"
                    in 300..399 -> "Server redirected the request - enter its final URL"
                    else -> "Server error: ${response.code()}"
                }
                val type = if (response.code() in listOf(401, 403)) ApiErrorType.AUTHENTICATION else ApiErrorType.OTHER
                ApiResult.Error(errorMessage, type)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val type = when (e) {
                is SocketException, is SocketTimeoutException, is UnknownHostException -> ApiErrorType.NETWORK
                else -> ApiErrorType.OTHER
            }
            ApiResult.Error(e.message ?: "Unknown error occurred", type)
        }
    }

    suspend fun getVehicles(serverUrl: String, apiKey: String): ApiResult<List<Vehicle>> {
        val localVehicles = getCachedVehicles(serverUrl)
        if (localVehicles.isNotEmpty()) {
            return ApiResult.Success(localVehicles)
        }

        return when (val remote = fetchVehiclesRemote(serverUrl, apiKey)) {
            is ApiResult.Success -> remote
            is ApiResult.Error -> {
                val fallback = getCachedVehicles(serverUrl)
                if (fallback.isNotEmpty()) ApiResult.Success(fallback) else remote
            }
        }
    }

    suspend fun syncNow(serverUrl: String, apiKey: String): ApiResult<Unit> {
        return syncEngine.syncServer(serverUrl, apiKey)
    }
}
