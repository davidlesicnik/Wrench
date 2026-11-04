package com.lesicnik.wrench.ui.login

import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.data.repository.ApiErrorType
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.Credentials
import com.lesicnik.wrench.data.repository.CredentialsStore
import com.lesicnik.wrench.data.repository.LoginVehicleSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val verified = Credentials("https://server.example", "verified-test-key")
    private val vehicles = listOf(Vehicle(1, null, 2020, "Test", "Car", null, null, false, false, "km"))

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `rejected saved credentials cannot unlock cached data and are removed`() = runTest(dispatcher) {
        val store = FakeCredentialsStore(verified)
        val vm = LoginViewModel(store, source(ApiErrorType.AUTHENTICATION))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showOfflinePrompt)
        assertNotNull(vm.uiState.value.errorMessage)
        assertNull(store.credentials)
        vm.continueOfflineLogin()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals(0, store.saves)
    }

    @Test
    fun `previously verified credentials allow offline access during connection failure`() = runTest(dispatcher) {
        val store = FakeCredentialsStore(verified)
        val vm = LoginViewModel(store, source(ApiErrorType.NETWORK))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showOfflinePrompt)
        vm.continueOfflineLogin()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals(vehicles, vm.uiState.value.vehicles)
        assertEquals(verified, store.credentials)
        assertEquals(0, store.saves)
    }

    @Test
    fun `TLS and other non-connectivity errors do not offer offline access`() = runTest(dispatcher) {
        val vm = LoginViewModel(FakeCredentialsStore(verified), source(ApiErrorType.OTHER))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showOfflinePrompt)
        assertFalse(vm.uiState.value.isLoggedIn)
    }

    @Test
    fun `unverified key cannot unlock another sessions cache`() = runTest(dispatcher) {
        val store = FakeCredentialsStore(verified)
        val vm = LoginViewModel(store, source(ApiErrorType.NETWORK))
        advanceUntilIdle()
        vm.onApiKeyChanged("unverified-test-key")
        vm.login()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showOfflinePrompt)
        vm.continueOfflineLogin()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals(verified, store.credentials)
    }

    @Test
    fun `editing server or key invalidates an existing offline prompt`() = runTest(dispatcher) {
        for (changeServer in listOf(false, true)) {
            val vm = LoginViewModel(FakeCredentialsStore(verified), source(ApiErrorType.NETWORK))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.showOfflinePrompt)
            if (changeServer) vm.onServerUrlChanged("https://other.example")
            else vm.onApiKeyChanged("other-test-key")
            vm.continueOfflineLogin()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.showOfflinePrompt)
            assertFalse(vm.uiState.value.isLoggedIn)
        }
    }

    @Test
    fun `first login cannot use cached data without verified stored credentials`() = runTest(dispatcher) {
        val store = FakeCredentialsStore(null)
        val vm = LoginViewModel(store, source(ApiErrorType.NETWORK))
        advanceUntilIdle()
        vm.onServerUrlChanged(verified.serverUrl)
        vm.onApiKeyChanged(verified.apiKey)
        vm.login()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showOfflinePrompt)
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals(0, store.saves)
    }

    @Test
    fun `parsed HTTP URLs still require a local host and explicit warning`() = runTest(dispatcher) {
        val vm = LoginViewModel(FakeCredentialsStore(null), source(ApiErrorType.NETWORK))
        advanceUntilIdle()
        vm.onApiKeyChanged(verified.apiKey)
        vm.onServerUrlChanged("http:////fc-attacker.example")
        vm.login()
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.showHttpWarning)
        vm.onServerUrlChanged("http:////127.0.0.1")
        vm.login()
        assertTrue(vm.uiState.value.showHttpWarning)
    }

    @Test
    fun `successful online login verifies and saves the submitted credentials`() = runTest(dispatcher) {
        val store = FakeCredentialsStore(null)
        val online = object : LoginVehicleSource {
            override suspend fun fetchVehiclesRemote(serverUrl: String, apiKey: String) = ApiResult.Success(vehicles)
            override suspend fun getCachedVehicles(serverUrl: String) = vehicles
        }
        val vm = LoginViewModel(store, online)
        advanceUntilIdle()
        vm.onServerUrlChanged(verified.serverUrl)
        vm.onApiKeyChanged(verified.apiKey)
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals(verified, store.credentials)
        assertEquals(1, store.saves)
    }

    @Test
    fun `a late login response cannot authenticate credentials edited during the request`() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<List<Vehicle>>>()
        val store = FakeCredentialsStore(verified)
        val delayed = object : LoginVehicleSource {
            override suspend fun fetchVehiclesRemote(serverUrl: String, apiKey: String) = response.await()
            override suspend fun getCachedVehicles(serverUrl: String) = vehicles
        }
        val vm = LoginViewModel(store, delayed)
        advanceUntilIdle()
        vm.onApiKeyChanged("unverified-test-key")
        response.complete(ApiResult.Success(vehicles))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertFalse(vm.uiState.value.showOfflinePrompt)
        assertEquals(verified, store.credentials)
    }

    @Test
    fun `removing saved credentials while the prompt is open prevents offline login`() = runTest(dispatcher) {
        val store = FakeCredentialsStore(verified)
        val vm = LoginViewModel(store, source(ApiErrorType.NETWORK))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showOfflinePrompt)
        store.deleteCredentials()
        vm.continueOfflineLogin()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals(0, store.saves)
    }

    private fun source(type: ApiErrorType) = object : LoginVehicleSource {
        override suspend fun fetchVehiclesRemote(serverUrl: String, apiKey: String): ApiResult<List<Vehicle>> =
            ApiResult.Error("Test failure", type)
        override suspend fun getCachedVehicles(serverUrl: String): List<Vehicle> = vehicles
    }

    private class FakeCredentialsStore(var credentials: Credentials?) : CredentialsStore {
        var saves = 0
        override suspend fun getCredentials(): Credentials? = credentials
        override suspend fun saveCredentials(serverUrl: String, apiKey: String) {
            saves++
            credentials = Credentials(serverUrl, apiKey)
        }
        override suspend fun deleteCredentials() { credentials = null }
    }
}
