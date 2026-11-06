package com.lesicnik.wrench.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lesicnik.wrench.data.local.entity.ReminderEntity
import com.lesicnik.wrench.data.local.entity.ReminderSyncEntity

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE accountKey = :accountKey AND vehicleId = :vehicleId")
    fun observe(accountKey: String, vehicleId: Int): kotlinx.coroutines.flow.Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE accountKey = :accountKey AND vehicleId = :vehicleId")
    suspend fun getReminders(accountKey: String, vehicleId: Int): List<ReminderEntity>

    @Query("SELECT fetchedAt FROM reminder_sync WHERE accountKey = :accountKey AND vehicleId = :vehicleId")
    suspend fun getFetchedAt(accountKey: String, vehicleId: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSync(sync: ReminderSyncEntity)

    @Query("DELETE FROM reminders WHERE accountKey = :accountKey AND vehicleId = :vehicleId")
    suspend fun deleteReminders(accountKey: String, vehicleId: Int)
}
