package com.example.kwh.data

data class MeterWithLatestReading(
    val meter: MeterEntity,
    val latestReading: MeterReadingEntity?
)
