package com.example.kwh.ui.home

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kwh.data.MeterDatabase
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import com.example.kwh.settings.SettingsRepository
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelTest {

    private lateinit var database: MeterDatabase
    private lateinit var repository: MeterRepository
    private lateinit var scheduler: RecordingScheduler
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeterDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = MeterRepository(database.meterDao())
        scheduler = RecordingScheduler(
            ApplicationProvider.getApplicationContext(),
            SettingsRepository(ApplicationProvider.getApplicationContext())
        )
        viewModel = HomeViewModel(repository, scheduler)
    }

    @After
    fun tearDown() {
        database.close()
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
        settingsRepository: SettingsRepository
    ) : ReminderScheduler(context, settingsRepository) {
        val enabledMeters = mutableListOf<Long>()
        val disabledMeters = mutableListOf<Long>()

        override fun enableReminder(meter: com.example.kwh.data.MeterEntity) {
            enabledMeters.add(meter.id)
        }

        override fun disableReminder(meterId: Long) {
            disabledMeters.add(meterId)
        }
    }
}
