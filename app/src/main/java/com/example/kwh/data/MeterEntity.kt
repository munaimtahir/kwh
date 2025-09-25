package com.example.kwh.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meters")
data class MeterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean = false,
    @ColumnInfo(name = "reminder_frequency_days") val reminderFrequencyDays: Int = 30,
    @ColumnInfo(name = "reminder_hour") val reminderHour: Int = 9,
    @ColumnInfo(name = "reminder_minute") val reminderMinute: Int = 0
)
