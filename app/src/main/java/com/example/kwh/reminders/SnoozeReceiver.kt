package com.example.kwh.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.kwh.settings.SettingsRepository

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val meterId = intent.getLongExtra(MeterReminderWorker.EXTRA_METER_ID, -1)
        val meterName = intent.getStringExtra(MeterReminderWorker.EXTRA_METER_NAME) ?: return
        val frequency = intent.getIntExtra(MeterReminderWorker.EXTRA_FREQUENCY_DAYS, 30)
        val hour = intent.getIntExtra(MeterReminderWorker.EXTRA_HOUR, 9)
        val minute = intent.getIntExtra(MeterReminderWorker.EXTRA_MINUTE, 0)
        if (meterId == -1L) return

        Log.d(TAG, "Snoozing reminder for meter=$meterId")
        MeterReminderWorker.scheduleSnoozedReminder(
            context = context,
            meterId = meterId,
            meterName = meterName,
            frequencyDays = frequency,
            hour = hour,
            minute = minute,
            snoozeMinutes = intent.getIntExtra(MeterReminderWorker.EXTRA_SNOOZE_MINUTES, SettingsRepository.DEFAULT_SNOOZE)
        )
    }

    companion object {
        private const val TAG = "SnoozeReceiver"
    }
}
