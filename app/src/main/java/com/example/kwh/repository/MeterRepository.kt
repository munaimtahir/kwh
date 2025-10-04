package com.example.kwh.repository

import com.example.kwh.billing.BillingCycleCalculator
import com.example.kwh.billing.CycleStats
import com.example.kwh.data.MeterDao
import com.example.kwh.data.MeterEntity
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.data.MeterWithLatestReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

@Singleton
class MeterRepository @Inject constructor(
    private val meterDao: MeterDao,
    private val billingCycleCalculator: BillingCycleCalculator,
    private val clock: Clock
) {
    val metersWithLatestReading: Flow<List<MeterWithLatestReading>> =
        meterDao.observeMetersWithReadings().map { meters ->
            meters.map { meterWithReadings ->
                MeterWithLatestReading(
                    meter = meterWithReadings.meter,
                    latestReading = meterWithReadings.readings.maxByOrNull { it.recordedAt }
                )
            }
        }

    fun readingsForMeter(meterId: Long): Flow<List<MeterReadingEntity>> =
        meterDao.observeReadingsForMeter(meterId)

    suspend fun addMeter(name: String, reminderFrequencyDays: Int, reminderHour: Int, reminderMinute: Int): Long {
        val meter = MeterEntity(
            name = name,
            reminderEnabled = false,
            reminderFrequencyDays = reminderFrequencyDays,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute
        )
        return meterDao.insertMeter(meter)
    }

    suspend fun addReading(meterId: Long, value: Double, notes: String?, recordedAt: Long): Long {
        return meterDao.insertReading(
            MeterReadingEntity(
                meterId = meterId,
                value = value,
                notes = notes,
                recordedAt = recordedAt
            )
        )
    }

    suspend fun updateMeter(meter: MeterEntity) {
        meterDao.updateMeter(meter)
    }

    suspend fun updateReminderConfig(
        meterId: Long,
        enabled: Boolean,
        frequencyDays: Int,
        hour: Int,
        minute: Int
    ): MeterEntity? {
        val current = meterDao.getMeterById(meterId) ?: return null
        val updated = current.copy(
            reminderEnabled = enabled,
            reminderFrequencyDays = frequencyDays,
            reminderHour = hour,
            reminderMinute = minute
        )
        meterDao.updateMeter(updated)
        return updated
    }

    suspend fun getMeter(meterId: Long): MeterEntity? = meterDao.getMeterById(meterId)

    suspend fun deleteMeter(meterId: Long): Boolean {
        val meter = meterDao.getMeterById(meterId) ?: return false
        meterDao.deleteMeter(meter)
        return true
    }

    suspend fun deleteReading(readingId: Long): MeterReadingEntity? {
        val reading = meterDao.getReadingById(readingId) ?: return null
        meterDao.deleteReadingById(readingId)
        return reading
    }

    suspend fun restoreReading(reading: MeterReadingEntity) {
        meterDao.insertReadings(listOf(reading))
    }

    suspend fun restoreReadings(readings: List<MeterReadingEntity>) {
        meterDao.insertReadings(readings)
    }

    suspend fun getCycleStats(meterId: Long): CycleStats? {
        val meter = meterDao.getMeterById(meterId) ?: return null
        val window = billingCycleCalculator.currentWindow(meter.billingAnchorDay, clock)
        val zone: ZoneId = clock.zone
        val startMillis = window.start.toEpochMilli()
        val endMillis = window.end.toEpochMilli()
        val earliestInWindow = meterDao.getEarliestReadingInWindow(meterId, startMillis, endMillis)
        val baseline = earliestInWindow ?: meterDao.getLatestReadingBefore(meterId, startMillis)
        val latest = meterDao.getLatestReadingInWindow(meterId, startMillis, endMillis)

        val usedUnits = if (baseline != null && latest != null) {
            latest.value - baseline.value
        } else {
            0.0
        }

        val today: LocalDate = Instant.now(clock).atZone(zone).toLocalDate()
        val cycleStart: LocalDate = window.start.atZone(zone).toLocalDate()
        val cycleEnd: LocalDate = window.end.atZone(zone).toLocalDate()
        val rawDaysElapsed = ChronoUnit.DAYS.between(cycleStart, today)
        val daysElapsed = max(1L, rawDaysElapsed.coerceAtLeast(0))
        val cycleLengthDays = ChronoUnit.DAYS.between(cycleStart, cycleEnd).coerceAtLeast(1)
        val rate = if (baseline != null && latest != null) usedUnits / daysElapsed else 0.0

        val projectedUnits = if (baseline != null && latest != null) {
            round(rate * cycleLengthDays)
        } else {
            null
        }

        val thresholds = meter.thresholdsCsv
            .split(',')
            .mapNotNull { value ->
                value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            }
            .sorted()

        val nextThresholdInfo = if (baseline != null && latest != null && rate > 0.0) {
            thresholds.firstNotNullOfOrNull { threshold ->
                if (threshold > usedUnits) {
                    val daysUntil = ceil((threshold - usedUnits) / rate).toLong()
                    val eta = today.plusDays(daysUntil)
                    if (eta.isBefore(cycleEnd)) {
                        threshold to eta
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } else {
            null
        }

        return CycleStats(
            window = window,
            baseline = baseline,
            latest = latest,
            usedUnits = usedUnits,
            projectedUnits = projectedUnits,
            nextThreshold = nextThresholdInfo?.first,
            nextThresholdDate = nextThresholdInfo?.second
        )
    }
}
