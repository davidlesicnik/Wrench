package com.lesicnik.wrench.data.sync

import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.remote.ReminderRecord
import com.lesicnik.wrench.data.remote.ReminderWriteResponse
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.repository.Credentials
import com.lesicnik.wrench.data.repository.expense.ExpenseMapper
import java.util.Locale
import retrofit2.Response

interface MaintenanceRemote {
    suspend fun reminders(c: Credentials, vehicleId: Int): Response<List<ReminderRecord>>
    suspend fun update(c: Credentials, id: Int, values: ReminderValues): Response<ReminderWriteResponse>
    suspend fun delete(c: Credentials, id: Int): Response<ReminderWriteResponse>
    suspend fun create(c: Credentials, expense: ExpenseEntity): Response<ReminderWriteResponse>
    suspend fun services(c: Credentials, vehicleId: Int): List<Expense>
}

class PublicMaintenanceRemote : MaintenanceRemote {
    override suspend fun reminders(c: Credentials, vehicleId: Int) = NetworkModule.getApi(c.serverUrl).getReminders(c.apiKey, vehicleId)
    override suspend fun update(c: Credentials, id: Int, values: ReminderValues): Response<ReminderWriteResponse> {
        val draft = values.draft()
        return NetworkModule.getWriteApi(c.serverUrl).updateReminder(c.apiKey, id, values.description,
            draft.apiMetric, values.dueDate, values.dueOdometer, values.notes, values.tags)
    }
    override suspend fun delete(c: Credentials, id: Int) = NetworkModule.getWriteApi(c.serverUrl).deleteReminder(c.apiKey, id)
    override suspend fun create(c: Credentials, expense: ExpenseEntity) = NetworkModule.getWriteApi(c.serverUrl).recordMaintenanceService(
        c.apiKey, expense.vehicleRemoteId, expense.date, expense.odometer?.toString().orEmpty(), expense.description,
        String.format(Locale.US, "%.2f", expense.cost).replace('.', ','), expense.notes.orEmpty())
    override suspend fun services(c: Credentials, vehicleId: Int): List<Expense> {
        val response = NetworkModule.getApi(c.serverUrl).getServiceRecords(c.apiKey, vehicleId)
        check(response.isSuccessful) { "Couldn't verify service records (HTTP ${response.code()})." }
        return requireNotNull(response.body()).map { ExpenseMapper().mapServiceRecord(it) }
    }
}
