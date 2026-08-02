package com.example.kwh.ui.metersettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.R
import com.example.kwh.data.EnvironmentEntity
import com.example.kwh.data.MeterActivationEntity
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.reminders.SwitchReminderScheduler
import com.example.kwh.repository.CalibrationAccuracy
import com.example.kwh.repository.MeterOverview
import com.example.kwh.repository.MeterRepository
import com.example.kwh.ui.common.StringResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MeterSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MeterRepository,
    private val reminderScheduler: ReminderScheduler,
    private val switchReminderScheduler: SwitchReminderScheduler,
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
            combine(
                repository.meterOverview(meterId),
                repository.environments,
                repository.activationHistory(meterId),
                repository.isMeterActive(meterId),
                repository.calibrationAccuracy(meterId)
            ) { overview, environments, history, isActive, calibration ->
                MeterSettingsSnapshot(overview, environments, history, isActive, calibration)
            }.collect { snapshot ->
                val data = snapshot.overview ?: return@collect
                val current = _uiState.value
                val nameText = if (current.isDirty) current.meterNameText else data.meter.name
                val anchorText = if (current.isDirty) current.anchorDayText else data.meter.billingAnchorDay.toString()
                val thresholdsText = if (current.isDirty) current.thresholdsText else data.meter.thresholdsCsv
                val frequencyText = if (current.isDirty) current.reminderFrequencyText else data.meter.reminderFrequencyDays.toString()
                val safetyBufferText = if (current.isDirty) current.safetyBufferDaysText else data.meter.safetyBufferDays.toString()
                _uiState.value = current.copy(
                    meterName = data.meter.name,
                    meterNameText = nameText,
                    anchorDayText = anchorText,
                    thresholdsText = thresholdsText,
                    reminderFrequencyText = frequencyText,
                    safetyBufferDaysText = safetyBufferText,
                    initialMeterName = data.meter.name,
                    initialAnchorDay = data.meter.billingAnchorDay,
                    initialThresholds = data.meter.thresholdsCsv,
                    initialReminderFrequency = data.meter.reminderFrequencyDays,
                    initialSafetyBufferDays = data.meter.safetyBufferDays,
                    environmentId = data.meter.environmentId,
                    environmentName = snapshot.environments.firstOrNull { it.id == data.meter.environmentId }?.name,
                    availableEnvironments = snapshot.environments.map { EnvironmentOption(it.id, it.name) },
                    isActiveMeter = snapshot.isActive,
                    activationHistory = snapshot.history.map { ActivationHistoryItem(it.startedAt, it.endedAt) },
                    calibrationAccuracy = snapshot.calibration,
                    isLoading = false
                )
            }
        }
    }

    fun onMeterNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(meterNameText = value)
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

    fun onSafetyBufferDaysChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(2)
        _uiState.value = _uiState.value.copy(safetyBufferDaysText = filtered)
    }

    fun toggleDeleteDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteDialog = show)
    }

    fun deleteMeter() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            val deleted = repository.deleteMeter(meterId)
            if (deleted) {
                reminderScheduler.disableReminder(meterId)
                switchReminderScheduler.cancelCheck(meterId)
                _uiState.value = _uiState.value.copy(showDeleteDialog = false)
                _events.send(MeterSettingsEvent.Deleted)
            } else {
                _uiState.value = _uiState.value.copy(isDeleting = false, showDeleteDialog = false)
                emitError(stringResolver.get(R.string.error_deleting_meter))
            }
        }
    }

    fun toggleEnvironmentDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showEnvironmentDialog = show,
            newEnvironmentNameText = if (show) "" else _uiState.value.newEnvironmentNameText
        )
    }

    fun onNewEnvironmentNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(newEnvironmentNameText = value)
    }

    fun selectEnvironment(environmentId: Long) {
        viewModelScope.launch {
            repository.assignMeterToEnvironment(meterId, environmentId)
            _uiState.value = _uiState.value.copy(showEnvironmentDialog = false)
        }
    }

    fun unassignEnvironment() {
        viewModelScope.launch {
            repository.assignMeterToEnvironment(meterId, null)
            switchReminderScheduler.cancelCheck(meterId)
            _uiState.value = _uiState.value.copy(showEnvironmentDialog = false)
        }
    }

    fun createAndAssignEnvironment() {
        val name = _uiState.value.newEnvironmentNameText.trim()
        if (name.isBlank()) {
            emitError(stringResolver.get(R.string.error_environment_name_blank))
            return
        }
        viewModelScope.launch {
            val environmentId = repository.createEnvironment(name)
            repository.assignMeterToEnvironment(meterId, environmentId)
            _uiState.value = _uiState.value.copy(showEnvironmentDialog = false, newEnvironmentNameText = "")
        }
    }

    fun activateMeter() {
        viewModelScope.launch {
            val activated = repository.activateMeter(meterId)
            val environmentId = activated?.environmentId ?: return@launch
            repository.metersInEnvironment(environmentId).forEach { sibling ->
                if (sibling.id == meterId) {
                    switchReminderScheduler.scheduleImmediateCheck(sibling.id, sibling.name)
                } else {
                    switchReminderScheduler.cancelCheck(sibling.id)
                }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        val name = state.meterNameText.trim()
        if (name.isBlank()) {
            emitError(stringResolver.get(R.string.meter_settings_name_error))
            return
        }

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

        val safetyBufferDays = state.safetyBufferDaysText.toIntOrNull()
        if (safetyBufferDays == null || safetyBufferDays !in
            MeterRepository.MIN_SAFETY_BUFFER_DAYS..MeterRepository.MAX_SAFETY_BUFFER_DAYS
        ) {
            emitError(stringResolver.get(R.string.meter_settings_safety_buffer_error))
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)

            // First update meter name if changed
            val meter = repository.getMeter(meterId)
            if (meter != null && meter.name != name) {
                val updatedMeter = meter.copy(name = name)
                repository.updateMeter(updatedMeter)
            }

            val updated = repository.updateMeterSettings(
                meterId = meterId,
                billingAnchorDay = anchor,
                thresholdsCsv = sanitizedThresholds,
                reminderFrequencyDays = frequency
            )
            if (updated != null && updated.environmentId != null) {
                repository.updateSafetyBufferDays(meterId, safetyBufferDays)
            }
            if (updated != null) {
                if (updated.reminderEnabled) {
                    reminderScheduler.enableReminder(updated)
                }
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    meterName = name,
                    meterNameText = name,
                    initialMeterName = name,
                    initialAnchorDay = anchor,
                    initialThresholds = sanitizedThresholds,
                    initialReminderFrequency = frequency,
                    initialSafetyBufferDays = safetyBufferDays,
                    anchorDayText = anchor.toString(),
                    thresholdsText = sanitizedThresholds,
                    reminderFrequencyText = frequency.toString(),
                    safetyBufferDaysText = safetyBufferDays.toString()
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
    val meterNameText: String = "",
    val anchorDayText: String = "1",
    val thresholdsText: String = "",
    val reminderFrequencyText: String = "7",
    val safetyBufferDaysText: String = "3",
    val initialMeterName: String = "",
    val initialAnchorDay: Int = 1,
    val initialThresholds: String = "",
    val initialReminderFrequency: Int = 7,
    val initialSafetyBufferDays: Int = 3,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val environmentId: Long? = null,
    val environmentName: String? = null,
    val availableEnvironments: List<EnvironmentOption> = emptyList(),
    val isActiveMeter: Boolean = false,
    val activationHistory: List<ActivationHistoryItem> = emptyList(),
    val calibrationAccuracy: CalibrationAccuracy? = null,
    val showEnvironmentDialog: Boolean = false,
    val newEnvironmentNameText: String = ""
) {
    val isDirty: Boolean
        get() = meterNameText != initialMeterName ||
            anchorDayText != initialAnchorDay.toString() ||
            thresholdsText != initialThresholds ||
            reminderFrequencyText != initialReminderFrequency.toString() ||
            (environmentId != null && safetyBufferDaysText != initialSafetyBufferDays.toString())

    val canSave: Boolean
        get() = !isSaving && meterNameText.isNotBlank() && anchorDayText.isNotBlank() && reminderFrequencyText.isNotBlank()
}

data class EnvironmentOption(val id: Long, val name: String)

data class ActivationHistoryItem(val startedAt: Long, val endedAt: Long?)

private data class MeterSettingsSnapshot(
    val overview: MeterOverview?,
    val environments: List<EnvironmentEntity>,
    val history: List<MeterActivationEntity>,
    val isActive: Boolean,
    val calibration: CalibrationAccuracy?
)

sealed interface MeterSettingsEvent {
    data class Saved(val message: String) : MeterSettingsEvent
    data class Error(val message: String) : MeterSettingsEvent
    object Deleted : MeterSettingsEvent
}
