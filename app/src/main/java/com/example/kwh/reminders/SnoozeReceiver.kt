package com.example.kwh.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kwh.settings.SettingsRepository

/**
 * Receives snooze actions from reminder notifications and schedules a new reminder after
 * the snooze interval. The necessary parameters are provided via intent extras.
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val meterId = intent.getLongExtra(MeterReminderWorker.EXTRA_METER_ID, -1)
        if (meterId <= 0) return
        val meterName = intent.getStringExtra(MeterReminderWorker.EXTRA_METER_NAME) ?: return
        val frequencyDays = intent.getIntExtra(MeterReminderWorker.EXTRA_FREQUENCY_DAYS, 30)
        val hour = intent.getIntExtra(MeterReminderWorker.EXTRA_HOUR, 9)
        val minute = intent.getIntExtra(MeterReminderWorker.EXTRA_MINUTE, 0)
        val snoozeMinutes = intent.getIntExtra(
            MeterReminderWorker.EXTRA_SNOOZE_MINUTES,
            SettingsRepository.DEFAULT_SNOOZE
        )
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