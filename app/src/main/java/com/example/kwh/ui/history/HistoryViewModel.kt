package com.example.kwh.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MeterRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val meterId: Long = checkNotNull(savedStateHandle[METER_ID_KEY])

    private val _uiState = MutableStateFlow(HistoryUiState(isLoading = true))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    private var cachedReadings: List<MeterReadingEntity> = emptyList()

    init {
        viewModelScope.launch {
            val meter = repository.getMeter(meterId)
            if (meter == null) {
                _events.send(HistoryEvent.Error("Meter not found"))
                _uiState.value = HistoryUiState(isLoading = false)
                return@launch
            }
            _uiState.update {
                it.copy(
                    meterId = meter.id,
                    meterName = meter.name,
                    reminderEnabled = meter.reminderEnabled,
                    reminderSummary = buildReminderSummary(meter.reminderFrequencyDays, meter.reminderHour, meter.reminderMinute),
                    isLoading = false
                )
            }
            repository.readingsForMeter(meterId).collect { readings ->
                cachedReadings = readings
                updateFilteredReadings(_uiState.value.filter)
            }
        }
    }

    fun onFilterSelected(filter: HistoryFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        updateFilteredReadings(filter)
    }

    fun toggleDeleteMeterDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteMeterDialog = show) }
    }

    fun deleteMeter() {
        viewModelScope.launch {
            val deleted = repository.deleteMeter(meterId)
            if (deleted) {
                reminderScheduler.disableReminder(meterId)
                _events.send(HistoryEvent.MeterDeleted)
            } else {
                _events.send(HistoryEvent.Error("Unable to delete meter"))
            }
            _uiState.update { it.copy(showDeleteMeterDialog = false) }
        }
    }

    fun deleteReading(readingId: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteReading(readingId)
            if (deleted != null) {
                _events.send(HistoryEvent.ShowUndo(deleted))
            } else {
                _events.send(HistoryEvent.Error("Unable to delete reading"))
            }
        }
    }

    fun undoDelete(reading: MeterReadingEntity) {
        viewModelScope.launch {
            repository.restoreReading(reading)
        }
    }

    fun requestCsvExport() {
        viewModelScope.launch {
            val csv = buildCsv(cachedReadings)
            _events.send(HistoryEvent.ExportCsv(csv))
        }
    }

    fun importFromCsv(csv: String) {
        viewModelScope.launch {
            val parsed = CsvParser.parse(csv, meterId)
            if (parsed.isEmpty()) {
                _events.send(HistoryEvent.Error("No readings parsed"))
                return@launch
            }
            runCatching { repository.restoreReadings(parsed) }
                .onSuccess { _events.send(HistoryEvent.Imported(parsed.size)) }
                .onFailure {
                    _events.send(HistoryEvent.Error("Unable to import readings"))
                }
        }
    }

    private fun updateFilteredReadings(filter: HistoryFilter) {
        val threshold = filter.thresholdMillis()
        val filtered = if (threshold == null) {
            cachedReadings
        } else {
            cachedReadings.filter { it.recordedAt >= threshold }
        }
        val readings = filtered.map { entity ->
            HistoryReading(
                id = entity.id,
                value = entity.value,
                notes = entity.notes,
                recordedAt = Instant.ofEpochMilli(entity.recordedAt)
            )
        }
        _uiState.update {
            it.copy(
                readings = readings,
                isEmpty = readings.isEmpty(),
                trend = TrendChartData.from(readings)
            )
        }
    }

    private fun buildCsv(readings: List<MeterReadingEntity>): String {
        val header = "recorded_at,value,notes"
        val rows = readings.sortedBy { it.recordedAt }.joinToString("\n") { reading ->
            val formattedTime = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(reading.recordedAt))
            val value = reading.value
            val notes = reading.notes?.replace("\n", " ") ?: ""
            "$formattedTime,$value,$notes"
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun buildReminderSummary(frequency: Int, hour: Int, minute: Int): String {
        val time = String.format("%02d:%02d", hour, minute)
        return "Every $frequency day(s) at $time"
    }

    companion object {
        const val METER_ID_KEY = "meterId"
    }
}

sealed interface HistoryEvent {
    data class ShowUndo(val reading: MeterReadingEntity) : HistoryEvent
    object MeterDeleted : HistoryEvent
    data class ExportCsv(val csv: String) : HistoryEvent
    data class Imported(val count: Int) : HistoryEvent
    data class Error(val message: String) : HistoryEvent
}

data class HistoryUiState(
    val meterId: Long = 0,
    val meterName: String = "",
    val reminderEnabled: Boolean = false,
    val reminderSummary: String = "",
    val filter: HistoryFilter = HistoryFilter.Days30,
    val readings: List<HistoryReading> = emptyList(),
    val isEmpty: Boolean = false,
    val trend: TrendChartData = TrendChartData(emptyList()),
    val showDeleteMeterDialog: Boolean = false,
    val isLoading: Boolean = false
)

data class HistoryReading(
    val id: Long,
    val value: Double,
    val notes: String?,
    val recordedAt: Instant
)

enum class HistoryFilter(val label: String, private val days: Int?) {
    Days7("7 days", 7),
    Days30("30 days", 30),
    Days90("90 days", 90),
    All("All", null);

    fun thresholdMillis(): Long? {
        val dayCount = days ?: return null
        val cutoff = Instant.now().minusSeconds(dayCount.toLong() * 24 * 60 * 60)
        return cutoff.toEpochMilli()
    }
}

data class TrendChartData(val points: List<TrendPoint>) {
    companion object {
        fun from(readings: List<HistoryReading>): TrendChartData {
            val points = readings.sortedBy { it.recordedAt }.map { reading ->
                TrendPoint(
                    time = reading.recordedAt,
                    value = reading.value
                )
            }
            return TrendChartData(points)
        }
    }
}

data class TrendPoint(val time: Instant, val value: Double)

private object CsvParser {
    fun parse(csv: String, meterId: Long): List<MeterReadingEntity> {
        return csv.lineSequence()
            .dropWhile { it.isBlank() }
            .drop(1) // remove header
            .mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size < 2) return@mapNotNull null
                val instant = runCatching { Instant.parse(parts[0].trim()) }.getOrNull() ?: return@mapNotNull null
                val value = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                val notes = parts.drop(2).joinToString(",").ifBlank { null }
                MeterReadingEntity(
                    id = 0,
                    meterId = meterId,
                    value = value,
                    notes = notes,
                    recordedAt = instant.toEpochMilli()
                )
            }
            .toList()
    }
}
