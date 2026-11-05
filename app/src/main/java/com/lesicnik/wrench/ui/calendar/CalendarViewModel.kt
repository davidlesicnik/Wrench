package com.lesicnik.wrench.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.CredentialsStore
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.data.repository.ReminderRepository
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
    val serverUrl: String? = null
)

class CalendarViewModel(
    private val credentialsStore: CredentialsStore,
    private val reminderRepository: ReminderRepository,
    private val vehicleId: Int
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init { refresh() }

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
            when (val result = reminderRepository.refresh(credentials, vehicleId)) {
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
