package com.lesicnik.wrench.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI

data class LoginUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val saveCredentials: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isCheckingStoredCredentials: Boolean = true,
    val showHttpWarning: Boolean = false,
    val pendingHttpLogin: Boolean = false
)

class LoginViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

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
        _uiState.value = _uiState.value.copy(serverUrl = url, errorMessage = null)
    }

    fun onApiKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, errorMessage = null)
    }

    fun onSaveCredentialsChanged(save: Boolean) {
        _uiState.value = _uiState.value.copy(saveCredentials = save)
        // When unchecking "Save credentials", delete any stored credentials
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

    private fun isHttpUrl(url: String): Boolean {
        return url.trim().lowercase().startsWith("http://")
    }

    private fun isLocalHttpUrl(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return false
        }

        if (uri.scheme?.lowercase() != "http") return false
        val host = uri.host ?: return false
        val normalizedHost = host.trim().lowercase()

        if (normalizedHost == "localhost") return true

        val hostNoBrackets = normalizedHost.removePrefix("[").removeSuffix("]")
        return isLocalIpv4(hostNoBrackets) || isLocalIpv6(hostNoBrackets)
    }

    private fun isLocalIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val first = octets[0]
        val second = octets[1]

        return when {
            first == 127 -> true // Loopback 127.0.0.0/8
            first == 10 -> true // 10.0.0.0/8
            first == 192 && second == 168 -> true // 192.168.0.0/16
            first == 172 && second in 16..31 -> true // 172.16.0.0/12
            first == 169 && second == 254 -> true // Link-local 169.254.0.0/16
            else -> false
        }
    }

    private fun isLocalIpv6(host: String): Boolean {
        val normalized = host.lowercase()
        if (normalized == "::1") return true // Loopback
        if (normalized.startsWith("fe8") || normalized.startsWith("fe9") ||
            normalized.startsWith("fea") || normalized.startsWith("feb")) {
            return true // Link-local fe80::/10
        }
        if (normalized.startsWith("fc") || normalized.startsWith("fd")) {
            return true // Unique local fc00::/7
        }
        return false
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

        // Allow HTTP only for local addresses; show warning for local HTTP opt-in
        if (isHttpUrl(state.serverUrl)) {
            if (!isLocalHttpUrl(state.serverUrl)) {
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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = vehicleRepository.getVehicles(state.serverUrl, state.apiKey)) {
                is ApiResult.Success -> {
                    if (state.saveCredentials && !autoLogin) {
                        credentialsRepository.saveCredentials(state.serverUrl, state.apiKey)
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
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
