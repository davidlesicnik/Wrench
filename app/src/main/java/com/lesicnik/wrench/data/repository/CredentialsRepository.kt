package com.lesicnik.wrench.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Credentials(
    val serverUrl: String,
    val apiKey: String
)

class CredentialsRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val appContext = context.applicationContext

    init {
        // Migrate from old Room database if it exists
        migrateFromRoomIfNeeded(context)
    }

    private fun migrateFromRoomIfNeeded(context: Context) {
        val dbFile = context.getDatabasePath("wrench_database")
        if (dbFile.exists()) {
            // Old database exists - we can't easily read from Room without the DAO,
            // so we'll just delete the old database. Users will need to re-enter credentials.
            // This is acceptable since it's a one-time migration for security.
            try {
                context.deleteDatabase("wrench_database")
                // Also delete the schema files if they exist
                File(context.getDatabasePath("wrench_database").parent, "wrench_database-shm").delete()
                File(context.getDatabasePath("wrench_database").parent, "wrench_database-wal").delete()
            } catch (e: Exception) {
                // Ignore migration errors
            }
        }
    }

    suspend fun getCredentials(): Credentials? = withContext(Dispatchers.IO) {
        val serverUrl = encryptedPrefs.getString(KEY_SERVER_URL, null)
        val apiKey = encryptedPrefs.getString(KEY_API_KEY, null)

        if (serverUrl != null && apiKey != null) {
            Credentials(serverUrl, apiKey)
        } else {
            null
        }
    }

    suspend fun saveCredentials(serverUrl: String, apiKey: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    suspend fun deleteCredentials() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_API_KEY)
            .apply()
    }

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "wrench_secure_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
    }
}
