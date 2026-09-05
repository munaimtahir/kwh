package com.example.kwh.repository

import com.example.kwh.billing.BillingCycleCalculator
import com.example.kwh.billing.CycleStats
import com.example.kwh.billing.CycleWindow
import com.example.kwh.billing.ReadingSnapshot
import com.example.kwh.billing.SwitchAdvisor
import com.example.kwh.billing.SwitchRecommendation
import com.example.kwh.billing.ThresholdForecast
import com.example.kwh.data.EnvironmentEntity
import com.example.kwh.data.MeterActivationEntity
import com.example.kwh.data.MeterDao
import com.example.kwh.data.MeterEntity
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.data.MeterWithReadings
import com.example.kwh.data.SwitchOutcomeEntity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    val meterOverviews: Flow<List<MeterOverview>> = combine(
        meterDao.observeMetersWithReadings(),
        meterDao.observeAllActivations()
    ) { meters, activations ->
        meters.map { withReadings ->
            val meter = withReadings.meter
            val window = billingCycleCalculator.currentWindow(meter.billingAnchorDay, clock)
            val meterActivations = activations.filter { it.meterId == meter.id }
            MeterOverview(
                meter = meter,
                latestReading = withReadings.readings.maxByOrNull { it.recordedAt },
                cycleStats = buildCycleStats(meter, window, withReadings, meterActivations)
            )
        }
    }

    fun meterOverview(meterId: Long): Flow<MeterOverview?> =
        meterOverviews.map { meters -> meters.firstOrNull { it.meter.id == meterId } }

    fun readingsForMeter(meterId: Long): Flow<List<MeterReadingEntity>> =
        meterDao.observeReadingsForMeter(meterId)

    /** Meters grouped by environment, with the currently active meter (if any) per environment. */
    val environmentOverviews: Flow<List<EnvironmentOverview>> = combine(
        meterDao.observeEnvironments(),
        meterOverviews,
        meterDao.observeActiveActivations()
    ) { environments, overviews, activeActivations ->
        val activeMeterIds = activeActivations.map { it.meterId }.toSet()
        environments.map { environment ->
            val environmentMeters = overviews.filter { it.meter.environmentId == environment.id }
            val activeOverview = environmentMeters.firstOrNull { it.meter.id in activeMeterIds }
            val recommendation = activeOverview?.let { buildSwitchRecommendation(it) }
            val targetMeterId = if (recommendation != null) {
                environmentMeters
                    .filter { it.meter.id != activeOverview.meter.id }
                    .minByOrNull { it.cycleStats.usedUnits }
                    ?.meter?.id
            } else {
                null
            }
            EnvironmentOverview(
                environment = environment,
                meters = environmentMeters,
                activeMeterId = activeOverview?.meter?.id,
                switchRecommendation = recommendation,
                switchTargetMeterId = targetMeterId
            )
        }
    }

    /** Meters not assigned to any environment; these behave exactly as before this feature. */
    val ungroupedMeterOverviews: Flow<List<MeterOverview>> = meterOverviews.map { overviews ->
        overviews.filter { it.meter.environmentId == null }
    }

    suspend fun createEnvironment(name: String): Long {
        return meterDao.insertEnvironment(EnvironmentEntity(name = name.trim()))
    }

    suspend fun renameEnvironment(environmentId: Long, name: String): EnvironmentEntity? {
        val current = meterDao.getEnvironmentById(environmentId) ?: return null
        val updated = current.copy(name = name.trim())
        meterDao.updateEnvironment(updated)
        return updated
    }

    /** Deletes the environment and unassigns its meters so they fall back to "Ungrouped". */
    suspend fun deleteEnvironment(environmentId: Long): Boolean {
        val environment = meterDao.getEnvironmentById(environmentId) ?: return false
        meterDao.getMetersForEnvironment(environmentId).forEach { meter ->
            meterDao.updateMeter(meter.copy(environmentId = null))
        }
        meterDao.deleteEnvironment(environment)
        return true
    }

    suspend fun assignMeterToEnvironment(meterId: Long, environmentId: Long?): MeterEntity? {
        val meter = meterDao.getMeterById(meterId) ?: return null
        val updated = meter.copy(environmentId = environmentId)
        meterDao.updateMeter(updated)
        return updated
    }

    /**
     * Marks [meterId] as the active meter in its environment, closing any other open
     * activation row for meters in that environment first. No-op (idempotent) if [meterId] is
     * already the sole active meter. Returns null if the meter doesn't exist or isn't assigned
     * to an environment.
     */
    suspend fun activateMeter(meterId: Long): MeterEntity? {
        val meter = meterDao.getMeterById(meterId) ?: return null
        val environmentId = meter.environmentId ?: return null
        val openActivations = meterDao.getOpenActivationsForEnvironment(environmentId)
        if (openActivations.size == 1 && openActivations.single().meterId == meterId) {
            return meter
        }
        // Record how well the recommendation (if any) for the outgoing meter(s) panned out, and
        // let the outcome nudge that meter's own safety buffer, before we close their activations.
        openActivations.forEach { activation ->
            if (activation.meterId != meterId) {
                recordSwitchOutcomeIfRecommended(activation.meterId)
            }
        }
        val now = clock.instant().toEpochMilli()
        openActivations.forEach { activation ->
            meterDao.updateActivation(activation.copy(endedAt = now))
        }
        meterDao.insertActivation(MeterActivationEntity(meterId = meterId, startedAt = now))
        return meter
    }

    private suspend fun recordSwitchOutcomeIfRecommended(outgoingMeterId: Long) {
        val recommendation = getSwitchRecommendation(outgoingMeterId) ?: return
        val overview = meterOverviews.first().firstOrNull { it.meter.id == outgoingMeterId } ?: return
        meterDao.insertSwitchOutcome(
            SwitchOutcomeEntity(
                meterId = outgoingMeterId,
                recommendedDate = recommendation.switchByDate.toEpochMillis(),
                actualSwitchDate = LocalDate.now(clock).toEpochMillis(),
                unitsAtSwitch = overview.cycleStats.usedUnits,
                thresholdTarget = recommendation.targetThreshold
            )
        )
        recalibrateSafetyBuffer(outgoingMeterId)
    }

    /**
     * Bounded rolling-average adjustment (not a model, per `docs/AGENT_ENVIRONMENT_SWITCHING.md`):
     * nudges `safety_buffer_days` by at most 1 day based on the last [CALIBRATION_WINDOW] switch
     * outcomes for [meterId]. Tightens when the user consistently switches with comfortable
     * margin to spare; widens when they land close to or over the threshold.
     */
    private suspend fun recalibrateSafetyBuffer(meterId: Long) {
        val outcomes = meterDao.getRecentSwitchOutcomes(meterId, CALIBRATION_WINDOW)
        if (outcomes.size < MIN_OUTCOMES_FOR_CALIBRATION) return
        val meter = meterDao.getMeterById(meterId) ?: return

        val averageUnitsMargin = outcomes.map { it.thresholdTarget - it.unitsAtSwitch }.average()
        val averageThreshold = outcomes.map { it.thresholdTarget }.average()
        val marginRatio = if (averageThreshold > 0) averageUnitsMargin / averageThreshold else 0.0

        val adjustment = when {
            averageUnitsMargin < 0 -> 1
            marginRatio > COMFORTABLE_MARGIN_RATIO -> -1
            else -> 0
        }
        if (adjustment == 0) return

        val newBuffer = (meter.safetyBufferDays + adjustment)
            .coerceIn(MIN_SAFETY_BUFFER_DAYS, MAX_SAFETY_BUFFER_DAYS)
        if (newBuffer != meter.safetyBufferDays) {
            meterDao.updateMeter(meter.copy(safetyBufferDays = newBuffer))
        }
    }

    fun switchOutcomes(meterId: Long): Flow<List<SwitchOutcomeEntity>> =
        meterDao.observeSwitchOutcomes(meterId)

    /** Rolling accuracy summary for [meterId]'s switch history, or null if there's no history yet. */
    fun calibrationAccuracy(meterId: Long): Flow<CalibrationAccuracy?> =
        switchOutcomes(meterId).map { outcomes ->
            if (outcomes.isEmpty()) {
                null
            } else {
                val recent = outcomes.take(CALIBRATION_WINDOW)
                CalibrationAccuracy(
                    sampleSize = recent.size,
                    averageDaysEarly = recent.map {
                        (it.recommendedDate - it.actualSwitchDate) / MILLIS_PER_DAY
                    }.average(),
                    averageUnitsMargin = recent.map { it.thresholdTarget - it.unitsAtSwitch }.average()
                )
            }
        }

    suspend fun updateSafetyBufferDays(meterId: Long, days: Int): MeterEntity? {
        val current = meterDao.getMeterById(meterId) ?: return null
        val sanitized = days.coerceIn(MIN_SAFETY_BUFFER_DAYS, MAX_SAFETY_BUFFER_DAYS)
        val updated = current.copy(safetyBufferDays = sanitized)
        meterDao.updateMeter(updated)
        return updated
    }

    private fun LocalDate.toEpochMillis(): Long = atStartOfDay(clock.zone).toInstant().toEpochMilli()

    suspend fun getActiveMeter(environmentId: Long): MeterEntity? =
        meterDao.getActiveMeterForEnvironment(environmentId)

    fun activationHistory(meterId: Long): Flow<List<MeterActivationEntity>> =
        meterDao.observeActivationHistory(meterId)

    fun isMeterActive(meterId: Long): Flow<Boolean> =
        meterDao.observeActiveActivations().map { activations -> activations.any { it.meterId == meterId } }

    val environments: Flow<List<EnvironmentEntity>> = meterDao.observeEnvironments()

    suspend fun metersInEnvironment(environmentId: Long): List<MeterEntity> =
        meterDao.getMetersForEnvironment(environmentId)

    /** One-shot switch recommendation for [meterId], or null if it isn't the active meter in its environment. */
    suspend fun getSwitchRecommendation(meterId: Long): SwitchRecommendation? {
        val overview = meterOverviews.first().firstOrNull { it.meter.id == meterId } ?: return null
        if (!isMeterActive(meterId).first()) return null
        return buildSwitchRecommendation(overview)
    }

    private fun buildSwitchRecommendation(overview: MeterOverview): SwitchRecommendation? {
        val stats = overview.cycleStats
        val today = LocalDate.now(clock)
        val forecast = SwitchAdvisor.switchThresholdForecast(
            thresholdsCsv = overview.meter.thresholdsCsv,
            usedUnits = stats.usedUnits,
            ratePerDay = stats.ratePerDay,
            cycleEnd = stats.window.end.atZone(clock.zone).toLocalDate(),
            today = today
        )
        return SwitchAdvisor.recommend(
            meterId = overview.meter.id,
            forecast = forecast,
            safetyBufferDays = overview.meter.safetyBufferDays,
            today = today
        )
    }

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
        require(value.isFinite() && value > 0.0) { "Enter a finite, positive meter reading." }
        val date = Instant.ofEpochMilli(recordedAt).atZone(clock.zone).toLocalDate()
        return meterDao.insertValidatedReading(
            MeterReadingEntity(
                meterId = meterId,
                value = value,
                notes = notes,
                recordedAt = recordedAt
            ),
            date.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
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
        val activations = meterDao.getActivationsForMeter(meterId)
        return buildCycleStats(
            meter = meter,
            window = window,
            baseline = carry ?: earliest,
            latest = latest ?: earliest,
            carry = carry,
            activations = activations
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

    /** Re-imports a meter's activation log (e.g. from a CSV export) as new activation rows. */
    suspend fun restoreActivations(meterId: Long, activations: List<Pair<Long, Long?>>) {
        activations.forEach { (startedAt, endedAt) ->
            meterDao.insertActivation(
                MeterActivationEntity(meterId = meterId, startedAt = startedAt, endedAt = endedAt)
            )
        }
    }

    private fun buildCycleStats(
        meter: MeterEntity,
        window: CycleWindow,
        withReadings: MeterWithReadings,
        activations: List<MeterActivationEntity> = emptyList()
    ): CycleStats {
        val sorted = withReadings.readings.sortedBy { it.recordedAt }
        val start = window.start.toEpochMilli()
        val end = window.end.toEpochMilli()
        val baseline = sorted.lastOrNull { it.recordedAt < start }
            ?: sorted.firstOrNull { it.recordedAt in start until end }
        val latest = sorted.lastOrNull { it.recordedAt in start until end }
        val carry = sorted.lastOrNull { it.recordedAt < start }
        return buildCycleStats(meter, window, baseline, latest, carry, activations)
    }

    private fun buildCycleStats(
        meter: MeterEntity,
        window: CycleWindow,
        baseline: MeterReadingEntity?,
        latest: MeterReadingEntity?,
        carry: MeterReadingEntity?,
        activations: List<MeterActivationEntity> = emptyList()
    ): CycleStats {
        val baselineSnapshot = baseline?.toSnapshot() ?: carry?.toSnapshot()
        val latestSnapshot = latest?.toSnapshot()
        val usedUnits = if (baselineSnapshot != null && latestSnapshot != null) {
            latestSnapshot.value - baselineSnapshot.value
        } else {
            0.0
        }

        // Meters in an environment with activation history use active-day-weighted elapsed time
        // so idle periods (when another meter was carrying the load) don't distort the rate.
        val elapsedDays = if (meter.environmentId != null && activations.isNotEmpty()) {
            activeDays(window, activations)
        } else {
            elapsedDays(window)
        }
        val rate = if (baselineSnapshot != null && latestSnapshot != null && elapsedDays > 0.0) {
            usedUnits / elapsedDays
        } else {
            0.0
        }

        val projected = if (baselineSnapshot != null && latestSnapshot != null) {
            round(rate * cycleLengthDays(window))
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

    /**
     * Sum of the overlap between each activation row and the cycle window, clipped to "now" for
     * the still-open row. See `docs/ENVIRONMENT_SWITCHING_SPEC.md` (active-day-weighted rate).
     */
    private fun activeDays(window: CycleWindow, activations: List<MeterActivationEntity>): Double {
        val cycleStart = window.start.toEpochMilli()
        val cycleEnd = window.end.toEpochMilli()
        val now = clock.instant().toEpochMilli().coerceAtMost(cycleEnd)
        val totalMillis = activations.sumOf { activation ->
            val overlapStart = max(activation.startedAt, cycleStart)
            val overlapEnd = minOf(activation.endedAt ?: now, cycleEnd)
            (overlapEnd - overlapStart).coerceAtLeast(0L)
        }
        val days = totalMillis / MILLIS_PER_DAY
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

        /** Rolling window size for adaptive calibration (spec: "last N=3-6 switches"). */
        private const val CALIBRATION_WINDOW = 6

        /** Require at least this many recorded switches before nudging the safety buffer. */
        private const val MIN_OUTCOMES_FOR_CALIBRATION = 3

        /** Margin (as a fraction of the threshold) considered "comfortable enough to tighten". */
        private const val COMFORTABLE_MARGIN_RATIO = 0.15

        const val MIN_SAFETY_BUFFER_DAYS = 1
        const val MAX_SAFETY_BUFFER_DAYS = 10
    }
}

/**
 * Rolling accuracy summary of a meter's switch history. [averageDaysEarly] is positive when the
 * user tends to switch before the recommended date (safe) and negative when they switch late.
 * [averageUnitsMargin] is positive when they land under the threshold with room to spare.
 */
data class CalibrationAccuracy(
    val sampleSize: Int,
    val averageDaysEarly: Double,
    val averageUnitsMargin: Double
)

data class MeterOverview(
    val meter: MeterEntity,
    val latestReading: MeterReadingEntity?,
    val cycleStats: CycleStats
)

data class EnvironmentOverview(
    val environment: EnvironmentEntity,
    val meters: List<MeterOverview>,
    val activeMeterId: Long?,
    val switchRecommendation: SwitchRecommendation?,
    val switchTargetMeterId: Long?
)
