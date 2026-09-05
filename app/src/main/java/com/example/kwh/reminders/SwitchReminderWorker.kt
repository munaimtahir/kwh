package com.example.kwh.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.kwh.billing.SwitchUrgency
import com.example.kwh.repository.MeterRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Daily self-rescheduling check for the switch recommendation of a single (environment-assigned,
 * currently active) meter. Reuses the [MeterReminderWorker] self-rescheduling pattern: this worker
 * decides whether to notify, then always re-enqueues itself for the next day as long as the meter
 * is still the active one in its environment.
 */
class SwitchReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository: MeterRepository by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        entryPoint.meterRepository()
    }

    override suspend fun doWork(): Result {
        val meterId = inputData.getLong(KEY_METER_ID, -1)
        if (meterId == -1L) {
            Log.w(TAG, "Switch check triggered without meter id")
            return Result.failure()
        }

        val meterName = inputData.getString(KEY_METER_NAME) ?: return Result.failure()

        val meter = repository.getMeter(meterId)
        if (meter == null || meter.environmentId == null) {
            // Meter was deleted or unassigned; stop rescheduling.
            return Result.success()
        }
        val stillActive = repository.isMeterActive(meterId).first()
        if (!stillActive) {
            // No longer the active meter; a switch (or unassignment) should already have
            // cancelled this work, but stop rescheduling here as a safety net.
            return Result.success()
        }

        val recommendation = repository.getSwitchRecommendation(meterId)
        if (recommendation != null && recommendation.urgency != SwitchUrgency.NORMAL) {
            showNotification(meterId, meterName, recommendation.urgency, recommendation.switchByDate)
        }

        // Keep checking daily regardless of today's recommendation: usage (and therefore the
        // rate) can change day to day while this meter remains active.
        scheduleCheck(applicationContext, meterId, meterName)

        return Result.success()
    }

    private fun showNotification(
        meterId: Long,
        meterName: String,
        urgency: SwitchUrgency,
        switchByDate: java.time.LocalDate
    ) {
        ensureChannel()
        val formattedDate = switchByDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val titleRes = if (urgency == SwitchUrgency.OVERDUE) {
            com.example.kwh.R.string.switch_notification_title_overdue
        } else {
            com.example.kwh.R.string.switch_notification_title_soon
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(applicationContext.getString(titleRes))
            .setContentText(
                applicationContext.getString(
                    com.example.kwh.R.string.switch_notification_body,
                    meterName,
                    formattedDate
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId(meterId), notification)
        } catch (denied: SecurityException) {
            // Permission can be revoked after scheduling; keep the daily check scheduled.
            Log.i(TAG, "Notification permission unavailable; switch alert not posted", denied)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Switch reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "SwitchReminderWorker"
        const val CHANNEL_ID = "switch_reminders"
        private const val KEY_METER_ID = "key_meter_id"
        private const val KEY_METER_NAME = "key_meter_name"

        /** How often to re-check the switch recommendation while a meter stays active. */
        private const val CHECK_INTERVAL_HOURS = 24L

        /** Notification IDs live in a distinct range from [MeterReminderWorker]'s meter-id-keyed ones. */
        private fun notificationId(meterId: Long): Int = (1_000_000 + meterId).toInt()

        private fun uniqueWorkName(meterId: Long): String = "switch_reminder_$meterId"

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface WorkerEntryPoint {
            fun meterRepository(): MeterRepository
        }

        fun scheduleCheck(context: Context, meterId: Long, meterName: String) {
            val data = Data.Builder()
                .putLong(KEY_METER_ID, meterId)
                .putString(KEY_METER_NAME, meterName)
                .build()

            val request = OneTimeWorkRequestBuilder<SwitchReminderWorker>()
                .setInputData(data)
                .setInitialDelay(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(meterId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Kicks off the first check immediately (used when a meter is newly activated). */
        fun scheduleImmediateCheck(context: Context, meterId: Long, meterName: String) {
            val data = Data.Builder()
                .putLong(KEY_METER_ID, meterId)
                .putString(KEY_METER_NAME, meterName)
                .build()

            val request = OneTimeWorkRequestBuilder<SwitchReminderWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(meterId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelCheck(context: Context, meterId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(meterId))
        }
    }
}
