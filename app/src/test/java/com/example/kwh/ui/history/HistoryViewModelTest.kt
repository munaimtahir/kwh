package com.example.kwh.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kwh.data.MeterDatabase
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import com.example.kwh.settings.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    private lateinit var database: MeterDatabase
    private lateinit var repository: MeterRepository
    private lateinit var scheduler: ReminderScheduler
    private var meterId: Long = 0

    @BeforeTest
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            MeterDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = MeterRepository(database.meterDao())
        scheduler = object : ReminderScheduler(context, SettingsRepository(context)) {}
        meterId = runBlocking { repository.addMeter("Home", 30, 9, 0) }
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun importFromCsv_emitsImportedEvent() = runTest {
        val viewModel = HistoryViewModel(
            SavedStateHandle(mapOf(HistoryViewModel.METER_ID_KEY to meterId)),
            repository,
            scheduler
        )
        advanceUntilIdle()

        val csv = """
            recorded_at,value,notes
            2024-01-01T00:00:00Z,123.4,Initial
        """.trimIndent()

        val deferred = async { viewModel.events.filterIsInstance<HistoryEvent.Imported>().first() }

        viewModel.importFromCsv(csv)
        advanceUntilIdle()

        val event = deferred.await()
        assertEquals(1, event.count)

        val readings = repository.readingsForMeter(meterId).first()
        assertEquals(1, readings.size)
        assertEquals(123.4, readings.first().value)
    }
}
