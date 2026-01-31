package com.lesicnik.wrench.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.FuelStatistics
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val fuelStatistics: FuelStatistics? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val expenseRepository: ExpenseRepository,
    private val vehicleId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Check if we have cached data available
            val cachedStats = expenseRepository.getCachedFuelStatistics(vehicleId)
            val cachedExpenses = expenseRepository.getCachedExpenses(vehicleId)

            // If we have cached data and not forcing refresh, use it immediately
            if (!forceRefresh && cachedStats != null && cachedExpenses != null) {
                val stats = calculateMonthlyCosts(cachedStats, cachedExpenses)
                _uiState.value = _uiState.value.copy(
                    fuelStatistics = stats,
                    isLoading = false
                )
                return@launch
            }

            // Only show loading if we don't have data yet
            val hasData = _uiState.value.fuelStatistics != null
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
                    val stats = if (expensesResult is ApiResult.Success) {
                        calculateMonthlyCosts(statsResult.data, expensesResult.data)
                    } else {
                        statsResult.data
                    }

                    _uiState.value = _uiState.value.copy(
                        fuelStatistics = stats,
                        isLoading = false
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

    private fun calculateMonthlyCosts(
        stats: FuelStatistics,
        expenses: List<com.lesicnik.wrench.data.remote.records.Expense>
    ): FuelStatistics {
        val now = LocalDate.now()
        val thisMonthStart = now.withDayOfMonth(1)
        val lastMonthStart = thisMonthStart.minusMonths(1)

        val thisMonthExpenses = expenses.filter { it.date >= thisMonthStart }
        val lastMonthExpenses = expenses.filter {
            it.date >= lastMonthStart && it.date < thisMonthStart
        }

        return stats.copy(
            thisMonthFuelCost = thisMonthExpenses
                .filter { it.type == ExpenseType.FUEL }
                .sumOf { it.cost },
            thisMonthOtherCost = thisMonthExpenses
                .filter { it.type != ExpenseType.FUEL }
                .sumOf { it.cost },
            lastMonthFuelCost = lastMonthExpenses
                .filter { it.type == ExpenseType.FUEL }
                .sumOf { it.cost },
            lastMonthOtherCost = lastMonthExpenses
                .filter { it.type != ExpenseType.FUEL }
                .sumOf { it.cost }
        )
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
            return HomeViewModel(credentialsRepository, expenseRepository, vehicleId) as T
        }
    }
}
