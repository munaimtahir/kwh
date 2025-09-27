package com.example.kwh.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.util.concurrent.TimeUnit

class MeterReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val meterId = inputData.getLong(KEY_METER_ID, -1)
        if (meterId == -1L) return Result.failure()

        val meterName = inputData.getString(KEY_METER_NAME) ?: return Result.failure()
        val frequencyDays = inputData.getInt(KEY_FREQUENCY_DAYS, 30)
        val hour = inputData.getInt(KEY_HOUR, 9)
        val minute = inputData.getInt(KEY_MINUTE, 0)

        showNotification(meterId.toInt(), meterName)

        scheduleNextReminder(
            context = applicationContext,
            meterId = meterId,
            meterName = meterName,
            frequencyDays = frequencyDays,
            hour = hour,
            minute = minute
        )

        return Result.success()
    }

    private fun showNotification(notificationId: Int, meterName: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(applicationContext.getString(com.example.kwh.R.string.reminder_notification_title))
            .setContentText(meterName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meter reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "meter_reminders"
        private const val KEY_METER_ID = "key_meter_id"
        private const val KEY_METER_NAME = "key_meter_name"
        private const val KEY_FREQUENCY_DAYS = "key_frequency_days"
        private const val KEY_HOUR = "key_hour"
        private const val KEY_MINUTE = "key_minute"
        private const val KEY_SNOOZE_MINUTES = "key_snooze_minutes"
        
        const val EXTRA_METER_ID = "extra_meter_id"
        const val EXTRA_METER_NAME = "extra_meter_name"
        const val EXTRA_FREQUENCY_DAYS = "extra_frequency"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

        fun scheduleReminder(
            context: Context,
            meterId: Long,
            meterName: String,
            frequencyDays: Int,
            hour: Int,
            minute: Int,
            snoozeMinutes: Int = 60
        ) {
            val delay = ReminderScheduler.nextReminderDelay(frequencyDays, hour, minute)
            val data = Data.Builder()
                .putLong(KEY_METER_ID, meterId)
                .putString(KEY_METER_NAME, meterName)
                .putInt(KEY_FREQUENCY_DAYS, frequencyDays)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .putInt(KEY_SNOOZE_MINUTES, snoozeMinutes)
                .build()

            val request = OneTimeWorkRequestBuilder<MeterReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(meterId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelReminder(context: Context, meterId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(meterId))
        }

        private fun scheduleNextReminder(
            context: Context,
            meterId: Long,
            meterName: String,
            frequencyDays: Int,
            hour: Int,
            minute: Int
        ) {
            scheduleReminder(context, meterId, meterName, frequencyDays, hour, minute)
        }

        private fun uniqueWorkName(meterId: Long): String = "meter_reminder_$meterId"
    }
}
