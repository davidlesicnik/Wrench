package com.lesicnik.wrench.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class CredentialsEntity(
    @PrimaryKey
    val id: Int = 1,
    val serverUrl: String,
    val apiKey: String
)
