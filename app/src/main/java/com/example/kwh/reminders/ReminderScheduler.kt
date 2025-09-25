package com.example.kwh.reminders

import android.content.Context
import com.example.kwh.data.MeterEntity
import java.time.Duration
import java.time.ZonedDateTime

class ReminderScheduler(private val context: Context) {
    fun enableReminder(meter: MeterEntity) {
        MeterReminderWorker.scheduleReminder(
            context = context,
            meterId = meter.id,
            meterName = meter.name,
            frequencyDays = meter.reminderFrequencyDays,
            hour = meter.reminderHour,
            minute = meter.reminderMinute
        )
    }

    fun disableReminder(meterId: Long) {
        MeterReminderWorker.cancelReminder(context, meterId)
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
