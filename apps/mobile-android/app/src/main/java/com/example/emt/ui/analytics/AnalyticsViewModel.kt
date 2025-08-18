package com.example.emt.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.emt.data.Usage
import com.example.emt.data.UsageRepository
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Date

data class AnalyticsUiState(
    val totalKwhThisMonth: Double = 0.0,
    val averageDailyKwh: Double = 0.0,
    val chartData: List<Pair<String, Double>> = emptyList()
)

class AnalyticsViewModel(private val repository: UsageRepository) : ViewModel() {

    private val _selectedTimePeriod = MutableStateFlow("1 Month")
    val selectedTimePeriod: StateFlow<String> = _selectedTimePeriod.asStateFlow()

    val uiState: StateFlow<AnalyticsUiState> = repository.allUsages
        .combine(selectedTimePeriod) { usages, period ->
            calculateAnalytics(usages, period)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsUiState()
        )

    fun setTimePeriod(period: String) {
        _selectedTimePeriod.value = period
    }

    internal fun calculateAnalytics(usages: List<Usage>, period: String): AnalyticsUiState {
        if (usages.isEmpty()) return AnalyticsUiState()

        val now = Calendar.getInstance()
        val (startDate, endDate) = when (period) {
            "7 Days" -> {
                val start = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.time
                Pair(start, now.time)
            }
            "30 Days" -> {
                val start = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -30) }.time
                Pair(start, now.time)
            }
            else -> { // "1 Month"
                val start = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.time
                Pair(start, now.time)
            }
        }

        val filteredUsages = usages.filter { it.date in startDate..endDate }
        if (filteredUsages.isEmpty()) return AnalyticsUiState()

        val totalKwh = filteredUsages.sumOf { it.kwh }
        val daysInRange = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)) + 1
        val averageKwh = totalKwh / daysInRange

        // For simplicity, chart data will just be the kwh values for now.
        // A real implementation would group by day/week.
        val chartData = filteredUsages.map {
            Pair(it.date.toString(), it.kwh)
        }

        return AnalyticsUiState(
            totalKwhThisMonth = totalKwh, // Note: This is total for the selected period
            averageDailyKwh = averageKwh,
            chartData = chartData
        )
    }
}

class AnalyticsViewModelFactory(private val repository: UsageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
