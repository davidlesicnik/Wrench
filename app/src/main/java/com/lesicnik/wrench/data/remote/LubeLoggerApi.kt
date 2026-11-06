package com.lesicnik.wrench.data.remote

import com.lesicnik.wrench.data.remote.records.FuelRecord
import com.lesicnik.wrench.data.remote.records.RepairRecord
import com.lesicnik.wrench.data.remote.records.ServiceRecord
import com.lesicnik.wrench.data.remote.records.TaxRecord
import com.lesicnik.wrench.data.remote.records.UpgradeRecord
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface LubeLoggerApi {

    @FormUrlEncoded
    @retrofit2.http.PUT("api/vehicle/reminders/update")
    suspend fun updateReminder(
        @Header("x-api-key") apiKey: String,
        @Field("id") id: Int,
        @Field("description") description: String,
        @Field("metric") metric: String,
        @Field("dueDate") dueDate: String?,
        @Field("dueOdometer") dueOdometer: Int?,
        @Field("notes") notes: String,
        @Field("tags") tags: String
    ): Response<ReminderWriteResponse>

    @DELETE("api/vehicle/reminders/delete")
    suspend fun deleteReminder(@Header("x-api-key") apiKey: String, @Query("id") id: Int): Response<ReminderWriteResponse>

    @FormUrlEncoded
    @POST("api/vehicle/servicerecords/add")
    suspend fun recordMaintenanceService(
        @Header("x-api-key") apiKey: String,
        @Field("vehicleId") vehicleId: Int,
        @Field("date") date: String,
        @Field("odometer") odometer: String,
        @Field("description") description: String,
        @Field("cost") cost: String,
        @Field("notes") notes: String
    ): Response<ReminderWriteResponse>

    @GET("api/vehicle/odometerrecords/latest")
    suspend fun getLatestOdometer(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int
    ): Response<Int>

    @FormUrlEncoded
    @POST("api/vehicle/reminders/add")
    suspend fun addReminder(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int,
        @Field("description") description: String,
        @Field("metric") metric: String,
        @Field("dueDate") dueDate: String?,
        @Field("dueOdometer") dueOdometer: Int?,
        @Field("notes") notes: String,
        @Field("tags") tags: String
    ): Response<ReminderWriteResponse>

    @Headers("culture-invariant: true")
    @GET("api/vehicle/reminders")
    suspend fun getReminders(
        @Header("x-api-key") apiKey: String,
        @Query("vehicleId") vehicleId: Int
    ): Response<List<ReminderRecord>>

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

    @FormUrlEncoded
    @POST("api/vehicle/servicerecords/add")
    suspend fun addServiceRecord(
        @Header("x-api-key") apiKey: String,
        @Field("vehicleId") vehicleId: Int,
        @Field("date") date: String,
        @Field("odometer") odometer: String,
        @Field("description") description: String,
        @Field("cost") cost: String,
        @Field("notes") notes: String
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/vehicle/repairrecords/add")
    suspend fun addRepairRecord(
        @Header("x-api-key") apiKey: String,
        @Field("vehicleId") vehicleId: Int,
        @Field("date") date: String,
        @Field("odometer") odometer: String,
        @Field("description") description: String,
        @Field("cost") cost: String,
        @Field("notes") notes: String
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/vehicle/upgraderecords/add")
    suspend fun addUpgradeRecord(
        @Header("x-api-key") apiKey: String,
        @Field("vehicleId") vehicleId: Int,
        @Field("date") date: String,
        @Field("odometer") odometer: String,
        @Field("description") description: String,
        @Field("cost") cost: String,
        @Field("notes") notes: String
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/vehicle/gasrecords/add")
    suspend fun addFuelRecord(
        @Header("x-api-key") apiKey: String,
        @Field("vehicleId") vehicleId: Int,
        @Field("date") date: String,
        @Field("odometer") odometer: String,
        @Field("fuelConsumed") fuelConsumed: String,
        @Field("isFillToFull") isFillToFull: String,
        @Field("missedFuelUp") missedFuelUp: String,
        @Field("cost") cost: String,
        @Field("notes") notes: String
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/vehicle/taxrecords/add")
    suspend fun addTaxRecord(
        @Header("x-api-key") apiKey: String,
        @Field("vehicleId") vehicleId: Int,
        @Field("date") date: String,
        @Field("description") description: String,
        @Field("cost") cost: String,
        @Field("isRecurring") isRecurring: String,
        @Field("notes") notes: String
    ): Response<Unit>

    @DELETE("api/vehicle/servicerecords/delete")
    suspend fun deleteServiceRecord(
        @Header("x-api-key") apiKey: String,
        @Query("id") id: Int
    ): Response<Unit>

    @DELETE("api/vehicle/repairrecords/delete")
    suspend fun deleteRepairRecord(
        @Header("x-api-key") apiKey: String,
        @Query("id") id: Int
    ): Response<Unit>

    @DELETE("api/vehicle/upgraderecords/delete")
    suspend fun deleteUpgradeRecord(
        @Header("x-api-key") apiKey: String,
        @Query("id") id: Int
    ): Response<Unit>

    @DELETE("api/vehicle/gasrecords/delete")
    suspend fun deleteFuelRecord(
        @Header("x-api-key") apiKey: String,
        @Query("id") id: Int
    ): Response<Unit>

    @DELETE("api/vehicle/taxrecords/delete")
    suspend fun deleteTaxRecord(
        @Header("x-api-key") apiKey: String,
        @Query("id") id: Int
    ): Response<Unit>

}
