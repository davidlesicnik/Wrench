package com.example.wrench.data.remote

import com.example.wrench.data.remote.records.FuelRecord
import com.example.wrench.data.remote.records.RepairRecord
import com.example.wrench.data.remote.records.ServiceRecord
import com.example.wrench.data.remote.records.TaxRecord
import com.example.wrench.data.remote.records.UpgradeRecord
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface LubeLoggerApi {

    @GET("api/vehicles")
    suspend fun getVehicles(
        @Header("x-api-key") apiKey: String
    ): Response<List<Vehicle>>

    @GET("api/vehicle/servicerecords")
    suspend fun getServiceRecords(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int
    ): Response<List<ServiceRecord>>

    @GET("api/vehicle/repairrecords")
    suspend fun getRepairRecords(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int
    ): Response<List<RepairRecord>>

    @GET("api/vehicle/upgraderecords")
    suspend fun getUpgradeRecords(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int
    ): Response<List<UpgradeRecord>>

    @GET("api/vehicle/gasrecords")
    suspend fun getFuelRecords(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int
    ): Response<List<FuelRecord>>

    @GET("api/vehicle/taxrecords")
    suspend fun getTaxRecords(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int
    ): Response<List<TaxRecord>>

    companion object {
        fun create(baseUrl: String): LubeLoggerApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LubeLoggerApi::class.java)
        }
    }
}
