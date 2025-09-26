package com.example.kwh.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.data.MeterWithLatestReading
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MeterRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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
        val sanitizedName = name.trim()
        if (sanitizedName.isBlank()) {
            emitError("Meter name cannot be empty")
            return
        }
        val frequency = reminderFrequencyDays.coerceAtLeast(1)
        val sanitizedHour = hour.coerceIn(0, 23)
        val sanitizedMinute = minute.coerceIn(0, 59)
        viewModelScope.launch {
            runCatching {
                repository.addMeter(sanitizedName, frequency, sanitizedHour, sanitizedMinute)
            }.onSuccess {
                _events.send(HomeEvent.ShowMessage("Meter added"))
            }.onFailure {
                emitError("Failed to add meter")
            }
        }
    }

    fun addReading(meterId: Long, value: Double, notes: String?) {
        if (value.isNaN() || value <= 0.0) {
            emitError("Enter a positive reading value")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.addReading(
                    meterId = meterId,
                    value = value,
                    notes = notes,
                    recordedAt = System.currentTimeMillis()
                )
            }.onSuccess {
                _events.send(HomeEvent.ShowMessage("Reading saved"))
            }.onFailure {
                emitError("Failed to save reading")
            }
        }
    }

    fun updateReminder(
        meterId: Long,
        enabled: Boolean,
        frequencyDays: Int,
        hour: Int,
        minute: Int
    ) {
        val sanitizedFrequency = frequencyDays.coerceAtLeast(1)
        val sanitizedHour = hour.coerceIn(0, 23)
        val sanitizedMinute = minute.coerceIn(0, 59)
        viewModelScope.launch {
            runCatching {
                repository.updateReminderConfig(
                    meterId = meterId,
                    enabled = enabled,
                    frequencyDays = sanitizedFrequency,
                    hour = sanitizedHour,
                    minute = sanitizedMinute
                )
            }.onSuccess { updated ->
                if (updated != null) {
                    if (enabled) {
                        reminderScheduler.enableReminder(updated)
                    } else {
                        reminderScheduler.disableReminder(meterId)
                    }
                }
            }.onFailure {
                emitError("Failed to update reminder")
            }
        }
    }

    fun deleteMeter(meterId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteMeter(meterId)
            }.onSuccess { deleted ->
                if (deleted) {
                    reminderScheduler.disableReminder(meterId)
                    _events.send(HomeEvent.ShowMessage("Meter deleted"))
                } else {
                    emitError("Meter not found")
                }
            }.onFailure {
                emitError("Failed to delete meter")
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

    private fun emitError(message: String) {
        viewModelScope.launch {
            _events.send(HomeEvent.Error(message))
        }
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

sealed interface HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent
    data class Error(val message: String) : HomeEvent
}
