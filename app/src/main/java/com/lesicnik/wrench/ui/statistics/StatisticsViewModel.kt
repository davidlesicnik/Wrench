package com.lesicnik.wrench.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedPeriod: TimePeriod = TimePeriod.LAST_12_MONTHS,
    val expenseBreakdown: List<ExpenseBreakdownItem> = emptyList(),
    val costTrends: List<CostTrendPoint> = emptyList(),
    val fuelEconomyTrends: List<FuelEconomyPoint> = emptyList(),
    val odometerTrends: List<OdometerPoint> = emptyList(),
    val keyStatistics: KeyStatistics? = null
)

class StatisticsViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val expenseRepository: ExpenseRepository,
    private val vehicleId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Check for cached data first
            val cachedExpenses = expenseRepository.getCachedExpenses(vehicleId)
            if (!forceRefresh && cachedExpenses != null) {
                updateStatistics(cachedExpenses)
                return@launch
            }

            // Show loading only if we don't have data yet
            val hasData = _uiState.value.keyStatistics != null
            if (!hasData) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            val credentials = credentialsRepository.getCredentials()
            if (credentials == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Not logged in"
                )
                return@launch
            }

            when (val result = expenseRepository.getExpenses(
                credentials.serverUrl,
                credentials.apiKey,
                vehicleId,
                forceRefresh = forceRefresh
            )) {
                is ApiResult.Success -> {
                    updateStatistics(result.data)
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

    private fun updateStatistics(allExpenses: List<com.lesicnik.wrench.data.remote.records.Expense>) {
        val period = _uiState.value.selectedPeriod
        val filteredExpenses = StatisticsCalculator.filterExpensesByPeriod(allExpenses, period)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = null,
            expenseBreakdown = StatisticsCalculator.calculateExpenseBreakdown(filteredExpenses),
            costTrends = StatisticsCalculator.calculateCostTrends(filteredExpenses, period),
            fuelEconomyTrends = StatisticsCalculator.calculateFuelEconomyTrends(filteredExpenses),
            odometerTrends = StatisticsCalculator.calculateOdometerTrends(filteredExpenses),
            keyStatistics = StatisticsCalculator.calculateKeyStatistics(filteredExpenses, period)
        )
    }

    fun selectPeriod(period: TimePeriod) {
        if (period == _uiState.value.selectedPeriod) return

        _uiState.value = _uiState.value.copy(selectedPeriod = period)

        // Recalculate with cached data if available
        val cachedExpenses = expenseRepository.getCachedExpenses(vehicleId)
        if (cachedExpenses != null) {
            updateStatistics(cachedExpenses)
        } else {
            loadStatistics()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(
        private val credentialsRepository: CredentialsRepository,
        private val expenseRepository: ExpenseRepository,
        private val vehicleId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatisticsViewModel(credentialsRepository, expenseRepository, vehicleId) as T
        }
    }
}
