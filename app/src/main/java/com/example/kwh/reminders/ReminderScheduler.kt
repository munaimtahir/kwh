package com.example.kwh.reminders

import android.content.Context
import com.example.kwh.data.MeterEntity
import com.example.kwh.settings.SnoozePreferenceReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Schedules and cancels reminders for meters using [MeterReminderWorker]. This class
 * encapsulates access to the current snooze duration stored in [SnoozePreferenceReader]
 * and exposes convenience functions for clients to enable or disable reminders.
 */
open class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snoozePreferenceReader: SnoozePreferenceReader
) {

    /**
     * Enable a reminder for the provided [meter]. A new WorkManager task is scheduled based
     * on the meter's reminder configuration and the user's current snooze duration.
     */
    open fun enableReminder(meter: MeterEntity) {
        val snoozeMinutes = currentSnoozeMinutes()
        scheduleReminder(
            meterId = meter.id,
            meterName = meter.name,
            frequencyDays = meter.reminderFrequencyDays,
            hour = meter.reminderHour,
            minute = meter.reminderMinute,
            snoozeMinutes = snoozeMinutes
        )
    }

    /**
     * Cancel any existing reminder associated with the given [meterId].
     */
    open fun disableReminder(meterId: Long) {
        MeterReminderWorker.cancelReminder(context, meterId)
    }

    protected open fun scheduleReminder(
        meterId: Long,
        meterName: String,
        frequencyDays: Int,
        hour: Int,
        minute: Int,
        snoozeMinutes: Int
    ) {
        MeterReminderWorker.scheduleReminder(
            context = context,
            meterId = meterId,
            meterName = meterName,
            frequencyDays = frequencyDays,
            hour = hour,
            minute = minute,
            snoozeMinutes = snoozeMinutes
        )
    }

    /**
     * Read the current snooze duration from preferences. This is a blocking operation
     * because it is only called from background threads or ViewModel scope.
     */
    protected open fun currentSnoozeMinutes(): Int = runBlocking {
        snoozePreferenceReader.currentSnoozeMinutes()
    }

    companion object {
        /**
         * Compute the next occurrence of a reminder. Ensures the returned time is at least
         * one minute in the future. The [frequencyDays] is coerced to at least one day.
         */
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

        /**
         * Return the duration until the next scheduled reminder. The result will never be
         * negative. If the computed delay is negative, [Duration.ZERO] is returned.
         */
        fun nextReminderDelay(frequencyDays: Int, hour: Int, minute: Int): Duration {
            val next = nextReminderTime(frequencyDays, hour, minute)
            val delay = Duration.between(ZonedDateTime.now(), next)
            return if (delay.isNegative) Duration.ZERO else delay
        }
    }
}