package com.example.kwh.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Simple BroadcastReceiver to handle reminder snoozing.
 * This is a minimal implementation to support the test fix.
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        
        val meterId = intent.getLongExtra(MeterReminderWorker.EXTRA_METER_ID, -1)
        val meterName = intent.getStringExtra(MeterReminderWorker.EXTRA_METER_NAME) ?: return
        val frequency = intent.getIntExtra(MeterReminderWorker.EXTRA_FREQUENCY_DAYS, 30)
        val hour = intent.getIntExtra(MeterReminderWorker.EXTRA_HOUR, 9)
        val minute = intent.getIntExtra(MeterReminderWorker.EXTRA_MINUTE, 0)
        val snoozeMinutes = intent.getIntExtra(MeterReminderWorker.EXTRA_SNOOZE_MINUTES, 60)
        
        if (meterId == -1L) return

        // Schedule a snoozed reminder with the specified delay
        val data = Data.Builder()
            .putLong("key_meter_id", meterId)
            .putString("key_meter_name", meterName)
            .putInt("key_frequency_days", frequency)
            .putInt("key_hour", hour)
            .putInt("key_minute", minute)
            .putInt("key_snooze_minutes", snoozeMinutes)
            .build()

        val request = OneTimeWorkRequestBuilder<MeterReminderWorker>()
            .setInputData(data)
            .setInitialDelay(snoozeMinutes.toLong(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "meter_reminder_$meterId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}