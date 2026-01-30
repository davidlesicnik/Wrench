package com.lesicnik.wrench.data.remote.records

import com.google.gson.annotations.SerializedName

data class FuelRecord(
    @SerializedName("id")
    val id: String,
    @SerializedName("date")
    val date: String,
    @SerializedName("odometer")
    val odometer: String?,
    @SerializedName("fuelConsumed")
    val fuelConsumed: String?,
    @SerializedName("isFillToFull")
    val isFillToFull: String?,
    @SerializedName("cost")
    val cost: String?,
    @SerializedName("notes")
    val notes: String?
)
