package com.lesicnik.wrench.data.local.dao

import androidx.room.*
import com.lesicnik.wrench.data.local.entity.ReminderOperationEntity
import com.lesicnik.wrench.data.local.entity.ServiceUploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM reminder_operations WHERE accountKey = :key AND vehicleId = :vehicleId")
    fun observeReminders(key: String, vehicleId: Int): Flow<List<ReminderOperationEntity>>
    @Query("SELECT * FROM service_uploads WHERE accountKey = :key AND vehicleId = :vehicleId ORDER BY expenseLocalId")
    fun observeUploads(key: String, vehicleId: Int): Flow<List<ServiceUploadEntity>>
    @Query("SELECT * FROM reminder_operations WHERE accountKey = :key")
    suspend fun reminders(key: String): List<ReminderOperationEntity>
    @Query("SELECT * FROM service_uploads WHERE accountKey = :key ORDER BY expenseLocalId")
    suspend fun uploads(key: String): List<ServiceUploadEntity>
    @Query("SELECT * FROM service_uploads WHERE expenseLocalId = :id")
    suspend fun upload(id: Int): ServiceUploadEntity?
    @Upsert suspend fun save(op: ReminderOperationEntity)
    @Upsert suspend fun save(upload: ServiceUploadEntity)
}
