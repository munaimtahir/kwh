package com.example.emt

import com.example.emt.data.Usage
import com.example.emt.data.UsageRepository
import com.example.emt.ui.analytics.AnalyticsViewModel
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat

class AnalyticsViewModelTest {

    private lateinit var viewModel: AnalyticsViewModel
    private val mockRepository = mock(UsageRepository::class.java)

    @Before
    fun setup() {
        `when`(mockRepository.allUsages).thenReturn(flowOf(emptyList()))
        viewModel = AnalyticsViewModel(mockRepository)
    }

    @Test
    fun calculateAnalytics_1Month_isCorrect() {
        val now = Calendar.getInstance()
        val usages = listOf(
            Usage(id = 1, date = now.time, kwh = 10.0),
            Usage(id = 2, date = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.time, kwh = 5.0)
        )

        val result = viewModel.calculateAnalytics(usages, "1 Month")

        assertEquals(15.0, result.totalKwhThisMonth, 0.01)
    }

    @Test
    fun calculateAnalytics_groupsChartDataByDay() {
        val cal = Calendar.getInstance()
        val date1 = cal.time
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val date2 = cal.time
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val date3 = cal.time

        val usages = listOf(
            Usage(id = 1, date = date1, kwh = 10.0),
            Usage(id = 2, date = date1, kwh = 5.0), // Same day as 1
            Usage(id = 3, date = date2, kwh = 8.0),
            Usage(id = 4, date = date3, kwh = 12.0)
        )

        val result = viewModel.calculateAnalytics(usages, "7 Days")

        assertEquals(3, result.chartData.size)
        assertEquals(15.0, result.chartData.find { it.first.contains("2025-08-18") }?.second, 0.01) // Adjust date based on test execution
        assertEquals(8.0, result.chartData.find { it.first.contains("2025-08-17") }?.second, 0.01)
        assertEquals(12.0, result.chartData.find { it.first.contains("2025-08-16") }?.second, 0.01)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val date1Str = dateFormat.format(date1)
        val date2Str = dateFormat.format(date2)
        val date3Str = dateFormat.format(date3)

        assertEquals(3, result.chartData.size)
        assertEquals(15.0, result.chartData.find { it.first.contains(date1Str) }?.second, 0.01)
        assertEquals(8.0, result.chartData.find { it.first.contains(date2Str) }?.second, 0.01)
        assertEquals(12.0, result.chartData.find { it.first.contains(date3Str) }?.second, 0.01)
    }

    @Test
    fun calculateAnalytics_emptyList_returnsZeros() {
        val usages = emptyList<Usage>()
        val result = viewModel.calculateAnalytics(usages, "1 Month")
        assertEquals(0.0, result.totalKwhThisMonth, 0.01)
        assertEquals(0.0, result.averageDailyKwh, 0.01)
    }
}
