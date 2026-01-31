package com.lesicnik.wrench.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CredentialsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: CredentialsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = CredentialsRepository(context)
    }

    @Test
    fun `getCredentials returns null when empty`() = runTest {
        // First ensure credentials are deleted
        repository.deleteCredentials()

        val credentials = repository.getCredentials()

        assertNull(credentials)
    }

    @Test
    fun `saveCredentials stores credentials securely`() = runTest {
        val serverUrl = "https://test.example.com"
        val apiKey = "test-api-key-12345"

        repository.saveCredentials(serverUrl, apiKey)

        val credentials = repository.getCredentials()

        assertNotNull(credentials)
        assertEquals(serverUrl, credentials?.serverUrl)
        assertEquals(apiKey, credentials?.apiKey)
    }

    @Test
    fun `deleteCredentials removes all data`() = runTest {
        // First save some credentials
        repository.saveCredentials("https://test.com", "api-key")

        // Verify they're saved
        assertNotNull(repository.getCredentials())

        // Delete them
        repository.deleteCredentials()

        // Verify they're gone
        assertNull(repository.getCredentials())
    }

    @Test
    fun `saveCredentials overwrites existing credentials`() = runTest {
        val originalUrl = "https://original.com"
        val originalKey = "original-key"
        val newUrl = "https://new.com"
        val newKey = "new-key"

        repository.saveCredentials(originalUrl, originalKey)
        repository.saveCredentials(newUrl, newKey)

        val credentials = repository.getCredentials()

        assertEquals(newUrl, credentials?.serverUrl)
        assertEquals(newKey, credentials?.apiKey)
    }

    @Test
    fun `credentials survive repository recreation`() = runTest {
        val serverUrl = "https://persistent.example.com"
        val apiKey = "persistent-api-key"

        repository.saveCredentials(serverUrl, apiKey)

        // Create a new repository instance
        val newRepository = CredentialsRepository(context)

        val credentials = newRepository.getCredentials()

        assertNotNull(credentials)
        assertEquals(serverUrl, credentials?.serverUrl)
        assertEquals(apiKey, credentials?.apiKey)
    }
}
