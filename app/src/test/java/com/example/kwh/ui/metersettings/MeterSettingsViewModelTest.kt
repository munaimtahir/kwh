package com.example.kwh.ui.metersettings

import androidx.lifecycle.SavedStateHandle
import android.app.Application
import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.data.MeterEntity
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.reminders.SwitchReminderScheduler
import com.example.kwh.repository.MeterRepository
import com.example.kwh.settings.SnoozePreferenceReader
import com.example.kwh.testing.FakeMeterDao
import com.example.kwh.testing.MainDispatcherRule
import com.example.kwh.ui.common.StringResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeterSettingsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var dao: FakeMeterDao
    private lateinit var repository: MeterRepository
    private lateinit var scheduler: ReminderScheduler
    private lateinit var switchScheduler: SwitchReminderScheduler
    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-20T00:00:00Z"), ZoneOffset.UTC)
    private var meterId: Long = 0

    private fun createViewModel(): MeterSettingsViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("meterId" to meterId))
        return MeterSettingsViewModel(
            savedStateHandle = savedStateHandle,
            repository = repository,
            reminderScheduler = scheduler,
            switchReminderScheduler = switchScheduler,
            stringResolver = FakeStringResolver()
        )
    }

    @Before
    fun setUp() = runTest {
        dao = FakeMeterDao()
        repository = MeterRepository(dao, clock, DefaultBillingCycleCalculator())
        scheduler = ReminderScheduler(Application(), FakeSnoozePreferenceReader())
        switchScheduler = object : SwitchReminderScheduler(Application()) {
            override fun scheduleImmediateCheck(meterId: Long, meterName: String) {}
            override fun cancelCheck(meterId: Long) {}
        }
        meterId = repository.addMeter("Meter A", 30, 9, 0)
    }

    @Test
    fun `creating and assigning a new environment updates state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewEnvironmentNameChanged("House")
        viewModel.createAndAssignEnvironment()
        advanceUntilIdle()

        assertEquals("House", viewModel.uiState.value.environmentName)
        assertTrue(viewModel.uiState.value.environmentId != null)
    }

    @Test
    fun `blank environment name emits an error and does not assign`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewEnvironmentNameChanged("   ")
        viewModel.createAndAssignEnvironment()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.environmentId)
    }

    @Test
    fun `activating a meter reflects in isActiveMeter`() = runTest {
        val envId = repository.createEnvironment("House")
        repository.assignMeterToEnvironment(meterId, envId)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.activateMeter()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isActiveMeter)
        assertEquals(1, viewModel.uiState.value.activationHistory.size)
    }

    @Test
    fun `unassignEnvironment clears environment fields`() = runTest {
        val envId = repository.createEnvironment("House")
        repository.assignMeterToEnvironment(meterId, envId)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.unassignEnvironment()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.environmentId)
        assertNull(viewModel.uiState.value.environmentName)
    }

    private class FakeSnoozePreferenceReader : SnoozePreferenceReader {
        override suspend fun currentSnoozeMinutes(): Int = 60
    }

    private class FakeStringResolver : StringResolver {
        override fun get(id: Int, vararg formatArgs: Any): String = id.toString()
    }
}
