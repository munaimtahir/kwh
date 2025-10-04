package com.example.kwh.ui.metersettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.R
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import com.example.kwh.ui.common.StringResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MeterSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MeterRepository,
    private val reminderScheduler: ReminderScheduler,
    private val stringResolver: StringResolver
) : ViewModel() {

    private val meterId: Long = savedStateHandle.get<Long>("meterId")
        ?: throw IllegalStateException("Missing meterId argument")

    private val _uiState = MutableStateFlow(MeterSettingsUiState())
    val uiState: StateFlow<MeterSettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<MeterSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.meterOverview(meterId).collect { overview ->
                overview?.let { data ->
                    val current = _uiState.value
                    val anchorText = if (current.isDirty) current.anchorDayText else data.meter.billingAnchorDay.toString()
                    val thresholdsText = if (current.isDirty) current.thresholdsText else data.meter.thresholdsCsv
                    val frequencyText = if (current.isDirty) current.reminderFrequencyText else data.meter.reminderFrequencyDays.toString()
                    _uiState.value = current.copy(
                        meterName = data.meter.name,
                        anchorDayText = anchorText,
                        thresholdsText = thresholdsText,
                        reminderFrequencyText = frequencyText,
                        initialAnchorDay = data.meter.billingAnchorDay,
                        initialThresholds = data.meter.thresholdsCsv,
                        initialReminderFrequency = data.meter.reminderFrequencyDays,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onAnchorDayChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(2)
        _uiState.value = _uiState.value.copy(anchorDayText = filtered)
    }

    fun onThresholdsChanged(value: String) {
        _uiState.value = _uiState.value.copy(thresholdsText = value)
    }

    fun onReminderFrequencyChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(3)
        _uiState.value = _uiState.value.copy(reminderFrequencyText = filtered)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        val anchor = state.anchorDayText.toIntOrNull()
        if (anchor == null || anchor !in 1..31) {
            emitError(stringResolver.get(R.string.meter_settings_anchor_error))
            return
        }

        val frequency = state.reminderFrequencyText.toIntOrNull()
        if (frequency == null || frequency < 1) {
            emitError(stringResolver.get(R.string.meter_settings_frequency_error))
            return
        }

        val sanitizedThresholds = sanitizeThresholds(state.thresholdsText)
            ?: run {
                emitError(stringResolver.get(R.string.meter_settings_invalid_thresholds))
                return
            }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            val updated = repository.updateMeterSettings(
                meterId = meterId,
                billingAnchorDay = anchor,
                thresholdsCsv = sanitizedThresholds,
                reminderFrequencyDays = frequency
            )
            if (updated != null) {
                if (updated.reminderEnabled) {
                    reminderScheduler.enableReminder(updated)
                }
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    initialAnchorDay = anchor,
                    initialThresholds = sanitizedThresholds,
                    initialReminderFrequency = frequency,
                    anchorDayText = anchor.toString(),
                    thresholdsText = sanitizedThresholds,
                    reminderFrequencyText = frequency.toString()
                )
                _events.send(MeterSettingsEvent.Saved(stringResolver.get(R.string.meter_settings_saved)))
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false)
                emitError(stringResolver.get(R.string.meter_settings_save_error))
            }
        }
    }

    private fun sanitizeThresholds(input: String): String? {
        if (input.isBlank()) return ""
        val values = mutableListOf<Int>()
        val parts = input.split(',')
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val parsed = trimmed.toIntOrNull() ?: return null
            if (parsed > 0) {
                values += parsed
            }
        }
        return values.distinct().sorted().joinToString(separator = ",")
    }

    private fun emitError(message: String) {
        viewModelScope.launch {
            _events.send(MeterSettingsEvent.Error(message))
        }
    }
}

data class MeterSettingsUiState(
    val meterName: String = "",
    val anchorDayText: String = "1",
    val thresholdsText: String = "",
    val reminderFrequencyText: String = "7",
    val initialAnchorDay: Int = 1,
    val initialThresholds: String = "",
    val initialReminderFrequency: Int = 7,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
) {
    val isDirty: Boolean
        get() = anchorDayText != initialAnchorDay.toString() ||
            thresholdsText != initialThresholds ||
            reminderFrequencyText != initialReminderFrequency.toString()

    val canSave: Boolean
        get() = !isSaving && anchorDayText.isNotBlank() && reminderFrequencyText.isNotBlank()
}

sealed interface MeterSettingsEvent {
    data class Saved(val message: String) : MeterSettingsEvent
    data class Error(val message: String) : MeterSettingsEvent
}
