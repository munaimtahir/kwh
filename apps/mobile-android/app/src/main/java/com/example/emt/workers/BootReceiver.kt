package com.example.emt.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.emt.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsRepository = SettingsRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val enabled = settingsRepository.remindersEnabledFlow.first()
                if (enabled) {
                    val time = settingsRepository.reminderTimeFlow.first()
                    ReminderScheduler.scheduleReminder(context, time)
                }
            }
        }
    }
}
