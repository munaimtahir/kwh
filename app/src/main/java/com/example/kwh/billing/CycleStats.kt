package com.example.kwh.billing

import java.time.Instant
import java.time.LocalDate

/**
 * Snapshot of a meter reading used for cycle level calculations.
 */
data class ReadingSnapshot(
    val id: Long,
    val value: Double,
    val recordedAt: Instant,
    val notes: String?
)

/**
 * Represents the predicted point in time when a configured threshold will be
 * reached, assuming the current consumption rate remains constant.
 */
data class ThresholdForecast(
    val threshold: Int,
    val eta: LocalDate
)

/**
 * Aggregated statistics for a billing cycle window.
 */
data class CycleStats(
    val meterId: Long,
    val window: CycleWindow,
    val baseline: ReadingSnapshot?,
    val latest: ReadingSnapshot?,
    val usedUnits: Double,
    val ratePerDay: Double,
    val projectedUnits: Double,
    val nextThreshold: ThresholdForecast?
)
