package com.lesicnik.wrench.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedFilters: Set<ExpenseType> = ExpenseType.entries.toSet()
) {
    val filteredExpenses: List<Expense>
        get() = if (selectedFilters.size == ExpenseType.entries.size) {
            expenses
        } else {
            expenses.filter { it.type in selectedFilters }
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
