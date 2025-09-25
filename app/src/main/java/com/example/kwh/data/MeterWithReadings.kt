package com.example.kwh.data

import androidx.room.Embedded
import androidx.room.Relation

data class MeterWithReadings(
    @Embedded val meter: MeterEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "meter_id"
    )
    val readings: List<MeterReadingEntity>
)
