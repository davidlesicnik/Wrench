package com.lesicnik.wrench.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.data.repository.ApiErrorType
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.Credentials
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.CredentialsStore
import com.lesicnik.wrench.data.repository.LoginVehicleSource
import com.lesicnik.wrench.data.repository.VehicleRepository
import com.lesicnik.wrench.util.LocalNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class LoginUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val saveCredentials: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val isCheckingStoredCredentials: Boolean = true,
    val showHttpWarning: Boolean = false,
    val pendingHttpLogin: Boolean = false,
    val showOfflinePrompt: Boolean = false,
    val offlinePromptMessage: String? = null
)

class LoginViewModel(
    private val credentialsRepository: CredentialsStore,
    private val vehicleRepository: LoginVehicleSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var offlineCredentials: Credentials? = null
    private var loginAttempt = 0

    init {
        checkStoredCredentials()
    }

    private fun checkStoredCredentials() {
        viewModelScope.launch {
            val credentials = credentialsRepository.getCredentials()
            if (credentials != null) {
                _uiState.value = _uiState.value.copy(
                    serverUrl = credentials.serverUrl,
                    apiKey = credentials.apiKey,
                    isCheckingStoredCredentials = false
                )
                login(autoLogin = true)
            } else {
                _uiState.value = _uiState.value.copy(isCheckingStoredCredentials = false)
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        invalidateOfflineLogin()
        _uiState.value = _uiState.value.copy(serverUrl = url, errorMessage = null)
    }

    fun onApiKeyChanged(key: String) {
        invalidateOfflineLogin()
        _uiState.value = _uiState.value.copy(apiKey = key, errorMessage = null)
    }

    private fun invalidateOfflineLogin() {
        loginAttempt++
        offlineCredentials = null
        _uiState.value = _uiState.value.copy(
            isLoading = false, showOfflinePrompt = false, offlinePromptMessage = null,
            showHttpWarning = false, pendingHttpLogin = false
        )
    }

    fun onSaveCredentialsChanged(save: Boolean) {
        _uiState.value = _uiState.value.copy(saveCredentials = save)
        if (!save) {
            viewModelScope.launch {
                credentialsRepository.deleteCredentials()
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissHttpWarning() {
        _uiState.value = _uiState.value.copy(showHttpWarning = false, pendingHttpLogin = false)
    }

    fun confirmHttpLogin() {
        _uiState.value = _uiState.value.copy(showHttpWarning = false)
        performLogin(autoLogin = false)
    }

    fun dismissOfflinePrompt() {
        offlineCredentials = null
        _uiState.value = _uiState.value.copy(showOfflinePrompt = false)
    }

    fun continueOfflineLogin() {
        val state = _uiState.value
        val credentials = Credentials(state.serverUrl, state.apiKey)
        if (!state.showOfflinePrompt || credentials != offlineCredentials) return
        val attempt = loginAttempt
        viewModelScope.launch {
            // Offline access only uses credentials previously verified by the server.
            if (credentialsRepository.getCredentials() != credentials) return@launch
            val vehicles = vehicleRepository.getCachedVehicles(state.serverUrl)
            if (attempt != loginAttempt || offlineCredentials != credentials) return@launch
            offlineCredentials = null
            _uiState.value = _uiState.value.copy(
                showOfflinePrompt = false,
                offlinePromptMessage = null,
                isLoading = false,
                errorMessage = null,
                isLoggedIn = true,
                vehicles = vehicles
            )
        }
    }

    fun retryOnlineLogin() {
        _uiState.value = _uiState.value.copy(showOfflinePrompt = false, offlinePromptMessage = null)
        performLogin(autoLogin = false)
    }

    fun login(autoLogin: Boolean = false) {
        val state = _uiState.value

        if (state.serverUrl.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Server URL is required")
            return
        }

        if (state.apiKey.isBlank()) {
            _uiState.value = state.copy(errorMessage = "API Key is required")
            return
        }

        val server = state.serverUrl.trim().toHttpUrlOrNull()
        if (server == null || server.username.isNotEmpty() || server.password.isNotEmpty()) {
            _uiState.value = state.copy(errorMessage = "Enter a valid HTTP or HTTPS server URL without embedded credentials")
            return
        }

        if (server.scheme == "http") {
            if (!LocalNetwork.isLocalHost(server.host)) {
                _uiState.value = state.copy(
                    errorMessage = "Insecure HTTP is only allowed for local network addresses"
                )
                return
            }
            if (!autoLogin) {
                _uiState.value = state.copy(showHttpWarning = true, pendingHttpLogin = true)
                return
            }
        }

        performLogin(autoLogin)
    }

    private fun performLogin(autoLogin: Boolean) {
        val state = _uiState.value
        val credentials = Credentials(state.serverUrl, state.apiKey)
        val attempt = ++loginAttempt
        offlineCredentials = null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, errorMessage = null, showOfflinePrompt = false, offlinePromptMessage = null
            )

            val result = vehicleRepository.fetchVehiclesRemote(state.serverUrl, state.apiKey)
            if (attempt != loginAttempt) return@launch
            when (result) {
                is ApiResult.Success -> {
                    if (state.saveCredentials && !autoLogin) {
                        credentialsRepository.saveCredentials(state.serverUrl, state.apiKey)
                    }
                    if (attempt != loginAttempt) return@launch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        vehicles = result.data
                    )
                }
                is ApiResult.Error -> {
                    val storedCredentials = credentialsRepository.getCredentials()
                    if (result.type == ApiErrorType.AUTHENTICATION && storedCredentials == credentials) {
                        credentialsRepository.deleteCredentials()
                    }
                    val canContinueOffline = result.type == ApiErrorType.NETWORK &&
                        storedCredentials == credentials &&
                        vehicleRepository.getCachedVehicles(state.serverUrl).isNotEmpty()
                    if (attempt != loginAttempt) return@launch
                    if (canContinueOffline) {
                        offlineCredentials = credentials
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showOfflinePrompt = true,
                            offlinePromptMessage = result.message
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    class Factory(
        private val credentialsRepository: CredentialsRepository,
        private val vehicleRepository: VehicleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(credentialsRepository, vehicleRepository) as T
        }
    }
}
