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

data class LoginUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val saveCredentials: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isCheckingStoredCredentials: Boolean = true
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
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
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
