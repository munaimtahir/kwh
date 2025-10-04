package com.example.kwh.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.repository.MeterRepository
import com.example.kwh.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.BufferedReader
import java.io.StringReader
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * View model backing the history screen. It exposes a [HistoryUiState] that reflects the
 * current meter name, selected filter, list of readings and trend chart data. It also
 * emits one-off [HistoryEvent]s for undo prompts, CSV export/import notifications and
 * errors.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MeterRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val meterId: Long = savedStateHandle.get<Long>("meterId")
        ?: throw IllegalStateException("Missing meterId argument")

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // All readings loaded from the database, unfiltered. Used to recompute when filter changes.
    private var allReadings: List<HistoryReading> = emptyList()

    // Cache the most recently deleted reading for undo support
    private var recentlyDeleted: MeterReadingEntity? = null

    private var billingAnchorDay: Int = 1
    private var thresholdsCsv: String = "200,300"

    init {
        // Load the meter name and readings
        viewModelScope.launch {
            // Fetch meter to get its name
            repository.getMeter(meterId)?.let { meter ->
                billingAnchorDay = meter.billingAnchorDay
                thresholdsCsv = meter.thresholdsCsv
                _uiState.value = _uiState.value.copy(meterName = meter.name)
            }
            // Collect readings for this meter. When they change the UI state is updated.
            repository.readingsForMeter(meterId).collect { entities ->
                val readings = entities.map { entity ->
                    HistoryReading(
                        id = entity.id,
                        value = entity.value,
                        recordedAt = Instant.ofEpochMilli(entity.recordedAt),
                        notes = entity.notes
                    )
                }.sortedByDescending { it.recordedAt }
                allReadings = readings
                applyFilterAndUpdate()
            }
        }
    }

    /**
     * Called when the user selects a different time filter. Updates the UI state accordingly.
     */
    fun onFilterSelected(filter: HistoryFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilterAndUpdate()
    }

    /**
     * Delete a reading by id. After deletion, show an undo snackbar via [HistoryEvent.ShowUndo].
     */
    fun deleteReading(id: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteReading(id)
            if (deleted != null) {
                recentlyDeleted = deleted
                _events.send(
                    HistoryEvent.ShowUndo(
                        HistoryReading(
                            id = deleted.id,
                            value = deleted.value,
                            recordedAt = Instant.ofEpochMilli(deleted.recordedAt),
                            notes = deleted.notes
                        )
                    )
                )
            }
        }
    }

    /**
     * Restore the last deleted reading. This is invoked when the user taps the undo action.
     */
    fun undoDelete(reading: HistoryReading) {
        val deleted = recentlyDeleted ?: return
        viewModelScope.launch {
            repository.restoreReading(deleted)
            recentlyDeleted = null
        }
    }

    /**
     * Show or hide the delete meter confirmation dialog.
     */
    fun toggleDeleteMeterDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteMeterDialog = show)
    }

    /**
     * Permanently delete the current meter. If successful, emit [HistoryEvent.MeterDeleted].
     */
    fun deleteMeter() {
        viewModelScope.launch {
            val deleted = repository.deleteMeter(meterId)
            if (deleted) {
                // Disable any scheduled reminders for this meter
                reminderScheduler.disableReminder(meterId)
                _events.send(HistoryEvent.MeterDeleted)
            } else {
                _events.send(HistoryEvent.Error("Unable to delete meter"))
            }
        }
    }

    /**
     * Request to export the reading history as CSV. Generates a CSV from all readings (not
     * filtered) and emits [HistoryEvent.ExportCsv] with the contents.
     */
    fun requestCsvExport() {
        viewModelScope.launch {
            val csv = buildCsv(allReadings)
            _events.send(HistoryEvent.ExportCsv(csv))
        }
    }

    /**
     * Import readings from a CSV string. Each line is expected to contain comma-separated
     * values: `timestamp,value,notes`. Lines that cannot be parsed are ignored. Emits
     * [HistoryEvent.Imported] with the number of imported rows on success or [HistoryEvent.Error]
     * on failure.
     */
    fun importFromCsv(csv: String) {
        viewModelScope.launch {
            try {
                val newReadings = mutableListOf<MeterReadingEntity>()
                val reader = BufferedReader(StringReader(csv))
                reader.readLine() // skip header if present
                var importedAnchor = 1
                var importedThresholds = "200,300"
                while (true) {
                    val line = reader.readLine() ?: break
                    val parts = line.split(',')
                    if (parts.size < 2) continue
                    val timestamp = parts[0].toLongOrNull()
                    val value = parts[1].toDoubleOrNull()
                    val notes = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                    parts.getOrNull(3)?.toIntOrNull()?.let { anchor ->
                        if (anchor in 1..31) {
                            importedAnchor = anchor
                        }
                    }
                    parts.getOrNull(4)?.let { thresholds ->
                        val trimmed = thresholds.trim()
                        if (trimmed.isNotEmpty()) {
                            importedThresholds = trimmed
                        }
                    }
                    if (timestamp != null && value != null && value > 0.0) {
                        newReadings += MeterReadingEntity(
                            id = 0L,
                            meterId = meterId,
                            value = value,
                            notes = notes,
                            recordedAt = timestamp
                        )
                    }
                }
                if (newReadings.isEmpty()) {
                    _events.send(HistoryEvent.Error("No valid rows to import"))
                    return@launch
                }
                repository.restoreReadings(newReadings)
                repository.getMeter(meterId)?.let { meter ->
                    val updated = meter.copy(
                        billingAnchorDay = importedAnchor,
                        thresholdsCsv = importedThresholds
                    )
                    repository.updateMeter(updated)
                    billingAnchorDay = updated.billingAnchorDay
                    thresholdsCsv = updated.thresholdsCsv
                }
                _events.send(HistoryEvent.Imported(newReadings.size))
            } catch (e: Exception) {
                _events.send(HistoryEvent.Error("Failed to import CSV"))
            }
        }
    }

    /**
     * Apply the currently selected [HistoryFilter] to [allReadings] and update the UI state.
     */
    private fun applyFilterAndUpdate() {
        val filter = _uiState.value.filter
        val filtered = when (val days = filter.days) {
            null -> allReadings
            else -> {
                val cutoff = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
                allReadings.filter { it.recordedAt >= cutoff }
            }
        }
        val trendData = TrendChartData(points = filtered.map { TrendPoint(it.value) })
        _uiState.value = _uiState.value.copy(
            readings = filtered,
            trend = trendData
        )
    }

    /**
     * Convert a list of readings into a CSV string. The first line is a header.
     */
    private fun buildCsv(readings: List<HistoryReading>): String {
        val builder = StringBuilder()
        builder.append("timestamp,value,notes,billing_anchor_day,thresholds\n")
        readings.forEach { reading ->
            builder.append(reading.recordedAt.toEpochMilli())
                .append(',')
                .append(reading.value)
                .append(',')
                .append(reading.notes ?: "")
                .append(',')
                .append(billingAnchorDay)
                .append(',')
                .append(thresholdsCsv)
                .append('\n')
        }
        return builder.toString()
    }
}

