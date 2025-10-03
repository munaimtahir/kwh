package com.example.kwh.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kwh.settings.SettingsRepository

/**
 * Receives snooze actions from reminder notifications and schedules a new reminder after
 * the snooze interval. The necessary parameters are provided via intent extras.
 */
open class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = parseSnoozeRequest(intent) ?: return
        scheduleSnoozedReminder(
            context = context,
            meterId = request.meterId,
            meterName = request.meterName,
            frequencyDays = request.frequencyDays,
            hour = request.hour,
            minute = request.minute,
            snoozeMinutes = request.snoozeMinutes
        )
    }

    protected open fun parseSnoozeRequest(intent: Intent): SnoozeRequest? {
        val meterId = intent.getLongExtra(MeterReminderWorker.EXTRA_METER_ID, -1)
        if (meterId <= 0) return null
        val meterName = intent.getStringExtra(MeterReminderWorker.EXTRA_METER_NAME) ?: return null
        val frequencyDays = intent.getIntExtra(MeterReminderWorker.EXTRA_FREQUENCY_DAYS, 30)
        val hour = intent.getIntExtra(MeterReminderWorker.EXTRA_HOUR, 9)
        val minute = intent.getIntExtra(MeterReminderWorker.EXTRA_MINUTE, 0)
        val snoozeMinutes = intent.getIntExtra(
            MeterReminderWorker.EXTRA_SNOOZE_MINUTES,
            SettingsRepository.DEFAULT_SNOOZE
        )
        return SnoozeRequest(
            meterId = meterId,
            meterName = meterName,
            frequencyDays = frequencyDays,
            hour = hour,
            minute = minute,
            snoozeMinutes = snoozeMinutes
        )
    }

    protected open fun scheduleSnoozedReminder(
        context: Context,
        meterId: Long,
        meterName: String,
        frequencyDays: Int,
        hour: Int,
        minute: Int,
        snoozeMinutes: Int
    ) {
        MeterReminderWorker.scheduleSnoozedReminder(
            context = context,
            meterId = meterId,
            meterName = meterName,
            frequencyDays = frequencyDays,
            hour = hour,
            minute = minute,
            snoozeMinutes = snoozeMinutes
        )
    }
}

data class SnoozeRequest(
    val meterId: Long,
    val meterName: String,
    val frequencyDays: Int,
    val hour: Int,
    val minute: Int,
    val snoozeMinutes: Int
)