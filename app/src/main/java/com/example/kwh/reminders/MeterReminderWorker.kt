package com.example.kwh.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.example.kwh.billing.CycleStats
import com.example.kwh.repository.MeterRepository
import com.example.kwh.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.Duration
import java.util.concurrent.TimeUnit

class MeterReminderWorker(
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
            Log.w(TAG, "Work triggered without meter id")
            return Result.failure()
        }

        val meterName = inputData.getString(KEY_METER_NAME) ?: return Result.failure()
        val frequencyDays = inputData.getInt(KEY_FREQUENCY_DAYS, 30)
        val hour = inputData.getInt(KEY_HOUR, 9)
        val minute = inputData.getInt(KEY_MINUTE, 0)
        val snoozeMinutes = inputData.getInt(KEY_SNOOZE_MINUTES, SettingsRepository.DEFAULT_SNOOZE)

        val cycleStats = repository.getCycleStats(meterId)
        Log.i(TAG, "Posting reminder for meter=$meterId name=$meterName")
        val additionalLines = cycleStats?.let { buildAdditionalMessages(it) } ?: emptyList()
        showNotification(
            meterId = meterId,
            meterName = meterName,
            frequencyDays = frequencyDays,
            hour = hour,
            minute = minute,
            snoozeMinutes = snoozeMinutes,
            additionalLines = additionalLines
        )

        scheduleNextReminder(
            context = applicationContext,
            meterId = meterId,
            meterName = meterName,
            frequencyDays = frequencyDays,
            hour = hour,
            minute = minute,
            snoozeMinutes = snoozeMinutes
        )

        Log.d(TAG, "Next reminder scheduled for meter=$meterId in ${ReminderScheduler.nextReminderDelay(frequencyDays, hour, minute)}")

        return Result.success()
    }

    private fun showNotification(
        meterId: Long,
        meterName: String,
        frequencyDays: Int,
        hour: Int,
        minute: Int,
        snoozeMinutes: Int,
        additionalLines: List<String>
    ) {
        ensureChannel()
        val snoozeIntent = Intent(applicationContext, SnoozeReceiver::class.java).apply {
            putExtra(EXTRA_METER_ID, meterId)
            putExtra(EXTRA_METER_NAME, meterName)
            putExtra(EXTRA_FREQUENCY_DAYS, frequencyDays)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val snoozePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            meterId.toInt(),
            snoozeIntent,
            pendingFlags
        )
        val contentLines = buildList {
            add(meterName)
            addAll(additionalLines)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(applicationContext.getString(com.example.kwh.R.string.reminder_notification_title))
            .setContentText(meterName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                applicationContext.getString(
                    com.example.kwh.R.string.snooze_for_minutes,
                    snoozeMinutes
                ),
                snoozePendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentLines.joinToString("\n")))
            .build()

        NotificationManagerCompat.from(applicationContext).notify(meterId.toInt(), notification)
    }

    private fun buildAdditionalMessages(stats: CycleStats): List<String> {
        val zone: ZoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val messages = mutableListOf<String>()

        val thresholdForecast = stats.nextThreshold
        if (thresholdForecast != null) {
            val thresholdDate = thresholdForecast.eta
            val thresholdValue = thresholdForecast.threshold
            val daysUntil = ChronoUnit.DAYS.between(today, thresholdDate)
            if (daysUntil in 0 until THRESHOLD_WARNING_WINDOW_DAYS) {
                val formattedDate = thresholdDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                messages += applicationContext.getString(
                    com.example.kwh.R.string.reminder_notification_threshold,
                    thresholdValue,
                    formattedDate
                )
            }
        }

        if (stats.latest == null) {
            val cycleStart = stats.window.start.atZone(zone).toLocalDate()
            val daysIntoCycle = ChronoUnit.DAYS.between(cycleStart, today)
            if (daysIntoCycle > NO_READING_NUDGE_THRESHOLD_DAYS) {
                messages += applicationContext.getString(
                    com.example.kwh.R.string.reminder_notification_no_reading
                )
            }
        }

        return messages
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
        private const val TAG = "MeterReminderWorker"
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

        /**
         * Threshold warning window in days. When a threshold is expected to be crossed within
         * this many days, a proactive alert will be shown in the notification.
         */
        private const val THRESHOLD_WARNING_WINDOW_DAYS = 7

        /**
         * Minimum days into cycle before showing "no reading" nudge. If no readings have been
         * recorded in the current cycle and this many days have elapsed, a nudge notification
         * will be shown.
         */
        private const val NO_READING_NUDGE_THRESHOLD_DAYS = 10

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface WorkerEntryPoint {
            fun meterRepository(): MeterRepository
        }

        fun scheduleReminder(
            context: Context,
            meterId: Long,
            meterName: String,
            frequencyDays: Int,
            hour: Int,
            minute: Int,
            snoozeMinutes: Int
        ) {
            val delay = ReminderScheduler.nextReminderDelay(frequencyDays, hour, minute)
            Log.d(TAG, "Scheduling reminder for meter=$meterId delay=$delay")
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
            Log.d(TAG, "Cancelling reminder for meter=$meterId")
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(meterId))
        }

        private fun scheduleNextReminder(
            context: Context,
            meterId: Long,
            meterName: String,
            frequencyDays: Int,
            hour: Int,
            minute: Int,
            snoozeMinutes: Int
        ) {
            scheduleReminder(context, meterId, meterName, frequencyDays, hour, minute, snoozeMinutes)
        }

        private fun uniqueWorkName(meterId: Long): String = "meter_reminder_$meterId"

        fun scheduleSnoozedReminder(
            context: Context,
            meterId: Long,
            meterName: String,
            frequencyDays: Int,
            hour: Int,
            minute: Int,
            snoozeMinutes: Int
        ) {
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
                .setInitialDelay(snoozeMinutes.toLong(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(meterId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
