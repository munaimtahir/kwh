package com.example.kwh.billing

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * How urgently the active meter in an environment should be switched out.
 */
enum class SwitchUrgency { NORMAL, SOON, OVERDUE }

/**
 * A dated recommendation to switch away from the currently active meter in an environment.
 */
data class SwitchRecommendation(
    val meterId: Long,
    val switchByDate: LocalDate,
    val urgency: SwitchUrgency,
    val targetThreshold: Int
)

/**
 * Turns a meter's existing threshold ETA ([ThresholdForecast]) into a "switch by this date"
 * recommendation, using the meter's configured safety buffer. See
 * `docs/ENVIRONMENT_SWITCHING_SPEC.md` for the algorithm this implements.
 */
object SwitchAdvisor {

    /** Recommendation is escalated to SOON once this many days (or fewer) remain until switchByDate. */
    private const val SOON_WINDOW_DAYS = 2L

    fun recommend(
        meterId: Long,
        forecast: ThresholdForecast?,
        safetyBufferDays: Int,
        today: LocalDate
    ): SwitchRecommendation? {
        if (forecast == null) return null
        val switchByDate = forecast.eta.minusDays(safetyBufferDays.toLong())
        val daysUntilSwitch = ChronoUnit.DAYS.between(today, switchByDate)
        val urgency = when {
            !today.isBefore(switchByDate) -> SwitchUrgency.OVERDUE
            daysUntilSwitch <= SOON_WINDOW_DAYS -> SwitchUrgency.SOON
            else -> SwitchUrgency.NORMAL
        }
        return SwitchRecommendation(
            meterId = meterId,
            switchByDate = switchByDate,
            urgency = urgency,
            targetThreshold = forecast.threshold
        )
    }

    /**
     * Threshold-ETA forecast targeting specifically the *lowest* configured threshold (the
     * assumed switch target per `docs/ENVIRONMENT_SWITCHING_SPEC.md` Open Question 2), not
     * whichever threshold [CycleStats.nextThreshold] happens to point at next. Unlike the generic
     * threshold ETA, this still returns a (past-dated) forecast once the target has already been
     * crossed, so an already-over-threshold meter surfaces as OVERDUE instead of silently
     * disappearing or retargeting a higher slab.
     */
    fun switchThresholdForecast(
        thresholdsCsv: String,
        usedUnits: Double,
        ratePerDay: Double,
        cycleEnd: LocalDate,
        today: LocalDate
    ): ThresholdForecast? {
        if (ratePerDay <= 0.0) return null
        val target = thresholdsCsv.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .minOrNull() ?: return null

        val remaining = target - usedUnits
        val daysUntil = ceil(remaining / ratePerDay)
        if (daysUntil.isNaN() || daysUntil.isInfinite()) return null
        val eta = today.plusDays(daysUntil.toLong())

        // Not yet crossed: only worth flagging if it would happen before the cycle resets.
        // Already crossed: always surface it, however far into the cycle we are.
        if (usedUnits < target && !eta.isBefore(cycleEnd)) return null

        return ThresholdForecast(target, eta)
    }
}
