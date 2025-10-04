package com.example.kwh.ui.home

import android.app.Application
import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.data.MeterEntity
import com.example.kwh.repository.MeterRepository
import com.example.kwh.reminders.ReminderScheduler
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
    private lateinit var viewModel: HomeViewModel
    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-20T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setup() {
        val dao = FakeMeterDao()
        repository = MeterRepository(dao, DefaultBillingCycleCalculator(), clock)
        scheduler = RecordingScheduler(Application(), FakeSnoozePreferenceReader())
        viewModel = HomeViewModel(
            repository = repository,
            reminderScheduler = scheduler,
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
        viewModel.addReading(meterId = 1, value = -2.0, notes = null)
        advanceUntilIdle()
        assertTrue(deferred.await() is HomeEvent.Error)
    }

    @Test
    fun deleteMeter_disablesReminder() = runTest {
        val meterId = repository.addMeter("Home", 30, 9, 0)
        scheduler.enabledMeters.clear()

        viewModel.deleteMeter(meterId)
        advanceUntilIdle()

        assertTrue(scheduler.disabledMeters.contains(meterId))
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