/**
 * Immutable UI state for the history screen.
 */
data class HistoryUiState(
    val meterName: String = "",
    val readings: List<HistoryReading> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val trend: TrendChartData = TrendChartData(emptyList()),
    val showDeleteMeterDialog: Boolean = false
) {
    val isEmpty: Boolean get() = readings.isEmpty()
}

/**
 * Represents a single reading for UI consumption. Contains the id, value, recorded time
 * and optional notes.
 */
data class HistoryReading(
    val id: Long,
    val value: Double,
    val recordedAt: Instant,
    val notes: String?
)

/**
 * Enum describing available history filters. Each entry optionally specifies the number of
 * days to include and a short label used in the UI.
 */
enum class HistoryFilter(val days: Int?, val label: String) {
    ALL(null, "All"),
    WEEK(7, "7d"),
    MONTH(30, "30d"),
    YEAR(365, "365d")
}

/**
 * Data class containing points for the trend chart. Each point holds only a value; the
 * position on the X axis is determined by the order of the list.
 */
data class TrendChartData(val points: List<TrendPoint>)

/**
 * A single point in the trend chart. Only the value is needed for drawing.
 */
data class TrendPoint(val value: Double)

/**
 * One-off events emitted by [HistoryViewModel] that are consumed by the UI. Events are
 * processed through a [kotlinx.coroutines.channels.Channel].
 */
sealed interface HistoryEvent {
    /** Prompt the user with an undo option after deleting a reading. */
    data class ShowUndo(val reading: HistoryReading) : HistoryEvent
    /** Emitted after the current meter is deleted; the screen should navigate back. */
    object MeterDeleted : HistoryEvent
    /** Request to export the readings to a CSV. */
    data class ExportCsv(val csv: String) : HistoryEvent
    /** Notify the user that N rows were imported from a CSV. */
    data class Imported(val count: Int) : HistoryEvent
    /** Report an error to the user via a snackbar. */
    data class Error(val message: String) : HistoryEvent
}