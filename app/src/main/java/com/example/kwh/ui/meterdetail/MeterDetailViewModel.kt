package com.example.kwh.ui.meterdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MeterDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MeterRepository
) : ViewModel() {

    val meterId: Long = savedStateHandle.get<Long>("meterId")
        ?: throw IllegalStateException("Missing meterId argument")

    private val _uiState = MutableStateFlow(MeterDetailUiState())
    val uiState: StateFlow<MeterDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<MeterDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.meterOverview(meterId).collect { overview ->
                if (overview != null) {
                    val meter = overview.meter
                    val stats = overview.cycleStats
                    val hasProjection = stats.baseline != null && stats.latest != null
                    val threshold = stats.nextThreshold

                    val nextReminder = if (meter.reminderEnabled) {
                        ReminderScheduler
                            .nextReminderTime(
                                meter.reminderFrequencyDays,
                                meter.reminderHour,
                                meter.reminderMinute
                            )
                            .toInstant()
                    } else {
                        null
                    }

                    _uiState.value = MeterDetailUiState(
                        meterName = meter.name,
                        lastReading = overview.latestReading?.value,
                        lastRecordedDate = overview.latestReading?.let {
                            Instant.ofEpochMilli(it.recordedAt)
                        },
                        usedUnits = stats.usedUnits,
                        projectedUnits = stats.projectedUnits,
                        ratePerDay = stats.ratePerDay,
                        hasProjection = hasProjection,
                        nextReminder = nextReminder,
                        cycleInfo = CycleInfo(
                            start = stats.window.start,
                            end = stats.window.end,
                            usedUnits = stats.usedUnits,
                            projectedUnits = stats.projectedUnits,
                            nextThresholdValue = threshold?.threshold,
                            nextThresholdDate = threshold?.eta
                        )
                    )
                }
            }
        }
    }
}

data class MeterDetailUiState(
    val meterName: String = "",
    val lastReading: Double? = null,
    val lastRecordedDate: Instant? = null,
    val usedUnits: Double = 0.0,
    val projectedUnits: Double = 0.0,
    val ratePerDay: Double = 0.0,
    val hasProjection: Boolean = false,
    val nextReminder: Instant? = null,
    val cycleInfo: CycleInfo? = null
)

data class CycleInfo(
    val start: Instant,
    val end: Instant,
    val usedUnits: Double,
    val projectedUnits: Double,
    val nextThresholdValue: Int?,
    val nextThresholdDate: LocalDate?
)

sealed interface MeterDetailEvent {
    data class ShowMessage(val message: String) : MeterDetailEvent
    data class Error(val message: String) : MeterDetailEvent
}
