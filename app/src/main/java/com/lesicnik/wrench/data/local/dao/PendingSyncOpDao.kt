package com.lesicnik.wrench.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity

@Dao
interface PendingSyncOpDao {

    @Query(
        """
        SELECT * FROM pending_sync_ops
        WHERE serverUrl = :serverUrl
        ORDER BY createdAt ASC, opId ASC
        """
    )
    suspend fun getPendingOperations(serverUrl: String): List<PendingSyncOpEntity>

    @Query(
        """
        SELECT * FROM pending_sync_ops
        WHERE serverUrl = :serverUrl
          AND expenseLocalId = :expenseLocalId
        ORDER BY createdAt DESC, opId DESC
        LIMIT 1
        """
    )
    suspend fun getLatestOperationForExpense(serverUrl: String, expenseLocalId: Int): PendingSyncOpEntity?

    @Query(
        """
        SELECT * FROM pending_sync_ops
        WHERE serverUrl = :serverUrl
          AND expenseLocalId = :expenseLocalId
        ORDER BY createdAt ASC, opId ASC
        """
    )
    suspend fun getOperationsForExpense(serverUrl: String, expenseLocalId: Int): List<PendingSyncOpEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE serverUrl = :serverUrl")
    suspend fun countPending(serverUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: PendingSyncOpEntity): Long

    @Update
    suspend fun updateOperation(operation: PendingSyncOpEntity)

    @Query("DELETE FROM pending_sync_ops WHERE opId = :opId")
    suspend fun deleteById(opId: Long)

    @Query("DELETE FROM pending_sync_ops WHERE serverUrl = :serverUrl AND expenseLocalId = :expenseLocalId")
    suspend fun deleteForExpense(serverUrl: String, expenseLocalId: Int)
}
