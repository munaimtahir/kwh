package com.example.kwh.reminders

import android.app.Application
import android.content.Intent
import com.example.kwh.data.MeterEntity
import com.example.kwh.settings.SnoozePreferenceReader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class ReminderSchedulerSnoozeTest {

    private val context = Application()

    @Test
    fun enableReminder_usesSnoozeMinutesFromSettings() {
        val scheduler = RecordingScheduler(
            context = context,
            snoozePreferenceReader = FakeSnoozePreferenceReader(45)
        )
        val meter = MeterEntity(
            id = 42,
            name = "Test",
            reminderEnabled = true,
            reminderFrequencyDays = 1,
            reminderHour = 8,
            reminderMinute = 30
        )

        scheduler.enableReminder(meter)

        val scheduled = scheduler.lastScheduled
        assertNotNull(scheduled)
        assertEquals(45, scheduled.snoozeMinutes)
        assertEquals(meter.id, scheduled.meterId)
    }

    @Test
    fun snoozeReceiverSchedulesDelayUsingIntentExtra() {
        val receiver = RecordingSnoozeReceiver()
        val snoozeMinutes = 90
        receiver.nextParsedRequest = SnoozeRequest(
            meterId = 99L,
            meterName = "Water",
            frequencyDays = 3,
            hour = 10,
            minute = 15,
            snoozeMinutes = snoozeMinutes
        )

        receiver.onReceive(context, Intent())

        val scheduled = receiver.lastRequest
        assertNotNull(scheduled)
        assertEquals(99L, scheduled.meterId)
        assertEquals(snoozeMinutes, scheduled.snoozeMinutes)
    }

    private class RecordingScheduler(
        context: android.content.Context,
        snoozePreferenceReader: SnoozePreferenceReader
    ) : ReminderScheduler(context, snoozePreferenceReader) {
        var lastScheduled: ScheduledReminder? = null

        override fun scheduleReminder(
            meterId: Long,
            meterName: String,
            frequencyDays: Int,
            hour: Int,
            minute: Int,
            snoozeMinutes: Int
        ) {
            lastScheduled = ScheduledReminder(
                meterId = meterId,
                meterName = meterName,
                frequencyDays = frequencyDays,
                hour = hour,
                minute = minute,
                snoozeMinutes = snoozeMinutes
            )
        }
    }

    private data class ScheduledReminder(
        val meterId: Long,
        val meterName: String,
        val frequencyDays: Int,
        val hour: Int,
        val minute: Int,
        val snoozeMinutes: Int
    )

    private class FakeSnoozePreferenceReader(
        private val snoozeMinutes: Int
    ) : SnoozePreferenceReader {
        override suspend fun currentSnoozeMinutes(): Int = snoozeMinutes
    }

    private class RecordingSnoozeReceiver : SnoozeReceiver() {
        var lastRequest: ScheduledReminder? = null
        var nextParsedRequest: SnoozeRequest? = null

        override fun parseSnoozeRequest(intent: Intent): SnoozeRequest? {
            return nextParsedRequest
        }

        override fun scheduleSnoozedReminder(
            context: android.content.Context,
            meterId: Long,
            meterName: String,
            frequencyDays: Int,
            hour: Int,
            minute: Int,
            snoozeMinutes: Int
        ) {
            lastRequest = ScheduledReminder(
                meterId = meterId,
                meterName = meterName,
                frequencyDays = frequencyDays,
                hour = hour,
                minute = minute,
                snoozeMinutes = snoozeMinutes
            )
        }
    }
}
