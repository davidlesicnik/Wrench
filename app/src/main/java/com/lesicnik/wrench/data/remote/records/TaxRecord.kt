package com.lesicnik.wrench.data.remote.records

import com.google.gson.annotations.SerializedName

data class TaxRecord(
    @SerializedName("id")
    val id: String,
    @SerializedName("date")
    val date: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("cost")
    val cost: String?,
    @SerializedName("isRecurring")
    val isRecurring: String?,
    @SerializedName("notes")
    val notes: String?
)
