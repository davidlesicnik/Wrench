package com.lesicnik.wrench.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lesicnik.wrench.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query(
        """
        SELECT * FROM vehicles
        WHERE serverUrl = :serverUrl
        ORDER BY (soldDate IS NOT NULL), year DESC, make ASC, model ASC
        """
    )
    fun observeVehicles(serverUrl: String): Flow<List<VehicleEntity>>

    @Query(
        """
        SELECT * FROM vehicles
        WHERE serverUrl = :serverUrl
        ORDER BY (soldDate IS NOT NULL), year DESC, make ASC, model ASC
        """
    )
    suspend fun getVehicles(serverUrl: String): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicles(vehicles: List<VehicleEntity>)

    @Query("DELETE FROM vehicles WHERE serverUrl = :serverUrl")
    suspend fun deleteVehiclesForServer(serverUrl: String)
}
