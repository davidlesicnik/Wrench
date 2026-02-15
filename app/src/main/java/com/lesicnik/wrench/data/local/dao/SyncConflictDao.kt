package com.lesicnik.wrench.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lesicnik.wrench.data.local.entity.SyncConflictEntity

@Dao
interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity): Long

    @Query(
        """
        SELECT * FROM sync_conflicts
        WHERE serverUrl = :serverUrl
          AND resolvedAt IS NULL
        ORDER BY createdAt ASC
        """
    )
    suspend fun getUnresolvedConflicts(serverUrl: String): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE conflictId = :conflictId LIMIT 1")
    suspend fun getById(conflictId: Long): SyncConflictEntity?

    @Query(
        """
        SELECT * FROM sync_conflicts
        WHERE serverUrl = :serverUrl
          AND expenseLocalId = :expenseLocalId
          AND resolvedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun getUnresolvedConflictForExpense(serverUrl: String, expenseLocalId: Int): SyncConflictEntity?

    @Update
    suspend fun updateConflict(conflict: SyncConflictEntity)
}
