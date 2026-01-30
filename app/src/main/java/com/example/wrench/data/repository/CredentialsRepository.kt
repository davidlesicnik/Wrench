package com.example.wrench.data.repository

import com.example.wrench.data.local.CredentialsDao
import com.example.wrench.data.local.CredentialsEntity

class CredentialsRepository(private val credentialsDao: CredentialsDao) {

    suspend fun getCredentials(): CredentialsEntity? {
        return credentialsDao.getCredentials()
    }

    suspend fun saveCredentials(serverUrl: String, apiKey: String) {
        credentialsDao.saveCredentials(
            CredentialsEntity(
                serverUrl = serverUrl,
                apiKey = apiKey
            )
        )
    }

    suspend fun deleteCredentials() {
        credentialsDao.deleteCredentials()
    }
}
