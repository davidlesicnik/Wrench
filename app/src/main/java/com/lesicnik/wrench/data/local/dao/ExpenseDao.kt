package com.lesicnik.wrench.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT * FROM expenses
        WHERE serverUrl = :serverUrl
          AND vehicleRemoteId = :vehicleRemoteId
          AND isDeletedLocal = 0
        ORDER BY date DESC, COALESCE(odometer, -1) DESC, localId DESC
        """
    )
    fun observeVisibleExpenses(serverUrl: String, vehicleRemoteId: Int): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE serverUrl = :serverUrl
          AND vehicleRemoteId = :vehicleRemoteId
          AND isDeletedLocal = 0
        ORDER BY date DESC, COALESCE(odometer, -1) DESC, localId DESC
        """
    )
    suspend fun getVisibleExpenses(serverUrl: String, vehicleRemoteId: Int): List<ExpenseEntity>

    @Query(
        """
        SELECT * FROM expenses
        WHERE serverUrl = :serverUrl
          AND vehicleRemoteId = :vehicleRemoteId
          AND type = :type
          AND remoteId = :remoteId
        LIMIT 1
        """
    )
    suspend fun findByRemoteId(serverUrl: String, vehicleRemoteId: Int, type: String, remoteId: Int): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Int): ExpenseEntity?

    @Query(
        """
        SELECT * FROM expenses
        WHERE serverUrl = :serverUrl
          AND vehicleRemoteId = :vehicleRemoteId
          AND isDeletedLocal = 0
          AND syncState IN (:syncStates)
        ORDER BY updatedAt ASC
        """
    )
    suspend fun findBySyncStates(
        serverUrl: String,
        vehicleRemoteId: Int,
        syncStates: List<String>
    ): List<ExpenseEntity>

    @Query(
        """
        SELECT * FROM expenses
        WHERE serverUrl = :serverUrl
          AND vehicleRemoteId = :vehicleRemoteId
          AND remoteId IS NOT NULL
          AND isDeletedLocal = 0
        """
    )
    suspend fun getSyncedRemoteLinkedExpenses(serverUrl: String, vehicleRemoteId: Int): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Int)

    @Query("DELETE FROM expenses WHERE serverUrl = :serverUrl AND vehicleRemoteId = :vehicleRemoteId")
    suspend fun deleteForVehicle(serverUrl: String, vehicleRemoteId: Int)
}
