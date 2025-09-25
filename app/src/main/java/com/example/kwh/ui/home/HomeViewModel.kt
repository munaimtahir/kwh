package com.example.kwh.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.data.MeterWithLatestReading
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MeterRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.metersWithLatestReading.collectLatest { meters ->
                _uiState.value = _uiState.value.copy(
                    meters = meters.map { it.toMeterItem() }
                )
            }
        }
    }

    fun showAddMeterDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddMeterDialog = show)
    }

    fun showAddReadingDialog(meterId: Long?, show: Boolean) {
        _uiState.value = _uiState.value.copy(
            meterIdForReading = if (show) meterId else null,
            showAddReadingDialog = show
        )
    }

    fun addMeter(name: String, reminderFrequencyDays: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.addMeter(name, reminderFrequencyDays, hour, minute)
        }
    }

    fun addReading(meterId: Long, value: Double, notes: String?) {
        viewModelScope.launch {
            repository.addReading(
                meterId = meterId,
                value = value,
                notes = notes,
                recordedAt = System.currentTimeMillis()
            )
        }
    }

    fun updateReminder(
        meterId: Long,
        enabled: Boolean,
        frequencyDays: Int,
        hour: Int,
        minute: Int
    ) {
        viewModelScope.launch {
            val updated = repository.updateReminderConfig(
                meterId = meterId,
                enabled = enabled,
                frequencyDays = frequencyDays,
                hour = hour,
                minute = minute
            )
            if (updated != null) {
                if (enabled) {
                    reminderScheduler.enableReminder(updated)
                } else {
                    reminderScheduler.disableReminder(meterId)
                }
            }
        }
    }

    private fun MeterWithLatestReading.toMeterItem(): MeterItem {
        return MeterItem(
            id = meter.id,
            name = meter.name,
            reminderEnabled = meter.reminderEnabled,
            reminderFrequencyDays = meter.reminderFrequencyDays,
            reminderHour = meter.reminderHour,
            reminderMinute = meter.reminderMinute,
            latestReading = latestReading?.let {
                MeterReading(
                    value = it.value,
                    recordedAt = Instant.ofEpochMilli(it.recordedAt),
                    notes = it.notes
                )
            },
            nextReminder = if (meter.reminderEnabled) {
                ReminderScheduler
                    .nextReminderTime(meter.reminderFrequencyDays, meter.reminderHour, meter.reminderMinute)
                    .toInstant()
            } else {
                null
            }
        )
    }
}

data class HomeUiState(
    val meters: List<MeterItem> = emptyList(),
    val showAddMeterDialog: Boolean = false,
    val showAddReadingDialog: Boolean = false,
    val meterIdForReading: Long? = null
)

data class MeterItem(
    val id: Long,
    val name: String,
    val reminderEnabled: Boolean,
    val reminderFrequencyDays: Int,
    val reminderHour: Int,
    val reminderMinute: Int,
    val latestReading: MeterReading?,
    val nextReminder: Instant?
)

data class MeterReading(
    val value: Double,
    val recordedAt: Instant,
    val notes: String?
)
