package com.lesicnik.wrench.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.FuelStatistics
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.data.repository.ReminderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val fuelStatistics: FuelStatistics? = null,
    val overview: HomeOverview? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val reminders: List<Reminder> = emptyList(),
    val remindersLoaded: Boolean = false,
    val maintenanceError: String? = null
)

class HomeViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val expenseRepository: ExpenseRepository,
    private val vehicleId: Int,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var maintenanceJob: Job? = null

    init {
        viewModelScope.launch {
            expenseRepository.sessionDataReady.collect { loadStatistics() }
        }
        loadStatistics()
    }

    private fun loadMaintenance() {
        if (maintenanceJob?.isActive == true) return
        maintenanceJob = viewModelScope.launch {
            val credentials = credentialsRepository.getCredentials() ?: return@launch
            reminderRepository.cached(credentials, vehicleId)?.let { snapshot ->
                _uiState.value = _uiState.value.copy(reminders = snapshot.reminders, remindersLoaded = true)
            }
            when (val result = reminderRepository.refresh(credentials, vehicleId)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    reminders = result.data.reminders, remindersLoaded = true, maintenanceError = null)
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(maintenanceError = result.message)
            }
        }
    }

    fun loadStatistics(forceRefresh: Boolean = false) {
        loadMaintenance()
        viewModelScope.launch {
            // Check if we have cached data available
            val cachedStats = expenseRepository.getCachedFuelStatistics(vehicleId)
            val cachedExpenses = expenseRepository.getCachedExpenses(vehicleId)

            // If we have cached data and not forcing refresh, use it immediately
            if (!forceRefresh && cachedStats != null && cachedExpenses != null) {
                _uiState.value = _uiState.value.copy(
                    fuelStatistics = cachedStats,
                    overview = buildHomeOverview(cachedExpenses),
                    isLoading = false,
                    errorMessage = null
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val credentials = credentialsRepository.getCredentials()
            if (credentials == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Not logged in"
                )
                return@launch
            }

            // Load fuel statistics and expenses in parallel
            val statsDeferred = async {
                expenseRepository.getFuelStatistics(
                    credentials.serverUrl,
                    credentials.apiKey,
                    vehicleId,
                    forceRefresh = forceRefresh
                )
            }
            val expensesDeferred = async {
                expenseRepository.getExpenses(
                    credentials.serverUrl,
                    credentials.apiKey,
                    vehicleId,
                    forceRefresh = forceRefresh
                )
            }

            val statsResult = statsDeferred.await()
            val expensesResult = expensesDeferred.await()

            when (statsResult) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        fuelStatistics = statsResult.data,
                        overview = if (expensesResult is ApiResult.Success) {
                            buildHomeOverview(expensesResult.data)
                        } else _uiState.value.overview,
                        isLoading = false,
                        errorMessage = (expensesResult as? ApiResult.Error)?.message
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = statsResult.message
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(
        private val credentialsRepository: CredentialsRepository,
        private val expenseRepository: ExpenseRepository,
        private val vehicleId: Int,
        private val reminderRepository: ReminderRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(credentialsRepository, expenseRepository, vehicleId, reminderRepository) as T
        }
    }
}
