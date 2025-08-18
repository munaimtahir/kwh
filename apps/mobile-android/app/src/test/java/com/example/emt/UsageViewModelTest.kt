package com.example.emt

import app.cash.turbine.test
import com.example.emt.data.Usage
import com.example.emt.data.UsageRepository
import com.example.emt.ui.usage.UsageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.util.Date

@ExperimentalCoroutinesApi
class UsageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository = mock(UsageRepository::class.java)
    private lateinit var viewModel: UsageViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addUsage_updates_stream() = runTest {
        val flow = MutableSharedFlow<List<Usage>>(replay = 1)
        `when`(mockRepository.allUsages).thenReturn(flow)
        viewModel = UsageViewModel(mockRepository)

        viewModel.allUsages.test {
            // Initial empty list
            assertEquals(emptyList<Usage>(), awaitItem())

            // Emit a list
            val initialList = listOf(Usage(1, Date(), 10.0))
            flow.emit(initialList)
            assertEquals(initialList, awaitItem())

            // Call addUsage, which should trigger the repository
            // and in a real scenario, the flow would emit a new list.
            // Here we just verify the repository method was called.
            viewModel.addUsage(20.0, Date())
            verify(mockRepository).insert(eq(20.0), any(Date::class.java))
        }
    }
}
