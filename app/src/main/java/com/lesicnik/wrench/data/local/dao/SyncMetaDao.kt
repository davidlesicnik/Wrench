package com.lesicnik.wrench.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lesicnik.wrench.data.local.entity.SyncMetaEntity

@Dao
interface SyncMetaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: SyncMetaEntity)

    @Query(
        """
        SELECT * FROM sync_meta
        WHERE serverUrl = :serverUrl
          AND vehicleRemoteId = :vehicleRemoteId
        LIMIT 1
        """
    )
    suspend fun getMeta(serverUrl: String, vehicleRemoteId: Int): SyncMetaEntity?
}
