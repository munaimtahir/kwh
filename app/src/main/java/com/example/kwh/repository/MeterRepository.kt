package com.example.kwh.repository

import com.example.kwh.billing.BillingCycleCalculator
import com.example.kwh.billing.CycleStats
import com.example.kwh.billing.CycleWindow
import com.example.kwh.billing.ReadingSnapshot
import com.example.kwh.billing.ThresholdForecast
import com.example.kwh.data.MeterDao
import com.example.kwh.data.MeterEntity
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.data.MeterWithReadings
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.round

@Singleton
class MeterRepository @Inject constructor(
    private val meterDao: MeterDao,
    private val clock: Clock,
    private val billingCycleCalculator: BillingCycleCalculator
) {

    val meterOverviews: Flow<List<MeterOverview>> =
        meterDao.observeMetersWithReadings().map { meters ->
            meters.map { withReadings ->
                val meter = withReadings.meter
                val window = billingCycleCalculator.currentWindow(meter.billingAnchorDay, clock)
                MeterOverview(
                    meter = meter,
                    latestReading = withReadings.readings.maxByOrNull { it.recordedAt },
                    cycleStats = buildCycleStats(meter, window, withReadings)
                )
            }
        }

    fun meterOverview(meterId: Long): Flow<MeterOverview?> =
        meterOverviews.map { meters -> meters.firstOrNull { it.meter.id == meterId } }

    fun readingsForMeter(meterId: Long): Flow<List<MeterReadingEntity>> =
        meterDao.observeReadingsForMeter(meterId)

    suspend fun addMeter(
        name: String,
        reminderFrequencyDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ): Long {
        val meter = MeterEntity(
            name = name,
            reminderEnabled = false,
            reminderFrequencyDays = reminderFrequencyDays,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute
        )
        return meterDao.insertMeter(meter)
    }

    suspend fun addReading(
        meterId: Long,
        value: Double,
        notes: String?,
        recordedAt: Long
    ): Long {
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

    suspend fun updateMeterSettings(
        meterId: Long,
        billingAnchorDay: Int,
        thresholdsCsv: String,
        reminderFrequencyDays: Int
    ): MeterEntity? {
        val current = meterDao.getMeterById(meterId) ?: return null
        val sanitizedAnchor = billingAnchorDay.coerceIn(1, 31)
        val sanitizedThresholds = sanitizeThresholds(thresholdsCsv)
        val sanitizedFrequency = reminderFrequencyDays.coerceAtLeast(1)
        val updated = current.copy(
            billingAnchorDay = sanitizedAnchor,
            thresholdsCsv = sanitizedThresholds,
            reminderFrequencyDays = sanitizedFrequency
        )
        meterDao.updateMeter(updated)
        return updated
    }

    suspend fun getMeter(meterId: Long): MeterEntity? = meterDao.getMeterById(meterId)

    suspend fun getCycleStats(meterId: Long): CycleStats? {
        val meter = meterDao.getMeterById(meterId) ?: return null
        val window = billingCycleCalculator.currentWindow(meter.billingAnchorDay, clock)
        val start = window.start.toEpochMilli()
        val end = window.end.toEpochMilli()
        val earliest = meterDao.earliestInWindow(meterId, start, end)
        val latest = meterDao.latestInWindow(meterId, start, end)
        val carry = meterDao.latestBefore(meterId, start)
        return buildCycleStats(
            meter = meter,
            window = window,
            baseline = earliest ?: carry,
            latest = latest ?: earliest,
            carry = carry
        )
    }

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

    private fun buildCycleStats(
        meter: MeterEntity,
        window: CycleWindow,
        withReadings: MeterWithReadings
    ): CycleStats {
        val sorted = withReadings.readings.sortedBy { it.recordedAt }
        val start = window.start.toEpochMilli()
        val end = window.end.toEpochMilli()
        val baseline = sorted.firstOrNull { it.recordedAt in start until end }
            ?: sorted.lastOrNull { it.recordedAt < start }
        val latest = sorted.lastOrNull { it.recordedAt in start until end }
        val carry = sorted.lastOrNull { it.recordedAt < start }
        return buildCycleStats(meter, window, baseline, latest, carry)
    }

    private fun buildCycleStats(
        meter: MeterEntity,
        window: CycleWindow,
        baseline: MeterReadingEntity?,
        latest: MeterReadingEntity?,
        carry: MeterReadingEntity?
    ): CycleStats {
        val baselineSnapshot = baseline?.toSnapshot() ?: carry?.toSnapshot()
        val latestSnapshot = latest?.toSnapshot()
        val usedUnits = if (baselineSnapshot != null && latestSnapshot != null) {
            latestSnapshot.value - baselineSnapshot.value
        } else {
            0.0
        }

        val elapsedDays = elapsedDays(window)
        val rate = if (baselineSnapshot != null && latestSnapshot != null && elapsedDays > 0.0) {
            usedUnits / elapsedDays
        } else {
            0.0
        }

        val projected = if (baselineSnapshot != null && latestSnapshot != null) {
            rate * cycleLengthDays(window)
        } else {
            0.0
        }

        val nextThreshold = resolveNextThreshold(
            thresholdsCsv = meter.thresholdsCsv,
            usedUnits = usedUnits,
            rate = rate,
            window = window
        )

        return CycleStats(
            meterId = meter.id,
            window = window,
            baseline = baselineSnapshot,
            latest = latestSnapshot,
            usedUnits = usedUnits,
            ratePerDay = rate,
            projectedUnits = projected,
            nextThreshold = nextThreshold
        )
    }

    private fun sanitizeThresholds(input: String): String {
        val values = parseThresholds(input)
        return values.joinToString(separator = ",")
    }

    private fun elapsedDays(window: CycleWindow): Double {
        val now = minOf(clock.instant(), window.end.minusMillis(1))
        val elapsed = Duration.between(window.start, now)
        val days = elapsed.toMillis() / MILLIS_PER_DAY
        return if (days < 1.0) 1.0 else days
    }

    private fun cycleLengthDays(window: CycleWindow): Double {
        val duration = Duration.between(window.start, window.end)
        return duration.toMillis() / MILLIS_PER_DAY
    }

    private fun resolveNextThreshold(
        thresholdsCsv: String,
        usedUnits: Double,
        rate: Double,
        window: CycleWindow
    ): ThresholdForecast? {
        if (rate <= 0.0) return null
        val thresholds = parseThresholds(thresholdsCsv)
        if (thresholds.isEmpty()) return null
        val today = LocalDate.now(clock)
        val endDate = window.end.atZone(clock.zone).toLocalDate()
        return thresholds.asSequence()
            .filter { it.toDouble() > usedUnits }
            .mapNotNull { threshold ->
                val daysUntil = ceil((threshold - usedUnits) / rate)
                if (daysUntil.isNaN() || daysUntil.isInfinite()) {
                    null
                } else {
                    val eta = today.plusDays(daysUntil.toLong())
                    if (eta.isBefore(endDate)) {
                        ThresholdForecast(threshold, eta)
                    } else {
                        null
                    }
                }
            }
            .firstOrNull()
    }

    private fun parseThresholds(csv: String): List<Int> {
        return csv.split(',')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) null else trimmed.toIntOrNull()
            }
            .filter { it > 0 }
            .distinct()
            .sorted()
    }

    private fun MeterReadingEntity.toSnapshot(): ReadingSnapshot =
        ReadingSnapshot(
            id = id,
            value = value,
            recordedAt = Instant.ofEpochMilli(recordedAt),
            notes = notes
        )

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000.0
    }
}

data class MeterOverview(
    val meter: MeterEntity,
    val latestReading: MeterReadingEntity?,
    val cycleStats: CycleStats
)
