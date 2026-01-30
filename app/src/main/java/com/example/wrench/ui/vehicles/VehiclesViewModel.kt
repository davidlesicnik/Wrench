package com.example.wrench.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wrench.data.remote.Vehicle
import com.example.wrench.data.repository.ApiResult
import com.example.wrench.data.repository.CredentialsRepository
import com.example.wrench.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VehiclesUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isLoggedOut: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = ""
)

class VehiclesViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehiclesUiState())
    val uiState: StateFlow<VehiclesUiState> = _uiState.asStateFlow()

    init {
        loadVehicles()
    }

    fun loadVehicles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val credentials = credentialsRepository.getCredentials()
            if (credentials == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedOut = true
                )
                return@launch
            }

            when (val result = vehicleRepository.getVehicles(credentials.serverUrl, credentials.apiKey)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        vehicles = result.data,
                        isLoading = false,
                        serverUrl = credentials.serverUrl,
                        apiKey = credentials.apiKey
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

    fun logout() {
        viewModelScope.launch {
            credentialsRepository.deleteCredentials()
            _uiState.value = _uiState.value.copy(isLoggedOut = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(
        private val credentialsRepository: CredentialsRepository,
        private val vehicleRepository: VehicleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VehiclesViewModel(credentialsRepository, vehicleRepository) as T
        }
    }
}
