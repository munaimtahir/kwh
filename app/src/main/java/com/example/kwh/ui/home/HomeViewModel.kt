package com.example.kwh.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.R
import com.example.kwh.repository.MeterOverview
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import com.example.kwh.ui.common.StringResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Instant
import java.time.LocalDate
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
    private val reminderScheduler: ReminderScheduler,
    private val stringResolver: StringResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.meterOverviews.collectLatest { meters ->
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
            emitError(stringResolver.get(R.string.error_meter_name_blank))
            return
        }
        val frequency = reminderFrequencyDays.coerceAtLeast(1)
        val sanitizedHour = hour.coerceIn(0, 23)
        val sanitizedMinute = minute.coerceIn(0, 59)
        viewModelScope.launch {
            runCatching {
                repository.addMeter(sanitizedName, frequency, sanitizedHour, sanitizedMinute)
            }.onSuccess {
            }
        }
    }

    fun addReading(meterId: Long, value: Double, notes: String?) {
        if (value.isNaN() || value <= 0.0) {
            emitError(stringResolver.get(R.string.error_positive_reading))
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
            }.onFailure { throwable ->
                emitError(throwable.message ?: "Failed to update reminder")
            }
        }
    }

    /**
     * Delete the specified meter. On success, the associated reminder is cancelled.
     */
    fun deleteMeter(meterId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteMeter(meterId)
            }.onSuccess { deleted ->
                if (deleted) {
                    reminderScheduler.disableReminder(meterId)
                } else {
                    emitError(stringResolver.get(R.string.error_deleting_meter))
                }
            }.onFailure { throwable ->
                emitError(throwable.message ?: stringResolver.get(R.string.error_deleting_meter))
            }
        }
    }

    private fun MeterOverview.toMeterItem(): MeterItem {
        val stats = cycleStats
        val hasProjection = stats.baseline != null && stats.latest != null
        val threshold = stats.nextThreshold
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
            },
            cycle = CycleUiModel(
                start = stats.window.start,
                end = stats.window.end,
                usedUnits = stats.usedUnits,
                projectedUnits = stats.projectedUnits,
                ratePerDay = stats.ratePerDay,
                hasProjection = hasProjection,
                nextThresholdValue = threshold?.threshold,
                nextThresholdDate = threshold?.eta
            )
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
    val nextReminder: Instant?,
    val cycle: CycleUiModel
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

data class CycleUiModel(
    val start: Instant,
    val end: Instant,
    val usedUnits: Double,
    val projectedUnits: Double,
    val ratePerDay: Double,
    val hasProjection: Boolean,
    val nextThresholdValue: Int?,
    val nextThresholdDate: LocalDate?
)
