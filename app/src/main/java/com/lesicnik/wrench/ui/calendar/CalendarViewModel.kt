package com.lesicnik.wrench.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.Credentials
import com.lesicnik.wrench.data.repository.CredentialsStore
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.data.repository.ReminderRepository
import com.lesicnik.wrench.data.sync.MaintenanceState
import com.lesicnik.wrench.data.sync.ReminderValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = true,
    val fetchedAt: Long? = null,
    val errorMessage: String? = null,
    val serverUrl: String? = null,
    val maintenance: Map<Int, MaintenanceState> = emptyMap(),
    val serviceMatches: List<Expense> = emptyList(),
    val reviewingExpenseId: Int? = null
)

class CalendarViewModel(
    private val credentialsStore: CredentialsStore,
    private val reminderRepository: ReminderRepository,
    private val vehicleId: Int
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        observeMaintenance()
        refresh()
    }

    private fun observeMaintenance() {
        viewModelScope.launch {
            val c = credentialsStore.getCredentials() ?: return@launch
            launch { reminderRepository.observe(c, vehicleId).collect { snapshot ->
                snapshot?.let { value -> _uiState.update { it.copy(reminders = value.reminders, fetchedAt = value.fetchedAt) } }
            } }
            reminderRepository.maintenance.observe(c, vehicleId).collect { value -> _uiState.update { it.copy(maintenance = value) } }
        }
    }

    fun change(reminder: Reminder, target: ReminderValues?) = action {
        reminderRepository.maintenance.queue(it, reminder, target)
    }
    fun cancel(reminderId: Int) = action { reminderRepository.maintenance.cancel(it, vehicleId, reminderId) }
    fun resolve(reminderId: Int, applyMine: Boolean) = action { reminderRepository.maintenance.resolve(it, vehicleId, reminderId, applyMine) }
    fun reviewUpload(expenseId: Int, remoteId: Int?) = action { reminderRepository.maintenance.reviewUpload(it, expenseId, remoteId) }

    fun loadMatches(expenseId: Int) {
        viewModelScope.launch {
            try {
                val c = credentialsStore.getCredentials() ?: return@launch
                val records = reminderRepository.maintenance.matchingServices(c, expenseId)
                _uiState.update { it.copy(serviceMatches = records, reviewingExpenseId = expenseId) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) { _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }
    fun dismissReview() { _uiState.update { it.copy(reviewingExpenseId = null, serviceMatches = emptyList()) } }

    private fun action(block: suspend (Credentials) -> Unit) {
        viewModelScope.launch {
            try {
                val c = credentialsStore.getCredentials() ?: return@launch
                block(c)
                dismissReview()
                reminderRepository.requestSync()
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) { _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun refreshAfterCreation() {
        refreshJob?.cancel()
        refreshJob = null
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val credentials = credentialsStore.getCredentials()
            if (credentials == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Sign in to load your reminders.") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null, serverUrl = credentials.serverUrl) }
            reminderRepository.cached(credentials, vehicleId)?.let { snapshot ->
                _uiState.update { it.copy(reminders = snapshot.reminders, fetchedAt = snapshot.fetchedAt) }
            }
            when (val result = reminderRepository.syncAndRefresh(credentials, vehicleId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(reminders = result.data.reminders, fetchedAt = result.data.fetchedAt, isLoading = false)
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    class Factory(
        private val credentialsStore: CredentialsStore,
        private val repository: ReminderRepository,
        private val vehicleId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CalendarViewModel(credentialsStore, repository, vehicleId) as T
    }
}
