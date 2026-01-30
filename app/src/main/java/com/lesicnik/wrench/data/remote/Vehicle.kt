package com.lesicnik.wrench.data.remote

import com.google.gson.annotations.SerializedName

data class Vehicle(
    @SerializedName("id")
    val id: Int,
    @SerializedName("imageLocation")
    val imageLocation: String?,
    @SerializedName("year")
    val year: Int,
    @SerializedName("make")
    val make: String,
    @SerializedName("model")
    val model: String,
    @SerializedName("licensePlate")
    val licensePlate: String?,
    @SerializedName("soldDate")
    val soldDate: String?,
    @SerializedName("isElectric")
    val isElectric: Boolean,
    @SerializedName("useHours")
    val useHours: Boolean,
    @SerializedName("odometerUnit")
    val odometerUnit: String?
) {
    val displayName: String
        get() = "$year $make $model"

    val isSold: Boolean
        get() = !soldDate.isNullOrBlank()
}
