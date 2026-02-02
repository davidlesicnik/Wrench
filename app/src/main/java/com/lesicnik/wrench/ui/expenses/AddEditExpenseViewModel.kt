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
import java.time.LocalDate

data class AddEditExpenseUiState(
    val expenseType: ExpenseType = ExpenseType.FUEL,
    val date: LocalDate = LocalDate.now(),
    val odometer: String = "",
    val description: String = "",
    val cost: String = "",
    val notes: String = "",
    val fuelConsumed: String = "",
    val isFillToFull: Boolean = true,
    val isMissedFuelUp: Boolean = false,
    val isRecurring: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    // Edit mode state
    val isEditMode: Boolean = false,
    val originalExpense: Expense? = null,
    val showDiscardDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false
) {
    val isDirty: Boolean
        get() {
            val original = originalExpense ?: return false
            // Convert original cost to POS-style digits for comparison (e.g., 45.55 -> "4555")
            val originalCostDigits = ((original.cost * 100).toLong()).toString()
            return expenseType != original.type ||
                    date != original.date ||
                    odometer != (original.odometer?.toString() ?: "") ||
                    description != original.description ||
                    cost != originalCostDigits ||
                    notes != (original.notes ?: "") ||
                    fuelConsumed != (original.liters?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
        }
}

class AddEditExpenseViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val expenseRepository: ExpenseRepository,
    private val vehicleId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditExpenseUiState())
    val uiState: StateFlow<AddEditExpenseUiState> = _uiState.asStateFlow()

    fun initializeForEdit(expenseId: Int, expenseType: ExpenseType) {
        val expense = expenseRepository.getExpenseById(vehicleId, expenseId, expenseType)
        if (expense != null) {
            // Convert cost to POS-style digits (e.g., 45.55 -> "4555")
            val costDigits = ((expense.cost * 100).toLong()).toString()
            _uiState.value = AddEditExpenseUiState(
                expenseType = expense.type,
                date = expense.date,
                odometer = expense.odometer?.toString() ?: "",
                description = expense.description,
                cost = costDigits,
                notes = expense.notes ?: "",
                fuelConsumed = expense.liters?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "",
                isFillToFull = true,
                isMissedFuelUp = false,
                isRecurring = false,
                isEditMode = true,
                originalExpense = expense
            )
        }
    }

    fun onExpenseTypeChanged(type: ExpenseType) {
        _uiState.value = _uiState.value.copy(expenseType = type)
    }

    fun onDateChanged(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date)
    }

    fun onOdometerChanged(odometer: String) {
        _uiState.value = _uiState.value.copy(odometer = odometer.filter { it.isDigit() })
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onCostChanged(cost: String) {
        // POS-style: store only digits, visual transformation handles display
        val digits = cost.filter { it.isDigit() }
        // Limit to reasonable length (max 99,999,999.99 = 10 digits)
        val limited = if (digits.length > 10) digits.takeLast(10) else digits
        // Remove leading zeros (but keep at least one digit if all zeros)
        val trimmed = limited.trimStart('0').ifEmpty { if (limited.isNotEmpty()) "0" else "" }
        _uiState.value = _uiState.value.copy(cost = trimmed)
    }

    // Convert stored digits to decimal for saving (e.g., "4555" -> 45.55)
    fun getCostAsDecimal(): Double? {
        val digits = _uiState.value.cost
        if (digits.isEmpty()) return null
        val padded = digits.padStart(3, '0')
        val intPart = padded.dropLast(2).trimStart('0').ifEmpty { "0" }
        val decPart = padded.takeLast(2)
        return "$intPart.$decPart".toDoubleOrNull()
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun onFuelConsumedChanged(fuelConsumed: String) {
        // Allow digits and one decimal separator (comma or period, normalized to period)
        val filtered = fuelConsumed.filter { it.isDigit() || it == '.' || it == ',' }
            .replace(',', '.')
        val parts = filtered.split(".")
        val sanitized = when {
            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("")
            else -> filtered
        }
        _uiState.value = _uiState.value.copy(fuelConsumed = sanitized)
    }

    fun onFillToFullChanged(isFillToFull: Boolean) {
        _uiState.value = _uiState.value.copy(isFillToFull = isFillToFull)
    }

    fun onMissedFuelUpChanged(isMissedFuelUp: Boolean) {
        _uiState.value = _uiState.value.copy(isMissedFuelUp = isMissedFuelUp)
    }

    fun onRecurringChanged(isRecurring: Boolean) {
        _uiState.value = _uiState.value.copy(isRecurring = isRecurring)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun showDiscardDialog() {
        _uiState.value = _uiState.value.copy(showDiscardDialog = true)
    }

    fun dismissDiscardDialog() {
        _uiState.value = _uiState.value.copy(showDiscardDialog = false)
    }

    fun showDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun confirmDelete() {
        val expense = _uiState.value.originalExpense ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)

            val credentials = credentialsRepository.getCredentials()
            if (credentials == null) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    showDeleteDialog = false,
                    errorMessage = "Not logged in"
                )
                return@launch
            }

            val result = expenseRepository.deleteExpense(
                serverUrl = credentials.serverUrl,
                apiKey = credentials.apiKey,
                vehicleId = vehicleId,
                expenseId = expense.id,
                type = expense.type
            )

            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        showDeleteDialog = false,
                        isDeleted = true
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        showDeleteDialog = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun saveExpense() {
        val state = _uiState.value

        // Validation - convert POS digits to decimal
        val cost = getCostAsDecimal()
        if (cost == null || cost <= 0) {
            _uiState.value = state.copy(errorMessage = "Please enter a valid cost")
            return
        }

        if (state.expenseType != ExpenseType.FUEL && state.description.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter a description")
            return
        }

        if (state.expenseType == ExpenseType.FUEL) {
            val fuelConsumed = state.fuelConsumed.toDoubleOrNull()
            if (fuelConsumed == null || fuelConsumed <= 0) {
                _uiState.value = state.copy(errorMessage = "Please enter fuel amount")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)

            val credentials = credentialsRepository.getCredentials()
            if (credentials == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Not logged in"
                )
                return@launch
            }

            val result = if (state.isEditMode && state.originalExpense != null) {
                // Edit mode: delete old + add new
                expenseRepository.updateExpense(
                    serverUrl = credentials.serverUrl,
                    apiKey = credentials.apiKey,
                    vehicleId = vehicleId,
                    originalExpenseId = state.originalExpense.id,
                    originalType = state.originalExpense.type,
                    type = state.expenseType,
                    date = state.date,
                    odometer = state.odometer.toIntOrNull(),
                    description = if (state.expenseType == ExpenseType.FUEL) "Fuel" else state.description,
                    cost = cost,
                    notes = state.notes,
                    fuelConsumed = state.fuelConsumed.toDoubleOrNull(),
                    isFillToFull = state.isFillToFull,
                    isMissedFuelUp = state.isMissedFuelUp,
                    isRecurring = state.isRecurring
                )
            } else {
                // Add mode
                expenseRepository.addExpense(
                    serverUrl = credentials.serverUrl,
                    apiKey = credentials.apiKey,
                    vehicleId = vehicleId,
                    type = state.expenseType,
                    date = state.date,
                    odometer = state.odometer.toIntOrNull(),
                    description = if (state.expenseType == ExpenseType.FUEL) "Fuel" else state.description,
                    cost = cost,
                    notes = state.notes,
                    fuelConsumed = state.fuelConsumed.toDoubleOrNull(),
                    isFillToFull = state.isFillToFull,
                    isMissedFuelUp = state.isMissedFuelUp,
                    isRecurring = state.isRecurring
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSaved = true
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
        private val expenseRepository: ExpenseRepository,
        private val vehicleId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditExpenseViewModel(credentialsRepository, expenseRepository, vehicleId) as T
        }
    }
}
