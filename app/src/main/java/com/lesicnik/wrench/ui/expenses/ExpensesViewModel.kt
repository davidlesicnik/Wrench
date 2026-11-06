package com.lesicnik.wrench.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.ExpenseSyncSummary
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val syncSummary: ExpenseSyncSummary = ExpenseSyncSummary(),
    val selectedFilters: Set<ExpenseType> = ExpenseType.entries.toSet()
) {
    val filteredExpenses: List<Expense>
        get() = expenses.filter { expense ->
            expense.type in selectedFilters &&
                (dateFrom == null || !expense.date.isBefore(dateFrom)) &&
                (dateTo == null || !expense.date.isAfter(dateTo)) &&
                (searchQuery.isBlank() || listOf(expense.description, expense.notes.orEmpty(), expense.type.name)
                    .any { it.contains(searchQuery.trim(), ignoreCase = true) })
        }
}

class ExpensesViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val expenseRepository: ExpenseRepository,
    private val vehicleId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val credentials = credentialsRepository.getCredentials() ?: return@launch
            launch {
                expenseRepository.observeExpenses(credentials.serverUrl, vehicleId).collect { expenses ->
                    _uiState.value = _uiState.value.copy(expenses = expenses)
                }
            }
            launch {
                expenseRepository.observeSyncSummary(credentials.serverUrl, vehicleId).collect { summary ->
                    _uiState.value = _uiState.value.copy(syncSummary = summary)
                }
            }
        }
        loadExpenses()
    }

    fun loadExpenses(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

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
                    _uiState.value = _uiState.value.copy(
                        expenses = result.data,
                        isLoading = false
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

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setDateRange(from: LocalDate?, to: LocalDate?) {
        _uiState.value = _uiState.value.copy(dateFrom = from, dateTo = to)
    }

    fun resetFilters() {
        _uiState.value = _uiState.value.copy(searchQuery = "", dateFrom = null, dateTo = null,
            selectedFilters = ExpenseType.entries.toSet())
    }

    fun claimLegacyOperations() {
        viewModelScope.launch {
            val c = credentialsRepository.getCredentials() ?: return@launch
            expenseRepository.claimLegacyOperations(c.serverUrl, c.apiKey, vehicleId)
            loadExpenses(forceRefresh = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun toggleFilter(type: ExpenseType) {
        val currentFilters = _uiState.value.selectedFilters
        val newFilters = if (type in currentFilters) {
            currentFilters - type
        } else {
            currentFilters + type
        }
        _uiState.value = _uiState.value.copy(selectedFilters = newFilters)
    }

    fun selectAllFilters() {
        _uiState.value = _uiState.value.copy(selectedFilters = ExpenseType.entries.toSet())
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(selectedFilters = emptySet())
    }

    class Factory(
        private val credentialsRepository: CredentialsRepository,
        private val expenseRepository: ExpenseRepository,
        private val vehicleId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpensesViewModel(credentialsRepository, expenseRepository, vehicleId) as T
        }
    }
}
