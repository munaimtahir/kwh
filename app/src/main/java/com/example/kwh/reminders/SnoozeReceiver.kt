package com.example.kwh.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
        val meterId = intent.getLongExtra(MeterReminderWorker.EXTRA_METER_ID, -1)
        val meterName = intent.getStringExtra(MeterReminderWorker.EXTRA_METER_NAME) ?: return
        val frequency = intent.getIntExtra(MeterReminderWorker.EXTRA_FREQUENCY_DAYS, 30)
        val hour = intent.getIntExtra(MeterReminderWorker.EXTRA_HOUR, 9)
        val minute = intent.getIntExtra(MeterReminderWorker.EXTRA_MINUTE, 0)