package com.lesicnik.wrench.data.repository

import com.lesicnik.wrench.data.remote.LubeLoggerApi
import com.lesicnik.wrench.data.remote.Vehicle

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

class VehicleRepository {

    suspend fun getVehicles(serverUrl: String, apiKey: String): ApiResult<List<Vehicle>> {
        return try {
            val api = LubeLoggerApi.create(serverUrl)
            val response = api.getVehicles(apiKey)

            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: emptyList())
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Invalid API key"
                    403 -> "Access forbidden - check API key permissions"
                    404 -> "Server not found - check server URL"
                    else -> "Server error: ${response.code()}"
                }
                ApiResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}
