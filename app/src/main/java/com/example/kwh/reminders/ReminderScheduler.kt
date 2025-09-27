package com.example.kwh.reminders

import android.content.Context
import com.example.kwh.data.MeterEntity
import com.example.kwh.settings.SettingsRepository
import java.time.Duration
import java.time.ZonedDateTime
        MeterReminderWorker.scheduleReminder(
            context = context,
            meterId = meter.id,
            meterName = meter.name,
            frequencyDays = meter.reminderFrequencyDays,
            hour = meter.reminderHour,
            minute = meter.reminderMinute,
        )
    }

    open fun disableReminder(meterId: Long) {
        MeterReminderWorker.cancelReminder(context, meterId)
    }

    private fun currentSnoozeMinutes(): Int = runBlocking {
        settingsRepository.settings.first().snoozeMinutes
    }

    companion object {
        fun nextReminderTime(frequencyDays: Int, hour: Int, minute: Int): ZonedDateTime {
            val now = ZonedDateTime.now()
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            val minNext = now.plusMinutes(1)
            val step = frequencyDays.toLong().coerceAtLeast(1)
            while (!next.isAfter(minNext)) {
                next = next.plusDays(step)
            }
            return next
        }

        fun nextReminderDelay(frequencyDays: Int, hour: Int, minute: Int): Duration {
            val next = nextReminderTime(frequencyDays, hour, minute)
            val delay = Duration.between(ZonedDateTime.now(), next)
            return if (delay.isNegative) Duration.ZERO else delay
        }
    }
}
