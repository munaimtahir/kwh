package com.example.kwh.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import com.example.kwh.settings.SnoozePreferenceReader
import com.example.kwh.testing.FakeMeterDao
import com.example.kwh.testing.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var dao: FakeMeterDao
    private lateinit var repository: MeterRepository
    private lateinit var scheduler: ReminderScheduler
    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-20T00:00:00Z"), ZoneOffset.UTC)

    private fun createViewModel(meterId: Long): HistoryViewModel {
        return HistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("meterId" to meterId)),
            repository = repository,
            reminderScheduler = scheduler
        )
    }

    @Before
    fun setUp() {
        dao = FakeMeterDao()
        repository = MeterRepository(dao, clock, DefaultBillingCycleCalculator())
        scheduler = ReminderScheduler(Application(), object : SnoozePreferenceReader {
            override suspend fun currentSnoozeMinutes(): Int = 60
        })
    }

    @Test
    fun `export includes environment name, safety buffer, and activation log`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterId = repository.addMeter("Meter A", 30, 9, 0)
        repository.assignMeterToEnvironment(meterId, envId)
        repository.updateSafetyBufferDays(meterId, 5)
        repository.addReading(meterId, 10.0, null, clock.millis())
        repository.activateMeter(meterId)
        advanceUntilIdle()

        val viewModel = createViewModel(meterId)
        advanceUntilIdle()

        val deferred = async { viewModel.events.first() }
        viewModel.requestCsvExport()
        val event = deferred.await()
        assertTrue(event is HistoryEvent.ExportCsv)
        val csv = (event as HistoryEvent.ExportCsv).csv

        assertTrue(csv.contains("\"200,300\",House,5\n"))
        assertTrue(csv.contains("# activations"))
    }

    @Test
    fun `import assigns environment, safety buffer, and activation rows`() = runTest {
        val meterId = repository.addMeter("Meter A", 30, 9, 0)
        val viewModel = createViewModel(meterId)
        advanceUntilIdle()

        val csv = buildString {
            append("# version: 2\n")
            append("timestamp,value,notes,billing_anchor_day,thresholds,environment_name,safety_buffer_days\n")
            append("1000,10.0,,1,\"100,150\",Garage,6\n")
            append("# activations\n")
            append("started_at,ended_at\n")
            append("500,900\n")
        }

        val deferred = async { viewModel.events.first() }
        viewModel.importFromCsv(csv)
        val event = deferred.await()
        advanceUntilIdle()

        assertTrue(event is HistoryEvent.Imported)
        val meter = repository.getMeter(meterId)
        assertEquals(6, meter?.safetyBufferDays)
        // Proves the quoted, comma-containing thresholds field didn't shift later columns.
        assertEquals("100,150", meter?.thresholdsCsv)
        val environmentId = meter?.environmentId
        assertEquals("Garage", environmentId?.let { repository.environments.first().firstOrNull { env -> env.id == it }?.name })
        val history = repository.activationHistory(meterId).first()
        assertEquals(1, history.size)
        assertEquals(500L, history.single().startedAt)
        assertEquals(900L, history.single().endedAt)
    }
}
