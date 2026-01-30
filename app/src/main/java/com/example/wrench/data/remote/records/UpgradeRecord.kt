package com.example.wrench.data.remote.records

import com.google.gson.annotations.SerializedName

data class UpgradeRecord(
    @SerializedName("id")
    val id: String,
    @SerializedName("date")
    val date: String,
    @SerializedName("odometer")
    val odometer: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("cost")
    val cost: String?,
    @SerializedName("notes")
    val notes: String?
)
