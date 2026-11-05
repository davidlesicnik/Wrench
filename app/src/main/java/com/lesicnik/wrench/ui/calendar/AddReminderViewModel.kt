package com.lesicnik.wrench.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.repository.CredentialsStore
import com.lesicnik.wrench.data.repository.Credentials
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.ReminderCreateResult
import com.lesicnik.wrench.data.repository.ReminderDraft
import com.lesicnik.wrench.data.repository.ReminderMetric
import com.lesicnik.wrench.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class MileageInputMode { DISTANCE, ODOMETER }

data class AddReminderUiState(
    val description: String = "",
    val metric: ReminderMetric = ReminderMetric.ODOMETER,
    val dueDate: LocalDate = LocalDate.now(),
    val odometer: String = "",
    val notes: String = "",
    val tags: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
    val requiresCalendarCheck: Boolean = false,
    val initialDate: LocalDate = dueDate,
    val initialMetric: ReminderMetric = metric,
    val mileageInputMode: MileageInputMode = MileageInputMode.DISTANCE,
    val distance: String = "",
    val startingOdometer: String = "",
    val startingReadingEdited: Boolean = false,
    val recordedOdometer: Int? = null,
    val isLoadingOdometer: Boolean = false,
    val odometerLoadError: String? = null
) {
    val targetOdometer: Int? get() {
        if (mileageInputMode == MileageInputMode.ODOMETER) return odometer.toIntOrNull()?.takeIf { it >= 0 }
        val start = startingOdometer.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val interval = distance.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return (start.toLong() + interval).takeIf { it <= Int.MAX_VALUE }?.toInt()
    }
    val draft get() = ReminderDraft(description, metric, dueDate.takeIf { metric != ReminderMetric.ODOMETER },
        targetOdometer?.takeIf { metric != ReminderMetric.DATE }, notes, tags)
    val odometerInvalid get() = metric != ReminderMetric.DATE && mileageInputMode == MileageInputMode.ODOMETER &&
        odometer.isNotBlank() && odometer.toIntOrNull()?.let { it >= 0 } != true
    val distanceInvalid get() = distance.isNotBlank() && (distance.toIntOrNull()?.let { it > 0 } != true ||
        (startingOdometer.toIntOrNull() != null && targetOdometer == null))
    val canSave get() = draft.validationError() == null && !isSaving && !saved && !requiresCalendarCheck &&
        !(metric != ReminderMetric.DATE && mileageInputMode == MileageInputMode.DISTANCE && isLoadingOdometer)
    val savedLocation get() = if (metric == ReminderMetric.ODOMETER) "mileage" else dueDate.toString()
    val hasChanges get() = description.isNotBlank() || notes.isNotBlank() || tags.isNotBlank() ||
        odometer.isNotBlank() || distance.isNotBlank() || startingReadingEdited || dueDate != initialDate || metric != initialMetric
}

class AddReminderViewModel(
    private val credentialsStore: CredentialsStore,
    private val repository: ReminderRepository,
    private val vehicleId: Int,
    initialDate: LocalDate,
    initialMetric: ReminderMetric = ReminderMetric.ODOMETER,
    private val loadOdometer: suspend (Credentials, Int) -> ApiResult<Int> = repository::latestOdometer
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddReminderUiState(dueDate = initialDate, metric = initialMetric))
    val uiState = _uiState.asStateFlow()
    private var odometerRequested = false

    init { if (initialMetric != ReminderMetric.DATE) loadStartingReading() }

    private fun loadStartingReading() {
        if (odometerRequested) return
        odometerRequested = true
        _uiState.update { it.copy(isLoadingOdometer = true) }
        viewModelScope.launch {
            val credentials = credentialsStore.getCredentials()
            val result = if (credentials != null) loadOdometer(credentials, vehicleId)
                else ApiResult.Error("Sign in to load the latest odometer.")
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(isLoadingOdometer = false, recordedOdometer = result.data,
                        startingOdometer = if (state.startingReadingEdited || state.isSaving || state.saved) state.startingOdometer else result.data.toString(),
                        odometerLoadError = null)
                    is ApiResult.Error -> state.copy(isLoadingOdometer = false, odometerLoadError = result.message)
                }
            }
        }
    }

    fun update(transform: (AddReminderUiState) -> AddReminderUiState) {
        if (!_uiState.value.isSaving && !_uiState.value.saved) {
            _uiState.update(transform)
            if (_uiState.value.metric != ReminderMetric.DATE) loadStartingReading()
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        // Set this synchronously so rapid taps cannot launch duplicate POSTs.
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val credentials = credentialsStore.getCredentials()
            if (credentials == null) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Sign in before adding a reminder.") }
                return@launch
            }
            when (val result = repository.create(credentials, vehicleId, state.draft)) {
                is ReminderCreateResult.Created -> _uiState.update { it.copy(isSaving = false, saved = true) }
                is ReminderCreateResult.Failed -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.message, requiresCalendarCheck = result.mayHaveSaved)
                }
            }
        }
    }

    class Factory(
        private val credentialsStore: CredentialsStore,
        private val repository: ReminderRepository,
        private val vehicleId: Int,
        private val initialDate: LocalDate,
        private val initialMetric: ReminderMetric = ReminderMetric.ODOMETER
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddReminderViewModel(credentialsStore, repository, vehicleId, initialDate, initialMetric) as T
    }
}
