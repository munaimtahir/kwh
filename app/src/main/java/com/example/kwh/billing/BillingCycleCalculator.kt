package com.example.kwh.billing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Represents the time window for a billing cycle. The window is half-open, i.e. it includes
 * [start] and excludes [end].
 */
data class CycleWindow(val start: Instant, val end: Instant) {
    init {
        require(end.isAfter(start)) { "Cycle end must be after start" }
    }
}

/**
 * Calculates billing cycle boundaries based on an anchor day supplied by the utility.
 */
interface BillingCycleCalculator {
    fun currentWindow(anchorDay: Int, clock: Clock = Clock.systemDefaultZone()): CycleWindow
}

/**
 * Default implementation of [BillingCycleCalculator]. It interprets the billing anchor day as the
 * utility's reading date for each month. When the month does not contain the anchor day (e.g., the
 * 31st in February) the final day of the month is used instead.
 */
class DefaultBillingCycleCalculator : BillingCycleCalculator {

    override fun currentWindow(anchorDay: Int, clock: Clock): CycleWindow {
        require(anchorDay in 1..31) { "Anchor day must be within 1..31" }

        val zone: ZoneId = clock.zone
        val today = LocalDate.now(clock)
        val cycleStartDate = resolveCycleStart(today, anchorDay)
        val cycleEndDate = cycleStartDate.plusMonths(1)

        return CycleWindow(
            start = cycleStartDate.atStartOfDay(zone).toInstant(),
            end = cycleEndDate.atStartOfDay(zone).toInstant()
        )
    }

    private fun resolveCycleStart(today: LocalDate, anchorDay: Int): LocalDate {
        val anchorThisMonth = today.withDayOfMonth(anchorDay.coerceAtMost(today.lengthOfMonth()))
        return if (anchorThisMonth <= today) {
            anchorThisMonth
        } else {
            val previousMonth = today.minusMonths(1)
            previousMonth.withDayOfMonth(anchorDay.coerceAtMost(previousMonth.lengthOfMonth()))
        }
    }
}
