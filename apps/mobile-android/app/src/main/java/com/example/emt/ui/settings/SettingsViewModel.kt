package com.example.emt.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.emt.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val remindersEnabled: StateFlow<Boolean> = repository.remindersEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reminderTime: StateFlow<String> = repository.reminderTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "12:00")

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRemindersEnabled(enabled)
        }
    }

    fun setReminderTime(time: String) {
        viewModelScope.launch {
            repository.setReminderTime(time)
        }
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
