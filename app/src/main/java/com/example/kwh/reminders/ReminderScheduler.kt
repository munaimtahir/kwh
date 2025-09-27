package com.example.kwh.reminders

import android.content.Context
import com.example.kwh.data.MeterEntity
import com.example.kwh.settings.SettingsRepository
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReminderScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    fun enableReminder(meter: MeterEntity) {
        val snoozeMinutes = currentSnoozeMinutes()
        MeterReminderWorker.scheduleReminder(
            context = context,
            meterId = meter.id,
            meterName = meter.name,
            frequencyDays = meter.reminderFrequencyDays,
            hour = meter.reminderHour,
            minute = meter.reminderMinute,
            snoozeMinutes = snoozeMinutes
        )
    }

    fun disableReminder(meterId: Long) {
        MeterReminderWorker.cancelReminder(context, meterId)
    }

    private fun currentSnoozeMinutes(): Int = runBlocking {
        settingsRepository.settings.first().snoozeMinutes
    }

    companion object {
        fun nextReminderTime(frequencyDays: Int, hour: Int, minute: Int): ZonedDateTime {
            val now = ZonedDateTime.now()
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) {
                next = next.plusDays(frequencyDays.toLong().coerceAtLeast(1))
            }
            return next
        }

        fun nextReminderDelay(frequencyDays: Int, hour: Int, minute: Int): Duration {
            val next = nextReminderTime(frequencyDays, hour, minute)
            return Duration.between(ZonedDateTime.now(), next)
        }
    }
}
