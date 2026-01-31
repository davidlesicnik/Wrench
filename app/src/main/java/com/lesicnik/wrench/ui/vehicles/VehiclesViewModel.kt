package com.lesicnik.wrench.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.remote.Vehicle
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.VehicleRepository
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
    private val vehicleRepository: VehicleRepository,
    private val expenseRepository: ExpenseRepository
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
            expenseRepository.clearAllCaches()
            NetworkModule.clearCache()
            _uiState.value = _uiState.value.copy(isLoggedOut = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun preloadVehicleData(vehicleId: Int) {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank()) return

        viewModelScope.launch {
            expenseRepository.preloadVehicleData(
                state.serverUrl,
                state.apiKey,
                vehicleId
            )
        }
    }

    class Factory(
        private val credentialsRepository: CredentialsRepository,
        private val vehicleRepository: VehicleRepository,
        private val expenseRepository: ExpenseRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VehiclesViewModel(credentialsRepository, vehicleRepository, expenseRepository) as T
        }
    }
}
