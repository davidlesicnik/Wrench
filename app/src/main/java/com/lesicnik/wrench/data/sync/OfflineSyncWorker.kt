package com.lesicnik.wrench.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ReminderRepository

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val credentials = CredentialsRepository(applicationContext).getCredentials() ?: return Result.success()

        val database = WrenchDatabase.getInstance(applicationContext)
        val engine = OfflineSyncEngine(database)
        return when (val result = engine.syncServer(credentials.serverUrl, credentials.apiKey)) {
            is ApiResult.Success -> {
                // Reminder availability must not block queued expense uploads.
                val reminders = ReminderRepository(database)
                database.vehicleDao().getVehicles(credentials.serverUrl).forEach {
                    reminders.refresh(credentials, it.remoteId)
                }
                Result.success()
            }
            is ApiResult.Error -> {
                if (runAttemptCount >= 8) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }
    }
}
