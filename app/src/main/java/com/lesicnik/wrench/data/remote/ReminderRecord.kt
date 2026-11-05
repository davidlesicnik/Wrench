package com.lesicnik.wrench.data.remote

import com.google.gson.annotations.SerializedName

/** Invariant API responses may contain numbers or strings; Gson accepts both as strings. */
data class ReminderRecord(
    @SerializedName(value = "id", alternate = ["Id"]) val id: String? = null,
    @SerializedName(value = "vehicleId", alternate = ["VehicleId"]) val vehicleId: String? = null,
    @SerializedName(value = "description", alternate = ["Description"]) val description: String? = null,
    @SerializedName(value = "userMetric", alternate = ["UserMetric"]) val userMetric: String? = null,
    @SerializedName(value = "metric", alternate = ["Metric"]) val metric: String? = null,
    @SerializedName(value = "urgency", alternate = ["Urgency"]) val urgency: String? = null,
    @SerializedName(value = "dueDate", alternate = ["DueDate"]) val dueDate: String? = null,
    @SerializedName(value = "dueOdometer", alternate = ["DueOdometer"]) val dueOdometer: String? = null,
    @SerializedName(value = "dueDistance", alternate = ["DueDistance"]) val dueDistance: String? = null,
    @SerializedName(value = "notes", alternate = ["Notes"]) val notes: String? = null,
    @SerializedName(value = "tags", alternate = ["Tags"]) val tags: String? = null
)
