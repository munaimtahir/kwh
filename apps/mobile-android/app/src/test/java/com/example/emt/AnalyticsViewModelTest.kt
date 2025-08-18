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
    fun calculateAnalytics_emptyList_returnsZeros() {
        val usages = emptyList<Usage>()
        val result = viewModel.calculateAnalytics(usages, "1 Month")
        assertEquals(0.0, result.totalKwhThisMonth, 0.01)
        assertEquals(0.0, result.averageDailyKwh, 0.01)
    }
}
