package com.example.kwh.reminders

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Enqueues/cancels the daily [SwitchReminderWorker] check for a meter. Scheduling is driven by
 * activation state changes (see `HomeViewModel`/`MeterSettingsViewModel`/`MeterDetailViewModel`
 * `activateMeter` calls) rather than a user-facing toggle: any meter that becomes the active one
 * in its environment gets a check scheduled, and its former sibling's check is cancelled.
 */
open class SwitchReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun scheduleImmediateCheck(meterId: Long, meterName: String) {
        SwitchReminderWorker.scheduleImmediateCheck(context, meterId, meterName)
    }

    open fun cancelCheck(meterId: Long) {
        SwitchReminderWorker.cancelCheck(context, meterId)
    }
}
