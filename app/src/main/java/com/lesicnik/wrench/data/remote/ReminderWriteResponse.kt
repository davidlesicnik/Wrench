package com.lesicnik.wrench.data.remote

import com.google.gson.annotations.SerializedName

data class ReminderWriteResponse(
    @SerializedName(value = "success", alternate = ["Success"]) val success: Boolean? = null,
    @SerializedName(value = "additionalData", alternate = ["AdditionalData"]) val additionalData: ReminderWriteData? = null
)

data class ReminderWriteData(
    @SerializedName(value = "recordId", alternate = ["RecordId"]) val recordId: Int? = null
)
