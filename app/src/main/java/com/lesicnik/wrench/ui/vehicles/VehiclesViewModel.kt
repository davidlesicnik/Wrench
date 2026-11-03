package com.lesicnik.wrench.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.preload.SessionPreloader
import com.lesicnik.wrench.data.remote.NetworkModule
import com.lesicnik.wrench.data.remote.Vehicle
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
    val lastReadings: Map<Int, Int> = emptyMap(),
    val errorMessage: String? = null,
    val isLoggedOut: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = ""
)

class VehiclesViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val vehicleRepository: VehicleRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionPreloader: SessionPreloader
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehiclesUiState())
    val uiState: StateFlow<VehiclesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            expenseRepository.sessionDataReady.collect {
                updateLastReadings(_uiState.value.vehicles)
            }
        }
        loadVehicles()
    }

    private fun updateLastReadings(vehicles: List<Vehicle>) {
        val readings = vehicles.mapNotNull { vehicle ->
            expenseRepository.getCachedExpenses(vehicle.id)
                ?.filter { it.odometer != null }
                ?.maxWithOrNull(compareBy<com.lesicnik.wrench.data.remote.records.Expense> { it.date }
                    .thenBy { it.odometer })
                ?.odometer?.let { vehicle.id to it }
        }.toMap()
        _uiState.value = _uiState.value.copy(lastReadings = readings)
    }

    fun loadVehicles(forceRefresh: Boolean = false) {
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

            val result = if (forceRefresh) {
                vehicleRepository.fetchVehiclesRemote(credentials.serverUrl, credentials.apiKey)
            } else {
                vehicleRepository.getVehicles(credentials.serverUrl, credentials.apiKey)
            }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        vehicles = result.data,
                        isLoading = false,
                        serverUrl = credentials.serverUrl,
                        apiKey = credentials.apiKey
                    )
                    updateLastReadings(result.data)
                }
                is ApiResult.Error -> {
                    val fallback = vehicleRepository.getCachedVehicles(credentials.serverUrl)
                    if (fallback.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            vehicles = fallback,
                            isLoading = false,
                            serverUrl = credentials.serverUrl,
                            apiKey = credentials.apiKey,
                            errorMessage = result.message
                        )
                        updateLastReadings(fallback)
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

    fun logout() {
        viewModelScope.launch {
            sessionPreloader.cancelAndJoin()
            credentialsRepository.deleteCredentials()
            expenseRepository.clearAllCaches()
            NetworkModule.clearCache()
            _uiState.value = _uiState.value.copy(isLoggedOut = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(
        private val credentialsRepository: CredentialsRepository,
        private val vehicleRepository: VehicleRepository,
        private val expenseRepository: ExpenseRepository,
        private val sessionPreloader: SessionPreloader
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VehiclesViewModel(credentialsRepository, vehicleRepository, expenseRepository, sessionPreloader) as T
        }
    }
}
