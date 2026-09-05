package com.example.kwh.ui.home

import android.app.Application
import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.data.MeterEntity
import com.example.kwh.repository.MeterRepository
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.reminders.SwitchReminderScheduler
import com.example.kwh.settings.SnoozePreferenceReader
import com.example.kwh.testing.FakeMeterDao
import com.example.kwh.testing.MainDispatcherRule
import com.example.kwh.ui.common.StringResolver
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: MeterRepository
    private lateinit var scheduler: RecordingScheduler
    private lateinit var switchScheduler: RecordingSwitchScheduler
    private lateinit var viewModel: HomeViewModel
    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-20T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setup() {
        val dao = FakeMeterDao()
        repository = MeterRepository(dao, clock, DefaultBillingCycleCalculator())
        scheduler = RecordingScheduler(Application(), FakeSnoozePreferenceReader())
        switchScheduler = RecordingSwitchScheduler(Application())
        viewModel = HomeViewModel(
            repository = repository,
            reminderScheduler = scheduler,
            switchReminderScheduler = switchScheduler,
            stringResolver = FakeStringResolver()
        )
    }

    @Test
    fun addMeter_blankName_emitsError() = runTest {
        val deferred = async { viewModel.events.first() }
        viewModel.addMeter("   ", 30, 9, 0)
        advanceUntilIdle()
        assertTrue(deferred.await() is HomeEvent.Error)
    }

    @Test
    fun addReading_invalidValue_emitsError() = runTest {
        val deferred = async { viewModel.events.first() }
        viewModel.addReading(meterId = 1, value = -2.0, notes = null, recordedAt = clock.millis())
        advanceUntilIdle()
        assertTrue(deferred.await() is HomeEvent.Error)
    }

    @Test
    fun lowerReading_reportsWarningAndCanBeCorrected() = runTest {
        val id = repository.addMeter("Home", 7, 9, 0)
        repository.addReading(id, 15840.0, null, clock.millis() - 86400000)
        var error: String? = null
        viewModel.addReading(id, 15480.0, null, clock.millis()) { error = it }
        advanceUntilIdle()
        assertTrue(error?.contains("Previous: 15840.0") == true)
        assertTrue(error?.contains("New: 15480.0") == true)
        kotlin.test.assertEquals(1, repository.readingsForMeter(id).first().size)
        viewModel.addReading(id, 15880.0, null, clock.millis()) { error = it }
        advanceUntilIdle()
        kotlin.test.assertNull(error)
        kotlin.test.assertEquals(2, repository.readingsForMeter(id).first().size)
    }

    @Test
    fun rapidSaveCallsCreateOnlyOneReading() = runTest {
        val id = repository.addMeter("Home", 7, 9, 0)
        viewModel.addReading(id, 100.0, null, clock.millis())
        viewModel.addReading(id, 100.0, null, clock.millis() + 1)
        advanceUntilIdle()
        kotlin.test.assertEquals(1, repository.readingsForMeter(id).first().size)
    }

    @Test
    fun environmentGroups_separateFromUngroupedMeters() = runTest {
        val envId = repository.createEnvironment("House")
        val groupedId = repository.addMeter("Grouped", 30, 9, 0)
        repository.assignMeterToEnvironment(groupedId, envId)
        repository.addMeter("Solo", 30, 9, 0)
        repository.activateMeter(groupedId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.environmentGroups.single().meters.single().id == groupedId)
        assertTrue(state.environmentGroups.single().activeMeterId == groupedId)
        assertTrue(state.ungroupedMeters.single().name == "Solo")
    }

    @Test
    fun deleteMeter_disablesReminder() = runTest {
        val meterId = repository.addMeter("Home", 30, 9, 0)
        scheduler.enabledMeters.clear()

        viewModel.deleteMeter(meterId)
        advanceUntilIdle()

        assertTrue(scheduler.disabledMeters.contains(meterId))
    }

    @Test
    fun activateMeter_schedulesCheckForActivatedMeterOnly() = runTest {
        val envId = repository.createEnvironment("House")
        val meterAId = repository.addMeter("Meter A", 30, 9, 0)
        val meterBId = repository.addMeter("Meter B", 30, 9, 0)
        repository.assignMeterToEnvironment(meterAId, envId)
        repository.assignMeterToEnvironment(meterBId, envId)
        viewModel.activateMeter(meterAId)
        advanceUntilIdle()
        switchScheduler.scheduled.clear()
        switchScheduler.cancelled.clear()

        viewModel.activateMeter(meterBId)
        advanceUntilIdle()

        assertTrue(switchScheduler.scheduled.contains(meterBId))
        assertTrue(switchScheduler.cancelled.contains(meterAId))
    }

    private class RecordingSwitchScheduler(
        context: android.content.Context
    ) : com.example.kwh.reminders.SwitchReminderScheduler(context) {
        val scheduled = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()

        override fun scheduleImmediateCheck(meterId: Long, meterName: String) {
            scheduled.add(meterId)
        }

        override fun cancelCheck(meterId: Long) {
            cancelled.add(meterId)
        }
    }

    private class RecordingScheduler(
        context: android.content.Context,
        snoozePreferenceReader: SnoozePreferenceReader
    ) : ReminderScheduler(
        context = context,
        snoozePreferenceReader = snoozePreferenceReader
    ) {
        val enabledMeters = mutableListOf<Long>()
        val disabledMeters = mutableListOf<Long>()

        override fun enableReminder(meter: MeterEntity) {
            enabledMeters.add(meter.id)
        }

        override fun disableReminder(meterId: Long) {
            disabledMeters.add(meterId)
        }
    }

    private class FakeSnoozePreferenceReader : SnoozePreferenceReader {
        override suspend fun currentSnoozeMinutes(): Int = 60
    }

    private class FakeStringResolver : StringResolver {
        override fun get(id: Int, vararg formatArgs: Any): String = id.toString()
    }
}
