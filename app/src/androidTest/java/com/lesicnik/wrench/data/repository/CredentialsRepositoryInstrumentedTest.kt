package com.lesicnik.wrench.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialsRepositoryInstrumentedTest {

    private lateinit var repository: CredentialsRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        repository = CredentialsRepository(context)
    }

    @Test
    fun getCredentials_returnsNull_whenEmpty() = runBlocking {
        repository.deleteCredentials()
        assertNull(repository.getCredentials())
    }

    @Test
    fun saveCredentials_storesAndReadsBack() = runBlocking {
        val serverUrl = "https://test.example.com"
        val apiKey = "test-api-key-12345"

        repository.saveCredentials(serverUrl, apiKey)
        val credentials = repository.getCredentials()

        assertNotNull(credentials)
        assertEquals(serverUrl, credentials?.serverUrl)
        assertEquals(apiKey, credentials?.apiKey)
    }

    @Test
    fun deleteCredentials_removesAllData() = runBlocking {
        repository.saveCredentials("https://test.com", "api-key")
        assertNotNull(repository.getCredentials())

        repository.deleteCredentials()
        assertNull(repository.getCredentials())
    }

    @Test
    fun saveCredentials_overwritesExistingCredentials() = runBlocking {
        repository.saveCredentials("https://original.com", "original-key")
        repository.saveCredentials("https://new.com", "new-key")

        val credentials = repository.getCredentials()
        assertEquals("https://new.com", credentials?.serverUrl)
        assertEquals("new-key", credentials?.apiKey)
    }
}

