package com.lesicnik.wrench.data.repository.offline

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.remote.records.ExpenseType
import java.security.MessageDigest

data class ExpenseOperationPayload(
    val baseRemoteId: Int?,
    val baseType: String?,
    val baseFingerprint: String?
)

data class RemoteExpenseSnapshot(
    val vehicleRemoteId: Int,
    val remoteId: Int,
    val type: ExpenseType,
    val date: String,
    val cost: Double,
    val odometer: Int?,
    val description: String,
    val notes: String?,
    val liters: Double?,
    val isFillToFull: Boolean?,
    val isMissedFuelUp: Boolean?,
    val isRecurring: Boolean?
)

private val gson = Gson()

fun ExpenseOperationPayload.toJson(): String = gson.toJson(this)

fun parseExpenseOperationPayload(value: String?): ExpenseOperationPayload {
    if (value.isNullOrBlank()) {
        return ExpenseOperationPayload(null, null, null)
    }
    return try {
        gson.fromJson(value, ExpenseOperationPayload::class.java)
            ?: ExpenseOperationPayload(null, null, null)
    } catch (_: Exception) {
        ExpenseOperationPayload(null, null, null)
    }
}

fun ExpenseEntity.computeFingerprint(): String {
    val raw = listOf(
        type,
        date,
        cost.toString(),
        odometer?.toString().orEmpty(),
        description,
        notes.orEmpty(),
        liters?.toString().orEmpty(),
        isFillToFull?.toString().orEmpty(),
        isMissedFuelUp?.toString().orEmpty(),
        isRecurring?.toString().orEmpty()
    ).joinToString("|")
    return raw.sha256()
}

fun RemoteExpenseSnapshot.computeFingerprint(): String {
    val raw = listOf(
        type.name,
        date,
        cost.toString(),
        odometer?.toString().orEmpty(),
        description,
        notes.orEmpty(),
        liters?.toString().orEmpty(),
        isFillToFull?.toString().orEmpty(),
        isMissedFuelUp?.toString().orEmpty(),
        isRecurring?.toString().orEmpty()
    ).joinToString("|")
    return raw.sha256()
}

fun RemoteExpenseSnapshot.toJson(): String = gson.toJson(this)

fun ExpenseEntity.toJson(): String = gson.toJson(this)

fun parseRemoteExpenseSnapshot(json: String): RemoteExpenseSnapshot? {
    return try {
        gson.fromJson(json, RemoteExpenseSnapshot::class.java)
    } catch (_: Exception) {
        null
    }
}

fun parseRemoteExpenseSnapshots(json: String): List<RemoteExpenseSnapshot> {
    return try {
        val type = object : TypeToken<List<RemoteExpenseSnapshot>>() {}.type
        gson.fromJson<List<RemoteExpenseSnapshot>>(json, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}
