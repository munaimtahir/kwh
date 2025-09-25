package com.example.kwh.repository

import com.example.kwh.data.MeterDao
import com.example.kwh.data.MeterEntity
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.data.MeterWithLatestReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeterRepository(private val meterDao: MeterDao) {
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

    suspend fun addReading(meterId: Long, value: Double, notes: String?, recordedAt: Long) {
        meterDao.insertReading(
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
}
