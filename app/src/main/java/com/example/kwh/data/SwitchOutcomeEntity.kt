package com.example.kwh.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records a single switch's predicted-vs-actual outcome, used by [com.example.kwh.repository.MeterRepository]
 * to self-tune a meter's `safety_buffer_days`. See `docs/ENVIRONMENT_SWITCHING_SPEC.md` (M9).
 */
@Entity(
    tableName = "switch_outcomes",
    foreignKeys = [
        ForeignKey(
            entity = MeterEntity::class,
            parentColumns = ["id"],
            childColumns = ["meter_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meter_id")]
)
data class SwitchOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meter_id") val meterId: Long,
    @ColumnInfo(name = "recommended_date") val recommendedDate: Long,
    @ColumnInfo(name = "actual_switch_date") val actualSwitchDate: Long,
    @ColumnInfo(name = "units_at_switch") val unitsAtSwitch: Double,
    @ColumnInfo(name = "threshold_target") val thresholdTarget: Int
)
