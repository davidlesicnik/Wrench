package com.example.wrench.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CredentialsDao {
    @Query("SELECT * FROM credentials WHERE id = 1")
    suspend fun getCredentials(): CredentialsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCredentials(credentials: CredentialsEntity)

    @Query("DELETE FROM credentials")
    suspend fun deleteCredentials()
}
