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
    fun `CSV v2 round trip preserves reading metadata settings environment and activations`() = runTest {
        val source = repository.addMeter("Source", 30, 9, 0)
        val env = repository.createEnvironment("House, East")
        repository.assignMeterToEnvironment(source, env)
        repository.updateMeterSettings(source, 31, "200,300,400", 30)
        repository.updateSafetyBufferDays(source, 5)
        repository.addReading(source, 14000.0, "note, with \"quotes\"\nand a second line", 1000)
        repository.addReading(source, 14125.0, null, 86401000)
        repository.restoreActivations(source, listOf(500L to 900L, 1000L to null))
        val exporter = createViewModel(source)
        advanceUntilIdle()
        val export = async { exporter.events.first() }
        exporter.requestCsvExport()
        val csv = (export.await() as HistoryEvent.ExportCsv).csv
        val target = repository.addMeter("Target", 30, 9, 0)
        val importer = createViewModel(target)
        advanceUntilIdle()
        val imported = async { importer.events.first() }
        importer.importFromCsv(csv)
        assertTrue(imported.await() is HistoryEvent.Imported)
        val meter = repository.getMeter(target)!!
        assertEquals(31, meter.billingAnchorDay)
        assertEquals("200,300,400", meter.thresholdsCsv)
        assertEquals(env, meter.environmentId)
        assertEquals(5, meter.safetyBufferDays)
        assertEquals(repository.readingsForMeter(source).first().map { Triple(it.recordedAt, it.value, it.notes) },
            repository.readingsForMeter(target).first().map { Triple(it.recordedAt, it.value, it.notes) })
        assertEquals(repository.activationHistory(source).first().map { it.startedAt to it.endedAt },
            repository.activationHistory(target).first().map { it.startedAt to it.endedAt })
        assertEquals("Source", repository.getMeter(source)?.name)
        assertEquals("Target", meter.name) // CSV v2 imports into the selected existing meter.
    }

    @Test
    fun `CSV v2 header cannot assign an ungrouped meter to a fabricated environment`() = runTest {
        val source = repository.addMeter("Source", 7, 9, 0)
        repository.addReading(source, 100.0, null, 1000)
        val exporter = createViewModel(source)
        advanceUntilIdle()
        val export = async { exporter.events.first() }
        exporter.requestCsvExport()
        val csv = (export.await() as HistoryEvent.ExportCsv).csv
        val target = repository.addMeter("Target", 7, 9, 0)
        val importer = createViewModel(target)
        advanceUntilIdle()
        val imported = async { importer.events.first() }
        importer.importFromCsv(csv)
        assertTrue(imported.await() is HistoryEvent.Imported)
        kotlin.test.assertNull(repository.getMeter(target)?.environmentId)
        assertTrue(repository.environments.first().isEmpty())
        assertEquals("200,300", repository.getMeter(target)?.thresholdsCsv)
        assertEquals(100.0, repository.readingsForMeter(target).first().single().value)
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
