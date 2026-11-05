package com.lesicnik.wrench.data.repository

import androidx.room.withTransaction
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.local.entity.ReminderEntity
import com.lesicnik.wrench.data.local.entity.ReminderSyncEntity
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.remote.ReminderRecord
import com.lesicnik.wrench.data.remote.ReminderWriteResponse
import retrofit2.Response
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate

data class ReminderSnapshot(val reminders: List<Reminder>, val fetchedAt: Long)

class ReminderRepository(
    private val database: WrenchDatabase,
    private val fetchOdometer: suspend (Credentials, Int) -> Response<Int> = { credentials, vehicleId ->
        NetworkModule.getApi(credentials.serverUrl).getLatestOdometer(credentials.apiKey, vehicleId)
    },
    private val writeReminder: suspend (Credentials, Int, ReminderDraft) -> Response<ReminderWriteResponse> = { credentials, vehicleId, draft ->
        NetworkModule.getWriteApi(credentials.serverUrl).addReminder(
            credentials.apiKey, vehicleId, draft.description.trim(), draft.apiMetric,
            draft.dueDate?.takeIf { draft.metric != ReminderMetric.ODOMETER }?.toString(),
            draft.dueOdometer?.takeIf { draft.metric != ReminderMetric.DATE },
            draft.notes.trim(), draft.tags.trim()
        )
    },
    private val fetchReminders: suspend (Credentials, Int) -> Response<List<ReminderRecord>> = { credentials, vehicleId ->
        NetworkModule.getApi(credentials.serverUrl).getReminders(credentials.apiKey, vehicleId)
    }
) {
    private val mutex = Mutex()

    suspend fun latestOdometer(credentials: Credentials, vehicleId: Int): ApiResult<Int> = try {
        val response = fetchOdometer(credentials, vehicleId)
        val value = response.body()
        if (response.isSuccessful && value != null && value >= 0) ApiResult.Success(value)
        else ApiResult.Error("Couldn't load the latest odometer. Enter a starting reading or use an exact target.")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ApiResult.Error("Couldn't load the latest odometer. Enter a starting reading or use an exact target.")
    }

    suspend fun create(credentials: Credentials, vehicleId: Int, draft: ReminderDraft): ReminderCreateResult = mutex.withLock {
        draft.validationError()?.let { return@withLock ReminderCreateResult.Failed(it) }
        try {
            val response = writeReminder(credentials, vehicleId, draft)
            when {
                response.code() == 401 || response.code() == 403 -> ReminderCreateResult.Failed(
                    "Your API key doesn't have permission to add reminders. Use an Editor or Manager key."
                )
                response.code() == 404 || response.code() == 405 -> ReminderCreateResult.Failed(
                    "This server doesn't support adding reminders. Update LubeLogger to enable it."
                )
                response.code() >= 500 -> uncertainSave()
                !response.isSuccessful -> ReminderCreateResult.Failed("LubeLogger couldn't save this reminder. Check the details and try again.")
                response.body()?.success == true -> ReminderCreateResult.Created(response.body()?.additionalData?.recordId)
                response.body()?.success == false -> ReminderCreateResult.Failed("LubeLogger couldn't save this reminder. Check the details and try again.")
                else -> uncertainSave()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            uncertainSave()
        }
    }

    private fun uncertainSave() = ReminderCreateResult.Failed(
        "Couldn't confirm whether the reminder was saved. Check the calendar before adding it again.",
        mayHaveSaved = true
    )

    suspend fun cached(credentials: Credentials, vehicleId: Int): ReminderSnapshot? {
        val key = accountKey(credentials)
        return database.withTransaction {
            val fetchedAt = database.reminderDao().getFetchedAt(key, vehicleId) ?: return@withTransaction null
            val reminders = database.reminderDao().getReminders(key, vehicleId).map {
                Reminder(it.id, it.vehicleId, it.description, ReminderMetric.valueOf(it.metric),
                    ReminderUrgency.valueOf(it.urgency), it.dueDate?.let(LocalDate::parse),
                    it.dueOdometer, it.dueDistance, it.notes, it.tags)
            }
            ReminderSnapshot(reminders.sortedBy { it.id }, fetchedAt)
        }
    }

    suspend fun refresh(credentials: Credentials, vehicleId: Int): ApiResult<ReminderSnapshot> = mutex.withLock {
        try {
            val response = fetchReminders(credentials, vehicleId)
            if (!response.isSuccessful) {
                return@withLock when (response.code()) {
                    401, 403 -> ApiResult.Error("Access denied. Check your LubeLogger API key and vehicle permissions.", ApiErrorType.AUTHENTICATION)
                    404 -> ApiResult.Error("This LubeLogger server does not support reminders. Update LubeLogger to use the calendar.")
                    else -> ApiResult.Error("Couldn't refresh reminders (HTTP ${response.code()}). Try again.")
                }
            }
            // Parse the entire response before replacing the cache; invalid data must never erase it.
            val records = requireNotNull(response.body()) { "Empty response" }.map { it.toReminder() }.sortedBy { it.id }
            require(records.all { it.vehicleId == vehicleId }) { "Unexpected vehicle" }
            val key = accountKey(credentials)
            val now = System.currentTimeMillis()
            database.withTransaction {
                database.reminderDao().deleteReminders(key, vehicleId)
                database.reminderDao().insertReminders(records.map {
                    ReminderEntity(key, vehicleId, it.id, it.description, it.metric.name, it.urgency.name,
                        it.dueDate?.toString(), it.dueOdometer, it.dueDistance, it.notes, it.tags)
                })
                database.reminderDao().setSync(ReminderSyncEntity(key, vehicleId, now))
            }
            ApiResult.Success(ReminderSnapshot(records, now))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            ApiResult.Error("Couldn't connect to LubeLogger. Check your connection and try again.", ApiErrorType.NETWORK)
        } catch (_: Exception) {
            ApiResult.Error("Couldn't read reminders from LubeLogger. Try refreshing or update the server.")
        }
    }

    companion object {
        // Scope caches to both server and API identity without storing the credential itself.
        internal fun accountKey(credentials: Credentials): String = MessageDigest.getInstance("SHA-256")
            .digest("${credentials.serverUrl.trimEnd('/')}\n${credentials.apiKey}".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
