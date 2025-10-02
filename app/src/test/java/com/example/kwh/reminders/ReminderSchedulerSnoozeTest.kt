package com.example.kwh.reminders

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.kwh.data.MeterEntity
import com.example.kwh.settings.SettingsRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config

@Config(sdk = [34])
class ReminderSchedulerSnoozeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var scheduler: ReminderScheduler

    @BeforeTest
    fun setup() {
        clearDataStore()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        WorkManager.getInstance(context).pruneWork().result.get()
        settingsRepository = SettingsRepository(context)
        scheduler = ReminderScheduler(context, settingsRepository)
    }

    @AfterTest
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
        clearDataStore()
    }

    @Test
    fun enableReminder_usesSnoozeMinutesFromSettings() = runBlocking {
        settingsRepository.setSnoozeMinutes(45)
        val meter = MeterEntity(
            id = 42,
            name = "Test",
            reminderEnabled = true,
            reminderFrequencyDays = 1,
            reminderHour = 8,
            reminderMinute = 30
        )

        scheduler.enableReminder(meter)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("meter_reminder_${meter.id}")
            .get()
        assertEquals(1, workInfos.size)
        val workSpec = WorkManagerTestInitHelper.getTestDriver(context)!!
            .getWorkSpec(workInfos.first().id)
        assertNotNull(workSpec)
        val snooze = workSpec.input.getInt("key_snooze_minutes", -1)
        assertEquals(45, snooze)
    }

    @Test
    fun snoozeReceiverSchedulesDelayUsingIntentExtra() {
        val snoozeMinutes = 90
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(MeterReminderWorker.EXTRA_METER_ID, 99L)
            putExtra(MeterReminderWorker.EXTRA_METER_NAME, "Water")
            putExtra(MeterReminderWorker.EXTRA_FREQUENCY_DAYS, 3)
            putExtra(MeterReminderWorker.EXTRA_HOUR, 10)
            putExtra(MeterReminderWorker.EXTRA_MINUTE, 15)
            putExtra(MeterReminderWorker.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }

        SnoozeReceiver().onReceive(context, intent)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("meter_reminder_99")
            .get()
        assertEquals(1, workInfos.size)
        val workSpec = WorkManagerTestInitHelper.getTestDriver(context)!!
            .getWorkSpec(workInfos.first().id)
        assertNotNull(workSpec)
        assertEquals(
            TimeUnit.MINUTES.toMillis(snoozeMinutes.toLong()),
            workSpec.initialDelay
        )
    }

       private fun clearDataStore() {
        val datastoreDir = File(context.filesDir.parentFile, "datastore")
        datastoreDir.listFiles()?.forEach { it.delete() }
    }
}
